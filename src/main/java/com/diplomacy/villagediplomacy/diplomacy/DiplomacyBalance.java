package com.diplomacy.villagediplomacy.diplomacy;

/**
 * Centralized balance defaults for the 0.1 gameplay loop.
 *
 * <p>Keeping these values outside gameplay services makes later balancing
 * changes explicit and prevents unrelated systems from inventing their own
 * constants.</p>
 */
public final class DiplomacyBalance {
    public static final int DEFAULT_SETTLEMENT_RADIUS = 64;
    public static final int SPECIALIZATION_SCAN_RADIUS = 48;
    public static final double SECONDARY_SPECIALIZATION_MULTIPLIER = 0.5D;

    public static final int PLAINS_WHEAT_PER_DAY = 24;
    public static final int PLAINS_WOOL_PER_DAY = 6;
    public static final int PLAINS_RAW_MUTTON_PER_DAY = 4;
    public static final int MOUNTAIN_COAL_PER_DAY = 12;
    public static final int MOUNTAIN_RAW_COPPER_PER_DAY = 6;
    public static final int MOUNTAIN_RAW_IRON_PER_DAY = 3;
    public static final int MOUNTAIN_STONE_PER_DAY = 48;
    public static final int FOREST_LOGS_PER_DAY = 24;
    public static final int FOREST_HONEYCOMB_PER_DAY = 4;
    public static final int FOREST_APPLES_PER_DAY = 3;

    public static final double INTERNAL_CONSUMPTION_SHARE = 0.30D;
    public static final double TRADE_SURPLUS_SHARE = 0.70D;
    public static final int WAREHOUSE_CAPACITY_DAYS = 5;

    public static final int LEADER_TABLE_MAX_DISTANCE = 32;
    public static final int LEADERSHIP_CONFIRMATION_WINDOW_TICKS = 200;
    public static final int DIPLOMATIC_SCROLL_COOLDOWN_TICKS = 100;
    public static final int TRIAL_DELIVERIES_FOR_TRADE_AGREEMENT = 1;
    public static final int DELIVERIES_FOR_ALLIANCE = 3;

    private DiplomacyBalance() {
    }
}
