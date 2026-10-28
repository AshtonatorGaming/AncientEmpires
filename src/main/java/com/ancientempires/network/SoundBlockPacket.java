package com.ancientempires.network;

import com.ancientempires.block.decor.AwSoundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: a sound block's new tune from its editor. Anyone within reach, as in AW. */
public class SoundBlockPacket
{
    private final BlockPos pos;
    private final CompoundTag tune;

    public SoundBlockPacket(BlockPos pos, CompoundTag tune)
    {
        this.pos = pos;
        this.tune = tune;
    }

    public static void encode(SoundBlockPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        buffer.writeNbt(packet.tune);
    }

    public static SoundBlockPacket decode(FriendlyByteBuf buffer)
    {
        BlockPos pos = buffer.readBlockPos();
        CompoundTag tune = buffer.readNbt();
        return new SoundBlockPacket(pos, tune == null ? new CompoundTag() : tune);
    }

    public static void handle(SoundBlockPacket packet, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player == null || player.distanceToSqr(packet.pos.getCenter()) > 64.0D || !player.level().isLoaded(packet.pos))
        {
            return;
        }
        if (player.level().getBlockEntity(packet.pos) instanceof AwSoundBlockEntity sound)
        {
            sound.setTune(packet.tune);
        }
    }
}
