package com.diplomacy.villagediplomacy.client.screen;

import com.diplomacy.villagediplomacy.menu.DiplomacyTableMenu;
import com.diplomacy.villagediplomacy.menu.DiplomacyTableSnapshot;
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

public final class DiplomacyTableScreen extends AbstractContainerScreen<DiplomacyTableMenu> {
    private static final int PANEL_COLOR = 0xFFE3D2AD;
    private static final int PANEL_DARK = 0xFF3A2A1E;
    private static final int TEXT_COLOR = 0xFF2A1B12;
    private static final int MUTED_COLOR = 0xFF6A5648;
    private static final int MAX_LINES = 11;

    private Tab selectedTab = Tab.SETTLEMENT;
    private int scrollOffset;

    public DiplomacyTableScreen(DiplomacyTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 220;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        int tabWidth = 68;
        for (int index = 0; index < Tab.values().length; index++) {
            Tab tab = Tab.values()[index];
            addRenderableWidget(Button.builder(
                            Component.translatable(tab.translationKey),
                            ignored -> selectTab(tab))
                    .bounds(leftPos + 8 + index * (tabWidth + 4), topPos + 28, tabWidth, 20)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_DARK);
        guiGraphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, PANEL_COLOR);
        guiGraphics.fill(leftPos + 7, topPos + 52, leftPos + imageWidth - 7, topPos + imageHeight - 8, 0x55FFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        DiplomacyTableSnapshot snapshot = menu.getSnapshot();
        guiGraphics.drawString(font, title, 8, 9, TEXT_COLOR, false);
        Component access = Component.translatable(snapshot.canManage()
                ? "screen.villagediplomacy.table.access.manage"
                : "screen.villagediplomacy.table.access.view");
        guiGraphics.drawString(font, access, imageWidth - 8 - font.width(access), 9,
                snapshot.canManage() ? 0xFF236523 : 0xFF765A25, false);

        renderLines(guiGraphics, currentLines(snapshot));
    }

    private List<Component> currentLines(DiplomacyTableSnapshot snapshot) {
        return switch (selectedTab) {
            case SETTLEMENT -> settlementLines(snapshot);
            case RELATIONS -> relationLines(snapshot);
            case TRADE -> tradeLines(snapshot);
            case ROUTES -> routeLines(snapshot);
        };
    }

    private void renderLines(GuiGraphics guiGraphics, List<Component> lines) {
        int rows = lines.size() > MAX_LINES ? MAX_LINES - 1 : MAX_LINES;
        int maximumOffset = Math.max(0, lines.size() - rows);
        scrollOffset = Math.min(scrollOffset, maximumOffset);
        int shown = Math.min(rows, lines.size() - scrollOffset);
        for (int index = 0; index < shown; index++) {
            Component line = lines.get(index + scrollOffset);
            String clipped = font.plainSubstrByWidth(line.getString(), imageWidth - 28);
            guiGraphics.drawString(font, clipped, 14, 60 + index * 13,
                    index + scrollOffset == 0 ? TEXT_COLOR : MUTED_COLOR, false);
        }
        if (lines.size() > MAX_LINES) {
            Component position = Component.translatable(
                    "screen.villagediplomacy.table.scroll",
                    scrollOffset + 1,
                    scrollOffset + shown,
                    lines.size()
            );
            guiGraphics.drawString(font, position, 14, 60 + shown * 13, MUTED_COLOR, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<Component> lines = currentLines(menu.getSnapshot());
        int rows = lines.size() > MAX_LINES ? MAX_LINES - 1 : MAX_LINES;
        int maximumOffset = Math.max(0, lines.size() - rows);
        if (maximumOffset == 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int direction = delta > 0.0D ? -1 : 1;
        scrollOffset = Math.max(0, Math.min(maximumOffset, scrollOffset + direction));
        return true;
    }

    private void selectTab(Tab tab) {
        selectedTab = tab;
        scrollOffset = 0;
    }

    private List<Component> settlementLines(DiplomacyTableSnapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.villagediplomacy.table.settlement.name", snapshot.settlementName()));
        lines.add(Component.translatable("screen.villagediplomacy.table.settlement.center",
                snapshot.center().getX(), snapshot.center().getY(), snapshot.center().getZ(), snapshot.radius()));
        lines.add(Component.translatable("screen.villagediplomacy.table.settlement.leader",
                snapshot.leaderName().isBlank()
                        ? Component.translatable("screen.villagediplomacy.table.none")
                        : Component.literal(snapshot.leaderName())));
        Component primary = specialization(snapshot.primarySpecialization());
        if (snapshot.secondarySpecialization().isBlank()) {
            lines.add(Component.translatable("screen.villagediplomacy.table.settlement.specialization", primary));
        } else {
            lines.add(Component.translatable("screen.villagediplomacy.table.settlement.specializations",
                    primary, specialization(snapshot.secondarySpecialization())));
        }
        lines.add(Component.translatable("screen.villagediplomacy.table.settlement.wood", itemName(snapshot.localWood())));
        lines.add(Component.translatable("screen.villagediplomacy.table.production"));
        if (snapshot.production().isEmpty()) {
            lines.add(Component.translatable("screen.villagediplomacy.table.production.empty"));
        } else {
            snapshot.production().forEach(entry -> lines.add(Component.translatable(
                    "screen.villagediplomacy.table.production.entry",
                    itemName(entry.itemId()), decimal(entry.grossPerDay()), decimal(entry.tradePerDay())
            )));
        }
        lines.add(Component.translatable("screen.villagediplomacy.table.warehouse"));
        if (snapshot.warehouse().isEmpty()) {
            lines.add(Component.translatable("screen.villagediplomacy.table.empty"));
        } else {
            snapshot.warehouse().forEach(entry -> lines.add(Component.translatable(
                    "screen.villagediplomacy.table.stock",
                    itemName(entry.itemId()), entry.stored(), entry.reserved(), entry.capacity()
            )));
        }
        return lines;
    }

    private List<Component> relationLines(DiplomacyTableSnapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.villagediplomacy.table.relations.count", snapshot.relations().size()));
        if (snapshot.relations().isEmpty()) {
            lines.add(Component.translatable("screen.villagediplomacy.table.relations.empty"));
        } else {
            snapshot.relations().forEach(entry -> {
                lines.add(Component.translatable("screen.villagediplomacy.table.relation",
                        entry.settlementName(), entry.reputation(),
                        Component.translatable("relation.villagediplomacy." + entry.relation())));
                lines.add(Component.translatable("screen.villagediplomacy.table.relation.location",
                        entry.distanceBlocks(), specialization(entry.specialization()),
                        entry.leaderName().isBlank()
                                ? Component.translatable("screen.villagediplomacy.table.none")
                                : Component.literal(entry.leaderName())));
                lines.add(Component.translatable("screen.villagediplomacy.table.relation.details",
                        status("trade", entry.tradeAgreement()), status("alliance", entry.alliance()),
                        entry.successfulDeliveries(), entry.failedContracts()));
            });
        }
        return lines;
    }

    private List<Component> tradeLines(DiplomacyTableSnapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.villagediplomacy.table.contracts.count", snapshot.contracts().size()));
        if (snapshot.contracts().isEmpty()) {
            lines.add(Component.translatable("screen.villagediplomacy.table.contracts.empty"));
        } else {
            snapshot.contracts().forEach(contract -> {
                lines.add(Component.translatable("screen.villagediplomacy.table.contract",
                        contract.sourceName(), contract.destinationName(), contractStatus(contract.status()),
                        contract.rewardEmeralds()));
                for (DiplomacyTableSnapshot.GoodsEntry goods : contract.goods()) {
                    lines.add(Component.translatable("screen.villagediplomacy.table.goods",
                            itemName(goods.itemId()), goods.issued(), goods.delivered(), goods.required()));
                }
            });
        }
        return lines;
    }

    private List<Component> routeLines(DiplomacyTableSnapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.villagediplomacy.table.routes.count", snapshot.contracts().size()));
        if (snapshot.contracts().isEmpty()) {
            lines.add(Component.translatable("screen.villagediplomacy.table.routes.empty"));
        } else {
            snapshot.contracts().forEach(contract -> {
                long days = (contract.remainingTicks() + 23_999L) / 24_000L;
                lines.add(Component.translatable("screen.villagediplomacy.table.route",
                        contract.sourceName(), contract.destinationName()));
                lines.add(Component.translatable("screen.villagediplomacy.table.route.details",
                        contractStatus(contract.status()), contract.distanceBlocks(), contract.successfulDeliveries(),
                        status("trade", contract.tradeAgreement())));
                lines.add(Component.translatable("screen.villagediplomacy.table.route.delivery",
                        days,
                        contract.trial()
                                ? Component.translatable("screen.villagediplomacy.table.yes")
                                : Component.translatable("screen.villagediplomacy.table.no"),
                        Component.translatable("screen.villagediplomacy.table.none")));
            });
        }
        return lines;
    }

    private static Component specialization(String serializedName) {
        return Component.translatable("specialization.villagediplomacy." + serializedName.toLowerCase(Locale.ROOT));
    }

    private static Component status(String category, String serializedName) {
        return Component.translatable("status.villagediplomacy." + category + "." + serializedName);
    }

    private static Component contractStatus(String serializedName) {
        return Component.translatable("status.villagediplomacy.contract." + serializedName);
    }

    private static Component itemName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return Component.literal(itemId);
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null ? Component.literal(itemId) : item.getDescription();
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private enum Tab {
        SETTLEMENT("screen.villagediplomacy.table.tab.settlement"),
        RELATIONS("screen.villagediplomacy.table.tab.relations"),
        TRADE("screen.villagediplomacy.table.tab.trade"),
        ROUTES("screen.villagediplomacy.table.tab.routes");

        private final String translationKey;

        Tab(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
