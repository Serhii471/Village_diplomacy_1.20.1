package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Objects;

/** Server-side placement and access rules for a diplomacy table. */
public final class DiplomacyTableAccess {
    public enum Failure {
        NONE,
        OUTSIDE_SETTLEMENT,
        TOO_FAR_FROM_CENTER
    }

    public record Result(@Nullable Settlement settlement, Failure failure) {
        public boolean allowed() {
            return settlement != null && failure == Failure.NONE;
        }
    }

    private DiplomacyTableAccess() {
    }

    public static Result evaluate(SettlementSavedData data, BlockPos tablePos) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(tablePos, "tablePos");
        Settlement settlement = data.findSettlementAt(tablePos);
        if (settlement == null) {
            return new Result(null, Failure.OUTSIDE_SETTLEMENT);
        }

        long maximumDistanceSqr = (long) DiplomacyBalance.LEADER_TABLE_MAX_DISTANCE
                * DiplomacyBalance.LEADER_TABLE_MAX_DISTANCE;
        if (settlement.getCenter().distSqr(tablePos) > maximumDistanceSqr) {
            return new Result(settlement, Failure.TOO_FAR_FROM_CENTER);
        }
        return new Result(settlement, Failure.NONE);
    }
}
