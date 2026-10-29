package com.ancientempires.network;

import com.ancientempires.client.BlockOutlines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Server to client: outline these blocks for a while, seen through walls (a structure's flag, the spawners holding it). */
public class OutlinePacket
{
    /** The most blocks one packet outlines. */
    public static final int MAX = 64;

    /** What is outlined: its colour and height (a flag stands taller than its block). */
    public enum Kind
    {
        FLAG(1.0F, 0.8F, 0.2F, 0.75D),
        SPAWNER(1.0F, 0.25F, 0.2F, 0.0D);

        public final float red;
        public final float green;
        public final float blue;
        public final double extraHeight;

        Kind(float red, float green, float blue, double extraHeight)
        {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.extraHeight = extraHeight;
        }
    }

    private final List<BlockPos> blocks;
    private final Kind kind;

    public OutlinePacket(List<BlockPos> blocks, Kind kind)
    {
        this.blocks = blocks.size() > MAX ? blocks.subList(0, MAX) : blocks;
        this.kind = kind;
    }

    public static void encode(OutlinePacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeEnum(packet.kind);
        buffer.writeCollection(packet.blocks, FriendlyByteBuf::writeBlockPos);
    }

    public static OutlinePacket decode(FriendlyByteBuf buffer)
    {
        Kind kind = buffer.readEnum(Kind.class);
        return new OutlinePacket(buffer.readList(FriendlyByteBuf::readBlockPos), kind);
    }

    public static void handle(OutlinePacket packet, Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BlockOutlines.show(packet.blocks, packet.kind));
    }
}
