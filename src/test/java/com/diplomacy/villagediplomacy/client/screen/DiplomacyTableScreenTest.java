package com.diplomacy.villagediplomacy.client.screen;

import com.diplomacy.villagediplomacy.menu.DiplomacyTableSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiplomacyTableScreenTest {
    @Test
    void selectedOfferIsTheOnlyOfferShownAlongsideContractHistory() {
        DiplomacyTableSnapshot.ContractEntry firstOffer = contract("offered");
        DiplomacyTableSnapshot.ContractEntry active = contract("active");
        DiplomacyTableSnapshot.ContractEntry secondOffer = contract("offered");
        DiplomacyTableSnapshot.ContractEntry completed = contract("completed");

        assertEquals(
                List.of(secondOffer, active, completed),
                DiplomacyTableScreen.visibleTradeContracts(
                        List.of(firstOffer, active, secondOffer, completed), 1));
    }

    private static DiplomacyTableSnapshot.ContractEntry contract(String status) {
        return new DiplomacyTableSnapshot.ContractEntry(
                UUID.randomUUID(), "Source", "Destination", "delivery", status, "",
                true, 1_000L, 1, 100L, 0, "none", List.of(), List.of());
    }
}
