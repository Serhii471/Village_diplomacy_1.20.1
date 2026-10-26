package com.diplomacy.villagediplomacy.residentcheck;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.client.renderer.HumanVillagerRenderer;
import com.diplomacy.villagediplomacy.client.screen.GarrisonScreen;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import com.diplomacy.villagediplomacy.garrison.GarrisonSavedData;
import com.diplomacy.villagediplomacy.menu.GarrisonMenu;
import com.diplomacy.villagediplomacy.menu.DiplomacyTableMenu;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import java.nio.file.Files;
import java.util.*;

@Mod.EventBusSubscriber(modid=VillageDiplomacyMod.MOD_ID,value=Dist.CLIENT)
public final class ResidentCheckClient {
    private static final java.nio.file.Path ROOT=java.nio.file.Path.of(System.getProperty("villagediplomacy.skinCheckRoot"));
    private static ResidentCheckServer.Scene scene;
    private static long started=System.currentTimeMillis();
    private static int ticks,frames;
    private static String phase="";
    private static boolean recruited,invalidAttempt,demobilized;
    private static final Map<UUID,String> ORIGINAL_TEXTURES=new HashMap<>();
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();ticks++;
        String viewer=mc.getUser().getName();boolean leader=viewer.equals("SkinViewerOne");
        if(System.currentTimeMillis()-started>420000){Files.writeString(ROOT.resolve(viewer+"-failure.txt"),"Timeout at "+phase);mc.stop();return;}
        if(Files.exists(ROOT.resolve("server-passed.txt"))&&mc.level==null){mc.stop();return;}
        if(mc.level==null){
            if(ticks%100==0&&(mc.screen instanceof TitleScreen||mc.screen instanceof DisconnectedScreen)&&Files.exists(ROOT.resolve("server-ready.txt"))){
                if(Files.exists(ROOT.resolve("reload.marker"))&&!Files.readString(ROOT.resolve("server-ready.txt")).equals("3"))return;
                var server=new ServerData("Resident verification","127.0.0.1:25579",false);
                ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(server.ip),server,false);
            }
            return;
        }
        if(scene==null)scene=new Gson().fromJson(Files.readString(ROOT.resolve("scene.json")),ResidentCheckServer.Scene.class);
        String next=Files.readString(ROOT.resolve("phase.txt"));
        if(!next.equals(phase)){phase=next;frames=0;mc.getToasts().clear();}
        if(phase.equals("initial")){
            if(done(viewer,"initial"))return;
            List<LivingEntity> residents=new ArrayList<>();
            for(var entry:scene.residents().entrySet()){
                // Client entity storage is ID-indexed; find stable UUIDs from tracked entities.
                Villager villager=findVillager(mc,UUID.fromString(entry.getKey()));
                if(villager==null)return;
                var renderer=mc.getEntityRenderDispatcher().getRenderer(villager);
                if(!(renderer instanceof HumanVillagerRenderer))throw new IllegalStateException("Vanilla villager renderer was not replaced");
                String[] parts=entry.getValue().split(":");
                String job=parts[1].equals("0")?"worker":parts[1].equals("1")?"smith":"scholar";
                String expected="villagediplomacy:textures/entity/resident/"+parts[0]+"_"+job+"_"+parts[2]+".png";
                String actual=renderer.getTextureLocation(villager).toString();
                if(!actual.equals(expected))return;
                mc.getResourceManager().getResource(renderer.getTextureLocation(villager)).orElseThrow();
                ORIGINAL_TEXTURES.put(villager.getUUID(),actual);residents.add(villager);
            }
            if(!(mc.screen instanceof Gallery))mc.setScreen(new Gallery(residents,false));
            if(++frames==45){capture(mc,viewer+"-residents.png");mark(viewer,"initial");}
            return;
        }
        if(!(mc.player.containerMenu instanceof GarrisonMenu menu)){
            if(mc.player.containerMenu instanceof DiplomacyTableMenu&&ticks%20==0)mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,GarrisonMenu.OPEN);
            else if(ticks%40==0&&mc.screen==null){BlockPos pos=new BlockPos(-24,80,7);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));}
            return;
        }
        var snapshot=menu.getSnapshot();
        if(ticks%100==0){
            Files.writeString(ROOT.resolve(viewer+"-progress.json"),new Gson().toJson(Map.of(
                    "phase",phase,"message",snapshot.message(),"civilians",snapshot.civilians(),"trainees",snapshot.trainees(),
                    "soldiers",snapshot.soldiers(),"canManage",snapshot.canManage(),"recruitSent",recruited,
                    "supplies",java.util.stream.IntStream.range(0,3).mapToObj(i->menu.getSlot(i).getItem().toString()).toList())));
            capture(mc,viewer+"-progress.png");
        }
        if(snapshot.canManage()!=leader)throw new IllegalStateException("Garrison permissions differ between viewers");
        UUID candidate=UUID.fromString(scene.candidate());
        if(phase.equals("train")){
            if(done(viewer,"changed"))return;
            if(!leader){
                if(!invalidAttempt){com.diplomacy.villagediplomacy.network.ResidentNetwork.sendAction(menu.containerId,candidate);invalidAttempt=true;}
                if(++frames==45){capture(mc,viewer+"-read-only.png");mark(viewer,"changed");}
                return;
            }
            if(snapshot.trainees()==0&&!recruited){
                for(int target=0;target<3;target++)if(menu.getSlot(target).getItem().isEmpty()){
                    for(int i=3;i<menu.slots.size();i++){
                        var stack=menu.getSlot(i).getItem();
                        boolean match=target==0?stack.is(Items.IRON_SWORD):target==1?stack.is(Items.LEATHER_CHESTPLATE):stack.is(Items.BREAD);
                        if(match&&ticks%5==0){mc.gameMode.handleInventoryMouseClick(menu.containerId,i,0,ClickType.QUICK_MOVE,mc.player);return;}
                    }
                    return;
                }
                com.diplomacy.villagediplomacy.network.ResidentNetwork.sendAction(menu.containerId,candidate);recruited=true;frames=0;return;
            }
            if(snapshot.trainees()==1&&++frames==45){capture(mc,viewer+"-training.png");mark(viewer,"changed");}
        }else if(phase.equals("reloaded")){
            if(done(viewer,"reloaded"))return;
            if(snapshot.trainees()!=1)return;
            var entry=snapshot.entries().stream().filter(e->e.identity().equals(candidate)).findFirst().orElseThrow();
            if(entry.role()!=GarrisonSavedData.Role.TRAINING||entry.remaining()<20000)throw new IllegalStateException("Training or deadline lost on server restart");
            if(++frames==45){capture(mc,viewer+"-training-after-reload.png");mark(viewer,"reloaded");}
        }else if(phase.equals("converted")){
            VillageGuardEntity guard=findGuard(mc,candidate);if(guard==null||snapshot.soldiers()!=1)return;
            String expected="villagediplomacy:textures/entity/guard/guard_plains_face_1.png";
            if(!mc.getEntityRenderDispatcher().getRenderer(guard).getTextureLocation(guard).toString().equals(expected))throw new IllegalStateException("Recruited identity face changed");
            if(!done(viewer,"converted")){
                if(++frames==45){capture(mc,viewer+"-garrison-soldier.png");mc.setScreen(new Gallery(List.of(guard),true));}
                if(frames==70){capture(mc,viewer+"-recruited-soldier.png");mc.setScreen(new GarrisonScreen(menu,mc.player.getInventory(),Component.translatable("screen.villagediplomacy.garrison")));mark(viewer,"converted");}
            }else if(leader&&!demobilized&&Files.exists(ROOT.resolve("demobilize.go"))){com.diplomacy.villagediplomacy.network.ResidentNetwork.sendAction(menu.containerId,candidate);demobilized=true;}
        }else if(phase.equals("restored")){
            if(done(viewer,"removed"))return;
            Villager original=findVillager(mc,candidate);if(original==null||snapshot.soldiers()!=0||snapshot.trainees()!=0||snapshot.civilians()!=9)return;
            if(!mc.getEntityRenderDispatcher().getRenderer(original).getTextureLocation(original).toString().equals(ORIGINAL_TEXTURES.get(candidate)))throw new IllegalStateException("Restored civilian appearance changed");
            if(findGuard(mc,candidate)!=null)throw new IllegalStateException("Demobilization left a duplicate soldier");
            if(++frames==45){capture(mc,viewer+"-demobilized.png");mark(viewer,"removed");}
        }
    }
    private static int index(GarrisonMenu menu,UUID id){for(int i=0;i<menu.getSnapshot().entries().size();i++)if(menu.getSnapshot().entries().get(i).identity().equals(id))return i;throw new IllegalStateException("Candidate not in roster");}
    private static Villager findVillager(Minecraft mc,UUID id){for(var e:mc.level.entitiesForRendering())if(e instanceof Villager v&&v.getUUID().equals(id))return v;return null;}
    private static VillageGuardEntity findGuard(Minecraft mc,UUID id){for(var e:mc.level.entitiesForRendering())if(e instanceof VillageGuardEntity g&&g.getResidentIdentity().filter(id::equals).isPresent())return g;return null;}
    private static boolean done(String viewer,String step){return Files.exists(ROOT.resolve(viewer+"-"+step+".txt"));}
    private static void mark(String viewer,String step)throws Exception{Files.writeString(ROOT.resolve(viewer+"-"+step+".txt"),"Real client state and server menu protocol verified");VillageDiplomacyMod.LOGGER.info("RESIDENT_VIEWER_PASSED {} {}",viewer,step);}
    private static void capture(Minecraft mc,String file)throws Exception{try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(ROOT.resolve(file));}}
    private static final class Gallery extends Screen{
        private final List<LivingEntity> entities;private final boolean soldier;
        Gallery(List<LivingEntity> entities,boolean soldier){super(Component.literal("Resident appearance check"));this.entities=entities;this.soldier=soldier;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xff15202a);
            g.drawCenteredString(font,soldier?"Мешканець після військової підготовки":"Жителі поселень — людський вигляд",width/2,12,0xffeee7d9);
            if(soldier){draw(g,entities.get(0),width/2-100,height-70,150,false);draw(g,entities.get(0),width/2+100,height-70,150,true);return;}
            String[] regions={"plains","mountain","forest"},titles={"Рівнинне поселення","Гірське поселення","Лісове поселення"},jobs={"Працівники","Ковалі","Книжники"};
            int rowHeight=(height-70)/3;
            for(int r=0;r<3;r++){
                int center=width*r/3+width/6;g.drawCenteredString(font,titles[r],center,30,0xffd9bf83);
                for(int job=0;job<3;job++){
                    int feet=52+(job+1)*rowHeight-16;
                    for(int face=0;face<3;face++){
                        String key=regions[r]+":"+job+":"+face;
                        var entity=entities.stream().filter(e->key.equals(scene.residents().get(e.getUUID().toString()))).findFirst().orElseThrow();
                        draw(g,entity,center+(face-1)*70,feet,(rowHeight-28)/2,false);
                    }
                    g.drawCenteredString(font,jobs[job],center,feet+8,0xffa7b6c4);
                }
            }
        }
        private void draw(GuiGraphics g,LivingEntity entity,int x,int y,int scale,boolean back){
            float body=entity.yBodyRot,head=entity.yHeadRot,oldHead=entity.yHeadRotO,angle=entity.getYRot();
            entity.yBodyRot=back?15:195;entity.yHeadRot=entity.yBodyRot;entity.yHeadRotO=entity.yHeadRot;entity.setYRot(entity.yBodyRot);
            InventoryScreen.renderEntityInInventory(g,x,y,scale,new Quaternionf().rotateZ((float)Math.PI).rotateX(.06f),new Quaternionf().rotateX(.06f),entity);
            entity.yBodyRot=body;entity.yHeadRot=head;entity.yHeadRotO=oldHead;entity.setYRot(angle);
        }
    }
}
