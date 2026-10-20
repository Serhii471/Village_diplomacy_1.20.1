package com.diplomacy.villagediplomacy.diplomacy;

import java.util.Locale;

public enum SettlementSpecialization {
    UNASSIGNED,
    PLAINS,
    MOUNTAIN,
    FOREST;

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SettlementSpecialization fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return UNASSIGNED;
        }

        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNASSIGNED;
        }
    }
}
