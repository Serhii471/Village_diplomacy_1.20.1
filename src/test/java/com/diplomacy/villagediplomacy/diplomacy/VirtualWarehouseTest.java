package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualWarehouseTest {
    private static final ResourceLocation WHEAT = Objects.requireNonNull(ResourceLocation.tryParse("minecraft:wheat"));

    @Test
    void reservationsAndWithdrawalsKeepStockConsistent() {
        VirtualWarehouse warehouse = new VirtualWarehouse();
        warehouse.configureCapacity(WHEAT, 100);

        assertEquals(100, warehouse.add(WHEAT, 120));
        assertTrue(warehouse.tryReserve(WHEAT, 40));
        assertFalse(warehouse.tryReserve(WHEAT, 61));
        assertEquals(15, warehouse.takeReserved(WHEAT, 15));
        assertEquals(10, warehouse.releaseReservation(WHEAT, 10));
        assertEquals(60, warehouse.takeAvailable(WHEAT, 60));

        VirtualWarehouse.StockView stock = warehouse.getStock(WHEAT);
        assertEquals(25, stock.stored());
        assertEquals(15, stock.reserved());
        assertEquals(10, stock.available());
        assertEquals(100, stock.capacity());
    }

    @Test
    void roundTripPreservesStockAndClampsCorruptValues() {
        VirtualWarehouse warehouse = new VirtualWarehouse();
        warehouse.configureCapacity(WHEAT, 50);
        warehouse.add(WHEAT, 35);
        warehouse.tryReserve(WHEAT, 12);

        CompoundTag tag = warehouse.saveNBT();
        VirtualWarehouse loaded = VirtualWarehouse.loadNBT(tag);

        assertEquals(new VirtualWarehouse.StockView(35, 12, 50), loaded.getStock(WHEAT));
        assertThrows(IllegalArgumentException.class, () -> loaded.add(WHEAT, 0));
    }
}
