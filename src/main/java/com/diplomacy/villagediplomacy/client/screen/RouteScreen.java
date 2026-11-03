package com.diplomacy.villagediplomacy.client.screen;

import com.diplomacy.villagediplomacy.menu.RouteMenu;
import com.diplomacy.villagediplomacy.menu.RouteSnapshot;
import com.diplomacy.villagediplomacy.network.DiplomacyNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class RouteScreen extends AbstractContainerScreen<RouteMenu> {
    private int selection;
    private boolean closing;
    private EditBox name;
    private Button create;
    private Button map;
    private Button rename;
    private Button close;

    public RouteScreen(RouteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 238;
        inventoryLabelY = 10_000;
        selection = menu.selected();
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), ignored -> select(-1))
                .bounds(leftPos + 8, topPos + 28, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), ignored -> select(1))
                .bounds(leftPos + 270, topPos + 28, 22, 20).build());
        name = addRenderableWidget(new EditBox(font, leftPos + 12, topPos + 155, 276, 20,
                Component.translatable("screen.villagediplomacy.routes.name")));
        name.setMaxLength(64);
        create = button("create", 12, 182, 136, () -> send(menu.snapshot().signpost() ? RouteMenu.Action.BIND : RouteMenu.Action.CREATE));
        map = button("map", 152, 182, 136, () -> send(RouteMenu.Action.MAP));
        rename = button("rename", 12, 206, 136, () -> send(RouteMenu.Action.RENAME));
        close = button("close", 152, 206, 136, () -> {
            if (closing) {
                send(RouteMenu.Action.CLOSE);
            } else {
                closing = true;
                close.setMessage(Component.translatable("screen.villagediplomacy.routes.confirm_close"));
            }
        });
        updateControls();
    }

    private Button button(String key, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.translatable("screen.villagediplomacy.routes." + key),
                ignored -> action.run()).bounds(leftPos + x, topPos + y, width, 20).build());
    }

    private void select(int delta) {
        int count = menu.snapshot().targets().size();
        selection = count == 0 ? 0 : Math.floorMod(selection + delta, count);
        closing = false;
        updateControls();
    }

    private void updateControls() {
        boolean hasTarget = !menu.snapshot().targets().isEmpty();
        boolean sign = menu.snapshot().signpost();
        boolean active = hasTarget && menu.snapshot().targets().get(selection).active();
        boolean manage = hasTarget && menu.snapshot().canManage();
        create.active = manage && (sign || !active);
        create.setMessage(Component.translatable("screen.villagediplomacy.routes." + (sign ? "bind" : "create")));
        map.visible = rename.visible = close.visible = name.visible = !sign;
        map.active = rename.active = close.active = manage && active;
        close.setMessage(Component.translatable("screen.villagediplomacy.routes.close"));
        if (hasTarget) {
            var target = menu.snapshot().targets().get(selection);
            String routeName = target.routeName().isBlank() ? menu.snapshot().homeName() + " -> " + target.name() : target.routeName();
            name.setValue(routeName.substring(0, Math.min(64, routeName.length())));
        }
    }

    private void send(RouteMenu.Action action) {
        if (!menu.snapshot().targets().isEmpty()) {
            create.active = map.active = rename.active = close.active = false;
            DiplomacyNetwork.sendRouteAction(menu.containerId, selection, action, name.getValue());
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (name.isFocused() && key != 256) {
            return name.keyPressed(key, scan, modifiers) || name.canConsumeInput();
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float tick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF3A2A1E);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFFE3D2AD);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        line(graphics, 9, Component.translatable("screen.villagediplomacy.routes.home", menu.snapshot().homeName()), false);
        if (menu.snapshot().targets().isEmpty()) {
            line(graphics, 60, Component.translatable("screen.villagediplomacy.routes.empty"), false);
            return;
        }
        RouteSnapshot.Target target = menu.snapshot().targets().get(selection);
        String label = font.plainSubstrByWidth((selection + 1) + "/" + menu.snapshot().targets().size() + " " + target.name(), 228);
        graphics.drawString(font, label, 36, 34, target.allied() ? 0x174F1B : 0x241B12, false);
        line(graphics, 58, Component.translatable("screen.villagediplomacy.routes.position", target.center().toShortString()), target.allied());
        line(graphics, 73, Component.translatable("screen.villagediplomacy.routes.bearing", target.distance(),
                Component.translatable("direction.villagediplomacy." + target.bearing())), target.allied());
        line(graphics, 88, Component.translatable("screen.villagediplomacy.routes.relations",
                Component.translatable("relation.villagediplomacy." + target.relation()),
                Component.translatable("status.villagediplomacy.trade." + target.trade())), false);
        line(graphics, 103, Component.translatable("screen.villagediplomacy.routes.overview", target.routeName(),
                Component.translatable("status.villagediplomacy.route." + (!target.active() ? "closed" : target.marked() ? "marked" : "active"))), target.allied());
        line(graphics, 118, Component.translatable("screen.villagediplomacy.routes.deliveries", target.deliveries(),
                Component.translatable("screen.villagediplomacy.table." + (target.allied() ? "yes" : "no"))), target.allied());
        line(graphics, 136, Component.translatable("screen.villagediplomacy.routes." +
                (menu.snapshot().signpost() ? "sign_hint" : "name")), false);
    }

    private void line(GuiGraphics graphics, int y, Component value, boolean allied) {
        graphics.drawString(font, font.plainSubstrByWidth(value.getString(), 276), 12, y,
                allied ? 0x174F1B : 0x241B12, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, tick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
