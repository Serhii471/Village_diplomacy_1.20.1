package com.diplomacy.villagediplomacy.core;

import com.diplomacy.villagediplomacy.VillageDiplomacyMod;
import com.diplomacy.villagediplomacy.menu.DiplomacyTableMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, VillageDiplomacyMod.MOD_ID);

    public static final RegistryObject<MenuType<DiplomacyTableMenu>> DIPLOMACY_TABLE = MENUS.register(
            "diplomacy_table",
            () -> IForgeMenuType.create(DiplomacyTableMenu::fromNetwork)
    );

    private ModMenus() {
    }
}
