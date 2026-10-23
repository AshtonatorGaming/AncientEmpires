package com.ancientempires.network;

import com.ancientempires.block.WorksiteArea;
import com.ancientempires.block.WorksiteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: a worksite's area and its limits. Opens the bounds screen,
 * or refreshes it when it is already open on that worksite.
 */
public class WorksiteBoundsPacket
{
    /** What the bounds screen shows and checks a change against. */
    public record State(BlockPos pos, WorksiteArea area, int maxSize, int reach, boolean mayContainBlock, boolean locked, int tier,
            WorksiteBlockEntity.UpgradeKind kind)
    {
    }

    private final State state;

    public WorksiteBoundsPacket(State state)
    {
        this.state = state;
    }

    public static void encode(WorksiteBoundsPacket packet, FriendlyByteBuf buffer)
    {
        State state = packet.state;
        buffer.writeBlockPos(state.pos());
        state.area().write(buffer);
        buffer.writeVarInt(state.maxSize());
        buffer.writeVarInt(state.reach());
        buffer.writeBoolean(state.mayContainBlock());
        buffer.writeBoolean(state.locked());
        buffer.writeVarInt(state.tier());
        buffer.writeEnum(state.kind());
    }

    public static WorksiteBoundsPacket decode(FriendlyByteBuf buffer)
    {
        return new WorksiteBoundsPacket(new State(buffer.readBlockPos(), WorksiteArea.read(buffer), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readEnum(WorksiteBlockEntity.UpgradeKind.class)));
    }

    public static void handle(WorksiteBoundsPacket packet, Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ancientempires.client.WorksiteBoundsScreen.show(packet.state));
    }
}
