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
import net.minecraft.world.entity.Mob;
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
 * Fighting as a unit (phase 3): a leader scans for the unit, every
 * {@code chain.planTicks}, and hands its soldiers their targets
 * ({@link #plan}): the nearest enemy each, weighted so they spread over the
 * foes in front rather than all pile on one, released a few ticks apart,
 * nearest first. A soldier in formation is a grunt ({@link #grunt}): it never
 * runs its own target scan and takes what it is handed, keeping only its own
 * defence (a hit still turns it on the attacker) and its fighting skill.
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
    private static final String NAME_KEY = "AeUnitName";
    private static final String POSTED_KEY = "AePosted";
    private static final String NUMBER_KEY = "AeUnitNumber";
    private static final String STANCE_KEY = "AeStance";
    private static final String SPREAD_KEY = "AeSpread";
    /** Ticks of each volley in which a Volley unit's archers may loose. */
    private static final int VOLLEY_WINDOW = 4;
    /** Ticks between a marching leader's looks at its stragglers. */
    private static final int MARCH_CHECK_TICKS = 10;
    /** Blocks from its spot at which a marching unit stops marching and deploys there. */
    private static final double DEPLOY = 3.0D;
    /** Ticks a hit counts as provoking a Passive unit. */
    private static final int PROVOKED_TICKS = 200;
    /** The spacing a unit closes to and opens out to (Close ranks, Spread out), around 1. */
    private static final double[] SPREADS = {0.75D, 1.0D, 2.0D};
    /** Ticks between looking for a missing leader, and between a leader's member checks. */
    private static final int RESOLVE_TICKS = 20;
    private static final int PRUNE_TICKS = 100;
    /** Ticks a leader's slot layout is kept before it is worked out again, if it has not moved. */
    private static final int SLOT_TICKS = 10;
    /** Blocks a moving leader goes, and degrees it turns, before its unit's slots are laid out again. */
    private static final double SLOT_MOVE = 2.5D;
    /** A marching leader re-lays its slots every block, laid out this far ahead of it, so the ranks walk steadily ahead. */
    private static final double MARCH_SLOT_MOVE = 1.0D;
    private static final float SLOT_TURN = 25.0F;
    /** How far up a loop check looks (soldier, sergeant, captain, general and one spare). */
    private static final int MAX_DEPTH = 5;
    /** Blocks from its rally point at which a falling-back unit stops and holds. */
    private static final double RALLIED = 2.0D;

    /**
     * A move to a far spot (docs/army-command-design.md): the unit first forms
     * up where it stands facing the spot, then marches there with its leader
     * at {@code chain.marchSpeed}, its front rank ahead of it.
     */
    public enum March
    {
        NONE, FORMING, MARCHING
    }

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
    /**
     * A posted unit (a structure's garrison) holds its order from where each
     * soldier stands, its own post: no formation, no leader walking to the
     * spot, each fighting for itself within the order's leash. Any new order
     * takes it off its posts.
     */
    private boolean posted;
    /** The unit's number for the order keys (1 to 9; 0 for none), given when it first takes a soldier. */
    private int unitNumber;
    /** The unit's name, given on the command screen ("Iron Wolves"); empty for none. */
    private String name = "";
    /** This leader's own stance, or null to take the one above it. */
    @Nullable
    private NpcStance stance;
    /** Spacing multiplier of this leader's layout (Close ranks, Spread out). */
    private double spread = 1.0D;
    /** The order holder's march (form up, then march) to a far spot. */
    private March march = March.NONE;
    private long marchSince;
    private Vec3 formFrom;
    private float marchHeading;
    /** Whether a marching leader is waiting for stragglers. */
    private boolean straggling;
    /**
     * Released: the unit fights freely without giving up its order's spot, and
     * goes back to its slots once no enemy has been in reach for
     * {@code chain.releaseCalmTicks}.
     */
    private boolean released;
    private long calmSince = -1L;

    @Nullable
    private NpcEntity leader;
    /** Whose order this NPC acts on (itself, or the holder above it). */
    private NpcChain holder = this;
    /** Whose stance this NPC fights by (itself, or the nearest above with one). */
    private NpcChain stanceSource = this;
    /** The way the unit faces while moving: where its leader last walked. */
    private float facing = Float.NaN;
    private Vec3 lastPos;
    private final Map<UUID, Vec3> slots = new HashMap<>();
    /** Members that face their own way (out of a square), not the unit's. */
    private final Map<UUID, Float> slotFacings = new HashMap<>();
    private Vec3 slotsFrom;
    private float slotsFacing;
    private NpcFormation slotsFormation;
    private long slotsAt = -1L;
    /** A leader's hand-out: whom each of its soldiers fights, and from when (the staggered release). */
    private final Map<UUID, LivingEntity> assigned = new HashMap<>();
    private final Map<UUID, Long> releaseAt = new HashMap<>();
    private long planAt = -1L;
    /** Game time until which a released rider is charging (faster, and its first hit at speed lands harder). */
    private long chargeUntil = -1L;

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

    /**
     * A soldier in formation with no unit of its own: it does no thinking of
     * its own (no target scan); its leader hands it its fights.
     */
    public boolean grunt()
    {
        return this.members.isEmpty() && this.formed();
    }

    /** Whom this leader has handed {@code member}, once its turn in the release has come; null for nobody yet. */
    @Nullable
    public LivingEntity handedTo(NpcEntity member)
    {
        LivingEntity enemy = this.assigned.get(member.getUUID());
        if (enemy == null)
        {
            return null;
        }
        if (!enemy.isAlive() || enemy.isRemoved())
        {
            // That one fell: think again next tick.
            this.assigned.remove(member.getUUID());
            this.planAt = -1L;
            return null;
        }
        Long from = this.releaseAt.get(member.getUUID());
        return from == null || this.npc.level().getGameTime() >= from ? enemy : null;
    }

    /** A released rider's charge: on until {@code chargeTicks} pass or its first hit lands. */
    public boolean charging()
    {
        return this.chargeUntil >= 0L && this.npc.level().getGameTime() < this.chargeUntil;
    }

    /**
     * The damage multiplier for a melee hit now: {@code chargeDamage} for a
     * charging rider still moving at {@code chargeMinSpeed} (blocks a tick) or
     * more, which spends the charge; else 1.
     */
    public float chargeHit()
    {
        if (!this.charging())
        {
            return 1.0F;
        }
        this.chargeUntil = -1L;
        Entity mount = this.npc.getVehicle();
        double speed = (mount != null ? mount : this.npc).getDeltaMovement().horizontalDistance();
        return speed >= AncientEmpiresConfig.chainChargeMinSpeed ? (float) AncientEmpiresConfig.chainChargeDamage : 1.0F;
    }

    /** Whether this soldier stands in formation within {@code formationMoraleRange} of its leader (squad morale). */
    public boolean formedNearLeader()
    {
        NpcEntity current = this.leader();
        double range = AncientEmpiresConfig.chainFormationMoraleRange;
        return this.formed() && current != null && this.npc.distanceToSqr(current) <= range * range;
    }

    /** Whether this NPC's leader has broken (routing, shattered, or surrendered): its unit is shaken while it lasts. */
    public boolean leaderBroken()
    {
        NpcEntity current = this.leader();
        return current != null && current.getMorale().isBroken();
    }

    /** Whether this NPC keeps a slot in its leader's formation now. */
    public boolean formed()
    {
        return AncientEmpiresConfig.chainEnabled && !this.holdsOrder() && this.leader() != null && this.effectiveOrder().formed() && !this.holder.posted;
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
        this.posted = false;
        this.released = false;
        this.npc.setFollowing(top && order == NpcOrder.FOLLOW);
        this.startMarch();
        this.slotsAt = -1L;
    }

    /**
     * A Hold or Defend spot further than {@code chain.formUpDistance} from a
     * leader with a unit: form up first, then march. Anything else goes straight.
     */
    private void startMarch()
    {
        this.march = March.NONE;
        this.straggling = false;
        if (this.order != NpcOrder.HOLD && this.order != NpcOrder.DEFEND || this.orderPos == null || this.members.isEmpty())
        {
            return;
        }
        Vec3 spot = Vec3.atBottomCenterOf(this.orderPos);
        Vec3 here = this.npc.position();
        double far = AncientEmpiresConfig.chainFormUpDistance;
        if (here.distanceToSqr(spot.x, here.y, spot.z) > far * far)
        {
            this.march = March.FORMING;
            this.marchSince = this.npc.level().getGameTime();
            this.formFrom = here;
            this.marchHeading = NpcFormation.yawToward(here.x, here.z, spot.x, spot.z);
        }
    }

    /** Where this unit's order holder is in a march to a far spot. */
    public March march()
    {
        return this.holder.march;
    }

    /** Whether a marching leader is halted for its stragglers. */
    public boolean straggling()
    {
        return this.holder.straggling;
    }

    /** The stance this NPC fights by: its own, or the nearest one above it. */
    public NpcStance stance()
    {
        NpcStance own = this.stanceSource.stance;
        return own == null ? NpcStance.DEFAULT : own;
    }

    /** Sets this leader's stance; its unit, and units under it without their own, take it. */
    public void setStance(NpcStance stance)
    {
        this.stance = stance;
        this.stanceSource = this;
    }

    public double spread()
    {
        return this.spread;
    }

    /** Closes the ranks one step (0.75, 1, 2 times the spacing), or opens them with {@code wider}. */
    public void stepSpread(boolean wider)
    {
        int at = 1;
        for (int i = 0; i < SPREADS.length; i++)
        {
            if (Math.abs(SPREADS[i] - this.spread) < 1.0E-6)
            {
                at = i;
            }
        }
        this.spread = SPREADS[Mth.clamp(at + (wider ? 1 : -1), 0, SPREADS.length - 1)];
        this.slotsAt = -1L;
    }

    /**
     * Whether a Volley unit's archers may loose now: only in the few ticks of
     * each volley, so those at full draw loose together. Always for any other fire.
     */
    public boolean volleyReady()
    {
        NpcChain source = this.stanceSource;
        if (this.stance().fire() != NpcStance.Fire.VOLLEY || !this.inChain())
        {
            return true;
        }
        long beat = this.npc.level().getGameTime() + source.npc.getId();
        return Math.floorMod(beat, AncientEmpiresConfig.chainVolleyTicks) < VOLLEY_WINDOW;
    }

    /**
     * Moves the spot this unit holds {@code blocks} along its facing (back for
     * negative). A unit not holding a spot (following, free) first takes one
     * where it stands, facing its way.
     */
    public void advance(double blocks)
    {
        this.anchorHere();
        double[] forward = NpcFormation.forward(this.orderFacing);
        BlockPos to = BlockPos.containing(this.orderPos.getX() + 0.5D + forward[0] * blocks, this.orderPos.getY(),
                this.orderPos.getZ() + 0.5D + forward[1] * blocks);
        this.setOrder(this.order, to, this.orderFacing, null);
    }

    /** Turns the unit {@code degrees} in place (positive to its right), holding a spot where it stands if it held none. */
    public void turn(float degrees)
    {
        this.anchorHere();
        this.face(this.orderFacing + degrees);
    }

    /** Faces the unit {@code yaw} in place, holding a spot where it stands if it held none. */
    public void face(float yaw)
    {
        this.anchorHere();
        this.orderFacing = Mth.wrapDegrees(yaw);
        this.slotsAt = -1L;
    }

    /** Takes a Hold where the unit stands, facing its way, unless it already holds a spot of its own. */
    private void anchorHere()
    {
        if (!this.holdsOrder() || this.order == null || !this.order.anchored() || this.orderPos == null || Float.isNaN(this.orderFacing))
        {
            Vec3 at = this.origin();
            this.setOrder(NpcOrder.HOLD, BlockPos.containing(at), this.facing(), null);
        }
    }

    /** Lets the unit fight freely without giving up its spot; it re-forms there once the fight is over. */
    public void release()
    {
        this.anchorHere();
        this.released = true;
        this.calmSince = -1L;
        this.planAt = -1L;
    }

    /** Whether this unit's order holder has released it into a free fight. */
    public boolean released()
    {
        return this.holder.released;
    }

    public int unitNumber()
    {
        return this.unitNumber;
    }

    public void setUnitNumber(int number)
    {
        this.unitNumber = Mth.clamp(number, 0, 9);
    }

    public String name()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name.strip();
    }

    /** Whether this unit holds its order from its posts ({@link #posted}). */
    public boolean posted()
    {
        return this.holder.posted;
    }

    /** Puts this unit on its posts (set after its order: a new order takes it off them). */
    public void setPosted(boolean posted)
    {
        this.posted = posted;
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
     * on top of it; Attack anything. Soldiers with a bow reach further. The
     * stance narrows or widens that: an Aggressive unit reaches
     * {@code aggressiveLeash} further, a Passive one fights only what provoked
     * it, archers under Hold fire only what is on top of them, and a released
     * unit anything (but when falling back).
     */
    public boolean allowsTarget(LivingEntity target)
    {
        if (!AncientEmpiresConfig.chainEnabled || !this.inChain())
        {
            return true;
        }
        NpcStance stance = this.stance();
        boolean bow = this.npc.isHoldingBow();
        NpcOrder order = this.effectiveOrder();
        if (order == NpcOrder.FALL_BACK || bow && stance.fire() == NpcStance.Fire.HOLD)
        {
            return this.cornered(target);
        }
        if (stance.engage() == NpcStance.Engage.PASSIVE && !this.provoked(target))
        {
            return false;
        }
        if (this.released())
        {
            return true;
        }
        double reach = (bow ? AncientEmpiresConfig.chainRangedLeash : 0.0D)
                + (stance.engage() == NpcStance.Engage.AGGRESSIVE ? AncientEmpiresConfig.chainAggressiveLeash : 0.0D);
        return switch (order)
        {
            case FREE, ATTACK -> true;
            case FOLLOW -> within(target, this.holder.npc.position(), AncientEmpiresConfig.chainFollowLeash + reach);
            case HOLD -> within(target, this.place(), AncientEmpiresConfig.chainHoldRange + reach);
            case DEFEND -> this.orderPos() == null || within(target, Vec3.atBottomCenterOf(this.orderPos()), AncientEmpiresConfig.chainDefendRadius + reach);
            case FALL_BACK -> this.cornered(target);
        };
    }

    private boolean cornered(LivingEntity target)
    {
        return this.npc.distanceToSqr(target) <= AncientEmpiresConfig.chainCornerRange * AncientEmpiresConfig.chainCornerRange;
    }

    /**
     * Whether {@code target} started it, for a Passive unit: it hit this NPC
     * lately, is after this NPC or someone of its unit, or is on top of it.
     */
    private boolean provoked(LivingEntity target)
    {
        if (this.cornered(target) || target == this.npc.getLastHurtByMob()
                && this.npc.tickCount - this.npc.getLastHurtByMobTimestamp() <= PROVOKED_TICKS)
        {
            return true;
        }
        return target instanceof Mob mob && mob.getTarget() instanceof NpcEntity victim && (victim == this.npc || victim.getChain().sameUnit(this.npc));
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
        link(leader, soldier);
        if (chain.unitNumber == 0 && leader.level() instanceof ServerLevel level && leader.getOwner() != null)
        {
            // A new unit gets the owner's lowest free number, for the order keys.
            chain.unitNumber = NpcArmy.freeNumber(level, leader.getOwner());
        }
        return Assign.ADDED;
    }

    /**
     * Puts {@code soldier} under {@code leader} with no checks: a player's
     * assignment once {@link #toggle} has checked it, or a faction garrison
     * forming up as it comes out ({@link NpcGarrison}), where an acting
     * sergeant of soldier rank may lead.
     */
    static void link(NpcEntity leader, NpcEntity soldier)
    {
        NpcChain chain = leader.getChain();
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
        if (soldier.getRank() != NpcRank.SOLDIER)
        {
            chain.officers.add(soldier.getUUID());
        }
        chain.slotsAt = -1L;
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
        this.assigned.remove(member);
        this.releaseAt.remove(member);
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
            next.stance = this.stance;
            next.spread = this.spread;
            next.posted = this.posted;
            next.unitNumber = this.unitNumber > 0 ? this.unitNumber : next.unitNumber;
            next.name = this.name.isEmpty() ? next.name : this.name;
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
        this.stanceSource = this.stance != null || current == null ? this : current.getChain().stanceSource;
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
        if (this.soldierCount() > 0 && !this.posted() && (this.planAt < 0L || now >= this.planAt))
        {
            this.planAt = now + AncientEmpiresConfig.chainPlanTicks;
            this.npc.level().getProfiler().push("ancientempires_unit_plan");
            this.plan(level, now);
            this.npc.level().getProfiler().pop();
        }
        NpcOrder effective = this.effectiveOrder();
        LivingEntity target = this.npc.getTarget();
        if (this.grunt())
        {
            // A grunt fights what it is handed; it keeps a live foe it already has (one that hit it, say).
            LivingEntity handed = current.getChain().handedTo(this.npc);
            if (handed != null && handed != target && (target == null || !target.isAlive()))
            {
                this.npc.setTarget(handed);
                if (this.npc.isPassenger())
                {
                    // A rider released into the fight charges (VISION: charge bonus).
                    this.chargeUntil = now + AncientEmpiresConfig.chainChargeTicks;
                }
            }
        }
        else if (effective == NpcOrder.ATTACK && target == null && this.holder.orderTarget != null
                && level.getEntity(this.holder.orderTarget) instanceof LivingEntity enemy && enemy.isAlive())
        {
            this.npc.setTarget(enemy);
        }
        if (this.npc.getTarget() != null && now % 10L == Math.floorMod(this.npc.getId(), 10) && !this.allowsTarget(this.npc.getTarget()))
        {
            // Out of what the order allows (a holding line does not chase off).
            this.npc.setTarget(null);
        }
    }

    /**
     * The leader thinks for its unit: one scan of the shared candidates around
     * it (the attack order's enemy alone, if there is one), then each soldier
     * keeps a target that is still alive and allowed, and the rest are handed
     * the nearest enemy, weighted by how many are on it already
     * ({@code chain.spreadWeight} blocks each), released {@code releaseStagger}
     * ticks apart, nearest first. Nobody is handed anything under Free or Fall back.
     */
    private void plan(ServerLevel level, long now)
    {
        NpcOrder order = this.effectiveOrder();
        if (order == NpcOrder.FREE || order == NpcOrder.FALL_BACK)
        {
            this.assigned.clear();
            this.releaseAt.clear();
            return;
        }
        List<LivingEntity> enemies = new ArrayList<>();
        if (order == NpcOrder.ATTACK && this.holder.orderTarget != null
                && level.getEntity(this.holder.orderTarget) instanceof LivingEntity ordered && ordered.isAlive())
        {
            // An attack order: everyone on that one.
            enemies.add(ordered);
        }
        else
        {
            double range = AncientEmpiresConfig.chainEngageRange;
            if (this.released() || this.stance().engage() == NpcStance.Engage.AGGRESSIVE)
            {
                range += AncientEmpiresConfig.chainAggressiveLeash;
            }
            for (LivingEntity candidate : NpcTargetCache.candidates(this.npc, range))
            {
                if (candidate != this.npc && candidate.isAlive() && !candidate.isRemoved() && this.npc.distanceToSqr(candidate) <= range * range
                        && this.npc.getRelations().scanTarget(candidate))
                {
                    enemies.add(candidate);
                }
            }
        }
        if (this.holder.released)
        {
            // A released unit re-forms once nothing has been in reach for a while.
            if (!enemies.isEmpty())
            {
                this.holder.calmSince = -1L;
            }
            else if (this.holder.calmSince < 0L)
            {
                this.holder.calmSince = now;
            }
            else if (now - this.holder.calmSince >= AncientEmpiresConfig.chainReleaseCalmTicks)
            {
                this.holder.released = false;
            }
        }
        List<NpcEntity> waiting = new ArrayList<>();
        Map<LivingEntity, Integer> load = new HashMap<>();
        for (UUID id : this.members)
        {
            if (this.officers.contains(id) || !(level.getEntity(id) instanceof NpcEntity soldier) || !soldier.isAlive() || soldier.avoidsFights())
            {
                this.assigned.remove(id);
                continue;
            }
            LivingEntity kept = this.assigned.get(id);
            if (kept != null && kept.isAlive() && enemies.contains(kept) && soldier.getChain().allowsTarget(kept))
            {
                load.merge(kept, 1, Integer::sum);
                continue;
            }
            this.assigned.remove(id);
            this.releaseAt.remove(id);
            waiting.add(soldier);
        }
        if (enemies.isEmpty() || waiting.isEmpty())
        {
            return;
        }
        // The front goes first: those nearest an enemy.
        waiting.sort((a, b) -> Double.compare(nearest(a, enemies), nearest(b, enemies)));
        double spread = AncientEmpiresConfig.chainSpreadWeight;
        int turn = 0;
        for (NpcEntity soldier : waiting)
        {
            LivingEntity best = null;
            double bestScore = Double.MAX_VALUE;
            for (LivingEntity enemy : enemies)
            {
                if (!soldier.getChain().allowsTarget(enemy))
                {
                    continue;
                }
                double score = Math.sqrt(soldier.distanceToSqr(enemy)) + load.getOrDefault(enemy, 0) * spread;
                if (score < bestScore)
                {
                    bestScore = score;
                    best = enemy;
                }
            }
            if (best != null)
            {
                this.assigned.put(soldier.getUUID(), best);
                this.releaseAt.put(soldier.getUUID(), now + (long) turn * AncientEmpiresConfig.chainReleaseStagger);
                load.merge(best, 1, Integer::sum);
                turn++;
            }
        }
    }

    private static double nearest(NpcEntity soldier, List<LivingEntity> enemies)
    {
        double best = Double.MAX_VALUE;
        for (LivingEntity enemy : enemies)
        {
            best = Math.min(best, soldier.distanceToSqr(enemy));
        }
        return best;
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
        if (this.march != March.NONE)
        {
            this.npc.level().getProfiler().push("ancientempires_unit_march");
            this.marchStep(level.getGameTime());
            this.npc.level().getProfiler().pop();
        }
    }

    /**
     * Forming: done once {@code formUpReady} of the unit stands in its slots,
     * or after {@code formUpTicks}. Marching: the leader halts while more than
     * {@code marchStraggleShare} of the unit is {@code marchStraggle} off its
     * slots, and deploys at the spot.
     */
    private void marchStep(long now)
    {
        if (this.orderPos == null || this.order == null || !this.order.anchored() || this.members.isEmpty())
        {
            this.march = March.NONE;
            return;
        }
        if (this.march == March.FORMING)
        {
            if (now - this.marchSince >= AncientEmpiresConfig.chainFormUpTicks
                    || this.shareWithin(AncientEmpiresConfig.chainSlotTolerance) >= AncientEmpiresConfig.chainFormUpReady)
            {
                this.march = March.MARCHING;
                this.marchSince = now;
                this.slotsAt = -1L;
            }
            return;
        }
        Vec3 spot = Vec3.atBottomCenterOf(this.orderPos);
        if (this.npc.position().distanceToSqr(spot.x, this.npc.getY(), spot.z) <= DEPLOY * DEPLOY)
        {
            this.march = March.NONE;
            this.straggling = false;
            this.slotsAt = -1L;
            return;
        }
        if (now % MARCH_CHECK_TICKS == Math.floorMod(this.npc.getId(), MARCH_CHECK_TICKS))
        {
            double off = 1.0D - this.shareWithin(AncientEmpiresConfig.chainMarchStraggle);
            // Halts past the share and goes on once half of that have caught up.
            this.straggling = this.straggling ? off > AncientEmpiresConfig.chainMarchStraggleShare / 2.0D : off > AncientEmpiresConfig.chainMarchStraggleShare;
        }
    }

    /** The share of this leader's loaded members within {@code range} of their slots (1 for none). */
    private double shareWithin(double range)
    {
        if (!(this.npc.level() instanceof ServerLevel level))
        {
            return 1.0D;
        }
        int counted = 0;
        int within = 0;
        for (UUID id : this.members)
        {
            if (level.getEntity(id) instanceof NpcEntity member && member.isAlive())
            {
                Vec3 slot = this.slotOf(member);
                if (slot == null)
                {
                    continue;
                }
                counted++;
                if (member.position().distanceToSqr(slot.x, member.getY(), slot.z) <= range * range)
                {
                    within++;
                }
            }
        }
        return counted == 0 ? 1.0D : (double) within / counted;
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

    /**
     * The way this unit faces, in degrees (a mob's yaw): toward the spot while
     * forming up, the way its order holder walks while marching, the order's
     * facing at a held spot, else where it walks.
     */
    public float facing()
    {
        NpcChain over = this.holder;
        if (over.march == March.FORMING)
        {
            return over.marchHeading;
        }
        if (over.march == March.MARCHING)
        {
            return Float.isNaN(over.facing) ? over.marchHeading : over.facing;
        }
        if (this.effectiveOrder().anchored() && !Float.isNaN(over.orderFacing))
        {
            return over.orderFacing;
        }
        return Float.isNaN(this.facing) ? this.npc.getYRot() : this.facing;
    }

    /** The way {@code member} faces in its slot: out of a square, else the unit's way. */
    public float facingOf(NpcEntity member)
    {
        Float own = this.slotFacings.get(member.getUUID());
        return own != null ? own : this.facing();
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
        Vec3 here = this.origin();
        float facing = this.facing();
        // Slots stay put until the leader has really moved or turned: shuffling about its spot no longer
        // nudges the whole unit, and a marching column trails its leader rather than re-forming each step.
        double moved = this.fixedOrigin() ? 0.25D : this.holdsOrder() && this.march == March.MARCHING ? MARCH_SLOT_MOVE * MARCH_SLOT_MOVE : SLOT_MOVE * SLOT_MOVE;
        if (this.slotsAt < 0L || this.slotsFormation != this.formation || now - this.slotsAt > SLOT_TICKS
                && (this.slotsFrom.distanceToSqr(here) > moved || Math.abs(Mth.wrapDegrees(facing - this.slotsFacing)) > SLOT_TURN))
        {
            this.layout(here, facing);
            this.slotsAt = now;
        }
        return this.slots.get(member.getUUID());
    }

    /**
     * Where this leader's formation is laid out from: the order's spot itself
     * while it holds one there (its own shuffling about the spot moves no slot),
     * where it stood while its unit forms up for a march, else where the leader
     * stands.
     */
    private Vec3 origin()
    {
        if (this.holdsOrder() && this.march == March.FORMING && this.formFrom != null)
        {
            return this.formFrom;
        }
        if (this.holdsOrder() && this.orderPos != null && this.effectiveOrder().anchored() && this.march == March.NONE)
        {
            return Vec3.atBottomCenterOf(this.orderPos);
        }
        if (this.holdsOrder() && this.march == March.MARCHING)
        {
            // Laid out a re-layout's walk ahead, so the slots lead the marching leader rather than trail it.
            double[] forward = NpcFormation.forward(this.facing());
            return this.npc.position().add(forward[0] * MARCH_SLOT_MOVE, 0.0D, forward[1] * MARCH_SLOT_MOVE);
        }
        return this.npc.position();
    }

    /** Whether the layout's origin stands still (a held spot, or where a unit forms up), not on the walking leader. */
    private boolean fixedOrigin()
    {
        return this.holdsOrder() && (this.march == March.FORMING
                || this.march == March.NONE && this.orderPos != null && this.effectiveOrder().anchored());
    }

    /** Whether {@code other} is in the same unit (the same order holder over both): they do not shove each other. */
    public boolean sameUnit(NpcEntity other)
    {
        return this.inChain() && other.getChain().inChain() && other.getChain().holder == this.holder;
    }

    private void layout(Vec3 here, float facing)
    {
        this.slots.clear();
        this.slotsFrom = here;
        this.slotsFacing = facing;
        this.slotsFormation = this.formation;
        this.slotFacings.clear();
        double[] forward = NpcFormation.forward(facing);
        // A mob facing yaw 0 looks south (+z); its right is west (-x).
        double forwardX = forward[0];
        double forwardZ = forward[1];
        double rightX = -forwardZ;
        double rightZ = forwardX;
        int lineWidth = AncientEmpiresConfig.chainLineWidth;
        int columnWidth = AncientEmpiresConfig.chainColumnWidth;
        double spacing = AncientEmpiresConfig.chainSpacing * this.spread;
        NpcFormation shape = this.formation;
        NpcFormation support = shape.support();
        // Melee on foot in the front ranks, archers in the rows behind them, riders behind those at double spacing.
        List<UUID> soldiers = new ArrayList<>();
        List<UUID> archers = new ArrayList<>();
        List<UUID> riders = new ArrayList<>();
        List<UUID> leaders = new ArrayList<>();
        for (UUID id : this.members)
        {
            Entity member = this.npc.level() instanceof ServerLevel level ? level.getEntity(id) : null;
            if (this.officers.contains(id))
            {
                leaders.add(id);
            }
            else if (member instanceof NpcEntity npc && npc.isPassenger())
            {
                riders.add(id);
            }
            else if (member instanceof NpcEntity npc && npc.isHoldingBow())
            {
                archers.add(id);
            }
            else
            {
                soldiers.add(id);
            }
        }
        double riderSpacing = spacing * AncientEmpiresConfig.chainRiderSpacing;
        int riderWidth = Math.max(1, lineWidth / 2);
        boolean mounted = soldiers.isEmpty() && archers.isEmpty() && !riders.isEmpty();
        if (mounted)
        {
            // Horse alone: the riders are the front ranks, at their own spacing.
            soldiers = riders;
            riders = new ArrayList<>();
        }
        double meleeSpacing = mounted ? riderSpacing : spacing;
        int meleeWidth = mounted ? riderWidth : lineWidth;
        double front = 0.0D;
        for (int i = 0; i < soldiers.size(); i++)
        {
            double[] slot = shape.soldierSlot(i, soldiers.size(), meleeWidth, columnWidth, meleeSpacing);
            front = Math.max(front, -slot[1]);
            Vec3 at = place(here, slot, forwardX, forwardZ, rightX, rightZ);
            this.slots.put(soldiers.get(i), at);
            if (shape.facesOut() && slot[0] * slot[0] + slot[1] * slot[1] > 1.0E-6)
            {
                this.slotFacings.put(soldiers.get(i), NpcFormation.yawToward(here.x, here.z, at.x, at.z));
            }
        }
        if (shape == NpcFormation.SQUARE)
        {
            // Archers and riders inside the square.
            List<UUID> inside = new ArrayList<>(archers);
            inside.addAll(riders);
            int side = NpcFormation.squareSide(Math.max(1, soldiers.size()));
            for (int i = 0; i < inside.size(); i++)
            {
                double[] slot = NpcFormation.insideSquare(i, inside.size(), side, shape.spacing(spacing));
                this.slots.put(inside.get(i), place(here, slot, forwardX, forwardZ, rightX, rightZ));
            }
        }
        else
        {
            // Archers start a row of their own behind the last melee row, however short that one is.
            double rowGap = support.spacing(spacing);
            double rear = soldiers.isEmpty() ? -support.lead(spacing) - rowGap : shape.rearmost(soldiers.size(), meleeWidth, columnWidth, meleeSpacing);
            double archerShift = rear + rowGap + support.lead(spacing);
            for (int i = 0; i < archers.size(); i++)
            {
                double[] slot = support.soldierSlot(i, archers.size(), lineWidth, columnWidth, spacing);
                slot[1] += archerShift;
                front = Math.max(front, -slot[1]);
                this.slots.put(archers.get(i), place(here, slot, forwardX, forwardZ, rightX, rightZ));
            }
            if (!archers.isEmpty())
            {
                rear = support.rearmost(archers.size(), lineWidth, columnWidth, spacing) + archerShift;
            }
            NpcFormation riderShape = shape == NpcFormation.WEDGE ? NpcFormation.WEDGE : support;
            double riderBack = Math.max(0.0D, rear + rowGap) + riderShape.spacing(riderSpacing)
                    - riderShape.frontmost(riders.size(), riderWidth, columnWidth, riderSpacing);
            for (int i = 0; i < riders.size(); i++)
            {
                double[] slot = riderShape.soldierSlot(i, riders.size(), riderWidth, columnWidth, riderSpacing);
                slot[1] += riderBack;
                this.slots.put(riders.get(i), place(here, slot, forwardX, forwardZ, rightX, rightZ));
            }
        }
        // Officers' units, assumed full: as wide and deep as a whole squad in this formation, standing ahead.
        int squad = AncientEmpiresConfig.chainSquadSize;
        double frontage = shape.frontage(squad, lineWidth, columnWidth, spacing);
        double behind = shape.depthBehind(squad, lineWidth, columnWidth, spacing);
        double depth = -shape.frontmost(squad, lineWidth, columnWidth, spacing) + behind;
        for (int i = 0; i < leaders.size(); i++)
        {
            double[] slot = shape.officerSlot(i, leaders.size(), front, frontage, behind, depth, spacing);
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
        if (!this.name.isEmpty())
        {
            tag.putString(NAME_KEY, this.name);
        }
        if (this.posted)
        {
            tag.putBoolean(POSTED_KEY, true);
        }
        if (this.unitNumber > 0)
        {
            tag.putInt(NUMBER_KEY, this.unitNumber);
        }
        if (this.stance != null)
        {
            tag.put(STANCE_KEY, this.stance.save());
        }
        if (this.spread != 1.0D)
        {
            tag.putDouble(SPREAD_KEY, this.spread);
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
        this.name = tag.getString(NAME_KEY);
        this.posted = tag.getBoolean(POSTED_KEY);
        this.unitNumber = Mth.clamp(tag.getInt(NUMBER_KEY), 0, 9);
        this.stance = tag.contains(STANCE_KEY) ? NpcStance.load(tag.getCompound(STANCE_KEY)) : null;
        this.spread = tag.contains(SPREAD_KEY) ? Mth.clamp(tag.getDouble(SPREAD_KEY), 0.5D, 3.0D) : 1.0D;
        this.holder = this;
        this.stanceSource = this;
    }
}
