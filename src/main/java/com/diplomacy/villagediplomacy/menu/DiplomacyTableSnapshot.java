package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementRelationship;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.SettlementProductionService;
import com.diplomacy.villagediplomacy.diplomacy.TradeContract;
import com.diplomacy.villagediplomacy.diplomacy.VirtualWarehouse;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Immutable, server-authored data displayed by the diplomacy table screen. */
public record DiplomacyTableSnapshot(
        String settlementName,
        BlockPos center,
        int radius,
        String leaderName,
        boolean canManage,
        String primarySpecialization,
        String secondarySpecialization,
        String localWood,
        List<ProductionEntry> production,
        List<WarehouseEntry> warehouse,
        List<RelationEntry> relations,
        List<ContractEntry> contracts
) {
    private static final int MAX_NETWORK_ENTRIES = 256;
    private static final int MAX_VISIBLE_CONTRACT_HISTORY = 128;

    public DiplomacyTableSnapshot {
        settlementName = Objects.requireNonNull(settlementName, "settlementName");
        center = Objects.requireNonNull(center, "center");
        leaderName = Objects.requireNonNull(leaderName, "leaderName");
        primarySpecialization = Objects.requireNonNull(primarySpecialization, "primarySpecialization");
        secondarySpecialization = Objects.requireNonNull(secondarySpecialization, "secondarySpecialization");
        localWood = Objects.requireNonNull(localWood, "localWood");
        production = List.copyOf(production);
        warehouse = List.copyOf(warehouse);
        relations = List.copyOf(relations);
        contracts = List.copyOf(contracts);
    }

    public static DiplomacyTableSnapshot create(ServerLevel level, ServerPlayer viewer, Settlement settlement) {
        SettlementSavedData data = SettlementSavedData.get(level);
        Function<UUID, String> leaderNameResolver = leaderId -> {
            ServerPlayer onlineLeader = level.getServer().getPlayerList().getPlayer(leaderId);
            return onlineLeader == null ? shortUuid(leaderId) : onlineLeader.getGameProfile().getName();
        };
        return create(data, settlement, viewer.getUUID(), level.getGameTime(), leaderNameResolver,
                SettlementProductionService.currentSettings());
    }

    static DiplomacyTableSnapshot create(SettlementSavedData data,
                                         Settlement settlement,
                                         UUID viewerId,
                                         long gameTime,
                                         Function<UUID, String> leaderNameResolver,
                                         SettlementProductionService.ProductionSettings productionSettings) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(leaderNameResolver, "leaderNameResolver");
        Objects.requireNonNull(productionSettings, "productionSettings");

        String leaderName = settlement.getLeaderUuid() == null
                ? ""
                : leaderNameResolver.apply(settlement.getLeaderUuid());

        List<WarehouseEntry> warehouse = settlement.getWarehouse().getContents().entrySet().stream()
                .map(entry -> WarehouseEntry.from(entry.getKey(), entry.getValue()))
                .toList();

        Map<ResourceLocation, Double> tradeProduction =
                SettlementProductionService.calculateDailyTradeProduction(settlement, productionSettings);
        List<ProductionEntry> production = SettlementProductionService
                .calculateDailyGrossProduction(settlement, productionSettings)
                .entrySet().stream()
                .map(entry -> new ProductionEntry(
                        entry.getKey().toString(),
                        entry.getValue(),
                        tradeProduction.getOrDefault(entry.getKey(), 0.0D)
                ))
                .toList();

        List<RelationEntry> relations = settlement.getRelationships().stream()
                .filter(SettlementRelationship::isKnown)
                .map(relationship -> {
                    Settlement target = data.getSettlement(relationship.getTargetSettlementId());
                    String targetName = target == null ? shortUuid(relationship.getTargetSettlementId()) : target.getName();
                    String targetLeader = target == null || target.getLeaderUuid() == null
                            ? ""
                            : leaderNameResolver.apply(target.getLeaderUuid());
                    return new RelationEntry(
                            targetName,
                            target == null ? 0L : roundedDistance(settlement.getCenter(), target.getCenter()),
                            target == null ? "unassigned" : target.getPrimarySpecialization().getSerializedName(),
                            targetLeader,
                            relationship.getReputation(),
                            relationship.getDiplomacyRelation().name().toLowerCase(),
                            relationship.getTradeAgreementStatus().getSerializedName(),
                            relationship.getAllianceStatus().getSerializedName(),
                            relationship.getSuccessfulDeliveries(),
                            relationship.getFailedContracts()
                    );
                })
                .sorted(Comparator.comparing(RelationEntry::settlementName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<ContractEntry> contracts = data.getAllTradeContracts().stream()
                .filter(contract -> contract.involves(settlement.getId()))
                .sorted(Comparator.comparingLong(TradeContract::getCreatedGameTime)
                        .reversed()
                        .thenComparing(contract -> contract.getId().toString()))
                .limit(MAX_VISIBLE_CONTRACT_HISTORY)
                .map(contract -> ContractEntry.from(data, settlement, contract, gameTime, leaderNameResolver))
                .toList();

        return new DiplomacyTableSnapshot(
                settlement.getName(),
                settlement.getCenter(),
                settlement.getRadius(),
                leaderName,
                settlement.isPlayerLeader(viewerId),
                settlement.getPrimarySpecialization().getSerializedName(),
                settlement.getSecondarySpecialization().map(value -> value.getSerializedName()).orElse(""),
                settlement.getLocalWoodResource().toString(),
                production,
                warehouse,
                relations,
                contracts
        );
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(settlementName);
        buffer.writeBlockPos(center);
        buffer.writeVarInt(radius);
        buffer.writeUtf(leaderName);
        buffer.writeBoolean(canManage);
        buffer.writeUtf(primarySpecialization);
        buffer.writeUtf(secondarySpecialization);
        buffer.writeUtf(localWood);

        buffer.writeVarInt(production.size());
        production.forEach(entry -> entry.write(buffer));
        buffer.writeVarInt(warehouse.size());
        warehouse.forEach(entry -> entry.write(buffer));
        buffer.writeVarInt(relations.size());
        relations.forEach(entry -> entry.write(buffer));
        buffer.writeVarInt(contracts.size());
        contracts.forEach(entry -> entry.write(buffer));
    }

    public static DiplomacyTableSnapshot read(FriendlyByteBuf buffer) {
        String settlementName = buffer.readUtf();
        BlockPos center = buffer.readBlockPos();
        int radius = buffer.readVarInt();
        String leaderName = buffer.readUtf();
        boolean canManage = buffer.readBoolean();
        String primary = buffer.readUtf();
        String secondary = buffer.readUtf();
        String localWood = buffer.readUtf();

        List<ProductionEntry> production = readEntries(buffer, ProductionEntry::read);
        List<WarehouseEntry> warehouse = readEntries(buffer, WarehouseEntry::read);
        List<RelationEntry> relations = readEntries(buffer, RelationEntry::read);
        List<ContractEntry> contracts = readEntries(buffer, ContractEntry::read);
        return new DiplomacyTableSnapshot(settlementName, center, radius, leaderName, canManage,
                primary, secondary, localWood, production, warehouse, relations, contracts);
    }

    private static <T> List<T> readEntries(FriendlyByteBuf buffer, Function<FriendlyByteBuf, T> reader) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_NETWORK_ENTRIES) {
            throw new IllegalArgumentException("Invalid diplomacy table entry count: " + count);
        }
        List<T> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(reader.apply(buffer));
        }
        return result;
    }

    private static String settlementName(SettlementSavedData data, UUID settlementId) {
        Settlement settlement = data.getSettlement(settlementId);
        return settlement == null ? shortUuid(settlementId) : settlement.getName();
    }

    private static String shortUuid(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static long roundedDistance(BlockPos first, BlockPos second) {
        return Math.round(Math.sqrt(first.distSqr(second)));
    }

    public record ProductionEntry(String itemId, double grossPerDay, double tradePerDay) {
        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(itemId);
            buffer.writeDouble(grossPerDay);
            buffer.writeDouble(tradePerDay);
        }

        static ProductionEntry read(FriendlyByteBuf buffer) {
            return new ProductionEntry(buffer.readUtf(), buffer.readDouble(), buffer.readDouble());
        }
    }

    public record WarehouseEntry(String itemId, int stored, int reserved, int capacity) {
        static WarehouseEntry from(ResourceLocation itemId, VirtualWarehouse.StockView stock) {
            return new WarehouseEntry(itemId.toString(), stock.stored(), stock.reserved(), stock.capacity());
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(itemId);
            buffer.writeVarInt(stored);
            buffer.writeVarInt(reserved);
            buffer.writeVarInt(capacity);
        }

        static WarehouseEntry read(FriendlyByteBuf buffer) {
            return new WarehouseEntry(buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record RelationEntry(String settlementName,
                                long distanceBlocks,
                                String specialization,
                                String leaderName,
                                int reputation,
                                String relation,
                                String tradeAgreement,
                                String alliance,
                                int successfulDeliveries,
                                int failedContracts) {
        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(settlementName);
            buffer.writeLong(distanceBlocks);
            buffer.writeUtf(specialization);
            buffer.writeUtf(leaderName);
            buffer.writeInt(reputation);
            buffer.writeUtf(relation);
            buffer.writeUtf(tradeAgreement);
            buffer.writeUtf(alliance);
            buffer.writeVarInt(successfulDeliveries);
            buffer.writeVarInt(failedContracts);
        }

        static RelationEntry read(FriendlyByteBuf buffer) {
            return new RelationEntry(buffer.readUtf(), buffer.readLong(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readInt(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record ContractEntry(UUID contractId,
                                String sourceName,
                                String destinationName,
                                String type,
                                String status,
                                String carrierName,
                                boolean trial,
                                long remainingTicks,
                                int rewardEmeralds,
                                long distanceBlocks,
                                int successfulDeliveries,
                                String tradeAgreement,
                                List<GoodsEntry> goods,
                                List<GoodsEntry> returnGoods) {
        public ContractEntry {
            goods = List.copyOf(goods);
            returnGoods = List.copyOf(returnGoods);
        }

        static ContractEntry from(SettlementSavedData data,
                                  Settlement viewedSettlement,
                                  TradeContract contract,
                                  long gameTime,
                                  Function<UUID, String> playerNameResolver) {
            List<GoodsEntry> goods = contract.getRequiredGoods().entrySet().stream()
                    .map(entry -> new GoodsEntry(
                            entry.getKey().toString(),
                            entry.getValue(),
                            contract.getIssuedAmount(entry.getKey()),
                            contract.getDeliveredAmount(entry.getKey())
                    ))
                    .toList();
            List<GoodsEntry> returnGoods = contract.getReturnGoods().entrySet().stream()
                    .map(entry -> new GoodsEntry(
                            entry.getKey().toString(),
                            entry.getValue(),
                            contract.getReturnIssuedAmount(entry.getKey()),
                            contract.getReturnDeliveredAmount(entry.getKey())
                    ))
                    .toList();
            Settlement source = data.getSettlement(contract.getSourceSettlementId());
            Settlement destination = data.getSettlement(contract.getDestinationSettlementId());
            UUID otherId = contract.getSourceSettlementId().equals(viewedSettlement.getId())
                    ? contract.getDestinationSettlementId()
                    : contract.getSourceSettlementId();
            SettlementRelationship relationship = viewedSettlement.findRelationship(otherId).orElse(null);
            return new ContractEntry(
                    contract.getId(),
                    settlementName(data, contract.getSourceSettlementId()),
                    settlementName(data, contract.getDestinationSettlementId()),
                    contract.getType().getSerializedName(),
                    contract.getStatus().getSerializedName(),
                    contract.getCarrierId() == null ? "" : playerNameResolver.apply(contract.getCarrierId()),
                    contract.isTrial(),
                    Math.max(0L, contract.getDeadlineGameTime() - gameTime),
                    contract.getRewardEmeralds(),
                    source == null || destination == null
                            ? 0L
                            : roundedDistance(source.getCenter(), destination.getCenter()),
                    relationship == null ? 0 : relationship.getSuccessfulDeliveries(),
                    relationship == null
                            ? "none"
                            : relationship.getTradeAgreementStatus().getSerializedName(),
                    goods,
                    returnGoods
            );
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(contractId);
            buffer.writeUtf(sourceName);
            buffer.writeUtf(destinationName);
            buffer.writeUtf(type);
            buffer.writeUtf(status);
            buffer.writeUtf(carrierName);
            buffer.writeBoolean(trial);
            buffer.writeLong(remainingTicks);
            buffer.writeVarInt(rewardEmeralds);
            buffer.writeLong(distanceBlocks);
            buffer.writeVarInt(successfulDeliveries);
            buffer.writeUtf(tradeAgreement);
            buffer.writeVarInt(goods.size());
            goods.forEach(entry -> entry.write(buffer));
            buffer.writeVarInt(returnGoods.size());
            returnGoods.forEach(entry -> entry.write(buffer));
        }

        static ContractEntry read(FriendlyByteBuf buffer) {
            UUID contractId = buffer.readUUID();
            String source = buffer.readUtf();
            String destination = buffer.readUtf();
            String type = buffer.readUtf();
            String status = buffer.readUtf();
            String carrier = buffer.readUtf();
            boolean trial = buffer.readBoolean();
            long remaining = buffer.readLong();
            int reward = buffer.readVarInt();
            long distance = buffer.readLong();
            int deliveries = buffer.readVarInt();
            String agreement = buffer.readUtf();
            List<GoodsEntry> goods = readEntries(buffer, GoodsEntry::read);
            List<GoodsEntry> returnGoods = readEntries(buffer, GoodsEntry::read);
            return new ContractEntry(contractId, source, destination, type, status, carrier,
                    trial, remaining, reward, distance, deliveries, agreement, goods, returnGoods);
        }
    }

    public record GoodsEntry(String itemId, int required, int issued, int delivered) {
        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(itemId);
            buffer.writeVarInt(required);
            buffer.writeVarInt(issued);
            buffer.writeVarInt(delivered);
        }

        static GoodsEntry read(FriendlyByteBuf buffer) {
            return new GoodsEntry(buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
    }
}
