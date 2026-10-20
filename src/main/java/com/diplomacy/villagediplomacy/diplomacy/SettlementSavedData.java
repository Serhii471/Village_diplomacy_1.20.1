package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.*;

public class SettlementSavedData extends SavedData {
    public static final String DATA_NAME = "village_diplomacy_settlements";
    private final Map<UUID, Settlement> settlements = new HashMap<>();

    public SettlementSavedData() {}

    public static SettlementSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                SettlementSavedData::load,
                SettlementSavedData::new,
                DATA_NAME
        );
    }

    public Collection<Settlement> getAllSettlements() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    @Nullable
    public Settlement getSettlement(UUID id) {
        return settlements.get(id);
    }

    public void registerSettlement(Settlement settlement) {
        settlements.put(settlement.getId(), settlement);
        setDirty();
    }

    public void removeSettlement(UUID id) {
        if (settlements.remove(id) != null) {
            for (Settlement s : settlements.values()) {
                s.setReputationWith(id, 0);
            }
            setDirty();
        }
    }

    @Nullable
    public Settlement findSettlementAt(BlockPos pos) {
        for (Settlement settlement : settlements.values()) {
            if (settlement.contains(pos)) {
                return settlement;
            }
        }
        return null;
    }

    public Settlement getOrCreateSettlementNear(BlockPos pos, int defaultRadius, String defaultName) {
        Settlement existing = findSettlementAt(pos);
        if (existing != null) {
            return existing;
        }

        Settlement newSettlement = new Settlement(UUID.randomUUID(), defaultName, pos, defaultRadius);
        registerSettlement(newSettlement);
        return newSettlement;
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag) {
        ListTag list = new ListTag();
        for (Settlement settlement : settlements.values()) {
            list.add(settlement.saveNBT());
        }
        compoundTag.put("Settlements", list);
        return compoundTag;
    }

    public static SettlementSavedData load(CompoundTag compoundTag) {
        SettlementSavedData data = new SettlementSavedData();
        if (compoundTag.contains("Settlements", Tag.TAG_LIST)) {
            ListTag list = compoundTag.getList("Settlements", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Settlement s = Settlement.loadNBT(list.getCompound(i));
                data.settlements.put(s.getId(), s);
            }
        }
        return data;
    }
}
