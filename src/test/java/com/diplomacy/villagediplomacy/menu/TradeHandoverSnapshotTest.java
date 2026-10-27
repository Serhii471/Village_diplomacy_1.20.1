package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.TradeContract;
import com.diplomacy.villagediplomacy.diplomacy.TradeContractService;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeHandoverSnapshotTest {
    private static final ResourceLocation WHEAT = id("minecraft:wheat");
    private static final ResourceLocation COAL = id("minecraft:coal");
    private static final TradeContractService.ContractRules RULES =
            new TradeContractService.ContractRules(72_000L, 10, 10);

    @Test
    void snapshotContainsServerProgressInventoryAndRoundTrips() {
        SettlementSavedData data = new SettlementSavedData();
        Settlement source = new Settlement(UUID.randomUUID(), "Source", BlockPos.ZERO, 64);
        Settlement destination = new Settlement(UUID.randomUUID(), "Destination", new BlockPos(200, 64, 0), 64);
        data.registerSettlement(source);
        data.registerSettlement(destination);
        data.discoverSettlement(source.getId(), destination.getId());
        source.getWarehouse().configureCapacity(WHEAT, 100);
        source.getWarehouse().add(WHEAT, 40);
        destination.getWarehouse().configureCapacity(COAL, 100);
        destination.getWarehouse().add(COAL, 20);
        UUID carrier = UUID.randomUUID();
        TradeContract contract = TradeContractService.createTrialExchangeOffer(
                data, source.getId(), destination.getId(),
                Map.of(WHEAT, 16), Map.of(COAL, 8), 3, 0L, RULES);
        TradeContractService.acceptContract(data, contract.getId(), carrier, 1L);

        TradeHandoverSnapshot snapshot = TradeHandoverSnapshot.create(
                data, destination, carrier, 2L, itemId -> itemId.equals(WHEAT) ? 12 : 0);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        snapshot.write(buffer);
        TradeHandoverSnapshot restored = TradeHandoverSnapshot.read(buffer);

        assertEquals(snapshot, restored);
        assertEquals(1, restored.contracts().size());
        TradeHandoverSnapshot.ContractEntry entry = restored.contracts().get(0);
        assertEquals(TradeHandoverSnapshot.Phase.OUTBOUND_DELIVERY, entry.phase());
        assertTrue(entry.actionable());
        assertEquals(12, entry.outboundGoods().get(0).inventory());
        assertEquals(1, entry.returnGoods().size());
    }

    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value));
    }
}
