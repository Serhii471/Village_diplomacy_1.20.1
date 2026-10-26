package com.diplomacy.villagediplomacy.skincheck;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Uses the real client world, network entity metadata and production renderer. */
@Mod.EventBusSubscriber(modid = VillageDiplomacyMod.MOD_ID, value = Dist.CLIENT)
public final class GuardSkinCheckClient {
    private static final Path ROOT = Path.of(System.getProperty("villagediplomacy.skinCheckRoot"));
    private static int ticks;
    private static int screenshotFrames;
    private static String step = "";
    private static final long STARTED = System.currentTimeMillis();

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (Files.exists(ROOT.resolve("server-passed.txt")) && mc.level == null) {
            mc.stop();
            return;
        }
        if (System.currentTimeMillis() - STARTED > 420_000) {
            Files.writeString(ROOT.resolve(mc.getUser().getName() + "-failure.txt"), "Client timed out at " + step);
            mc.stop();
            return;
        }
        if (mc.level == null) {
            if (ticks % 100 == 0 && (mc.screen instanceof TitleScreen || mc.screen instanceof DisconnectedScreen)
                    && Files.exists(ROOT.resolve("server-ready.txt"))
                    && !Files.exists(ROOT.resolve("server-passed.txt"))) {
                if (Files.exists(ROOT.resolve("reload.marker"))
                        && !Files.readString(ROOT.resolve("server-ready.txt")).equals("3")) return;
                ServerData server = new ServerData("Disposable skin check", "127.0.0.1:25579", false);
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(server.ip), server, false);
            }
            return;
        }
        List<VillageGuardEntity> guards = new ArrayList<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof VillageGuardEntity guard && guard.hasCustomName()
                    && guard.getCustomName().getString().startsWith("skincheck:")) guards.add(guard);
        }
        if (guards.size() != 8) return;
        guards.sort(Comparator.comparing(g -> g.getCustomName().getString()));
        boolean reloading = Files.exists(ROOT.resolve("reload.marker"));
        String next = reloading ? "reloaded" : !Files.exists(ROOT.resolve(mc.getUser().getName() + "-initial.txt"))
                ? "initial" : !Files.exists(ROOT.resolve(mc.getUser().getName() + "-changed.txt")) ? "changed" : "removed";
        if (Files.exists(ROOT.resolve(mc.getUser().getName() + "-" + next + ".txt"))) {
            if (next.equals("reloaded")) mc.stop();
            return;
        }
        for (VillageGuardEntity guard : guards) {
            String[] parts = guard.getCustomName().getString().split(":");
            String expected = parts[1];
            if (!next.equals("initial") && expected.equals("plains")) expected = "forest";
            if ((next.equals("removed") || next.equals("reloaded")) && expected.equals("forest") && parts[1].equals("forest")) expected = "base";
            String actual = guard.getUniformSpecialization().getSerializedName();
            if (!actual.equals(expected.equals("base") ? "unassigned" : expected)) return;
            var renderer = mc.getEntityRenderDispatcher().getRenderer(guard);
            String texture = renderer.getTextureLocation(guard).toString();
            if (!texture.equals("villagediplomacy:textures/entity/guard/guard_" + expected + ".png"))
                throw new IllegalStateException("Wrong production texture: " + texture);
            mc.getResourceManager().getResource(renderer.getTextureLocation(guard)).orElseThrow();
            if (!(renderer instanceof LivingEntityRenderer<?, ?> living) || !(living.getModel() instanceof PlayerModel<?> model)
                    || !model.jacket.visible || !model.leftSleeve.visible || !model.rightSleeve.visible
                    || !model.leftPants.visible || !model.rightPants.visible || !model.hat.visible)
                throw new IllegalStateException("Skin outer layers missing");
            if (parts[2].equals("1") && !guard.getItemBySlot(EquipmentSlot.CHEST).is(Items.CHAINMAIL_CHESTPLATE))
                throw new IllegalStateException("Spawn equipment was lost");
        }
        if (!next.equals(step) || !(mc.screen instanceof Gallery)) {
            step = next;
            screenshotFrames = 0;
            mc.getToasts().clear();
            mc.setScreen(new Gallery(guards, next));
        }
        if (++screenshotFrames == 45) {
            try (NativeImage screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                screenshot.writeToFile(ROOT.resolve(mc.getUser().getName() + "-" + next + ".png"));
            }
            Files.writeString(ROOT.resolve(mc.getUser().getName() + "-" + next + ".txt"),
                    "8 server-tracked guards: uniforms, renderer texture, outer layers and chainmail verified; " + next);
            VillageDiplomacyMod.LOGGER.info("GUARD_SKIN_VIEWER_PASSED {} {}", mc.getUser().getName(), next);
        }
    }

    private static final class Gallery extends Screen {
        private final List<VillageGuardEntity> guards;
        private final String stage;
        Gallery(List<VillageGuardEntity> guards, String stage) {
            super(Component.literal("Guard uniform verification"));
            this.guards = guards;
            this.stage = stage;
        }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff121b24);
            graphics.drawCenteredString(font, "Settlement guard uniforms | " + minecraft.getUser().getName() + " | " + stage,
                    width / 2, 10, 0xffeee7d9);
            graphics.drawCenteredString(font, "Actual server entities | production renderer | all skin layers enabled", width / 2, 24, 0xffa7b6c4);
            int colWidth = width / 4;
            int rowHeight = (height - 62) / 2;
            for (int type = 0; type < 4; type++) {
                String id = GuardSkinCheckServer.TYPES[type];
                int center = colWidth * type + colWidth / 2;
                graphics.drawCenteredString(font, id.toUpperCase(), center, 42, 0xffd9c28a);
                for (int armor = 0; armor < 2; armor++) {
                    String name = "skincheck:" + id + ":" + armor;
                    VillageGuardEntity guard = guards.stream().filter(g -> name.equals(g.getCustomName().getString())).findFirst().orElseThrow();
                    int feet = 62 + rowHeight * (armor + 1) - 26;
                    int scale = Math.max(22, (rowHeight - 34) / 2);
                    drawGuard(graphics, guard, center - colWidth / 5, feet, scale, false);
                    drawGuard(graphics, guard, center + colWidth / 5, feet, scale, true);
                    graphics.drawCenteredString(font, armor == 0 ? "Uniform / front + back" : "Chainmail / front + back", center, feet + 9, 0xffa7b6c4);
                }
            }
        }
        private void drawGuard(GuiGraphics graphics, VillageGuardEntity guard, int x, int y, int scale, boolean back) {
            float oldBody = guard.yBodyRot;
            float oldHead = guard.yHeadRot;
            float oldHeadO = guard.yHeadRotO;
            float oldY = guard.getYRot();
            guard.yBodyRot = back ? 15 : 195;
            guard.yHeadRot = guard.yBodyRot;
            guard.yHeadRotO = guard.yHeadRot;
            guard.setYRot(guard.yBodyRot);
            Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI).rotateX(.08f);
            InventoryScreen.renderEntityInInventory(graphics, x, y, scale, rotation, new Quaternionf().rotateX(.08f), guard);
            guard.yBodyRot = oldBody;
            guard.yHeadRot = oldHead;
            guard.yHeadRotO = oldHeadO;
            guard.setYRot(oldY);
        }
    }
}
