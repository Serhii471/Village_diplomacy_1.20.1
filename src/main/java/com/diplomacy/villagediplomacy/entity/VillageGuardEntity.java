package com.diplomacy.villagediplomacy.entity;

import com.diplomacy.villagediplomacy.diplomacy.DiplomacyRelation;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSpecialization;
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
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
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
    private static final EntityDataAccessor<String> UNIFORM_SPECIALIZATION =
            SynchedEntityData.defineId(VillageGuardEntity.class, EntityDataSerializers.STRING);

    public VillageGuardEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
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
        this.entityData.define(UNIFORM_SPECIALIZATION, SettlementSpecialization.UNASSIGNED.getSerializedName());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, false));
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

    public SettlementSpecialization getUniformSpecialization() {
        return SettlementSpecialization.fromSerializedName(this.entityData.get(UNIFORM_SPECIALIZATION));
    }

    public void setSettlementId(@Nullable UUID id) {
        this.entityData.set(SETTLEMENT_ID, Optional.ofNullable(id));
        refreshSettlementRestriction();
        clearInvalidTarget();
    }

    @Nullable
    private Settlement getHomeSettlement() {
        if (level() instanceof ServerLevel serverLevel) {
            return getSettlementId().map(SettlementSavedData.get(serverLevel)::getSettlement).orElse(null);
        }
        return null;
    }

    private void refreshSettlementRestriction() {
        if (level().isClientSide()) {
            return;
        }
        Settlement home = getHomeSettlement();
        if (home == null) {
            clearRestriction();
            // A removed settlement must not leave a stale combat affiliation.
            this.entityData.set(SETTLEMENT_ID, Optional.empty());
            this.entityData.set(UNIFORM_SPECIALIZATION, SettlementSpecialization.UNASSIGNED.getSerializedName());
        } else {
            restrictTo(home.getCenter(), home.getRadius());
            // Clients cannot read settlement SavedData. Send the primary type with
            // normal entity metadata, including to players who start tracking later.
            this.entityData.set(UNIFORM_SPECIALIZATION, home.getPrimarySpecialization().getSerializedName());
        }
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide()) {
            if (tickCount % 20 == 0) {
                refreshSettlementRestriction();
            }
            clearInvalidTarget();
        }
        super.aiStep();
    }

    private void clearInvalidTarget() {
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || !canAttack(target))) {
            setTarget(null);
            getNavigation().stop();
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (!super.canAttack(target) || target == this
                || !isWithinRestriction() || !isWithinRestriction(target.blockPosition())) {
            return false;
        }
        if (target instanceof VillageGuardEntity) {
            // Retaliation follows the same diplomacy rules as proactive targeting.
            return isHostileSettlementGuard(target);
        }
        if (target instanceof AbstractVillager || target instanceof IronGolem) {
            return false;
        }
        if (target instanceof Player player && level() instanceof ServerLevel serverLevel) {
            Settlement home = getHomeSettlement();
            if (home != null) {
                if (home.isPlayerLeader(player.getUUID())) {
                    return false;
                }
                Settlement playerHome = SettlementSavedData.get(serverLevel).findSettlementLedBy(player.getUUID());
                if (playerHome != null) {
                    DiplomacyRelation relation = home.getRelationWith(playerHome.getId());
                    if (relation == DiplomacyRelation.ALLY || relation == DiplomacyRelation.FRIENDLY) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return target instanceof LivingEntity livingTarget && canAttack(livingTarget)
                && super.doHurtTarget(target);
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
            if (mine != null && data.getSettlement(otherSettlement.get()) != null) {
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
        setSettlementId(tag.hasUUID("SettlementId") ? tag.getUUID("SettlementId") : null);
    }
}
