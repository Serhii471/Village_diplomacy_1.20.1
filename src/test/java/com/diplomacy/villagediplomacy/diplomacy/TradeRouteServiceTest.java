package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.diplomacy.villagediplomacy.diplomacy.TradeRouteService.Action.*;
import static com.diplomacy.villagediplomacy.diplomacy.TradeRouteService.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeRouteServiceTest {
    @Test
    void lifecycleIsSymmetricIdempotentAndPersisted() {
        Fixture f = fixture();
        assertEquals(UPDATED, act(f, CREATE, "Road"));
        TradeRoute route = f.data.findTradeRoute(f.target.getId(), f.home.getId());
        assertNotNull(route);
        assertEquals(UNAVAILABLE, act(f, CREATE, "Duplicate"));
        assertEquals(1, f.data.getTradeRoutes().size());
        assertEquals(UPDATED, act(f, RENAME, "Forest road"));
        assertEquals(UPDATED, act(f, CLOSE, ""));
        assertEquals(UNAVAILABLE, act(f, CLOSE, ""));
        SettlementSavedData loaded = SettlementSavedData.load(f.data.save(new CompoundTag()));
        assertTrue(loaded.findTradeRoute(f.home.getId(), f.target.getId()).closed());
        assertEquals(UPDATED, act(f, CREATE, "Reopened"));
        assertEquals(route.id(), f.data.findTradeRoute(f.home.getId(), f.target.getId()).id());
    }

    @Test
    void rejectsUnauthorizedUnknownSelfAndUndiscoveredTargets() {
        Fixture f = fixture();
        assertEquals(NOT_LEADER, TradeRouteService.act(f.data, f.home.getId(), f.target.getId(), UUID.randomUUID(), CREATE, "X"));
        assertEquals(UNAVAILABLE, TradeRouteService.act(f.data, f.home.getId(), f.home.getId(), f.leader, CREATE, "X"));
        assertEquals(UNAVAILABLE, TradeRouteService.act(f.data, f.home.getId(), UUID.randomUUID(), f.leader, CREATE, "X"));
        f.home.getOrCreateRelationship(f.target.getId()).setKnown(false);
        assertEquals(UNDISCOVERED, act(f, CREATE, "X"));
        assertTrue(f.data.getTradeRoutes().isEmpty());
    }

    @Test
    void namesAreBoundedAndFormattingCodesAreRemoved() {
        Fixture f = fixture();
        assertEquals(INVALID_NAME, act(f, CREATE, "  "));
        assertEquals(INVALID_NAME, act(f, CREATE, "x".repeat(65)));
        assertEquals(UPDATED, act(f, CREATE, "\n§ Safe\t road "));
        assertEquals("Safe road", f.data.findTradeRoute(f.home.getId(), f.target.getId()).name());
    }

    @Test
    void reciprocalNearbySignsMarkRouteAndBreakingOrRetargetingUnmarksIt() {
        Fixture f = fixture();
        act(f, CREATE, "Road");
        BlockPos first = new BlockPos(10, 64, 0);
        BlockPos second = new BlockPos(290, 64, 0);
        assertEquals(UPDATED, bind(f, f.home, f.target, first, f.leader));
        assertFalse(TradeRouteService.isMarked(f.data, f.home.getId(), f.target.getId()));
        assertEquals(UPDATED, bind(f, f.target, f.home, second, f.leader));
        assertTrue(TradeRouteService.isMarked(f.data, f.target.getId(), f.home.getId()));
        SettlementSavedData restored = SettlementSavedData.load(f.data.save(new CompoundTag()));
        assertTrue(TradeRouteService.isMarked(restored, f.home.getId(), f.target.getId()));
        restored.removeSignpost(second);
        assertFalse(TradeRouteService.isMarked(restored, f.home.getId(), f.target.getId()));
        bind(f, f.home, f.target, second, f.leader);
        assertFalse(TradeRouteService.isMarked(f.data, f.home.getId(), f.target.getId()));
    }

    @Test
    void distantOrUnauthorizedSignsCannotMarkRouteAndClosedRoutesHaveNoBonus() {
        Fixture f = fixture();
        act(f, CREATE, "Road");
        assertEquals(NOT_LEADER, TradeRouteService.bindSignpost(f.data, f.target.getId(), f.home.getId(),
                f.target.getCenter(), UUID.randomUUID(), UUID.randomUUID()));
        bind(f, f.home, f.target, new BlockPos(2_000, 64, 0), f.leader);
        bind(f, f.target, f.home, f.target.getCenter(), f.leader);
        assertFalse(TradeRouteService.isMarked(f.data, f.home.getId(), f.target.getId()));
        bind(f, f.home, f.target, f.home.getCenter(), f.leader);
        assertTrue(TradeRouteService.isMarked(f.data, f.home.getId(), f.target.getId()));
        act(f, CLOSE, "");
        assertFalse(TradeRouteService.isMarked(f.data, f.home.getId(), f.target.getId()));
        assertEquals(3, f.data.getSignposts().size());
    }

    @Test
    void markedRoutesIncreaseOnlyNewOfferRewardsAndRemovingSettlementCleansReferences() {
        Fixture plain = fixture();
        Fixture marked = fixture();
        ResourceLocation wheat = ResourceLocation.tryParse("minecraft:wheat");
        for (Fixture f : new Fixture[]{plain, marked}) {
            f.home.getWarehouse().configureCapacity(wheat, 100);
            f.home.getWarehouse().add(wheat, 40);
        }
        act(marked, CREATE, "Road");
        bind(marked, marked.home, marked.target, marked.home.getCenter(), marked.leader);
        bind(marked, marked.target, marked.home, marked.target.getCenter(), marked.leader);
        var rules = new TradeContractService.ContractRules(72_000L, 10, 10);
        var offers = new TradeOfferGenerator.OfferRules(3, 16, 3, 12);
        var baseline = TradeOfferGenerator.ensureOffers(plain.data, plain.home.getId(), 0L, offers, rules).get(0);
        var bonus = TradeOfferGenerator.ensureOffers(marked.data, marked.home.getId(), 0L, offers, rules).get(0);
        assertEquals(baseline.getRewardEmeralds() + 1, bonus.getRewardEmeralds());
        marked.data.removeSignpost(marked.target.getCenter());
        assertEquals(baseline.getRewardEmeralds() + 1, bonus.getRewardEmeralds());
        marked.data.removeSettlement(marked.target.getId());
        assertTrue(marked.data.getTradeRoutes().isEmpty());
        assertTrue(marked.data.getSignposts().isEmpty());
    }

    @Test
    void oldWorldLoadsWithoutRoutesAndMalformedRouteIsSkipped() {
        Fixture f = fixture();
        CompoundTag saved = f.data.save(new CompoundTag());
        saved.putInt("DataVersion", 5);
        saved.remove("TradeRoutes");
        saved.remove("RouteSignposts");
        SettlementSavedData legacy = SettlementSavedData.load(saved);
        assertEquals(2, legacy.getAllSettlements().size());
        assertTrue(legacy.getTradeRoutes().isEmpty());
        ListTag routes = new ListTag();
        routes.add(new CompoundTag());
        saved.put("TradeRoutes", routes);
        assertTrue(SettlementSavedData.load(saved).getTradeRoutes().isEmpty());
    }

    @Test
    void compassDirectionsMatchMinecraftCoordinates() {
        assertEquals("n", TradeRouteService.bearing(BlockPos.ZERO, new BlockPos(0, 0, -10)));
        assertEquals("e", TradeRouteService.bearing(BlockPos.ZERO, new BlockPos(10, 0, 0)));
        assertEquals("s", TradeRouteService.bearing(BlockPos.ZERO, new BlockPos(0, 0, 10)));
        assertEquals("w", TradeRouteService.bearing(BlockPos.ZERO, new BlockPos(-10, 0, 0)));
        assertEquals("nw", TradeRouteService.bearing(BlockPos.ZERO, new BlockPos(-10, 0, -10)));
    }

    private static TradeRouteService.Result bind(Fixture f, Settlement home, Settlement target, BlockPos pos, UUID owner) {
        return TradeRouteService.bindSignpost(f.data, home.getId(), target.getId(), pos, f.leader, owner);
    }

    private static TradeRouteService.Result act(Fixture f, TradeRouteService.Action action, String name) {
        return TradeRouteService.act(f.data, f.home.getId(), f.target.getId(), f.leader, action, name);
    }

    private static Fixture fixture() {
        SettlementSavedData data = new SettlementSavedData();
        UUID leader = UUID.randomUUID();
        Settlement home = new Settlement(UUID.randomUUID(), "Home", new BlockPos(0, 64, 0), 64);
        Settlement target = new Settlement(UUID.randomUUID(), "Target", new BlockPos(300, 64, 0), 64);
        home.setLeaderUuid(leader);
        data.registerSettlement(home);
        data.registerSettlement(target);
        data.discoverSettlement(home.getId(), target.getId());
        return new Fixture(data, home, target, leader);
    }

    private record Fixture(SettlementSavedData data, Settlement home, Settlement target, UUID leader) {
    }
}
