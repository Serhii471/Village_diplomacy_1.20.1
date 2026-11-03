package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.diplomacy.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RouteSnapshotTest {
    @Test
    void serverSnapshotContainsPersistedRouteAndRoundTripsThroughNetwork() {
        SettlementSavedData data = new SettlementSavedData();
        UUID leader = UUID.randomUUID();
        Settlement home = new Settlement(UUID.randomUUID(), "Home", BlockPos.ZERO, 64);
        Settlement target = new Settlement(UUID.randomUUID(), "Target", new BlockPos(200, 0, 0), 64);
        home.setLeaderUuid(leader);
        data.registerSettlement(home);
        data.registerSettlement(target);
        data.discoverSettlement(home.getId(), target.getId());
        TradeRouteService.act(data, home.getId(), target.getId(), leader, TradeRouteService.Action.CREATE, "Road");
        RouteSnapshot snapshot = RouteSnapshot.create(data, home, leader, BlockPos.ZERO, false, false);
        assertTrue(snapshot.canManage());
        assertEquals("Road", snapshot.targets().get(0).routeName());
        assertEquals(200, snapshot.targets().get(0).distance());
        assertEquals("e", snapshot.targets().get(0).bearing());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            snapshot.write(buffer);
            assertEquals(snapshot, RouteSnapshot.read(buffer));
        } finally {
            buffer.release();
        }
        assertFalse(RouteSnapshot.create(data, home, UUID.randomUUID(), BlockPos.ZERO, false, true).canManage());
        assertTrue(RouteSnapshot.create(data, home, UUID.randomUUID(), BlockPos.ZERO, true, true).canManage());
    }

    @Test
    void malformedTargetCountIsRejected() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(UUID.randomUUID());
            buffer.writeUtf("Home");
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeVarInt(257);
            assertThrows(IllegalArgumentException.class, () -> RouteSnapshot.read(buffer));
        } finally {
            buffer.release();
        }
    }
}
