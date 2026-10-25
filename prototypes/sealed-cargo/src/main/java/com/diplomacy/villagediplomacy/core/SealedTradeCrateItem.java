package com.diplomacy.villagediplomacy.core;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class SealedTradeCrateItem extends Item {
    public static final String NBT_CONTRACT_ID = "ContractId";
    public static final String NBT_SENDER_NAME = "SenderName";
    public static final String NBT_TARGET_NAME = "TargetName";
    public static final String NBT_EXPIRY = "Expiry";

    public SealedTradeCrateItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide() && entity instanceof Player player) {
            // Apply burden effect if carried on foot without a mount
            if (player.getVehicle() == null) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBT_CONTRACT_ID)) {
            String sender = tag.getString(NBT_SENDER_NAME);
            String target = tag.getString(NBT_TARGET_NAME);
            long expiry = tag.getLong(NBT_EXPIRY);

            tooltip.add(Component.literal("§6Route: §e" + sender + " §7➔ §e" + target));
            tooltip.add(Component.literal("§7Sealed by village authority. Do not tamper.").withStyle(ChatFormatting.ITALIC));
            if (level != null) {
                long remainingTicks = expiry - level.getDayTime();
                if (remainingTicks > 0) {
                    long minutes = remainingTicks / (20 * 60);
                    tooltip.add(Component.literal("§cExpires in: §e~" + minutes + " min"));
                } else {
                    tooltip.add(Component.literal("§4EXPIRED CONTRACT").withStyle(ChatFormatting.BOLD));
                }
            }
        } else {
            tooltip.add(Component.literal("§7Unassigned trade crate.").withStyle(ChatFormatting.GRAY));
        }
    }
}
