package com.diplomacy.villagediplomacy.core;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VillageDiplomacyMod.MOD_ID);

    public static final RegistryObject<Item> DIPLOMATIC_SCROLL = ITEMS.register("diplomatic_scroll",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> LEADERSHIP_SEAL = ITEMS.register("leadership_seal",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> SEALED_TRADE_CRATE = ITEMS.register("sealed_trade_crate",
            SealedTradeCrateItem::new);

    public static final RegistryObject<Item> TRADE_ROUTE_MAP = ITEMS.register("trade_route_map",
            TradeRouteMapItem::new);

    public static final RegistryObject<Item> VILLAGE_GUARD_SPAWN_EGG = ITEMS.register("village_guard_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.VILLAGE_GUARD, 0x4f5d73, 0x8b3a3a, new Item.Properties()));

    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(DIPLOMATIC_SCROLL.get());
            event.accept(LEADERSHIP_SEAL.get());
            event.accept(SEALED_TRADE_CRATE.get());
            event.accept(TRADE_ROUTE_MAP.get());
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(VILLAGE_GUARD_SPAWN_EGG.get());
        }
    }
}
