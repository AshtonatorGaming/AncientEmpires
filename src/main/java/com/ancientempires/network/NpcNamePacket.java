package com.ancientempires.network;

import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sets or clears the NPC's custom name. Sent when the name field loses focus.
 */
public class NpcNamePacket
{
    public static final int MAX_LENGTH = 32;

    private final int entityId;
    private final String name;

    public NpcNamePacket(int entityId, String name)
    {
        this.entityId = entityId;
        this.name = name;
    }

    public static void encode(NpcNamePacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(packet.entityId);
        buffer.writeUtf(packet.name, MAX_LENGTH);
    }

    public static NpcNamePacket decode(FriendlyByteBuf buffer)
    {
        return new NpcNamePacket(buffer.readVarInt(), buffer.readUtf(MAX_LENGTH));
    }

    public static void handle(NpcNamePacket packet, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player == null)
        {
            return;
        }
        Entity entity = player.level().getEntity(packet.entityId);
        if (entity instanceof NpcEntity npc)
        {
            npc.applyCustomName(player, packet.name);
        }
    }
}
