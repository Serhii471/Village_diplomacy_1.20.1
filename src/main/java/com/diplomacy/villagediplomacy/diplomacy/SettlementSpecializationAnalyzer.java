package com.diplomacy.villagediplomacy.diplomacy;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.config.VillageDiplomacyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SettlementSpecializationAnalyzer {
    private static final int LOG_SEARCH_DEPTH = 12;
    private static final int ALTITUDE_BASELINE = 72;
    private static final ResourceLocation DEFAULT_WOOD = Objects.requireNonNull(
            ResourceLocation.tryParse("minecraft:oak_log")
    );

    private SettlementSpecializationAnalyzer() {
    }

    /**
     * Assigns a specialization once. Existing assignments are never recalculated.
     */
    public static boolean ensureAssigned(ServerLevel level, Settlement settlement) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(settlement, "settlement");
        if (settlement.getPrimarySpecialization() != SettlementSpecialization.UNASSIGNED) {
            return false;
        }

        AnalysisResult result = analyze(level, settlement.getCenter(), currentSettings());
        settlement.setSpecializations(result.primary(), result.secondary());
        settlement.setLocalWoodResource(result.localWoodResource());
        VillageDiplomacyMod.LOGGER.info(
                "Assigned {} specialization{} to settlement {} at {} (scores: {})",
                result.primary().getSerializedName(),
                result.secondary() == null ? "" : "/" + result.secondary().getSerializedName(),
                settlement.getName(),
                settlement.getCenter().toShortString(),
                result.scores()
        );
        return true;
    }

    public static AnalysisResult analyze(ServerLevel level, BlockPos center, AnalysisSettings settings) {
        return classify(survey(level, center, settings), settings);
    }

    static AnalysisResult classify(TerrainSurvey survey, AnalysisSettings settings) {
        if (survey.totalColumns() <= 0) {
            return new AnalysisResult(
                    SettlementSpecialization.PLAINS,
                    null,
                    DEFAULT_WOOD,
                    Map.of(SettlementSpecialization.PLAINS, 1.0D)
            );
        }

        double forestBiomeRatio = ratio(survey.forestBiomeColumns(), survey.totalColumns());
        double mountainBiomeRatio = ratio(survey.mountainBiomeColumns(), survey.totalColumns());
        double plainsBiomeRatio = ratio(survey.plainsBiomeColumns(), survey.totalColumns());
        double canopyRatio = ratio(survey.canopyColumns(), survey.totalColumns());
        double logRatio = ratio(survey.logColumns(), survey.totalColumns());
        double rockyRatio = ratio(survey.rockySurfaceColumns(), survey.totalColumns());
        double openGroundRatio = ratio(survey.openGroundColumns(), survey.totalColumns());

        double reliefScore = clamp01((double) survey.heightRelief() / settings.mountainRelief());
        double flatnessScore = 1.0D - reliefScore;
        double averageHeight = survey.averageHeight();
        double altitudeDivisor = Math.max(1.0D, settings.mountainAltitude() - ALTITUDE_BASELINE);
        double altitudeScore = clamp01((averageHeight - ALTITUDE_BASELINE) / altitudeDivisor);

        Map<SettlementSpecialization, Double> scores = new EnumMap<>(SettlementSpecialization.class);
        scores.put(SettlementSpecialization.PLAINS,
                plainsBiomeRatio * 0.55D + flatnessScore * 0.25D + openGroundRatio * 0.20D);
        scores.put(SettlementSpecialization.MOUNTAIN,
                mountainBiomeRatio * 0.55D + altitudeScore * 0.20D + reliefScore * 0.20D + rockyRatio * 0.05D);
        scores.put(SettlementSpecialization.FOREST,
                forestBiomeRatio * 0.65D + canopyRatio * 0.25D + logRatio * 0.10D);

        Comparator<SettlementSpecialization> byScore = Comparator
                .comparingDouble((SettlementSpecialization specialization) -> scores.getOrDefault(specialization, 0.0D))
                .reversed()
                .thenComparingInt(Enum::ordinal);
        SettlementSpecialization[] ranked = scores.keySet().stream().sorted(byScore)
                .toArray(SettlementSpecialization[]::new);
        SettlementSpecialization primary = ranked[0];
        SettlementSpecialization secondaryCandidate = ranked[1];
        double primaryScore = scores.get(primary);
        double secondaryScore = scores.get(secondaryCandidate);
        SettlementSpecialization secondary = secondaryScore >= settings.minimumSecondaryScore()
                && secondaryScore >= primaryScore * settings.secondaryScoreRatio()
                ? secondaryCandidate
                : null;

        ResourceLocation localWood = survey.woodCounts().entrySet().stream()
                .max(Map.Entry.<ResourceLocation, Integer>comparingByValue()
                        .thenComparing(entry -> entry.getKey().toString(), Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .orElse(DEFAULT_WOOD);

        Map<SettlementSpecialization, Double> orderedScores = new LinkedHashMap<>();
        for (SettlementSpecialization specialization : ranked) {
            orderedScores.put(specialization, scores.get(specialization));
        }
        return new AnalysisResult(primary, secondary, localWood, orderedScores);
    }

    private static TerrainSurvey survey(ServerLevel level, BlockPos center, AnalysisSettings settings) {
        int totalColumns = 0;
        int minimumHeight = Integer.MAX_VALUE;
        int maximumHeight = Integer.MIN_VALUE;
        long heightSum = 0L;
        int plainsBiomeColumns = 0;
        int mountainBiomeColumns = 0;
        int forestBiomeColumns = 0;
        int openGroundColumns = 0;
        int rockySurfaceColumns = 0;
        int canopyColumns = 0;
        int logColumns = 0;
        Map<ResourceLocation, Integer> woodCounts = new HashMap<>();

        int radius = settings.scanRadius();
        int step = settings.sampleStep();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int surfaceY = Math.max(level.getMinBuildHeight(),
                        level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                cursor.set(x, surfaceY, z);

                totalColumns++;
                minimumHeight = Math.min(minimumHeight, surfaceY);
                maximumHeight = Math.max(maximumHeight, surfaceY);
                heightSum += surfaceY;

                String biomePath = level.getBiome(cursor).unwrapKey()
                        .map(key -> key.location().getPath())
                        .orElse("");
                if (isForestBiome(biomePath)) {
                    forestBiomeColumns++;
                } else if (isMountainBiome(biomePath)) {
                    mountainBiomeColumns++;
                } else if (isPlainsBiome(biomePath)) {
                    plainsBiomeColumns++;
                }

                BlockState surface = level.getBlockState(cursor);
                if (surface.is(BlockTags.LEAVES)) {
                    canopyColumns++;
                }
                if (isOpenGround(surface)) {
                    openGroundColumns++;
                }
                if (surface.is(BlockTags.BASE_STONE_OVERWORLD)) {
                    rockySurfaceColumns++;
                }

                ResourceLocation wood = findWoodInColumn(level, cursor, surfaceY);
                if (wood != null) {
                    logColumns++;
                    woodCounts.merge(wood, 1, Integer::sum);
                }
            }
        }

        return new TerrainSurvey(
                totalColumns,
                minimumHeight,
                maximumHeight,
                heightSum,
                plainsBiomeColumns,
                mountainBiomeColumns,
                forestBiomeColumns,
                openGroundColumns,
                rockySurfaceColumns,
                canopyColumns,
                logColumns,
                woodCounts
        );
    }

    private static ResourceLocation findWoodInColumn(ServerLevel level,
                                                     BlockPos.MutableBlockPos cursor,
                                                     int surfaceY) {
        int minimumY = Math.max(level.getMinBuildHeight(), surfaceY - LOG_SEARCH_DEPTH);
        for (int y = surfaceY; y >= minimumY; y--) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (!state.is(BlockTags.LOGS)) {
                continue;
            }

            Item item = state.getBlock().asItem();
            if (item != Items.AIR) {
                return BuiltInRegistries.ITEM.getKey(item);
            }
        }
        return null;
    }

    private static boolean isOpenGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.SAND);
    }

    private static boolean isForestBiome(String path) {
        return containsAny(path, "forest", "taiga", "jungle", "grove", "wooded", "bamboo", "mangrove");
    }

    private static boolean isMountainBiome(String path) {
        return containsAny(path, "mountain", "peak", "slope", "windswept", "hill");
    }

    private static boolean isPlainsBiome(String path) {
        return containsAny(path, "plains", "meadow", "savanna", "desert", "sunflower");
    }

    private static boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static double ratio(int count, int total) {
        return total <= 0 ? 0.0D : (double) count / total;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static AnalysisSettings currentSettings() {
        return new AnalysisSettings(
                VillageDiplomacyConfig.SERVER.specializationScanRadius.get(),
                VillageDiplomacyConfig.SERVER.specializationSampleStep.get(),
                VillageDiplomacyConfig.SERVER.secondarySpecializationScoreRatio.get(),
                VillageDiplomacyConfig.SERVER.minimumSecondarySpecializationScore.get(),
                VillageDiplomacyConfig.SERVER.mountainAltitude.get(),
                VillageDiplomacyConfig.SERVER.mountainRelief.get()
        );
    }

    public record AnalysisSettings(int scanRadius,
                                   int sampleStep,
                                   double secondaryScoreRatio,
                                   double minimumSecondaryScore,
                                   int mountainAltitude,
                                   int mountainRelief) {
        public AnalysisSettings {
            if (scanRadius <= 0 || sampleStep <= 0 || mountainRelief <= 0) {
                throw new IllegalArgumentException("Radii, sample step, and mountain relief must be positive");
            }
            if (secondaryScoreRatio < 0.0D || secondaryScoreRatio > 1.0D
                    || minimumSecondaryScore < 0.0D || minimumSecondaryScore > 1.0D) {
                throw new IllegalArgumentException("Specialization score thresholds must be between 0 and 1");
            }
        }
    }

    public record TerrainSurvey(int totalColumns,
                                int minimumHeight,
                                int maximumHeight,
                                long heightSum,
                                int plainsBiomeColumns,
                                int mountainBiomeColumns,
                                int forestBiomeColumns,
                                int openGroundColumns,
                                int rockySurfaceColumns,
                                int canopyColumns,
                                int logColumns,
                                Map<ResourceLocation, Integer> woodCounts) {
        public TerrainSurvey {
            woodCounts = Map.copyOf(woodCounts);
        }

        public int heightRelief() {
            return totalColumns <= 0 ? 0 : maximumHeight - minimumHeight;
        }

        public double averageHeight() {
            return totalColumns <= 0 ? 0.0D : (double) heightSum / totalColumns;
        }
    }

    public record AnalysisResult(SettlementSpecialization primary,
                                 SettlementSpecialization secondary,
                                 ResourceLocation localWoodResource,
                                 Map<SettlementSpecialization, Double> scores) {
        public AnalysisResult {
            Objects.requireNonNull(primary, "primary");
            Objects.requireNonNull(localWoodResource, "localWoodResource");
            scores = Map.copyOf(scores);
        }
    }
}
