package com.diplomacy.villagediplomacy.network;

import com.diplomacy.villagediplomacy.menu.DiplomacyTableMenu;
import com.diplomacy.villagediplomacy.menu.TradeHandoverMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class DiplomacyNetwork {
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryParse("villagediplomacy:menu_actions"), () -> VERSION,
            VERSION::equals, VERSION::equals);

    private DiplomacyNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, MenuAction.class, MenuAction::write, MenuAction::read,
                MenuAction::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendMenuAction(int containerId, int actionId) {
        CHANNEL.sendToServer(new MenuAction(containerId, actionId));
    }

    public record MenuAction(int containerId, int actionId) {
        void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(containerId);
            buffer.writeVarInt(actionId);
        }

        static MenuAction read(FriendlyByteBuf buffer) {
            return new MenuAction(buffer.readVarInt(), buffer.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !player.isAlive() || player.isSpectator()) {
                    return;
                }
                var menu = player.containerMenu;
                if (menu.containerId == containerId && menu.stillValid(player)
                        && (menu instanceof DiplomacyTableMenu || menu instanceof TradeHandoverMenu)) {
                    menu.clickMenuButton(player, actionId);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
