package com.ancientempires.network;

import com.ancientempires.entity.npc.NpcArmy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Client to server: a command wheel order for these unit numbers (none: all), aimed where the player looks. */
public class UnitOrderPacket
{
    private final Set<Integer> units;
    private final NpcArmy.Command command;

    public UnitOrderPacket(Set<Integer> units, NpcArmy.Command command)
    {
        this.units = units;
        this.command = command;
    }

    public static void encode(UnitOrderPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeEnum(packet.command);
        int mask = 0;
        for (int unit : packet.units)
        {
            mask |= unit >= 1 && unit <= 9 ? 1 << unit : 0;
        }
        buffer.writeVarInt(mask);
    }

    public static UnitOrderPacket decode(FriendlyByteBuf buffer)
    {
        NpcArmy.Command command = buffer.readEnum(NpcArmy.Command.class);
        int mask = buffer.readVarInt();
        Set<Integer> units = new HashSet<>();
        for (int unit = 1; unit <= 9; unit++)
        {
            if ((mask & 1 << unit) != 0)
            {
                units.add(unit);
            }
        }
        return new UnitOrderPacket(units, command);
    }

    public static void handle(UnitOrderPacket packet, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            NpcArmy.command(player, packet.units, packet.command);
            ModNetwork.sendArmy(player, false);
        }
    }
}
