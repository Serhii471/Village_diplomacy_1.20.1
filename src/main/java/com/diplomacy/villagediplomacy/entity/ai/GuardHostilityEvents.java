package com.diplomacy.villagediplomacy.entity.ai;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VillageDiplomacyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuardHostilityEvents {
    private GuardHostilityEvents() {
    }

    @SubscribeEvent
    public static void onMobJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)
                || !(mob instanceof Enemy) || mob instanceof NeutralMob
                || mob instanceof Piglin || mob instanceof Warden) {
            // Preserve anger/gold/sensory rules for conditionally hostile mobs.
            return;
        }
        if (mob.targetSelector.getAvailableGoals().stream()
                .noneMatch(goal -> goal.getGoal() instanceof GuardTargetGoal)) {
            // Retaliation and existing player/villager goals keep their higher priorities.
            mob.targetSelector.addGoal(4, new GuardTargetGoal(mob));
        }
    }

    @SubscribeEvent
    public static void onSlimeTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Slime slime) || !slime.isEffectiveAi()
                || !(slime.getTarget() instanceof VillageGuardEntity guard)
                || !guard.isAlive() || !slime.canAttack(guard)) {
            return;
        }
        // Vanilla slime collision damage is hard-coded for players and iron golems.
        // Selecting a guard alone would make slimes chase it without ever hurting it.
        boolean magmaCube = slime instanceof MagmaCube;
        if ((!magmaCube && slime.getSize() <= 1) || !slime.hasLineOfSight(guard)) {
            return;
        }
        double reach = 0.6D * slime.getSize();
        if (slime.distanceToSqr(guard) < reach * reach) {
            float damage = (float) slime.getAttributeValue(Attributes.ATTACK_DAMAGE) + (magmaCube ? 2.0F : 0.0F);
            if (guard.hurt(slime.damageSources().mobAttack(slime), damage)) {
                slime.playSound(SoundEvents.SLIME_ATTACK, 1.0F,
                        (slime.getRandom().nextFloat() - slime.getRandom().nextFloat()) * 0.2F + 1.0F);
            }
        }
    }
}
