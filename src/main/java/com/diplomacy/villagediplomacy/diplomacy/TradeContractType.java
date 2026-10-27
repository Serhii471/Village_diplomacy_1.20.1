package com.diplomacy.villagediplomacy.diplomacy;

import java.util.Locale;

public enum TradeContractType {
    DELIVERY,
    EXCHANGE;

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TradeContractType fromSerializedName(String value) {
        for (TradeContractType type : values()) {
            if (type.getSerializedName().equals(value)) {
                return type;
            }
        }
        return DELIVERY;
    }
}
