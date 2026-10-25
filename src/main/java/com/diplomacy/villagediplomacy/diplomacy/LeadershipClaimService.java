package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side rules for claiming settlement leadership with a deliberate
 * second interaction as confirmation.
 */
public final class LeadershipClaimService {
    private static final String PENDING_SETTLEMENT_KEY = "VillageDiplomacyPendingLeadership";
    private static final String PENDING_EXPIRY_KEY = "VillageDiplomacyPendingLeadershipExpiry";

    private LeadershipClaimService() {
    }

    public static ClaimOutcome requestClaim(SettlementSavedData data,
                                            Settlement target,
                                            UUID playerId,
                                            long gameTime,
                                            CompoundTag playerPersistentData) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerPersistentData, "playerPersistentData");

        UUID currentLeader = target.getLeaderUuid();
        if (playerId.equals(currentLeader)) {
            clearPendingClaim(playerPersistentData);
            return new ClaimOutcome(ClaimResult.ALREADY_LEADER, target);
        }
        if (currentLeader != null) {
            clearPendingClaim(playerPersistentData);
            return new ClaimOutcome(ClaimResult.SETTLEMENT_ALREADY_CLAIMED, target);
        }

        Settlement ledSettlement = data.findSettlementLedBy(playerId);
        if (ledSettlement != null && !ledSettlement.getId().equals(target.getId())) {
            clearPendingClaim(playerPersistentData);
            return new ClaimOutcome(ClaimResult.PLAYER_LEADS_ANOTHER_SETTLEMENT, ledSettlement);
        }

        long safeGameTime = Math.max(0L, gameTime);
        boolean confirmsPendingClaim = playerPersistentData.hasUUID(PENDING_SETTLEMENT_KEY)
                && target.getId().equals(playerPersistentData.getUUID(PENDING_SETTLEMENT_KEY))
                && playerPersistentData.getLong(PENDING_EXPIRY_KEY) >= safeGameTime;

        if (!confirmsPendingClaim) {
            playerPersistentData.putUUID(PENDING_SETTLEMENT_KEY, target.getId());
            playerPersistentData.putLong(
                    PENDING_EXPIRY_KEY,
                    safeGameTime + DiplomacyBalance.LEADERSHIP_CONFIRMATION_WINDOW_TICKS
            );
            return new ClaimOutcome(ClaimResult.CONFIRMATION_REQUIRED, target);
        }

        clearPendingClaim(playerPersistentData);
        target.setLeaderUuid(playerId);
        return new ClaimOutcome(ClaimResult.CLAIMED, target);
    }

    private static void clearPendingClaim(CompoundTag playerPersistentData) {
        playerPersistentData.remove(PENDING_SETTLEMENT_KEY);
        playerPersistentData.remove(PENDING_EXPIRY_KEY);
    }

    public enum ClaimResult {
        CONFIRMATION_REQUIRED,
        CLAIMED,
        ALREADY_LEADER,
        SETTLEMENT_ALREADY_CLAIMED,
        PLAYER_LEADS_ANOTHER_SETTLEMENT
    }

    public record ClaimOutcome(ClaimResult result, @Nullable Settlement relatedSettlement) {
    }
}
