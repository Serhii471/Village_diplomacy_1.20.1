package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LeadershipClaimServiceTest {
    private SettlementSavedData data;
    private Settlement first;
    private Settlement second;
    private UUID playerId;
    private CompoundTag playerData;

    @BeforeEach
    void setUp() {
        data = new SettlementSavedData();
        first = new Settlement(UUID.randomUUID(), "First", BlockPos.ZERO, 64);
        second = new Settlement(UUID.randomUUID(), "Second", new BlockPos(200, 64, 0), 64);
        data.registerSettlement(first);
        data.registerSettlement(second);
        playerId = UUID.randomUUID();
        playerData = new CompoundTag();
    }

    @Test
    void firstInteractionRequiresConfirmationAndSecondClaimsSettlement() {
        LeadershipClaimService.ClaimOutcome firstAttempt = request(first, 1_000L);

        assertEquals(LeadershipClaimService.ClaimResult.CONFIRMATION_REQUIRED, firstAttempt.result());
        assertNull(first.getLeaderUuid());

        LeadershipClaimService.ClaimOutcome secondAttempt = request(first, 1_100L);

        assertEquals(LeadershipClaimService.ClaimResult.CLAIMED, secondAttempt.result());
        assertEquals(playerId, first.getLeaderUuid());
    }

    @Test
    void expiredConfirmationMustBeStartedAgain() {
        request(first, 1_000L);

        LeadershipClaimService.ClaimOutcome expiredAttempt = request(
                first,
                1_000L + DiplomacyBalance.LEADERSHIP_CONFIRMATION_WINDOW_TICKS + 1L
        );

        assertEquals(LeadershipClaimService.ClaimResult.CONFIRMATION_REQUIRED, expiredAttempt.result());
        assertNull(first.getLeaderUuid());
    }

    @Test
    void confirmationForAnotherSettlementDoesNotClaimTarget() {
        request(first, 1_000L);

        LeadershipClaimService.ClaimOutcome otherAttempt = request(second, 1_050L);

        assertEquals(LeadershipClaimService.ClaimResult.CONFIRMATION_REQUIRED, otherAttempt.result());
        assertNull(first.getLeaderUuid());
        assertNull(second.getLeaderUuid());
    }

    @Test
    void playerCannotLeadTwoSettlements() {
        first.setLeaderUuid(playerId);

        LeadershipClaimService.ClaimOutcome outcome = request(second, 1_000L);

        assertEquals(LeadershipClaimService.ClaimResult.PLAYER_LEADS_ANOTHER_SETTLEMENT, outcome.result());
        assertEquals(first, outcome.relatedSettlement());
        assertNull(second.getLeaderUuid());
    }

    @Test
    void settlementWithAnotherLeaderCannotBeClaimed() {
        first.setLeaderUuid(UUID.randomUUID());

        LeadershipClaimService.ClaimOutcome outcome = request(first, 1_000L);

        assertEquals(LeadershipClaimService.ClaimResult.SETTLEMENT_ALREADY_CLAIMED, outcome.result());
        assertEquals(first, outcome.relatedSettlement());
    }

    @Test
    void currentLeaderGetsIdempotentResult() {
        first.setLeaderUuid(playerId);

        LeadershipClaimService.ClaimOutcome outcome = request(first, 1_000L);

        assertEquals(LeadershipClaimService.ClaimResult.ALREADY_LEADER, outcome.result());
        assertEquals(playerId, first.getLeaderUuid());
    }

    private LeadershipClaimService.ClaimOutcome request(Settlement settlement, long gameTime) {
        return LeadershipClaimService.requestClaim(data, settlement, playerId, gameTime, playerData);
    }
}
