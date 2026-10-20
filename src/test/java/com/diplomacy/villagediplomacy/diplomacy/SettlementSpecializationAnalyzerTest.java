package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SettlementSpecializationAnalyzerTest {
    private static final SettlementSpecializationAnalyzer.AnalysisSettings SETTINGS =
            new SettlementSpecializationAnalyzer.AnalysisSettings(48, 4, 0.65D, 0.30D, 100, 32);
    private static final ResourceLocation OAK = id("minecraft:oak_log");
    private static final ResourceLocation SPRUCE = id("minecraft:spruce_log");

    @Test
    void flatAgriculturalTerrainBecomesPlains() {
        SettlementSpecializationAnalyzer.AnalysisResult result = classify(survey(
                100, 64, 68, 6_600L,
                80, 0, 0,
                80, 0, 0, 0,
                Map.of()
        ));

        assertEquals(SettlementSpecialization.PLAINS, result.primary());
        assertNull(result.secondary());
        assertEquals(OAK, result.localWoodResource());
    }

    @Test
    void highRuggedTerrainBecomesMountain() {
        SettlementSpecializationAnalyzer.AnalysisResult result = classify(survey(
                100, 80, 150, 12_000L,
                0, 80, 0,
                0, 60, 0, 0,
                Map.of()
        ));

        assertEquals(SettlementSpecialization.MOUNTAIN, result.primary());
        assertNull(result.secondary());
    }

    @Test
    void ForestTerrainUsesMostCommonLocalWood() {
        SettlementSpecializationAnalyzer.AnalysisResult result = classify(survey(
                100, 65, 75, 7_000L,
                0, 0, 80,
                10, 0, 70, 50,
                Map.of(SPRUCE, 35, OAK, 15)
        ));

        assertEquals(SettlementSpecialization.FOREST, result.primary());
        assertNull(result.secondary());
        assertEquals(SPRUCE, result.localWoodResource());
    }

    @Test
    void BorderTerrainReceivesSecondarySpecialization() {
        SettlementSpecializationAnalyzer.AnalysisResult result = classify(survey(
                100, 64, 72, 6_800L,
                45, 0, 50,
                40, 0, 40, 30,
                Map.of(OAK, 30)
        ));

        assertEquals(SettlementSpecialization.PLAINS, result.primary());
        assertEquals(SettlementSpecialization.FOREST, result.secondary());
    }

    private static SettlementSpecializationAnalyzer.AnalysisResult classify(
            SettlementSpecializationAnalyzer.TerrainSurvey survey) {
        return SettlementSpecializationAnalyzer.classify(survey, SETTINGS);
    }

    private static SettlementSpecializationAnalyzer.TerrainSurvey survey(
            int totalColumns,
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
        return new SettlementSpecializationAnalyzer.TerrainSurvey(
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

    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value));
    }
}
