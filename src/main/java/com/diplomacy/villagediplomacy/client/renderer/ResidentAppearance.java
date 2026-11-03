package com.diplomacy.villagediplomacy.client.renderer;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.garrison.ResidentData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = VillageDiplomacyMod.MOD_ID, value = Dist.CLIENT)
public final class ResidentAppearance {
    private static final Map<UUID, String> REGIONS = new HashMap<>();
    public static void accept(UUID entity, String region) { REGIONS.put(entity, region); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { REGIONS.clear(); }
    public static ResourceLocation texture(Villager villager) {
        String region = REGIONS.get(villager.getUUID());
        if (region == null || region.equals("unassigned") || region.equals("base")) {
            VillagerType biome = villager.getVillagerData().getType();
            region = biome == VillagerType.SNOW || biome == VillagerType.TAIGA ? "mountain"
                    : biome == VillagerType.JUNGLE || biome == VillagerType.SWAMP ? "forest" : "plains";
        }
        String job = "worker";
        VillagerProfession profession = villager.getVillagerData().getProfession();
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        // Unknown professions from other mods keep a valid regional outfit.
        // Resolve on every render so acquiring/changing a job updates immediately.
        if (key != null && key.getNamespace().equals("minecraft")) {
            job = switch (key.getPath()) {
                case "armorer", "toolsmith", "weaponsmith", "librarian", "cartographer",
                        "cleric", "farmer", "fisherman", "fletcher", "leatherworker",
                        "butcher", "mason", "shepherd", "nitwit" -> key.getPath();
                default -> "worker";
            };
        }
        return ResourceLocation.tryParse("villagediplomacy:textures/entity/resident/" + region + "_" + job
                + "_" + ResidentData.face(villager.getUUID()) + ".png");
    }
}
