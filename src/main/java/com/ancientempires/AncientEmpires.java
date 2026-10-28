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
import com.ancientempires.registry.ModLoot;
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
 * Ancient Empires: the mod's entry point. Registers the deferred registries
 * ({@code registry}), the network channel, the entity attributes, and both
 * configs (gameplay per world, visuals per client).
 *
 * <p>Each package has a {@code package-info.java} saying what it holds and how
 * it connects to the rest; {@code docs/architecture.md} walks through the main
 * flows (the NPC goal stack, combat, targeting, workers, structures, gates,
 * claims).
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
        ModLoot.ENTRY_TYPES.register(modBus);
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
