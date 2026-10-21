package com.diplomacy.villagediplomacy.diplomacy;

import com.diplomacy.villagediplomacy.config.VillageDiplomacyConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Advances settlement production from persisted game time. Only the trade
 * surplus enters the virtual warehouse; internal consumption is implicit.
 */
public final class SettlementProductionService {
    private static final ResourceLocation WHEAT = id("minecraft:wheat");
    private static final ResourceLocation WHITE_WOOL = id("minecraft:white_wool");
    private static final ResourceLocation RAW_MUTTON = id("minecraft:mutton");
    private static final ResourceLocation COAL = id("minecraft:coal");
    private static final ResourceLocation RAW_COPPER = id("minecraft:raw_copper");
    private static final ResourceLocation RAW_IRON = id("minecraft:raw_iron");
    private static final ResourceLocation STONE = id("minecraft:stone");
    private static final ResourceLocation HONEYCOMB = id("minecraft:honeycomb");
    private static final ResourceLocation APPLE = id("minecraft:apple");

    private SettlementProductionService() {
    }

    public static void updateAll(ServerLevel level) {
        SettlementSavedData data = SettlementSavedData.get(level);
        ProductionSettings settings = currentSettings();
        long gameTime = level.getGameTime();
        for (Settlement settlement : data.getAllSettlements()) {
            updateProduction(settlement, gameTime, settings);
        }
    }

    public static void initializeSettlement(Settlement settlement, long gameTime) {
        if (settlement.getPrimarySpecialization() == SettlementSpecialization.UNASSIGNED) {
            return;
        }
        ProductionSettings settings = currentSettings();
        configureWarehouse(settlement, settings);
        if (!settlement.isProductionInitialized()) {
            settlement.initializeProduction(gameTime);
        }
    }

    public static ProductionReport updateProduction(Settlement settlement,
                                                    long currentGameTime,
                                                    ProductionSettings settings) {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(settings, "settings");
        if (settlement.getPrimarySpecialization() == SettlementSpecialization.UNASSIGNED) {
            return ProductionReport.empty();
        }

        configureWarehouse(settlement, settings);
        long safeGameTime = Math.max(0L, currentGameTime);
        if (!settlement.isProductionInitialized()) {
            settlement.initializeProduction(safeGameTime);
            return ProductionReport.clockInitialized();
        }

        long previousGameTime = settlement.getLastProductionGameTime();
        if (safeGameTime < previousGameTime) {
            settlement.setLastProductionGameTime(safeGameTime);
            return ProductionReport.clockInitialized();
        }

        long elapsedDays = (safeGameTime - previousGameTime) / DiplomacyBalance.MINECRAFT_DAY_TICKS;
        if (elapsedDays <= 0L) {
            return ProductionReport.empty();
        }

        Map<ResourceLocation, Double> dailyTradeProduction = calculateDailyTradeProduction(settlement, settings);
        Map<ResourceLocation, Long> accepted = new LinkedHashMap<>();
        Map<ResourceLocation, Long> discarded = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, Double> entry : dailyTradeProduction.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            double exactProduction = entry.getValue() * elapsedDays + settlement.getProductionRemainder(itemId);
            double normalizedProduction = snapToNearbyInteger(exactProduction);
            long wholeUnits = floorToLong(normalizedProduction);
            double remainder = normalizeRemainder(normalizedProduction - Math.floor(normalizedProduction));
            settlement.setProductionRemainder(itemId, remainder);

            if (wholeUnits <= 0L) {
                continue;
            }

            VirtualWarehouse.StockView stock = settlement.getWarehouse().getStock(itemId);
            int freeCapacity = Math.max(0, stock.capacity() - stock.stored());
            int acceptedUnits = (int) Math.min(wholeUnits, freeCapacity);
            if (acceptedUnits > 0) {
                settlement.getWarehouse().add(itemId, acceptedUnits);
                accepted.put(itemId, (long) acceptedUnits);
            }
            if (wholeUnits > acceptedUnits) {
                discarded.put(itemId, wholeUnits - acceptedUnits);
            }
        }

        settlement.setLastProductionGameTime(
                previousGameTime + elapsedDays * DiplomacyBalance.MINECRAFT_DAY_TICKS
        );
        return new ProductionReport(elapsedDays, accepted, discarded, false);
    }

    public static Map<ResourceLocation, Double> calculateDailyGrossProduction(Settlement settlement,
                                                                              ProductionSettings settings) {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(settings, "settings");
        Map<ResourceLocation, Double> production = new TreeMap<>((left, right) ->
                left.toString().compareTo(right.toString()));

        addSpecializationProduction(
                production,
                settlement.getPrimarySpecialization(),
                1.0D,
                settlement.getLocalWoodResource()
        );
        settlement.getSecondarySpecialization().ifPresent(secondary -> addSpecializationProduction(
                production,
                secondary,
                settings.secondarySpecializationMultiplier(),
                settlement.getLocalWoodResource()
        ));
        return Collections.unmodifiableMap(production);
    }

    public static Map<ResourceLocation, Double> calculateDailyTradeProduction(Settlement settlement,
                                                                              ProductionSettings settings) {
        Map<ResourceLocation, Double> result = new LinkedHashMap<>();
        calculateDailyGrossProduction(settlement, settings).forEach((itemId, gross) ->
                result.put(itemId, gross * settings.tradeSurplusShare()));
        return Collections.unmodifiableMap(result);
    }

    private static void configureWarehouse(Settlement settlement, ProductionSettings settings) {
        calculateDailyTradeProduction(settlement, settings).forEach((itemId, dailyTradeAmount) -> {
            long calculatedCapacity = (long) Math.ceil(dailyTradeAmount * settings.warehouseCapacityDays());
            int capacity = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, calculatedCapacity));
            settlement.getWarehouse().configureCapacity(itemId, capacity);
        });
    }

    private static void addSpecializationProduction(Map<ResourceLocation, Double> production,
                                                    SettlementSpecialization specialization,
                                                    double multiplier,
                                                    ResourceLocation localWoodResource) {
        if (specialization == SettlementSpecialization.UNASSIGNED) {
            return;
        }

        switch (specialization) {
            case PLAINS -> {
                add(production, WHEAT, DiplomacyBalance.PLAINS_WHEAT_PER_DAY, multiplier);
                add(production, WHITE_WOOL, DiplomacyBalance.PLAINS_WOOL_PER_DAY, multiplier);
                add(production, RAW_MUTTON, DiplomacyBalance.PLAINS_RAW_MUTTON_PER_DAY, multiplier);
            }
            case MOUNTAIN -> {
                add(production, COAL, DiplomacyBalance.MOUNTAIN_COAL_PER_DAY, multiplier);
                add(production, RAW_COPPER, DiplomacyBalance.MOUNTAIN_RAW_COPPER_PER_DAY, multiplier);
                add(production, RAW_IRON, DiplomacyBalance.MOUNTAIN_RAW_IRON_PER_DAY, multiplier);
                add(production, STONE, DiplomacyBalance.MOUNTAIN_STONE_PER_DAY, multiplier);
            }
            case FOREST -> {
                add(production, localWoodResource, DiplomacyBalance.FOREST_LOGS_PER_DAY, multiplier);
                add(production, HONEYCOMB, DiplomacyBalance.FOREST_HONEYCOMB_PER_DAY, multiplier);
                add(production, APPLE, DiplomacyBalance.FOREST_APPLES_PER_DAY, multiplier);
            }
            default -> {
            }
        }
    }

    private static void add(Map<ResourceLocation, Double> production,
                            ResourceLocation itemId,
                            int baseAmount,
                            double multiplier) {
        production.merge(itemId, baseAmount * multiplier, Double::sum);
    }

    private static long floorToLong(double value) {
        if (value <= 0.0D) {
            return 0L;
        }
        if (value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) Math.floor(value);
    }

    private static double normalizeRemainder(double value) {
        if (!Double.isFinite(value) || value < 1.0E-9D || value >= 1.0D) {
            return 0.0D;
        }
        return value;
    }

    private static double snapToNearbyInteger(double value) {
        double nearestInteger = Math.rint(value);
        return Math.abs(value - nearestInteger) < 1.0E-9D ? nearestInteger : value;
    }

    public static ProductionSettings currentSettings() {
        return new ProductionSettings(
                VillageDiplomacyConfig.SERVER.tradeSurplusShare.get(),
                VillageDiplomacyConfig.SERVER.secondaryProductionMultiplier.get(),
                VillageDiplomacyConfig.SERVER.warehouseCapacityDays.get()
        );
    }

    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value));
    }

    public record ProductionSettings(double tradeSurplusShare,
                                     double secondarySpecializationMultiplier,
                                     int warehouseCapacityDays) {
        public ProductionSettings {
            if (!Double.isFinite(tradeSurplusShare) || tradeSurplusShare <= 0.0D || tradeSurplusShare > 1.0D) {
                throw new IllegalArgumentException("Trade surplus share must be in the range (0, 1]");
            }
            if (!Double.isFinite(secondarySpecializationMultiplier)
                    || secondarySpecializationMultiplier <= 0.0D
                    || secondarySpecializationMultiplier > 1.0D) {
                throw new IllegalArgumentException("Secondary production multiplier must be in the range (0, 1]");
            }
            if (warehouseCapacityDays <= 0) {
                throw new IllegalArgumentException("Warehouse capacity days must be positive");
            }
        }
    }

    public record ProductionReport(long elapsedDays,
                                   Map<ResourceLocation, Long> accepted,
                                   Map<ResourceLocation, Long> discarded,
                                   boolean initialized) {
        public ProductionReport {
            accepted = Map.copyOf(accepted);
            discarded = Map.copyOf(discarded);
        }

        private static ProductionReport empty() {
            return new ProductionReport(0L, Map.of(), Map.of(), false);
        }

        private static ProductionReport clockInitialized() {
            return new ProductionReport(0L, Map.of(), Map.of(), true);
        }
    }
}
