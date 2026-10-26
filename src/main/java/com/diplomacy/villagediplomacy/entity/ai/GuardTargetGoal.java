package com.diplomacy.villagediplomacy.entity.ai;

import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/** Let a hostile mob use its existing combat behavior against village guards. */
public final class GuardTargetGoal extends NearestAttackableTargetGoal<VillageGuardEntity> {
    public GuardTargetGoal(Mob mob) {
        super(mob, VillageGuardEntity.class, 10, true, false, null);
    }

    @Override
    public boolean canUse() {
        if (mob instanceof Spider && mob.getLightLevelDependentMagicValue() >= 0.5F) {
            return false;
        }
        if (mob instanceof Hoglin hoglin && (hoglin.isBaby()
                || mob.getBrain().hasMemoryValue(MemoryModuleType.PACIFIED)
                || mob.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_REPELLENT)
                || mob.getBrain().hasMemoryValue(MemoryModuleType.AVOID_TARGET))) {
            return false;
        }
        // Brain-driven mobs may already be fighting without using Mob#getTarget.
        if (usesAttackMemory() && mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            return false;
        }
        return super.canUse();
    }

    @Override
    public void start() {
        super.start();
        if (usesAttackMemory()) {
            updateAttackMemory();
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (usesAttackMemory()) {
            var brainTarget = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
            if (brainTarget.isPresent() && brainTarget.get() != target) {
                return false;
            }
            if (mob instanceof Hoglin && (mob.getBrain().hasMemoryValue(MemoryModuleType.PACIFIED)
                    || mob.getBrain().hasMemoryValue(MemoryModuleType.AVOID_TARGET))) {
                return false;
            }
        }
        return super.canContinueToUse();
    }

    @Override
    public void tick() {
        if (usesAttackMemory() && target != null && target.isAlive()) {
            updateAttackMemory();
        }
    }

    @Override
    public void stop() {
        if (usesAttackMemory()) {
            if (mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == target) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            if (mob instanceof AbstractPiglin
                    && mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS).orElse(null) == target) {
                mob.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
            }
        }
        super.stop();
    }

    private boolean usesAttackMemory() {
        return mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED);
    }

    private void updateAttackMemory() {
        if (!(target instanceof VillageGuardEntity guard)) {
            return;
        }
        // Piglin brains validate combat against their sensed nemesis, not Mob#getTarget.
        if (mob instanceof AbstractPiglin) {
            mob.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS, guard);
        }
        mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, guard, 200L);
    }
}
