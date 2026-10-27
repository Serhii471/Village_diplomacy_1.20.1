package com.diplomacy.villagediplomacy.gameplay;

import com.diplomacy.villagediplomacy.core.ModItems;
import com.diplomacy.villagediplomacy.diplomacy.DiplomacyBalance;
import com.diplomacy.villagediplomacy.diplomacy.LeadershipClaimService;
import com.diplomacy.villagediplomacy.diplomacy.Settlement;
import com.diplomacy.villagediplomacy.diplomacy.SettlementRelationship;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSavedData;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSpecialization;
import com.diplomacy.villagediplomacy.diplomacy.SettlementSpecializationAnalyzer;
import com.diplomacy.villagediplomacy.menu.TradeHandoverMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public final class VillageBellInteractionHandler {
    @SubscribeEvent
    public void onBellRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !event.getLevel().getBlockState(event.getPos()).is(Blocks.BELL)) {
            return;
        }

        ItemStack heldItem = event.getEntity().getItemInHand(event.getHand());
        boolean usesLeadershipSeal = heldItem.is(ModItems.LEADERSHIP_SEAL.get());
        boolean usesDiplomaticScroll = heldItem.is(ModItems.DIPLOMATIC_SCROLL.get());
        boolean usesTradeHandover = heldItem.isEmpty();
        if (!usesLeadershipSeal && !usesDiplomaticScroll && !usesTradeHandover) {
            return;
        }

        if (!usesTradeHandover) {
            // Prevent the vanilla bell action on both logical sides for explicit
            // mod items. Every state change below still runs on the server.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }

        if (!(event.getLevel() instanceof ServerLevel serverLevel)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        BlockPos bellPos = event.getPos();
        SettlementSavedData data = SettlementSavedData.get(serverLevel);

        if (usesTradeHandover) {
            Settlement representativeSettlement = data.findSettlementAt(bellPos);
            if (representativeSettlement == null || !representativeSettlement.getCenter().equals(bellPos)) {
                return;
            }
            if (!TradeHandoverMenu.openFor(player, representativeSettlement, bellPos)) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (usesLeadershipSeal) {
            Settlement target = data.getOrCreateSettlementNear(bellPos, defaultSettlementName(bellPos));
            SettlementSpecializationAnalyzer.ensureAssigned(serverLevel, target);
            handleLeadershipSeal(serverLevel, player, data, target);
        } else {
            handleDiplomaticScroll(serverLevel, player, data, bellPos);
        }
    }

    private static void handleLeadershipSeal(ServerLevel level,
                                             ServerPlayer player,
                                             SettlementSavedData data,
                                             Settlement target) {
        LeadershipClaimService.ClaimOutcome outcome = LeadershipClaimService.requestClaim(
                data,
                target,
                player.getUUID(),
                level.getGameTime(),
                player.getPersistentData()
        );

        switch (outcome.result()) {
            case CONFIRMATION_REQUIRED -> player.sendSystemMessage(
                    Component.translatable("message.villagediplomacy.leadership.confirm", target.getName())
                            .withStyle(ChatFormatting.YELLOW)
            );
            case CLAIMED -> player.sendSystemMessage(
                    Component.translatable("message.villagediplomacy.leadership.claimed", target.getName())
                            .withStyle(ChatFormatting.GREEN)
            );
            case ALREADY_LEADER -> player.sendSystemMessage(
                    Component.translatable("message.villagediplomacy.leadership.already_leader", target.getName())
                            .withStyle(ChatFormatting.GRAY)
            );
            case SETTLEMENT_ALREADY_CLAIMED -> player.sendSystemMessage(
                    Component.translatable("message.villagediplomacy.leadership.claimed_by_other", target.getName())
                            .withStyle(ChatFormatting.RED)
            );
            case PLAYER_LEADS_ANOTHER_SETTLEMENT -> {
                Settlement ledSettlement = outcome.relatedSettlement();
                String settlementName = ledSettlement == null ? "?" : ledSettlement.getName();
                player.sendSystemMessage(
                        Component.translatable("message.villagediplomacy.leadership.leads_other", settlementName)
                                .withStyle(ChatFormatting.RED)
                );
            }
        }
    }

    private static void handleDiplomaticScroll(ServerLevel level,
                                               ServerPlayer player,
                                               SettlementSavedData data,
                                               BlockPos bellPos) {
        if (player.getCooldowns().isOnCooldown(ModItems.DIPLOMATIC_SCROLL.get())) {
            player.sendSystemMessage(
                    Component.translatable("message.villagediplomacy.scroll.cooldown")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        Settlement home = data.findSettlementLedBy(player.getUUID());
        if (home == null) {
            player.sendSystemMessage(
                    Component.translatable("message.villagediplomacy.scroll.requires_home")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }
        SettlementSpecializationAnalyzer.ensureAssigned(level, home);

        Settlement target = data.getOrCreateSettlementNear(bellPos, defaultSettlementName(bellPos));
        SettlementSpecializationAnalyzer.ensureAssigned(level, target);
        if (home.getId().equals(target.getId())) {
            player.sendSystemMessage(
                    Component.translatable("message.villagediplomacy.scroll.own_settlement", home.getName())
                            .withStyle(ChatFormatting.GRAY)
            );
            return;
        }

        boolean wasKnown = home.getKnownSettlementIds().contains(target.getId());
        data.discoverSettlement(home.getId(), target.getId());
        player.getCooldowns().addCooldown(
                ModItems.DIPLOMATIC_SCROLL.get(),
                DiplomacyBalance.DIPLOMATIC_SCROLL_COOLDOWN_TICKS
        );

        player.sendSystemMessage(
                Component.translatable(
                                wasKnown
                                        ? "message.villagediplomacy.scroll.already_known"
                                        : "message.villagediplomacy.scroll.discovered",
                                target.getName()
                        )
                        .withStyle(wasKnown ? ChatFormatting.GRAY : ChatFormatting.GOLD)
        );
        sendSettlementInformation(level, player, home, target);
    }

    private static void sendSettlementInformation(ServerLevel level,
                                                  ServerPlayer player,
                                                  Settlement home,
                                                  Settlement target) {
        BlockPos center = target.getCenter();
        SettlementRelationship relationship = home.findRelationship(target.getId()).orElseThrow();
        int distance = (int) Math.round(Math.sqrt(home.getCenter().distSqr(target.getCenter())));

        player.sendSystemMessage(
                Component.translatable("message.villagediplomacy.scroll.header", target.getName())
                        .withStyle(ChatFormatting.GOLD)
        );
        player.sendSystemMessage(info("message.villagediplomacy.scroll.coordinates",
                center.getX(), center.getY(), center.getZ()));
        player.sendSystemMessage(info(
                "message.villagediplomacy.scroll.specialization",
                specializationComponent(target)
        ));
        player.sendSystemMessage(info(
                "message.villagediplomacy.scroll.resources",
                resourcesComponent(target)
        ));
        player.sendSystemMessage(info(
                "message.villagediplomacy.scroll.leader",
                resolveLeaderName(level, target)
        ));
        player.sendSystemMessage(info(
                "message.villagediplomacy.scroll.relation",
                Component.translatable(relationship.getDiplomacyRelation().getTranslationKey())
        ));
        player.sendSystemMessage(info("message.villagediplomacy.scroll.distance", distance));
    }

    private static Component resolveLeaderName(ServerLevel level, Settlement settlement) {
        UUID leaderId = settlement.getLeaderUuid();
        if (leaderId == null) {
            return Component.translatable("message.villagediplomacy.scroll.village_council");
        }

        String name = level.getServer().getProfileCache().get(leaderId)
                .map(profile -> profile.getName())
                .orElse(leaderId.toString());
        return Component.literal(name);
    }

    private static Component specializationComponent(Settlement settlement) {
        Component primary = Component.translatable(settlement.getPrimarySpecialization().getTranslationKey());
        return settlement.getSecondarySpecialization()
                .<Component>map(secondary -> Component.translatable(
                        "message.villagediplomacy.scroll.mixed_specialization",
                        primary,
                        Component.translatable(secondary.getTranslationKey())
                ))
                .orElse(primary);
    }

    private static Component resourcesComponent(Settlement settlement) {
        Component primary = resourcesFor(settlement, settlement.getPrimarySpecialization());
        return settlement.getSecondarySpecialization()
                .<Component>map(secondary -> Component.translatable(
                        "message.villagediplomacy.scroll.combined_resources",
                        primary,
                        resourcesFor(settlement, secondary)
                ))
                .orElse(primary);
    }

    private static Component resourcesFor(Settlement settlement, SettlementSpecialization specialization) {
        if (specialization != SettlementSpecialization.FOREST) {
            return Component.translatable(specialization.getResourcesTranslationKey());
        }

        Item localWood = BuiltInRegistries.ITEM.get(settlement.getLocalWoodResource());
        return Component.translatable(
                specialization.getResourcesTranslationKey(),
                Component.translatable(localWood.getDescriptionId())
        );
    }

    private static Component info(String key, Object... arguments) {
        return Component.translatable(key, arguments).withStyle(ChatFormatting.GRAY);
    }

    private static String defaultSettlementName(BlockPos bellPos) {
        return "Settlement " + bellPos.getX() + ", " + bellPos.getZ();
    }
}
