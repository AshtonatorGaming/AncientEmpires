package com.ancientempires.network;

import com.ancientempires.client.DraftingScreen;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.item.BlueprintItem;
import com.ancientempires.registry.ModBlocks;
import com.ancientempires.structure.AeBuilds;
import com.ancientempires.structure.AeTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Server to client: the templates a Drafting Station can draft, opening its
 * screen. Client to server goes by {@link Request}: draft one.
 */
public class DraftingPacket
{
    private static final double REACH = 8.0D;

    private final BlockPos station;
    private final List<AeBuilds.Entry> entries;

    public DraftingPacket(BlockPos station, List<AeBuilds.Entry> entries)
    {
        this.station = station;
        this.entries = entries;
    }

    public static void open(ServerPlayer player, BlockPos station)
    {
        ModNetwork.send(player, new DraftingPacket(station, AeBuilds.survival()));
    }

    public static void encode(DraftingPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(packet.station);
        buffer.writeCollection(packet.entries, (out, entry) ->
        {
            out.writeUtf(entry.name());
            out.writeUtf(entry.category());
            out.writeVarInt(entry.sizeX());
            out.writeVarInt(entry.sizeY());
            out.writeVarInt(entry.sizeZ());
        });
    }

    public static DraftingPacket decode(FriendlyByteBuf buffer)
    {
        BlockPos station = buffer.readBlockPos();
        List<AeBuilds.Entry> entries = buffer.readList(in -> new AeBuilds.Entry(in.readUtf(), in.readUtf(), in.readVarInt(), in.readVarInt(), in.readVarInt()));
        return new DraftingPacket(station, entries);
    }

    public static void handle(DraftingPacket packet, Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DraftingScreen.show(packet.station, packet.entries));
    }

    /** Client to server: draft a Blueprint of {@code template} at the station. */
    public record Request(BlockPos station, String template)
    {
        public static void encode(Request request, FriendlyByteBuf buffer)
        {
            buffer.writeBlockPos(request.station);
            buffer.writeUtf(request.template);
        }

        public static Request decode(FriendlyByteBuf buffer)
        {
            return new Request(buffer.readBlockPos(), buffer.readUtf());
        }

        /** Checked again here: at a station in reach, a survival template, and the paper paid (not in creative). */
        public static void handle(Request request, Supplier<NetworkEvent.Context> context)
        {
            ServerPlayer player = context.get().getSender();
            if (player == null || !player.level().getBlockState(request.station).is(ModBlocks.DRAFTING_STATION.get())
                    || player.distanceToSqr(request.station.getCenter()) > REACH * REACH)
            {
                return;
            }
            ResourceLocation id = AeTemplates.byName(request.template);
            if (id == null || !AeBuilds.isSurvival(id))
            {
                return;
            }
            int paper = AncientEmpiresConfig.structuresBlueprintPaper;
            if (!player.getAbilities().instabuild)
            {
                if (player.getInventory().countItem(Items.PAPER) < paper)
                {
                    player.displayClientMessage(Component.translatable("message.ancientempires.drafting.paper", paper), true);
                    return;
                }
                player.getInventory().clearOrCountMatchingItems(stack -> stack.is(Items.PAPER), paper, player.inventoryMenu.getCraftSlots());
            }
            ItemStack blueprint = BlueprintItem.of(AeTemplates.name(id));
            if (!player.getInventory().add(blueprint))
            {
                player.drop(blueprint, false);
            }
            player.displayClientMessage(Component.translatable("message.ancientempires.drafting.drafted", AeTemplates.name(id)), true);
        }
    }
}
