package com.diplomacy.villagediplomacy.diplomacy;

import com.diplomacy.villagediplomacy.config.VillageDiplomacyConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-authoritative lifecycle for trial delivery contracts. */
public final class TradeContractService {
    private TradeContractService() {
    }

    public static TradeContract createTrialDeliveryOffer(SettlementSavedData data,
                                                         UUID sourceSettlementId,
                                                         UUID destinationSettlementId,
                                                         Map<ResourceLocation, Integer> requiredGoods,
                                                         int rewardEmeralds,
                                                         long currentGameTime) {
        return createTrialDeliveryOffer(
                data,
                sourceSettlementId,
                destinationSettlementId,
                requiredGoods,
                rewardEmeralds,
                currentGameTime,
                currentRules()
        );
    }

    public static TradeContract createTrialDeliveryOffer(SettlementSavedData data,
                                                         UUID sourceSettlementId,
                                                         UUID destinationSettlementId,
                                                         Map<ResourceLocation, Integer> requiredGoods,
                                                         int rewardEmeralds,
                                                         long currentGameTime,
                                                         ContractRules rules) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(rules, "rules");
        Settlement source = requireSettlement(data, sourceSettlementId);
        Settlement destination = requireOtherSettlement(data, destinationSettlementId, sourceSettlementId);
        if (!isDiscovered(source, destination) && !isDiscovered(destination, source)) {
            throw new IllegalStateException("At least one settlement must have discovered the other");
        }

        long createdGameTime = Math.max(0L, currentGameTime);
        long deadlineGameTime = addClamped(createdGameTime, rules.trialDurationTicks());
        TradeContract contract = new TradeContract(
                UUID.randomUUID(),
                source.getId(),
                destination.getId(),
                requiredGoods,
                true,
                createdGameTime,
                deadlineGameTime,
                rewardEmeralds
        );
        data.registerTradeContract(contract);
        return contract;
    }

    public static ActivationResult acceptContract(SettlementSavedData data,
                                                  UUID contractId,
                                                  UUID carrierId,
                                                  long currentGameTime) {
        UUID safeCarrierId = Objects.requireNonNull(carrierId, "carrierId");
        TradeContract contract = requireContract(data, contractId);
        if (contract.getStatus() != TradeContractStatus.OFFERED) {
            throw new IllegalStateException("Only an offered contract can be accepted");
        }
        if (isPastDeadline(contract, currentGameTime)) {
            contract.expire(currentGameTime);
            return ActivationResult.EXPIRED;
        }

        Settlement source = requireSettlement(data, contract.getSourceSettlementId());
        boolean allAvailable = contract.getRequiredGoods().entrySet().stream().allMatch(entry ->
                source.getWarehouse().getStock(entry.getKey()).available() >= entry.getValue());
        if (!allAvailable) {
            return ActivationResult.INSUFFICIENT_STOCK;
        }

        ArrayList<Map.Entry<ResourceLocation, Integer>> reserved = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> entry : contract.getRequiredGoods().entrySet()) {
            if (!source.getWarehouse().tryReserve(entry.getKey(), entry.getValue())) {
                reserved.forEach(previous ->
                        source.getWarehouse().releaseReservation(previous.getKey(), previous.getValue()));
                return ActivationResult.INSUFFICIENT_STOCK;
            }
            reserved.add(entry);
        }
        try {
            contract.activate(safeCarrierId, currentGameTime);
        } catch (RuntimeException exception) {
            reserved.forEach(previous ->
                    source.getWarehouse().releaseReservation(previous.getKey(), previous.getValue()));
            throw exception;
        }
        return ActivationResult.ACTIVATED;
    }

    /**
     * Removes reserved stock when the server interaction layer successfully
     * hands ordinary item stacks to the assigned carrier.
     */
    public static int issueGoods(SettlementSavedData data,
                                 UUID contractId,
                                 UUID carrierId,
                                 ResourceLocation itemId,
                                 int requestedAmount,
                                 long currentGameTime) {
        requirePositive(requestedAmount, "requestedAmount");
        TradeContract contract = requireActiveCarrierContract(data, contractId, carrierId, currentGameTime);
        if (contract == null) {
            return 0;
        }
        requireContractItem(contract, itemId);
        int amount = Math.min(requestedAmount, contract.getRemainingToIssue(itemId));
        if (amount <= 0) {
            return 0;
        }

        Settlement source = requireSettlement(data, contract.getSourceSettlementId());
        int issued = source.getWarehouse().takeReserved(itemId, amount);
        if (issued > 0) {
            contract.recordIssued(itemId, issued);
        }
        return issued;
    }

    /**
     * Records items already validated and removed from the carrier inventory by
     * the server interaction layer. Partial delivery is supported.
     */
    public static DeliveryResult recordDelivery(SettlementSavedData data,
                                                UUID contractId,
                                                UUID carrierId,
                                                ResourceLocation itemId,
                                                int offeredAmount,
                                                long currentGameTime) {
        return recordDelivery(data, contractId, carrierId, itemId, offeredAmount,
                currentGameTime, currentRules());
    }

    public static DeliveryResult recordDelivery(SettlementSavedData data,
                                                UUID contractId,
                                                UUID carrierId,
                                                ResourceLocation itemId,
                                                int offeredAmount,
                                                long currentGameTime,
                                                ContractRules rules) {
        requirePositive(offeredAmount, "offeredAmount");
        Objects.requireNonNull(rules, "rules");
        TradeContract contract = requireActiveCarrierContract(data, contractId, carrierId, currentGameTime, rules);
        if (contract == null) {
            return new DeliveryResult(0, false);
        }
        requireContractItem(contract, itemId);
        int accepted = Math.min(offeredAmount, contract.getRemainingToDeliver(itemId));
        if (accepted <= 0) {
            return new DeliveryResult(0, false);
        }

        contract.recordDelivered(itemId, accepted);
        if (!contract.isDeliveryComplete()) {
            return new DeliveryResult(accepted, false);
        }

        releaseRemainingReservations(data, contract);
        contract.complete(currentGameTime);
        data.recordSuccessfulDeliveryBetween(
                contract.getSourceSettlementId(),
                contract.getDestinationSettlementId(),
                currentGameTime
        );
        data.adjustReputationBetween(
                contract.getSourceSettlementId(),
                contract.getDestinationSettlementId(),
                rules.successfulDeliveryReputation()
        );
        return new DeliveryResult(accepted, true);
    }

    /** Returns the emerald reward exactly once; zero means no payable reward. */
    public static int claimReward(SettlementSavedData data, UUID contractId, UUID carrierId) {
        TradeContract contract = requireContract(data, contractId);
        if (contract.getStatus() != TradeContractStatus.COMPLETED
                || contract.isRewardClaimed()
                || !Objects.equals(contract.getCarrierId(), carrierId)) {
            return 0;
        }
        contract.claimReward();
        return contract.getRewardEmeralds();
    }

    public static int expireDueContracts(SettlementSavedData data, long currentGameTime) {
        return expireDueContracts(data, currentGameTime, currentRules());
    }

    public static int expireDueContracts(SettlementSavedData data,
                                         long currentGameTime,
                                         ContractRules rules) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(rules, "rules");
        int expired = 0;
        for (TradeContract contract : data.getAllTradeContracts()) {
            if ((contract.getStatus() == TradeContractStatus.OFFERED
                    || contract.getStatus() == TradeContractStatus.ACTIVE)
                    && isPastDeadline(contract, currentGameTime)) {
                expireContract(data, contract, currentGameTime, rules);
                expired++;
            }
        }
        return expired;
    }

    public static void cancelContract(SettlementSavedData data, UUID contractId, long currentGameTime) {
        TradeContract contract = requireContract(data, contractId);
        if (contract.getStatus() == TradeContractStatus.ACTIVE) {
            releaseRemainingReservations(data, contract);
        }
        contract.cancel(currentGameTime);
    }

    static void releaseRemainingReservations(SettlementSavedData data, TradeContract contract) {
        Settlement source = data.getSettlement(contract.getSourceSettlementId());
        if (source == null) {
            return;
        }
        contract.getRequiredGoods().forEach((itemId, required) -> {
            int remaining = Math.max(0, required - contract.getIssuedAmount(itemId));
            if (remaining > 0) {
                source.getWarehouse().releaseReservation(itemId, remaining);
            }
        });
    }

    private static TradeContract requireActiveCarrierContract(SettlementSavedData data,
                                                              UUID contractId,
                                                              UUID carrierId,
                                                              long currentGameTime) {
        TradeContract contract = validateActiveCarrierContract(data, contractId, carrierId);
        if (isPastDeadline(contract, currentGameTime)) {
            expireContract(data, contract, currentGameTime, currentRules());
            return null;
        }
        return contract;
    }

    private static TradeContract requireActiveCarrierContract(SettlementSavedData data,
                                                              UUID contractId,
                                                              UUID carrierId,
                                                              long currentGameTime,
                                                              ContractRules rules) {
        TradeContract contract = validateActiveCarrierContract(data, contractId, carrierId);
        if (isPastDeadline(contract, currentGameTime)) {
            expireContract(data, contract, currentGameTime, rules);
            return null;
        }
        return contract;
    }

    private static TradeContract validateActiveCarrierContract(SettlementSavedData data,
                                                               UUID contractId,
                                                               UUID carrierId) {
        TradeContract contract = requireContract(data, contractId);
        if (contract.getStatus() != TradeContractStatus.ACTIVE) {
            throw new IllegalStateException("Contract is not active");
        }
        if (!Objects.equals(contract.getCarrierId(), Objects.requireNonNull(carrierId, "carrierId"))) {
            throw new IllegalArgumentException("Player is not the assigned contract carrier");
        }
        return contract;
    }

    private static void expireContract(SettlementSavedData data,
                                       TradeContract contract,
                                       long currentGameTime,
                                       ContractRules rules) {
        boolean wasActive = contract.getStatus() == TradeContractStatus.ACTIVE;
        if (wasActive) {
            releaseRemainingReservations(data, contract);
        }
        contract.expire(currentGameTime);
        if (wasActive) {
            data.recordFailedContractBetween(
                    contract.getSourceSettlementId(),
                    contract.getDestinationSettlementId(),
                    currentGameTime
            );
            data.adjustReputationBetween(
                    contract.getSourceSettlementId(),
                    contract.getDestinationSettlementId(),
                    -rules.failedContractReputationPenalty()
            );
        }
    }

    private static boolean isDiscovered(Settlement observer, Settlement target) {
        return observer.findRelationship(target.getId()).map(SettlementRelationship::isKnown).orElse(false);
    }

    private static boolean isPastDeadline(TradeContract contract, long currentGameTime) {
        return Math.max(0L, currentGameTime) > contract.getDeadlineGameTime();
    }

    private static TradeContract requireContract(SettlementSavedData data, UUID contractId) {
        Objects.requireNonNull(data, "data");
        TradeContract contract = data.getTradeContract(Objects.requireNonNull(contractId, "contractId"));
        if (contract == null) {
            throw new IllegalArgumentException("Unknown trade contract: " + contractId);
        }
        return contract;
    }

    private static Settlement requireSettlement(SettlementSavedData data, UUID settlementId) {
        Settlement settlement = data.getSettlement(Objects.requireNonNull(settlementId, "settlementId"));
        if (settlement == null) {
            throw new IllegalArgumentException("Unknown settlement: " + settlementId);
        }
        return settlement;
    }

    private static Settlement requireOtherSettlement(SettlementSavedData data,
                                                     UUID settlementId,
                                                     UUID firstId) {
        if (Objects.requireNonNull(settlementId, "settlementId").equals(firstId)) {
            throw new IllegalArgumentException("A settlement cannot trade with itself");
        }
        return requireSettlement(data, settlementId);
    }

    private static long addClamped(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireContractItem(TradeContract contract, ResourceLocation itemId) {
        ResourceLocation safeItemId = Objects.requireNonNull(itemId, "itemId");
        if (!contract.getRequiredGoods().containsKey(safeItemId)) {
            throw new IllegalArgumentException("Item is not part of this contract: " + safeItemId);
        }
    }

    private static ContractRules currentRules() {
        return new ContractRules(
                (long) VillageDiplomacyConfig.SERVER.trialContractDurationDays.get()
                        * DiplomacyBalance.MINECRAFT_DAY_TICKS,
                VillageDiplomacyConfig.SERVER.successfulDeliveryReputation.get(),
                VillageDiplomacyConfig.SERVER.failedContractReputationPenalty.get()
        );
    }

    public enum ActivationResult {
        ACTIVATED,
        EXPIRED,
        INSUFFICIENT_STOCK
    }

    public record DeliveryResult(int acceptedAmount, boolean completed) {
    }

    public record ContractRules(long trialDurationTicks,
                                int successfulDeliveryReputation,
                                int failedContractReputationPenalty) {
        public ContractRules {
            if (trialDurationTicks <= 0L) {
                throw new IllegalArgumentException("Trial duration must be positive");
            }
            if (successfulDeliveryReputation < 0 || successfulDeliveryReputation > 100) {
                throw new IllegalArgumentException("Successful delivery reputation must be in [0, 100]");
            }
            if (failedContractReputationPenalty < 0 || failedContractReputationPenalty > 100) {
                throw new IllegalArgumentException("Failed contract penalty must be in [0, 100]");
            }
        }
    }
}
