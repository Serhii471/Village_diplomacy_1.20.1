package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.nbt.CompoundTag;

public final class TradeRouteMapData {
    private TradeRouteMapData() {
    }

    public static CompoundTag create(TradeRoute route, Settlement source, Settlement target, String dimension,
                                     SettlementSavedData data) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("RouteId", route.id());
        tag.putUUID("Source", source.getId());
        tag.putUUID("Target", target.getId());
        tag.putString("SourceName", source.getName());
        tag.putString("TargetName", target.getName());
        tag.putLong("SourcePos", source.getCenter().asLong());
        tag.putLong("TargetPos", target.getCenter().asLong());
        tag.putString("Dimension", dimension);
        tag.putString("RouteName", route.name());
        tag.putLong("Distance", Math.round(Math.sqrt(source.getCenter().distSqr(target.getCenter()))));
        tag.putString("Status", route.closed() ? "closed" : TradeRouteService.isMarked(data, source.getId(), target.getId()) ? "marked" : "active");
        return tag;
    }

    public static TradeRoute resolve(SettlementSavedData data, CompoundTag tag, String dimension) {
        if (tag == null || !tag.hasUUID("Source") || !tag.hasUUID("Target") || !tag.hasUUID("RouteId")
                || !dimension.equals(tag.getString("Dimension"))) {
            return null;
        }
        TradeRoute route = data.findTradeRoute(tag.getUUID("Source"), tag.getUUID("Target"));
        return route != null && route.id().equals(tag.getUUID("RouteId")) ? route : null;
    }
}
