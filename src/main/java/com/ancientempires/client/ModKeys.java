package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** The faction standings key (J) and the parry key (R), rebindable in Controls. */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModKeys
{
    public static final KeyMapping STANDINGS = new KeyMapping("key.ancientempires.standings",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.ancientempires");
    /** Parry with the weapon in hand (R by default). The server times it, as for NPCs. */
    public static final KeyMapping PARRY = new KeyMapping("key.ancientempires.parry",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.ancientempires");

    private ModKeys()
    {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event)
    {
        event.register(STANDINGS);
        event.register(PARRY);
    }

    @Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, value = Dist.CLIENT)
    public static final class Presses
    {
        private Presses()
        {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event)
        {
            if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null)
            {
                return;
            }
            while (PARRY.consumeClick())
            {
                if (Minecraft.getInstance().screen == null)
                {
                    ModNetwork.sendParry();
                }
            }
            while (STANDINGS.consumeClick())
            {
                if (Minecraft.getInstance().screen == null)
                {
                    ModNetwork.requestStandings();
                }
            }
        }
    }
}
