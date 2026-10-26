package com.diplomacy.villagediplomacy.garrison;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Population ledger survives chunk unloads; unloading is not a death. */
public final class GarrisonSavedData extends SavedData {
    public enum Role { CIVILIAN, TRAINING, SOLDIER }
    public record Person(UUID identity, UUID home, UUID entity, Role role, boolean recruited) { }
    private final Map<UUID, Person> people = new LinkedHashMap<>();

    public static GarrisonSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(GarrisonSavedData::load, GarrisonSavedData::new,
                "villagediplomacy_population");
    }

    public Collection<Person> people() { return java.util.List.copyOf(people.values()); }
    public Person get(UUID identity) { return people.get(identity); }
    public Person byEntity(UUID entity) {
        return people.values().stream().filter(p -> p.entity().equals(entity)).findFirst().orElse(null);
    }
    public void put(Person person) {
        if (!person.equals(people.put(person.identity(), person))) setDirty();
    }
    public void remove(UUID identity) { if (people.remove(identity) != null) setDirty(); }
    public int count(UUID home, Role role) {
        return (int) people.values().stream().filter(p -> p.home().equals(home) && p.role() == role).count();
    }

    public static GarrisonSavedData load(CompoundTag tag) {
        GarrisonSavedData data = new GarrisonSavedData();
        ListTag entries = tag.getList("People", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("Identity") || !entry.hasUUID("Home") || !entry.hasUUID("Entity")) continue;
            try {
                Person person = new Person(entry.getUUID("Identity"), entry.getUUID("Home"), entry.getUUID("Entity"),
                        Role.valueOf(entry.getString("Role")), entry.getBoolean("Recruited"));
                data.people.put(person.identity(), person);
            } catch (IllegalArgumentException ignored) { }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        people.values().forEach(person -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Identity", person.identity()); entry.putUUID("Home", person.home());
            entry.putUUID("Entity", person.entity()); entry.putString("Role", person.role().name());
            entry.putBoolean("Recruited", person.recruited()); entries.add(entry);
        });
        tag.put("People", entries);
        return tag;
    }
}
