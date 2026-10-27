package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeOfferGeneratorTest {
    private static final ResourceLocation WHEAT = id("minecraft:wheat");
    private static final ResourceLocation COAL = id("minecraft:coal");
    private static final TradeContractService.ContractRules CONTRACT_RULES =
            new TradeContractService.ContractRules(72_000L, 10, 10);
    private static final TradeOfferGenerator.OfferRules OFFER_RULES =
            new TradeOfferGenerator.OfferRules(3, 16, 3, 12);

    @Test
    void complementarySurplusCreatesOneExchangeOfferWithoutDuplicates() {
        Fixture fixture = fixture();
        stock(fixture.home(), WHEAT, 100, 40);
        stock(fixture.target(), COAL, 100, 20);

        List<TradeContract> first = TradeOfferGenerator.ensureOffers(
                fixture.data(), fixture.home().getId(), 1_000L, OFFER_RULES, CONTRACT_RULES);
        List<TradeContract> repeated = TradeOfferGenerator.ensureOffers(
                fixture.data(), fixture.home().getId(), 1_001L, OFFER_RULES, CONTRACT_RULES);

        assertEquals(1, first.size());
        assertTrue(repeated.isEmpty());
        TradeContract offer = first.get(0);
        assertEquals(TradeContractType.EXCHANGE, offer.getType());
        assertEquals(16, offer.getRequiredGoods().get(WHEAT));
        assertEquals(10, offer.getReturnGoods().get(COAL));
        assertEquals(5, offer.getRewardEmeralds());
        assertEquals(1, fixture.data().getAllTradeContracts().size());
    }

    @Test
    void oneSidedSurplusCreatesDeliveryOffer() {
        Fixture fixture = fixture();
        stock(fixture.home(), WHEAT, 100, 12);

        TradeContract offer = TradeOfferGenerator.ensureOffers(
                fixture.data(), fixture.home().getId(), 0L, OFFER_RULES, CONTRACT_RULES)
                .get(0);

        assertEquals(TradeContractType.DELIVERY, offer.getType());
        assertEquals(6, offer.getRequiredGoods().get(WHEAT));
        assertTrue(offer.getReturnGoods().isEmpty());
    }

    @Test
    void noWarehouseSurplusCreatesNoOffer() {
        Fixture fixture = fixture();

        assertTrue(TradeOfferGenerator.ensureOffers(
                fixture.data(), fixture.home().getId(), 0L, OFFER_RULES, CONTRACT_RULES).isEmpty());
    }

    private static Fixture fixture() {
        SettlementSavedData data = new SettlementSavedData();
        Settlement home = new Settlement(UUID.randomUUID(), "Home", BlockPos.ZERO, 64);
        Settlement target = new Settlement(UUID.randomUUID(), "Target", new BlockPos(200, 0, 0), 64);
        data.registerSettlement(home);
        data.registerSettlement(target);
        data.discoverSettlement(home.getId(), target.getId());
        return new Fixture(data, home, target);
    }

    private static void stock(Settlement settlement, ResourceLocation itemId, int capacity, int amount) {
        settlement.getWarehouse().configureCapacity(itemId, capacity);
        settlement.getWarehouse().add(itemId, amount);
    }

    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value));
    }

    private record Fixture(SettlementSavedData data, Settlement home, Settlement target) {
    }
}
