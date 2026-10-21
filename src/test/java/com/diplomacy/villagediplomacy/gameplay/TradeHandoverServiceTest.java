package com.diplomacy.villagediplomacy.gameplay;

import com.diplomacy.villagediplomacy.diplomacy.DiplomacyBalance;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.TradeContract;
import com.diplomacy.villagediplomacy.diplomacy.TradeContractService;
import com.diplomacy.villagediplomacy.diplomacy.TradeContractStatus;
import com.diplomacy.villagediplomacy.diplomacy.VirtualWarehouse;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeHandoverServiceTest {
    private static final ResourceLocation WHEAT = id("minecraft:wheat");
    private static final ResourceLocation EMERALD = id("minecraft:emerald");
    private static final TradeContractService.ContractRules RULES =
            new TradeContractService.ContractRules(3L * DiplomacyBalance.MINECRAFT_DAY_TICKS, 10, 10);

    @Test
    void carrierCanCollectAndDeliverOneContractInSeveralTrips() {
        Fixture fixture = fixture(16, 3);
        FakeInventory inventory = new FakeInventory(10);

        TradeHandoverService.HandoverSummary firstPickup = handle(
                fixture, fixture.source().getId(), inventory, 100L);
        assertEquals(10, firstPickup.totalIssued());
        assertEquals(10, inventory.count(WHEAT));
        assertEquals(new VirtualWarehouse.StockView(30, 6, 100),
                fixture.source().getWarehouse().getStock(WHEAT));

        TradeHandoverService.HandoverSummary firstDelivery = handle(
                fixture, fixture.destination().getId(), inventory, 200L);
        assertEquals(10, firstDelivery.totalDelivered());
        assertEquals(0, firstDelivery.completedContracts());
        assertEquals(0, inventory.count(WHEAT));

        TradeHandoverService.HandoverSummary secondPickup = handle(
                fixture, fixture.source().getId(), inventory, 300L);
        assertEquals(6, secondPickup.totalIssued());
        assertEquals(new VirtualWarehouse.StockView(24, 0, 100),
                fixture.source().getWarehouse().getStock(WHEAT));

        TradeHandoverService.HandoverSummary completion = handle(
                fixture, fixture.destination().getId(), inventory, 400L);
        assertEquals(6, completion.totalDelivered());
        assertEquals(1, completion.completedContracts());
        assertEquals(3, completion.rewardEmeralds());
        assertEquals(3, inventory.count(EMERALD));
        assertEquals(TradeContractStatus.COMPLETED, fixture.contract().getStatus());
        assertTrue(fixture.contract().isRewardClaimed());
        assertEquals(new VirtualWarehouse.StockView(16, 0, 16),
                fixture.destination().getWarehouse().getStock(WHEAT));
    }

    @Test
    void lostContractGoodsCanBeReplacedWithOrdinaryMatchingItems() {
        Fixture fixture = fixture(16, 3);
        FakeInventory inventory = new FakeInventory(64);
        inventory.preload(WHEAT, 16);

        TradeHandoverService.HandoverSummary completion = handle(
                fixture, fixture.destination().getId(), inventory, 100L);

        assertEquals(16, completion.totalDelivered());
        assertEquals(TradeContractStatus.COMPLETED, fixture.contract().getStatus());
        assertEquals(new VirtualWarehouse.StockView(40, 0, 100),
                fixture.source().getWarehouse().getStock(WHEAT));
        assertEquals(3, inventory.count(EMERALD));
    }

    @Test
    void overdueHandoverExpiresContractWithoutTakingPlayerItems() {
        Fixture fixture = fixture(16, 3);
        FakeInventory inventory = new FakeInventory(64);
        inventory.preload(WHEAT, 16);

        TradeHandoverService.HandoverSummary result = handle(
                fixture,
                fixture.destination().getId(),
                inventory,
                fixture.contract().getDeadlineGameTime() + 1L
        );

        assertFalse(result.handled());
        assertEquals(16, inventory.count(WHEAT));
        assertEquals(TradeContractStatus.EXPIRED, fixture.contract().getStatus());
        assertEquals(new VirtualWarehouse.StockView(40, 0, 100),
                fixture.source().getWarehouse().getStock(WHEAT));
    }

    @Test
    void fullInventoryDropsRewardWithoutAllowingSecondClaim() {
        Fixture fixture = fixture(1, 3);
        FakeInventory inventory = new FakeInventory(1);
        inventory.preload(WHEAT, 1);

        TradeHandoverService.HandoverSummary completion = handle(
                fixture, fixture.destination().getId(), inventory, 100L);
        TradeHandoverService.HandoverSummary repeated = handle(
                fixture, fixture.destination().getId(), inventory, 200L);

        assertEquals(3, completion.rewardEmeralds());
        assertEquals(1, inventory.count(EMERALD));
        assertEquals(2, inventory.dropped(EMERALD));
        assertFalse(repeated.handled());
        assertEquals(2, inventory.dropped(EMERALD));
    }

    @Test
    void completedUnpaidContractCanRedeemRewardAfterLaterHandover() {
        Fixture fixture = fixture(1, 3);
        TradeContractService.recordDelivery(
                fixture.data(),
                fixture.contract().getId(),
                fixture.carrierId(),
                WHEAT,
                1,
                100L,
                RULES
        );
        FakeInventory inventory = new FakeInventory(64);

        TradeHandoverService.HandoverSummary payout = handle(
                fixture, fixture.destination().getId(), inventory, 200L);
        TradeHandoverService.HandoverSummary repeated = handle(
                fixture, fixture.destination().getId(), inventory, 300L);

        assertEquals(3, payout.rewardEmeralds());
        assertEquals(1, payout.completedContracts());
        assertEquals(3, inventory.count(EMERALD));
        assertTrue(fixture.contract().isRewardClaimed());
        assertFalse(repeated.handled());
    }

    @Test
    void unrelatedSettlementCannotAccessCarrierContract() {
        Fixture fixture = fixture(16, 3);
        FakeInventory inventory = new FakeInventory(64);

        TradeHandoverService.HandoverSummary result = TradeHandoverService.handleAtSettlement(
                fixture.data(),
                UUID.randomUUID(),
                fixture.carrierId(),
                100L,
                inventory,
                RULES
        );

        assertFalse(result.handled());
        assertEquals(0, inventory.count(WHEAT));
        assertEquals(0, fixture.contract().getIssuedAmount(WHEAT));
    }

    private static TradeHandoverService.HandoverSummary handle(Fixture fixture,
                                                               UUID settlementId,
                                                               FakeInventory inventory,
                                                               long gameTime) {
        return TradeHandoverService.handleAtSettlement(
                fixture.data(), settlementId, fixture.carrierId(), gameTime, inventory, RULES);
    }

    private static Fixture fixture(int requiredWheat, int rewardEmeralds) {
        SettlementSavedData data = new SettlementSavedData();
        Settlement source = new Settlement(UUID.randomUUID(), "Source", BlockPos.ZERO, 64);
        Settlement destination = new Settlement(
                UUID.randomUUID(), "Destination", new BlockPos(200, 64, 0), 64);
        data.registerSettlement(source);
        data.registerSettlement(destination);
        data.discoverSettlement(source.getId(), destination.getId());
        source.getWarehouse().configureCapacity(WHEAT, 100);
        source.getWarehouse().add(WHEAT, 40);

        UUID carrierId = UUID.randomUUID();
        TradeContract contract = TradeContractService.createTrialDeliveryOffer(
                data,
                source.getId(),
                destination.getId(),
                Map.of(WHEAT, requiredWheat),
                rewardEmeralds,
                0L,
                RULES
        );
        TradeContractService.acceptContract(data, contract.getId(), carrierId, 1L);
        return new Fixture(data, source, destination, contract, carrierId);
    }

    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value));
    }

    private record Fixture(SettlementSavedData data,
                           Settlement source,
                           Settlement destination,
                           TradeContract contract,
                           UUID carrierId) {
    }

    private static final class FakeInventory implements TradeHandoverService.TradeInventoryPort {
        private final Map<ResourceLocation, Integer> contents = new LinkedHashMap<>();
        private final Map<ResourceLocation, Integer> dropped = new LinkedHashMap<>();
        private final int capacity;

        private FakeInventory(int capacity) {
            this.capacity = capacity;
        }

        private void preload(ResourceLocation itemId, int amount) {
            contents.merge(itemId, amount, Integer::sum);
        }

        private int dropped(ResourceLocation itemId) {
            return dropped.getOrDefault(itemId, 0);
        }

        @Override
        public int count(ResourceLocation itemId) {
            return contents.getOrDefault(itemId, 0);
        }

        @Override
        public int insert(ResourceLocation itemId, int amount) {
            int currentTotal = contents.values().stream().mapToInt(Integer::intValue).sum();
            int inserted = Math.min(amount, Math.max(0, capacity - currentTotal));
            if (inserted > 0) {
                contents.merge(itemId, inserted, Integer::sum);
            }
            return inserted;
        }

        @Override
        public int extract(ResourceLocation itemId, int amount) {
            int extracted = Math.min(amount, count(itemId));
            int remaining = count(itemId) - extracted;
            if (remaining == 0) {
                contents.remove(itemId);
            } else {
                contents.put(itemId, remaining);
            }
            return extracted;
        }

        @Override
        public void drop(ResourceLocation itemId, int amount) {
            dropped.merge(itemId, amount, Integer::sum);
        }
    }
}
