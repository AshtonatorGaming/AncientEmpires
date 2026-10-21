package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcRoster;
import com.ancientempires.item.NpcSpawnEggItem;
import com.ancientempires.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * Tints the scroll the way Ancient Warfare did. Layer 1 is the faction color.
 * Layer 2 is the wax on a known faction. Owned eggs stay white.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NpcEggColors
{
    private static final int WHITE = 0xFFFFFFFF;
    private static final int FACTION_TOP = 0xFFEF5757;

    private NpcEggColors()
    {
    }

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.Item event)
    {
        Item[] items = new Item[ModItems.SPAWN_EGGS.size()];
        for (int index = 0; index < items.length; index++)
        {
            RegistryObject<Item> egg = ModItems.SPAWN_EGGS.get(index);
            items[index] = egg.get();
        }
        event.register(NpcEggColors::color, items);
    }

    private static int color(ItemStack stack, int tint)
    {
        if (!(stack.getItem() instanceof NpcSpawnEggItem egg))
        {
            return WHITE;
        }
        NpcRoster.Entry entry = NpcRoster.byId(egg.rosterId());
        if (entry == null || entry.owned())
        {
            return WHITE;
        }
        if (tint == 1)
        {
            return entry.color();
        }
        if (tint == 2)
        {
            return FACTION_TOP;
        }
        return WHITE;
    }
}
