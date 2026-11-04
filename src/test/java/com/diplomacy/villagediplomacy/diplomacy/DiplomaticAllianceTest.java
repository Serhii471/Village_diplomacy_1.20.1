package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static com.diplomacy.villagediplomacy.diplomacy.TradeAgreementService.Action.*;
import static com.diplomacy.villagediplomacy.diplomacy.TradeAgreementService.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class DiplomaticAllianceTest {
    private static final ResourceLocation WHEAT = ResourceLocation.tryParse("minecraft:wheat");
    private static final TradeContractService.ContractRules RULES = new TradeContractService.ContractRules(72_000L, 10, 10);

    @Test
    void playerProposalPersistsAndOnlyRecipientLeaderCanAcceptOnce() {
        Fixture f = fixture(true, 3);
        assertEquals(PROPOSED, act(f, PROPOSE, 10L));
        assertEquals(AllianceStatus.PROPOSED_BY_OTHER, f.target.getOrCreateRelationship(f.home.getId()).getAllianceStatus());
        assertEquals(UNAVAILABLE, act(f, ACCEPT, 10L));
        assertEquals(UNAVAILABLE, act(f, PROPOSE, 10L));
        SettlementSavedData loaded = SettlementSavedData.load(f.data.save(new CompoundTag()));
        assertEquals(NOT_LEADER, TradeAgreementService.actAlliance(loaded, f.target.getId(), f.home.getId(),
                f.leader, ACCEPT, 11L));
        assertEquals(ACTIVATED, TradeAgreementService.actAlliance(loaded, f.target.getId(), f.home.getId(),
                f.otherLeader, ACCEPT, 11L));
        assertTrue(TradeAgreementService.isAllied(loaded.getSettlement(f.home.getId()), loaded.getSettlement(f.target.getId())));
        SettlementSavedData reloaded = SettlementSavedData.load(loaded.save(new CompoundTag()));
        assertTrue(TradeAgreementService.isAllied(reloaded.getSettlement(f.home.getId()), reloaded.getSettlement(f.target.getId())));
        assertEquals(UNAVAILABLE, TradeAgreementService.actAlliance(reloaded, f.target.getId(), f.home.getId(),
                f.otherLeader, ACCEPT, 12L));
    }

    @Test
    void leaderlessSettlementAcceptsEligibleAllianceAutomatically() {
        Fixture f = fixture(false, 3);
        assertEquals(ACTIVATED, act(f, PROPOSE, 10L));
        assertTrue(TradeAgreementService.isAllied(f.home, f.target));
    }

    @Test
    void rejectsUnknownSelfAndUnauthorizedActions() {
        Fixture f = fixture(false, 3);
        assertEquals(NOT_LEADER, TradeAgreementService.actAlliance(f.data, f.home.getId(), f.target.getId(),
                UUID.randomUUID(), PROPOSE, 10L));
        assertEquals(UNAVAILABLE, TradeAgreementService.actAlliance(f.data, f.home.getId(), UUID.randomUUID(),
                f.leader, PROPOSE, 10L));
        assertEquals(UNAVAILABLE, TradeAgreementService.actAlliance(f.data, f.home.getId(), f.home.getId(),
                f.leader, PROPOSE, 10L));
        assertFalse(TradeAgreementService.isAllied(f.home, f.target));
    }

    @Test
    void requiresMutualTradeFriendshipAndThreeDeliveries() {
        Fixture f = fixture(false, 2);
        assertEquals(DELIVERIES_REQUIRED, act(f, PROPOSE, 10L));
        f.data.setTradeAgreementStatusBetween(f.home.getId(), f.target.getId(), TradeAgreementStatus.NONE);
        assertEquals(AGREEMENT_REQUIRED, act(f, PROPOSE, 10L));
        f.data.setTradeAgreementStatusBetween(f.home.getId(), f.target.getId(), TradeAgreementStatus.ACTIVE);
        f.target.getOrCreateRelationship(f.home.getId()).setReputation(19);
        assertEquals(RELATIONS_TOO_LOW, act(f, PROPOSE, 10L));
    }

    @Test
    void acceptanceRechecksTradeRelationsAndCurrentLeader() {
        Fixture f = fixture(true, 3);
        assertEquals(PROPOSED, act(f, PROPOSE, 10L));
        f.home.getOrCreateRelationship(f.target.getId()).setReputation(19);
        assertEquals(RELATIONS_TOO_LOW, TradeAgreementService.actAlliance(f.data, f.target.getId(), f.home.getId(),
                f.otherLeader, ACCEPT, 11L));
        f.home.getOrCreateRelationship(f.target.getId()).setReputation(30);
        f.target.setLeaderUuid(UUID.randomUUID());
        assertEquals(NOT_LEADER, TradeAgreementService.actAlliance(f.data, f.target.getId(), f.home.getId(),
                f.otherLeader, ACCEPT, 11L));
        assertEquals(CLOSED, TradeAgreementService.act(f.data, f.home.getId(), f.target.getId(), f.leader, TERMINATE));
        assertEquals(AllianceStatus.NONE, f.home.getOrCreateRelationship(f.target.getId()).getAllianceStatus());
    }

    @Test
    void recentFailureAndOverdueActiveDeliveryBlockAllianceUntilRecovery() {
        Fixture f = fixture(false, 3);
        TradeContract failed = delivery(f, 10L);
        assertEquals(UNRESOLVED_FAILURES, act(f, PROPOSE, 72_011L));
        TradeContractService.expireDueContracts(f.data, 72_011L, RULES);
        assertEquals(TradeContractStatus.FAILED, failed.getStatus());
        assertEquals(UNRESOLVED_FAILURES, act(f, PROPOSE, 72_012L));
        completeDelivery(f, 72_013L);
        assertEquals(ACTIVATED, act(f, PROPOSE, 72_014L));
    }

    @Test
    void declineWithdrawAndTerminationAreSymmetricAndPreserveTrade() {
        Fixture f = fixture(true, 3);
        assertEquals(PROPOSED, act(f, PROPOSE, 10L));
        assertEquals(UNAVAILABLE, act(f, DECLINE, 10L));
        assertEquals(CLOSED, TradeAgreementService.actAlliance(f.data, f.target.getId(), f.home.getId(),
                f.otherLeader, DECLINE, 11L));
        assertEquals(PROPOSED, act(f, PROPOSE, 12L));
        assertEquals(CLOSED, act(f, CANCEL, 13L));
        f.target.setLeaderUuid(null);
        assertEquals(ACTIVATED, act(f, PROPOSE, 14L));
        assertEquals(CLOSED, act(f, TERMINATE, 15L));
        assertTrue(TradeAgreementService.isActive(f.home, f.target));
        assertFalse(TradeAgreementService.isAllied(f.home, f.target));
        assertEquals(AllianceStatus.NONE, f.target.getOrCreateRelationship(f.home.getId()).getAllianceStatus());
        assertEquals(UNAVAILABLE, act(f, TERMINATE, 15L));
    }

    @Test
    void allianceUnlocksLargerBetterPaidOffersAndEndingTradeDisablesBonuses() {
        Fixture f = fixture(false, 3);
        assertEquals(ACTIVATED, act(f, PROPOSE, 10L));
        var offers = TradeOfferGenerator.ensureOffers(f.data, f.home.getId(), 10L,
                new TradeOfferGenerator.OfferRules(3, 16, 3, 12), RULES);
        assertEquals(3, offers.size());
        for (TradeContract contract : offers) {
            assertFalse(contract.isTrial());
            assertEquals(48, contract.getRequiredGoods().get(WHEAT));
            assertTrue(contract.getRewardEmeralds() >= 8);
        }
        assertEquals(CLOSED, TradeAgreementService.act(f.data, f.home.getId(), f.target.getId(), f.leader, TERMINATE));
        assertFalse(TradeAgreementService.isAllied(f.home, f.target));
        assertTrue(TradeOfferGenerator.ensureOffers(f.data, f.home.getId(), 11L,
                new TradeOfferGenerator.OfferRules(3, 16, 3, 12), RULES).isEmpty());
    }

    private static TradeAgreementService.Result act(Fixture f, TradeAgreementService.Action action, long time) {
        return TradeAgreementService.actAlliance(f.data, f.home.getId(), f.target.getId(), f.leader, action, time);
    }

    private static TradeContract delivery(Fixture f, long time) {
        TradeContract contract = TradeContractService.createTrialDeliveryOffer(f.data, f.home.getId(), f.target.getId(),
                Map.of(WHEAT, 1), 3, time, RULES);
        TradeContractService.acceptContract(f.data, contract.getId(), f.leader, time);
        return contract;
    }

    private static void completeDelivery(Fixture f, long time) {
        TradeContract contract = delivery(f, time);
        TradeContractService.issueGoods(f.data, contract.getId(), f.leader, WHEAT, 1, time, RULES);
        TradeContractService.recordDelivery(f.data, contract.getId(), f.leader, WHEAT, 1, time, RULES);
    }

    private static Fixture fixture(boolean playerTarget, int deliveries) {
        SettlementSavedData data = new SettlementSavedData();
        Settlement home = new Settlement(UUID.randomUUID(), "Home", BlockPos.ZERO, 64);
        Settlement target = new Settlement(UUID.randomUUID(), "Target", new BlockPos(200, 64, 0), 64);
        UUID leader = UUID.randomUUID();
        UUID otherLeader = UUID.randomUUID();
        home.setLeaderUuid(leader);
        if (playerTarget) {
            target.setLeaderUuid(otherLeader);
        }
        data.registerSettlement(home);
        data.registerSettlement(target);
        data.discoverSettlement(home.getId(), target.getId());
        home.getWarehouse().configureCapacity(WHEAT, 200);
        home.getWarehouse().add(WHEAT, 120);
        ResourceLocation coal = ResourceLocation.tryParse("minecraft:coal");
        target.getWarehouse().configureCapacity(coal, 200);
        target.getWarehouse().add(coal, 120);
        Fixture f = new Fixture(data, home, target, leader, otherLeader);
        for (int i = 1; i <= deliveries; i++) {
            completeDelivery(f, i);
        }
        data.setTradeAgreementStatusBetween(home.getId(), target.getId(), TradeAgreementStatus.ACTIVE);
        return f;
    }

    private record Fixture(SettlementSavedData data, Settlement home, Settlement target, UUID leader, UUID otherLeader) {
    }
}
