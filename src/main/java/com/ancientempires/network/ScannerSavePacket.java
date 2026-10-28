package com.ancientempires.network;

import com.ancientempires.item.ScannerItem;
import com.ancientempires.structure.StructureScanner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: save the held scanner's selection under this name. Creative operators only. */
public class ScannerSavePacket
{
    private static final int MAX_NAME = 64;

    private final InteractionHand hand;
    private final String name;

    public ScannerSavePacket(InteractionHand hand, String name)
    {
        this.hand = hand;
        this.name = name;
    }

    public static void encode(ScannerSavePacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeEnum(packet.hand);
        buffer.writeUtf(packet.name, MAX_NAME);
    }

    public static ScannerSavePacket decode(FriendlyByteBuf buffer)
    {
        return new ScannerSavePacket(buffer.readEnum(InteractionHand.class), buffer.readUtf(MAX_NAME));
    }

    public static void handle(ScannerSavePacket packet, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player == null || !player.isCreative() || !player.hasPermissions(2))
        {
            return;
        }
        ItemStack stack = player.getItemInHand(packet.hand);
        if (!(stack.getItem() instanceof ScannerItem) || !ScannerItem.ready(stack))
        {
            return;
        }
        player.sendSystemMessage(StructureScanner.scan((ServerLevel) player.level(), player, ScannerItem.pos(stack, "Pos1"), ScannerItem.pos(stack, "Pos2"),
                ScannerItem.pos(stack, "Key"), ScannerItem.facing(stack), packet.name));
    }
}
