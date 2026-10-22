package com.ancientempires.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: the standings key was pressed; the server answers with {@link StandingsPacket}. */
public class StandingsRequestPacket
{
    public static void encode(StandingsRequestPacket packet, FriendlyByteBuf buffer)
    {
    }

    public static StandingsRequestPacket decode(FriendlyByteBuf buffer)
    {
        return new StandingsRequestPacket();
    }

    public static void handle(StandingsRequestPacket packet, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            ModNetwork.sendStandings(player);
        }
    }
}
