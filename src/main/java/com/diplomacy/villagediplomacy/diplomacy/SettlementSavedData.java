package com.diplomacy.villagediplomacy.diplomacy;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SettlementSavedData extends SavedData {
    public static final String DATA_NAME = "village_diplomacy_settlements";
    public static final int CURRENT_DATA_VERSION = 4;

    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();
    private final Map<UUID, TradeContract> tradeContracts = new LinkedHashMap<>();

    public SettlementSavedData() {
    }

    public static SettlementSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                SettlementSavedData::load,
                SettlementSavedData::new,
                DATA_NAME
        );
    }

    public int getDataVersion() {
        return CURRENT_DATA_VERSION;
    }

    public Collection<Settlement> getAllSettlements() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    public Collection<TradeContract> getAllTradeContracts() {
        return Collections.unmodifiableCollection(tradeContracts.values());
    }

    @Nullable
    public TradeContract getTradeContract(UUID id) {
        return tradeContracts.get(id);
    }

    @Nullable
    public Settlement getSettlement(UUID id) {
        return settlements.get(id);
    }

    @Nullable
    public Settlement findSettlementLedBy(UUID playerId) {
        for (Settlement settlement : settlements.values()) {
            if (settlement.isPlayerLeader(playerId)) {
                return settlement;
            }
        }
        return null;
    }

    public void registerSettlement(Settlement settlement) {
        Settlement safeSettlement = Objects.requireNonNull(settlement, "settlement");
        attachDirtyTracking(safeSettlement);
        settlements.put(safeSettlement.getId(), safeSettlement);
        setDirty();
    }

    public void registerTradeContract(TradeContract contract) {
        TradeContract safeContract = Objects.requireNonNull(contract, "contract");
        requireSettlement(safeContract.getSourceSettlementId());
        requireOtherSettlement(safeContract.getDestinationSettlementId(), safeContract.getSourceSettlementId());
        if (tradeContracts.containsKey(safeContract.getId())) {
            throw new IllegalArgumentException("Duplicate trade contract: " + safeContract.getId());
        }
        attachDirtyTracking(safeContract);
        tradeContracts.put(safeContract.getId(), safeContract);
        setDirty();
    }

    public void removeSettlement(UUID id) {
        tradeContracts.values().stream()
                .filter(contract -> contract.involves(id))
                .filter(contract -> contract.getStatus() == TradeContractStatus.ACTIVE)
                .forEach(contract -> TradeContractService.releaseRemainingReservations(this, contract));
        if (tradeContracts.entrySet().removeIf(entry -> entry.getValue().involves(id))) {
            setDirty();
        }
        if (settlements.remove(id) != null) {
            settlements.values().forEach(settlement -> settlement.removeRelationship(id));
            setDirty();
        }
    }

    @Nullable
    public Settlement findSettlementAt(BlockPos pos) {
        for (Settlement settlement : settlements.values()) {
            if (settlement.contains(pos)) {
                return settlement;
            }
        }
        return null;
    }

    public Settlement getOrCreateSettlementNear(BlockPos pos, int defaultRadius, String defaultName) {
        Settlement existing = findSettlementAt(pos);
        if (existing != null) {
            return existing;
        }

        Settlement newSettlement = new Settlement(UUID.randomUUID(), defaultName, pos, defaultRadius);
        registerSettlement(newSettlement);
        return newSettlement;
    }

    public Settlement getOrCreateSettlementNear(BlockPos pos, String defaultName) {
        return getOrCreateSettlementNear(pos, DiplomacyBalance.DEFAULT_SETTLEMENT_RADIUS, defaultName);
    }

    public void discoverSettlement(UUID observerId, UUID targetId) {
        Settlement observer = requireSettlement(observerId);
        requireDistinctSettlement(targetId, observerId);
        requireSettlement(targetId);
        observer.discoverSettlement(targetId);
    }

    public void adjustReputationBetween(UUID firstId, UUID secondId, int delta) {
        Settlement first = requireSettlement(firstId);
        Settlement second = requireOtherSettlement(secondId, firstId);
        first.adjustReputationWith(secondId, delta);
        second.adjustReputationWith(firstId, delta);
    }

    public void setTradeAgreementStatusBetween(UUID firstId, UUID secondId,
                                               TradeAgreementStatus statusFromFirstPerspective) {
        TradeAgreementStatus safeStatus = Objects.requireNonNull(statusFromFirstPerspective, "statusFromFirstPerspective");
        Settlement first = requireSettlement(firstId);
        Settlement second = requireOtherSettlement(secondId, firstId);
        first.getOrCreateRelationship(secondId).setTradeAgreementStatus(safeStatus);
        second.getOrCreateRelationship(firstId).setTradeAgreementStatus(safeStatus.oppositePerspective());
    }

    public void setAllianceStatusBetween(UUID firstId, UUID secondId,
                                         AllianceStatus statusFromFirstPerspective) {
        AllianceStatus safeStatus = Objects.requireNonNull(statusFromFirstPerspective, "statusFromFirstPerspective");
        Settlement first = requireSettlement(firstId);
        Settlement second = requireOtherSettlement(secondId, firstId);

        if (safeStatus == AllianceStatus.ACTIVE
                && (first.getOrCreateRelationship(secondId).getTradeAgreementStatus() != TradeAgreementStatus.ACTIVE
                || second.getOrCreateRelationship(firstId).getTradeAgreementStatus() != TradeAgreementStatus.ACTIVE)) {
            throw new IllegalStateException("An active alliance requires an active trade agreement");
        }

        first.getOrCreateRelationship(secondId).setAllianceStatus(safeStatus);
        second.getOrCreateRelationship(firstId).setAllianceStatus(safeStatus.oppositePerspective());
    }

    public void recordSuccessfulDeliveryBetween(UUID firstId, UUID secondId, long gameTime) {
        Settlement first = requireSettlement(firstId);
        Settlement second = requireOtherSettlement(secondId, firstId);
        first.getOrCreateRelationship(secondId).recordSuccessfulDelivery(gameTime);
        second.getOrCreateRelationship(firstId).recordSuccessfulDelivery(gameTime);
    }

    public void recordFailedContractBetween(UUID firstId, UUID secondId, long gameTime) {
        Settlement first = requireSettlement(firstId);
        Settlement second = requireOtherSettlement(secondId, firstId);
        first.getOrCreateRelationship(secondId).recordFailedContract(gameTime);
        second.getOrCreateRelationship(firstId).recordFailedContract(gameTime);
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag) {
        compoundTag.putInt("DataVersion", CURRENT_DATA_VERSION);

        ListTag list = new ListTag();
        settlements.values().stream()
                .sorted((left, right) -> left.getId().toString().compareTo(right.getId().toString()))
                .forEach(settlement -> list.add(settlement.saveNBT()));
        compoundTag.put("Settlements", list);

        ListTag contractList = new ListTag();
        tradeContracts.values().stream()
                .sorted((left, right) -> left.getId().toString().compareTo(right.getId().toString()))
                .forEach(contract -> contractList.add(contract.saveNBT()));
        compoundTag.put("TradeContracts", contractList);
        return compoundTag;
    }

    public static SettlementSavedData load(CompoundTag compoundTag) {
        SettlementSavedData data = new SettlementSavedData();
        // A missing DataVersion is the legacy version 0 format. New fields are
        // deliberately optional, so migration happens while each settlement loads.
        int storedDataVersion = compoundTag.contains("DataVersion", Tag.TAG_INT)
                ? compoundTag.getInt("DataVersion")
                : 0;
        if (storedDataVersion > CURRENT_DATA_VERSION) {
            VillageDiplomacyMod.LOGGER.warn(
                    "Settlement data version {} is newer than supported version {}; unknown fields may be ignored",
                    storedDataVersion,
                    CURRENT_DATA_VERSION
            );
        }

        if (compoundTag.contains("Settlements", Tag.TAG_LIST)) {
            ListTag list = compoundTag.getList("Settlements", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag settlementTag = list.getCompound(i);
                if (!settlementTag.hasUUID("Id")) {
                    continue;
                }
                Settlement settlement = Settlement.loadNBT(settlementTag);
                data.attachDirtyTracking(settlement);
                data.settlements.put(settlement.getId(), settlement);
            }
        }
        if (compoundTag.contains("TradeContracts", Tag.TAG_LIST)) {
            ListTag list = compoundTag.getList("TradeContracts", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag contractTag = list.getCompound(i);
                if (!contractTag.hasUUID("Id")
                        || !contractTag.hasUUID("SourceSettlement")
                        || !contractTag.hasUUID("DestinationSettlement")) {
                    continue;
                }
                try {
                    TradeContract contract = TradeContract.loadNBT(contractTag);
                    if (data.settlements.containsKey(contract.getSourceSettlementId())
                            && data.settlements.containsKey(contract.getDestinationSettlementId())
                            && !data.tradeContracts.containsKey(contract.getId())) {
                        data.attachDirtyTracking(contract);
                        data.tradeContracts.put(contract.getId(), contract);
                    }
                } catch (IllegalArgumentException exception) {
                    VillageDiplomacyMod.LOGGER.warn("Skipping invalid trade contract data", exception);
                }
            }
        }
        if (storedDataVersion < CURRENT_DATA_VERSION) {
            data.setDirty();
        }
        return data;
    }

    private Settlement requireSettlement(UUID settlementId) {
        Settlement settlement = settlements.get(Objects.requireNonNull(settlementId, "settlementId"));
        if (settlement == null) {
            throw new IllegalArgumentException("Unknown settlement: " + settlementId);
        }
        return settlement;
    }

    private Settlement requireOtherSettlement(UUID settlementId, UUID firstId) {
        requireDistinctSettlement(settlementId, firstId);
        return requireSettlement(settlementId);
    }

    private static void requireDistinctSettlement(UUID settlementId, UUID firstId) {
        if (Objects.requireNonNull(settlementId, "settlementId").equals(firstId)) {
            throw new IllegalArgumentException("A settlement cannot target itself");
        }
    }

    private void attachDirtyTracking(Settlement settlement) {
        settlement.setDirtyCallback(this::setDirty);
    }

    private void attachDirtyTracking(TradeContract contract) {
        contract.setDirtyCallback(this::setDirty);
    }
}
