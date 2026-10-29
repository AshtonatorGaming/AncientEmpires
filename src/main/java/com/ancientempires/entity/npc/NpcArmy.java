package com.ancientempires.entity.npc;

import com.ancientempires.item.SignalFlagItem;
import com.ancientempires.network.ArmyPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The server side of the command screen (docs/chain-of-command.md, phase 4):
 * a player's army as the screen shows it (their soldiers loaded in their
 * dimension, nearest first), and the orders and changes the screen sends,
 * each checked against ownership before it is done.
 */
public final class NpcArmy
{
    /** The most soldiers one screen lists. */
    public static final int MAX = 256;
    /** A unit's name, at most this long. */
    public static final int NAME_LENGTH = 32;

    /** What the screen can ask for. */
    public enum Action
    {
        FOLLOW, FREE, HOLD, FALL_BACK, FORMATION, REJOIN, REMOVE, MOVE, RENAME, FLAG
    }

    private NpcArmy()
    {
    }

    /** The player's soldiers in their dimension, nearest first, as the screen lists them. */
    public static List<ArmyPacket.Entry> list(ServerPlayer player)
    {
        ServerLevel level = player.serverLevel();
        List<NpcEntity> soldiers = new ArrayList<>();
        for (Entity entity : level.getAllEntities())
        {
            if (entity instanceof NpcEntity npc && npc.isAlive() && npc.isOwnedNpc() && player.getUUID().equals(npc.getOwner())
                    && (npc.getNpcPath().combat() || npc.getChain().inChain()))
            {
                soldiers.add(npc);
            }
        }
        soldiers.sort(Comparator.comparingDouble(npc -> npc.distanceToSqr(player)));
        List<ArmyPacket.Entry> entries = new ArrayList<>();
        for (NpcEntity npc : soldiers.subList(0, Math.min(MAX, soldiers.size())))
        {
            NpcChain chain = npc.getChain();
            NpcOrder own = chain.order();
            entries.add(new ArmyPacket.Entry(npc.getUUID(), Component.Serializer.toJson(npc.getDisplayName()), npc.getRank().ordinal(),
                    npc.getLevel(), npc.getHealth() / Math.max(1.0F, npc.getMaxHealth()), chain.effectiveOrder().ordinal(), own != null,
                    chain.formation().ordinal(), chain.leaderId(), chain.members().size(), (float) Math.sqrt(npc.distanceToSqr(player)),
                    chain.name(), npc.isHoldingBow(), npc.isPassenger(), Component.Serializer.toJson(npc.getRankName())));
        }
        return entries;
    }

    /** Carries out one of the screen's actions, if the player owns the soldiers involved. */
    public static void act(ServerPlayer player, Action action, UUID subject, @Nullable UUID other, String text)
    {
        NpcEntity npc = owned(player, subject);
        if (npc == null)
        {
            return;
        }
        NpcChain chain = npc.getChain();
        switch (action)
        {
            case FOLLOW -> chain.setOrder(NpcOrder.FOLLOW, null, Float.NaN, null);
            case FREE -> chain.setOrder(NpcOrder.FREE, null, Float.NaN, null);
            case HOLD -> chain.setOrder(NpcOrder.HOLD, npc.blockPosition(), chain.facing(), null);
            case FALL_BACK -> chain.setOrder(NpcOrder.FALL_BACK, player.blockPosition(), player.getYRot(), null);
            case FORMATION -> chain.setFormation(chain.formation().next());
            case REJOIN -> chain.setOrder(NpcOrder.FOLLOW, null, Float.NaN, null);
            case REMOVE -> chain.leave();
            case MOVE ->
            {
                NpcEntity leader = other == null ? null : owned(player, other);
                if (leader != null && !leader.getChain().members().contains(npc.getUUID()))
                {
                    NpcChain.Assign result = NpcChain.toggle(leader, npc);
                    if (result != NpcChain.Assign.ADDED)
                    {
                        player.displayClientMessage(Component.translatable("message.ancientempires.signal." + result.name().toLowerCase(Locale.ROOT),
                                npc.getDisplayName()), true);
                    }
                }
            }
            case RENAME -> chain.setName(text.length() > NAME_LENGTH ? text.substring(0, NAME_LENGTH) : text);
            case FLAG ->
            {
                ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
                if (held.getItem() instanceof SignalFlagItem)
                {
                    SignalFlagItem.select(held, npc);
                    player.displayClientMessage(Component.translatable("message.ancientempires.army.flag", npc.getDisplayName()), true);
                }
                else
                {
                    player.displayClientMessage(Component.translatable("message.ancientempires.army.no_flag"), true);
                }
            }
        }
    }

    @Nullable
    private static NpcEntity owned(ServerPlayer player, UUID id)
    {
        return player.serverLevel().getEntity(id) instanceof NpcEntity npc && npc.isAlive() && npc.isOwnedNpc()
                && player.getUUID().equals(npc.getOwner()) ? npc : null;
    }
}
