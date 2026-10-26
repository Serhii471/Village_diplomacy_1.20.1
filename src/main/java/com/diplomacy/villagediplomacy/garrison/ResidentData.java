package com.diplomacy.villagediplomacy.garrison;

import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

import java.util.UUID;

public final class ResidentData {
    public static final String KEY = "VillageDiplomacyResident";
    public static final String TRAINING = "Training";
    private ResidentData() { }
    public static CompoundTag data(Villager villager) {
        CompoundTag root = villager.getPersistentData();
        if (!root.contains(KEY)) root.put(KEY, new CompoundTag());
        return root.getCompound(KEY);
    }
    public static int face(UUID identity) { return Math.floorMod(identity.hashCode(), 3); }
    public static Component name(Villager villager) {
        return villager.hasCustomName() ? villager.getCustomName()
                : Component.translatable("name.villagediplomacy.resident." + Math.floorMod(villager.getUUID().hashCode(), 12));
    }
    public static Settlement home(ServerLevel level, Villager villager) {
        CompoundTag data = data(villager);
        SettlementSavedData settlements = SettlementSavedData.get(level);
        Settlement home = data.hasUUID("Home") ? settlements.getSettlement(data.getUUID("Home")) : null;
        if (home == null) {
            home = settlements.findSettlementAt(villager.blockPosition());
            if (home == null) data.remove("Home"); else data.putUUID("Home", home.getId());
        }
        return home;
    }
    public static boolean isTraining(Villager villager) { return data(villager).contains(TRAINING); }
    public static long remaining(Villager villager) {
        return Math.max(0L, data(villager).getCompound(TRAINING).getLong("Finish") - villager.level().getGameTime());
    }
}
