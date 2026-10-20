package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.UUID;

public class TradeContract {
    public enum Status {
        AVAILABLE,
        IN_TRANSIT,
        COMPLETED,
        EXPIRED
    }

    private final UUID id;
    private final UUID senderSettlementId;
    private final String senderName;
    private final UUID targetSettlementId;
    private final String targetName;

    private final Item offeredItem;
    private final int offeredCount;
    private final Item requestedItem;
    private final int requestedCount;

    private final int rewardEmeralds;
    private final int reputationGain;
    private final long expiryGameTime;
    private Status status;

    public TradeContract(UUID id, UUID senderSettlementId, String senderName,
                         UUID targetSettlementId, String targetName,
                         Item offeredItem, int offeredCount,
                         Item requestedItem, int requestedCount,
                         int rewardEmeralds, int reputationGain,
                         long expiryGameTime, Status status) {
        this.id = id;
        this.senderSettlementId = senderSettlementId;
        this.senderName = senderName;
        this.targetSettlementId = targetSettlementId;
        this.targetName = targetName;
        this.offeredItem = offeredItem;
        this.offeredCount = offeredCount;
        this.requestedItem = requestedItem;
        this.requestedCount = requestedCount;
        this.rewardEmeralds = rewardEmeralds;
        this.reputationGain = reputationGain;
        this.expiryGameTime = expiryGameTime;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSenderSettlementId() {
        return senderSettlementId;
    }

    public String getSenderName() {
        return senderName;
    }

    public UUID getTargetSettlementId() {
        return targetSettlementId;
    }

    public String getTargetName() {
        return targetName;
    }

    public Item getOfferedItem() {
        return offeredItem;
    }

    public int getOfferedCount() {
        return offeredCount;
    }

    public Item getRequestedItem() {
        return requestedItem;
    }

    public int getRequestedCount() {
        return requestedCount;
    }

    public int getRewardEmeralds() {
        return rewardEmeralds;
    }

    public int getReputationGain() {
        return reputationGain;
    }

    public long getExpiryGameTime() {
        return expiryGameTime;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isExpired(long currentGameTime) {
        return currentGameTime > expiryGameTime && status != Status.COMPLETED;
    }

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("SenderId", senderSettlementId);
        tag.putString("SenderName", senderName);
        tag.putUUID("TargetId", targetSettlementId);
        tag.putString("TargetName", targetName);

        tag.putString("OfferedItem", BuiltInRegistries.ITEM.getKey(offeredItem).toString());
        tag.putInt("OfferedCount", offeredCount);
        tag.putString("RequestedItem", BuiltInRegistries.ITEM.getKey(requestedItem).toString());
        tag.putInt("RequestedCount", requestedCount);

        tag.putInt("RewardEmeralds", rewardEmeralds);
        tag.putInt("ReputationGain", reputationGain);
        tag.putLong("Expiry", expiryGameTime);
        tag.putString("Status", status.name());
        return tag;
    }

    public static TradeContract loadNBT(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        UUID senderId = tag.getUUID("SenderId");
        String senderName = tag.getString("SenderName");
        UUID targetId = tag.getUUID("TargetId");
        String targetName = tag.getString("TargetName");

        Item offered = BuiltInRegistries.ITEM.get(new ResourceLocation(tag.getString("OfferedItem")));
        int offeredCount = tag.getInt("OfferedCount");
        Item requested = BuiltInRegistries.ITEM.get(new ResourceLocation(tag.getString("RequestedItem")));
        int requestedCount = tag.getInt("RequestedCount");

        int reward = tag.getInt("RewardEmeralds");
        int rep = tag.getInt("ReputationGain");
        long expiry = tag.getLong("Expiry");
        Status status = Status.valueOf(tag.getString("Status"));

        return new TradeContract(id, senderId, senderName, targetId, targetName,
                offered, offeredCount, requested, requestedCount, reward, rep, expiry, status);
    }
}
