package com.diplomacy.villagediplomacy.core;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.block.RouteSignpostBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, VillageDiplomacyMod.MOD_ID);
    public static final RegistryObject<BlockEntityType<RouteSignpostBlockEntity>> ROUTE_SIGNPOST = TYPES.register("route_signpost",
            () -> BlockEntityType.Builder.of(RouteSignpostBlockEntity::new, ModBlocks.ROUTE_SIGNPOST.get()).build(null));

    private ModBlockEntities() {
    }
}
