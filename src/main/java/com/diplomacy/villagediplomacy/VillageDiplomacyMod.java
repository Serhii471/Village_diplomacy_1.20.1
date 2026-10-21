package com.diplomacy.villagediplomacy;

import com.diplomacy.villagediplomacy.client.renderer.VillageGuardRenderer;
import com.diplomacy.villagediplomacy.config.VillageDiplomacyConfig;
import com.diplomacy.villagediplomacy.core.ModEntities;
import com.diplomacy.villagediplomacy.core.ModItems;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import com.diplomacy.villagediplomacy.gameplay.VillageBellInteractionHandler;
import com.diplomacy.villagediplomacy.gameplay.SettlementProductionTicker;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(VillageDiplomacyMod.MOD_ID)
public class VillageDiplomacyMod {
    public static final String MOD_ID = "villagediplomacy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VillageDiplomacyMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER,
                VillageDiplomacyConfig.SERVER_SPEC,
                "villagediplomacy-server.toml"
        );

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModItems::addToCreativeTab);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new VillageBellInteractionHandler());
        MinecraftForge.EVENT_BUS.register(new SettlementProductionTicker());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Village Diplomacy: common setup initialized");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Village Diplomacy: server starting, loading settlements");
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerAttributes(EntityAttributeCreationEvent event) {
            event.put(ModEntities.VILLAGE_GUARD.get(), VillageGuardEntity.createAttributes().build());
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.VILLAGE_GUARD.get(), VillageGuardRenderer::new);
        }
    }
}
