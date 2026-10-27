package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.core.ModMenus;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.TradeContractService;
import com.diplomacy.villagediplomacy.gameplay.TradeHandoverService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.NetworkHooks;

import java.util.Objects;

public final class TradeHandoverMenu extends AbstractContainerMenu {
    private static final int ACTION_BUTTON_BASE = 1_000;
    private final BlockPos bellPos;
    private final TradeHandoverSnapshot snapshot;

    public TradeHandoverMenu(int containerId,
                             Inventory inventory,
                             BlockPos bellPos,
                             TradeHandoverSnapshot snapshot) {
        super(ModMenus.TRADE_HANDOVER.get(), containerId);
        this.bellPos = Objects.requireNonNull(bellPos, "bellPos").immutable();
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public static TradeHandoverMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        return new TradeHandoverMenu(
                containerId,
                inventory,
                buffer.readBlockPos(),
                TradeHandoverSnapshot.read(buffer)
        );
    }

    public static boolean openFor(ServerPlayer player, Settlement settlement, BlockPos bellPos) {
        ServerLevel level = player.serverLevel();
        if (!settlement.getCenter().equals(bellPos)
                || !level.getBlockState(bellPos).is(Blocks.BELL)
                || player.distanceToSqr(
                        bellPos.getX() + 0.5D,
                        bellPos.getY() + 0.5D,
                        bellPos.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        SettlementSavedData data = SettlementSavedData.get(level);
        TradeContractService.expireDueContracts(data, level.getGameTime());
        TradeHandoverSnapshot snapshot = TradeHandoverSnapshot.create(
                data, settlement, player, level.getGameTime());
        if (snapshot.contracts().isEmpty()) {
            return false;
        }
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) ->
                                new TradeHandoverMenu(containerId, inventory, bellPos, snapshot),
                        Component.translatable("screen.villagediplomacy.trade_handover")
                ),
                buffer -> {
                    buffer.writeBlockPos(bellPos);
                    snapshot.write(buffer);
                }
        );
        return true;
    }

    public TradeHandoverSnapshot getSnapshot() {
        return snapshot;
    }

    public int buttonIdForContract(java.util.UUID contractId) {
        for (int index = 0; index < snapshot.contracts().size(); index++) {
            if (snapshot.contracts().get(index).contractId().equals(contractId)) {
                return ACTION_BUTTON_BASE + index;
            }
        }
        return -1;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || buttonId < ACTION_BUTTON_BASE
                || !stillValid(player)) {
            return false;
        }
        int contractIndex = buttonId - ACTION_BUTTON_BASE;
        if (contractIndex < 0 || contractIndex >= snapshot.contracts().size()) {
            return false;
        }
        ServerLevel level = serverPlayer.serverLevel();
        SettlementSavedData data = SettlementSavedData.get(level);
        Settlement settlement = data.getSettlement(snapshot.settlementId());
        if (settlement == null || !settlement.getCenter().equals(bellPos)) {
            return false;
        }
        TradeHandoverService.HandoverSummary summary = TradeHandoverService.handleContractAtSettlement(
                data,
                settlement.getId(),
                snapshot.contracts().get(contractIndex).contractId(),
                serverPlayer,
                level.getGameTime()
        );
        TradeHandoverService.sendResult(serverPlayer, summary);
        if (!openFor(serverPlayer, settlement, bellPos)) {
            serverPlayer.closeContainer();
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!AbstractContainerMenu.stillValid(
                ContainerLevelAccess.create(player.level(), bellPos), player, Blocks.BELL)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        Settlement settlement = SettlementSavedData.get(level).getSettlement(snapshot.settlementId());
        return settlement != null && settlement.getCenter().equals(bellPos);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
