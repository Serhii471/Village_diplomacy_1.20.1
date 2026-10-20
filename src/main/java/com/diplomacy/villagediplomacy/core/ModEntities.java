package com.diplomacy.villagediplomacy.core;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, VillageDiplomacyMod.MOD_ID);

    public static final RegistryObject<EntityType<VillageGuardEntity>> VILLAGE_GUARD =
            ENTITY_TYPES.register("village_guard", () ->
                    EntityType.Builder.of(VillageGuardEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .build("village_guard")
            );
}
