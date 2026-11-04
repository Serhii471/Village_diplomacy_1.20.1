package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiplomacyTableAccessTest {
    @Test
    void tableAtMaximumDistanceIsUsable() {
        SettlementSavedData data = new SettlementSavedData();
        Settlement settlement = new Settlement(UUID.randomUUID(), "Center", BlockPos.ZERO, 64);
        data.registerSettlement(settlement);

        DiplomacyTableAccess.Result result = DiplomacyTableAccess.evaluate(data, new BlockPos(32, 0, 0));

        assertTrue(result.allowed());
        assertSame(settlement, result.settlement());
        assertEquals(DiplomacyTableAccess.Failure.NONE, result.failure());
    }

    @Test
    void tableInsideSettlementButTooFarFromBellIsRejected() {
        SettlementSavedData data = new SettlementSavedData();
        Settlement settlement = new Settlement(UUID.randomUUID(), "Wide", BlockPos.ZERO, 64);
        data.registerSettlement(settlement);

        DiplomacyTableAccess.Result result = DiplomacyTableAccess.evaluate(data, new BlockPos(33, 0, 0));

        assertFalse(result.allowed());
        assertSame(settlement, result.settlement());
        assertEquals(DiplomacyTableAccess.Failure.TOO_FAR_FROM_CENTER, result.failure());
    }

    @Test
    void tableOutsideRegisteredSettlementIsRejected() {
        SettlementSavedData data = new SettlementSavedData();
        data.registerSettlement(new Settlement(UUID.randomUUID(), "Small", BlockPos.ZERO, 16));

        DiplomacyTableAccess.Result result = DiplomacyTableAccess.evaluate(data, new BlockPos(20, 0, 0));

        assertFalse(result.allowed());
        assertEquals(DiplomacyTableAccess.Failure.OUTSIDE_SETTLEMENT, result.failure());
    }
}
