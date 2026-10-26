package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import com.diplomacy.villagediplomacy.garrison.GarrisonSavedData;
import com.diplomacy.villagediplomacy.garrison.GarrisonService;
import com.diplomacy.villagediplomacy.garrison.ResidentData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record GarrisonSnapshot(String homeName, boolean canManage, int civilians, int trainees, int soldiers,
                               int limit, boolean room, List<Entry> entries, String message) {
    public record Entry(UUID identity, Component name, GarrisonSavedData.Role role, boolean canAct, long remaining) { }
    public static GarrisonSnapshot create(ServerLevel level, ServerPlayer player, Settlement home, String message) {
        GarrisonService.discover(level);
        GarrisonService.Population population = GarrisonService.population(level, home);
        List<Entry> entries = new ArrayList<>();
        for (var person : GarrisonSavedData.get(level).people()) {
            if (!person.home().equals(home.getId())) continue;
            var entity = level.getEntity(person.entity());
            Component name = Component.translatable("name.villagediplomacy.resident." + Math.floorMod(person.identity().hashCode(), 12));
            boolean canAct = false; long remaining = 0;
            if (entity instanceof Villager villager) {
                name = ResidentData.name(villager);
                canAct = person.role() == GarrisonSavedData.Role.TRAINING || GarrisonService.eligible(villager);
                remaining = ResidentData.isTraining(villager) ? ResidentData.remaining(villager) : 0;
            } else if (entity instanceof VillageGuardEntity guard) {
                name = guard.getName();
                canAct = person.recruited() && guard.isAlive() && guard.getTarget() == null;
            }
            entries.add(new Entry(person.identity(), name, person.role(), canAct, remaining));
        }
        entries.sort(Comparator.comparingInt((Entry e) -> e.role() == GarrisonSavedData.Role.TRAINING ? 0
                : e.role() == GarrisonSavedData.Role.SOLDIER ? 1 : 2).thenComparing(e -> e.identity().toString()));
        return new GarrisonSnapshot(home.getName(), home.isPlayerLeader(player.getUUID()), population.civilians(),
                population.trainees(), population.soldiers(), population.limit(), population.canRecruit(),
                List.copyOf(entries.subList(0, Math.min(entries.size(), 256))), message);
    }
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(homeName, 256); buffer.writeBoolean(canManage);
        buffer.writeVarInt(civilians); buffer.writeVarInt(trainees); buffer.writeVarInt(soldiers); buffer.writeVarInt(limit);
        buffer.writeBoolean(room); buffer.writeUtf(message, 64); buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.identity()); buffer.writeComponent(entry.name()); buffer.writeEnum(entry.role());
            buffer.writeBoolean(entry.canAct()); buffer.writeVarLong(entry.remaining());
        }
    }
    public static GarrisonSnapshot read(FriendlyByteBuf buffer) {
        String name = buffer.readUtf(256); boolean manage = buffer.readBoolean();
        int civilians = buffer.readVarInt(), trainees = buffer.readVarInt(), soldiers = buffer.readVarInt(), limit = buffer.readVarInt();
        boolean room = buffer.readBoolean(); String message = buffer.readUtf(64); int count = buffer.readVarInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid roster size");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) entries.add(new Entry(buffer.readUUID(), buffer.readComponent(),
                buffer.readEnum(GarrisonSavedData.Role.class), buffer.readBoolean(), buffer.readVarLong()));
        return new GarrisonSnapshot(name, manage, civilians, trainees, soldiers, limit, room, List.copyOf(entries), message);
    }
}
