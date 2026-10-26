package com.diplomacy.villagediplomacy.garrison;

import com.diplomacy.villagediplomacy.core.ModEntities;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

import javax.annotation.Nullable;
import java.util.UUID;

public final class GarrisonService {
    public static final long TRAINING_TICKS = 24_000L;
    public static final int BREAD_COST = 6;
    public static final int MIN_CIVILIANS = 4;
    private GarrisonService() { }

    public record Population(int civilians, int trainees, int soldiers) {
        public int adults() { return civilians + trainees + soldiers; }
        public int limit() { return adults() / 4; }
        public boolean canRecruit() { return civilians - 1 >= MIN_CIVILIANS && trainees + soldiers + 1 <= limit(); }
    }
    public static Population population(ServerLevel level, Settlement home) {
        GarrisonSavedData roster = GarrisonSavedData.get(level);
        return new Population(roster.count(home.getId(), GarrisonSavedData.Role.CIVILIAN),
                roster.count(home.getId(), GarrisonSavedData.Role.TRAINING),
                roster.count(home.getId(), GarrisonSavedData.Role.SOLDIER));
    }
    public static boolean eligible(Villager villager) {
        return villager.isAlive() && !villager.isBaby() && !ResidentData.isTraining(villager)
                && villager.getVillagerData().getProfession() == VillagerProfession.NONE
                && villager.getTradingPlayer() == null && !villager.isPassenger() && !villager.isSleeping();
    }
    public static boolean sword(ItemStack stack) { return stack.getItem() instanceof SwordItem; }
    public static boolean chestplate(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.CHEST;
    }
    public static void discover(ServerLevel level) {
        for (var entity : level.getAllEntities()) {
            if (entity instanceof Villager villager) register(level, villager);
            else if (entity instanceof VillageGuardEntity guard) register(level, guard);
        }
    }
    public static void register(ServerLevel level, Villager villager) {
        GarrisonSavedData roster = GarrisonSavedData.get(level);
        if (!villager.isAlive() || villager.isRemoved()) { roster.remove(villager.getUUID()); return; }
        Settlement home = ResidentData.home(level, villager);
        if (home == null || villager.isBaby()) { roster.remove(villager.getUUID()); return; }
        roster.put(new GarrisonSavedData.Person(villager.getUUID(), home.getId(), villager.getUUID(),
                ResidentData.isTraining(villager) ? GarrisonSavedData.Role.TRAINING : GarrisonSavedData.Role.CIVILIAN, true));
    }
    public static void register(ServerLevel level, VillageGuardEntity guard) {
        GarrisonSavedData roster = GarrisonSavedData.get(level);
        UUID identity = guard.getResidentIdentity().orElse(guard.getUUID());
        if (!guard.isAlive() || guard.isRemoved()) { roster.remove(identity); return; }
        UUID home = guard.getSettlementId().orElse(null);
        if (home == null) { roster.remove(identity); return; }
        roster.put(new GarrisonSavedData.Person(identity, home, guard.getUUID(), GarrisonSavedData.Role.SOLDIER,
                guard.getResidentIdentity().isPresent()));
    }

    /** Result keys are shown in the garrison screen; all checks are server-side. */
    public static String recruit(ServerPlayer leader, Settlement home, Villager villager, Container supplies) {
        ServerLevel level = leader.serverLevel();
        if (!home.isPlayerLeader(leader.getUUID())) return "not_leader";
        discover(level);
        Settlement actualHome = ResidentData.home(level, villager);
        if (actualHome == null || !actualHome.getId().equals(home.getId())
                || SettlementSavedData.get(level).findSettlementAt(villager.blockPosition()) != home) return "wrong_home";
        if (!eligible(villager)) return "ineligible";
        if (!population(level, home).canRecruit()) return "population_limit";
        ItemStack weapon = supplies.getItem(0), armor = supplies.getItem(1), food = supplies.getItem(2);
        if (!sword(weapon) || !chestplate(armor) || !food.is(Items.BREAD) || food.getCount() < BREAD_COST) return "supplies";
        if (!villager.hasCustomName()) villager.setCustomName(ResidentData.name(villager));
        CompoundTag original = villager.saveWithoutId(new CompoundTag());
        CompoundTag training = new CompoundTag();
        training.putLong("Finish", level.getGameTime() + TRAINING_TICKS);
        training.putUUID("Home", home.getId());
        training.putBoolean("OriginalNoAI", villager.isNoAi());
        training.put("Original", original);
        training.put("Weapon", weapon.copyWithCount(1).save(new CompoundTag()));
        training.put("Armor", armor.copyWithCount(1).save(new CompoundTag()));
        training.put("Food", food.copyWithCount(BREAD_COST).save(new CompoundTag()));
        ResidentData.data(villager).put(ResidentData.TRAINING, training);
        weapon.shrink(1); armor.shrink(1); food.shrink(BREAD_COST); supplies.setChanged();
        villager.getNavigation().stop();
        villager.setNoAi(true);
        register(level, villager);
        return "started";
    }

    public static String cancel(ServerPlayer leader, Settlement home, Villager villager) {
        if (!home.isPlayerLeader(leader.getUUID())) return "not_leader";
        Settlement actualHome = ResidentData.home(leader.serverLevel(), villager);
        if (actualHome == null || !actualHome.getId().equals(home.getId())) return "wrong_home";
        if (!ResidentData.isTraining(villager)) return "ineligible";
        cancelTraining(leader.serverLevel(), villager, leader);
        return "cancelled";
    }
    public static void cancelTraining(ServerLevel level, Villager villager, @Nullable ServerPlayer recipient) {
        CompoundTag training = ResidentData.data(villager).getCompound(ResidentData.TRAINING);
        if (training.isEmpty()) return;
        give(villager, recipient, ItemStack.of(training.getCompound("Weapon")));
        give(villager, recipient, ItemStack.of(training.getCompound("Armor")));
        give(villager, recipient, ItemStack.of(training.getCompound("Food")));
        villager.setNoAi(training.getBoolean("OriginalNoAI"));
        ResidentData.data(villager).remove(ResidentData.TRAINING);
        register(level, villager);
    }
    private static void give(Villager source, @Nullable ServerPlayer recipient, ItemStack item) {
        if (item.isEmpty()) return;
        if (recipient != null) recipient.getInventory().placeItemBackInInventory(item);
        else source.spawnAtLocation(item);
    }

    public static boolean finishTraining(ServerLevel level, Villager villager) {
        if (!villager.isAlive() || !ResidentData.isTraining(villager)) return false;
        CompoundTag training = ResidentData.data(villager).getCompound(ResidentData.TRAINING);
        Settlement home = training.hasUUID("Home") ? SettlementSavedData.get(level).getSettlement(training.getUUID("Home")) : null;
        if (home == null) { cancelTraining(level, villager, null); return false; }
        if (ResidentData.remaining(villager) > 0) return false;
        CompoundTag original = training.getCompound("Original").copy();
        // Old bed/workstation memories must not claim POIs while this person serves.
        original.remove("Brain");
        VillageGuardEntity guard = ModEntities.VILLAGE_GUARD.get().create(level);
        if (guard == null) return false;
        guard.moveTo(villager.position().x, villager.position().y, villager.position().z, villager.getYRot(), villager.getXRot());
        guard.setResidentSnapshot(original);
        guard.setSettlementId(home.getId());
        guard.setCustomName(villager.getCustomName());
        guard.setCustomNameVisible(villager.isCustomNameVisible());
        guard.setHealth(guard.getMaxHealth() * villager.getHealth() / villager.getMaxHealth());
        guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.of(training.getCompound("Weapon")));
        guard.setItemSlot(EquipmentSlot.CHEST, ItemStack.of(training.getCompound("Armor")));
        guard.setDropChance(EquipmentSlot.MAINHAND, .05f);
        guard.setDropChance(EquipmentSlot.CHEST, .05f);
        if (!level.addFreshEntity(guard)) return false;
        for (var poi : Villager.POI_MEMORIES.keySet()) villager.releasePoi(poi);
        ResidentData.data(villager).remove(ResidentData.TRAINING);
        villager.discard();
        register(level, guard);
        return true;
    }

    public static String demobilize(ServerPlayer leader, Settlement home, VillageGuardEntity guard) {
        ServerLevel level = leader.serverLevel();
        if (!home.isPlayerLeader(leader.getUUID())) return "not_leader";
        if (guard.getSettlementId().isEmpty() || !guard.getSettlementId().get().equals(home.getId())) return "wrong_home";
        CompoundTag original = guard.getResidentSnapshot();
        if (!guard.isAlive() || !original.hasUUID("UUID") || !guard.getResidentIdentity().isPresent()) return "not_recruited";
        if (level.getEntity(original.getUUID("UUID")) != null) return "identity_conflict";
        if (guard.getTarget() != null || guard.isPassenger() || guard.getHealth() <= 0) return "in_combat";
        Villager restored = EntityType.VILLAGER.create(level);
        if (restored == null) return "unavailable";
        restored.load(original);
        restored.moveTo(guard.getX(), guard.getY(), guard.getZ(), guard.getYRot(), guard.getXRot());
        restored.setCustomName(guard.getCustomName());
        restored.setHealth(restored.getMaxHealth() * guard.getHealth() / guard.getMaxHealth());
        if (!level.addFreshEntity(restored)) return "unavailable";
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipment = guard.getItemBySlot(slot);
            if (!equipment.isEmpty()) leader.getInventory().placeItemBackInInventory(equipment.copy());
            guard.setItemSlot(slot, ItemStack.EMPTY);
        }
        guard.discard();
        register(level, restored);
        return "demobilized";
    }
}
