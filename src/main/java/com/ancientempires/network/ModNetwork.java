package com.ancientempires.network;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcFactions;
import com.ancientempires.entity.npc.NpcStandings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * Packets the container button click cannot carry. Slot clicks stay on the menu.
 */
public final class ModNetwork
{
    /** Bump whenever a packet is added or changes shape, so mismatched clients are refused cleanly. */
    private static final String PROTOCOL = "2";
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
        CHANNEL.messageBuilder(StandingsRequestPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StandingsRequestPacket::encode)
                .decoder(StandingsRequestPacket::decode)
                .consumerMainThread(StandingsRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(StandingsPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StandingsPacket::encode)
                .decoder(StandingsPacket::decode)
                .consumerMainThread(StandingsPacket::handle)
                .add();
    }

    public static void requestStandings()
    {
        CHANNEL.sendToServer(new StandingsRequestPacket());
    }

    /** Sends a player their standing with every faction, which opens the standings screen. */
    public static void sendStandings(ServerPlayer player)
    {
        NpcStandings standings = NpcStandings.get(player.server);
        List<StandingsPacket.Entry> entries = new ArrayList<>();
        for (String faction : NpcFactions.ids())
        {
            entries.add(new StandingsPacket.Entry(faction, standings.get(player.getUUID(), faction)));
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StandingsPacket(entries));
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
