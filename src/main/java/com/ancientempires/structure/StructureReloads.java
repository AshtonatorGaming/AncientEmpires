package com.ancientempires.structure;

import com.ancientempires.AncientEmpires;
import com.ancientempires.worldgen.TerritorySettings;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reads the structure templates' index and the remap tables with every datapack reload. */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class StructureReloads
{
    private StructureReloads()
    {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event)
    {
        event.addListener(new AeRemap());
        event.addListener(new AeTemplates());
        event.addListener(new TerritorySettings());
    }
}
