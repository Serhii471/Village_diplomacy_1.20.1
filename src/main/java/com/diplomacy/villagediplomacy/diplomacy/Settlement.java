package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Settlement {
    private final UUID id;
    private String name;
    private BlockPos center;
    private int radius;
    @Nullable
    private UUID leaderUuid;
    private final Map<UUID, Integer> relations = new HashMap<>();

    public Settlement(UUID id, String name, BlockPos center, int radius) {
        this.id = id;
        this.name = name;
        this.center = center;
        this.radius = radius;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BlockPos getCenter() {
        return center;
    }

    public void setCenter(BlockPos center) {
        this.center = center;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    @Nullable
    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(@Nullable UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public boolean isPlayerLeader(UUID playerUuid) {
        return leaderUuid != null && leaderUuid.equals(playerUuid);
    }

    public int getReputationWith(UUID otherSettlementId) {
        return relations.getOrDefault(otherSettlementId, 0);
    }

    public DiplomacyRelation getRelationWith(UUID otherSettlementId) {
        return DiplomacyRelation.fromScore(getReputationWith(otherSettlementId));
    }

    public void setReputationWith(UUID otherSettlementId, int reputation) {
        int clamped = Math.max(-100, Math.min(100, reputation));
        relations.put(otherSettlementId, clamped);
    }

    public void adjustReputationWith(UUID otherSettlementId, int delta) {
        setReputationWith(otherSettlementId, getReputationWith(otherSettlementId) + delta);
    }

    public boolean contains(BlockPos pos) {
        return center.distSqr(pos) <= (long) radius * radius;
    }

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putLong("Center", center.asLong());
        tag.putInt("Radius", radius);

        if (leaderUuid != null) {
            tag.putUUID("Leader", leaderUuid);
        }

        ListTag relList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : relations.entrySet()) {
            CompoundTag relTag = new CompoundTag();
            relTag.putUUID("Target", entry.getKey());
            relTag.putInt("Score", entry.getValue());
            relList.add(relTag);
        }
        tag.put("Relations", relList);

        return tag;
    }

    public static Settlement loadNBT(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        String name = tag.getString("Name");
        BlockPos center = BlockPos.of(tag.getLong("Center"));
        int radius = tag.getInt("Radius");

        Settlement settlement = new Settlement(id, name, center, radius);

        if (tag.hasUUID("Leader")) {
            settlement.setLeaderUuid(tag.getUUID("Leader"));
        }

        if (tag.contains("Relations", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Relations", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag relTag = list.getCompound(i);
                settlement.setReputationWith(relTag.getUUID("Target"), relTag.getInt("Score"));
            }
        }

        return settlement;
    }
}
