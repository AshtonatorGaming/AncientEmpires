package com.ancientempires.entity.npc;

import java.util.stream.Collectors;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.LinkedHashSet;
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
        FOLLOW, FREE, HOLD, FALL_BACK, FORMATION, REJOIN, REMOVE, MOVE, RENAME, FLAG, NUMBER,
        /** The War Table's settings, the value by name in the text: a formation, an engagement, a fire order. */
        SET_FORMATION, SET_ENGAGE, SET_FIRE, SHIELDS
    }

    /**
     * The command wheel's orders (Mount & Blade's): given to the selected
     * units, aimed where the player looks. Orders, then formations, stances,
     * and maneuvers (small changes to the order a unit holds).
     */
    public enum Command
    {
        FOLLOW, HOLD, DEFEND, CHARGE, FALL_BACK, FREE,
        LINE, COLUMN, SHIELD_WALL, WEDGE, SQUARE, SKIRMISH,
        AGGRESSIVE, HOLD_LINE, PASSIVE, FIRE_AT_WILL, HOLD_FIRE, VOLLEY, SHIELDS,
        FACE, ABOUT_TURN, LEFT, RIGHT, ADVANCE, BACK, CLOSE_RANKS, SPREAD_OUT, RELEASE;

        public String id()
        {
            return this.name().toLowerCase(Locale.ROOT);
        }
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
                    chain.name(), npc.isHoldingBow(), npc.isPassenger(), Component.Serializer.toJson(npc.getRankName()), chain.unitNumber(),
                    (float) chain.spread(), chain.width(), ArmyPacket.Entry.pack(chain.stance()), npc.getMorale().value(),
                    npc.getSkinId(), npc.getRoleId(), NpcProgress.bandKey(npc.getNpcPath(), npc.getProgress().band()), npc.getMorale().isBroken()));
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
            // A commander's orders go to its whole command: its retinue and every cohort under it.
            case FOLLOW -> group(npc).forEach(each -> each.getChain().setOrder(NpcOrder.FOLLOW, null, Float.NaN, null));
            case FREE -> group(npc).forEach(each -> each.getChain().setOrder(NpcOrder.FREE, null, Float.NaN, null));
            case HOLD -> group(npc).forEach(each -> each.getChain().setOrder(NpcOrder.HOLD, each.blockPosition(), each.getChain().facing(), null));
            case FALL_BACK -> deploy(player.serverLevel(), group(npc), player.position(), player.getYRot(), 0.0D, NpcOrder.FALL_BACK);
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
            case SET_FORMATION -> chain.setFormation(NpcFormation.byName(text));
            case SET_ENGAGE ->
            {
                for (NpcStance.Engage engage : NpcStance.Engage.values())
                {
                    if (engage.name().equalsIgnoreCase(text))
                    {
                        chain.setStance(chain.stance().with(engage));
                    }
                }
            }
            case SET_FIRE ->
            {
                for (NpcStance.Fire fire : NpcStance.Fire.values())
                {
                    if (fire.name().equalsIgnoreCase(text))
                    {
                        chain.setStance(chain.stance().with(fire));
                    }
                }
            }
            case SHIELDS -> chain.setStance(chain.stance().withShields(!chain.stance().shieldsUp()));
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
     * Where the player placed a Hold or Defend in placement mode: a spot
     * ({@code from} and {@code to} the same) or a dragged front line from one
     * to the other, and the way the units face.
     */
    public record Placement(Vec3 from, Vec3 to, float facing)
    {
        /** Blocks of drag from which a placement is a front line rather than a spot. */
        public static final double LINE_FROM = 2.0D;

        public double length()
        {
            return Math.sqrt(Math.pow(this.to.x - this.from.x, 2) + Math.pow(this.to.z - this.from.z, 2));
        }

        public boolean line()
        {
            return this.length() >= LINE_FROM;
        }
    }

    /** Blocks between units standing side by side. */
    public static double unitGap()
    {
        return AncientEmpiresConfig.chainSpacing * 2.0D;
    }

    /**
     * Where a leader placed at {@code x, z} stands: on the ground within ten
     * blocks above or below {@code y} (the first open spot over something
     * solid, searching down from three above), else at {@code y}.
     */
    public static BlockPos ground(Level level, double x, double y, double z)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Mth.floor(y) + 3;
        for (int at = top; at >= top - 13; at--)
        {
            pos.set(Mth.floor(x), at, Mth.floor(z));
            BlockState feet = level.getBlockState(pos);
            BlockState below = level.getBlockState(pos.below());
            if (feet.getCollisionShape(level, pos).isEmpty() && !below.getCollisionShape(level, pos.below()).isEmpty() && feet.getFluidState().isEmpty())
            {
                return pos.immutable();
            }
        }
        return BlockPos.containing(x, y, z);
    }

    /**
     * An order from the command wheel to the player's units with these
     * numbers (all of them for none). Hold and Defend go where the player
     * placed them ({@code placement}: a spot or a dragged front line), else at
     * the block looked at; several units stand side by side across it, their
     * fronts on the line (FormationLayout.place). Charge at the creature
     * looked at (else a free charge), Fall back to the player, Follow, Free;
     * a formation or stance for each unit; or a maneuver of the spot each
     * holds (Face takes the player's way, turns pivot each unit in place).
     */
    public static void command(ServerPlayer player, Set<Integer> units, Command command, @Nullable Placement placement)
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
        // A picked commander brings its command along (the cohort model): its retinue and its cohorts.
        Set<NpcEntity> expanded = new LinkedHashSet<>();
        for (NpcEntity leader : leaders)
        {
            expanded.addAll(group(leader));
        }
        leaders = new ArrayList<>(expanded);
        Vec3 eye = player.getEyePosition();
        if (placement != null && (placement.from().distanceTo(eye) > AIM_RANGE + 8.0D || placement.to().distanceTo(eye) > AIM_RANGE + 8.0D
                || placement.length() > AncientEmpiresConfig.chainSpacing * 64.0D))
        {
            // Out of reach for any honest client.
            placement = null;
        }
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(AIM_RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 reach = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult creature = ProjectileUtil.getEntityHitResult(player, eye, reach, player.getBoundingBox().expandTowards(reach.subtract(eye)).inflate(1.0D),
                entity -> entity instanceof LivingEntity living && living.isAlive() && entity != player
                        && !(entity instanceof NpcEntity npc && player.getUUID().equals(npc.getOwner())), AIM_RANGE * AIM_RANGE);
        BlockPos spot = block.getType() == HitResult.Type.MISS ? null : block.getBlockPos().relative(block.getDirection());
        float facing = placement != null ? placement.facing() : player.getYRot();
        if (command == Command.HOLD || command == Command.DEFEND || command == Command.FALL_BACK)
        {
            Vec3 at;
            double length = 0.0D;
            if (command == Command.FALL_BACK)
            {
                at = player.position();
            }
            else if (placement != null)
            {
                at = placement.line() ? placement.from().add(placement.to()).scale(0.5D) : placement.from();
                length = placement.line() ? placement.length() : 0.0D;
            }
            else
            {
                at = spot == null ? null : Vec3.atBottomCenterOf(spot);
            }
            NpcOrder order = command == Command.HOLD ? NpcOrder.HOLD : command == Command.DEFEND ? NpcOrder.DEFEND : NpcOrder.FALL_BACK;
            if (at != null)
            {
                deploy(level, leaders, at, facing, length, order);
            }
            else
            {
                leaders.forEach(each -> each.getChain().setOrder(order, each.blockPosition(), facing, null));
            }
        }
        for (int i = 0; i < leaders.size(); i++)
        {
            NpcChain chain = leaders.get(i).getChain();
            switch (command)
            {
                case FOLLOW -> chain.setOrder(NpcOrder.FOLLOW, null, Float.NaN, null);
                case FREE -> chain.setOrder(NpcOrder.FREE, null, Float.NaN, null);
                case LINE -> chain.setFormation(NpcFormation.LINE);
                case COLUMN -> chain.setFormation(NpcFormation.COLUMN);
                case SHIELD_WALL -> chain.setFormation(NpcFormation.SHIELD_WALL);
                case WEDGE -> chain.setFormation(NpcFormation.WEDGE);
                case SQUARE -> chain.setFormation(NpcFormation.SQUARE);
                case SKIRMISH -> chain.setFormation(NpcFormation.SKIRMISH);
                case AGGRESSIVE -> chain.setStance(chain.stance().with(NpcStance.Engage.AGGRESSIVE));
                case HOLD_LINE -> chain.setStance(chain.stance().with(NpcStance.Engage.HOLD_LINE));
                case PASSIVE -> chain.setStance(chain.stance().with(NpcStance.Engage.PASSIVE));
                case FIRE_AT_WILL -> chain.setStance(chain.stance().with(NpcStance.Fire.AT_WILL));
                case HOLD_FIRE -> chain.setStance(chain.stance().with(NpcStance.Fire.HOLD));
                case VOLLEY -> chain.setStance(chain.stance().with(NpcStance.Fire.VOLLEY));
                case SHIELDS -> chain.setStance(chain.stance().withShields(!chain.stance().shieldsUp()));
                case FACE -> chain.face(facing);
                case ABOUT_TURN -> chain.turn(180.0F);
                // Yaw grows clockwise seen from above: a unit's left is less.
                case LEFT -> chain.turn(-90.0F);
                case RIGHT -> chain.turn(90.0F);
                case ADVANCE -> chain.advance(AncientEmpiresConfig.chainManeuverStep);
                case BACK -> chain.advance(-AncientEmpiresConfig.chainManeuverStep);
                case CLOSE_RANKS -> chain.stepSpread(false);
                case SPREAD_OUT -> chain.stepSpread(true);
                case RELEASE -> chain.release();
                case HOLD, DEFEND, FALL_BACK ->
                {
                    // Placed above.
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
            }
        }
        player.displayClientMessage(Component.translatable("message.ancientempires.army.ordered", units.isEmpty()
                ? Component.translatable("gui.ancientempires.army.all_units") : Component.literal(units.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "))),
                Component.translatable("gui.ancientempires.army.command." + command.id())), true);
    }

    /**
     * A leader and, if it commands, everyone it commands: its own unit (a
     * commander's retinue) first, then each officer's group under it, loaded
     * ones only.
     */
    public static List<NpcEntity> group(NpcEntity leader)
    {
        Set<NpcEntity> found = new LinkedHashSet<>();
        collect(leader, found, 0);
        return new ArrayList<>(found);
    }

    private static void collect(NpcEntity leader, Set<NpcEntity> found, int depth)
    {
        if (!found.add(leader) || depth > 4 || !(leader.level() instanceof ServerLevel level))
        {
            return;
        }
        for (UUID id : leader.getChain().officers())
        {
            if (level.getEntity(id) instanceof NpcEntity officer && officer.isAlive())
            {
                collect(officer, found, depth + 1);
            }
        }
    }

    /** Whether this leader commands cohorts (has officers under it): it stands behind them in a group order. */
    public static boolean commands(NpcEntity leader)
    {
        return leader.getChain().officerCount() > 0;
    }

    /**
     * Gives {@code leaders} a spot order across one spot or front line
     * ({@code length} 0 for a spot): cohorts on it side by side, commanders
     * behind its middle (FormationLayout.placeGroup), each on the ground.
     */
    static void deploy(ServerLevel level, List<NpcEntity> leaders, Vec3 at, float facing, double length, NpcOrder order)
    {
        FormationLayout.Unit[] shapes = new FormationLayout.Unit[leaders.size()];
        boolean[] commanders = new boolean[leaders.size()];
        for (int i = 0; i < shapes.length; i++)
        {
            shapes[i] = leaders.get(i).getChain().unitShape();
            commanders[i] = commands(leaders.get(i));
        }
        FormationLayout.Placed[] where = FormationLayout.placeGroup(shapes, commanders, at.x, at.z, facing, length, unitGap(), AncientEmpiresConfig.chainCommandPost);
        for (int i = 0; i < where.length; i++)
        {
            NpcChain chain = leaders.get(i).getChain();
            if (where[i].width() > 0)
            {
                // A dragged line: the unit stands as many abreast as its share of it holds.
                chain.setWidth(where[i].width());
            }
            chain.setOrder(order, ground(level, where[i].x(), at.y, where[i].z()), facing, null);
        }
    }

    @Nullable
    private static NpcEntity owned(ServerPlayer player, UUID id)
    {
        return player.serverLevel().getEntity(id) instanceof NpcEntity npc && npc.isAlive() && npc.isOwnedNpc()
                && player.getUUID().equals(npc.getOwner()) ? npc : null;
    }
}
