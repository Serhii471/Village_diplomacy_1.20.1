package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.*;

public class Settlement {
    private final UUID id;
    private String name;
    private BlockPos center;
    private int radius;
    @Nullable
    private UUID leaderUuid;

    private SettlementSpecialization specialization = SettlementSpecialization.PLAINS;
    private final VillageWarehouse warehouse = new VillageWarehouse();
    private final Map<UUID, Integer> relations = new HashMap<>();
    private final Map<UUID, TradeContract> contracts = new HashMap<>();
    private final Map<UUID, TradeRoute> routes = new HashMap<>();
    private long lastProductionDay = -1;

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

    public SettlementSpecialization getSpecialization() {
        return specialization;
    }

    public void setSpecialization(SettlementSpecialization specialization) {
        this.specialization = specialization;
    }

    public VillageWarehouse getWarehouse() {
        return warehouse;
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

    public void checkDailyProduction(long dayIndex) {
        if (lastProductionDay < 0) {
            lastProductionDay = dayIndex;
            warehouse.addDailyProduction(specialization);
        } else if (dayIndex > lastProductionDay) {
            long daysPassed = Math.min(5, dayIndex - lastProductionDay);
            for (int i = 0; i < daysPassed; i++) {
                warehouse.addDailyProduction(specialization);
            }
            lastProductionDay = dayIndex;
        }
    }

    public void addContract(TradeContract contract) {
        contracts.put(contract.getId(), contract);
    }

    @Nullable
    public TradeContract getContract(UUID contractId) {
        return contracts.get(contractId);
    }

    public Collection<TradeContract> getAllContracts() {
        return Collections.unmodifiableCollection(contracts.values());
    }

    public void addRoute(TradeRoute route) {
        routes.put(route.getDestinationId(), route);
    }

    @Nullable
    public TradeRoute getRouteTo(UUID destinationId) {
        return routes.get(destinationId);
    }

    public Collection<TradeRoute> getAllRoutes() {
        return Collections.unmodifiableCollection(routes.values());
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
        tag.putString("Specialization", specialization.name());
        tag.putLong("LastProductionDay", lastProductionDay);

        if (leaderUuid != null) {
            tag.putUUID("Leader", leaderUuid);
        }

        tag.put("Warehouse", warehouse.saveNBT());

        ListTag relList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : relations.entrySet()) {
            CompoundTag relTag = new CompoundTag();
            relTag.putUUID("Target", entry.getKey());
            relTag.putInt("Score", entry.getValue());
            relList.add(relTag);
        }
        tag.put("Relations", relList);

        ListTag contractList = new ListTag();
        for (TradeContract c : contracts.values()) {
            contractList.add(c.saveNBT());
        }
        tag.put("Contracts", contractList);

        ListTag routeList = new ListTag();
        for (TradeRoute r : routes.values()) {
            routeList.add(r.saveNBT());
        }
        tag.put("Routes", routeList);

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
        if (tag.contains("Specialization")) {
            settlement.setSpecialization(SettlementSpecialization.valueOf(tag.getString("Specialization")));
        }
        if (tag.contains("LastProductionDay")) {
            settlement.lastProductionDay = tag.getLong("LastProductionDay");
        }
        if (tag.contains("Warehouse")) {
            VillageWarehouse wh = VillageWarehouse.loadNBT(tag.getCompound("Warehouse"));
            for (Map.Entry<net.minecraft.world.item.Item, Integer> e : wh.getExportStock().entrySet()) {
                settlement.warehouse.depositResource(e.getKey(), e.getValue());
            }
        }
        if (tag.contains("Relations", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Relations", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag relTag = list.getCompound(i);
                settlement.setReputationWith(relTag.getUUID("Target"), relTag.getInt("Score"));
            }
        }
        if (tag.contains("Contracts", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Contracts", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                TradeContract c = TradeContract.loadNBT(list.getCompound(i));
                settlement.addContract(c);
            }
        }
        if (tag.contains("Routes", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Routes", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                TradeRoute r = TradeRoute.loadNBT(list.getCompound(i));
                settlement.addRoute(r);
            }
        }

        return settlement;
    }
}
