package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Persisted server-side delivery contract. Physical item transfer is performed
 * by a server interaction layer, while this object owns authoritative progress.
 */
public final class TradeContract {
    private final UUID id;
    private final UUID sourceSettlementId;
    private final UUID destinationSettlementId;
    private final TradeContractType type;
    private final Map<ResourceLocation, Integer> requiredGoods;
    private final Map<ResourceLocation, Integer> issuedGoods = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> deliveredGoods = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> returnGoods;
    private final Map<ResourceLocation, Integer> returnIssuedGoods = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> returnDeliveredGoods = new LinkedHashMap<>();
    private final boolean trial;
    private final long createdGameTime;
    private final long deadlineGameTime;
    private final int rewardEmeralds;
    private TradeContractStatus status = TradeContractStatus.OFFERED;
    @Nullable
    private UUID carrierId;
    private long activatedGameTime;
    private long completedGameTime;
    private boolean rewardClaimed;
    private Runnable dirtyCallback = () -> { };

    public TradeContract(UUID id,
                         UUID sourceSettlementId,
                         UUID destinationSettlementId,
                         Map<ResourceLocation, Integer> requiredGoods,
                         boolean trial,
                         long createdGameTime,
                         long deadlineGameTime,
                         int rewardEmeralds) {
        this(id, sourceSettlementId, destinationSettlementId, TradeContractType.DELIVERY,
                requiredGoods, Map.of(), trial, createdGameTime, deadlineGameTime, rewardEmeralds);
    }

    public TradeContract(UUID id,
                         UUID sourceSettlementId,
                         UUID destinationSettlementId,
                         TradeContractType type,
                         Map<ResourceLocation, Integer> requiredGoods,
                         Map<ResourceLocation, Integer> returnGoods,
                         boolean trial,
                         long createdGameTime,
                         long deadlineGameTime,
                         int rewardEmeralds) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceSettlementId = Objects.requireNonNull(sourceSettlementId, "sourceSettlementId");
        this.destinationSettlementId = Objects.requireNonNull(destinationSettlementId, "destinationSettlementId");
        if (sourceSettlementId.equals(destinationSettlementId)) {
            throw new IllegalArgumentException("Contract settlements must be distinct");
        }
        this.type = Objects.requireNonNull(type, "type");
        this.requiredGoods = normalizeGoods(requiredGoods, false);
        this.returnGoods = normalizeGoods(returnGoods, type == TradeContractType.DELIVERY);
        if (type == TradeContractType.EXCHANGE && this.returnGoods.isEmpty()) {
            throw new IllegalArgumentException("Exchange contracts require return goods");
        }
        if (type == TradeContractType.DELIVERY && !this.returnGoods.isEmpty()) {
            throw new IllegalArgumentException("Delivery contracts cannot contain return goods");
        }
        this.trial = trial;
        this.createdGameTime = Math.max(0L, createdGameTime);
        if (deadlineGameTime <= this.createdGameTime) {
            throw new IllegalArgumentException("Contract deadline must be after creation");
        }
        this.deadlineGameTime = deadlineGameTime;
        if (rewardEmeralds < 0) {
            throw new IllegalArgumentException("Contract reward cannot be negative");
        }
        this.rewardEmeralds = rewardEmeralds;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceSettlementId() {
        return sourceSettlementId;
    }

    public UUID getDestinationSettlementId() {
        return destinationSettlementId;
    }

    public TradeContractType getType() {
        return type;
    }

    public Map<ResourceLocation, Integer> getRequiredGoods() {
        return requiredGoods;
    }

    public Map<ResourceLocation, Integer> getIssuedGoods() {
        return immutableSortedCopy(issuedGoods);
    }

    public Map<ResourceLocation, Integer> getDeliveredGoods() {
        return immutableSortedCopy(deliveredGoods);
    }

    public Map<ResourceLocation, Integer> getReturnGoods() {
        return returnGoods;
    }

    public Map<ResourceLocation, Integer> getReturnIssuedGoods() {
        return immutableSortedCopy(returnIssuedGoods);
    }

    public Map<ResourceLocation, Integer> getReturnDeliveredGoods() {
        return immutableSortedCopy(returnDeliveredGoods);
    }

    public int getIssuedAmount(ResourceLocation itemId) {
        return issuedGoods.getOrDefault(Objects.requireNonNull(itemId, "itemId"), 0);
    }

    public int getDeliveredAmount(ResourceLocation itemId) {
        return deliveredGoods.getOrDefault(Objects.requireNonNull(itemId, "itemId"), 0);
    }

    public int getRemainingToIssue(ResourceLocation itemId) {
        return Math.max(0, getRequiredAmount(itemId) - getIssuedAmount(itemId));
    }

    public int getRemainingToDeliver(ResourceLocation itemId) {
        return Math.max(0, getRequiredAmount(itemId) - getDeliveredAmount(itemId));
    }

    public int getReturnIssuedAmount(ResourceLocation itemId) {
        return returnIssuedGoods.getOrDefault(Objects.requireNonNull(itemId, "itemId"), 0);
    }

    public int getReturnDeliveredAmount(ResourceLocation itemId) {
        return returnDeliveredGoods.getOrDefault(Objects.requireNonNull(itemId, "itemId"), 0);
    }

    public int getRemainingReturnToIssue(ResourceLocation itemId) {
        return Math.max(0, getReturnRequiredAmount(itemId) - getReturnIssuedAmount(itemId));
    }

    public int getRemainingReturnToDeliver(ResourceLocation itemId) {
        return Math.max(0, getReturnRequiredAmount(itemId) - getReturnDeliveredAmount(itemId));
    }

    public boolean isTrial() {
        return trial;
    }

    public long getCreatedGameTime() {
        return createdGameTime;
    }

    public long getDeadlineGameTime() {
        return deadlineGameTime;
    }

    public int getRewardEmeralds() {
        return rewardEmeralds;
    }

    public TradeContractStatus getStatus() {
        return status;
    }

    @Nullable
    public UUID getCarrierId() {
        return carrierId;
    }

    public long getActivatedGameTime() {
        return activatedGameTime;
    }

    public long getCompletedGameTime() {
        return completedGameTime;
    }

    public boolean isRewardClaimed() {
        return rewardClaimed;
    }

    public boolean isOutboundDeliveryComplete() {
        return requiredGoods.entrySet().stream()
                .allMatch(entry -> getDeliveredAmount(entry.getKey()) >= entry.getValue());
    }

    public boolean isReturnDeliveryComplete() {
        return type == TradeContractType.DELIVERY || returnGoods.entrySet().stream()
                .allMatch(entry -> getReturnDeliveredAmount(entry.getKey()) >= entry.getValue());
    }

    public boolean isDeliveryComplete() {
        return isOutboundDeliveryComplete() && isReturnDeliveryComplete();
    }

    public UUID getRewardSettlementId() {
        return type == TradeContractType.EXCHANGE ? sourceSettlementId : destinationSettlementId;
    }

    public boolean involves(UUID settlementId) {
        return sourceSettlementId.equals(settlementId) || destinationSettlementId.equals(settlementId);
    }

    void activate(UUID carrierId, long gameTime) {
        requireStatus(TradeContractStatus.OFFERED);
        this.carrierId = Objects.requireNonNull(carrierId, "carrierId");
        this.activatedGameTime = Math.max(0L, gameTime);
        this.status = TradeContractStatus.ACTIVE;
        markDirty();
    }

    void recordIssued(ResourceLocation itemId, int amount) {
        requireStatus(TradeContractStatus.ACTIVE);
        ResourceLocation safeItemId = requireContractItem(itemId);
        requirePositive(amount, "amount");
        if (amount > getRemainingToIssue(safeItemId)) {
            throw new IllegalArgumentException("Issued amount exceeds the contract remainder");
        }
        issuedGoods.merge(safeItemId, amount, Integer::sum);
        markDirty();
    }

    void recordDelivered(ResourceLocation itemId, int amount) {
        requireStatus(TradeContractStatus.ACTIVE);
        ResourceLocation safeItemId = requireContractItem(itemId);
        requirePositive(amount, "amount");
        if (amount > getRemainingToDeliver(safeItemId)) {
            throw new IllegalArgumentException("Delivered amount exceeds the contract remainder");
        }
        deliveredGoods.merge(safeItemId, amount, Integer::sum);
        markDirty();
    }

    void recordReturnIssued(ResourceLocation itemId, int amount) {
        requireStatus(TradeContractStatus.ACTIVE);
        ResourceLocation safeItemId = requireReturnContractItem(itemId);
        requirePositive(amount, "amount");
        if (!isOutboundDeliveryComplete()) {
            throw new IllegalStateException("Return goods cannot be issued before outbound delivery");
        }
        if (amount > getRemainingReturnToIssue(safeItemId)) {
            throw new IllegalArgumentException("Issued return amount exceeds the contract remainder");
        }
        returnIssuedGoods.merge(safeItemId, amount, Integer::sum);
        markDirty();
    }

    void recordReturnDelivered(ResourceLocation itemId, int amount) {
        requireStatus(TradeContractStatus.ACTIVE);
        ResourceLocation safeItemId = requireReturnContractItem(itemId);
        requirePositive(amount, "amount");
        if (amount > getRemainingReturnToDeliver(safeItemId)) {
            throw new IllegalArgumentException("Delivered return amount exceeds the contract remainder");
        }
        returnDeliveredGoods.merge(safeItemId, amount, Integer::sum);
        markDirty();
    }

    void complete(long gameTime) {
        requireStatus(TradeContractStatus.ACTIVE);
        if (!isDeliveryComplete()) {
            throw new IllegalStateException("Cannot complete a contract before all goods are delivered");
        }
        status = TradeContractStatus.COMPLETED;
        completedGameTime = Math.max(0L, gameTime);
        markDirty();
    }

    void expire(long gameTime) {
        requireStatus(TradeContractStatus.OFFERED);
        status = TradeContractStatus.EXPIRED;
        completedGameTime = Math.max(0L, gameTime);
        markDirty();
    }

    void fail(long gameTime) {
        if (status != TradeContractStatus.ACTIVE) {
            throw new IllegalStateException("Only an active contract can fail");
        }
        status = TradeContractStatus.FAILED;
        completedGameTime = Math.max(0L, gameTime);
        markDirty();
    }

    void cancel(long gameTime) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Contract is already terminal");
        }
        status = TradeContractStatus.CANCELLED;
        completedGameTime = Math.max(0L, gameTime);
        markDirty();
    }

    void claimReward() {
        requireStatus(TradeContractStatus.COMPLETED);
        if (rewardClaimed) {
            throw new IllegalStateException("Contract reward was already claimed");
        }
        rewardClaimed = true;
        markDirty();
    }

    void setDirtyCallback(Runnable dirtyCallback) {
        this.dirtyCallback = Objects.requireNonNull(dirtyCallback, "dirtyCallback");
    }

    CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("SourceSettlement", sourceSettlementId);
        tag.putUUID("DestinationSettlement", destinationSettlementId);
        tag.putString("Type", type.getSerializedName());
        tag.putBoolean("Trial", trial);
        tag.putLong("CreatedGameTime", createdGameTime);
        tag.putLong("DeadlineGameTime", deadlineGameTime);
        tag.putInt("RewardEmeralds", rewardEmeralds);
        tag.putString("Status", status.getSerializedName());
        if (carrierId != null) {
            tag.putUUID("Carrier", carrierId);
        }
        tag.putLong("ActivatedGameTime", activatedGameTime);
        tag.putLong("CompletedGameTime", completedGameTime);
        tag.putBoolean("RewardClaimed", rewardClaimed);

        ListTag goods = new ListTag();
        requiredGoods.forEach((itemId, required) -> {
            CompoundTag goodsTag = new CompoundTag();
            goodsTag.putString("Item", itemId.toString());
            goodsTag.putInt("Required", required);
            goodsTag.putInt("Issued", getIssuedAmount(itemId));
            goodsTag.putInt("Delivered", getDeliveredAmount(itemId));
            goods.add(goodsTag);
        });
        tag.put("Goods", goods);
        tag.put("ReturnGoods", saveGoods(returnGoods, returnIssuedGoods, returnDeliveredGoods));
        return tag;
    }

    static TradeContract loadNBT(CompoundTag tag) {
        Map<ResourceLocation, Integer> required = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> issued = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> delivered = new LinkedHashMap<>();
        loadGoods(tag.getList("Goods", Tag.TAG_COMPOUND), required, issued, delivered);
        Map<ResourceLocation, Integer> returns = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> returnIssued = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> returnDelivered = new LinkedHashMap<>();
        loadGoods(tag.getList("ReturnGoods", Tag.TAG_COMPOUND), returns, returnIssued, returnDelivered);
        TradeContractType type = tag.contains("Type", Tag.TAG_STRING)
                ? TradeContractType.fromSerializedName(tag.getString("Type"))
                : TradeContractType.DELIVERY;
        if (type == TradeContractType.EXCHANGE && returns.isEmpty()) {
            type = TradeContractType.DELIVERY;
        }

        TradeContract contract = new TradeContract(
                tag.getUUID("Id"),
                tag.getUUID("SourceSettlement"),
                tag.getUUID("DestinationSettlement"),
                type,
                required,
                returns,
                tag.getBoolean("Trial"),
                tag.getLong("CreatedGameTime"),
                tag.getLong("DeadlineGameTime"),
                Math.max(0, tag.getInt("RewardEmeralds"))
        );
        contract.status = TradeContractStatus.fromSerializedName(tag.getString("Status"));
        contract.carrierId = tag.hasUUID("Carrier") ? tag.getUUID("Carrier") : null;
        contract.activatedGameTime = Math.max(0L, tag.getLong("ActivatedGameTime"));
        contract.completedGameTime = Math.max(0L, tag.getLong("CompletedGameTime"));
        contract.rewardClaimed = contract.status == TradeContractStatus.COMPLETED
                && tag.getBoolean("RewardClaimed");
        issued.forEach((itemId, amount) -> {
            if (amount > 0) {
                contract.issuedGoods.put(itemId, amount);
            }
        });
        delivered.forEach((itemId, amount) -> {
            if (amount > 0) {
                contract.deliveredGoods.put(itemId, amount);
            }
        });
        returnIssued.forEach((itemId, amount) -> {
            if (amount > 0) {
                contract.returnIssuedGoods.put(itemId, amount);
            }
        });
        returnDelivered.forEach((itemId, amount) -> {
            if (amount > 0) {
                contract.returnDeliveredGoods.put(itemId, amount);
            }
        });
        if ((contract.status == TradeContractStatus.ACTIVE
                || contract.status == TradeContractStatus.COMPLETED)
                && contract.carrierId == null) {
            throw new IllegalArgumentException("Active or completed contract is missing its carrier");
        }
        if (contract.status == TradeContractStatus.COMPLETED && !contract.isDeliveryComplete()) {
            throw new IllegalArgumentException("Completed contract has incomplete delivery progress");
        }
        if (contract.status == TradeContractStatus.OFFERED) {
            contract.carrierId = null;
            contract.activatedGameTime = 0L;
        }
        return contract;
    }

    private int getRequiredAmount(ResourceLocation itemId) {
        return requiredGoods.getOrDefault(itemId, 0);
    }

    private int getReturnRequiredAmount(ResourceLocation itemId) {
        return returnGoods.getOrDefault(itemId, 0);
    }

    private ResourceLocation requireContractItem(ResourceLocation itemId) {
        ResourceLocation safeItemId = Objects.requireNonNull(itemId, "itemId");
        if (!requiredGoods.containsKey(safeItemId)) {
            throw new IllegalArgumentException("Item is not part of this contract: " + safeItemId);
        }
        return safeItemId;
    }

    private ResourceLocation requireReturnContractItem(ResourceLocation itemId) {
        ResourceLocation safeItemId = Objects.requireNonNull(itemId, "itemId");
        if (!returnGoods.containsKey(safeItemId)) {
            throw new IllegalArgumentException("Item is not part of the return leg: " + safeItemId);
        }
        return safeItemId;
    }

    private void requireStatus(TradeContractStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Contract must be " + expected + " but is " + status);
        }
    }

    private static Map<ResourceLocation, Integer> normalizeGoods(Map<ResourceLocation, Integer> goods,
                                                                 boolean allowEmpty) {
        Objects.requireNonNull(goods, "requiredGoods");
        if (!allowEmpty && goods.isEmpty()) {
            throw new IllegalArgumentException("Contract must contain at least one resource");
        }
        Map<ResourceLocation, Integer> normalized = new TreeMap<>((left, right) ->
                left.toString().compareTo(right.toString()));
        goods.forEach((itemId, amount) -> {
            ResourceLocation safeItemId = Objects.requireNonNull(itemId, "itemId");
            if (amount == null || amount <= 0) {
                throw new IllegalArgumentException("Contract resource amounts must be positive");
            }
            normalized.put(safeItemId, amount);
        });
        return Collections.unmodifiableMap(normalized);
    }

    private static ListTag saveGoods(Map<ResourceLocation, Integer> required,
                                     Map<ResourceLocation, Integer> issued,
                                     Map<ResourceLocation, Integer> delivered) {
        ListTag goods = new ListTag();
        required.forEach((itemId, requiredAmount) -> {
            CompoundTag goodsTag = new CompoundTag();
            goodsTag.putString("Item", itemId.toString());
            goodsTag.putInt("Required", requiredAmount);
            goodsTag.putInt("Issued", issued.getOrDefault(itemId, 0));
            goodsTag.putInt("Delivered", delivered.getOrDefault(itemId, 0));
            goods.add(goodsTag);
        });
        return goods;
    }

    private static void loadGoods(ListTag goods,
                                  Map<ResourceLocation, Integer> required,
                                  Map<ResourceLocation, Integer> issued,
                                  Map<ResourceLocation, Integer> delivered) {
        for (int i = 0; i < goods.size(); i++) {
            CompoundTag goodsTag = goods.getCompound(i);
            ResourceLocation itemId = ResourceLocation.tryParse(goodsTag.getString("Item"));
            int requiredAmount = goodsTag.getInt("Required");
            if (itemId == null || requiredAmount <= 0 || required.containsKey(itemId)) {
                continue;
            }
            required.put(itemId, requiredAmount);
            issued.put(itemId, Math.max(0, Math.min(goodsTag.getInt("Issued"), requiredAmount)));
            delivered.put(itemId, Math.max(0, Math.min(goodsTag.getInt("Delivered"), requiredAmount)));
        }
    }

    private static Map<ResourceLocation, Integer> immutableSortedCopy(Map<ResourceLocation, Integer> source) {
        Map<ResourceLocation, Integer> sorted = new TreeMap<>((left, right) ->
                left.toString().compareTo(right.toString()));
        sorted.putAll(source);
        return Collections.unmodifiableMap(sorted);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private void markDirty() {
        dirtyCallback.run();
    }
}
