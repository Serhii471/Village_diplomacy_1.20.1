package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.TradeContract;
import com.diplomacy.villagediplomacy.diplomacy.TradeContractStatus;
import com.diplomacy.villagediplomacy.diplomacy.TradeContractType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;

public record TradeHandoverSnapshot(
        UUID settlementId,
        String settlementName,
        List<ContractEntry> contracts
) {
    private static final int MAX_CONTRACTS = 64;
    private static final int MAX_GOODS = 64;

    public TradeHandoverSnapshot {
        settlementId = Objects.requireNonNull(settlementId, "settlementId");
        settlementName = Objects.requireNonNull(settlementName, "settlementName");
        contracts = List.copyOf(contracts);
        if (contracts.size() > MAX_CONTRACTS) {
            throw new IllegalArgumentException("Too many handover contracts");
        }
    }

    public static TradeHandoverSnapshot create(SettlementSavedData data,
                                               Settlement settlement,
                                               ServerPlayer player,
                                               long gameTime) {
        return create(data, settlement, player.getUUID(), gameTime, itemId -> {
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            return item == null ? 0 : player.getInventory().countItem(item);
        });
    }

    static TradeHandoverSnapshot create(SettlementSavedData data,
                                        Settlement settlement,
                                        UUID carrierId,
                                        long gameTime,
                                        ToIntFunction<ResourceLocation> inventoryCount) {
        List<ContractEntry> contracts = data.getTradeContractsForHandover(carrierId, settlement.getId()).stream()
                .limit(MAX_CONTRACTS)
                .map(contract -> ContractEntry.from(data, settlement, contract, gameTime, inventoryCount))
                .toList();
        return new TradeHandoverSnapshot(settlement.getId(), settlement.getName(), contracts);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(settlementId);
        buffer.writeUtf(settlementName);
        buffer.writeVarInt(contracts.size());
        contracts.forEach(contract -> contract.write(buffer));
    }

    public static TradeHandoverSnapshot read(FriendlyByteBuf buffer) {
        UUID settlementId = buffer.readUUID();
        String settlementName = buffer.readUtf();
        int count = readCount(buffer, MAX_CONTRACTS);
        List<ContractEntry> contracts = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            contracts.add(ContractEntry.read(buffer));
        }
        return new TradeHandoverSnapshot(settlementId, settlementName, contracts);
    }

    private static int readCount(FriendlyByteBuf buffer, int maximum) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid handover entry count: " + count);
        }
        return count;
    }

    public enum Phase {
        OUTBOUND_PICKUP,
        OUTBOUND_DELIVERY,
        RETURN_PICKUP,
        RETURN_DELIVERY,
        CLAIM_REWARD
    }

    public record ContractEntry(
            UUID contractId,
            String sourceName,
            String destinationName,
            String type,
            Phase phase,
            long remainingTicks,
            int rewardEmeralds,
            boolean actionable,
            List<GoodsEntry> outboundGoods,
            List<GoodsEntry> returnGoods
    ) {
        public ContractEntry {
            contractId = Objects.requireNonNull(contractId, "contractId");
            sourceName = Objects.requireNonNull(sourceName, "sourceName");
            destinationName = Objects.requireNonNull(destinationName, "destinationName");
            type = Objects.requireNonNull(type, "type");
            phase = Objects.requireNonNull(phase, "phase");
            outboundGoods = List.copyOf(outboundGoods);
            returnGoods = List.copyOf(returnGoods);
            if (outboundGoods.size() > MAX_GOODS || returnGoods.size() > MAX_GOODS) {
                throw new IllegalArgumentException("Too many handover goods");
            }
        }

        static ContractEntry from(SettlementSavedData data,
                                  Settlement settlement,
                                  TradeContract contract,
                                  long gameTime,
                                  ToIntFunction<ResourceLocation> inventoryCount) {
            boolean atSource = contract.getSourceSettlementId().equals(settlement.getId());
            Phase phase = phase(contract, atSource);
            List<GoodsEntry> outbound = contract.getRequiredGoods().entrySet().stream()
                    .map(entry -> new GoodsEntry(
                            entry.getKey().toString(),
                            entry.getValue(),
                            contract.getIssuedAmount(entry.getKey()),
                            contract.getDeliveredAmount(entry.getKey()),
                            inventoryCount.applyAsInt(entry.getKey())
                    ))
                    .toList();
            List<GoodsEntry> returning = contract.getReturnGoods().entrySet().stream()
                    .map(entry -> new GoodsEntry(
                            entry.getKey().toString(),
                            entry.getValue(),
                            contract.getReturnIssuedAmount(entry.getKey()),
                            contract.getReturnDeliveredAmount(entry.getKey()),
                            inventoryCount.applyAsInt(entry.getKey())
                    ))
                    .toList();
            boolean actionable = switch (phase) {
                case OUTBOUND_PICKUP -> contract.getRequiredGoods().keySet().stream()
                        .anyMatch(itemId -> contract.getRemainingToIssue(itemId) > 0);
                case OUTBOUND_DELIVERY -> contract.getRequiredGoods().keySet().stream()
                        .anyMatch(itemId -> contract.getRemainingToDeliver(itemId) > 0
                                && inventoryCount.applyAsInt(itemId) > 0);
                case RETURN_PICKUP -> contract.getReturnGoods().keySet().stream()
                        .anyMatch(itemId -> contract.getRemainingReturnToIssue(itemId) > 0);
                case RETURN_DELIVERY -> contract.getReturnGoods().keySet().stream()
                        .anyMatch(itemId -> contract.getRemainingReturnToDeliver(itemId) > 0
                                && inventoryCount.applyAsInt(itemId) > 0);
                case CLAIM_REWARD -> true;
            };
            return new ContractEntry(
                    contract.getId(),
                    settlementName(data, contract.getSourceSettlementId()),
                    settlementName(data, contract.getDestinationSettlementId()),
                    contract.getType().getSerializedName(),
                    phase,
                    Math.max(0L, contract.getDeadlineGameTime() - gameTime),
                    contract.getRewardEmeralds(),
                    actionable,
                    outbound,
                    returning
            );
        }

        private static Phase phase(TradeContract contract, boolean atSource) {
            if (contract.getStatus() == TradeContractStatus.COMPLETED) {
                return Phase.CLAIM_REWARD;
            }
            if (!contract.isOutboundDeliveryComplete()) {
                return atSource ? Phase.OUTBOUND_PICKUP : Phase.OUTBOUND_DELIVERY;
            }
            if (contract.getType() == TradeContractType.EXCHANGE) {
                return atSource ? Phase.RETURN_DELIVERY : Phase.RETURN_PICKUP;
            }
            return Phase.CLAIM_REWARD;
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(contractId);
            buffer.writeUtf(sourceName);
            buffer.writeUtf(destinationName);
            buffer.writeUtf(type);
            buffer.writeEnum(phase);
            buffer.writeLong(remainingTicks);
            buffer.writeVarInt(rewardEmeralds);
            buffer.writeBoolean(actionable);
            writeGoods(buffer, outboundGoods);
            writeGoods(buffer, returnGoods);
        }

        static ContractEntry read(FriendlyByteBuf buffer) {
            UUID contractId = buffer.readUUID();
            String sourceName = buffer.readUtf();
            String destinationName = buffer.readUtf();
            String type = buffer.readUtf();
            Phase phase = buffer.readEnum(Phase.class);
            long remainingTicks = buffer.readLong();
            int rewardEmeralds = buffer.readVarInt();
            boolean actionable = buffer.readBoolean();
            return new ContractEntry(contractId, sourceName, destinationName, type, phase,
                    remainingTicks, rewardEmeralds, actionable, readGoods(buffer), readGoods(buffer));
        }

        private static void writeGoods(FriendlyByteBuf buffer, List<GoodsEntry> goods) {
            buffer.writeVarInt(goods.size());
            goods.forEach(entry -> entry.write(buffer));
        }

        private static List<GoodsEntry> readGoods(FriendlyByteBuf buffer) {
            int count = readCount(buffer, MAX_GOODS);
            List<GoodsEntry> goods = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                goods.add(GoodsEntry.read(buffer));
            }
            return goods;
        }

        private static String settlementName(SettlementSavedData data, UUID id) {
            Settlement settlement = data.getSettlement(id);
            return settlement == null ? id.toString().substring(0, 8) : settlement.getName();
        }
    }

    public record GoodsEntry(String itemId, int required, int issued, int delivered, int inventory) {
        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(itemId);
            buffer.writeVarInt(required);
            buffer.writeVarInt(issued);
            buffer.writeVarInt(delivered);
            buffer.writeVarInt(inventory);
        }

        static GoodsEntry read(FriendlyByteBuf buffer) {
            return new GoodsEntry(buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt());
        }
    }
}
