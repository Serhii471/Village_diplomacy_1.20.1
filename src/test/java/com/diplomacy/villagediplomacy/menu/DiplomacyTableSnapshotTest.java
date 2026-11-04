package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSpecialization;
import com.diplomacy.villagediplomacy.diplomacy.SettlementProductionService;
import com.diplomacy.villagediplomacy.diplomacy.TradeContract;
import com.diplomacy.villagediplomacy.diplomacy.TradeContractType;
import com.diplomacy.villagediplomacy.diplomacy.TradeAgreementStatus;
import com.diplomacy.villagediplomacy.diplomacy.AllianceStatus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiplomacyTableSnapshotTest {
    @Test
    void serverSnapshotContainsCurrentDomainDataAndSurvivesNetworkRoundTrip() {
        SettlementSavedData data = new SettlementSavedData();
        UUID leaderId = UUID.randomUUID();
        Settlement home = new Settlement(UUID.randomUUID(), "Sosnove", new BlockPos(10, 64, 20), 64);
        Settlement neighbor = new Settlement(UUID.randomUUID(), "Lisove", new BlockPos(200, 64, 20), 64);
        home.setLeaderUuid(leaderId);
        home.setSpecializations(SettlementSpecialization.PLAINS, SettlementSpecialization.FOREST);
        data.registerSettlement(home);
        data.registerSettlement(neighbor);

        ResourceLocation wheat = Objects.requireNonNull(ResourceLocation.tryParse("minecraft:wheat"));
        ResourceLocation coal = Objects.requireNonNull(ResourceLocation.tryParse("minecraft:coal"));
        home.getWarehouse().configureCapacity(wheat, 100);
        home.getWarehouse().add(wheat, 40);
        home.getWarehouse().tryReserve(wheat, 10);
        data.discoverSettlement(home.getId(), neighbor.getId());
        data.adjustReputationBetween(home.getId(), neighbor.getId(), 25);

        TradeContract contract = new TradeContract(
                UUID.randomUUID(), home.getId(), neighbor.getId(), TradeContractType.EXCHANGE,
                Map.of(wheat, 16), Map.of(coal, 8), true, 1_000L, 73_000L, 5
        );
        data.registerTradeContract(contract);

        DiplomacyTableSnapshot snapshot = DiplomacyTableSnapshot.create(
                data, home, leaderId, 25_000L, ignored -> "Arthur", productionSettings()
        );

        assertTrue(snapshot.canManage());
        assertEquals("Arthur", snapshot.leaderName());
        assertFalse(snapshot.production().isEmpty());
        assertEquals(1, snapshot.warehouse().size());
        assertEquals(1, snapshot.relations().size());
        assertEquals(neighbor.getId(), snapshot.relations().get(0).settlementId());
        assertTrue(snapshot.relations().get(0).production().isEmpty());
        assertEquals(190L, snapshot.relations().get(0).distanceBlocks());
        assertEquals(1, snapshot.contracts().size());
        assertEquals(contract.getId(), snapshot.contracts().get(0).contractId());
        assertEquals("exchange", snapshot.contracts().get(0).type());
        assertEquals(1, snapshot.contracts().get(0).returnGoods().size());
        assertEquals(190L, snapshot.contracts().get(0).distanceBlocks());
        assertEquals(48_000L, snapshot.contracts().get(0).remainingTicks());

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        snapshot.write(buffer);
        DiplomacyTableSnapshot decoded = DiplomacyTableSnapshot.read(buffer);

        assertEquals(snapshot, decoded);
    }

    @Test
    void activeAllyRemainsListedAndSharesProductionOnlyWhileAllianceIsActive() {
        SettlementSavedData data = new SettlementSavedData();
        UUID leader = UUID.randomUUID();
        Settlement home = new Settlement(UUID.randomUUID(), "Home", BlockPos.ZERO, 64);
        Settlement ally = new Settlement(UUID.randomUUID(), "Ally", new BlockPos(200, 64, 0), 64);
        home.setLeaderUuid(leader);
        ally.setSpecializations(SettlementSpecialization.MOUNTAIN, null);
        data.registerSettlement(home);
        data.registerSettlement(ally);
        data.setTradeAgreementStatusBetween(home.getId(), ally.getId(), TradeAgreementStatus.ACTIVE);
        data.setAllianceStatusBetween(home.getId(), ally.getId(), AllianceStatus.ACTIVE);
        home.getOrCreateRelationship(ally.getId()).setKnown(false);
        var snapshot = DiplomacyTableSnapshot.create(data, home, leader, 0L, ignored -> "Leader", productionSettings());
        assertEquals(1, snapshot.relations().size());
        assertFalse(snapshot.relations().get(0).production().isEmpty());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            snapshot.write(buffer);
            assertEquals(snapshot, DiplomacyTableSnapshot.read(buffer));
        } finally {
            buffer.release();
        }
        home.getOrCreateRelationship(ally.getId()).setKnown(true);
        data.setAllianceStatusBetween(home.getId(), ally.getId(), AllianceStatus.NONE);
        var closed = DiplomacyTableSnapshot.create(data, home, leader, 0L, ignored -> "Leader", productionSettings());
        assertTrue(closed.relations().get(0).production().isEmpty());
    }

    @Test
    void nonLeaderReceivesViewOnlySnapshot() {
        SettlementSavedData data = new SettlementSavedData();
        Settlement settlement = new Settlement(UUID.randomUUID(), "Sosnove", BlockPos.ZERO, 64);
        settlement.setLeaderUuid(UUID.randomUUID());
        data.registerSettlement(settlement);

        DiplomacyTableSnapshot snapshot = DiplomacyTableSnapshot.create(
                data, settlement, UUID.randomUUID(), 0L, ignored -> "Leader", productionSettings()
        );

        assertFalse(snapshot.canManage());
    }

    @Test
    void largeSnapshotStaysBelowForgeMenuPayloadLimit() {
        SettlementSavedData data = new SettlementSavedData();
        UUID leader = UUID.randomUUID();
        Settlement home = new Settlement(UUID.randomUUID(), "H".repeat(1_000), BlockPos.ZERO, 64);
        Settlement neighbor = new Settlement(
                UUID.randomUUID(), "N".repeat(1_000), new BlockPos(200, 64, 0), 64);
        home.setLeaderUuid(leader);
        data.registerSettlement(home);
        data.registerSettlement(neighbor);
        data.discoverSettlement(home.getId(), neighbor.getId());
        ResourceLocation wheat = Objects.requireNonNull(ResourceLocation.tryParse("minecraft:wheat"));
        for (int index = 0; index < 128; index++) {
            data.registerTradeContract(new TradeContract(
                    UUID.randomUUID(), home.getId(), neighbor.getId(),
                    Map.of(wheat, 1), true, index, 72_000L + index, 1));
        }

        DiplomacyTableSnapshot snapshot = DiplomacyTableSnapshot.create(
                data, home, leader, 0L, ignored -> "Leader", productionSettings());

        assertTrue(snapshot.contracts().size() < 128);
        assertTrue(snapshot.contracts().size() > 0);
        assertTrue(DiplomacyTableSnapshot.networkSize(snapshot) <= DiplomacyTableSnapshot.MAX_NETWORK_BYTES);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBlockPos(BlockPos.ZERO);
            snapshot.write(buffer);
            assertTrue(buffer.readableBytes() < 32_600);
            buffer.readBlockPos();
            assertEquals(snapshot, DiplomacyTableSnapshot.read(buffer));
        } finally {
            buffer.release();
        }
    }

    private static SettlementProductionService.ProductionSettings productionSettings() {
        return new SettlementProductionService.ProductionSettings(0.7D, 0.5D, 5);
    }
}
