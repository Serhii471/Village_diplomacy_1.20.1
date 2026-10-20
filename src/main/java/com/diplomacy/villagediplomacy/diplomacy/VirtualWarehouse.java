package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Server-side inventory for settlement trade surplus.
 *
 * <p>All mutations preserve the invariant
 * {@code 0 <= reserved <= stored <= capacity}.</p>
 */
public final class VirtualWarehouse {
    private final Map<ResourceLocation, StockEntry> entries = new LinkedHashMap<>();
    private Runnable dirtyCallback = () -> { };

    public Map<ResourceLocation, StockView> getContents() {
        Map<ResourceLocation, StockView> result = new TreeMap<>((left, right) -> left.toString().compareTo(right.toString()));
        entries.forEach((id, entry) -> result.put(id, entry.snapshot()));
        return Collections.unmodifiableMap(result);
    }

    public StockView getStock(ResourceLocation itemId) {
        StockEntry entry = entries.get(Objects.requireNonNull(itemId, "itemId"));
        return entry == null ? StockView.EMPTY : entry.snapshot();
    }

    public void configureCapacity(ResourceLocation itemId, int capacity) {
        Objects.requireNonNull(itemId, "itemId");
        if (capacity < 0) {
            throw new IllegalArgumentException("Warehouse capacity cannot be negative");
        }

        StockEntry entry = entries.computeIfAbsent(itemId, ignored -> new StockEntry());
        int previousCapacity = entry.capacity;
        int previousStored = entry.stored;
        int previousReserved = entry.reserved;

        entry.capacity = capacity;
        entry.stored = Math.min(entry.stored, capacity);
        entry.reserved = Math.min(entry.reserved, entry.stored);

        if (previousCapacity != entry.capacity || previousStored != entry.stored || previousReserved != entry.reserved) {
            markDirty();
        }
    }

    /**
     * Adds available trade stock and returns the amount that fitted.
     */
    public int add(ResourceLocation itemId, int amount) {
        requirePositive(amount, "amount");
        StockEntry entry = entries.get(Objects.requireNonNull(itemId, "itemId"));
        if (entry == null || entry.capacity == 0) {
            return 0;
        }

        int accepted = Math.min(amount, entry.capacity - entry.stored);
        if (accepted > 0) {
            entry.stored += accepted;
            markDirty();
        }
        return accepted;
    }

    /**
     * Atomically reserves stock for a contract.
     */
    public boolean tryReserve(ResourceLocation itemId, int amount) {
        requirePositive(amount, "amount");
        StockEntry entry = entries.get(Objects.requireNonNull(itemId, "itemId"));
        if (entry == null || entry.available() < amount) {
            return false;
        }

        entry.reserved += amount;
        markDirty();
        return true;
    }

    public int releaseReservation(ResourceLocation itemId, int amount) {
        requirePositive(amount, "amount");
        StockEntry entry = entries.get(Objects.requireNonNull(itemId, "itemId"));
        if (entry == null) {
            return 0;
        }

        int released = Math.min(amount, entry.reserved);
        if (released > 0) {
            entry.reserved -= released;
            markDirty();
        }
        return released;
    }

    /**
     * Removes already reserved goods when they are physically handed to a carrier.
     */
    public int takeReserved(ResourceLocation itemId, int amount) {
        requirePositive(amount, "amount");
        StockEntry entry = entries.get(Objects.requireNonNull(itemId, "itemId"));
        if (entry == null) {
            return 0;
        }

        int taken = Math.min(amount, entry.reserved);
        if (taken > 0) {
            entry.reserved -= taken;
            entry.stored -= taken;
            markDirty();
        }
        return taken;
    }

    public int takeAvailable(ResourceLocation itemId, int amount) {
        requirePositive(amount, "amount");
        StockEntry entry = entries.get(Objects.requireNonNull(itemId, "itemId"));
        if (entry == null) {
            return 0;
        }

        int taken = Math.min(amount, entry.available());
        if (taken > 0) {
            entry.stored -= taken;
            markDirty();
        }
        return taken;
    }

    void setDirtyCallback(Runnable dirtyCallback) {
        this.dirtyCallback = Objects.requireNonNull(dirtyCallback, "dirtyCallback");
    }

    CompoundTag saveNBT() {
        CompoundTag warehouseTag = new CompoundTag();
        ListTag contents = new ListTag();

        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((left, right) -> left.toString().compareTo(right.toString())))
                .forEach(entry -> {
                    CompoundTag stockTag = new CompoundTag();
                    stockTag.putString("Item", entry.getKey().toString());
                    stockTag.putInt("Stored", entry.getValue().stored);
                    stockTag.putInt("Reserved", entry.getValue().reserved);
                    stockTag.putInt("Capacity", entry.getValue().capacity);
                    contents.add(stockTag);
                });

        warehouseTag.put("Contents", contents);
        return warehouseTag;
    }

    static VirtualWarehouse loadNBT(CompoundTag warehouseTag) {
        VirtualWarehouse warehouse = new VirtualWarehouse();
        if (!warehouseTag.contains("Contents", Tag.TAG_LIST)) {
            return warehouse;
        }

        ListTag contents = warehouseTag.getList("Contents", Tag.TAG_COMPOUND);
        for (int i = 0; i < contents.size(); i++) {
            CompoundTag stockTag = contents.getCompound(i);
            ResourceLocation itemId = ResourceLocation.tryParse(stockTag.getString("Item"));
            if (itemId == null) {
                continue;
            }

            StockEntry entry = new StockEntry();
            entry.capacity = Math.max(0, stockTag.getInt("Capacity"));
            entry.stored = Math.max(0, Math.min(stockTag.getInt("Stored"), entry.capacity));
            entry.reserved = Math.max(0, Math.min(stockTag.getInt("Reserved"), entry.stored));
            warehouse.entries.put(itemId, entry);
        }
        return warehouse;
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private void markDirty() {
        dirtyCallback.run();
    }

    private static final class StockEntry {
        private int stored;
        private int reserved;
        private int capacity;

        private int available() {
            return stored - reserved;
        }

        private StockView snapshot() {
            return new StockView(stored, reserved, capacity);
        }
    }

    public record StockView(int stored, int reserved, int capacity) {
        public static final StockView EMPTY = new StockView(0, 0, 0);

        public int available() {
            return stored - reserved;
        }
    }
}
