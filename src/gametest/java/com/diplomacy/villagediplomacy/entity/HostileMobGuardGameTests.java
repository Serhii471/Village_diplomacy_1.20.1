package com.diplomacy.villagediplomacy.entity;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.core.ModEntities;
import com.diplomacy.villagediplomacy.entity.ai.GuardTargetGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VillageDiplomacyMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HostileMobGuardGameTests {
    private static final String ARENA = "guard_arena";

    public static void clearPreviousBatchMobs(ServerLevel level) {
        // A villager from another scenario legitimately outranks a guard in zombie AI.
        // Keep this isolated dev batch free of entities left by earlier scenarios.
        java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false)
                .filter(entity -> entity instanceof LivingEntity || entity instanceof net.minecraft.world.entity.item.ItemEntity
                        || entity instanceof net.minecraft.world.entity.projectile.Projectile)
                .toList().forEach(net.minecraft.world.entity.Entity::discard);
    }

    // Normal targeting ranges reach adjacent arenas. Run each hostile scenario
    // in its own batch so another monster cannot invalidate a neutral-mob check.
    @BeforeBatch(batch = "hostile_guards_huskInitiatesMeleeAgainstPassiveGuard")
    public static void prepare_huskInitiatesMeleeAgainstPassiveGuard(ServerLevel level) { clearPreviousBatchMobs(level); }

    @BeforeBatch(batch = "hostile_guards_skeletonShootsPassiveGuard")
    public static void prepare_skeletonShootsPassiveGuard(ServerLevel level) { clearPreviousBatchMobs(level); }

    @BeforeBatch(batch = "hostile_guards_creeperApproachesAndStartsFuse")
    public static void prepare_creeperApproachesAndStartsFuse(ServerLevel level) { clearPreviousBatchMobs(level); }

    @BeforeBatch(batch = "hostile_guards_vindicatorInitiatesMelee")
    public static void prepare_vindicatorInitiatesMelee(ServerLevel level) { clearPreviousBatchMobs(level); }

    @BeforeBatch(batch = "hostile_guards_spiderAttacksGuardInDarkness")
    public static void prepare_spiderAttacksGuardInDarkness(ServerLevel level) { clearPreviousBatchMobs(level); }

    @BeforeBatch(batch = "hostile_guards_brightSpiderDoesNotBecomeAggressive")
    public static void prepare_brightSpiderDoesNotBecomeAggressive(ServerLevel level) { clearPreviousBatchMobs(level); }

    @BeforeBatch(batch = "hostile_guards_slimeChasesAndDealsContactDamage")
    public static void prepare_slimeChasesAndDealsContactDamage(ServerLevel level) { clearPreviousBatchMobs(level); }

    @BeforeBatch(batch = "hostile_guards_magmaCubeChasesAndDealsContactDamage")
    public static void prepare_magmaCubeChasesAndDealsContactDamage(ServerLevel level) { clearPreviousBatchMobs(level); }

    @BeforeBatch(batch = "hostile_guards_hoglinUsesBrainToAttackGuard")
    public static void prepare_hoglinUsesBrainToAttackGuard(ServerLevel level) { clearPreviousBatchMobs(level); }

    @BeforeBatch(batch = "hostile_guards_piglinBruteUsesBrainToAttackGuard")
    public static void prepare_piglinBruteUsesBrainToAttackGuard(ServerLevel level) { clearPreviousBatchMobs(level); }

    @BeforeBatch(batch = "hostile_guards_neutralEndermanDoesNotAcquireGuard")
    public static void prepare_neutralEndermanDoesNotAcquireGuard(ServerLevel level) { clearPreviousBatchMobs(level); }

    @BeforeBatch(batch = "hostile_guards_reloadedHostileMobStillTargetsGuard")
    public static void prepare_reloadedHostileMobStillTargetsGuard(ServerLevel level) { clearPreviousBatchMobs(level); }

    @GameTest(batch = "hostile_guards_huskInitiatesMeleeAgainstPassiveGuard", template = ARENA, timeoutTicks = 160)
    public static void huskInitiatesMeleeAgainstPassiveGuard(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        Mob husk = helper.spawn(EntityType.HUSK, 6, 2, 3);
        helper.succeedWhen(() -> helper.assertTrue(guard.getHealth() < guard.getMaxHealth(),
                "Husk must approach and attack without being hit first: target=" + husk.getTarget()
                        + ", position=" + husk.position() + ", guard=" + guard.position()));
    }

    @GameTest(batch = "hostile_guards_skeletonShootsPassiveGuard", template = ARENA, timeoutTicks = 240)
    public static void skeletonShootsPassiveGuard(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, 9, 2, 3);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        succeedsWhenGuardIsHurt(helper, guard, "Skeleton must shoot and hurt a guard without provocation");
    }

    @GameTest(batch = "hostile_guards_creeperApproachesAndStartsFuse", template = ARENA, timeoutTicks = 120)
    public static void creeperApproachesAndStartsFuse(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        Creeper creeper = helper.spawn(EntityType.CREEPER, 7, 2, 3);
        helper.succeedWhen(() -> helper.assertTrue(creeper.getTarget() == guard && creeper.getSwellDir() > 0,
                "Creeper must approach a guard and start its normal fuse"));
    }

    @GameTest(batch = "hostile_guards_vindicatorInitiatesMelee", template = ARENA, timeoutTicks = 160)
    public static void vindicatorInitiatesMelee(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        helper.spawn(EntityType.VINDICATOR, 6, 2, 3);
        succeedsWhenGuardIsHurt(helper, guard, "Raider must initiate an attack against a guard");
    }

    @GameTest(batch = "hostile_guards_spiderAttacksGuardInDarkness", template = ARENA, timeoutTicks = 160)
    public static void spiderAttacksGuardInDarkness(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        helper.spawn(EntityType.SPIDER, 7, 2, 3);
        succeedsWhenGuardIsHurt(helper, guard, "Darkness must allow a spider to approach and bite a guard");
    }

    @GameTest(batch = "hostile_guards_brightSpiderDoesNotBecomeAggressive", template = ARENA, timeoutTicks = 100)
    public static void brightSpiderDoesNotBecomeAggressive(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        helper.setBlock(6, 3, 3, Blocks.GLOWSTONE);
        Spider spider = helper.spawn(EntityType.SPIDER, 6, 2, 3);
        // Keep the spider under the light while retaining its actual target selection.
        spider.setNoGravity(true);
        helper.onEachTick(() -> {
            spider.setDeltaMovement(Vec3.ZERO);
            BlockPos position = helper.absolutePos(new BlockPos(6, 2, 3));
            spider.moveTo(position.getX(), position.getY(), position.getZ());
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(spider.getLightLevelDependentMagicValue() >= 0.5F, "Fixture must be bright");
            helper.assertTrue(spider.getTarget() == null, "Bright spider must retain its neutral behavior");
            helper.assertTrue(guard.getHealth() == guard.getMaxHealth(), "Bright spider must not damage guard");
            helper.succeed();
        });
    }

    @GameTest(batch = "hostile_guards_slimeChasesAndDealsContactDamage", template = ARENA, timeoutTicks = 200)
    public static void slimeChasesAndDealsContactDamage(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        Slime slime = helper.spawn(EntityType.SLIME, 6, 2, 3);
        slime.setSize(2, true);
        succeedsWhenGuardIsHurt(helper, guard, "Slime must both chase a guard and deal contact damage");
    }

    @GameTest(batch = "hostile_guards_magmaCubeChasesAndDealsContactDamage", template = ARENA, timeoutTicks = 240)
    public static void magmaCubeChasesAndDealsContactDamage(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        MagmaCube cube = helper.spawn(EntityType.MAGMA_CUBE, 6, 2, 3);
        cube.setSize(2, true);
        succeedsWhenGuardIsHurt(helper, guard, "Magma cube must chase a guard and deal contact damage");
    }

    @GameTest(batch = "hostile_guards_hoglinUsesBrainToAttackGuard", template = ARENA, timeoutTicks = 160)
    public static void hoglinUsesBrainToAttackGuard(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        Hoglin hoglin = helper.spawn(EntityType.HOGLIN, 6, 2, 3);
        hoglin.setImmuneToZombification(true);
        succeedsWhenGuardIsHurt(helper, guard, "Adult hoglin brain must approach and attack a guard");
    }

    @GameTest(batch = "hostile_guards_piglinBruteUsesBrainToAttackGuard", template = ARENA, timeoutTicks = 160)
    public static void piglinBruteUsesBrainToAttackGuard(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        PiglinBrute brute = helper.spawn(EntityType.PIGLIN_BRUTE, 6, 2, 3);
        brute.setImmuneToZombification(true);
        succeedsWhenGuardIsHurt(helper, guard, "Piglin brute brain must approach and attack a guard");
    }

    @GameTest(batch = "hostile_guards_neutralEndermanDoesNotAcquireGuard", template = ARENA, timeoutTicks = 100)
    public static void neutralEndermanDoesNotAcquireGuard(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        EnderMan enderman = helper.spawn(EntityType.ENDERMAN, 6, 2, 3);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(enderman.getTarget() == null, "Unprovoked enderman must remain neutral");
            helper.assertTrue(guard.getHealth() == guard.getMaxHealth(), "Neutral enderman must not hurt guard");
            helper.succeed();
        });
    }

    @GameTest(batch = "hostile_guards_reloadedHostileMobStillTargetsGuard", template = ARENA, timeoutTicks = 160)
    public static void reloadedHostileMobStillTargetsGuard(GameTestHelper helper) {
        VillageGuardEntity guard = passiveGuard(helper);
        Mob original = helper.spawn(EntityType.HUSK, 6, 2, 3);
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        original.discard();
        Mob reloaded = EntityType.HUSK.create(helper.getLevel());
        reloaded.load(saved);
        helper.getLevel().addFreshEntity(reloaded);
        long guardGoals = reloaded.targetSelector.getAvailableGoals().stream()
                .filter(goal -> goal.getGoal() instanceof GuardTargetGoal).count();
        helper.assertTrue(guardGoals == 1, "Reload must install exactly one guard targeting goal");
        succeedsWhenGuardIsHurt(helper, guard, "Reloaded hostile must still proactively attack a guard");
    }

    private static VillageGuardEntity passiveGuard(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        helper.setNight();
        VillageGuardEntity guard = helper.spawn(ModEntities.VILLAGE_GUARD.get(), 3, 2, 3);
        guard.setNoAi(true);
        return guard;
    }

    private static void succeedsWhenGuardIsHurt(GameTestHelper helper, VillageGuardEntity guard, String message) {
        helper.succeedWhen(() -> helper.assertTrue(guard.getHealth() < guard.getMaxHealth(), message));
    }
}
