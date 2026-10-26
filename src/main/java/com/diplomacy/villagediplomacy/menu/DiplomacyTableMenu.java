package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.core.ModBlocks;
import com.diplomacy.villagediplomacy.core.ModMenus;
import com.diplomacy.villagediplomacy.diplomacy.DiplomacyTableAccess;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class DiplomacyTableMenu extends AbstractContainerMenu {
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

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button == GarrisonMenu.OPEN && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && stillValid(player)) {
            GarrisonMenu.open(serverPlayer, tablePos, "");
            return true;
        }
        return false;
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
