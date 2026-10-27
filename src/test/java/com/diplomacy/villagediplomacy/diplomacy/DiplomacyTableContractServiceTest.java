package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiplomacyTableContractServiceTest {
    private static final ResourceLocation WHEAT = id("minecraft:wheat");
    private static final TradeContractService.ContractRules RULES =
            new TradeContractService.ContractRules(72_000L, 10, 10);

    @Test
    void onlyLeaderOfInvolvedSettlementCanAcceptOffer() {
        Fixture fixture = fixture();
        UUID outsider = UUID.randomUUID();

        assertEquals(DiplomacyTableContractService.AcceptResult.NOT_LEADER,
                DiplomacyTableContractService.acceptOffer(
                        fixture.data(), fixture.source().getId(), outsider,
                        fixture.contract().getId(), 1L));
        assertEquals(0, fixture.source().getWarehouse().getStock(WHEAT).reserved());

        assertEquals(DiplomacyTableContractService.AcceptResult.ACCEPTED,
                DiplomacyTableContractService.acceptOffer(
                        fixture.data(), fixture.source().getId(), fixture.leaderId(),
                        fixture.contract().getId(), 2L));
        assertEquals(8, fixture.source().getWarehouse().getStock(WHEAT).reserved());

        assertEquals(DiplomacyTableContractService.AcceptResult.UNAVAILABLE,
                DiplomacyTableContractService.acceptOffer(
                        fixture.data(), fixture.source().getId(), fixture.leaderId(),
                        fixture.contract().getId(), 3L));
    }

    @Test
    void unrelatedSettlementCannotAcceptOfferEvenWithItsLeader() {
        Fixture fixture = fixture();
        Settlement unrelated = new Settlement(UUID.randomUUID(), "Other", new BlockPos(400, 0, 0), 64);
        UUID unrelatedLeader = UUID.randomUUID();
        unrelated.setLeaderUuid(unrelatedLeader);
        fixture.data().registerSettlement(unrelated);

        assertEquals(DiplomacyTableContractService.AcceptResult.UNAVAILABLE,
                DiplomacyTableContractService.acceptOffer(
                        fixture.data(), unrelated.getId(), unrelatedLeader,
                        fixture.contract().getId(), 1L));
    }

    private static Fixture fixture() {
        SettlementSavedData data = new SettlementSavedData();
        Settlement source = new Settlement(UUID.randomUUID(), "Source", BlockPos.ZERO, 64);
        Settlement destination = new Settlement(UUID.randomUUID(), "Destination", new BlockPos(200, 0, 0), 64);
        UUID leader = UUID.randomUUID();
        source.setLeaderUuid(leader);
        data.registerSettlement(source);
        data.registerSettlement(destination);
        data.discoverSettlement(source.getId(), destination.getId());
        source.getWarehouse().configureCapacity(WHEAT, 100);
        source.getWarehouse().add(WHEAT, 40);
        TradeContract contract = TradeContractService.createTrialDeliveryOffer(
                data, source.getId(), destination.getId(), Map.of(WHEAT, 8), 3, 0L, RULES);
        return new Fixture(data, source, contract, leader);
    }

    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value));
    }

    private record Fixture(SettlementSavedData data,
                           Settlement source,
                           TradeContract contract,
                           UUID leaderId) {
    }
}
