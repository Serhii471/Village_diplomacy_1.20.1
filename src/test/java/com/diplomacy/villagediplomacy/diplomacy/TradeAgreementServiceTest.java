package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.diplomacy.villagediplomacy.diplomacy.TradeAgreementService.Action.*;
import static com.diplomacy.villagediplomacy.diplomacy.TradeAgreementService.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeAgreementServiceTest {
    @Test
    void playerProposalPersistsAndOnlyRecipientLeaderCanAccept() {
        Fixture f = fixture(true);
        assertEquals(PROPOSED, act(f, PROPOSE));
        assertEquals(TradeAgreementStatus.PROPOSED_BY_US, relation(f).getTradeAgreementStatus());
        assertTrue(f.target.getKnownSettlementIds().contains(f.home.getId()));
        assertEquals(UNAVAILABLE, act(f, ACCEPT));
        assertEquals(UNAVAILABLE, act(f, PROPOSE));
        SettlementSavedData loaded = SettlementSavedData.load(f.data.save(new CompoundTag()));
        assertEquals(NOT_LEADER, TradeAgreementService.act(loaded, f.target.getId(), f.home.getId(), f.leader, ACCEPT));
        assertEquals(ACTIVATED, TradeAgreementService.act(loaded, f.target.getId(), f.home.getId(), f.otherLeader, ACCEPT));
        assertTrue(TradeAgreementService.isActive(loaded.getSettlement(f.home.getId()), loaded.getSettlement(f.target.getId())));
        assertEquals(UNAVAILABLE, TradeAgreementService.act(loaded, f.target.getId(), f.home.getId(), f.otherLeader, ACCEPT));
    }

    @Test
    void leaderlessSettlementAutomaticallyAcceptsUsefulNearbyAgreement() {
        Fixture f = fixture(false);
        assertEquals(ACTIVATED, act(f, PROPOSE));
        assertTrue(TradeAgreementService.isActive(f.home, f.target));
    }

    @Test
    void unavailableUnknownSelfAndUnauthorizedRequestsDoNotActivate() {
        Fixture f = fixture(false);
        assertEquals(NOT_LEADER, TradeAgreementService.act(f.data, f.home.getId(), f.target.getId(), UUID.randomUUID(), PROPOSE));
        assertEquals(UNAVAILABLE, TradeAgreementService.act(f.data, f.home.getId(), UUID.randomUUID(), f.leader, PROPOSE));
        assertEquals(UNAVAILABLE, TradeAgreementService.act(f.data, f.home.getId(), f.home.getId(), f.leader, PROPOSE));
        relation(f).setKnown(false);
        assertEquals(UNDISCOVERED, act(f, PROPOSE));
        assertEquals(TradeAgreementStatus.NONE, relation(f).getTradeAgreementStatus());
    }

    @Test
    void requiresSuccessfulTrialNeutralRelationsAndUnreservedMutualSurplus() {
        Fixture f = fixture(false);
        f.data.adjustReputationBetween(f.home.getId(), f.target.getId(), -20);
        assertEquals(RELATIONS_TOO_LOW, act(f, PROPOSE));
        f.data.adjustReputationBetween(f.home.getId(), f.target.getId(), 20);
        ResourceLocation coal = ResourceLocation.tryParse("minecraft:coal");
        f.target.getWarehouse().tryReserve(coal, 80);
        assertEquals(NO_MUTUAL_SURPLUS, act(f, PROPOSE));
        Settlement fresh = new Settlement(UUID.randomUUID(), "Fresh", new BlockPos(400, 64, 0), 64);
        f.data.registerSettlement(fresh);
        f.data.discoverSettlement(f.home.getId(), fresh.getId());
        assertEquals(TRIAL_REQUIRED, TradeAgreementService.act(f.data, f.home.getId(), fresh.getId(), f.leader, PROPOSE));
    }

    @Test
    void acceptanceRechecksCurrentConditionsAndLeader() {
        Fixture f = fixture(true);
        assertEquals(PROPOSED, act(f, PROPOSE));
        f.data.adjustReputationBetween(f.home.getId(), f.target.getId(), -20);
        assertEquals(RELATIONS_TOO_LOW, TradeAgreementService.act(f.data, f.target.getId(), f.home.getId(), f.otherLeader, ACCEPT));
        f.data.adjustReputationBetween(f.home.getId(), f.target.getId(), 20);
        f.target.setLeaderUuid(UUID.randomUUID());
        assertEquals(NOT_LEADER, TradeAgreementService.act(f.data, f.target.getId(), f.home.getId(), f.otherLeader, ACCEPT));
    }

    @Test
    void recipientCanDeclineAndProposerCanWithdraw() {
        Fixture f = fixture(true);
        assertEquals(PROPOSED, act(f, PROPOSE));
        assertEquals(UNAVAILABLE, act(f, DECLINE));
        assertEquals(CLOSED, TradeAgreementService.act(f.data, f.target.getId(), f.home.getId(), f.otherLeader, DECLINE));
        assertEquals(PROPOSED, act(f, PROPOSE));
        assertEquals(CLOSED, act(f, CANCEL));
        assertEquals(TradeAgreementStatus.NONE, f.target.getOrCreateRelationship(f.home.getId()).getTradeAgreementStatus());
    }

    @Test
    void terminationClearsBothAgreementAndAllianceButPreservesContracts() {
        Fixture f = fixture(false);
        assertEquals(ACTIVATED, act(f, PROPOSE));
        f.data.setAllianceStatusBetween(f.home.getId(), f.target.getId(), AllianceStatus.ACTIVE);
        var offers = TradeOfferGenerator.ensureOffers(f.data, f.home.getId(), 0L,
                new TradeOfferGenerator.OfferRules(3, 16, 3, 12), new TradeContractService.ContractRules(72_000L, 10, 10));
        TradeContract contract = offers.get(0);
        TradeContractService.acceptContract(f.data, contract.getId(), f.leader, 0L);
        int reserved = f.home.getWarehouse().getStock(ResourceLocation.tryParse("minecraft:wheat")).reserved();
        assertEquals(CLOSED, act(f, TERMINATE));
        assertFalse(TradeAgreementService.isActive(f.home, f.target));
        assertEquals(AllianceStatus.NONE, relation(f).getAllianceStatus());
        assertEquals(AllianceStatus.NONE, f.target.getOrCreateRelationship(f.home.getId()).getAllianceStatus());
        assertEquals(TradeContractStatus.ACTIVE, contract.getStatus());
        assertEquals(reserved, f.home.getWarehouse().getStock(ResourceLocation.tryParse("minecraft:wheat")).reserved());
        assertEquals(UNAVAILABLE, act(f, TERMINATE));
    }

    @Test
    void automaticDecisionConsidersDistanceAndFailureHistory() {
        Fixture f = fixture(false);
        for (int i = 0; i < 3; i++) {
            f.data.recordFailedContractBetween(f.home.getId(), f.target.getId(), 0L);
        }
        assertEquals(DECLINED, act(f, PROPOSE));
        Fixture distant = fixture(false);
        distant.target.setCenter(new BlockPos(100_000, 64, 0));
        assertEquals(DECLINED, act(distant, PROPOSE));
    }

    private static TradeAgreementService.Result act(Fixture f, TradeAgreementService.Action action) {
        return TradeAgreementService.act(f.data, f.home.getId(), f.target.getId(), f.leader, action);
    }

    private static SettlementRelationship relation(Fixture f) {
        return f.home.getOrCreateRelationship(f.target.getId());
    }

    private static Fixture fixture(boolean playerTarget) {
        SettlementSavedData data = new SettlementSavedData();
        UUID leader = UUID.randomUUID();
        UUID otherLeader = UUID.randomUUID();
        Settlement home = new Settlement(UUID.randomUUID(), "Home", BlockPos.ZERO, 64);
        Settlement target = new Settlement(UUID.randomUUID(), "Target", new BlockPos(200, 64, 0), 64);
        home.setLeaderUuid(leader);
        if (playerTarget) {
            target.setLeaderUuid(otherLeader);
        }
        data.registerSettlement(home);
        data.registerSettlement(target);
        data.discoverSettlement(home.getId(), target.getId());
        data.recordSuccessfulDeliveryBetween(home.getId(), target.getId(), 0L);
        stock(home, "minecraft:wheat");
        stock(target, "minecraft:coal");
        return new Fixture(data, home, target, leader, otherLeader);
    }

    private static void stock(Settlement settlement, String item) {
        ResourceLocation id = ResourceLocation.tryParse(item);
        settlement.getWarehouse().configureCapacity(id, 100);
        settlement.getWarehouse().add(id, 80);
    }

    private record Fixture(SettlementSavedData data, Settlement home, Settlement target, UUID leader, UUID otherLeader) {
    }
}
