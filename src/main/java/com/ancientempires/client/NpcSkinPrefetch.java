package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Starts loading an NPC's skin in the background as the NPC arrives on the
 * client, so its first frame does not read and decode the PNG on the render
 * thread. A mixed army is dozens of skins at once; that read was the hitch.
 * Vanilla's texture manager does the loading and registers the texture on the
 * main thread when it is ready, as it does for its own preloads.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NpcSkinPrefetch
{
    /** Skins already asked for this session, so each is preloaded once. */
    private static final Set<ResourceLocation> REQUESTED = new HashSet<>();

    private NpcSkinPrefetch()
    {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event)
    {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof NpcEntity npc))
        {
            return;
        }
        request(npc);
    }

    /** Preloads the NPC's current skin, once per skin. Also called when the synced skin changes. */
    public static void request(NpcEntity npc)
    {
        ResourceLocation skin = npc.getSkinTexture();
        if (skin != null && REQUESTED.add(skin))
        {
            Minecraft.getInstance().getTextureManager().preload(skin, Util.backgroundExecutor());
        }
    }
}
