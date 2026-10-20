package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementSavedDataTest {
    @Test
    void completeDomainStateSurvivesNbtRoundTrip() {
        SettlementSavedData data = new SettlementSavedData();
        Settlement first = new Settlement(UUID.randomUUID(), "Sosnove", new BlockPos(10, 70, 20), 64);
        Settlement second = new Settlement(UUID.randomUUID(), "Lisove", new BlockPos(300, 68, -40), 64);
        data.registerSettlement(first);
        data.registerSettlement(second);

        first.setSpecializations(SettlementSpecialization.PLAINS, SettlementSpecialization.FOREST);
        first.setLastProductionGameTime(24_000L);
        ResourceLocation wheat = Objects.requireNonNull(ResourceLocation.tryParse("minecraft:wheat"));
        first.getWarehouse().configureCapacity(wheat, 84);
        first.getWarehouse().add(wheat, 40);
        first.getWarehouse().tryReserve(wheat, 16);

        data.discoverSettlement(first.getId(), second.getId());
        data.adjustReputationBetween(first.getId(), second.getId(), 25);
        data.setTradeAgreementStatusBetween(first.getId(), second.getId(), TradeAgreementStatus.ACTIVE);
        data.setAllianceStatusBetween(first.getId(), second.getId(), AllianceStatus.ACTIVE);
        data.recordSuccessfulDeliveryBetween(first.getId(), second.getId(), 30_000L);

        CompoundTag saved = data.save(new CompoundTag());
        SettlementSavedData loadedData = SettlementSavedData.load(saved);
        Settlement loaded = loadedData.getSettlement(first.getId());

        assertNotNull(loaded);
        assertEquals(SettlementSavedData.CURRENT_DATA_VERSION, saved.getInt("DataVersion"));
        assertEquals(SettlementSpecialization.PLAINS, loaded.getPrimarySpecialization());
        assertEquals(SettlementSpecialization.FOREST, loaded.getSecondarySpecialization().orElseThrow());
        assertEquals(new VirtualWarehouse.StockView(40, 16, 84), loaded.getWarehouse().getStock(wheat));
        assertTrue(loaded.getKnownSettlementIds().contains(second.getId()));

        SettlementRelationship relationship = loaded.findRelationship(second.getId()).orElseThrow();
        assertEquals(25, relationship.getReputation());
        assertEquals(TradeAgreementStatus.ACTIVE, relationship.getTradeAgreementStatus());
        assertEquals(AllianceStatus.ACTIVE, relationship.getAllianceStatus());
        assertEquals(1, relationship.getSuccessfulDeliveries());
        assertEquals(30_000L, relationship.getLastInteractionGameTime());
    }

    @Test
    void allianceCannotBecomeActiveBeforeTradeAgreement() {
        SettlementSavedData data = new SettlementSavedData();
        Settlement first = new Settlement(UUID.randomUUID(), "One", BlockPos.ZERO, 64);
        Settlement second = new Settlement(UUID.randomUUID(), "Two", new BlockPos(200, 64, 0), 64);
        data.registerSettlement(first);
        data.registerSettlement(second);

        assertThrows(IllegalStateException.class,
                () -> data.setAllianceStatusBetween(first.getId(), second.getId(), AllianceStatus.ACTIVE));
    }

    @Test
    void proposalsAreStoredFromEachSettlementsPerspective() {
        SettlementSavedData data = new SettlementSavedData();
        Settlement first = new Settlement(UUID.randomUUID(), "One", BlockPos.ZERO, 64);
        Settlement second = new Settlement(UUID.randomUUID(), "Two", new BlockPos(200, 64, 0), 64);
        data.registerSettlement(first);
        data.registerSettlement(second);

        data.setTradeAgreementStatusBetween(
                first.getId(), second.getId(), TradeAgreementStatus.PROPOSED_BY_US);

        assertEquals(TradeAgreementStatus.PROPOSED_BY_US,
                first.findRelationship(second.getId()).orElseThrow().getTradeAgreementStatus());
        assertEquals(TradeAgreementStatus.PROPOSED_BY_OTHER,
                second.findRelationship(first.getId()).orElseThrow().getTradeAgreementStatus());
    }

    @Test
    void oldSettlementFormatLoadsWithSafeDefaults() {
        UUID settlementId = UUID.randomUUID();
        CompoundTag legacySettlement = new CompoundTag();
        legacySettlement.putUUID("Id", settlementId);
        legacySettlement.putString("Name", "Legacy");
        legacySettlement.putLong("Center", BlockPos.ZERO.asLong());
        legacySettlement.putInt("Radius", 64);

        Settlement loaded = Settlement.loadNBT(legacySettlement);

        assertEquals(SettlementSpecialization.UNASSIGNED, loaded.getPrimarySpecialization());
        assertTrue(loaded.getWarehouse().getContents().isEmpty());
        assertTrue(loaded.getRelationships().isEmpty());
    }

    @Test
    void legacySavedDataIsMarkedDirtyForMigration() {
        CompoundTag legacyRoot = new CompoundTag();

        SettlementSavedData loaded = SettlementSavedData.load(legacyRoot);

        assertTrue(loaded.isDirty());
    }
}
