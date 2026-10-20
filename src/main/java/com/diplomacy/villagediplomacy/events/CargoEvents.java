package com.diplomacy.villagediplomacy.events;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.core.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VillageDiplomacyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CargoEvents {
    public static final String CARGO_NBT_KEY = "VillageCargo";

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }

        boolean isMount = target instanceof AbstractHorse || target instanceof Camel;
        if (!isMount) {
            return;
        }

        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());

        // Attach crate to mount
        if (held.is(ModItems.SEALED_TRADE_CRATE.get())) {
            if (target instanceof AbstractHorse horse && !horse.isTamed()) {
                player.sendSystemMessage(Component.literal("§cMount must be tamed before strapping cargo!"));
                return;
            }

            if (target.getPersistentData().contains(CARGO_NBT_KEY)) {
                player.sendSystemMessage(Component.literal("§cThis mount is already loaded with a cargo crate!"));
                return;
            }

            CompoundTag cargoTag = held.getTag() != null ? held.getTag().copy() : new CompoundTag();
            target.getPersistentData().put(CARGO_NBT_KEY, cargoTag);
            held.shrink(1);

            event.getLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.sendSystemMessage(Component.literal("§aTrade crate securely strapped to your mount!"));
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        // Unstrap crate with empty hand while sneaking
        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (target.getPersistentData().contains(CARGO_NBT_KEY)) {
                CompoundTag cargoTag = target.getPersistentData().getCompound(CARGO_NBT_KEY);
                target.getPersistentData().remove(CARGO_NBT_KEY);

                ItemStack crate = new ItemStack(ModItems.SEALED_TRADE_CRATE.get());
                crate.setTag(cargoTag);

                if (!player.addItem(crate)) {
                    target.spawnAtLocation(crate);
                }

                event.getLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 0.9F);
                player.sendSystemMessage(Component.literal("§eUnstrapped trade crate from your mount."));
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide() && entity.getPersistentData().contains(CARGO_NBT_KEY)) {
            CompoundTag cargoTag = entity.getPersistentData().getCompound(CARGO_NBT_KEY);
            ItemStack crate = new ItemStack(ModItems.SEALED_TRADE_CRATE.get());
            crate.setTag(cargoTag);
            entity.spawnAtLocation(crate);
            entity.getPersistentData().remove(CARGO_NBT_KEY);
        }
    }

    @SubscribeEvent
    public static void onMountTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || entity.tickCount % 20 != 0) {
            return;
        }

        // Grant trade speed bonus if mount is loaded with cargo
        if (entity.getPersistentData().contains(CARGO_NBT_KEY)) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, false, false, true));
        }
    }
}
