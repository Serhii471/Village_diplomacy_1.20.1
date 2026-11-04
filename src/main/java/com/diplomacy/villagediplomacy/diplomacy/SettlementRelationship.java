package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

/**
 * Settlement-local view of another settlement.
 *
 * <p>Mutual actions are updated through {@link SettlementSavedData}, which
 * keeps the two settlement records synchronized.</p>
 */
public final class SettlementRelationship {
    private final UUID targetSettlementId;
    private boolean known;
    private int reputation;
    private TradeAgreementStatus tradeAgreementStatus = TradeAgreementStatus.NONE;
    private AllianceStatus allianceStatus = AllianceStatus.NONE;
    private int successfulDeliveries;
    private int failedContracts;
    private long lastInteractionGameTime;
    private Runnable dirtyCallback = () -> { };

    public SettlementRelationship(UUID targetSettlementId) {
        this.targetSettlementId = Objects.requireNonNull(targetSettlementId, "targetSettlementId");
    }

    public UUID getTargetSettlementId() {
        return targetSettlementId;
    }

    public boolean isKnown() {
        return known;
    }

    public void setKnown(boolean known) {
        if (this.known != known) {
            this.known = known;
            markDirty();
        }
    }

    public int getReputation() {
        return reputation;
    }

    public void setReputation(int reputation) {
        int clamped = Math.max(-100, Math.min(100, reputation));
        if (this.reputation != clamped) {
            this.reputation = clamped;
            markDirty();
        }
    }

    public void adjustReputation(int delta) {
        long adjusted = (long) reputation + delta;
        setReputation((int) Math.max(-100L, Math.min(100L, adjusted)));
    }

    public DiplomacyRelation getDiplomacyRelation() {
        return DiplomacyRelation.fromScore(reputation);
    }

    public TradeAgreementStatus getTradeAgreementStatus() {
        return tradeAgreementStatus;
    }

    public void setTradeAgreementStatus(TradeAgreementStatus status) {
        TradeAgreementStatus safeStatus = Objects.requireNonNull(status, "status");
        if (tradeAgreementStatus != safeStatus) {
            tradeAgreementStatus = safeStatus;
            markDirty();
        }
    }

    public AllianceStatus getAllianceStatus() {
        return allianceStatus;
    }

    public void setAllianceStatus(AllianceStatus status) {
        AllianceStatus safeStatus = Objects.requireNonNull(status, "status");
        if (allianceStatus != safeStatus) {
            allianceStatus = safeStatus;
            markDirty();
        }
    }

    public int getSuccessfulDeliveries() {
        return successfulDeliveries;
    }

    public void recordSuccessfulDelivery(long gameTime) {
        successfulDeliveries++;
        lastInteractionGameTime = Math.max(0L, gameTime);
        markDirty();
    }

    public int getFailedContracts() {
        return failedContracts;
    }

    public void recordFailedContract(long gameTime) {
        failedContracts++;
        lastInteractionGameTime = Math.max(0L, gameTime);
        markDirty();
    }

    public long getLastInteractionGameTime() {
        return lastInteractionGameTime;
    }

    void setDirtyCallback(Runnable dirtyCallback) {
        this.dirtyCallback = Objects.requireNonNull(dirtyCallback, "dirtyCallback");
    }

    CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Target", targetSettlementId);
        tag.putBoolean("Known", known);
        tag.putInt("Reputation", reputation);
        tag.putString("TradeAgreement", tradeAgreementStatus.getSerializedName());
        tag.putString("Alliance", allianceStatus.getSerializedName());
        tag.putInt("SuccessfulDeliveries", successfulDeliveries);
        tag.putInt("FailedContracts", failedContracts);
        tag.putLong("LastInteractionGameTime", lastInteractionGameTime);
        return tag;
    }

    static SettlementRelationship loadNBT(CompoundTag tag) {
        SettlementRelationship relationship = new SettlementRelationship(tag.getUUID("Target"));
        relationship.known = tag.getBoolean("Known");
        relationship.reputation = Math.max(-100, Math.min(100, tag.getInt("Reputation")));
        relationship.tradeAgreementStatus = TradeAgreementStatus.fromSerializedName(tag.getString("TradeAgreement"));
        relationship.allianceStatus = AllianceStatus.fromSerializedName(tag.getString("Alliance"));
        relationship.successfulDeliveries = Math.max(0, tag.getInt("SuccessfulDeliveries"));
        relationship.failedContracts = Math.max(0, tag.getInt("FailedContracts"));
        relationship.lastInteractionGameTime = Math.max(0L, tag.getLong("LastInteractionGameTime"));
        return relationship;
    }

    private void markDirty() {
        dirtyCallback.run();
    }
}
