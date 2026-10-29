package com.ancientempires.client;

import com.ancientempires.network.ArmyPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** The last army list the server sent, for the command wheel's unit panel (refreshed each time it opens). */
@OnlyIn(Dist.CLIENT)
public final class ClientArmy
{
    private static List<ArmyPacket.Entry> latest = List.of();

    private ClientArmy()
    {
    }

    public static List<ArmyPacket.Entry> latest()
    {
        return latest;
    }

    static void set(List<ArmyPacket.Entry> entries)
    {
        latest = List.copyOf(entries);
    }
}
