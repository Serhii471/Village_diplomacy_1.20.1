package com.diplomacy.villagediplomacy.block;

import com.diplomacy.villagediplomacy.core.ModBlockEntities;
import com.diplomacy.villagediplomacy.diplomacy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class RouteSignpostBlockEntity extends BlockEntity {
    private UUID owner;
    private String targetName = "";
    private String direction = "n";
    private long distance;
    private boolean allied;

    public RouteSignpostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROUTE_SIGNPOST.get(), pos, state);
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public String targetName() {
        return targetName;
    }

    public String direction() {
        return direction;
    }

    public long distance() {
        return distance;
    }

    public boolean allied() {
        return allied;
    }

    public void refresh() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        SettlementSavedData data = SettlementSavedData.get(server);
        var link = data.getSignposts().get(worldPosition);
        Settlement home = link == null ? null : data.getSettlement(link.homeId());
        Settlement target = link == null ? null : data.getSettlement(link.targetId());
        String name = target == null ? "" : target.getName();
        String bearing = target == null ? "n" : TradeRouteService.bearing(worldPosition, target.getCenter());
        long blocks = target == null ? 0L : Math.round(Math.sqrt(worldPosition.distSqr(target.getCenter())));
        boolean isAlly = TradeAgreementService.isAllied(home, target);
        if (!targetName.equals(name) || !direction.equals(bearing) || distance != blocks || allied != isAlly) {
            targetName = name;
            direction = bearing;
            distance = blocks;
            allied = isAlly;
            setChanged();
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("TargetName", targetName);
        tag.putString("Bearing", direction);
        tag.putLong("Distance", distance);
        tag.putBoolean("Allied", allied);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        targetName = tag.getString("TargetName");
        direction = tag.getString("Bearing");
        distance = tag.getLong("Distance");
        allied = tag.getBoolean("Allied");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
