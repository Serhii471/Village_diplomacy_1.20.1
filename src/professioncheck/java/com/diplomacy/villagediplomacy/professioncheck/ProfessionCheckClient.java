package com.diplomacy.villagediplomacy.professioncheck;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.client.renderer.HumanVillagerRenderer;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import java.nio.file.*;
import java.util.*;

@Mod.EventBusSubscriber(modid=VillageDiplomacyMod.MOD_ID,value=Dist.CLIENT)
public final class ProfessionCheckClient {
    private static final Path ROOT=Path.of(System.getProperty("villagediplomacy.skinCheckRoot"));
    private static final String[] TITLES={"Без професії","Броняр","Інструментальник","Зброяр","Бібліотекар","Картограф","Священник",
            "Фермер","Рибалка","Лучник","Шкіряник","М’ясник","Каменяр","Пастух","Нероба"};
    private static ProfessionCheckServer.Scene scene;
    private static final long STARTED=System.currentTimeMillis();
    private static int ticks,frames,page;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();ticks++;
        String viewer=mc.getUser().getName();
        if(Files.exists(ROOT.resolve("server-passed.txt"))){mc.stop();return;}
        if(System.currentTimeMillis()-STARTED>300000){Files.writeString(ROOT.resolve(viewer+"-failure.txt"),"Timeout");mc.stop();return;}
        if(mc.level==null){
            if(ticks%100==0&&(mc.screen instanceof TitleScreen||mc.screen instanceof DisconnectedScreen)&&Files.exists(ROOT.resolve("server-ready.txt")))
                ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25579"),new ServerData("Profession check","127.0.0.1:25579",false),false);
            return;
        }
        if(scene==null)scene=new Gson().fromJson(Files.readString(ROOT.resolve("scene.json")),ProfessionCheckServer.Scene.class);
        String phase=Files.readString(ROOT.resolve("phase.txt"));
        if(phase.equals("gallery")){
            if(Files.exists(ROOT.resolve(viewer+"-gallery.txt")))return;
            mc.getToasts().clear();
            for(var entry:scene.residents().entrySet()){
                Villager v=find(mc,entry.getKey());if(v==null)return;
                String expected="villagediplomacy:textures/entity/resident/"+entry.getValue().replace(':','_')+".png";
                if(!(mc.getEntityRenderDispatcher().getRenderer(v) instanceof HumanVillagerRenderer renderer))throw new IllegalStateException("Wrong renderer");
                if(!renderer.getTextureLocation(v).toString().equals(expected))return;
                try(var stream=mc.getResourceManager().getResource(renderer.getTextureLocation(v)).orElseThrow().open();NativeImage image=NativeImage.read(stream)){
                    if(image.getWidth()!=64||image.getHeight()!=64)throw new IllegalStateException("Wrong texture size");
                }
            }
            if(!(mc.screen instanceof Gallery))mc.setScreen(new Gallery());
            if(++frames==40){
                try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(ROOT.resolve(viewer+"-professions-"+page+".png"));}
                page++;frames=0;
                if(page==4){Files.writeString(ROOT.resolve(viewer+"-gallery.txt"),"135 resources decoded and four native gallery frames captured");mc.setScreen(null);}
            }
        }else if(phase.startsWith("change-")){
            if(Files.exists(ROOT.resolve(viewer+"-"+phase+".txt")))return;
            int index=Integer.parseInt(phase.substring(7));Villager v=find(mc,scene.changing());if(v==null)return;
            if(v.getVillagerData().getProfession()!=ProfessionCheckServer.profession(ProfessionCheckServer.JOBS[index]))return;
            String expected="villagediplomacy:textures/entity/resident/plains_"+ProfessionCheckServer.JOBS[index]+"_0.png";
            String actual=mc.getEntityRenderDispatcher().getRenderer(v).getTextureLocation(v).toString();
            if(!actual.equals(expected))throw new IllegalStateException("Profession update: "+actual+" != "+expected);
            Files.writeString(ROOT.resolve(viewer+"-"+phase+".txt"),expected);
        }
    }
    private static Villager find(Minecraft mc,String uuid){UUID id=UUID.fromString(uuid);for(var entity:mc.level.entitiesForRendering())if(entity instanceof Villager v&&v.getUUID().equals(id))return v;return null;}
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Професії мешканців"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            String region=page<2?"plains":page==2?"mountain":"forest";int face=page<2?0:page-1;boolean back=page==1;
            g.fill(0,0,width,height,0xff18232d);g.drawCenteredString(font,"Професії мешканців — "+region+" — "+(back?"вигляд ззаду":"вигляд спереду"),width/2,10,0xfff0debb);
            int cellW=width/5,cellH=(height-32)/3;
            for(int i=0;i<15;i++){
                String job=ProfessionCheckServer.JOBS[i];
                int x=(i%5)*cellW+cellW/2,y=32+(i/5)*cellH;
                g.fill(x-cellW/2+3,y,x+cellW/2-3,y+cellH-3,0xff22323e);
                Villager v=find(Minecraft.getInstance(),scene.residents().entrySet().stream().filter(e->e.getValue().equals(region+":"+job+":"+face)).findFirst().orElseThrow().getKey());
                float body=v.yBodyRot,head=v.yHeadRot,old=v.yHeadRotO,angle=v.getYRot();
                v.yBodyRot=back?15:195;v.yHeadRot=v.yBodyRot;v.yHeadRotO=v.yHeadRot;v.setYRot(v.yBodyRot);
                InventoryScreen.renderEntityInInventory(g,x,y+cellH-23,(cellH-35)/2,new Quaternionf().rotateZ((float)Math.PI).rotateX(.06f),new Quaternionf().rotateX(.06f),v);
                v.yBodyRot=body;v.yHeadRot=head;v.yHeadRotO=old;v.setYRot(angle);
                g.drawCenteredString(font,TITLES[i],x,y+cellH-16,0xffe4d2a4);
            }
        }
    }
}
