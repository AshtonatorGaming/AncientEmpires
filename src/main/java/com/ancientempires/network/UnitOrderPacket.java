package com.ancientempires.network;

import com.ancientempires.entity.npc.NpcArmy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Client to server: a command wheel order for these unit numbers (none: all),
 * aimed where the player looks, or for Hold and Defend where the player placed
 * it (a spot or a dragged front line).
 */
public class UnitOrderPacket
{
    private final Set<Integer> units;
    private final NpcArmy.Command command;
    @Nullable
    private final NpcArmy.Placement placement;

    public UnitOrderPacket(Set<Integer> units, NpcArmy.Command command)
    {
        this(units, command, null);
    }

    public UnitOrderPacket(Set<Integer> units, NpcArmy.Command command, @Nullable NpcArmy.Placement placement)
    {
        this.units = units;
        this.command = command;
        this.placement = placement;
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
        buffer.writeBoolean(packet.placement != null);
        if (packet.placement != null)
        {
            writeVec(buffer, packet.placement.from());
            writeVec(buffer, packet.placement.to());
            buffer.writeFloat(packet.placement.facing());
        }
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
        NpcArmy.Placement placement = buffer.readBoolean() ? new NpcArmy.Placement(readVec(buffer), readVec(buffer), buffer.readFloat()) : null;
        return new UnitOrderPacket(units, command, placement);
    }

    private static void writeVec(FriendlyByteBuf buffer, Vec3 vec)
    {
        buffer.writeDouble(vec.x);
        buffer.writeDouble(vec.y);
        buffer.writeDouble(vec.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buffer)
    {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(UnitOrderPacket packet, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player != null)
        {
            NpcArmy.command(player, packet.units, packet.command, packet.placement);
            ModNetwork.sendArmy(player, false);
        }
    }
}
