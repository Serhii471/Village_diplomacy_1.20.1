package com.diplomacy.villagediplomacy.gameplay;

import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.TradeContract;
import com.diplomacy.villagediplomacy.diplomacy.TradeContractService;
import com.diplomacy.villagediplomacy.diplomacy.TradeContractStatus;
import com.diplomacy.villagediplomacy.diplomacy.TradeContractType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bridges persisted contracts with ordinary player inventory stacks. The
 * generic inventory port keeps transfer accounting independently testable.
 */
public final class TradeHandoverService {
    private static final ResourceLocation EMERALD = id("minecraft:emerald");

    private TradeHandoverService() {
    }

    public static HandoverSummary handleAtSettlement(SettlementSavedData data,
                                                     UUID settlementId,
                                                     ServerPlayer player,
                                                     long currentGameTime) {
        PlayerInventoryPort inventory = new PlayerInventoryPort(player);
        return handleAtSettlement(
                data,
                settlementId,
                player.getUUID(),
                currentGameTime,
                inventory,
                TradeContractService.currentRules()
        );
    }

    static HandoverSummary handleAtSettlement(SettlementSavedData data,
                                              UUID settlementId,
                                              UUID carrierId,
                                              long currentGameTime,
                                              TradeInventoryPort inventory,
                                              TradeContractService.ContractRules rules) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(settlementId, "settlementId");
        Objects.requireNonNull(carrierId, "carrierId");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(rules, "rules");

        TradeContractService.expireDueContracts(data, currentGameTime, rules);
        var contracts = data.getTradeContractsForHandover(carrierId, settlementId);
        if (contracts.isEmpty()) {
            return HandoverSummary.empty();
        }

        HandoverSummary result = HandoverSummary.empty();
        for (TradeContract contract : contracts) {
            result = result.merge(handleContract(
                    data, settlementId, carrierId, currentGameTime, inventory, rules, contract));
        }
        return result;
    }

    public static HandoverSummary handleContractAtSettlement(SettlementSavedData data,
                                                             UUID settlementId,
                                                             UUID contractId,
                                                             ServerPlayer player,
                                                             long currentGameTime) {
        return handleContractAtSettlement(
                data, settlementId, contractId, player.getUUID(), currentGameTime,
                new PlayerInventoryPort(player), TradeContractService.currentRules());
    }

    static HandoverSummary handleContractAtSettlement(SettlementSavedData data,
                                                      UUID settlementId,
                                                      UUID contractId,
                                                      UUID carrierId,
                                                      long currentGameTime,
                                                      TradeInventoryPort inventory,
                                                      TradeContractService.ContractRules rules) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(settlementId, "settlementId");
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(carrierId, "carrierId");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(rules, "rules");
        TradeContractService.expireDueContracts(data, currentGameTime, rules);
        TradeContract contract = data.getTradeContract(contractId);
        if (contract == null || !carrierId.equals(contract.getCarrierId())
                || !isAvailableAt(contract, settlementId)) {
            return HandoverSummary.empty();
        }
        return handleContract(data, settlementId, carrierId, currentGameTime, inventory, rules, contract);
    }

    private static HandoverSummary handleContract(SettlementSavedData data,
                                                  UUID settlementId,
                                                  UUID carrierId,
                                                  long currentGameTime,
                                                  TradeInventoryPort inventory,
                                                  TradeContractService.ContractRules rules,
                                                  TradeContract contract) {
        Map<ResourceLocation, Integer> issued = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> delivered = new LinkedHashMap<>();
        int completedContracts = 0;
        int rewardEmeralds = 0;

        if (contract.getStatus().isTerminal()) {
            int reward = TradeContractService.claimReward(data, contract.getId(), carrierId);
            if (reward > 0) {
                giveOrDrop(inventory, EMERALD, reward);
                rewardEmeralds = reward;
                completedContracts = 1;
            }
            return new HandoverSummary(true, issued, delivered, completedContracts, rewardEmeralds);
        }

        boolean atSource = contract.getSourceSettlementId().equals(settlementId);
        if (atSource) {
            contract.getRequiredGoods().keySet().forEach(itemId -> {
                int transferred = issueIntoInventory(
                        data, contract, carrierId, itemId, currentGameTime, inventory, rules);
                if (transferred > 0) {
                    issued.merge(itemId, transferred, Integer::sum);
                }
            });
            if (contract.getType() == TradeContractType.EXCHANGE && contract.isOutboundDeliveryComplete()) {
                for (ResourceLocation itemId : contract.getReturnGoods().keySet()) {
                    int transferred = deliverReturnFromInventory(
                            data, contract, carrierId, itemId, currentGameTime, inventory, rules);
                    if (transferred > 0) {
                        delivered.merge(itemId, transferred, Integer::sum);
                    }
                }
            }
        } else {
            for (ResourceLocation itemId : contract.getRequiredGoods().keySet()) {
                int transferred = deliverFromInventory(
                        data, contract, carrierId, itemId, currentGameTime, inventory, rules);
                if (transferred > 0) {
                    delivered.merge(itemId, transferred, Integer::sum);
                }
            }
            if (contract.getType() == TradeContractType.EXCHANGE && contract.isOutboundDeliveryComplete()) {
                contract.getReturnGoods().keySet().forEach(itemId -> {
                    int transferred = issueReturnIntoInventory(
                            data, contract, carrierId, itemId, currentGameTime, inventory, rules);
                    if (transferred > 0) {
                        issued.merge(itemId, transferred, Integer::sum);
                    }
                });
            }
        }

        if (contract.getStatus() == TradeContractStatus.COMPLETED) {
            completedContracts = 1;
            int reward = TradeContractService.claimReward(data, contract.getId(), carrierId);
            if (reward > 0) {
                giveOrDrop(inventory, EMERALD, reward);
                rewardEmeralds = reward;
            }
        }
        return new HandoverSummary(true, issued, delivered, completedContracts, rewardEmeralds);
    }

    private static boolean isAvailableAt(TradeContract contract, UUID settlementId) {
        return contract.getStatus() == TradeContractStatus.ACTIVE && contract.involves(settlementId)
                || contract.getStatus() == TradeContractStatus.COMPLETED
                && !contract.isRewardClaimed()
                && contract.getRewardSettlementId().equals(settlementId);
    }

    public static void sendResult(ServerPlayer player, HandoverSummary summary) {
        if (!summary.handled()) {
            player.displayClientMessage(
                    Component.translatable("message.villagediplomacy.handover.unavailable"), true);
            return;
        }
        if (summary.totalIssued() > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.villagediplomacy.handover.issued", summary.totalIssued()).withStyle(ChatFormatting.GOLD));
        }
        if (summary.totalDelivered() > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.villagediplomacy.handover.delivered", summary.totalDelivered()).withStyle(ChatFormatting.GREEN));
        }
        if (summary.completedContracts() > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.villagediplomacy.handover.completed",
                    summary.completedContracts(), summary.rewardEmeralds()).withStyle(ChatFormatting.GREEN));
        }
        if (summary.totalIssued() == 0 && summary.totalDelivered() == 0
                && summary.completedContracts() == 0) {
            player.displayClientMessage(Component.translatable("message.villagediplomacy.handover.nothing"), true);
        }
    }

    private static int issueIntoInventory(SettlementSavedData data,
                                          TradeContract contract,
                                          UUID carrierId,
                                          ResourceLocation itemId,
                                          long currentGameTime,
                                          TradeInventoryPort inventory,
                                          TradeContractService.ContractRules rules) {
        int remaining = contract.getRemainingToIssue(itemId);
        if (remaining <= 0) {
            return 0;
        }

        int inserted = inventory.insert(itemId, remaining);
        if (inserted <= 0) {
            return 0;
        }

        try {
            int issued = TradeContractService.issueGoods(
                    data,
                    contract.getId(),
                    carrierId,
                    itemId,
                    inserted,
                    currentGameTime,
                    rules
            );
            if (issued < inserted) {
                restoreWarehouseMismatch(inventory, itemId, inserted - issued);
            }
            return issued;
        } catch (RuntimeException exception) {
            restoreWarehouseMismatch(inventory, itemId, inserted);
            throw exception;
        }
    }

    private static int deliverFromInventory(SettlementSavedData data,
                                            TradeContract contract,
                                            UUID carrierId,
                                            ResourceLocation itemId,
                                            long currentGameTime,
                                            TradeInventoryPort inventory,
                                            TradeContractService.ContractRules rules) {
        int requested = Math.min(
                contract.getRemainingToDeliver(itemId),
                inventory.count(itemId)
        );
        if (requested <= 0) {
            return 0;
        }

        int extracted = inventory.extract(itemId, requested);
        if (extracted <= 0) {
            return 0;
        }

        try {
            TradeContractService.DeliveryResult result = TradeContractService.recordDelivery(
                    data,
                    contract.getId(),
                    carrierId,
                    itemId,
                    extracted,
                    currentGameTime,
                    rules
            );
            if (result.acceptedAmount() < extracted) {
                giveOrDrop(inventory, itemId, extracted - result.acceptedAmount());
            }
            return result.acceptedAmount();
        } catch (RuntimeException exception) {
            giveOrDrop(inventory, itemId, extracted);
            throw exception;
        }
    }

    private static int issueReturnIntoInventory(SettlementSavedData data,
                                                TradeContract contract,
                                                UUID carrierId,
                                                ResourceLocation itemId,
                                                long currentGameTime,
                                                TradeInventoryPort inventory,
                                                TradeContractService.ContractRules rules) {
        int remaining = contract.getRemainingReturnToIssue(itemId);
        if (remaining <= 0) {
            return 0;
        }
        int inserted = inventory.insert(itemId, remaining);
        if (inserted <= 0) {
            return 0;
        }
        try {
            int issued = TradeContractService.issueReturnGoods(
                    data, contract.getId(), carrierId, itemId, inserted, currentGameTime, rules);
            if (issued < inserted) {
                restoreWarehouseMismatch(inventory, itemId, inserted - issued);
            }
            return issued;
        } catch (RuntimeException exception) {
            restoreWarehouseMismatch(inventory, itemId, inserted);
            throw exception;
        }
    }

    private static int deliverReturnFromInventory(SettlementSavedData data,
                                                  TradeContract contract,
                                                  UUID carrierId,
                                                  ResourceLocation itemId,
                                                  long currentGameTime,
                                                  TradeInventoryPort inventory,
                                                  TradeContractService.ContractRules rules) {
        int requested = Math.min(
                contract.getRemainingReturnToDeliver(itemId),
                inventory.count(itemId)
        );
        if (requested <= 0) {
            return 0;
        }
        int extracted = inventory.extract(itemId, requested);
        if (extracted <= 0) {
            return 0;
        }
        try {
            TradeContractService.DeliveryResult result = TradeContractService.recordReturnDelivery(
                    data, contract.getId(), carrierId, itemId, extracted, currentGameTime, rules);
            if (result.acceptedAmount() < extracted) {
                giveOrDrop(inventory, itemId, extracted - result.acceptedAmount());
            }
            return result.acceptedAmount();
        } catch (RuntimeException exception) {
            giveOrDrop(inventory, itemId, extracted);
            throw exception;
        }
    }

    private static void restoreWarehouseMismatch(TradeInventoryPort inventory,
                                                 ResourceLocation itemId,
                                                 int amount) {
        int removed = inventory.extract(itemId, amount);
        if (removed != amount) {
            throw new IllegalStateException("Could not roll back an incomplete warehouse issue");
        }
    }

    private static void giveOrDrop(TradeInventoryPort inventory,
                                   ResourceLocation itemId,
                                   int amount) {
        int inserted = inventory.insert(itemId, amount);
        int remainder = amount - inserted;
        if (remainder > 0) {
            inventory.drop(itemId, remainder);
        }
    }

    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value));
    }

    interface TradeInventoryPort {
        int count(ResourceLocation itemId);

        int insert(ResourceLocation itemId, int amount);

        int extract(ResourceLocation itemId, int amount);

        void drop(ResourceLocation itemId, int amount);
    }

    public record HandoverSummary(boolean handled,
                                  Map<ResourceLocation, Integer> issued,
                                  Map<ResourceLocation, Integer> delivered,
                                  int completedContracts,
                                  int rewardEmeralds) {
        public HandoverSummary {
            issued = Map.copyOf(issued);
            delivered = Map.copyOf(delivered);
        }

        private static HandoverSummary empty() {
            return new HandoverSummary(false, Map.of(), Map.of(), 0, 0);
        }

        private HandoverSummary merge(HandoverSummary other) {
            Map<ResourceLocation, Integer> mergedIssued = new LinkedHashMap<>(issued);
            other.issued.forEach((itemId, amount) -> mergedIssued.merge(itemId, amount, Integer::sum));
            Map<ResourceLocation, Integer> mergedDelivered = new LinkedHashMap<>(delivered);
            other.delivered.forEach((itemId, amount) -> mergedDelivered.merge(itemId, amount, Integer::sum));
            return new HandoverSummary(
                    handled || other.handled,
                    mergedIssued,
                    mergedDelivered,
                    completedContracts + other.completedContracts,
                    rewardEmeralds + other.rewardEmeralds
            );
        }

        public int totalIssued() {
            return issued.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int totalDelivered() {
            return delivered.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private static final class PlayerInventoryPort implements TradeInventoryPort {
        private final ServerPlayer player;

        private PlayerInventoryPort(ServerPlayer player) {
            this.player = Objects.requireNonNull(player, "player");
        }

        @Override
        public int count(ResourceLocation itemId) {
            return player.getInventory().countItem(resolveItem(itemId));
        }

        @Override
        public int insert(ResourceLocation itemId, int amount) {
            if (amount <= 0) {
                return 0;
            }
            Item item = resolveItem(itemId);
            int remaining = amount;
            while (remaining > 0) {
                int stackSize = Math.min(remaining, item.getMaxStackSize());
                ItemStack stack = new ItemStack(item, stackSize);
                player.getInventory().add(stack);
                int inserted = stackSize - stack.getCount();
                remaining -= inserted;
                if (inserted == 0 || !stack.isEmpty()) {
                    break;
                }
            }
            return amount - remaining;
        }

        @Override
        public int extract(ResourceLocation itemId, int amount) {
            if (amount <= 0) {
                return 0;
            }
            Item item = resolveItem(itemId);
            Inventory inventory = player.getInventory();
            int remaining = amount;
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.is(item)) {
                    continue;
                }
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
            if (remaining != amount) {
                inventory.setChanged();
            }
            return amount - remaining;
        }

        @Override
        public void drop(ResourceLocation itemId, int amount) {
            Item item = resolveItem(itemId);
            int remaining = amount;
            while (remaining > 0) {
                int stackSize = Math.min(remaining, item.getMaxStackSize());
                player.drop(new ItemStack(item, stackSize), false);
                remaining -= stackSize;
            }
        }

        private static Item resolveItem(ResourceLocation itemId) {
            Item item = BuiltInRegistries.ITEM.getOptional(Objects.requireNonNull(itemId, "itemId"))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown item: " + itemId));
            if (item == Items.AIR) {
                throw new IllegalArgumentException("Air cannot be transferred by a trade contract");
            }
            return item;
        }
    }
}
