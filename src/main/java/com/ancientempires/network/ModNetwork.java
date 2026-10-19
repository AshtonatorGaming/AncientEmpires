package com.ancientempires.network;

import com.ancientempires.AncientEmpires;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Packets the container button click cannot carry. Slot clicks stay on the menu.
 */
public final class ModNetwork
{
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.parse(AncientEmpires.MOD_ID + ":main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);
    private static int nextId;

    private ModNetwork()
    {
    }

    public static void register()
    {
        CHANNEL.messageBuilder(NpcNamePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NpcNamePacket::encode)
                .decoder(NpcNamePacket::decode)
                .consumerMainThread(NpcNamePacket::handle)
                .add();
        CHANNEL.messageBuilder(NpcSkinPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NpcSkinPacket::encode)
                .decoder(NpcSkinPacket::decode)
                .consumerMainThread(NpcSkinPacket::handle)
                .add();
    }

    public static void sendName(int entityId, String name)
    {
        CHANNEL.sendToServer(new NpcNamePacket(entityId, cap(name, NpcNamePacket.MAX_LENGTH)));
    }

    public static void sendSkin(int entityId, String skin)
    {
        CHANNEL.sendToServer(new NpcSkinPacket(entityId, cap(skin, NpcSkinPacket.MAX_LENGTH)));
    }

    private static String cap(String value, int max)
    {
        String text = value == null ? "" : value;
        if (text.length() > max)
        {
            return text.substring(0, max);
        }
        return text;
    }
}
