package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class VillageWarehouse {
    private static final int MAX_STORAGE_DAYS = 5;

    private final Map<Item, Integer> exportStock = new HashMap<>();
    private final Map<Item, Integer> internalStock = new HashMap<>();

    public void addDailyProduction(SettlementSpecialization specialization) {
        Map<Item, Integer> yield = specialization.getDailyYield();
        for (Map.Entry<Item, Integer> entry : yield.entrySet()) {
            Item item = entry.getKey();
            int total = entry.getValue();

            int toExport = Math.round(total * specialization.getExportShare());
            int toInternal = total - toExport;
            int maxCapacity = total * MAX_STORAGE_DAYS;

            int currentExport = exportStock.getOrDefault(item, 0);
            exportStock.put(item, Math.min(maxCapacity, currentExport + toExport));

            int currentInternal = internalStock.getOrDefault(item, 0);
            internalStock.put(item, Math.min(maxCapacity, currentInternal + toInternal));
        }
    }

    public boolean hasExportResource(Item item, int count) {
        return exportStock.getOrDefault(item, 0) >= count;
    }

    public boolean withdrawExportResource(Item item, int count) {
        int current = exportStock.getOrDefault(item, 0);
        if (current < count) return false;
        if (current == count) {
            exportStock.remove(item);
        } else {
            exportStock.put(item, current - count);
        }
        return true;
    }

    public void depositResource(Item item, int count) {
        exportStock.put(item, exportStock.getOrDefault(item, 0) + count);
    }

    public Map<Item, Integer> getExportStock() {
        return Collections.unmodifiableMap(exportStock);
    }

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag exportList = new ListTag();
        for (Map.Entry<Item, Integer> entry : exportStock.entrySet()) {
            CompoundTag itemTag = new CompoundTag();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.getKey());
            itemTag.putString("Item", id.toString());
            itemTag.putInt("Count", entry.getValue());
            exportList.add(itemTag);
        }
        tag.put("ExportStock", exportList);

        ListTag internalList = new ListTag();
        for (Map.Entry<Item, Integer> entry : internalStock.entrySet()) {
            CompoundTag itemTag = new CompoundTag();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.getKey());
            itemTag.putString("Item", id.toString());
            itemTag.putInt("Count", entry.getValue());
            internalList.add(itemTag);
        }
        tag.put("InternalStock", internalList);
        return tag;
    }

    public static VillageWarehouse loadNBT(CompoundTag tag) {
        VillageWarehouse warehouse = new VillageWarehouse();
        if (tag.contains("ExportStock", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ExportStock", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemTag.getString("Item")));
                warehouse.exportStock.put(item, itemTag.getInt("Count"));
            }
        }
        if (tag.contains("InternalStock", Tag.TAG_LIST)) {
            ListTag list = tag.getList("InternalStock", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemTag.getString("Item")));
                warehouse.internalStock.put(item, itemTag.getInt("Count"));
            }
        }
        return warehouse;
    }
}
