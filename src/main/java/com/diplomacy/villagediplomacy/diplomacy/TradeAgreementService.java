package com.diplomacy.villagediplomacy.diplomacy;

import java.util.UUID;

public final class TradeAgreementService {
    private TradeAgreementService() {
    }

    public static Result act(SettlementSavedData data, UUID homeId, UUID targetId,
                             UUID playerId, Action action) {
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
        TradeAgreementStatus status = relation.getTradeAgreementStatus();
        if (reverse.getTradeAgreementStatus() != status.oppositePerspective()) {
            return Result.UNAVAILABLE;
        }
        switch (action) {
            case PROPOSE, ACCEPT -> {
                if (action == Action.PROPOSE && status != TradeAgreementStatus.NONE
                        || action == Action.ACCEPT && status != TradeAgreementStatus.PROPOSED_BY_OTHER) {
                    return Result.UNAVAILABLE;
                }
                Result eligibility = eligibility(home, target);
                if (eligibility != Result.ELIGIBLE) {
                    return eligibility;
                }
                boolean activate = action == Action.ACCEPT || target.getLeaderUuid() == null;
                if (activate && target.getLeaderUuid() == null && !acceptsAutomatically(home, target)) {
                    return Result.DECLINED;
                }
                data.discoverSettlement(targetId, homeId);
                data.setTradeAgreementStatusBetween(homeId, targetId,
                        activate ? TradeAgreementStatus.ACTIVE : TradeAgreementStatus.PROPOSED_BY_US);
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
                data.setAllianceStatusBetween(homeId, targetId, AllianceStatus.NONE);
            }
        }
        data.setTradeAgreementStatusBetween(homeId, targetId, TradeAgreementStatus.NONE);
        return Result.CLOSED;
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
        UNDISCOVERED, RELATIONS_TOO_LOW, TRIAL_REQUIRED, NO_MUTUAL_SURPLUS, DECLINED
    }
}
