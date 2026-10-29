package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcEntity;
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

/** The faction standings key (J by default), the War Table's (K), and the battle wheel's (V, held), rebindable in Controls. */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModKeys
{
    public static final KeyMapping STANDINGS = new KeyMapping("key.ancientempires.standings",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.ancientempires");
    public static final KeyMapping COMMAND = new KeyMapping("key.ancientempires.command",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.ancientempires");
    /** Held: the command wheel (Mount & Blade's battle orders). */
    public static final KeyMapping ORDERS = new KeyMapping("key.ancientempires.orders",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.ancientempires");

    private ModKeys()
    {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event)
    {
        event.register(STANDINGS);
        event.register(COMMAND);
        event.register(ORDERS);
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
            while (STANDINGS.consumeClick())
            {
                if (Minecraft.getInstance().screen == null)
                {
                    ModNetwork.requestStandings();
                }
            }
            while (ORDERS.consumeClick())
            {
                if (PlacementMode.active())
                {
                    // The order key again while placing calls it off.
                    PlacementMode.cancel();
                }
                else if (Minecraft.getInstance().screen == null)
                {
                    BattleWheelScreen.open();
                }
            }
            ArmyPicks.tick();
            while (COMMAND.consumeClick())
            {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.screen == null)
                {
                    // Looking at one of your soldiers: the War Table opens with it in hand.
                    WarTableScreen.carryNext(minecraft.crosshairPickEntity instanceof NpcEntity npc && minecraft.player.getUUID().equals(npc.getOwner())
                            ? npc.getUUID() : null);
                    ModNetwork.requestArmy();
                }
            }
        }
    }
}
