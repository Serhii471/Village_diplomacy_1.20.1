package com.diplomacy.villagediplomacy.entity;

import com.diplomacy.villagediplomacy.diplomacy.DiplomacyRelation;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class VillageGuardEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> SETTLEMENT_ID =
            SynchedEntityData.defineId(VillageGuardEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public VillageGuardEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 4.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SETTLEMENT_ID, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25D, false));
        this.goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                (target) -> target instanceof Enemy));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, VillageGuardEntity.class, 10, true, false,
                this::isHostileSettlementGuard));
    }

    public Optional<UUID> getSettlementId() {
        return this.entityData.get(SETTLEMENT_ID);
    }

    public void setSettlementId(@Nullable UUID id) {
        this.entityData.set(SETTLEMENT_ID, Optional.ofNullable(id));
    }

    private boolean isHostileSettlementGuard(LivingEntity entity) {
        if (!(entity instanceof VillageGuardEntity otherGuard)) return false;
        Optional<UUID> mySettlement = getSettlementId();
        Optional<UUID> otherSettlement = otherGuard.getSettlementId();

        if (mySettlement.isEmpty() || otherSettlement.isEmpty()) return false;
        if (mySettlement.get().equals(otherSettlement.get())) return false;

        if (this.level() instanceof ServerLevel serverLevel) {
            SettlementSavedData data = SettlementSavedData.get(serverLevel);
            Settlement mine = data.getSettlement(mySettlement.get());
            if (mine != null) {
                return mine.getRelationWith(otherSettlement.get()) == DiplomacyRelation.HOSTILE;
            }
        }
        return false;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor levelAccessor, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(levelAccessor, difficulty, spawnType, spawnData, tag);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.05F);
        this.setDropChance(EquipmentSlot.CHEST, 0.05F);

        if (levelAccessor instanceof ServerLevel serverLevel) {
            SettlementSavedData savedData = SettlementSavedData.get(serverLevel);
            Settlement nearby = savedData.findSettlementAt(this.blockPosition());
            if (nearby != null) {
                setSettlementId(nearby.getId());
                this.restrictTo(nearby.getCenter(), nearby.getRadius());
            }
        }

        return data;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getSettlementId().ifPresent(uuid -> tag.putUUID("SettlementId", uuid));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("SettlementId")) {
            setSettlementId(tag.getUUID("SettlementId"));
        }
    }
}
