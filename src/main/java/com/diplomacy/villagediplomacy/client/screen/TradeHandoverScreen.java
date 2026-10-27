package com.diplomacy.villagediplomacy.client.screen;

import com.diplomacy.villagediplomacy.menu.TradeHandoverMenu;
import com.diplomacy.villagediplomacy.menu.TradeHandoverSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TradeHandoverScreen extends AbstractContainerScreen<TradeHandoverMenu> {
    private static final int PANEL = 0xFFE3D2AD;
    private static final int BORDER = 0xFF3A2A1E;
    private static final int TEXT = 0xFF2A1B12;
    private static final int MUTED = 0xFF6A5648;
    private static final int MAX_LINES = 11;
    private int contractIndex;
    private int scrollOffset;
    private Button previousButton;
    private Button actionButton;
    private Button nextButton;

    public TradeHandoverScreen(TradeHandoverMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 240;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        previousButton = addRenderableWidget(Button.builder(
                        Component.literal("<"), ignored -> changeContract(-1))
                .bounds(leftPos + 48, topPos + 211, 28, 20)
                .build());
        actionButton = addRenderableWidget(Button.builder(
                        Component.empty(), ignored -> performAction())
                .bounds(leftPos + 80, topPos + 211, 160, 20)
                .build());
        nextButton = addRenderableWidget(Button.builder(
                        Component.literal(">"), ignored -> changeContract(1))
                .bounds(leftPos + 244, topPos + 211, 28, 20)
                .build());
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BORDER);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, PANEL);
        graphics.fill(leftPos + 8, topPos + 46, leftPos + imageWidth - 8, topPos + 204, 0x55FFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        TradeHandoverSnapshot snapshot = menu.getSnapshot();
        graphics.drawString(font, title, 10, 9, TEXT, false);
        Component settlement = Component.translatable(
                "screen.villagediplomacy.trade_handover.settlement", snapshot.settlementName());
        graphics.drawString(font, settlement, 10, 24, MUTED, false);
        Component counter = Component.translatable(
                "screen.villagediplomacy.trade_handover.counter",
                contractIndex + 1,
                snapshot.contracts().size());
        graphics.drawString(font, counter, imageWidth - 10 - font.width(counter), 24, MUTED, false);

        List<Component> lines = lines(selectedContract());
        int maximumOffset = Math.max(0, lines.size() - MAX_LINES);
        scrollOffset = Math.min(scrollOffset, maximumOffset);
        int shown = Math.min(MAX_LINES, lines.size() - scrollOffset);
        for (int index = 0; index < shown; index++) {
            Component line = lines.get(index + scrollOffset);
            String clipped = font.plainSubstrByWidth(line.getString(), imageWidth - 32);
            graphics.drawString(font, clipped, 16, 53 + index * 13,
                    index + scrollOffset == 0 ? TEXT : MUTED, false);
        }
        if (maximumOffset > 0) {
            Component position = Component.translatable(
                    "screen.villagediplomacy.table.scroll",
                    scrollOffset + 1,
                    scrollOffset + shown,
                    lines.size());
            graphics.drawString(font, position, 16, 196 - font.lineHeight, MUTED, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<Component> lines = lines(selectedContract());
        int maximumOffset = Math.max(0, lines.size() - MAX_LINES);
        if (maximumOffset == 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        scrollOffset = Math.max(0, Math.min(maximumOffset, scrollOffset + (delta > 0.0D ? -1 : 1)));
        return true;
    }

    private TradeHandoverSnapshot.ContractEntry selectedContract() {
        return menu.getSnapshot().contracts().get(contractIndex);
    }

    private void changeContract(int delta) {
        int count = menu.getSnapshot().contracts().size();
        contractIndex = Math.floorMod(contractIndex + delta, count);
        scrollOffset = 0;
        updateButtons();
    }

    private void updateButtons() {
        if (actionButton == null) {
            return;
        }
        TradeHandoverSnapshot.ContractEntry contract = selectedContract();
        boolean multiple = menu.getSnapshot().contracts().size() > 1;
        previousButton.visible = multiple;
        nextButton.visible = multiple;
        actionButton.active = contract.actionable();
        actionButton.setMessage(Component.translatable(actionKey(contract.phase(), contract.actionable())));
    }

    private void performAction() {
        if (Minecraft.getInstance().gameMode == null) {
            return;
        }
        int buttonId = menu.buttonIdForContract(selectedContract().contractId());
        if (buttonId >= 0) {
            actionButton.active = false;
            Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private List<Component> lines(TradeHandoverSnapshot.ContractEntry contract) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(
                "screen.villagediplomacy.trade_handover.route",
                contract.sourceName(), contract.destinationName()));
        lines.add(Component.translatable(
                "screen.villagediplomacy.trade_handover.type_phase",
                Component.translatable("type.villagediplomacy.contract." + contract.type()),
                Component.translatable(phaseKey(contract.phase()))));
        long days = (contract.remainingTicks() + 23_999L) / 24_000L;
        lines.add(Component.translatable(
                "screen.villagediplomacy.trade_handover.deadline_reward",
                days, contract.rewardEmeralds()));
        lines.add(Component.translatable("screen.villagediplomacy.trade_handover.outbound"));
        contract.outboundGoods().forEach(goods -> lines.add(goodsLine(goods)));
        if (!contract.returnGoods().isEmpty()) {
            lines.add(Component.translatable("screen.villagediplomacy.trade_handover.return"));
            contract.returnGoods().forEach(goods -> lines.add(goodsLine(goods)));
        }
        lines.add(Component.translatable("screen.villagediplomacy.trade_handover.confirmation"));
        return lines;
    }

    private static Component goodsLine(TradeHandoverSnapshot.GoodsEntry goods) {
        return Component.translatable(
                "screen.villagediplomacy.trade_handover.goods",
                itemName(goods.itemId()), goods.issued(), goods.delivered(), goods.required(), goods.inventory());
    }

    private static String actionKey(TradeHandoverSnapshot.Phase phase, boolean actionable) {
        if (!actionable) {
            return "screen.villagediplomacy.trade_handover.action.wait";
        }
        return "screen.villagediplomacy.trade_handover.action."
                + phase.name().toLowerCase(Locale.ROOT);
    }

    private static String phaseKey(TradeHandoverSnapshot.Phase phase) {
        return "screen.villagediplomacy.trade_handover.phase."
                + phase.name().toLowerCase(Locale.ROOT);
    }

    private static Component itemName(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            return Component.literal(value);
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null ? Component.literal(value) : item.getDescription();
    }
}
