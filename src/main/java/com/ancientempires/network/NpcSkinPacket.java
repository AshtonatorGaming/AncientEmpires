package com.ancientempires.network;

import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Applies a catalog skin id. The inventory skin selector sends this.
 */
public class NpcSkinPacket
{
    public static final int MAX_LENGTH = 128;

    private final int entityId;
    private final String skin;

    public NpcSkinPacket(int entityId, String skin)
    {
        this.entityId = entityId;
        this.skin = skin;
    }

    public static void encode(NpcSkinPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(packet.entityId);
        buffer.writeUtf(packet.skin, MAX_LENGTH);
    }

    public static NpcSkinPacket decode(FriendlyByteBuf buffer)
    {
        return new NpcSkinPacket(buffer.readVarInt(), buffer.readUtf(MAX_LENGTH));
    }

    public static void handle(NpcSkinPacket packet, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player == null)
        {
            return;
        }
        Entity entity = player.level().getEntity(packet.entityId);
        if (entity instanceof NpcEntity npc)
        {
            npc.applySkin(player, packet.skin);
        }
    }
}
