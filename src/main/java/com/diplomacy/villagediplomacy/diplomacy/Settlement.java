package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class Settlement {
    private static final ResourceLocation DEFAULT_LOCAL_WOOD = Objects.requireNonNull(
            ResourceLocation.tryParse("minecraft:oak_log")
    );

    private final UUID id;
    private String name;
    private BlockPos center;
    private int radius;
    @Nullable
    private UUID leaderUuid;

    private SettlementSpecialization primarySpecialization = SettlementSpecialization.UNASSIGNED;
    @Nullable
    private SettlementSpecialization secondarySpecialization;
    private ResourceLocation localWoodResource = DEFAULT_LOCAL_WOOD;
    private long lastProductionGameTime;
    private VirtualWarehouse warehouse = new VirtualWarehouse();
    private final Map<UUID, SettlementRelationship> relationships = new LinkedHashMap<>();
    private Runnable dirtyCallback = () -> { };

    public Settlement(UUID id, String name, BlockPos center, int radius) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = requireName(name);
        this.center = Objects.requireNonNull(center, "center");
        this.radius = requirePositiveRadius(radius);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        String safeName = requireName(name);
        if (!this.name.equals(safeName)) {
            this.name = safeName;
            markDirty();
        }
    }

    public BlockPos getCenter() {
        return center;
    }

    public void setCenter(BlockPos center) {
        BlockPos safeCenter = Objects.requireNonNull(center, "center");
        if (!this.center.equals(safeCenter)) {
            this.center = safeCenter;
            markDirty();
        }
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        int safeRadius = requirePositiveRadius(radius);
        if (this.radius != safeRadius) {
            this.radius = safeRadius;
            markDirty();
        }
    }

    @Nullable
    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(@Nullable UUID leaderUuid) {
        if (!Objects.equals(this.leaderUuid, leaderUuid)) {
            this.leaderUuid = leaderUuid;
            markDirty();
        }
    }

    public boolean isPlayerLeader(UUID playerUuid) {
        return leaderUuid != null && leaderUuid.equals(playerUuid);
    }

    public SettlementSpecialization getPrimarySpecialization() {
        return primarySpecialization;
    }

    public Optional<SettlementSpecialization> getSecondarySpecialization() {
        return Optional.ofNullable(secondarySpecialization);
    }

    public void setSpecializations(SettlementSpecialization primary,
                                   @Nullable SettlementSpecialization secondary) {
        SettlementSpecialization safePrimary = Objects.requireNonNull(primary, "primary");
        if (secondary == SettlementSpecialization.UNASSIGNED) {
            secondary = null;
        }
        if (secondary != null && (safePrimary == SettlementSpecialization.UNASSIGNED || secondary == safePrimary)) {
            throw new IllegalArgumentException("Secondary specialization must differ from an assigned primary specialization");
        }

        if (primarySpecialization != safePrimary || secondarySpecialization != secondary) {
            primarySpecialization = safePrimary;
            secondarySpecialization = secondary;
            markDirty();
        }
    }

    public ResourceLocation getLocalWoodResource() {
        return localWoodResource;
    }

    public void setLocalWoodResource(ResourceLocation localWoodResource) {
        ResourceLocation safeResource = Objects.requireNonNull(localWoodResource, "localWoodResource");
        if (!this.localWoodResource.equals(safeResource)) {
            this.localWoodResource = safeResource;
            markDirty();
        }
    }

    public long getLastProductionGameTime() {
        return lastProductionGameTime;
    }

    public void setLastProductionGameTime(long gameTime) {
        long safeGameTime = Math.max(0L, gameTime);
        if (lastProductionGameTime != safeGameTime) {
            lastProductionGameTime = safeGameTime;
            markDirty();
        }
    }

    public VirtualWarehouse getWarehouse() {
        return warehouse;
    }

    public Collection<SettlementRelationship> getRelationships() {
        return Collections.unmodifiableCollection(relationships.values());
    }

    public Set<UUID> getKnownSettlementIds() {
        return relationships.values().stream()
                .filter(SettlementRelationship::isKnown)
                .map(SettlementRelationship::getTargetSettlementId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<SettlementRelationship> findRelationship(UUID otherSettlementId) {
        return Optional.ofNullable(relationships.get(otherSettlementId));
    }

    public SettlementRelationship getOrCreateRelationship(UUID otherSettlementId) {
        UUID safeId = validateOtherSettlementId(otherSettlementId);
        SettlementRelationship existing = relationships.get(safeId);
        if (existing != null) {
            return existing;
        }

        SettlementRelationship relationship = new SettlementRelationship(safeId);
        relationship.setDirtyCallback(this::markDirty);
        relationships.put(safeId, relationship);
        markDirty();
        return relationship;
    }

    public void discoverSettlement(UUID otherSettlementId) {
        getOrCreateRelationship(otherSettlementId).setKnown(true);
    }

    public int getReputationWith(UUID otherSettlementId) {
        SettlementRelationship relationship = relationships.get(otherSettlementId);
        return relationship == null ? 0 : relationship.getReputation();
    }

    public DiplomacyRelation getRelationWith(UUID otherSettlementId) {
        return DiplomacyRelation.fromScore(getReputationWith(otherSettlementId));
    }

    public void setReputationWith(UUID otherSettlementId, int reputation) {
        getOrCreateRelationship(otherSettlementId).setReputation(reputation);
    }

    public void adjustReputationWith(UUID otherSettlementId, int delta) {
        getOrCreateRelationship(otherSettlementId).adjustReputation(delta);
    }

    public boolean contains(BlockPos pos) {
        return center.distSqr(pos) <= (long) radius * radius;
    }

    void removeRelationship(UUID otherSettlementId) {
        if (relationships.remove(otherSettlementId) != null) {
            markDirty();
        }
    }

    void setDirtyCallback(Runnable dirtyCallback) {
        this.dirtyCallback = Objects.requireNonNull(dirtyCallback, "dirtyCallback");
        warehouse.setDirtyCallback(this::markDirty);
        relationships.values().forEach(relationship -> relationship.setDirtyCallback(this::markDirty));
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

        tag.putString("PrimarySpecialization", primarySpecialization.getSerializedName());
        if (secondarySpecialization != null) {
            tag.putString("SecondarySpecialization", secondarySpecialization.getSerializedName());
        }
        tag.putString("LocalWoodResource", localWoodResource.toString());
        tag.putLong("LastProductionGameTime", lastProductionGameTime);
        tag.put("Warehouse", warehouse.saveNBT());

        ListTag relationshipList = new ListTag();
        relationships.values().stream()
                .sorted((left, right) -> left.getTargetSettlementId().toString()
                        .compareTo(right.getTargetSettlementId().toString()))
                .forEach(relationship -> relationshipList.add(relationship.saveNBT()));
        tag.put("Relationships", relationshipList);

        return tag;
    }

    public static Settlement loadNBT(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        String name = tag.getString("Name");
        BlockPos center = BlockPos.of(tag.getLong("Center"));
        int radius = Math.max(1, tag.getInt("Radius"));

        Settlement settlement = new Settlement(id, name.isBlank() ? "Unnamed Settlement" : name, center, radius);

        if (tag.hasUUID("Leader")) {
            settlement.leaderUuid = tag.getUUID("Leader");
        }

        settlement.primarySpecialization = SettlementSpecialization.fromSerializedName(
                tag.getString("PrimarySpecialization"));
        if (tag.contains("SecondarySpecialization", Tag.TAG_STRING)) {
            SettlementSpecialization secondary = SettlementSpecialization.fromSerializedName(
                    tag.getString("SecondarySpecialization"));
            if (secondary != SettlementSpecialization.UNASSIGNED && secondary != settlement.primarySpecialization) {
                settlement.secondarySpecialization = secondary;
            }
        }
        if (tag.contains("LocalWoodResource", Tag.TAG_STRING)) {
            ResourceLocation localWood = ResourceLocation.tryParse(tag.getString("LocalWoodResource"));
            if (localWood != null) {
                settlement.localWoodResource = localWood;
            }
        }
        settlement.lastProductionGameTime = Math.max(0L, tag.getLong("LastProductionGameTime"));

        if (tag.contains("Warehouse", Tag.TAG_COMPOUND)) {
            settlement.warehouse = VirtualWarehouse.loadNBT(tag.getCompound("Warehouse"));
        }

        if (tag.contains("Relationships", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Relationships", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag relationshipTag = list.getCompound(i);
                if (!relationshipTag.hasUUID("Target")) {
                    continue;
                }
                SettlementRelationship relationship = SettlementRelationship.loadNBT(relationshipTag);
                if (!relationship.getTargetSettlementId().equals(id)) {
                    settlement.relationships.put(relationship.getTargetSettlementId(), relationship);
                }
            }
        } else if (tag.contains("Relations", Tag.TAG_LIST)) {
            // Data version 0 stored only a reputation score for each target.
            ListTag list = tag.getList("Relations", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag legacyTag = list.getCompound(i);
                if (!legacyTag.hasUUID("Target")) {
                    continue;
                }
                UUID targetId = legacyTag.getUUID("Target");
                if (targetId.equals(id)) {
                    continue;
                }
                SettlementRelationship relationship = new SettlementRelationship(targetId);
                relationship.setKnown(true);
                relationship.setReputation(legacyTag.getInt("Score"));
                settlement.relationships.put(targetId, relationship);
            }
        }

        return settlement;
    }

    private UUID validateOtherSettlementId(UUID otherSettlementId) {
        UUID safeId = Objects.requireNonNull(otherSettlementId, "otherSettlementId");
        if (id.equals(safeId)) {
            throw new IllegalArgumentException("A settlement cannot have a relationship with itself");
        }
        return safeId;
    }

    private static String requireName(String name) {
        String safeName = Objects.requireNonNull(name, "name").trim();
        if (safeName.isEmpty()) {
            throw new IllegalArgumentException("Settlement name cannot be blank");
        }
        return safeName;
    }

    private static int requirePositiveRadius(int radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("Settlement radius must be positive");
        }
        return radius;
    }

    private void markDirty() {
        dirtyCallback.run();
    }
}
