package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

public record TradeRoute(UUID id, UUID sourceId, UUID targetId, String name, boolean closed) {
    public TradeRoute {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(targetId);
        name = cleanName(name);
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Route endpoints must differ");
        }
    }

    public boolean connects(UUID first, UUID second) {
        return sourceId.equals(first) && targetId.equals(second) || sourceId.equals(second) && targetId.equals(first);
    }

    public static String cleanName(String value) {
        String clean = Objects.requireNonNull(value).replaceAll("[\\p{Cntrl}§]", "").strip();
        if (clean.isBlank() || clean.length() > 64) {
            throw new IllegalArgumentException("Route name must contain 1 to 64 characters");
        }
        return clean;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Source", sourceId);
        tag.putUUID("Target", targetId);
        tag.putString("Name", name);
        tag.putBoolean("Closed", closed);
        return tag;
    }

    static TradeRoute load(CompoundTag tag) {
        return new TradeRoute(tag.getUUID("Id"), tag.getUUID("Source"), tag.getUUID("Target"),
                tag.getString("Name"), tag.getBoolean("Closed"));
    }
}
