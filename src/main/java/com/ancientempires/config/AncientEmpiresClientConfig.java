package com.ancientempires.config;

import com.ancientempires.AncientEmpires;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Client preferences. Nametags stay off until a player turns them on here.
 * The NPC inventory preview never draws one, even when this is enabled.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AncientEmpiresClientConfig
{
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue SHOW_NAMETAGS;

    static
    {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SHOW_NAMETAGS = builder
                .comment("Show an NPC's custom name above it in the world. The inventory preview never draws that nametag.")
                .define("showNametags", false);
        SPEC = builder.build();
    }

    /** Off until the client config loads and a player enables it. */
    public static boolean showNametags = false;

    private AncientEmpiresClientConfig()
    {
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event)
    {
        if (event.getConfig().getSpec() != SPEC)
        {
            return;
        }
        showNametags = SHOW_NAMETAGS.get();
    }
}
