package com.diplomacy.villagediplomacy.professioncheck;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.diplomacy.*;
import com.diplomacy.villagediplomacy.garrison.ResidentData;
import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.nio.file.*;
import java.util.*;

/** Disposable-world visual oracle; excluded from ordinary builds. */
@Mod.EventBusSubscriber(modid=VillageDiplomacyMod.MOD_ID)
public final class ProfessionCheckServer {
    static final Path ROOT=Path.of(System.getProperty("villagediplomacy.skinCheckRoot"));
    static final String[] JOBS={"worker","armorer","toolsmith","weaponsmith","librarian","cartographer","cleric",
            "farmer","fisherman","fletcher","leatherworker","butcher","mason","shepherd","nitwit"};
    public record Scene(Map<String,String> residents,String changing) { }
    private static Scene scene;
    private static int stage=-1;
    private static long started;
    static VillagerProfession profession(String job){return BuiltInRegistries.VILLAGER_PROFESSION.get(
            ResourceLocation.tryParse("minecraft:"+(job.equals("worker")?"none":job)));}
    @SubscribeEvent public static void started(ServerStartedEvent event)throws Exception{
        started=System.currentTimeMillis();var server=event.getServer();var level=server.overworld();
        server.setDifficulty(Difficulty.PEACEFUL,true);level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.setDefaultSpawnPos(new BlockPos(0,80,0),0);
        for(int x=-2;x<=2;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        Map<String,String> residents=new LinkedHashMap<>();String changing=null;
        int r=0;
        for(String region:List.of("plains","mountain","forest")){
            int center=-24+r++*24;
            Settlement home=new Settlement(UUID.randomUUID(),region,new BlockPos(center,80,0),11);
            home.setSpecializations(SettlementSpecialization.fromSerializedName(region),null);
            SettlementSavedData.get(level).registerSettlement(home);
            int j=0;
            for(String job:JOBS){for(int face=0;face<3;face++){
                Villager v=EntityType.VILLAGER.create(level);UUID id;
                do{id=UUID.randomUUID();}while(ResidentData.face(id)!=face);
                v.setUUID(id);v.moveTo(center+face-1,80,j*.25,180,0);v.setNoAi(true);v.setNoGravity(true);
                v.setVillagerData(v.getVillagerData().setProfession(profession(job)));
                ResidentData.data(v).putUUID("Home",home.getId());level.addFreshEntity(v);
                residents.put(id.toString(),region+":"+job+":"+face);
                if(changing==null)changing=id.toString();
            }j++;}
        }
        scene=new Scene(residents,changing);Files.writeString(ROOT.resolve("scene.json"),new Gson().toJson(scene));
        Files.writeString(ROOT.resolve("phase.txt"),"gallery");Files.writeString(ROOT.resolve("server-ready.txt"),"0");
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player){player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(player.server.overworld(),0,82,6,180,0);}
    }
    private static boolean all(String marker){return Files.exists(ROOT.resolve("SkinViewerOne-"+marker+".txt"))
            &&Files.exists(ROOT.resolve("SkinViewerTwo-"+marker+".txt"));}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event)throws Exception{
        if(event.phase!=TickEvent.Phase.END||scene==null)return;var server=ServerLifecycleHooks.getCurrentServer();
        if(stage>=JOBS.length)return;
        if(System.currentTimeMillis()-started>300000){Files.writeString(ROOT.resolve("server-failure.txt"),"Timed out at "+stage);server.halt(false);return;}
        if(stage==-1&&!all("gallery"))return;
        if(stage>=0&&!all("change-"+stage))return;
        stage++;
        if(stage==JOBS.length){Files.writeString(ROOT.resolve("server-passed.txt"),"Both clients loaded 135 textures and observed 15 server-synchronized profession changes");server.halt(false);return;}
        Villager v=(Villager)server.overworld().getEntity(UUID.fromString(scene.changing()));
        v.setVillagerData(v.getVillagerData().setProfession(profession(JOBS[stage])));
        Files.writeString(ROOT.resolve("phase.txt"),"change-"+stage);
    }
}
