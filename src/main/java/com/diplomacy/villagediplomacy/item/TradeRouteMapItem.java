package com.diplomacy.villagediplomacy.item;

import com.diplomacy.villagediplomacy.diplomacy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class TradeRouteMapItem extends Item {
    public TradeRouteMapItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, TradeRoute route, Settlement source, Settlement target, String dimension,
                                    SettlementSavedData data) {
        ItemStack stack = new ItemStack(item);
        stack.setTag(TradeRouteMapData.create(route, source, target, dimension, data));
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            CompoundTag tag = stack.getTag();
            SettlementSavedData data = SettlementSavedData.get(serverPlayer.serverLevel());
            TradeRoute route = TradeRouteMapData.resolve(data, tag, level.dimension().location().toString());
            if (route == null) {
                serverPlayer.displayClientMessage(Component.translatable("message.villagediplomacy.route.unavailable"), false);
            } else {
                Settlement source = data.getSettlement(tag.getUUID("Source"));
                Settlement target = data.getSettlement(tag.getUUID("Target"));
                if (source == null || target == null) {
                    serverPlayer.displayClientMessage(Component.translatable("message.villagediplomacy.route.unavailable"), false);
                } else {
                    stack.setTag(create(this, route, source, target, tag.getString("Dimension"), data).getTag());
                    serverPlayer.displayClientMessage(Component.translatable("screen.villagediplomacy.routes.overview",
                            route.name(), Component.translatable("status.villagediplomacy.route." + stack.getTag().getString("Status"))), false);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID("RouteId")) {
            tooltip.add(Component.literal(tag.getString("RouteName")));
            tooltip.add(Component.literal(tag.getString("SourceName") + " -> " + tag.getString("TargetName")));
            BlockPos from = BlockPos.of(tag.getLong("SourcePos"));
            BlockPos to = BlockPos.of(tag.getLong("TargetPos"));
            tooltip.add(Component.literal(from.toShortString() + " -> " + to.toShortString()));
            tooltip.add(Component.translatable("screen.villagediplomacy.routes.distance", tag.getLong("Distance"),
                    Component.translatable("status.villagediplomacy.route." + tag.getString("Status"))));
            tooltip.add(Component.literal(tag.getString("Dimension")));
        }
    }
}
