package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.block.DiplomacyTableBlock;
import com.diplomacy.villagediplomacy.core.ModBlocks;
import com.diplomacy.villagediplomacy.core.ModMenus;
import com.diplomacy.villagediplomacy.diplomacy.DiplomacyTableAccess;
import com.diplomacy.villagediplomacy.diplomacy.DiplomacyTableContractService;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class DiplomacyTableMenu extends AbstractContainerMenu {
    private static final int ACCEPT_CONTRACT_BUTTON_BASE = 1_000;
    private final BlockPos tablePos;
    private final DiplomacyTableSnapshot snapshot;

    public DiplomacyTableMenu(int containerId,
                              Inventory inventory,
                              BlockPos tablePos,
                              DiplomacyTableSnapshot snapshot) {
        super(ModMenus.DIPLOMACY_TABLE.get(), containerId);
        this.tablePos = Objects.requireNonNull(tablePos, "tablePos").immutable();
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public static DiplomacyTableMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        BlockPos tablePos = buffer.readBlockPos();
        return new DiplomacyTableMenu(containerId, inventory, tablePos, DiplomacyTableSnapshot.read(buffer));
    }

    public DiplomacyTableSnapshot getSnapshot() {
        return snapshot;
    }

    public int buttonIdForContract(java.util.UUID contractId) {
        for (int index = 0; index < snapshot.contracts().size(); index++) {
            if (snapshot.contracts().get(index).contractId().equals(contractId)) {
                return ACCEPT_CONTRACT_BUTTON_BASE + index;
            }
        }
        return -1;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || buttonId < ACCEPT_CONTRACT_BUTTON_BASE) {
            return false;
        }
        int contractIndex = buttonId - ACCEPT_CONTRACT_BUTTON_BASE;
        if (contractIndex < 0 || contractIndex >= snapshot.contracts().size()) {
            return false;
        }

        ServerLevel level = serverPlayer.serverLevel();
        SettlementSavedData data = SettlementSavedData.get(level);
        DiplomacyTableAccess.Result access = DiplomacyTableAccess.evaluate(data, tablePos);
        if (!access.allowed()) {
            return false;
        }
        Settlement settlement = access.settlement();
        java.util.UUID contractId = snapshot.contracts().get(contractIndex).contractId();
        DiplomacyTableContractService.AcceptResult result = DiplomacyTableContractService.acceptOffer(
                data, settlement.getId(), serverPlayer.getUUID(), contractId, level.getGameTime());
        String messageKey = switch (result) {
            case ACCEPTED -> "message.villagediplomacy.contract.accepted";
            case EXPIRED -> "message.villagediplomacy.contract.expired";
            case INSUFFICIENT_STOCK -> "message.villagediplomacy.contract.insufficient_stock";
            case NOT_LEADER -> "message.villagediplomacy.contract.leader_only";
            case UNAVAILABLE -> "message.villagediplomacy.contract.unavailable";
        };
        serverPlayer.displayClientMessage(Component.translatable(messageKey), true);
        DiplomacyTableBlock.openFor(serverPlayer, tablePos);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!AbstractContainerMenu.stillValid(
                ContainerLevelAccess.create(player.level(), tablePos),
                player,
                ModBlocks.DIPLOMACY_TABLE.get())) {
            return false;
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            return DiplomacyTableAccess.evaluate(SettlementSavedData.get(serverLevel), tablePos).allowed();
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
