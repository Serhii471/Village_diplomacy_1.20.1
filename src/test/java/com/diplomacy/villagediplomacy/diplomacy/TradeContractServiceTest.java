package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeContractServiceTest {
    private static final ResourceLocation WHEAT = id("minecraft:wheat");
    private static final ResourceLocation WOOL = id("minecraft:white_wool");
    private static final TradeContractService.ContractRules RULES =
            new TradeContractService.ContractRules(3L * DiplomacyBalance.MINECRAFT_DAY_TICKS, 10, 10);

    @Test
    void activeContractReservesStockTracksPartialDeliveryAndPaysOnce() {
        Fixture fixture = fixture();
        fixture.source().getWarehouse().configureCapacity(WHEAT, 100);
        fixture.source().getWarehouse().add(WHEAT, 40);
        UUID carrierId = UUID.randomUUID();

        TradeContract contract = offer(fixture, Map.of(WHEAT, 16), 3, 1_000L);
        assertEquals(TradeContractService.ActivationResult.ACTIVATED,
                TradeContractService.acceptContract(fixture.data(), contract.getId(), carrierId, 1_100L));
        assertEquals(new VirtualWarehouse.StockView(40, 16, 100),
                fixture.source().getWarehouse().getStock(WHEAT));

        assertEquals(10, TradeContractService.issueGoods(
                fixture.data(), contract.getId(), carrierId, WHEAT, 10, 1_200L));
        assertEquals(new TradeContractService.DeliveryResult(6, false),
                TradeContractService.recordDelivery(
                        fixture.data(), contract.getId(), carrierId, WHEAT, 6, 1_300L, RULES));
        assertEquals(new TradeContractService.DeliveryResult(10, true),
                TradeContractService.recordDelivery(
                        fixture.data(), contract.getId(), carrierId, WHEAT, 20, 1_400L, RULES));

        assertEquals(TradeContractStatus.COMPLETED, contract.getStatus());
        assertEquals(10, contract.getIssuedAmount(WHEAT));
        assertEquals(16, contract.getDeliveredAmount(WHEAT));
        assertEquals(new VirtualWarehouse.StockView(30, 0, 100),
                fixture.source().getWarehouse().getStock(WHEAT));
        assertEquals(1, relation(fixture.source(), fixture.destination()).getSuccessfulDeliveries());
        assertEquals(10, relation(fixture.source(), fixture.destination()).getReputation());
        assertEquals(new VirtualWarehouse.StockView(16, 0, 16),
                fixture.destination().getWarehouse().getStock(WHEAT));
        assertEquals(3, TradeContractService.claimReward(fixture.data(), contract.getId(), carrierId));
        assertEquals(0, TradeContractService.claimReward(fixture.data(), contract.getId(), carrierId));
        assertTrue(contract.isRewardClaimed());
    }

    @Test
    void insufficientMultiItemStockLeavesNoPartialReservations() {
        Fixture fixture = fixture();
        fixture.source().getWarehouse().configureCapacity(WHEAT, 100);
        fixture.source().getWarehouse().configureCapacity(WOOL, 100);
        fixture.source().getWarehouse().add(WHEAT, 40);
        fixture.source().getWarehouse().add(WOOL, 2);
        Map<ResourceLocation, Integer> goods = new LinkedHashMap<>();
        goods.put(WHEAT, 16);
        goods.put(WOOL, 4);
        TradeContract contract = offer(fixture, goods, 3, 0L);

        assertEquals(TradeContractService.ActivationResult.INSUFFICIENT_STOCK,
                TradeContractService.acceptContract(
                        fixture.data(), contract.getId(), UUID.randomUUID(), 1L));

        assertEquals(0, fixture.source().getWarehouse().getStock(WHEAT).reserved());
        assertEquals(0, fixture.source().getWarehouse().getStock(WOOL).reserved());
        assertEquals(TradeContractStatus.OFFERED, contract.getStatus());
    }

    @Test
    void invalidCarrierCannotLeaveStockReserved() {
        Fixture fixture = fixture();
        fixture.source().getWarehouse().configureCapacity(WHEAT, 100);
        fixture.source().getWarehouse().add(WHEAT, 40);
        TradeContract contract = offer(fixture, Map.of(WHEAT, 16), 3, 0L);

        assertThrows(NullPointerException.class, () -> TradeContractService.acceptContract(
                fixture.data(), contract.getId(), null, 1L));

        assertEquals(0, fixture.source().getWarehouse().getStock(WHEAT).reserved());
        assertEquals(TradeContractStatus.OFFERED, contract.getStatus());
    }

    @Test
    void expiryReleasesUnissuedStockAndPenalizesActiveContractOnce() {
        Fixture fixture = fixture();
        fixture.source().getWarehouse().configureCapacity(WHEAT, 100);
        fixture.source().getWarehouse().add(WHEAT, 40);
        UUID carrierId = UUID.randomUUID();
        TradeContract contract = offer(fixture, Map.of(WHEAT, 16), 3, 0L);
        TradeContractService.acceptContract(fixture.data(), contract.getId(), carrierId, 10L);
        TradeContractService.issueGoods(fixture.data(), contract.getId(), carrierId, WHEAT, 6, 20L);

        long overdue = contract.getDeadlineGameTime() + 1L;
        assertEquals(1, TradeContractService.expireDueContracts(fixture.data(), overdue, RULES));
        assertEquals(0, TradeContractService.expireDueContracts(fixture.data(), overdue + 1L, RULES));

        assertEquals(TradeContractStatus.FAILED, contract.getStatus());
        assertEquals(new VirtualWarehouse.StockView(34, 0, 100),
                fixture.source().getWarehouse().getStock(WHEAT));
        assertEquals(1, relation(fixture.source(), fixture.destination()).getFailedContracts());
        assertEquals(-10, relation(fixture.source(), fixture.destination()).getReputation());
        assertEquals(0, TradeContractService.claimReward(fixture.data(), contract.getId(), carrierId));
    }

    @Test
    void unacceptedExpiredOfferDoesNotDamageRelations() {
        Fixture fixture = fixture();
        TradeContract contract = offer(fixture, Map.of(WHEAT, 1), 1, 0L);

        assertEquals(1, TradeContractService.expireDueContracts(
                fixture.data(), contract.getDeadlineGameTime() + 1L, RULES));

        assertEquals(TradeContractStatus.EXPIRED, contract.getStatus());
        assertEquals(0, relation(fixture.source(), fixture.destination()).getFailedContracts());
        assertEquals(0, relation(fixture.source(), fixture.destination()).getReputation());
    }

    @Test
    void contractProgressAndReservationSurviveWorldDataRoundTrip() {
        Fixture fixture = fixture();
        fixture.source().getWarehouse().configureCapacity(WHEAT, 100);
        fixture.source().getWarehouse().add(WHEAT, 40);
        UUID carrierId = UUID.randomUUID();
        TradeContract contract = offer(fixture, Map.of(WHEAT, 16), 3, 1_000L);
        TradeContractService.acceptContract(fixture.data(), contract.getId(), carrierId, 1_100L);
        TradeContractService.issueGoods(fixture.data(), contract.getId(), carrierId, WHEAT, 5, 1_200L);
        TradeContractService.recordDelivery(
                fixture.data(), contract.getId(), carrierId, WHEAT, 3, 1_300L, RULES);

        CompoundTag saved = fixture.data().save(new CompoundTag());
        SettlementSavedData loaded = SettlementSavedData.load(saved);
        TradeContract loadedContract = loaded.getTradeContract(contract.getId());

        assertNotNull(loadedContract);
        assertEquals(TradeContractStatus.ACTIVE, loadedContract.getStatus());
        assertEquals(carrierId, loadedContract.getCarrierId());
        assertEquals(5, loadedContract.getIssuedAmount(WHEAT));
        assertEquals(3, loadedContract.getDeliveredAmount(WHEAT));
        assertFalse(loadedContract.isRewardClaimed());
        Settlement loadedSource = loaded.getSettlement(fixture.source().getId());
        Settlement loadedDestination = loaded.getSettlement(fixture.destination().getId());
        assertNotNull(loadedSource);
        assertNotNull(loadedDestination);
        assertEquals(new VirtualWarehouse.StockView(35, 11, 100),
                loadedSource.getWarehouse().getStock(WHEAT));
        assertEquals(new VirtualWarehouse.StockView(3, 0, 3),
                loadedDestination.getWarehouse().getStock(WHEAT));
        assertEquals(SettlementSavedData.CURRENT_DATA_VERSION, saved.getInt("DataVersion"));
    }

    @Test
    void onlyDiscoveredSettlementsCanCreateTrialOffer() {
        SettlementSavedData data = new SettlementSavedData();
        Settlement source = new Settlement(UUID.randomUUID(), "Source", BlockPos.ZERO, 64);
        Settlement destination = new Settlement(
                UUID.randomUUID(), "Destination", new BlockPos(200, 64, 0), 64);
        data.registerSettlement(source);
        data.registerSettlement(destination);

        assertThrows(IllegalStateException.class, () -> TradeContractService.createTrialDeliveryOffer(
                data, source.getId(), destination.getId(), Map.of(WHEAT, 8), 1, 0L, RULES));
    }

    @Test
    void wrongCarrierAndWrongResourceAreRejected() {
        Fixture fixture = fixture();
        fixture.source().getWarehouse().configureCapacity(WHEAT, 100);
        fixture.source().getWarehouse().add(WHEAT, 40);
        UUID carrierId = UUID.randomUUID();
        TradeContract contract = offer(fixture, Map.of(WHEAT, 16), 3, 0L);
        TradeContractService.acceptContract(fixture.data(), contract.getId(), carrierId, 1L);

        assertThrows(IllegalArgumentException.class, () -> TradeContractService.issueGoods(
                fixture.data(), contract.getId(), UUID.randomUUID(), WHEAT, 1, 2L));
        assertThrows(IllegalArgumentException.class, () -> TradeContractService.recordDelivery(
                fixture.data(), contract.getId(), carrierId, WOOL, 1, 2L, RULES));
    }

    private static TradeContract offer(Fixture fixture,
                                       Map<ResourceLocation, Integer> goods,
                                       int reward,
                                       long gameTime) {
        return TradeContractService.createTrialDeliveryOffer(
                fixture.data(), fixture.source().getId(), fixture.destination().getId(),
                goods, reward, gameTime, RULES);
    }

    private static SettlementRelationship relation(Settlement source, Settlement destination) {
        return source.findRelationship(destination.getId()).orElseThrow();
    }

    private static Fixture fixture() {
        SettlementSavedData data = new SettlementSavedData();
        Settlement source = new Settlement(UUID.randomUUID(), "Source", BlockPos.ZERO, 64);
        Settlement destination = new Settlement(
                UUID.randomUUID(), "Destination", new BlockPos(200, 64, 0), 64);
        data.registerSettlement(source);
        data.registerSettlement(destination);
        data.discoverSettlement(source.getId(), destination.getId());
        return new Fixture(data, source, destination);
    }

    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value));
    }

    private record Fixture(SettlementSavedData data, Settlement source, Settlement destination) {
    }
}
