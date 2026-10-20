package com.diplomacy.villagediplomacy;

import com.diplomacy.villagediplomacy.client.renderer.VillageGuardRenderer;
import com.diplomacy.villagediplomacy.core.ModEntities;
import com.diplomacy.villagediplomacy.core.ModItems;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModItems::addToCreativeTab);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Village Diplomacy: common setup initialized");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Village Diplomacy: server starting, loading settlements");
    }

    @SubscribeEvent
    public void onBellRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getEntity();

        if (level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (level.getBlockState(pos).is(Blocks.BELL)) {
            ServerLevel serverLevel = (ServerLevel) level;
            SettlementSavedData data = SettlementSavedData.get(serverLevel);
            ItemStack heldItem = player.getItemInHand(event.getHand());

            Settlement settlement = data.getOrCreateSettlementNear(pos, 64, "Settlement #" + pos.toShortString());

            if (heldItem.is(ModItems.LEADERSHIP_SEAL.get())) {
                settlement.setLeaderUuid(player.getUUID());
                data.setDirty();
                player.sendSystemMessage(Component.literal("§aYou have declared yourself the leader of " + settlement.getName() + "!"));
                event.setCanceled(true);
            } else if (heldItem.is(ModItems.DIPLOMATIC_SCROLL.get())) {
                String leaderInfo = settlement.getLeaderUuid() != null
                        ? "Leader: " + (settlement.isPlayerLeader(player.getUUID()) ? "You" : settlement.getLeaderUuid())
                        : "Leader: Village Council (NPC)";
                player.sendSystemMessage(Component.literal("§6=== " + settlement.getName() + " ==="));
                player.sendSystemMessage(Component.literal("§7" + leaderInfo));
                player.sendSystemMessage(Component.literal("§7Relations registered: " + data.getAllSettlements().size() + " known settlements."));
                event.setCanceled(true);
            }
        }
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
