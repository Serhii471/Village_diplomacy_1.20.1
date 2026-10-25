package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementProductionServiceTest {
    private static final SettlementProductionService.ProductionSettings SETTINGS =
            new SettlementProductionService.ProductionSettings(0.70D, 0.50D, 5);
    private static final ResourceLocation WHEAT = id("minecraft:wheat");
    private static final ResourceLocation WOOL = id("minecraft:white_wool");
    private static final ResourceLocation MUTTON = id("minecraft:mutton");
    private static final ResourceLocation SPRUCE_LOG = id("minecraft:spruce_log");

    @Test
    void fractionalDailyProductionAccumulatesWithoutLongTermRoundingLoss() {
        Settlement settlement = settlement(SettlementSpecialization.PLAINS, null);
        settlement.initializeProduction(0L);

        for (int day = 1; day <= 5; day++) {
            SettlementProductionService.updateProduction(
                    settlement,
                    (long) day * DiplomacyBalance.MINECRAFT_DAY_TICKS,
                    SETTINGS
            );
        }

        assertEquals(new VirtualWarehouse.StockView(84, 0, 84), settlement.getWarehouse().getStock(WHEAT));
        assertEquals(new VirtualWarehouse.StockView(21, 0, 21), settlement.getWarehouse().getStock(WOOL));
        assertEquals(new VirtualWarehouse.StockView(14, 0, 14), settlement.getWarehouse().getStock(MUTTON));
        assertEquals(0.0D, settlement.getProductionRemainder(WHEAT), 1.0E-9D);
    }

    @Test
    void catchUpUsesPersistedGameTimeAndDoesNotDuplicateSamePeriod() {
        Settlement settlement = settlement(SettlementSpecialization.PLAINS, null);
        settlement.initializeProduction(1_000L);
        long afterThreeDays = 1_000L + 3L * DiplomacyBalance.MINECRAFT_DAY_TICKS;

        SettlementProductionService.ProductionReport first =
                SettlementProductionService.updateProduction(settlement, afterThreeDays, SETTINGS);
        SettlementProductionService.ProductionReport repeated =
                SettlementProductionService.updateProduction(settlement, afterThreeDays, SETTINGS);

        assertEquals(3L, first.elapsedDays());
        assertEquals(50, settlement.getWarehouse().getStock(WHEAT).stored());
        assertEquals(0.4D, settlement.getProductionRemainder(WHEAT), 1.0E-9D);
        assertEquals(0L, repeated.elapsedDays());
        assertTrue(repeated.accepted().isEmpty());
    }

    @Test
    void warehouseCapacityDiscardsOverflowInsteadOfDuplicatingIt() {
        Settlement settlement = settlement(SettlementSpecialization.PLAINS, null);
        settlement.initializeProduction(0L);
        SettlementProductionService.updateProduction(
                settlement,
                5L * DiplomacyBalance.MINECRAFT_DAY_TICKS,
                SETTINGS
        );

        SettlementProductionService.ProductionReport overflow = SettlementProductionService.updateProduction(
                settlement,
                6L * DiplomacyBalance.MINECRAFT_DAY_TICKS,
                SETTINGS
        );

        assertEquals(84, settlement.getWarehouse().getStock(WHEAT).stored());
        assertEquals(16L, overflow.discarded().get(WHEAT));
    }

    @Test
    void productionPreservesExistingTradeReservations() {
        Settlement settlement = settlement(SettlementSpecialization.PLAINS, null);
        settlement.initializeProduction(0L);
        SettlementProductionService.updateProduction(
                settlement,
                2L * DiplomacyBalance.MINECRAFT_DAY_TICKS,
                SETTINGS
        );
        assertTrue(settlement.getWarehouse().tryReserve(WHEAT, 10));

        SettlementProductionService.updateProduction(
                settlement,
                3L * DiplomacyBalance.MINECRAFT_DAY_TICKS,
                SETTINGS
        );

        assertEquals(new VirtualWarehouse.StockView(50, 10, 84),
                settlement.getWarehouse().getStock(WHEAT));
    }

    @Test
    void secondaryForestProducesHalfRateUsingLocalWood() {
        Settlement settlement = settlement(SettlementSpecialization.PLAINS, SettlementSpecialization.FOREST);
        settlement.setLocalWoodResource(SPRUCE_LOG);
        settlement.initializeProduction(0L);

        SettlementProductionService.updateProduction(
                settlement,
                5L * DiplomacyBalance.MINECRAFT_DAY_TICKS,
                SETTINGS
        );

        assertEquals(new VirtualWarehouse.StockView(42, 0, 42),
                settlement.getWarehouse().getStock(SPRUCE_LOG));
        assertFalse(settlement.getWarehouse().getContents().containsKey(id("minecraft:oak_log")));
    }

    @Test
    void firstProductionCheckInitializesClockWithoutRetroactiveGoods() {
        Settlement settlement = settlement(SettlementSpecialization.MOUNTAIN, null);

        SettlementProductionService.ProductionReport report =
                SettlementProductionService.updateProduction(settlement, 500_000L, SETTINGS);

        assertTrue(report.initialized());
        assertTrue(settlement.isProductionInitialized());
        assertEquals(500_000L, settlement.getLastProductionGameTime());
        assertTrue(settlement.getWarehouse().getContents().values().stream()
                .allMatch(stock -> stock.stored() == 0));
    }

    private static Settlement settlement(SettlementSpecialization primary,
                                         SettlementSpecialization secondary) {
        Settlement settlement = new Settlement(UUID.randomUUID(), "Test", BlockPos.ZERO, 64);
        settlement.setSpecializations(primary, secondary);
        return settlement;
    }

    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value));
    }
}
