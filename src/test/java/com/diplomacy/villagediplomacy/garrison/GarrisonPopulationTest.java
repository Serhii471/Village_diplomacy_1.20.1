package com.diplomacy.villagediplomacy.garrison;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GarrisonPopulationTest {
    @Test void reservesFourCiviliansAndCountsQueuedRecruitsTowardTheArmy() {
        assertFalse(new GarrisonService.Population(4, 0, 0).canRecruit());
        assertTrue(new GarrisonService.Population(5, 0, 0).canRecruit());
        assertFalse(new GarrisonService.Population(4, 1, 0).canRecruit());
        assertTrue(new GarrisonService.Population(8, 0, 0).canRecruit());
        assertTrue(new GarrisonService.Population(7, 1, 0).canRecruit());
        assertFalse(new GarrisonService.Population(6, 2, 0).canRecruit());
        assertFalse(new GarrisonService.Population(6, 0, 2).canRecruit());
    }
    @Test void populationLedgerSurvivesSaveWithDistinctPersonAndSoldierIdentities() {
        GarrisonSavedData data = new GarrisonSavedData();
        UUID person = UUID.randomUUID(), soldier = UUID.randomUUID(), home = UUID.randomUUID();
        var entry = new GarrisonSavedData.Person(person, home, soldier, GarrisonSavedData.Role.SOLDIER, true);
        data.put(entry);
        GarrisonSavedData loaded = GarrisonSavedData.load(data.save(new CompoundTag()));
        assertEquals(entry, loaded.get(person));
        assertEquals(entry, loaded.byEntity(soldier));
        assertEquals(1, loaded.count(home, GarrisonSavedData.Role.SOLDIER));
        loaded.remove(person);
        assertEquals(0, loaded.count(home, GarrisonSavedData.Role.SOLDIER));
    }
}
