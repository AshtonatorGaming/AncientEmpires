package com.ancientempires.network;

import com.ancientempires.client.FlagHighlight;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: outline this structure's flag for a while, seen through walls. */
public class FlagHighlightPacket
{
    private final BlockPos pos;

    public FlagHighlightPacket(BlockPos pos)
    {
        this.pos = pos;
    }

    public static void encode(FlagHighlightPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
    }

    public static FlagHighlightPacket decode(FriendlyByteBuf buffer)
    {
        return new FlagHighlightPacket(buffer.readBlockPos());
    }

    public static void handle(FlagHighlightPacket packet, Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FlagHighlight.show(packet.pos));
    }
}
