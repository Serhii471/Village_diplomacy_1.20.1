package com.diplomacy.villagediplomacy.diplomacy;

import java.util.UUID;

public final class TradeAgreementService {
    private TradeAgreementService() {
    }

    public static Result act(SettlementSavedData data, UUID homeId, UUID targetId,
                             UUID playerId, Action action) {
        return act(data, homeId, targetId, playerId, action, false, 0L);
    }

    public static Result actAlliance(SettlementSavedData data, UUID homeId, UUID targetId,
                                     UUID playerId, Action action, long gameTime) {
        return act(data, homeId, targetId, playerId, action, true, gameTime);
    }

    private static Result act(SettlementSavedData data, UUID homeId, UUID targetId,
                              UUID playerId, Action action, boolean alliance, long gameTime) {
        Settlement home = data.getSettlement(homeId);
        Settlement target = data.getSettlement(targetId);
        if (home == null || target == null || homeId.equals(targetId)) {
            return Result.UNAVAILABLE;
        }
        if (!home.isPlayerLeader(playerId)) {
            return Result.NOT_LEADER;
        }
        SettlementRelationship relation = home.getOrCreateRelationship(targetId);
        SettlementRelationship reverse = target.getOrCreateRelationship(homeId);
        TradeAgreementStatus status = status(relation, alliance);
        if (status(reverse, alliance) != status.oppositePerspective()) {
            return Result.UNAVAILABLE;
        }
        switch (action) {
            case PROPOSE, ACCEPT -> {
                if (action == Action.PROPOSE && status != TradeAgreementStatus.NONE
                        || action == Action.ACCEPT && status != TradeAgreementStatus.PROPOSED_BY_OTHER) {
                    return Result.UNAVAILABLE;
                }
                Result eligibility = alliance ? allianceEligibility(data, home, target, gameTime) : eligibility(home, target);
                if (eligibility != Result.ELIGIBLE) {
                    return eligibility;
                }
                boolean activate = action == Action.ACCEPT || target.getLeaderUuid() == null;
                if (!alliance && activate && target.getLeaderUuid() == null && !acceptsAutomatically(home, target)) {
                    return Result.DECLINED;
                }
                data.discoverSettlement(targetId, homeId);
                setStatus(data, homeId, targetId,
                        activate ? TradeAgreementStatus.ACTIVE : TradeAgreementStatus.PROPOSED_BY_US, alliance);
                return activate ? Result.ACTIVATED : Result.PROPOSED;
            }
            case DECLINE -> {
                if (status != TradeAgreementStatus.PROPOSED_BY_OTHER) {
                    return Result.UNAVAILABLE;
                }
            }
            case CANCEL -> {
                if (status != TradeAgreementStatus.PROPOSED_BY_US) {
                    return Result.UNAVAILABLE;
                }
            }
            case TERMINATE -> {
                if (status != TradeAgreementStatus.ACTIVE) {
                    return Result.UNAVAILABLE;
                }
                if (!alliance) {
                    data.setAllianceStatusBetween(homeId, targetId, AllianceStatus.NONE);
                }
            }
        }
        setStatus(data, homeId, targetId, TradeAgreementStatus.NONE, alliance);
        return Result.CLOSED;
    }

    private static TradeAgreementStatus status(SettlementRelationship relationship, boolean alliance) {
        return alliance ? TradeAgreementStatus.valueOf(relationship.getAllianceStatus().name())
                : relationship.getTradeAgreementStatus();
    }

    private static void setStatus(SettlementSavedData data, UUID homeId, UUID targetId,
                                   TradeAgreementStatus status, boolean alliance) {
        if (alliance) {
            data.setAllianceStatusBetween(homeId, targetId, AllianceStatus.valueOf(status.name()));
        } else {
            data.setTradeAgreementStatusBetween(homeId, targetId, status);
        }
    }

    public static boolean isAllied(Settlement first, Settlement second) {
        return first != null && second != null && isActive(first, second)
                && first.findRelationship(second.getId()).map(relation ->
                relation.getAllianceStatus() == AllianceStatus.ACTIVE).orElse(false)
                && second.findRelationship(first.getId()).map(relation ->
                relation.getAllianceStatus() == AllianceStatus.ACTIVE).orElse(false);
    }

    private static Result allianceEligibility(SettlementSavedData data, Settlement home,
                                               Settlement target, long gameTime) {
        if (!isActive(home, target)) {
            return Result.AGREEMENT_REQUIRED;
        }
        SettlementRelationship relation = home.getOrCreateRelationship(target.getId());
        SettlementRelationship reverse = target.getOrCreateRelationship(home.getId());
        if (Math.min(relation.getReputation(), reverse.getReputation()) < DiplomacyRelation.FRIENDLY.getMinScore()) {
            return Result.RELATIONS_TOO_LOW;
        }
        if (Math.min(relation.getSuccessfulDeliveries(), reverse.getSuccessfulDeliveries())
                < DiplomacyBalance.DELIVERIES_FOR_ALLIANCE) {
            return Result.DELIVERIES_REQUIRED;
        }
        var contracts = data.getAllTradeContracts().stream()
                .filter(contract -> contract.involves(home.getId()) && contract.involves(target.getId())).toList();
        long lastSuccess = contracts.stream().filter(contract -> contract.getStatus() == TradeContractStatus.COMPLETED)
                .mapToLong(TradeContract::getCompletedGameTime).max().orElse(-1L);
        boolean unresolved = contracts.stream().anyMatch(contract ->
                contract.getStatus() == TradeContractStatus.FAILED && contract.getCompletedGameTime() >= lastSuccess
                        || contract.getStatus() == TradeContractStatus.ACTIVE && gameTime > contract.getDeadlineGameTime());
        return unresolved ? Result.UNRESOLVED_FAILURES : Result.ELIGIBLE;
    }

    public static boolean isActive(Settlement first, Settlement second) {
        return first.getRelationships().stream().anyMatch(relation ->
                relation.getTargetSettlementId().equals(second.getId())
                        && relation.getTradeAgreementStatus() == TradeAgreementStatus.ACTIVE)
                && second.getRelationships().stream().anyMatch(relation ->
                relation.getTargetSettlementId().equals(first.getId())
                        && relation.getTradeAgreementStatus() == TradeAgreementStatus.ACTIVE);
    }

    private static Result eligibility(Settlement home, Settlement target) {
        SettlementRelationship relation = home.getOrCreateRelationship(target.getId());
        SettlementRelationship reverse = target.getOrCreateRelationship(home.getId());
        if (!relation.isKnown()) {
            return Result.UNDISCOVERED;
        }
        if (!relation.getDiplomacyRelation().canTrade() || !reverse.getDiplomacyRelation().canTrade()) {
            return Result.RELATIONS_TOO_LOW;
        }
        if (Math.min(relation.getSuccessfulDeliveries(), reverse.getSuccessfulDeliveries())
                < DiplomacyBalance.TRIAL_DELIVERIES_FOR_TRADE_AGREEMENT) {
            return Result.TRIAL_REQUIRED;
        }
        return hasUsefulSurplus(home, target) && hasUsefulSurplus(target, home)
                ? Result.ELIGIBLE : Result.NO_MUTUAL_SURPLUS;
    }

    private static boolean hasUsefulSurplus(Settlement source, Settlement receiver) {
        return source.getWarehouse().getContents().entrySet().stream().anyMatch(entry ->
                entry.getValue().available() > 0
                        && entry.getValue().available() > receiver.getWarehouse().getStock(entry.getKey()).available());
    }

    private static boolean acceptsAutomatically(Settlement home, Settlement target) {
        SettlementRelationship history = target.getOrCreateRelationship(home.getId());
        double distanceCost = Math.sqrt(home.getCenter().distSqr(target.getCenter())) / 256.0D;
        double confidence = 10.0D + history.getReputation()
                + Math.min(20, history.getSuccessfulDeliveries()) * 5.0D
                - Math.min(20, history.getFailedContracts()) * 10.0D;
        return confidence >= distanceCost;
    }

    public enum Action {
        PROPOSE, ACCEPT, DECLINE, CANCEL, TERMINATE
    }

    public enum Result {
        ELIGIBLE, PROPOSED, ACTIVATED, CLOSED, NOT_LEADER, UNAVAILABLE,
        UNDISCOVERED, RELATIONS_TOO_LOW, TRIAL_REQUIRED, NO_MUTUAL_SURPLUS, DECLINED,
        AGREEMENT_REQUIRED, DELIVERIES_REQUIRED, UNRESOLVED_FAILURES
    }
}
