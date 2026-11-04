package com.diplomacy.villagediplomacy.item;

import com.diplomacy.villagediplomacy.diplomacy.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TradeRouteMapTest {
    @Test
    void mapStoresAuthoritativeIdsCoordinatesDimensionAndCurrentStatus() {
        SettlementSavedData data = new SettlementSavedData();
        UUID leader = UUID.randomUUID();
        Settlement home = new Settlement(UUID.randomUUID(), "Home", BlockPos.ZERO, 64);
        Settlement target = new Settlement(UUID.randomUUID(), "Target", new BlockPos(300, 0, 400), 64);
        home.setLeaderUuid(leader);
        data.registerSettlement(home);
        data.registerSettlement(target);
        data.discoverSettlement(home.getId(), target.getId());
        TradeRouteService.act(data, home.getId(), target.getId(), leader, TradeRouteService.Action.CREATE, "Road");
        var route = data.findTradeRoute(home.getId(), target.getId());
        var tag = TradeRouteMapData.create(route, home, target, "minecraft:overworld", data);
        assertEquals(route.id(), tag.getUUID("RouteId"));
        assertEquals(home.getId(), tag.getUUID("Source"));
        assertEquals(target.getId(), tag.getUUID("Target"));
        assertEquals(target.getCenter(), BlockPos.of(tag.getLong("TargetPos")));
        assertEquals("minecraft:overworld", tag.getString("Dimension"));
        assertEquals(500, tag.getLong("Distance"));
        assertEquals("active", tag.getString("Status"));
        assertEquals(route, TradeRouteMapData.resolve(data, tag, "minecraft:overworld"));
        assertNull(TradeRouteMapData.resolve(data, tag, "minecraft:the_nether"));
        var forged = tag.copy();
        forged.putUUID("RouteId", UUID.randomUUID());
        assertNull(TradeRouteMapData.resolve(data, forged, "minecraft:overworld"));
        forged = tag.copy();
        forged.putUUID("Target", UUID.randomUUID());
        assertNull(TradeRouteMapData.resolve(data, forged, "minecraft:overworld"));
        assertNull(TradeRouteMapData.resolve(data, null, "minecraft:overworld"));
        TradeRouteService.act(data, home.getId(), target.getId(), leader, TradeRouteService.Action.CLOSE, "");
        var refreshed = TradeRouteMapData.create(data.findTradeRoute(home.getId(), target.getId()), home, target,
                "minecraft:overworld", data);
        assertEquals("closed", refreshed.getString("Status"));
        assertEquals("active", tag.getString("Status"));
    }
}
