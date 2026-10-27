package com.diplomacy.villagediplomacy.diplomacy;

import java.util.Objects;
import java.util.UUID;

/** Permission boundary for contract actions initiated from a diplomacy table. */
public final class DiplomacyTableContractService {
    private DiplomacyTableContractService() {
    }

    public static AcceptResult acceptOffer(SettlementSavedData data,
                                           UUID settlementId,
                                           UUID playerId,
                                           UUID contractId,
                                           long gameTime) {
        Objects.requireNonNull(data, "data");
        Settlement settlement = data.getSettlement(Objects.requireNonNull(settlementId, "settlementId"));
        if (settlement == null || !settlement.isPlayerLeader(Objects.requireNonNull(playerId, "playerId"))) {
            return AcceptResult.NOT_LEADER;
        }

        TradeContract contract = data.getTradeContract(Objects.requireNonNull(contractId, "contractId"));
        if (contract == null || !contract.involves(settlementId)
                || contract.getStatus() != TradeContractStatus.OFFERED) {
            return AcceptResult.UNAVAILABLE;
        }

        return switch (TradeContractService.acceptContract(data, contractId, playerId, gameTime)) {
            case ACTIVATED -> AcceptResult.ACCEPTED;
            case EXPIRED -> AcceptResult.EXPIRED;
            case INSUFFICIENT_STOCK -> AcceptResult.INSUFFICIENT_STOCK;
        };
    }

    public enum AcceptResult {
        ACCEPTED,
        EXPIRED,
        INSUFFICIENT_STOCK,
        NOT_LEADER,
        UNAVAILABLE
    }
}
