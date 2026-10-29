package com.ancientempires.registry;

import com.ancientempires.inventory.TradeOrderMenu;
import com.ancientempires.AncientEmpires;
import com.ancientempires.inventory.NpcMenu;
import com.ancientempires.inventory.NpcSpawnerMenu;
import com.ancientempires.inventory.WorksiteMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Container menus: the NPC inventory, the NPC spawner, and the worksite and Town
 * Hall storage. Their screens are registered on the client.
 */
public final class ModMenus
{
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, AncientEmpires.MOD_ID);

    public static final RegistryObject<MenuType<NpcMenu>> NPC_INVENTORY = MENUS.register("npc_inventory",
            () -> IForgeMenuType.create(NpcMenu::fromNetwork));
    /** A worksite's or Town Hall's store, with its supply slots. */
    public static final RegistryObject<MenuType<NpcSpawnerMenu>> NPC_SPAWNER = MENUS.register("npc_spawner",
            () -> IForgeMenuType.create(NpcSpawnerMenu::fromNetwork));
    public static final RegistryObject<MenuType<WorksiteMenu>> WORKSITE = MENUS.register("worksite",
            () -> IForgeMenuType.create(WorksiteMenu::fromNetwork));

    /** The Trade Order's goods editor, opened from the hand it is held in. */
    public static final RegistryObject<MenuType<TradeOrderMenu>> TRADE_ORDER = MENUS.register("trade_order",
            () -> IForgeMenuType.create(TradeOrderMenu::fromNetwork));

    private ModMenus()
    {
    }
}
