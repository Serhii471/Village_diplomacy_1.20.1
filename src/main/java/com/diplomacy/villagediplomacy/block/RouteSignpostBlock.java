package com.diplomacy.villagediplomacy.block;

import com.diplomacy.villagediplomacy.core.ModItems;
import com.diplomacy.villagediplomacy.diplomacy.*;
import com.diplomacy.villagediplomacy.menu.RouteMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RouteSignpostBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public RouteSignpostBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                               BlockPos pos, CollisionContext context) {
        return Block.box(1, 0, 1, 15, 16, 15);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouteSignpostBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (world, pos, block, entity) -> {
            if (world.getGameTime() % 100L == 0 && entity instanceof RouteSignpostBlockEntity sign) {
                sign.refresh();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RouteSignpostBlockEntity sign && placer != null) {
            sign.setOwner(placer.getUUID());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof RouteSignpostBlockEntity sign) {
            ItemStack held = player.getItemInHand(hand);
            var tag = held.getTag();
            if (held.is(ModItems.TRADE_ROUTE_MAP.get()) && tag != null && tag.hasUUID("Source")
                    && tag.hasUUID("Target") && tag.hasUUID("RouteId")) {
                SettlementSavedData data = SettlementSavedData.get(server.serverLevel());
                var from = data.getSettlement(tag.getUUID("Source"));
                var to = data.getSettlement(tag.getUUID("Target"));
                var route = TradeRouteMapData.resolve(data, tag, level.dimension().location().toString());
                if (from != null && to != null && route != null && !route.closed()) {
                    if (pos.distSqr(to.getCenter()) < pos.distSqr(from.getCenter())) {
                        var swap = from;
                        from = to;
                        to = swap;
                    }
                    var result = TradeRouteService.bindSignpost(data, from.getId(), to.getId(), pos, player.getUUID(), sign.owner());
                    server.displayClientMessage(Component.translatable("message.villagediplomacy.route."
                            + result.name().toLowerCase(java.util.Locale.ROOT)), false);
                    sign.refresh();
                } else {
                    server.displayClientMessage(Component.translatable("message.villagediplomacy.route.unavailable"), false);
                }
            } else {
                sign.refresh();
                RouteMenu.open(server, pos, true, null);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            SettlementSavedData.get(server).removeSignpost(pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
