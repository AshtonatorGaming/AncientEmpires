package com.ancientempires.network;

import com.ancientempires.block.WorksiteArea;
import com.ancientempires.block.WorksiteBlock;
import com.ancientempires.block.WorksiteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: a new area from the bounds screen. The server checks
 * reach, ownership, and the limits again, outlines the result, and sends the
 * state back, so a refused change snaps the screen back.
 */
public class WorksiteBoundsSetPacket
{
    private static final double MAX_DISTANCE_SQR = 12.0D * 12.0D;

    private final BlockPos pos;
    private final WorksiteArea area;

    public WorksiteBoundsSetPacket(BlockPos pos, WorksiteArea area)
    {
        this.pos = pos;
        this.area = area;
    }

    public static void encode(WorksiteBoundsSetPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.pos);
        packet.area.write(buffer);
    }

    public static WorksiteBoundsSetPacket decode(FriendlyByteBuf buffer)
    {
        return new WorksiteBoundsSetPacket(buffer.readBlockPos(), WorksiteArea.read(buffer));
    }

    public static void handle(WorksiteBoundsSetPacket packet, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player == null || player.distanceToSqr(packet.pos.getCenter()) > MAX_DISTANCE_SQR || !player.level().isLoaded(packet.pos))
        {
            return;
        }
        if (!(player.level().getBlockEntity(packet.pos) instanceof WorksiteBlockEntity site) || !WorksiteBlock.mayManage(player, site))
        {
            return;
        }
        if (site.setArea(packet.area))
        {
            WorksiteBlock.outline((ServerLevel) player.level(), site);
        }
        ModNetwork.sendWorksiteBounds(player, site);
    }
}
