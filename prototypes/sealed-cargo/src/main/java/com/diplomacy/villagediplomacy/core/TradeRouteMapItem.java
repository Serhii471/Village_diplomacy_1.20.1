package com.diplomacy.villagediplomacy.core;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class TradeRouteMapItem extends Item {
    public static final String NBT_ORIGIN_NAME = "OriginName";
    public static final String NBT_DESTINATION_NAME = "DestinationName";
    public static final String NBT_DISTANCE = "Distance";

    public TradeRouteMapItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBT_ORIGIN_NAME)) {
            String origin = tag.getString(NBT_ORIGIN_NAME);
            String destination = tag.getString(NBT_DESTINATION_NAME);
            double dist = tag.getDouble(NBT_DISTANCE);

            tooltip.add(Component.literal("§6Chartered Route: §e" + origin + " §7⇄ §e" + destination));
            tooltip.add(Component.literal(String.format("§7Estimated distance: §a%.1f blocks", dist)));
            tooltip.add(Component.literal("§8Used to link Route Signposts").withStyle(ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.literal("§7Blank route charter. Link at a Diplomacy Table.").withStyle(ChatFormatting.GRAY));
        }
    }
}
