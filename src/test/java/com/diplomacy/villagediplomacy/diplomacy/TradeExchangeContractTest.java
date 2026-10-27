package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TradeExchangeContractTest {
    private static final ResourceLocation WHEAT = id("minecraft:wheat");
    private static final ResourceLocation COAL = id("minecraft:coal");
    private static final TradeContractService.ContractRules RULES =
            new TradeContractService.ContractRules(72_000L, 10, 10);

    @Test
    void exchangeReservesBothWarehousesAndCompletesAfterReturnLeg() {
        Fixture fixture = fixture(40, 30);
        UUID carrier = UUID.randomUUID();
        TradeContract contract = offer(fixture);

        assertEquals(TradeContractService.ActivationResult.ACTIVATED,
                TradeContractService.acceptContract(fixture.data(), contract.getId(), carrier, 1L));
        assertEquals(16, fixture.source().getWarehouse().getStock(WHEAT).reserved());
        assertEquals(8, fixture.destination().getWarehouse().getStock(COAL).reserved());

        assertEquals(16, TradeContractService.issueGoods(
                fixture.data(), contract.getId(), carrier, WHEAT, 16, 2L, RULES));
        assertFalse(TradeContractService.recordDelivery(
                fixture.data(), contract.getId(), carrier, WHEAT, 16, 3L, RULES).completed());
        assertEquals(8, TradeContractService.issueReturnGoods(
                fixture.data(), contract.getId(), carrier, COAL, 8, 4L, RULES));
        assertEquals(new TradeContractService.DeliveryResult(8, true),
                TradeContractService.recordReturnDelivery(
                        fixture.data(), contract.getId(), carrier, COAL, 8, 5L, RULES));

        assertEquals(TradeContractStatus.COMPLETED, contract.getStatus());
        assertEquals(new VirtualWarehouse.StockView(8, 0, 8),
                fixture.source().getWarehouse().getStock(COAL));
        assertEquals(new VirtualWarehouse.StockView(16, 0, 16),
                fixture.destination().getWarehouse().getStock(WHEAT));
        assertEquals(10, fixture.source().getReputationWith(fixture.destination().getId()));
        assertEquals(3, TradeContractService.claimReward(fixture.data(), contract.getId(), carrier));
        assertEquals(0, TradeContractService.claimReward(fixture.data(), contract.getId(), carrier));
    }

    @Test
    void insufficientReturnStockRollsBackOutboundReservation() {
        Fixture fixture = fixture(40, 4);
        TradeContract contract = offer(fixture);

        assertEquals(TradeContractService.ActivationResult.INSUFFICIENT_STOCK,
                TradeContractService.acceptContract(
                        fixture.data(), contract.getId(), UUID.randomUUID(), 1L));

        assertEquals(0, fixture.source().getWarehouse().getStock(WHEAT).reserved());
        assertEquals(0, fixture.destination().getWarehouse().getStock(COAL).reserved());
        assertEquals(TradeContractStatus.OFFERED, contract.getStatus());
    }

    @Test
    void returnLegProgressSurvivesPersistence() {
        Fixture fixture = fixture(40, 30);
        UUID carrier = UUID.randomUUID();
        TradeContract contract = offer(fixture);
        TradeContractService.acceptContract(fixture.data(), contract.getId(), carrier, 1L);
        TradeContractService.recordDelivery(
                fixture.data(), contract.getId(), carrier, WHEAT, 16, 2L, RULES);
        TradeContractService.issueReturnGoods(
                fixture.data(), contract.getId(), carrier, COAL, 5, 3L, RULES);

        SettlementSavedData loaded = SettlementSavedData.load(fixture.data().save(new CompoundTag()));
        TradeContract restored = loaded.getTradeContract(contract.getId());

        assertNotNull(restored);
        assertEquals(TradeContractType.EXCHANGE, restored.getType());
        assertEquals(8, restored.getReturnGoods().get(COAL));
        assertEquals(5, restored.getReturnIssuedAmount(COAL));
        assertEquals(0, restored.getReturnDeliveredAmount(COAL));
    }

    private static TradeContract offer(Fixture fixture) {
        return TradeContractService.createTrialExchangeOffer(
                fixture.data(), fixture.source().getId(), fixture.destination().getId(),
                Map.of(WHEAT, 16), Map.of(COAL, 8), 3, 0L, RULES);
    }

    private static Fixture fixture(int wheat, int coal) {
        SettlementSavedData data = new SettlementSavedData();
        Settlement source = new Settlement(UUID.randomUUID(), "Source", BlockPos.ZERO, 64);
        Settlement destination = new Settlement(UUID.randomUUID(), "Destination", new BlockPos(200, 0, 0), 64);
        data.registerSettlement(source);
        data.registerSettlement(destination);
        data.discoverSettlement(source.getId(), destination.getId());
        source.getWarehouse().configureCapacity(WHEAT, 100);
        source.getWarehouse().add(WHEAT, wheat);
        destination.getWarehouse().configureCapacity(COAL, 100);
        destination.getWarehouse().add(COAL, coal);
        return new Fixture(data, source, destination);
    }

    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value));
    }

    private record Fixture(SettlementSavedData data, Settlement source, Settlement destination) {
    }
}
