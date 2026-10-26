package com.diplomacy.villagediplomacy.entity;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.core.ModEntities;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSpecialization;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(VillageDiplomacyMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardSkinGameTests {
    @GameTest(template = "guard_arena", batch = "guard_skins")
    public static void spawnAndReassignmentUsePrimaryType(GameTestHelper helper) {
        Settlement home = home(helper);
        home.setSpecializations(SettlementSpecialization.FOREST, SettlementSpecialization.MOUNTAIN);
        VillageGuardEntity guard = guard(helper);
        guard.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(guard.blockPosition()),
                MobSpawnType.SPAWN_EGG, null, null);
        helper.assertTrue(guard.getUniformSpecialization() == SettlementSpecialization.FOREST,
                "Spawn must use primary forest type, not secondary mountain type");
        for (SettlementSpecialization type : SettlementSpecialization.values()) {
            home.setSpecializations(type, null);
            guard.setSettlementId(home.getId());
            helper.assertTrue(guard.getUniformSpecialization() == type, "Reassignment must update uniform: " + type);
        }
        guard.setSettlementId(null);
        helper.assertTrue(guard.getUniformSpecialization() == SettlementSpecialization.UNASSIGNED,
                "Unbound guard must use the base uniform");
        helper.succeed();
    }

    @GameTest(template = "guard_arena", batch = "guard_skins")
    public static void reloadDerivesUniformFromSavedHome(GameTestHelper helper) {
        Settlement home = home(helper);
        home.setSpecializations(SettlementSpecialization.MOUNTAIN, null);
        VillageGuardEntity original = guard(helper);
        original.setSettlementId(home.getId());
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        original.discard();
        VillageGuardEntity loaded = ModEntities.VILLAGE_GUARD.get().create(helper.getLevel());
        loaded.load(saved);
        helper.assertTrue(loaded.getUniformSpecialization() == SettlementSpecialization.MOUNTAIN,
                "Reload must immediately derive the uniform from the persisted home");
        loaded.setSettlementId(UUID.randomUUID());
        helper.assertTrue(loaded.getUniformSpecialization() == SettlementSpecialization.UNASSIGNED,
                "Unknown home must fall back to base, including for old entity saves");
        helper.succeed();
    }

    @GameTest(template = "guard_arena", batch = "guard_skins")
    public static void changedOrRemovedHomeRefreshesUniform(GameTestHelper helper) {
        Settlement home = home(helper);
        home.setSpecializations(SettlementSpecialization.PLAINS, null);
        VillageGuardEntity guard = guard(helper);
        guard.setSettlementId(home.getId());
        home.setSpecializations(SettlementSpecialization.FOREST, null);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(guard.getUniformSpecialization() == SettlementSpecialization.FOREST,
                    "Uniform must track a changed home type within one second");
            SettlementSavedData.get(helper.getLevel()).removeSettlement(home.getId());
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(guard.getUniformSpecialization() == SettlementSpecialization.UNASSIGNED,
                    "Removed home must reset the uniform");
            helper.succeed();
        });
    }

    @GameTest(template = "guard_arena", batch = "guard_skins")
    public static void uniformSurvivesTrackingAndUpdatePackets(GameTestHelper helper) {
        Settlement home = home(helper);
        VillageGuardEntity serverGuard = guard(helper);
        VillageGuardEntity receivingGuard = ModEntities.VILLAGE_GUARD.get().create(helper.getLevel());
        home.setSpecializations(SettlementSpecialization.FOREST, null);
        serverGuard.setSettlementId(home.getId());
        // A newly tracking player receives the full non-default metadata snapshot.
        copyPacket(new ClientboundSetEntityDataPacket(serverGuard.getId(),
                serverGuard.getEntityData().getNonDefaultValues()), receivingGuard);
        helper.assertTrue(receivingGuard.getUniformSpecialization() == SettlementSpecialization.FOREST,
                "Initial entity tracking metadata must contain the forest uniform");
        serverGuard.getEntityData().packDirty();
        home.setSpecializations(SettlementSpecialization.MOUNTAIN, null);
        serverGuard.setSettlementId(home.getId());
        // An already tracking player receives only dirty metadata.
        copyPacket(new ClientboundSetEntityDataPacket(serverGuard.getId(),
                serverGuard.getEntityData().packDirty()), receivingGuard);
        helper.assertTrue(receivingGuard.getUniformSpecialization() == SettlementSpecialization.MOUNTAIN,
                "Delta metadata must update the receiving entity's uniform");
        helper.succeed();
    }

    private static void copyPacket(ClientboundSetEntityDataPacket packet, VillageGuardEntity receiver) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.write(buffer);
            receiver.getEntityData().assignValues(new ClientboundSetEntityDataPacket(buffer).packedItems());
        } finally {
            buffer.release();
        }
    }

    private static Settlement home(GameTestHelper helper) {
        Settlement home = new Settlement(UUID.randomUUID(), "Skin test home",
                helper.absolutePos(new BlockPos(3, 2, 3)), 7);
        SettlementSavedData.get(helper.getLevel()).registerSettlement(home);
        return home;
    }

    private static VillageGuardEntity guard(GameTestHelper helper) {
        return helper.spawn(ModEntities.VILLAGE_GUARD.get(), 3, 2, 3);
    }
}
