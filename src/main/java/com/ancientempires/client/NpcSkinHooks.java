package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Skin-button hook. A cosmetic pack replaces {@link #setOpener} and sends a
 * texture id with {@code ModNetwork.sendSkin}. The world model and the preview
 * both read {@link NpcEntity#getSkinId()}.
 */
@OnlyIn(Dist.CLIENT)
public final class NpcSkinHooks
{
    @FunctionalInterface
    public interface Opener
    {
        void open(NpcEntity npc);
    }

    private static Opener opener = NpcSkinHooks::unavailable;

    private NpcSkinHooks()
    {
    }

    public static void setOpener(Opener opener)
    {
        NpcSkinHooks.opener = opener == null ? NpcSkinHooks::unavailable : opener;
    }

    public static void open(NpcEntity npc)
    {
        NpcSkinHooks.opener.open(npc);
    }

    private static void unavailable(NpcEntity npc)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null)
        {
            minecraft.player.displayClientMessage(Component.translatable("message.ancientempires.npc.skin_unavailable"), true);
        }
    }
}
