package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.inventory.NpcMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus
{
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, AncientEmpires.MOD_ID);

    public static final RegistryObject<MenuType<NpcMenu>> NPC_INVENTORY = MENUS.register("npc_inventory",
            () -> IForgeMenuType.create(NpcMenu::fromNetwork));

    private ModMenus()
    {
    }
}
