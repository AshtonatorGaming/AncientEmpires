package com.ancientempires.network;

import com.ancientempires.entity.npc.NpcShop;
import com.ancientempires.client.TradeScreen;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcFactions;
import com.ancientempires.entity.npc.NpcStandings;
import com.ancientempires.entity.npc.NpcTrades;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Server to client: a trader's goods, opening the trade screen ({@code open})
 * or refreshing it after a trade. Client to server goes by {@link Request}.
 */
public class TradePacket
{
    /** Blocks within which a player may trade (AW's container reach). */
    private static final double REACH = 8.0D;

    private final int entityId;
    private final boolean open;
    private final List<NpcTrades.View> trades;

    public TradePacket(int entityId, boolean open, List<NpcTrades.View> trades)
    {
        this.entityId = entityId;
        this.open = open;
        this.trades = trades;
    }

    /** Opens {@code trader}'s goods for {@code player}, unless its faction is hostile to them. */
    public static void open(ServerPlayer player, NpcEntity trader)
    {
        if (refused(player, trader))
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.trade.refused", trader.getDisplayName()), true);
            return;
        }
        ModNetwork.send(player, new TradePacket(trader.getId(), true, views(player, trader)));
    }

    /** The goods: a player's trader's from its order and backpack, else the trader's own stock, brought up to date. */
    private static List<NpcTrades.View> views(ServerPlayer player, NpcEntity trader)
    {
        if (NpcShop.isShop(trader))
        {
            return NpcShop.views(trader);
        }
        trader.getTrades().update(player.level().getGameTime());
        return trader.getTrades().views();
    }

    private static boolean refused(ServerPlayer player, NpcEntity trader)
    {
        String faction = trader.getFaction();
        return !faction.isEmpty() && NpcStandings.get(player.server).hostile(player.getUUID(), faction)
                || trader.getTarget() != null || trader.getMorale().isBroken();
    }

    public static void encode(TradePacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(packet.entityId);
        buffer.writeBoolean(packet.open);
        buffer.writeCollection(packet.trades, NpcTrades.View::write);
    }

    public static TradePacket decode(FriendlyByteBuf buffer)
    {
        return new TradePacket(buffer.readVarInt(), buffer.readBoolean(), buffer.readList(NpcTrades.View::read));
    }

    public static void handle(TradePacket packet, Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TradeScreen.show(packet.entityId, packet.open, packet.trades));
    }

    /** Client to server: make trade {@code index} with the trader. */
    public record Request(int entityId, int index)
    {
        public static void encode(Request request, FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(request.entityId);
            buffer.writeVarInt(request.index);
        }

        public static Request decode(FriendlyByteBuf buffer)
        {
            return new Request(buffer.readVarInt(), buffer.readVarInt());
        }

        /** The trade, checked again here: in reach, not hostile, in stock, and the player carrying the price. */
        public static void handle(Request request, Supplier<NetworkEvent.Context> context)
        {
            ServerPlayer player = context.get().getSender();
            if (player == null || !(player.level().getEntity(request.entityId) instanceof NpcEntity trader) || !trader.isAlive()
                    || player.distanceToSqr(trader) > REACH * REACH || refused(player, trader))
            {
                return;
            }
            if (NpcShop.isShop(trader))
            {
                if (NpcShop.perform(trader, player, request.index))
                {
                    trader.swing(trader.getUsedItemHand());
                }
            }
            else if (trader.getTrades().perform(player, request.index, player.level().getGameTime()))
            {
                String faction = trader.getFaction();
                int change = faction.isEmpty() ? 0 : NpcFactions.tradeChange(faction);
                if (change != 0)
                {
                    NpcStandings.get(player.server).adjust(player.getUUID(), faction, change);
                }
                trader.swing(trader.getUsedItemHand());
            }
            ModNetwork.send(player, new TradePacket(trader.getId(), false, views(player, trader)));
        }
    }
}
