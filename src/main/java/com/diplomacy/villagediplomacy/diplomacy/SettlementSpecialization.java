package com.diplomacy.villagediplomacy.diplomacy;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum SettlementSpecialization {
    PLAINS("Plains", 0.70f, 0.30f),
    MOUNTAIN("Mountain", 0.70f, 0.30f),
    FOREST("Forest", 0.70f, 0.30f);

    private final String displayName;
    private final float exportShare;
    private final float internalShare;

    SettlementSpecialization(String displayName, float exportShare, float internalShare) {
        this.displayName = displayName;
        this.exportShare = exportShare;
        this.internalShare = internalShare;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getExportShare() {
        return exportShare;
    }

    public float getInternalShare() {
        return internalShare;
    }

    public Map<Item, Integer> getDailyYield() {
        Map<Item, Integer> yield = new LinkedHashMap<>();
        switch (this) {
            case PLAINS -> {
                yield.put(Items.WHEAT, 24);
                yield.put(Items.WHITE_WOOL, 6);
                yield.put(Items.MUTTON, 4);
            }
            case MOUNTAIN -> {
                yield.put(Items.COAL, 12);
                yield.put(Items.RAW_COPPER, 6);
                yield.put(Items.RAW_IRON, 3);
                yield.put(Items.STONE, 48);
            }
            case FOREST -> {
                yield.put(Items.OAK_LOG, 24);
                yield.put(Items.HONEYCOMB, 4);
                yield.put(Items.APPLE, 3);
            }
        }
        return Collections.unmodifiableMap(yield);
    }

    public static SettlementSpecialization detectFromBiome(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)) {
            return FOREST;
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
            return MOUNTAIN;
        }
        return PLAINS;
    }
}
