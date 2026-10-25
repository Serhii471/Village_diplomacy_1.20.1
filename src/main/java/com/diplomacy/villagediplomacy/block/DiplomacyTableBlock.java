package com.diplomacy.villagediplomacy.block;

import com.diplomacy.villagediplomacy.diplomacy.DiplomacyTableAccess;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.menu.DiplomacyTableMenu;
import com.diplomacy.villagediplomacy.menu.DiplomacyTableSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public final class DiplomacyTableBlock extends Block {
    public DiplomacyTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level,
                            BlockPos pos,
                            BlockState state,
                            LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        DiplomacyTableAccess.Result access = DiplomacyTableAccess.evaluate(
                SettlementSavedData.get(serverLevel),
                pos
        );
        if (access.allowed()) {
            return;
        }
        if (placer instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable(messageKey(access.failure())), true);
        }
        boolean dropTable = !(placer instanceof Player placingPlayer)
                || !placingPlayer.getAbilities().instabuild;
        level.destroyBlock(pos, dropTable, placer);
    }

    @Override
    public InteractionResult use(BlockState state,
                                 Level level,
                                 BlockPos pos,
                                 Player player,
                                 InteractionHand hand,
                                 BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        DiplomacyTableAccess.Result access = DiplomacyTableAccess.evaluate(
                SettlementSavedData.get(serverLevel),
                pos
        );
        if (!access.allowed()) {
            serverPlayer.displayClientMessage(Component.translatable(messageKey(access.failure())), true);
            return InteractionResult.CONSUME;
        }

        Settlement settlement = access.settlement();
        DiplomacyTableSnapshot snapshot = DiplomacyTableSnapshot.create(serverLevel, serverPlayer, settlement);
        MenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) ->
                        new DiplomacyTableMenu(containerId, inventory, pos, snapshot),
                Component.translatable("screen.villagediplomacy.diplomacy_table")
        );
        NetworkHooks.openScreen(serverPlayer, provider, buffer -> {
            buffer.writeBlockPos(pos);
            snapshot.write(buffer);
        });
        return InteractionResult.CONSUME;
    }

    private static String messageKey(DiplomacyTableAccess.Failure failure) {
        return failure == DiplomacyTableAccess.Failure.OUTSIDE_SETTLEMENT
                ? "message.villagediplomacy.table.outside_settlement"
                : "message.villagediplomacy.table.too_far";
    }
}
