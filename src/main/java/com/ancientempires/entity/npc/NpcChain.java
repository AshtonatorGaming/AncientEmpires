package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * An NPC's place in a chain of command (docs/chain-of-command.md): one link up
 * to its leader, and, for a leader, the short list of those directly under it.
 * <p>
 * Orders: an NPC either holds its own order (the top of a chain, or a unit
 * below given one of its own: detached until it rejoins) or inherits its
 * leader's. Whoever holds the order (the "holder") keeps its spot, facing, and
 * target; everyone under it reads those one link up each tick
 * ({@link #holder}), never walking the tree. The order also leashes whom a
 * soldier may fight ({@link #allowsTarget}): a holding line does not chase off,
 * a falling-back unit fights only when cornered.
 * <p>
 * A link to an NPC that cannot be found is asked of {@link NpcChainLedger}:
 * away (wait, acting free meanwhile) or gone (follow its heir, or stand alone).
 * Only players' soldiers join, and only when their owner assigns them (the
 * Signal Flag). Saved on the NPC (docs/nbt-keys.md); not packed: a repacked NPC
 * comes back out of any unit.
 */
public final class NpcChain
{
    private static final String LEADER_KEY = "AeLeader";
    private static final String MEMBERS_KEY = "AeSquad";
    private static final String OFFICERS_KEY = "AeSquadOfficers";
    private static final String ORDER_KEY = "AeOrder";
    private static final String ORDER_POS_KEY = "AeOrderPos";
    private static final String ORDER_FACING_KEY = "AeOrderFacing";
    private static final String ORDER_TARGET_KEY = "AeOrderTarget";
    private static final String FORMATION_KEY = "AeFormation";
    /** Ticks between looking for a missing leader, and between a leader's member checks. */
    private static final int RESOLVE_TICKS = 20;
    private static final int PRUNE_TICKS = 100;
    /** Ticks a leader's slot layout is kept before it is worked out again, if it has not moved. */
    private static final int SLOT_TICKS = 10;
    /** How far up a loop check looks (soldier, sergeant, captain, general and one spare). */
    private static final int MAX_DEPTH = 5;
    /** Blocks from its rally point at which a falling-back unit stops and holds. */
    private static final double RALLIED = 2.0D;

    /** Why an assignment was refused, or that it went through. */
    public enum Assign
    {
        ADDED, REMOVED, DISABLED, NOT_YOURS, NOT_SOLDIER, NOT_LOWER, FULL, LOOP
    }

    private final NpcEntity npc;
    @Nullable
    private UUID leaderId;
    private final List<UUID> members = new ArrayList<>();
    /** Members that lead units of their own (stood apart in the layout, counted against the officer cap). */
    private final Set<UUID> officers = new HashSet<>();
    /** This NPC's own order, or null to take its leader's (at the top of a chain, null is Free). */
    @Nullable
    private NpcOrder order;
    @Nullable
    private BlockPos orderPos;
    private float orderFacing = Float.NaN;
    @Nullable
    private UUID orderTarget;
    private NpcFormation formation = NpcFormation.LINE;

    @Nullable
    private NpcEntity leader;
    /** Whose order this NPC acts on (itself, or the holder above it). */
    private NpcChain holder = this;
    /** The way the unit faces while moving: where its leader last walked. */
    private float facing = Float.NaN;
    private Vec3 lastPos;
    private final Map<UUID, Vec3> slots = new HashMap<>();
    private Vec3 slotsFrom;
    private float slotsFacing;
    private NpcFormation slotsFormation;
    private long slotsAt = -1L;

    NpcChain(NpcEntity npc)
    {
        this.npc = npc;
    }

    public NpcEntity npc()
    {
        return this.npc;
    }

    @Nullable
    public UUID leaderId()
    {
        return this.leaderId;
    }

    /** The leader, when it is loaded and alive. */
    @Nullable
    public NpcEntity leader()
    {
        return this.leader != null && this.leader.isAlive() && !this.leader.isRemoved() ? this.leader : null;
    }

    public List<UUID> members()
    {
        return Collections.unmodifiableList(this.members);
    }

    public boolean leads()
    {
        return !this.members.isEmpty();
    }

    /** Whether this NPC is in a chain of command at all. */
    public boolean inChain()
    {
        return this.leaderId != null || !this.members.isEmpty();
    }

    /** This NPC's own order, or null when it takes its leader's. */
    @Nullable
    public NpcOrder order()
    {
        return this.order;
    }

    /** Whose order this NPC acts on: its own, or the nearest one above that holds one. */
    public NpcChain holder()
    {
        return this.holder;
    }

    /** The order this NPC acts on. */
    public NpcOrder effectiveOrder()
    {
        return this.holder.order == null ? NpcOrder.FREE : this.holder.order;
    }

    /** Whether this NPC holds an order of its own (it leads it, rather than forming on its leader). */
    public boolean holdsOrder()
    {
        return this.holder == this;
    }

    @Nullable
    public BlockPos orderPos()
    {
        return this.holder.orderPos;
    }

    public NpcFormation formation()
    {
        return this.formation;
    }

    /** Whether this NPC keeps a slot in its leader's formation now. */
    public boolean formed()
    {
        return AncientEmpiresConfig.chainEnabled && !this.holdsOrder() && this.leader() != null && this.effectiveOrder().formed();
    }

    /**
     * Gives this unit an order. At the top of a chain anything goes, Free and
     * Follow included (Follow follows the owner); a unit under another leader
     * takes Hold, Defend, Attack, or Fall back as its own (detaching), and
     * Follow or Free rejoins it to its leader.
     */
    public void setOrder(NpcOrder order, @Nullable BlockPos pos, float facing, @Nullable UUID target)
    {
        boolean top = this.leaderId == null;
        if (!top && (order == NpcOrder.FOLLOW || order == NpcOrder.FREE))
        {
            this.order = null;
            this.orderPos = null;
            this.orderTarget = null;
        }
        else
        {
            this.order = order;
            this.orderPos = pos == null ? null : pos.immutable();
            this.orderFacing = facing;
            this.orderTarget = target;
        }
        this.holder = this.order != null || top ? this : this.holder;
        this.npc.setFollowing(top && order == NpcOrder.FOLLOW);
        this.slotsAt = -1L;
    }

    public void setFormation(NpcFormation formation)
    {
        this.formation = formation;
        this.slotsAt = -1L;
    }

    /** Soldiers (not officers) directly under this leader. */
    public int soldierCount()
    {
        return this.members.size() - this.officers.size();
    }

    public int officerCount()
    {
        return this.officers.size();
    }

    /** How many officers this leader's rank may have under it (Integer.MAX_VALUE for any). */
    public static int officerCap(NpcRank rank)
    {
        return switch (rank)
        {
            case SOLDIER, SERGEANT -> 0;
            case CAPTAIN -> AncientEmpiresConfig.chainSquadsPerCaptain;
            case GENERAL -> AncientEmpiresConfig.chainCaptainsPerGeneral <= 0 ? Integer.MAX_VALUE : AncientEmpiresConfig.chainCaptainsPerGeneral;
        };
    }

    /**
     * Whether the order lets this NPC fight {@code target}. Outside a chain,
     * or Free, anything goes; Follow keeps within {@code followLeash} of the
     * holder, Hold within {@code holdRange} of the NPC's own place in the line,
     * Defend within {@code defendRadius} of the spot, Fall back only what is
     * on top of it; Attack anything. Soldiers with a bow reach further.
     */
    public boolean allowsTarget(LivingEntity target)
    {
        if (!AncientEmpiresConfig.chainEnabled || !this.inChain())
        {
            return true;
        }
        double reach = this.npc.isHoldingBow() ? AncientEmpiresConfig.chainRangedLeash : 0.0D;
        return switch (this.effectiveOrder())
        {
            case FREE, ATTACK -> true;
            case FOLLOW -> within(target, this.holder.npc.position(), AncientEmpiresConfig.chainFollowLeash + reach);
            case HOLD -> within(target, this.place(), AncientEmpiresConfig.chainHoldRange + reach);
            case DEFEND -> this.orderPos() == null || within(target, Vec3.atBottomCenterOf(this.orderPos()), AncientEmpiresConfig.chainDefendRadius + reach);
            case FALL_BACK -> this.npc.distanceToSqr(target) <= AncientEmpiresConfig.chainCornerRange * AncientEmpiresConfig.chainCornerRange;
        };
    }

    private static boolean within(LivingEntity target, Vec3 from, double range)
    {
        return target.position().distanceToSqr(from) <= range * range;
    }

    /** Where this NPC stands in its unit now: its slot, or the order's spot for whoever holds it. */
    private Vec3 place()
    {
        NpcEntity current = this.leader();
        Vec3 slot = this.formed() && current != null ? current.getChain().slotOf(this.npc) : null;
        if (slot != null)
        {
            return slot;
        }
        return this.holdsOrder() && this.orderPos != null ? Vec3.atBottomCenterOf(this.orderPos) : this.npc.position();
    }

    /**
     * Puts {@code soldier} under {@code leader}, or takes it out if it is
     * already there. Both must be the same player's soldiers, the leader of a
     * higher rank (sergeant or above) with room, and no loop made.
     */
    public static Assign toggle(NpcEntity leader, NpcEntity soldier)
    {
        if (!AncientEmpiresConfig.chainEnabled)
        {
            return Assign.DISABLED;
        }
        if (!leader.isOwnedNpc() || !soldier.isOwnedNpc() || leader.getOwner() == null || !leader.getOwner().equals(soldier.getOwner()))
        {
            return Assign.NOT_YOURS;
        }
        if (!soldier.getNpcPath().combat() || soldier.isLevied() || !leader.getNpcPath().combat())
        {
            return Assign.NOT_SOLDIER;
        }
        NpcChain chain = leader.getChain();
        if (chain.members.contains(soldier.getUUID()))
        {
            soldier.getChain().leave();
            return Assign.REMOVED;
        }
        if (leader.getRank() == NpcRank.SOLDIER || soldier.getRank().ordinal() >= leader.getRank().ordinal())
        {
            return Assign.NOT_LOWER;
        }
        boolean officer = soldier.getRank() != NpcRank.SOLDIER;
        if (officer ? chain.officerCount() >= officerCap(leader.getRank()) : chain.soldierCount() >= AncientEmpiresConfig.chainSquadSize)
        {
            return Assign.FULL;
        }
        // No loop: the soldier must not already stand above the leader.
        NpcEntity above = leader;
        for (int depth = 0; above != null && depth < MAX_DEPTH; depth++)
        {
            if (above == soldier)
            {
                return Assign.LOOP;
            }
            above = above.getChain().leader();
        }
        NpcChain own = soldier.getChain();
        own.leave();
        own.leaderId = leader.getUUID();
        own.leader = leader;
        // It joins under its new leader's order, and stops following on its own.
        own.order = null;
        own.orderPos = null;
        own.orderTarget = null;
        own.holder = chain.holder;
        soldier.setFollowing(false);
        chain.members.add(soldier.getUUID());
        if (officer)
        {
            chain.officers.add(soldier.getUUID());
        }
        chain.slotsAt = -1L;
        return Assign.ADDED;
    }

    /** Takes this NPC out of its leader's unit (its own unit stays with it). */
    public void leave()
    {
        NpcEntity current = this.leader();
        if (current != null)
        {
            current.getChain().drop(this.npc.getUUID());
        }
        this.leaderId = null;
        this.leader = null;
        this.holder = this;
    }

    private void drop(UUID member)
    {
        if (this.members.remove(member))
        {
            this.officers.remove(member);
            this.slots.remove(member);
            this.slotsAt = -1L;
        }
    }

    /**
     * Leaves the chain for good (death, or packed into a scroll). With
     * {@code succeed}, the best NPC under it takes its place at once: its
     * leader, order, formation, and the rest of its unit. Unloaded members find
     * the heir later through the ledger.
     *
     * @return the heir, or null
     */
    @Nullable
    public NpcEntity depart(boolean succeed)
    {
        if (!(this.npc.level() instanceof ServerLevel level) || !this.inChain())
        {
            return null;
        }
        NpcEntity heir = null;
        if (succeed)
        {
            for (UUID id : this.members)
            {
                if (level.getEntity(id) instanceof NpcEntity member && member.isAlive() && (heir == null || better(member, heir)))
                {
                    heir = member;
                }
            }
        }
        NpcEntity above = this.leader();
        if (heir != null)
        {
            NpcChain next = heir.getChain();
            for (UUID id : this.members)
            {
                if (id.equals(heir.getUUID()) || next.members.contains(id))
                {
                    continue;
                }
                next.members.add(id);
                if (this.officers.contains(id))
                {
                    next.officers.add(id);
                }
                if (level.getEntity(id) instanceof NpcEntity member)
                {
                    member.getChain().leaderId = heir.getUUID();
                    member.getChain().leader = heir;
                }
            }
            next.leaderId = this.leaderId;
            next.leader = above;
            next.order = this.order;
            next.orderPos = this.orderPos;
            next.orderFacing = this.orderFacing;
            next.orderTarget = this.orderTarget;
            next.formation = this.formation;
            next.holder = this.holder == this ? next : this.holder;
            next.slotsAt = -1L;
            // At the top of a chain the heir carries the order on (following the owner, say).
            heir.setFollowing(this.leaderId == null && this.order == NpcOrder.FOLLOW);
            if (above != null)
            {
                NpcChain upper = above.getChain();
                int at = upper.members.indexOf(this.npc.getUUID());
                if (at >= 0)
                {
                    upper.members.set(at, heir.getUUID());
                    // The heir now leads a unit: it stands with the officers.
                    upper.officers.remove(this.npc.getUUID());
                    upper.officers.add(heir.getUUID());
                    upper.slotsAt = -1L;
                }
            }
        }
        else
        {
            if (above != null)
            {
                above.getChain().drop(this.npc.getUUID());
            }
            for (UUID id : this.members)
            {
                if (level.getEntity(id) instanceof NpcEntity member)
                {
                    member.getChain().leaderId = null;
                    member.getChain().leader = null;
                    member.getChain().holder = member.getChain();
                }
            }
        }
        NpcChainLedger.get(level).record(this.npc.getUUID(), heir == null ? null : heir.getUUID(), level.getGameTime());
        this.members.clear();
        this.officers.clear();
        this.leaderId = null;
        this.leader = null;
        this.holder = this;
        return heir;
    }

    /** Who should take over: the higher rank, then the higher level. */
    static boolean better(NpcEntity candidate, NpcEntity than)
    {
        if (candidate.getRank() != than.getRank())
        {
            return candidate.getRank().ordinal() > than.getRank().ordinal();
        }
        return candidate.getLevel() > than.getLevel();
    }

    /**
     * Server tick: finds the leader, takes its order holder, tracks the unit's
     * heading, keeps the member list true, and carries out its own order's
     * changes (an attack whose enemy fell, a fall back that reached its point).
     */
    void tick()
    {
        if (!(this.npc.level() instanceof ServerLevel level))
        {
            return;
        }
        long now = level.getGameTime();
        if (this.leaderId != null && this.leader() == null && now % RESOLVE_TICKS == Math.floorMod(this.npc.getId(), RESOLVE_TICKS))
        {
            this.resolve(level);
        }
        NpcEntity current = this.leader();
        this.holder = this.order != null || this.leaderId == null || current == null ? this : current.getChain().holder;
        if (this.holder == this && this.leaderId != null && current == null && this.order == null)
        {
            // The leader is away: act free until it is back.
            return;
        }
        if (this.holdsOrder())
        {
            this.carryOut(level);
        }
        if (!this.members.isEmpty())
        {
            this.trackFacing();
            if (now % PRUNE_TICKS == Math.floorMod(this.npc.getId(), PRUNE_TICKS))
            {
                this.prune(level);
            }
        }
        NpcOrder effective = this.effectiveOrder();
        if (effective == NpcOrder.ATTACK && this.npc.getTarget() == null && this.holder.orderTarget != null
                && level.getEntity(this.holder.orderTarget) instanceof LivingEntity enemy && enemy.isAlive())
        {
            this.npc.setTarget(enemy);
        }
        else if (this.npc.getTarget() != null && now % 10L == Math.floorMod(this.npc.getId(), 10) && !this.allowsTarget(this.npc.getTarget()))
        {
            // Out of what the order allows (a holding line does not chase off).
            this.npc.setTarget(null);
        }
    }

    private void carryOut(ServerLevel level)
    {
        if (this.order == NpcOrder.ATTACK)
        {
            Entity enemy = this.orderTarget == null ? null : level.getEntity(this.orderTarget);
            if (!(enemy instanceof LivingEntity living) || !living.isAlive())
            {
                // The enemy fell (or is gone): hold where the unit stands.
                this.setOrder(NpcOrder.HOLD, this.npc.blockPosition(), this.facing(), null);
            }
        }
        else if (this.order == NpcOrder.FALL_BACK && this.orderPos != null
                && this.npc.position().distanceToSqr(Vec3.atBottomCenterOf(this.orderPos)) <= RALLIED * RALLIED)
        {
            this.setOrder(NpcOrder.HOLD, this.orderPos, this.orderFacing, null);
        }
    }

    private void resolve(ServerLevel level)
    {
        Entity found = level.getEntity(this.leaderId);
        if (found instanceof NpcEntity npc && npc.isAlive())
        {
            if (npc.getChain().members.contains(this.npc.getUUID()))
            {
                this.leader = npc;
            }
            else
            {
                // It let this NPC go (or took an heir's list without it): stand alone.
                this.leaderId = null;
            }
            return;
        }
        NpcChainLedger.Gone gone = NpcChainLedger.get(level).gone(this.leaderId);
        if (gone != null)
        {
            // Fallen or packed: answer to its heir, or to nobody.
            this.leaderId = gone.heir();
        }
    }

    /** A leader forgets members that are gone for good, and takes on their heirs. */
    private void prune(ServerLevel level)
    {
        NpcChainLedger ledger = NpcChainLedger.get(level);
        for (int i = this.members.size() - 1; i >= 0; i--)
        {
            UUID id = this.members.get(i);
            NpcChainLedger.Gone gone = level.getEntity(id) == null ? ledger.gone(id) : null;
            if (gone == null)
            {
                continue;
            }
            this.officers.remove(id);
            if (gone.heir() != null && !this.members.contains(gone.heir()))
            {
                this.members.set(i, gone.heir());
                this.officers.add(gone.heir());
            }
            else
            {
                this.members.remove(i);
            }
            this.slotsAt = -1L;
        }
    }

    private void trackFacing()
    {
        Vec3 here = this.npc.position();
        if (Float.isNaN(this.facing))
        {
            this.facing = this.npc.getYRot();
            this.lastPos = here;
            return;
        }
        double dx = here.x - this.lastPos.x;
        double dz = here.z - this.lastPos.z;
        if (dx * dx + dz * dz > 0.25D)
        {
            // Faces the way it walks; standing, it keeps the last heading.
            this.facing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            this.lastPos = here;
        }
    }

    /** The way this unit faces, in degrees (a mob's yaw): the order's facing at a held spot, else where it walks. */
    public float facing()
    {
        if (this.effectiveOrder().anchored() && !Float.isNaN(this.holder.orderFacing))
        {
            return this.holder.orderFacing;
        }
        return Float.isNaN(this.facing) ? this.npc.getYRot() : this.facing;
    }

    /**
     * Where {@code member} stands in this leader's formation, worked out for the
     * whole unit at once and kept until the leader moves a block, turns, or
     * {@link #SLOT_TICKS} pass. Null when it is not a member here.
     */
    @Nullable
    public Vec3 slotOf(NpcEntity member)
    {
        long now = this.npc.level().getGameTime();
        Vec3 here = this.npc.position();
        float facing = this.facing();
        if (this.slotsAt < 0L || this.slotsFormation != this.formation || now - this.slotsAt > SLOT_TICKS
                && (this.slotsFrom.distanceToSqr(here) > 1.0D || Math.abs(Mth.wrapDegrees(facing - this.slotsFacing)) > 15.0F))
        {
            this.layout(here, facing);
            this.slotsAt = now;
        }
        return this.slots.get(member.getUUID());
    }

    private void layout(Vec3 here, float facing)
    {
        this.slots.clear();
        this.slotsFrom = here;
        this.slotsFacing = facing;
        this.slotsFormation = this.formation;
        double radians = facing * Mth.DEG_TO_RAD;
        // A mob facing yaw 0 looks south (+z); its right is west (-x).
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        double rightX = -forwardZ;
        double rightZ = forwardX;
        int lineWidth = AncientEmpiresConfig.chainLineWidth;
        int columnWidth = AncientEmpiresConfig.chainColumnWidth;
        double spacing = AncientEmpiresConfig.chainSpacing;
        List<UUID> soldiers = new ArrayList<>();
        List<UUID> leaders = new ArrayList<>();
        for (UUID id : this.members)
        {
            (this.officers.contains(id) ? leaders : soldiers).add(id);
        }
        for (int i = 0; i < soldiers.size(); i++)
        {
            double[] slot = this.formation.soldierSlot(i, soldiers.size(), lineWidth, columnWidth, spacing);
            this.slots.put(soldiers.get(i), place(here, slot, forwardX, forwardZ, rightX, rightZ));
        }
        // Officers' units, assumed full: as wide and deep as a whole squad in this formation, standing ahead.
        int squad = AncientEmpiresConfig.chainSquadSize;
        double gap = this.formation.spacing(spacing);
        double frontage = Math.min(squad, this.formation.width(lineWidth, columnWidth)) * gap;
        double behind = this.formation.depthBehind(squad, lineWidth, columnWidth, spacing);
        double depth = this.formation.lead(spacing) + behind;
        int ownRows = this.formation.rows(soldiers.size(), lineWidth, columnWidth);
        for (int i = 0; i < leaders.size(); i++)
        {
            double[] slot = this.formation.officerSlot(i, leaders.size(), ownRows, frontage, behind, depth, spacing);
            this.slots.put(leaders.get(i), place(here, slot, forwardX, forwardZ, rightX, rightZ));
        }
    }

    private static Vec3 place(Vec3 here, double[] slot, double forwardX, double forwardZ, double rightX, double rightZ)
    {
        return new Vec3(here.x + rightX * slot[0] - forwardX * slot[1], here.y, here.z + rightZ * slot[0] - forwardZ * slot[1]);
    }

    void save(CompoundTag tag)
    {
        if (this.leaderId != null)
        {
            tag.putUUID(LEADER_KEY, this.leaderId);
        }
        if (!this.members.isEmpty())
        {
            ListTag list = new ListTag();
            this.members.forEach(id -> list.add(NbtUtils.createUUID(id)));
            tag.put(MEMBERS_KEY, list);
            ListTag officers = new ListTag();
            this.officers.forEach(id -> officers.add(NbtUtils.createUUID(id)));
            tag.put(OFFICERS_KEY, officers);
        }
        if (this.order != null)
        {
            tag.putString(ORDER_KEY, this.order.id());
        }
        if (this.orderPos != null)
        {
            tag.putLong(ORDER_POS_KEY, this.orderPos.asLong());
        }
        if (!Float.isNaN(this.orderFacing))
        {
            tag.putFloat(ORDER_FACING_KEY, this.orderFacing);
        }
        if (this.orderTarget != null)
        {
            tag.putUUID(ORDER_TARGET_KEY, this.orderTarget);
        }
        if (this.formation != NpcFormation.LINE)
        {
            tag.putString(FORMATION_KEY, this.formation.id());
        }
    }

    void load(CompoundTag tag)
    {
        this.leaderId = tag.hasUUID(LEADER_KEY) ? tag.getUUID(LEADER_KEY) : null;
        this.members.clear();
        this.officers.clear();
        for (Tag id : tag.getList(MEMBERS_KEY, Tag.TAG_INT_ARRAY))
        {
            this.members.add(NbtUtils.loadUUID(id));
        }
        for (Tag id : tag.getList(OFFICERS_KEY, Tag.TAG_INT_ARRAY))
        {
            this.officers.add(NbtUtils.loadUUID(id));
        }
        this.order = tag.contains(ORDER_KEY) ? NpcOrder.byName(tag.getString(ORDER_KEY)) : null;
        this.orderPos = tag.contains(ORDER_POS_KEY) ? BlockPos.of(tag.getLong(ORDER_POS_KEY)) : null;
        this.orderFacing = tag.contains(ORDER_FACING_KEY) ? tag.getFloat(ORDER_FACING_KEY) : Float.NaN;
        this.orderTarget = tag.hasUUID(ORDER_TARGET_KEY) ? tag.getUUID(ORDER_TARGET_KEY) : null;
        this.formation = NpcFormation.byName(tag.getString(FORMATION_KEY));
        this.holder = this;
    }
}
