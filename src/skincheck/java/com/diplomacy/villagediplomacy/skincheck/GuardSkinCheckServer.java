package com.diplomacy.villagediplomacy.skincheck;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.core.ModEntities;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSpecialization;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Opt-in local test world only; excluded from normal compilation and release JAR. */
@Mod.EventBusSubscriber(modid = VillageDiplomacyMod.MOD_ID)
public final class GuardSkinCheckServer {
    static final String[] TYPES = {"base", "plains", "mountain", "forest"};
    static final String[] VIEWERS = {"SkinViewerOne", "SkinViewerTwo"};
    static final Path ROOT = Path.of(System.getProperty("villagediplomacy.skinCheckRoot"));
    private static int stage;
    private static long started;

    @SubscribeEvent
    public static void started(ServerStartedEvent event) throws Exception {
        started = System.currentTimeMillis();
        ServerLevel level = event.getServer().overworld();
        level.setDefaultSpawnPos(new BlockPos(0, 80, 8), 180);
        event.getServer().setDifficulty(Difficulty.PEACEFUL, true);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.setDayTime(6000);
        // Keep the tiny test arena loaded during save/reload and network checks.
        for (int x = -1; x <= 0; x++) for (int z = -1; z <= 0; z++) {
            level.setChunkForced(x, z, true);
            level.getChunk(x, z);
        }
        if (Files.exists(ROOT.resolve("reload.marker"))) {
            stage = 3;
        } else {
            for (int x = -12; x <= 12; x++) for (int z = -5; z <= 12; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 80; y < 84; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            for (int type = 0; type < TYPES.length; type++) {
                Settlement home = null;
                if (type != 0) {
                    home = new Settlement(UUID.randomUUID(), "Skin check " + TYPES[type],
                            new BlockPos(-6 + 4 * type, 80, 0), 3);
                    home.setSpecializations(SettlementSpecialization.fromSerializedName(TYPES[type]), null);
                    SettlementSavedData.get(level).registerSettlement(home);
                }
                for (int armored = 0; armored < 2; armored++) {
                    VillageGuardEntity guard = ModEntities.VILLAGE_GUARD.get().create(level);
                    guard.moveTo(-6 + 4 * type, 80, armored * 3, 180, 0);
                    guard.finalizeSpawn(level, level.getCurrentDifficultyAt(guard.blockPosition()), MobSpawnType.COMMAND, null, null);
                    guard.setSettlementId(home == null ? null : home.getId());
                    guard.setNoAi(true);
                    guard.setCustomName(Component.literal("skincheck:" + TYPES[type] + ":" + armored));
                    if (armored == 0) guard.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                    // Armored row retains the actual spawn equipment and its protection.
                    level.addFreshEntity(guard);
                }
            }
            stage = 0;
        }
        Files.writeString(ROOT.resolve("server-ready.txt"), Integer.toString(stage));
        VillageDiplomacyMod.LOGGER.info("GUARD_SKIN_SERVER_READY stage={}", stage);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(player.server.overworld(), 0, 81, 8, 180, 0);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || started == 0) return;
        if (System.currentTimeMillis() - started > 360_000) {
            Files.writeString(ROOT.resolve("server-failure.txt"), "Timed out at stage " + stage);
            server.halt(false);
            return;
        }
        ServerLevel level = server.overworld();
        if (stage == 0 && all("initial")) {
            home(level, "plains").setSpecializations(SettlementSpecialization.FOREST, null);
            stage = 1;
        } else if (stage == 1 && all("changed")) {
            SettlementSavedData.get(level).removeSettlement(home(level, "forest").getId());
            stage = 2;
        } else if (stage == 2 && all("removed")) {
            Files.writeString(ROOT.resolve("reload.marker"), "Reopen the same disposable server world");
            server.saveEverything(true, true, true);
            VillageDiplomacyMod.LOGGER.info("GUARD_SKIN_PHASE_ONE_PASSED: both clients saw all uniforms and live updates");
            server.halt(false);
            stage = 4;
        } else if (stage == 3 && all("reloaded")) {
            Files.writeString(ROOT.resolve("server-passed.txt"), "Both clients verified persisted uniforms after full server restart");
            VillageDiplomacyMod.LOGGER.info("GUARD_SKIN_CLIENT_CHECKS_PASSED");
            server.halt(false);
            stage = 4;
        }
    }

    private static boolean all(String step) {
        for (String viewer : VIEWERS) if (!Files.exists(ROOT.resolve(viewer + "-" + step + ".txt"))) return false;
        return true;
    }

    private static Settlement home(ServerLevel level, String type) {
        for (var entity : level.getAllEntities()) {
            if (entity instanceof VillageGuardEntity guard && guard.hasCustomName()
                    && guard.getCustomName().getString().equals("skincheck:" + type + ":0")) {
                return SettlementSavedData.get(level).getSettlement(guard.getSettlementId().orElseThrow());
            }
        }
        throw new IllegalStateException("Missing test home: " + type);
    }
}
