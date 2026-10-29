package com.ancientempires.entity.npc;

import java.util.stream.Collectors;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import com.ancientempires.config.AncientEmpiresConfig;
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
        FOLLOW, FREE, HOLD, FALL_BACK, FORMATION, REJOIN, REMOVE, MOVE, RENAME, FLAG, NUMBER
    }

    /** The command wheel's orders (Mount & Blade's): given to the selected units, aimed where the player looks. */
    public enum Command
    {
        FOLLOW, HOLD, DEFEND, CHARGE, FALL_BACK, FREE, FORMATION
    }

    /** How far the command wheel's aim reaches, in blocks. */
    private static final double AIM_RANGE = 96.0D;

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
                    chain.name(), npc.isHoldingBow(), npc.isPassenger(), Component.Serializer.toJson(npc.getRankName()), chain.unitNumber()));
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
            case NUMBER -> chain.setUnitNumber((chain.unitNumber() + 1) % 10);
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

    /** The lowest unit number 1 to 9 none of the owner's units here has (1 if all are taken). */
    static int freeNumber(ServerLevel level, UUID owner)
    {
        boolean[] taken = new boolean[10];
        for (Entity entity : level.getAllEntities())
        {
            if (entity instanceof NpcEntity npc && owner.equals(npc.getOwner()))
            {
                taken[npc.getChain().unitNumber()] = true;
            }
        }
        for (int number = 1; number <= 9; number++)
        {
            if (!taken[number])
            {
                return number;
            }
        }
        return 1;
    }

    /**
     * An order from the command wheel to the player's units with these
     * numbers (all of them for none), aimed along the player's look: Hold and
     * Defend at the block looked at (several units side by side across it,
     * facing the player's way), Charge at the creature looked at (else a free
     * charge), Fall back to the player, Follow, Free, or the next formation.
     */
    public static void command(ServerPlayer player, Set<Integer> units, Command command)
    {
        ServerLevel level = player.serverLevel();
        List<NpcEntity> leaders = new ArrayList<>();
        for (Entity entity : level.getAllEntities())
        {
            if (entity instanceof NpcEntity npc && npc.isAlive() && player.getUUID().equals(npc.getOwner()) && npc.getChain().unitNumber() > 0
                    && npc.getChain().leads() && (units.isEmpty() || units.contains(npc.getChain().unitNumber())))
            {
                leaders.add(npc);
            }
        }
        if (leaders.isEmpty())
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.army.no_units"), true);
            return;
        }
        leaders.sort(Comparator.comparingInt(npc -> npc.getChain().unitNumber()));
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(AIM_RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 reach = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult creature = ProjectileUtil.getEntityHitResult(player, eye, reach, player.getBoundingBox().expandTowards(reach.subtract(eye)).inflate(1.0D),
                entity -> entity instanceof LivingEntity living && living.isAlive() && entity != player
                        && !(entity instanceof NpcEntity npc && player.getUUID().equals(npc.getOwner())), AIM_RANGE * AIM_RANGE);
        BlockPos spot = block.getType() == HitResult.Type.MISS ? null : block.getBlockPos().relative(block.getDirection());
        float facing = player.getYRot();
        // Units given a spot together stand side by side across it, each as wide as its front rank.
        double radians = facing * Mth.DEG_TO_RAD;
        double rightX = -Math.cos(radians);
        double rightZ = -Math.sin(radians);
        double gap = AncientEmpiresConfig.chainSpacing * 2.0D;
        double[] widths = new double[leaders.size()];
        double total = 0.0D;
        for (int i = 0; i < leaders.size(); i++)
        {
            NpcChain chain = leaders.get(i).getChain();
            widths[i] = Math.max(1, Math.min(AncientEmpiresConfig.chainLineWidth, chain.soldierCount())) * chain.formation().spacing(AncientEmpiresConfig.chainSpacing);
            total += widths[i] + (i > 0 ? gap : 0.0D);
        }
        double offset = -total / 2.0D;
        for (int i = 0; i < leaders.size(); i++)
        {
            NpcChain chain = leaders.get(i).getChain();
            double along = offset + widths[i] / 2.0D;
            offset += widths[i] + gap;
            switch (command)
            {
                case FOLLOW -> chain.setOrder(NpcOrder.FOLLOW, null, Float.NaN, null);
                case FREE -> chain.setOrder(NpcOrder.FREE, null, Float.NaN, null);
                case FORMATION -> chain.setFormation(chain.formation().next());
                case HOLD, DEFEND ->
                {
                    BlockPos at = spot == null ? leaders.get(i).blockPosition()
                            : BlockPos.containing(spot.getX() + 0.5D + rightX * along, spot.getY(), spot.getZ() + 0.5D + rightZ * along);
                    chain.setOrder(command == Command.HOLD ? NpcOrder.HOLD : NpcOrder.DEFEND, at, facing, null);
                }
                case CHARGE ->
                {
                    if (creature != null)
                    {
                        chain.setOrder(NpcOrder.ATTACK, null, Float.NaN, creature.getEntity().getUUID());
                    }
                    else
                    {
                        chain.setOrder(NpcOrder.FREE, null, Float.NaN, null);
                    }
                }
                case FALL_BACK -> chain.setOrder(NpcOrder.FALL_BACK,
                        BlockPos.containing(player.getX() + rightX * along, player.getY(), player.getZ() + rightZ * along), facing, null);
            }
        }
        player.displayClientMessage(Component.translatable("message.ancientempires.army.ordered", units.isEmpty()
                ? Component.translatable("gui.ancientempires.army.all_units") : Component.literal(units.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "))),
                Component.translatable("gui.ancientempires.army.command." + command.name().toLowerCase(Locale.ROOT))), true);
    }

    @Nullable
    private static NpcEntity owned(ServerPlayer player, UUID id)
    {
        return player.serverLevel().getEntity(id) instanceof NpcEntity npc && npc.isAlive() && npc.isOwnedNpc()
                && player.getUUID().equals(npc.getOwner()) ? npc : null;
    }
}
