package com.diplomacy.villagediplomacy.garrison;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import com.diplomacy.villagediplomacy.network.ResidentNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VillageDiplomacyMod.MOD_ID)
public final class ResidentEvents {
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getEntity() instanceof Villager villager) GarrisonService.register(level, villager);
        else if (event.getEntity() instanceof VillageGuardEntity guard) GarrisonService.register(level, guard);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Villager villager)
            ResidentNetwork.tracking(player, villager);
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Villager) && !(event.getEntity() instanceof VillageGuardEntity)) return;
        var reason = event.getEntity().getRemovalReason();
        if (reason == null || reason == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_WITH_PLAYER) return;
        GarrisonSavedData roster = GarrisonSavedData.get(level);
        var person = roster.byEntity(event.getEntity().getUUID());
        if (person != null) roster.remove(person.identity());
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Villager) && !(event.getEntity() instanceof VillageGuardEntity)) return;
        if (event.getEntity() instanceof Villager villager && ResidentData.isTraining(villager))
            GarrisonService.cancelTraining(level, villager, null);
        GarrisonSavedData roster = GarrisonSavedData.get(level);
        GarrisonSavedData.Person person = roster.byEntity(event.getEntity().getUUID());
        if (person != null) roster.remove(person.identity());
    }
    @SubscribeEvent public static void converted(LivingConversionEvent.Post event) {
        if (event.getEntity() instanceof Villager villager && villager.level() instanceof ServerLevel level) {
            if (ResidentData.isTraining(villager)) GarrisonService.cancelTraining(level, villager, null);
            GarrisonSavedData.get(level).remove(villager.getUUID());
        }
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.getGameTime() % 20 != 0) return;
        var loaded = java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false).toList();
        for (var entity : loaded) {
            if (entity instanceof Villager villager) {
                GarrisonService.register(level, villager);
                ResidentNetwork.refresh(level, villager);
                if (ResidentData.isTraining(villager)) GarrisonService.finishTraining(level, villager);
            } else if (entity instanceof VillageGuardEntity guard) GarrisonService.register(level, guard);
        }
    }
}
