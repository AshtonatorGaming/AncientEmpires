package com.ancientempires.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server to client: the player's standing with every faction, to show on the standings screen. */
public class StandingsPacket
{
    /** One faction: its id and the player's standing. */
    public record Entry(String faction, int standing)
    {
    }

    private static final int MAX_ENTRIES = 512;

    private final List<Entry> entries;

    public StandingsPacket(List<Entry> entries)
    {
        this.entries = entries;
    }

    public List<Entry> entries()
    {
        return this.entries;
    }

    public static void encode(StandingsPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries)
        {
            buffer.writeUtf(entry.faction(), 64);
            buffer.writeVarInt(entry.standing());
        }
    }

    public static StandingsPacket decode(FriendlyByteBuf buffer)
    {
        int count = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            entries.add(new Entry(buffer.readUtf(64), buffer.readVarInt()));
        }
        return new StandingsPacket(entries);
    }

    public static void handle(StandingsPacket packet, Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ancientempires.client.StandingsScreen.open(packet.entries()));
    }
}
