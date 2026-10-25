package com.diplomacy.villagediplomacy.entity;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.core.ModEntities;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(VillageDiplomacyMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageGuardGameTests {
    private static final String ARENA = "guard_arena";

    @GameTest(template = ARENA)
    public static void spawnBindsToSettlementAndEquipsGuard(GameTestHelper helper) {
        Settlement home = home(helper);
        VillageGuardEntity guard = guard(helper, 3, 3);
        guard.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(guard.blockPosition()),
                MobSpawnType.SPAWN_EGG, null, null);

        helper.assertTrue(guard.getSettlementId().orElseThrow().equals(home.getId()), "Spawn must bind to home");
        helper.assertTrue(guard.hasRestriction() && guard.getRestrictCenter().equals(home.getCenter()),
                "Spawn must establish home patrol");
        helper.assertTrue(guard.getMainHandItem().is(Items.IRON_SWORD), "Spawn must equip an iron sword");
        helper.assertTrue(guard.isPersistenceRequired(), "Guard must not despawn when the player leaves");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void saveReloadRestoresPatrolAndTracksHomeChanges(GameTestHelper helper) {
        Settlement home = home(helper);
        VillageGuardEntity original = guard(helper, 3, 3);
        original.setSettlementId(home.getId());
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        original.discard();

        VillageGuardEntity loaded = ModEntities.VILLAGE_GUARD.get().create(helper.getLevel());
        loaded.load(saved);
        helper.getLevel().addFreshEntity(loaded);
        helper.assertTrue(loaded.getSettlementId().orElseThrow().equals(home.getId()), "Home ID must survive NBT");
        helper.assertTrue(loaded.hasRestriction() && loaded.getRestrictCenter().equals(home.getCenter()),
                "Patrol restriction must be restored immediately after reload");

        home.setCenter(helper.absolutePos(new BlockPos(4, 2, 4)));
        home.setRadius(5);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(loaded.getRestrictCenter().equals(home.getCenter()), "Guard must track a moved home");
            helper.assertTrue(loaded.getRestrictRadius() == 5, "Guard must track a resized home");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void friendlyGuardsDoNotRetaliate(GameTestHelper helper) {
        Settlement home = home(helper);
        VillageGuardEntity guard = guard(helper, 3, 3);
        VillageGuardEntity friend = guard(helper, 5, 3);
        guard.setSettlementId(home.getId());
        friend.setSettlementId(home.getId());
        guard.setLastHurtByMob(friend);
        helper.assertFalse(guard.canAttack(friend), "Same-settlement guards must never be valid targets");
        helper.assertFalse(guard.doHurtTarget(friend), "Same-settlement guard must not take melee damage");

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(guard.getTarget() == null, "Retaliation goal must not target a friendly guard");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void diplomacyChangeStopsCombat(GameTestHelper helper) {
        Settlement home = home(helper);
        Settlement otherHome = otherHome(helper);
        VillageGuardEntity guard = guard(helper, 3, 3);
        VillageGuardEntity other = guard(helper, 6, 3);
        guard.setSettlementId(home.getId());
        other.setSettlementId(otherHome.getId());
        home.setReputationWith(otherHome.getId(), -100);
        otherHome.setReputationWith(home.getId(), -100);
        helper.assertTrue(guard.canAttack(other), "Hostile guard inside patrol area must be a valid target");
        guard.setTarget(other);
        other.setTarget(guard);

        helper.runAfterDelay(5, () -> {
            home.setReputationWith(otherHome.getId(), 100);
            otherHome.setReputationWith(home.getId(), 100);
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(guard.getTarget() == null && other.getTarget() == null,
                    "Both guards must stop fighting when settlements become allies");
            helper.assertFalse(guard.doHurtTarget(other), "Alliance must prevent further melee damage");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void guardProtectsLeadersAndVillagers(GameTestHelper helper) {
        Settlement home = home(helper);
        Settlement ally = otherHome(helper);
        VillageGuardEntity guard = guard(helper, 3, 3);
        guard.setSettlementId(home.getId());
        Player leader = helper.makeMockPlayer();
        leader.setPos(guard.getX() + 1, guard.getY(), guard.getZ());
        home.setLeaderUuid(leader.getUUID());
        helper.assertFalse(guard.canAttack(leader), "Own leader must be protected");

        Player alliedLeader = helper.makeMockPlayer();
        alliedLeader.setPos(guard.getX() + 1, guard.getY(), guard.getZ());
        ally.setLeaderUuid(alliedLeader.getUUID());
        home.setReputationWith(ally.getId(), 100);
        helper.assertFalse(guard.canAttack(alliedLeader), "Allied leader must be protected");
        helper.assertFalse(guard.canAttack(helper.spawn(EntityType.VILLAGER, 5, 2, 4)),
                "Villagers must be protected");
        helper.assertFalse(guard.canAttack(helper.spawn(EntityType.IRON_GOLEM, 5, 2, 5)),
                "Iron golems must be protected");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void escapingTargetIsReleased(GameTestHelper helper) {
        Settlement home = home(helper);
        VillageGuardEntity guard = guard(helper, 3, 3);
        guard.setSettlementId(home.getId());
        Husk enemy = helper.spawn(EntityType.HUSK, 5, 2, 3);
        enemy.setNoAi(true);
        helper.assertTrue(guard.canAttack(enemy), "Monster inside patrol area must be a valid target");
        guard.setTarget(enemy);
        BlockPos outside = helper.absolutePos(new BlockPos(14, 2, 3));
        enemy.moveTo(outside.getX(), outside.getY(), outside.getZ());
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(guard.getTarget() == null, "Guard must drop a target beyond home boundaries");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 240)
    public static void displacedGuardWalksHome(GameTestHelper helper) {
        Settlement home = home(helper);
        VillageGuardEntity guard = guard(helper, 13, 3);
        guard.setSettlementId(home.getId());
        helper.assertFalse(guard.isWithinRestriction(), "Test must start outside home");
        helper.succeedWhen(() -> helper.assertTrue(guard.isWithinRestriction(),
                "Displaced guard must navigate back to its patrol area: position=" + guard.position()
                        + ", home=" + guard.getRestrictCenter() + ", path=" + guard.getNavigation().getPath()
                        + ", onGround=" + guard.onGround() + ", noAi=" + guard.isNoAi()));
    }

    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void guardFightsMonstersInsideHome(GameTestHelper helper) {
        Settlement home = home(helper);
        VillageGuardEntity guard = guard(helper, 3, 3);
        guard.setSettlementId(home.getId());
        Husk enemy = helper.spawn(EntityType.HUSK, 6, 2, 3);
        enemy.setNoAi(true);
        float initialHealth = enemy.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(enemy.getHealth() < initialHealth,
                "Guard must acquire, approach and damage a monster inside its home"));
    }

    @GameTest(template = ARENA)
    public static void removedHomeClearsAffiliationAndRestriction(GameTestHelper helper) {
        Settlement home = home(helper);
        VillageGuardEntity guard = guard(helper, 3, 3);
        guard.setSettlementId(home.getId());
        SettlementSavedData.get(helper.getLevel()).removeSettlement(home.getId());
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(guard.getSettlementId().isEmpty(), "Deleted home must clear guard affiliation");
            helper.assertFalse(guard.hasRestriction(), "Deleted home must clear stale patrol restriction");
            helper.succeed();
        });
    }

    private static Settlement home(GameTestHelper helper) {
        Settlement settlement = new Settlement(UUID.randomUUID(), "Guard test home",
                helper.absolutePos(new BlockPos(3, 2, 3)), 7);
        SettlementSavedData.get(helper.getLevel()).registerSettlement(settlement);
        return settlement;
    }

    private static Settlement otherHome(GameTestHelper helper) {
        Settlement settlement = new Settlement(UUID.randomUUID(), "Guard test neighbour",
                helper.absolutePos(new BlockPos(7, 2, 3)), 7);
        SettlementSavedData.get(helper.getLevel()).registerSettlement(settlement);
        return settlement;
    }

    private static VillageGuardEntity guard(GameTestHelper helper, int x, int z) {
        // Forge places the structure floor one block above the structure-block origin.
        return helper.spawn(ModEntities.VILLAGE_GUARD.get(), x, 2, z);
    }
}
