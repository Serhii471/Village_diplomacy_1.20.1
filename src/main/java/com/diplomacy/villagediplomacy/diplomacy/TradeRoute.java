package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class TradeRoute {
    private final UUID originId;
    private final UUID destinationId;
    private double distance;
    private int completedDeliveries;
    private boolean hasSignposts;
    private boolean hasTradePact;
    private boolean hasAlliance;

    public TradeRoute(UUID originId, UUID destinationId, double distance) {
        this.originId = originId;
        this.destinationId = destinationId;
        this.distance = distance;
    }

    public UUID getOriginId() {
        return originId;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int getCompletedDeliveries() {
        return completedDeliveries;
    }

    public void incrementDeliveries() {
        this.completedDeliveries++;
    }

    public boolean hasSignposts() {
        return hasSignposts;
    }

    public void setHasSignposts(boolean hasSignposts) {
        this.hasSignposts = hasSignposts;
    }

    public boolean hasTradePact() {
        return hasTradePact;
    }

    public void setHasTradePact(boolean hasTradePact) {
        this.hasTradePact = hasTradePact;
    }

    public boolean hasAlliance() {
        return hasAlliance;
    }

    public void setHasAlliance(boolean hasAlliance) {
        this.hasAlliance = hasAlliance;
    }

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Origin", originId);
        tag.putUUID("Destination", destinationId);
        tag.putDouble("Distance", distance);
        tag.putInt("Deliveries", completedDeliveries);
        tag.putBoolean("Signposts", hasSignposts);
        tag.putBoolean("TradePact", hasTradePact);
        tag.putBoolean("Alliance", hasAlliance);
        return tag;
    }

    public static TradeRoute loadNBT(CompoundTag tag) {
        TradeRoute route = new TradeRoute(
                tag.getUUID("Origin"),
                tag.getUUID("Destination"),
                tag.getDouble("Distance")
        );
        route.completedDeliveries = tag.getInt("Deliveries");
        route.hasSignposts = tag.getBoolean("Signposts");
        route.hasTradePact = tag.getBoolean("TradePact");
        route.hasAlliance = tag.getBoolean("Alliance");
        return route;
    }
}
