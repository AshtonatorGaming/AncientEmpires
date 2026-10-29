package com.ancientempires.client;

import com.ancientempires.network.ArmyPacket;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** The last army list the server sent, for the battle wheel's units bar and the War Table (refreshed each time either opens). */
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

    /** A name or rank sent as text component JSON, or the text itself if it is not JSON. */
    public static Component parse(String json)
    {
        try
        {
            Component parsed = Component.Serializer.fromJson(json);
            return parsed == null ? Component.literal(json) : parsed;
        }
        catch (RuntimeException exception)
        {
            return Component.literal(json);
        }
    }

    static void set(List<ArmyPacket.Entry> entries)
    {
        latest = List.copyOf(entries);
        ArmyPicks.refreshGlow();
    }
}
