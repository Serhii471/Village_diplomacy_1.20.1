package com.diplomacy.villagediplomacy.core;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.block.DiplomacyTableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, VillageDiplomacyMod.MOD_ID);

    public static final RegistryObject<Block> DIPLOMACY_TABLE = BLOCKS.register("diplomacy_table",
            () -> new DiplomacyTableBlock(BlockBehaviour.Properties.copy(Blocks.LECTERN)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    private ModBlocks() {
    }
}
