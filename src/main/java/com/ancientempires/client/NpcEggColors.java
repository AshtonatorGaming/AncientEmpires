package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.item.NpcSpawnEggItem;
import com.ancientempires.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * Tints the scroll the way Ancient Warfare did. The colors live in
 * {@link NpcSpawnEggItem#getColor}, which Forge's own spawn egg handler also
 * reads, so the result does not depend on which handler registers last.
 * Registered at the lowest priority so ours is the last word on this bus.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NpcEggColors
{
    private static final int WHITE = 0xFFFFFFFF;

    private NpcEggColors()
    {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
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
        return egg.getColor(tint);
    }
}
