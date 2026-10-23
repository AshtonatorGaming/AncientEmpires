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
    private static final ForgeConfigSpec.BooleanValue SHOW_STATUS_ICONS;
    private static final ForgeConfigSpec.IntValue STATUS_ICON_RANGE;

    static
    {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SHOW_NAMETAGS = builder
                .comment("Show an NPC's custom name above it in the world. The inventory preview never draws that nametag.")
                .define("showNametags", false);
        SHOW_STATUS_ICONS = builder
                .comment("Show Ancient Warfare's task icon above an NPC: working, following, going home, sleeping, fighting, fleeing, hungry, and so on.")
                .define("showStatusIcons", true);
        STATUS_ICON_RANGE = builder
                .comment("Status icons show for NPCs within this many blocks.")
                .defineInRange("statusIconRange", 24, 4, 128);
        SPEC = builder.build();
    }

    /** Off until the client config loads and a player enables it. */
    public static boolean showNametags = false;
    public static boolean showStatusIcons = true;
    public static int statusIconRange = 24;

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
        showStatusIcons = SHOW_STATUS_ICONS.get();
        statusIconRange = STATUS_ICON_RANGE.get();
    }
}
