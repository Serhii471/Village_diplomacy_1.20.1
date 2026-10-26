package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.core.ModBlocks;
import com.diplomacy.villagediplomacy.core.ModMenus;
import com.diplomacy.villagediplomacy.diplomacy.DiplomacyTableAccess;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import com.diplomacy.villagediplomacy.garrison.GarrisonSavedData;
import com.diplomacy.villagediplomacy.garrison.GarrisonService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkHooks;

public final class GarrisonMenu extends AbstractContainerMenu {
    public static final int OPEN = 101, BACK = 1, REFRESH = 2, ACTION_BASE = 1000;
    private final BlockPos table;
    private final GarrisonSnapshot snapshot;
    private final SimpleContainer supplies = new SimpleContainer(3);
    public GarrisonMenu(int id, Inventory inventory, BlockPos table, GarrisonSnapshot snapshot) {
        super(ModMenus.GARRISON.get(), id);
        this.table = table.immutable(); this.snapshot = snapshot;
        for (int i = 0; i < 3; i++) {
            final int index = i;
            addSlot(new Slot(supplies, i, 225 + i * 36, 166) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return snapshot.canManage() && (index == 0 ? GarrisonService.sword(stack)
                            : index == 1 ? GarrisonService.chestplate(stack) : stack.is(Items.BREAD));
                }
                @Override public int getMaxStackSize() { return index < 2 ? 1 : 64; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 14 + col * 18, 154 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 14 + col * 18, 212));
    }
    public static GarrisonMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer) {
        return new GarrisonMenu(id, inventory, buffer.readBlockPos(), GarrisonSnapshot.read(buffer));
    }
    public GarrisonSnapshot getSnapshot() { return snapshot; }
    public SimpleContainer getSupplies() { return supplies; }
    public void actOnResident(ServerPlayer player, java.util.UUID identity) {
        // Vanilla button packets store the button ID in one signed byte. Use a
        // menu-scoped UUID packet for a paged roster, never a truncated row ID.
        for (int index = 0; index < snapshot.entries().size(); index++) {
            if (snapshot.entries().get(index).identity().equals(identity)) {
                clickMenuButton(player, ACTION_BASE + index);
                return;
            }
        }
    }
    public static void open(ServerPlayer player, BlockPos table, String message) {
        var access = DiplomacyTableAccess.evaluate(SettlementSavedData.get(player.serverLevel()), table);
        if (!access.allowed()) return;
        GarrisonSnapshot snapshot = GarrisonSnapshot.create(player.serverLevel(), player, access.settlement(), message);
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, ignored) ->
                new GarrisonMenu(id, inventory, table, snapshot), Component.translatable("screen.villagediplomacy.garrison")),
                buffer -> { buffer.writeBlockPos(table); snapshot.write(buffer); });
    }
    @Override public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(ContainerLevelAccess.create(player.level(), table), player, ModBlocks.DIPLOMACY_TABLE.get())
                && (!(player.level() instanceof ServerLevel level)
                || DiplomacyTableAccess.evaluate(SettlementSavedData.get(level), table).allowed());
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer serverPlayer) || !stillValid(player)) return false;
        if (button == BACK) {
            var access = DiplomacyTableAccess.evaluate(SettlementSavedData.get(serverPlayer.serverLevel()), table);
            if (!access.allowed()) return false;
            var main = DiplomacyTableSnapshot.create(serverPlayer.serverLevel(), serverPlayer, access.settlement());
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider((id, inventory, ignored) ->
                    new DiplomacyTableMenu(id, inventory, table, main), Component.translatable("screen.villagediplomacy.diplomacy_table")),
                    buffer -> { buffer.writeBlockPos(table); main.write(buffer); });
            return true;
        }
        if (button == REFRESH) { open(serverPlayer, table, ""); return true; }
        int index = button - ACTION_BASE;
        if (index < 0 || index >= snapshot.entries().size()) return false;
        ServerLevel level = serverPlayer.serverLevel();
        Settlement home = DiplomacyTableAccess.evaluate(SettlementSavedData.get(level), table).settlement();
        if (home == null || !home.isPlayerLeader(player.getUUID())) return false;
        var entry = snapshot.entries().get(index);
        var person = GarrisonSavedData.get(level).get(entry.identity());
        String result = "unavailable";
        if (person != null && person.home().equals(home.getId()) && person.role() == entry.role()) {
            var entity = level.getEntity(person.entity());
            if (entity instanceof Villager villager) result = person.role() == GarrisonSavedData.Role.TRAINING
                    ? GarrisonService.cancel(serverPlayer, home, villager) : GarrisonService.recruit(serverPlayer, home, villager, supplies);
            else if (entity instanceof VillageGuardEntity guard) result = GarrisonService.demobilize(serverPlayer, home, guard);
        }
        open(serverPlayer, table, result);
        return true;
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, supplies);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack item = slot.getItem(), original = item.copy();
        if (index < 3) {
            if (!moveItemStackTo(item, 3, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = GarrisonService.sword(item) ? 0 : GarrisonService.chestplate(item) ? 1 : item.is(Items.BREAD) ? 2 : -1;
            if (target < 0 || !slots.get(target).mayPlace(item) || !moveItemStackTo(item, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (item.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, item);
        return original;
    }
}
