package com.diplomacy.villagediplomacy.network;

import com.diplomacy.villagediplomacy.client.renderer.ResidentAppearance;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.garrison.ResidentData;
import com.diplomacy.villagediplomacy.menu.GarrisonMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Appearance plus menu-scoped management; inventory uses vanilla slot packets. */
public final class ResidentNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryParse("villagediplomacy:residents"), () -> "1", "1"::equals, "1"::equals);
    public record Appearance(UUID entity, String region) { }
    public record Action(int container, UUID identity) { }
    public static void register() {
        CHANNEL.registerMessage(0, Appearance.class,
                (message, buffer) -> { buffer.writeUUID(message.entity()); buffer.writeUtf(message.region(), 16); },
                buffer -> new Appearance(buffer.readUUID(), buffer.readUtf(16)), ResidentNetwork::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Action.class,
                (message, buffer) -> { buffer.writeVarInt(message.container()); buffer.writeUUID(message.identity()); },
                buffer -> new Action(buffer.readVarInt(), buffer.readUUID()),
                (message, context) -> {
                    context.get().enqueueWork(() -> {
                        ServerPlayer sender = context.get().getSender();
                        if (sender != null && sender.containerMenu instanceof GarrisonMenu menu
                                && menu.containerId == message.container()) menu.actOnResident(sender, message.identity());
                    });
                    context.get().setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void sendAction(int container, UUID identity) { CHANNEL.sendToServer(new Action(container, identity)); }
    private static void handle(Appearance message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ResidentAppearance.accept(message.entity(), message.region())));
        context.get().setPacketHandled(true);
    }
    private static Appearance appearance(ServerLevel level, Villager villager) {
        Settlement home = ResidentData.home(level, villager);
        return new Appearance(villager.getUUID(), home == null ? "base" : home.getPrimarySpecialization().getSerializedName());
    }
    public static void tracking(ServerPlayer viewer, Villager villager) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), appearance(viewer.serverLevel(), villager));
    }
    public static void refresh(ServerLevel level, Villager villager) {
        Appearance appearance = appearance(level, villager);
        var data = ResidentData.data(villager);
        if (!appearance.region().equals(data.getString("SentRegion"))) {
            data.putString("SentRegion", appearance.region());
            CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> villager), appearance);
        }
    }
}
