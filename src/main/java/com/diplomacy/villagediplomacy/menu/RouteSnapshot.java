package com.diplomacy.villagediplomacy.menu;

import com.diplomacy.villagediplomacy.diplomacy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record RouteSnapshot(UUID homeId, String homeName, boolean canManage, boolean signpost,
                            List<Target> targets) {
    public RouteSnapshot {
        targets = List.copyOf(targets);
    }

    public static RouteSnapshot create(SettlementSavedData data, Settlement home, UUID viewer,
                                        BlockPos origin, boolean signpost, boolean ownsSign) {
        List<Target> targets = home.getRelationships().stream()
                .filter(relation -> relation.isKnown() || TradeAgreementService.isAllied(home,
                        data.getSettlement(relation.getTargetSettlementId())))
                .map(relation -> data.getSettlement(relation.getTargetSettlementId()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(Settlement::getName).thenComparing(value -> value.getId().toString()))
                .limit(256).map(target -> {
                    TradeRoute route = data.findTradeRoute(home.getId(), target.getId());
                    SettlementRelationship relation = home.getOrCreateRelationship(target.getId());
                    return new Target(target.getId(), target.getName(), target.getCenter(),
                            Math.round(Math.sqrt(origin.distSqr(target.getCenter()))),
                            TradeRouteService.bearing(origin, target.getCenter()),
                            relation.getDiplomacyRelation().name().toLowerCase(java.util.Locale.ROOT),
                            relation.getTradeAgreementStatus().getSerializedName(), TradeAgreementService.isAllied(home, target),
                            route == null ? "" : route.name(), route != null && !route.closed(),
                            TradeRouteService.isMarked(data, home.getId(), target.getId()), relation.getSuccessfulDeliveries());
                }).toList();
        return new RouteSnapshot(home.getId(), home.getName(), home.isPlayerLeader(viewer) || signpost && ownsSign, signpost, targets);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(homeId);
        buffer.writeUtf(homeName);
        buffer.writeBoolean(canManage);
        buffer.writeBoolean(signpost);
        buffer.writeVarInt(targets.size());
        targets.forEach(target -> target.write(buffer));
    }

    public static RouteSnapshot read(FriendlyByteBuf buffer) {
        UUID home = buffer.readUUID();
        String name = buffer.readUtf();
        boolean manage = buffer.readBoolean();
        boolean sign = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > 256) {
            throw new IllegalArgumentException("Invalid route target count");
        }
        List<Target> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(Target.read(buffer));
        }
        return new RouteSnapshot(home, name, manage, sign, targets);
    }

    public record Target(UUID id, String name, BlockPos center, long distance, String bearing,
                          String relation, String trade, boolean allied, String routeName,
                          boolean active, boolean marked, int deliveries) {
        void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(id);
            buffer.writeUtf(name);
            buffer.writeBlockPos(center);
            buffer.writeLong(distance);
            buffer.writeUtf(bearing);
            buffer.writeUtf(relation);
            buffer.writeUtf(trade);
            buffer.writeBoolean(allied);
            buffer.writeUtf(routeName, 64);
            buffer.writeBoolean(active);
            buffer.writeBoolean(marked);
            buffer.writeVarInt(deliveries);
        }

        static Target read(FriendlyByteBuf buffer) {
            return new Target(buffer.readUUID(), buffer.readUtf(), buffer.readBlockPos(), buffer.readLong(),
                    buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readBoolean(),
                    buffer.readUtf(64), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt());
        }
    }
}
