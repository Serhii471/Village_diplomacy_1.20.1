package com.diplomacy.villagediplomacy.diplomacy;

import java.util.Locale;

public enum TradeContractStatus {
    OFFERED,
    ACTIVE,
    COMPLETED,
    EXPIRED,
    CANCELLED;

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == EXPIRED || this == CANCELLED;
    }

    public static TradeContractStatus fromSerializedName(String value) {
        for (TradeContractStatus status : values()) {
            if (status.getSerializedName().equals(value)) {
                return status;
            }
        }
        return OFFERED;
    }
}
