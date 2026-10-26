package com.diplomacy.villagediplomacy.residentcheck;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.core.ModBlocks;
import com.diplomacy.villagediplomacy.diplomacy.*;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import com.diplomacy.villagediplomacy.garrison.*;
import com.diplomacy.villagediplomacy.menu.GarrisonMenu;
import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Mod.EventBusSubscriber(modid = VillageDiplomacyMod.MOD_ID)
public final class ResidentCheckServer {
    static final Path ROOT = Path.of(System.getProperty("villagediplomacy.skinCheckRoot"));
    static final String[] VIEWERS = {"SkinViewerOne", "SkinViewerTwo"};
    public record Scene(Map<String, String> homes, Map<String, String> residents, String candidate) { }
    static Scene scene;
    private static int stage;
    private static long started;
    private static final BlockPos TABLE = new BlockPos(-24, 80, 7);
    @SubscribeEvent public static void started(ServerStartedEvent event) throws Exception {
        started = System.currentTimeMillis();
        ServerLevel level = event.getServer().overworld();
        event.getServer().setDifficulty(Difficulty.PEACEFUL, true);
        level.setDayTime(6000);
        level.setDefaultSpawnPos(TABLE.above(), 180);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        for (int x = -3; x <= 2; x++) for (int z = -1; z <= 0; z++) { level.setChunkForced(x,z,true); level.getChunk(x,z); }
        if (Files.exists(ROOT.resolve("reload.marker"))) {
            scene = new Gson().fromJson(Files.readString(ROOT.resolve("scene.json")), Scene.class);
            stage = 3;
        } else {
            Map<String,String> homes = new LinkedHashMap<>(), residents = new LinkedHashMap<>();
            UUID candidate = null;
            for (int x=-40;x<=40;x++)for(int z=-4;z<=12;z++){
                level.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState());
                for(int y=80;y<84;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            String[] regions={"plains","mountain","forest"};
            for(int region=0;region<3;region++){
                int center=-24+region*24;
                Settlement home=new Settlement(UUID.randomUUID(),"Test "+regions[region],new BlockPos(center,80,0),11);
                home.setSpecializations(SettlementSpecialization.fromSerializedName(regions[region]),null);
                SettlementSavedData.get(level).registerSettlement(home);homes.put(regions[region],home.getId().toString());
                for(int job=0;job<3;job++)for(int face=0;face<3;face++){
                    Villager villager=EntityType.VILLAGER.create(level); UUID id;
                    do{id=UUID.randomUUID();}while(ResidentData.face(id)!=face);
                    villager.setUUID(id);villager.moveTo(center-3+face*3,80,job*3,180,0);villager.setNoAi(true);
                    villager.setVillagerData(villager.getVillagerData().setProfession(job==0?VillagerProfession.NONE:job==1?VillagerProfession.ARMORER:VillagerProfession.LIBRARIAN));
                    ResidentData.data(villager).putUUID("Home",home.getId());
                    villager.setCustomName(ResidentData.name(villager));
                    if(job==2){MerchantOffers offers=new MerchantOffers();offers.add(new MerchantOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.BOOK),8,2,.05f));villager.setOffers(offers);}
                    level.addFreshEntity(villager);residents.put(id.toString(),regions[region]+":"+job+":"+face);
                    if(region==0&&job==0&&face==1)candidate=id;
                }
            }
            Villager child=EntityType.VILLAGER.create(level);child.moveTo(-24,80,-2,180,0);child.setBaby(true);child.setNoAi(true);level.addFreshEntity(child);
            scene=new Scene(homes,residents,Objects.requireNonNull(candidate).toString());
            Files.writeString(ROOT.resolve("scene.json"),new Gson().toJson(scene));
            level.setBlockAndUpdate(TABLE,ModBlocks.DIPLOMACY_TABLE.get().defaultBlockState());
            stage=0;
        }
        Files.writeString(ROOT.resolve("phase.txt"),stage==3?"reloaded":"initial");
        Files.writeString(ROOT.resolve("server-ready.txt"),Integer.toString(stage));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        player.setGameMode(GameType.SURVIVAL);player.getAbilities().invulnerable=true;
        player.teleportTo(player.server.overworld(),TABLE.getX()+.5,80,TABLE.getZ()+2.5,180,0);
        Settlement home=SettlementSavedData.get(player.serverLevel()).getSettlement(UUID.fromString(scene.homes().get("plains")));
        if(player.getGameProfile().getName().equals(VIEWERS[0])){
            home.setLeaderUuid(player.getUUID());
            if(stage==0){player.getInventory().clearContent();player.getInventory().add(new ItemStack(Items.IRON_SWORD));player.getInventory().add(new ItemStack(Items.LEATHER_CHESTPLATE));player.getInventory().add(new ItemStack(Items.BREAD,6));}
        }
        if(stage==3)GarrisonMenu.open(player,TABLE,"");
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event)throws Exception{
        if(event.phase!=TickEvent.Phase.END||started==0)return;
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        if(System.currentTimeMillis()-started>360000){Files.writeString(ROOT.resolve("server-failure.txt"),"Timeout stage "+stage);server.halt(false);return;}
        ServerLevel level=server.overworld();
        Settlement home=SettlementSavedData.get(level).getSettlement(UUID.fromString(scene.homes().get("plains")));
        if(stage==0&&all("initial")){
            openMenus();Files.writeString(ROOT.resolve("phase.txt"),"train");stage=1;
        }else if(stage==1&&all("changed")){
            if(GarrisonService.population(level,home).trainees()!=1)throw new IllegalStateException("Expected one real trainee");
            Files.writeString(ROOT.resolve("reload.marker"),"Reload the queued person and reserved items");
            server.saveEverything(true,true,true);server.halt(false);stage=9;
        }else if(stage==3&&all("reloaded")){
            Villager villager=(Villager)level.getEntity(UUID.fromString(scene.candidate()));
            if(villager==null||!ResidentData.isTraining(villager))throw new IllegalStateException("Training was lost after restart");
            ResidentData.data(villager).getCompound(ResidentData.TRAINING).putLong("Finish",level.getGameTime());stage=4;
        }else if(stage==4){
            var person=GarrisonSavedData.get(level).get(UUID.fromString(scene.candidate()));
            if(person!=null&&person.role()==GarrisonSavedData.Role.SOLDIER){openMenus();Files.writeString(ROOT.resolve("phase.txt"),"converted");stage=5;}
        }else if(stage==5&&all("converted")){
            Files.writeString(ROOT.resolve("demobilize.go"),"Use the actual garrison button");stage=6;
        }else if(stage==6){
            var person=GarrisonSavedData.get(level).get(UUID.fromString(scene.candidate()));
            if(person!=null&&person.role()==GarrisonSavedData.Role.CIVILIAN){openMenus();Files.writeString(ROOT.resolve("phase.txt"),"restored");stage=7;}
        }else if(stage==7&&all("removed")){
            var player=server.getPlayerList().getPlayerByName(VIEWERS[0]);
            if(player.getInventory().countItem(Items.IRON_SWORD)!=1||player.getInventory().countItem(Items.LEATHER_CHESTPLATE)!=1
                    ||player.getInventory().countItem(Items.BREAD)!=0)throw new IllegalStateException("Escrow duplicated or equipment was not returned");
            Villager original=(Villager)level.getEntity(UUID.fromString(scene.candidate()));
            if(original==null||GarrisonService.population(level,home).civilians()!=9||GarrisonService.population(level,home).soldiers()!=0)
                throw new IllegalStateException("Population or original villager was not restored");
            Files.writeString(ROOT.resolve("server-passed.txt"),"Two clients: human villagers, real inventory clicks and menu actions, training after restart, original identity, demobilization and exact equipment return verified");
            server.halt(false);stage=9;
        }
    }
    private static void openMenus(){for(var player:ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers())GarrisonMenu.open(player,TABLE,"");}
    private static boolean all(String step){for(String viewer:VIEWERS)if(!Files.exists(ROOT.resolve(viewer+"-"+step+".txt")))return false;return true;}
}
