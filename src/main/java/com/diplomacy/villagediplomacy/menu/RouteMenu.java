package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.block.DiplomacyTableBlock;
import com.diplomacy.villagediplomacy.block.RouteSignpostBlockEntity;
import com.diplomacy.villagediplomacy.core.*;
import com.diplomacy.villagediplomacy.diplomacy.*;
import com.diplomacy.villagediplomacy.item.TradeRouteMapItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import java.util.Comparator;
import java.util.UUID;

public final class RouteMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final RouteSnapshot snapshot;
    private final int selected;

    public RouteMenu(int id, Inventory inventory, BlockPos pos, RouteSnapshot snapshot, int selected) {
        super(ModMenus.ROUTES.get(), id);
        this.pos = pos.immutable();
        this.snapshot = snapshot;
        this.selected = selected;
    }

    public static RouteMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer) {
        return new RouteMenu(id, inventory, buffer.readBlockPos(), RouteSnapshot.read(buffer), buffer.readVarInt());
    }

    public RouteSnapshot snapshot() {
        return snapshot;
    }

    public int selected() {
        return Math.max(0, Math.min(selected, snapshot.targets().size() - 1));
    }

    public static boolean open(ServerPlayer player, BlockPos pos, boolean signpost, UUID preferred) {
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D
                || !player.level().getBlockState(pos).is(signpost ? ModBlocks.ROUTE_SIGNPOST.get() : ModBlocks.DIPLOMACY_TABLE.get())) {
            return false;
        }
        SettlementSavedData data = SettlementSavedData.get(player.serverLevel());
        var sign = player.level().getBlockEntity(pos) instanceof RouteSignpostBlockEntity value ? value : null;
        Settlement home;
        if (signpost) {
            home = data.getAllSettlements().stream().filter(value -> TradeRouteService.nearEndpoint(value, pos))
                    .min(Comparator.comparingDouble(value -> value.getCenter().distSqr(pos))).orElse(data.findSettlementLedBy(player.getUUID()));
        } else {
            var access = DiplomacyTableAccess.evaluate(data, pos);
            home = access.allowed() ? access.settlement() : null;
        }
        if (home == null || signpost && sign == null) {
            player.displayClientMessage(Component.translatable("message.villagediplomacy.route.unavailable"), false);
            return false;
        }
        RouteSnapshot snapshot = RouteSnapshot.create(data, home, player.getUUID(), signpost ? pos : home.getCenter(),
                signpost, sign != null && player.getUUID().equals(sign.owner()));
        int selected = 0;
        for (int i = 0; i < snapshot.targets().size(); i++) {
            if (snapshot.targets().get(i).id().equals(preferred)) {
                selected = i;
            }
        }
        int selection = selected;
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, ignored) ->
                new RouteMenu(id, inventory, pos, snapshot, selection), Component.translatable(
                "screen.villagediplomacy.routes.title")), buffer -> {
            buffer.writeBlockPos(pos);
            snapshot.write(buffer);
            buffer.writeVarInt(selection);
        });
        return true;
    }

    public void act(ServerPlayer player, int targetIndex, Action action, String name) {
        if (player.containerMenu != this || !stillValid(player) || targetIndex < 0 || targetIndex >= snapshot.targets().size()) {
            return;
        }
        SettlementSavedData data = SettlementSavedData.get(player.serverLevel());
        UUID targetId = snapshot.targets().get(targetIndex).id();
        Settlement home = data.getSettlement(snapshot.homeId());
        Settlement target = data.getSettlement(targetId);
        TradeRouteService.Result result = TradeRouteService.Result.UNAVAILABLE;
        if (home == null || target == null) {
            return;
        }
        if (snapshot.signpost() && action == Action.BIND
                && player.level().getBlockEntity(pos) instanceof RouteSignpostBlockEntity sign) {
            result = TradeRouteService.bindSignpost(data, home.getId(), targetId, pos, player.getUUID(), sign.owner());
            if (result == TradeRouteService.Result.UPDATED) {
                sign.refresh();
            }
        } else if (!snapshot.signpost() && home.isPlayerLeader(player.getUUID())) {
            if (action == Action.MAP) {
                TradeRoute route = data.findTradeRoute(home.getId(), targetId);
                if (route != null && !route.closed() && (home.getKnownSettlementIds().contains(targetId)
                        || TradeAgreementService.isAllied(home, target))) {
                    ItemStack map = TradeRouteMapItem.create(ModItems.TRADE_ROUTE_MAP.get(), route, home, target,
                            player.level().dimension().location().toString(), data);
                    result = player.getInventory().add(map) ? TradeRouteService.Result.MAP_GIVEN : TradeRouteService.Result.INVENTORY_FULL;
                    player.inventoryMenu.broadcastChanges();
                }
            } else if (action != Action.BIND) {
                result = TradeRouteService.act(data, home.getId(), targetId, player.getUUID(),
                        TradeRouteService.Action.valueOf(action.name()), name);
            }
        } else if (!snapshot.signpost()) {
            result = TradeRouteService.Result.NOT_LEADER;
        }
        player.displayClientMessage(Component.translatable("message.villagediplomacy.route."
                + result.name().toLowerCase(java.util.Locale.ROOT)), false);
        open(player, pos, snapshot.signpost(), targetId);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!AbstractContainerMenu.stillValid(ContainerLevelAccess.create(player.level(), pos), player,
                snapshot.signpost() ? ModBlocks.ROUTE_SIGNPOST.get() : ModBlocks.DIPLOMACY_TABLE.get())) {
            return false;
        }
        if (!player.level().isClientSide && !snapshot.signpost()) {
            var access = DiplomacyTableAccess.evaluate(SettlementSavedData.get(((ServerPlayer) player).serverLevel()), pos);
            return access.allowed() && access.settlement().getId().equals(snapshot.homeId());
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    public enum Action {
        CREATE, MAP, RENAME, CLOSE, BIND
    }
}
