package com.ancientempires;

import com.ancientempires.combat.BetterCombatBridge;
import com.ancientempires.config.AncientEmpiresClientConfig;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.registry.ModBlockEntities;
import com.ancientempires.registry.ModBlocks;
import com.ancientempires.registry.ModCreativeTabs;
import com.ancientempires.registry.ModEntities;
import com.ancientempires.registry.ModItems;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Ancient Empires.
 *
 * <p>Packages:
 * config — ranges, wind-up, and cooldowns
 * registry — entity, spawn egg, and creative tab
 * entity.npc — the NPC, its inventory, and its owner
 * inventory — the NPC container menu
 * network — name and skin packets for the NPC screen
 * combat — attack profiles, the melee goal, the bow goal, and the optional Better Combat reader
 * client — the NPC renderer and Better Combat poses
 */
@Mod(AncientEmpires.MOD_ID)
public class AncientEmpires
{
    public static final String MOD_ID = "ancientempires";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AncientEmpires(FMLJavaModLoadingContext context)
    {
        IEventBus modBus = context.getModEventBus();

        ModEntities.ENTITIES.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModNetwork.register();

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::addCreative);

        // Gameplay settings are per world (serverconfig/ancientempires-server.toml) and synced to joining clients.
        context.registerConfig(ModConfig.Type.SERVER, AncientEmpiresConfig.SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, AncientEmpiresClientConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event)
    {
        LOGGER.info("Ancient Empires ready. Better Combat loaded: {}", ModList.get().isLoaded(BetterCombatBridge.MOD_ID));
    }

    private void registerAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.NPC.get(), NpcEntity.createAttributes().build());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS)
        {
            for (var egg : ModItems.SPAWN_EGGS)
            {
                event.accept(egg.get());
            }
        }
    }
}
