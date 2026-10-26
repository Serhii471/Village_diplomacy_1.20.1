package com.diplomacy.villagediplomacy.client.screen;

import com.diplomacy.villagediplomacy.garrison.GarrisonSavedData;
import com.diplomacy.villagediplomacy.menu.GarrisonMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class GarrisonScreen extends AbstractContainerScreen<GarrisonMenu> {
    private static final int ROWS = 3;
    private int page;
    public GarrisonScreen(GarrisonMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 360; imageHeight = 236; inventoryLabelY = 10_000;
    }
    private void send(int button) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }
    @Override protected void init() {
        super.init();
        int pages = Math.max(1, (menu.getSnapshot().entries().size() + ROWS - 1) / ROWS);
        page = Math.min(page, pages - 1);
        addRenderableWidget(Button.builder(Component.literal("<"), button -> { page = Math.max(0, page - 1); rebuildWidgets(); })
                .bounds(leftPos + 14, topPos + 126, 22, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> { page = Math.min(pages - 1, page + 1); rebuildWidgets(); })
                .bounds(leftPos + 40, topPos + 126, 22, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.villagediplomacy.garrison.refresh"), button -> send(GarrisonMenu.REFRESH))
                .bounds(leftPos + 98, topPos + 126, 84, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.villagediplomacy.garrison.back"), button -> send(GarrisonMenu.BACK))
                .bounds(leftPos + 220, topPos + 210, 124, 20).build());
        var snapshot = menu.getSnapshot();
        for (int row = 0; row < ROWS; row++) {
            int index = page * ROWS + row;
            if (index >= snapshot.entries().size()) break;
            var entry = snapshot.entries().get(index);
            String action = entry.role() == GarrisonSavedData.Role.CIVILIAN ? "recruit"
                    : entry.role() == GarrisonSavedData.Role.TRAINING ? "cancel" : "demobilize";
            Button button = addRenderableWidget(Button.builder(Component.translatable("screen.villagediplomacy.garrison." + action),
                            ignored -> com.diplomacy.villagediplomacy.network.ResidentNetwork.sendAction(menu.containerId, entry.identity()))
                    .bounds(leftPos + 244, topPos + 58 + row * 22, 100, 20).build());
            button.active = snapshot.canManage() && entry.canAct()
                    && (entry.role() != GarrisonSavedData.Role.CIVILIAN || snapshot.room());
        }
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
        renderBackground(graphics); super.render(graphics, x, y, tick); renderTooltip(graphics, x, y);
    }
    @Override protected void renderBg(GuiGraphics g, float tick, int x, int y) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff3a2a1e);
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xffe3d2ad);
        for (var slot : menu.slots) {
            int sx = leftPos + slot.x, sy = topPos + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xff8d785d);
            g.fill(sx, sy, sx + 16, sy + 16, 0xffc5b18e);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) {
        var s = menu.getSnapshot();
        text(g, title.getString() + " — " + s.homeName(), 10, 8, 0xff2a1b12, 340);
        text(g, Component.translatable("screen.villagediplomacy.garrison.population", s.civilians(), s.trainees(), s.soldiers(), s.limit()).getString(), 10, 22, 0xff51402d, 340);
        text(g, Component.translatable("screen.villagediplomacy.garrison.rules").getString(), 10, 35, 0xff51402d, 340);
        String message = s.message().isBlank() ? (s.canManage() ? "supplies_hint" : "view_only") : s.message();
        text(g, Component.translatable("message.villagediplomacy.garrison." + message).getString(), 10, 47, 0xff375d30, 340);
        for (int row = 0; row < ROWS; row++) {
            int index = page * ROWS + row; if (index >= s.entries().size()) break;
            var entry = s.entries().get(index);
            String role = Component.translatable("role.villagediplomacy." + entry.role().name().toLowerCase(java.util.Locale.ROOT)).getString();
            if (entry.role() == GarrisonSavedData.Role.TRAINING) role += " · " + Component.translatable(
                    "screen.villagediplomacy.garrison.hours", (entry.remaining() + 999) / 1000).getString();
            text(g, entry.name().getString() + " · " + role, 14, 64 + row * 22, 0xff2a1b12, 222);
        }
        text(g, (page + 1) + "/" + Math.max(1, (s.entries().size() + ROWS - 1) / ROWS), 69, 132, 0xff51402d, 25);
        text(g, Component.translatable("screen.villagediplomacy.garrison.inventory").getString(), 14, 145, 0xff51402d, 175);
        String[] labels = {"weapon", "armor", "food"};
        for (int i = 0; i < labels.length; i++) text(g, Component.translatable("screen.villagediplomacy.garrison." + labels[i]).getString(), 222 + i * 36, 153, 0xff51402d, 34);
        text(g, Component.translatable("screen.villagediplomacy.garrison.cost").getString(), 204, 190, 0xff51402d, 144);
    }
    private void text(GuiGraphics g, String value, int x, int y, int color, int width) {
        g.drawString(font, font.plainSubstrByWidth(value, width), x, y, color, false);
    }
}
