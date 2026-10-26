package com.diplomacy.villagediplomacy.diplomacy;

import java.util.Locale;

public enum AllianceStatus {
    NONE,
    PROPOSED_BY_US,
    PROPOSED_BY_OTHER,
    ACTIVE;

    public AllianceStatus oppositePerspective() {
        return switch (this) {
            case PROPOSED_BY_US -> PROPOSED_BY_OTHER;
            case PROPOSED_BY_OTHER -> PROPOSED_BY_US;
            default -> this;
        };
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AllianceStatus fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }

        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
