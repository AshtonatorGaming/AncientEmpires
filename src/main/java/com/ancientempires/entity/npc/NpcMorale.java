package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Total War style morale for soldiers ({@code morale} config). Morale runs 0
 * to 100 and, once a second, drifts toward what the soldier's situation is
 * worth ({@link #target}): its rank's base, a commander's aura, the odds
 * around it (each side weighed by health), its wounds, a recent hit from
 * behind, hunger, and routing allies. Deaths and kills add shocks that wear
 * off. It falls faster than it rises.
 * <p>
 * Steady, then wavering (a badge), then routing: the soldier runs as a
 * civilian does ({@link NpcEntity#avoidsFights()}) and fights only when
 * cornered, and rallies once its morale has recovered and the rout has lasted
 * {@code minRoutTicks}. The {@code shatterAfterRouts}th rout shatters it: it
 * never rallies, and a faction soldier leaves the field once no player is
 * near; a player's own soldier pulls itself together after a long while clear
 * of enemies. A cornered, badly hurt faction router may surrender: it kneels,
 * and NPCs leave it be; hitting it shatters it.
 * <p>
 * A peaceful soldier (no target, not hurt lately, steady, no shock) only
 * drifts back to its base: the area count runs only for soldiers in a fight.
 * Saved as {@code AeMorale}, {@code AeMoraleState}, {@code AeRouts}.
 */
public final class NpcMorale
{
    private static final String VALUE_KEY = "AeMorale";
    private static final String STATE_KEY = "AeMoraleState";
    private static final String ROUTS_KEY = "AeRouts";
    /** Hurt this recently still counts as being in a fight. */
    private static final int FIGHT_MEMORY_TICKS = 200;
    /** The front arc, in degrees: hits from outside it are from the side or behind. */
    private static final double FLANK_ANGLE = 120.0D;

    public enum State
    {
        STEADY,
        WAVERING,
        ROUTING,
        SHATTERED,
        SURRENDERED;

        /** Running or given up: out of the fight. */
        public boolean broken()
        {
            return this.ordinal() >= ROUTING.ordinal();
        }
    }

    /** What morale sees around a soldier, for {@link #target}. Forces are summed health over 20 (a full-health man is 1). */
    record Situation(float base, float inspired, double allies, double enemies, float health, boolean flanked, boolean hungry, int routingAllies,
            float presence)
    {
    }

    /** Why morale stands where it does, as bits, for the NPC's stats panel. */
    public static final int REASON_OUTNUMBERED = 0;
    public static final int REASON_OUTNUMBERING = 1;
    public static final int REASON_WOUNDED = 2;
    public static final int REASON_FLANKED = 3;
    public static final int REASON_HUNGRY = 4;
    public static final int REASON_PANIC = 5;
    public static final int REASON_SHOCK = 6;
    public static final int REASON_HEARTENED = 7;
    public static final int REASON_INSPIRED = 8;
    public static final int REASON_OWNER = 9;
    public static final int REASON_ROUTED_BEFORE = 10;
    public static final int REASON_COUNT = 11;
    /** A term this large (in morale points) is worth naming. */
    private static final double REASON_THRESHOLD = 4.0D;

    /** The weights of {@link #target}, from the config. */
    record Weights(double outnumber, double wounds, double flank, double hunger, double routingAlly, double routingCap, double inspired)
    {
        static Weights fromConfig()
        {
            return new Weights(AncientEmpiresConfig.moraleOutnumberWeight, AncientEmpiresConfig.moraleWoundWeight,
                    AncientEmpiresConfig.moraleFlankPenalty, AncientEmpiresConfig.moraleHungerPenalty,
                    AncientEmpiresConfig.moraleRoutingAllyPenalty, AncientEmpiresConfig.moraleRoutingAllyCap,
                    AncientEmpiresConfig.moraleInspiredBonus);
        }
    }

    private final NpcEntity npc;
    /** -1 until first set: a new soldier starts at its base. */
    private float value = -1.0F;
    private State state = State.STEADY;
    private int routs;
    /** Shock from deaths and kills, added to the target and wearing off. */
    private float shock;
    private int routedAt;
    private int flankedAt = -1;
    /** Checks in a row with no enemy in range, for recovery and release. */
    private int clearTicks;
    /** The reasons found at the last check ({@code REASON_} bits). */
    private int reasons;

    NpcMorale(NpcEntity npc)
    {
        this.npc = npc;
    }

    /** Morale is on and this NPC has it: a soldier (not a civilian) of a faction that can break. */
    public boolean applies()
    {
        if (!AncientEmpiresConfig.moraleEnabled || !this.npc.getNpcPath().combat() || this.npc.isCreative())
        {
            return false;
        }
        if (this.npc.isFactionNpc())
        {
            return !NpcFactions.fearless(this.npc.getFaction());
        }
        return AncientEmpiresConfig.moraleOwnedNpcs;
    }

    public State state()
    {
        return this.state;
    }

    public boolean isBroken()
    {
        return this.state.broken();
    }

    public boolean isSurrendered()
    {
        return this.state == State.SURRENDERED;
    }

    /** The reasons found at the last check, as {@code 1 << REASON_...} bits. */
    public int reasons()
    {
        return this.reasons;
    }

    public float value()
    {
        return this.value < 0.0F ? this.base() : this.value;
    }

    private float base()
    {
        // Read along the list by level, as the other skills are; a player's own soldiers a little steadier.
        List<? extends Double> bases = AncientEmpiresConfig.moraleBaseByTier;
        float base = bases.isEmpty() ? 60.0F : (float) this.npc.skillValue(bases);
        return this.npc.isOwnedNpc() ? base + (float) AncientEmpiresConfig.moraleOwnedBaseBonus : base;
    }

    void tick()
    {
        if (!this.applies())
        {
            if (this.state != State.STEADY)
            {
                this.recover();
            }
            return;
        }
        int interval = AncientEmpiresConfig.moraleUpdateTicks;
        if ((this.npc.tickCount + this.npc.getId()) % interval != 0)
        {
            return;
        }
        this.npc.level().getProfiler().push("ancientempires_morale");
        float base = this.base();
        if (this.value < 0.0F)
        {
            this.value = base;
        }
        this.shock = decay(this.shock, (float) AncientEmpiresConfig.moraleShockDecay);
        if (this.isCalm())
        {
            // Nothing going on: no count of the area, just back toward the base.
            float presence = this.presence();
            this.value = drift(this.value, base + this.inspiredBonus() + presence, AncientEmpiresConfig.moraleRiseRate, AncientEmpiresConfig.moraleFallRate);
            this.reasons = (this.npc.isInspired() ? 1 << REASON_INSPIRED : 0) | (presence > 0.0F ? 1 << REASON_OWNER : 0);
            this.npc.level().getProfiler().pop();
            return;
        }
        Situation situation = this.look(base);
        Weights weights = Weights.fromConfig();
        float target = target(situation, weights) + this.shock;
        this.reasons = reasons(situation, weights, this.shock) | (this.routs > 0 ? 1 << REASON_ROUTED_BEFORE : 0);
        this.value = drift(this.value, target, AncientEmpiresConfig.moraleRiseRate, AncientEmpiresConfig.moraleFallRate);
        this.clearTicks = situation.enemies() > 0.0D ? 0 : this.clearTicks + interval;
        this.step();
        this.npc.level().getProfiler().pop();
    }

    private boolean isCalm()
    {
        if (this.state != State.STEADY || this.shock != 0.0F || this.npc.getTarget() != null)
        {
            return false;
        }
        return this.npc.getLastHurtByMob() == null || this.npc.tickCount - this.npc.getLastHurtByMobTimestamp() > FIGHT_MEMORY_TICKS;
    }

    private float inspiredBonus()
    {
        return this.npc.isInspired() ? this.npc.getInspiredStrength() * (float) AncientEmpiresConfig.moraleInspiredBonus : 0.0F;
    }

    /** One area query: allies and enemies by health, and routing allies. */
    private Situation look(float base)
    {
        double range = AncientEmpiresConfig.moraleRange;
        AABB area = this.npc.getBoundingBox().inflate(range, 4.0D, range);
        double allies = weight(this.npc);
        double enemies = 0.0D;
        int routing = 0;
        for (LivingEntity other : this.npc.level().getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive))
        {
            if (other == this.npc)
            {
                continue;
            }
            if (other instanceof NpcEntity npc)
            {
                if (this.npc.isAlly(npc))
                {
                    if (npc.isCivilian())
                    {
                        continue;
                    }
                    if (npc.getMorale().isBroken())
                    {
                        routing++;
                    }
                    else
                    {
                        allies += weight(npc);
                    }
                    continue;
                }
                if (npc.getMorale().isBroken())
                {
                    // A routing enemy is no threat.
                    continue;
                }
            }
            if (this.npc.isScanTarget(other) || (other instanceof Mob mob && mob.getTarget() == this.npc))
            {
                enemies += weight(other);
            }
        }
        boolean flanked = this.flankedAt >= 0 && this.npc.tickCount - this.flankedAt <= AncientEmpiresConfig.moraleFlankTicks;
        float health = this.npc.getMaxHealth() <= 0.0F ? 1.0F : this.npc.getHealth() / this.npc.getMaxHealth();
        return new Situation(base, this.npc.isInspired() ? this.npc.getInspiredStrength() : 0.0F, allies, enemies, health, flanked,
                this.npc.isHungry(), routing, this.presence());
    }

    /** A player's soldiers take heart from their owner fighting close by ({@code ownerPresenceBonus}). */
    private float presence()
    {
        if (!this.npc.isOwnedNpc() || AncientEmpiresConfig.moraleOwnerPresenceBonus <= 0.0D)
        {
            return 0.0F;
        }
        Player owner = this.npc.level().getPlayerByUUID(this.npc.getOwner());
        double range = AncientEmpiresConfig.moraleOwnerPresenceRange;
        return owner != null && owner.isAlive() && !owner.isSpectator() && owner.distanceToSqr(this.npc) <= range * range
                ? (float) AncientEmpiresConfig.moraleOwnerPresenceBonus : 0.0F;
    }

    /** A fighter's weight in the odds: its health, a full-health man being 1 (a giant counts for several). */
    private static double weight(LivingEntity living)
    {
        return Mth.clamp(living.getHealth() / 20.0D, 0.1D, 8.0D);
    }

    /** Moves between states after a check. */
    private void step()
    {
        switch (this.state)
        {
            case STEADY, WAVERING ->
            {
                if (this.value < AncientEmpiresConfig.moraleRoutBelow)
                {
                    this.rout();
                }
                else
                {
                    this.state = this.value < AncientEmpiresConfig.moraleWaverBelow ? State.WAVERING : State.STEADY;
                }
            }
            case ROUTING ->
            {
                if (this.value >= AncientEmpiresConfig.moraleRallyAbove && this.npc.tickCount - this.routedAt >= AncientEmpiresConfig.moraleMinRoutTicks)
                {
                    this.rally();
                }
            }
            case SHATTERED ->
            {
                if (!this.npc.isFactionNpc())
                {
                    if (this.clearTicks >= AncientEmpiresConfig.moraleShatteredRecoverTicks)
                    {
                        // A player's own soldier pulls itself together, its routs forgiven.
                        this.routs = 0;
                        this.rally();
                    }
                }
                else if (AncientEmpiresConfig.moraleShatteredDesert && !this.npc.hasCustomName() && !this.playerNear())
                {
                    this.npc.desert();
                }
            }
            case SURRENDERED ->
            {
                if (this.clearTicks >= AncientEmpiresConfig.moraleSurrenderReleaseTicks)
                {
                    // Nobody came to take it: it gets up and goes.
                    this.shatter();
                }
            }
        }
    }

    private boolean playerNear()
    {
        Player near = this.npc.level().getNearestPlayer(this.npc, AncientEmpiresConfig.moraleDesertRange);
        return near != null && !near.isSpectator();
    }

    private void rout()
    {
        this.routs++;
        this.routedAt = this.npc.tickCount;
        this.state = this.routs >= AncientEmpiresConfig.moraleShatterAfterRouts ? State.SHATTERED : State.ROUTING;
        this.clearTicks = 0;
        this.npc.onBroken();
    }

    private void shatter()
    {
        this.leaveSurrender();
        this.state = State.SHATTERED;
        this.clearTicks = 0;
        this.npc.onBroken();
    }

    /** Back in the fight, wavering. */
    private void rally()
    {
        this.leaveSurrender();
        this.state = State.WAVERING;
        this.value = Math.max(this.value, (float) AncientEmpiresConfig.moraleRallyAbove);
        this.shock = Math.max(this.shock, 0.0F);
        this.clearTicks = 0;
    }

    /** Morale off for this NPC (config, path, faction): steady at its base. */
    private void recover()
    {
        this.leaveSurrender();
        this.state = State.STEADY;
        this.value = -1.0F;
        this.shock = 0.0F;
        this.routs = 0;
    }

    private void leaveSurrender()
    {
        if (this.state == State.SURRENDERED && this.npc.getPose() == Pose.CROUCHING)
        {
            this.npc.setPose(Pose.STANDING);
        }
    }

    /** A War Horn rally: morale up to {@code hornRally}, and the routing (not the shattered) turn back. */
    void hornRally()
    {
        if (!this.applies())
        {
            return;
        }
        this.value = Math.max(this.value(), (float) AncientEmpiresConfig.moraleHornRally);
        this.shock = Math.max(this.shock, 0.0F);
        if (this.state == State.ROUTING)
        {
            this.rally();
        }
        else if (this.state == State.WAVERING && this.value >= AncientEmpiresConfig.moraleWaverBelow)
        {
            this.state = State.STEADY;
        }
    }

    /** Hit: a hit from the side or behind counts as flanked, and a surrendered soldier that is hit shatters. */
    void onHurt(DamageSource source)
    {
        if (!this.applies())
        {
            return;
        }
        Entity attacker = source.getEntity();
        if (this.state == State.SURRENDERED && attacker != null)
        {
            this.shatter();
            return;
        }
        if (attacker != null && fromBehind(this.npc.yBodyRot, attacker.getX() - this.npc.getX(), attacker.getZ() - this.npc.getZ(), FLANK_ANGLE))
        {
            this.flankedAt = this.npc.tickCount;
        }
    }

    void addShock(float amount)
    {
        if (this.applies())
        {
            this.shock += amount;
        }
    }

    /**
     * Cornered while routing: a faction soldier that is badly hurt may give
     * up ({@code morale.surrender}). Returns whether it did; otherwise it
     * stands and fights its way out.
     */
    boolean trySurrender()
    {
        if (!AncientEmpiresConfig.moraleSurrender || !this.npc.isFactionNpc() || this.state != State.ROUTING && this.state != State.SHATTERED
                || this.npc.getHealth() >= this.npc.getMaxHealth() * AncientEmpiresConfig.moraleSurrenderHealth
                || this.npc.getRandom().nextDouble() >= AncientEmpiresConfig.moraleSurrenderChance)
        {
            return false;
        }
        this.surrender();
        return true;
    }

    private void surrender()
    {
        this.state = State.SURRENDERED;
        this.clearTicks = 0;
        this.npc.getNavigation().stop();
        this.npc.setTarget(null);
        this.npc.setPose(Pose.CROUCHING);
        double range = AncientEmpiresConfig.moraleRange;
        for (NpcEntity other : this.npc.level().getEntitiesOfClass(NpcEntity.class, this.npc.getBoundingBox().inflate(range, 4.0D, range),
                other -> other.getTarget() == this.npc))
        {
            // Its enemies leave it be; a fresh scan will not pick it either.
            other.setTarget(null);
        }
    }

    /**
     * Admins and testing ({@code /ancientempires morale}): puts the NPC straight
     * into a state, as the real causes would. Returns false where morale does
     * not apply (civilians, fearless factions, morale off).
     */
    public boolean force(State wanted)
    {
        if (!this.applies())
        {
            return false;
        }
        switch (wanted)
        {
            case STEADY ->
            {
                this.leaveSurrender();
                this.state = State.STEADY;
                this.value = this.base();
                this.shock = 0.0F;
                this.routs = 0;
            }
            case WAVERING ->
            {
                this.leaveSurrender();
                this.state = State.WAVERING;
                this.value = (float) ((AncientEmpiresConfig.moraleWaverBelow + AncientEmpiresConfig.moraleRoutBelow) / 2.0D);
            }
            case ROUTING ->
            {
                this.leaveSurrender();
                this.value = (float) Math.max(0.0D, AncientEmpiresConfig.moraleRoutBelow - 1.0D);
                this.routs = Math.max(0, Math.min(this.routs, AncientEmpiresConfig.moraleShatterAfterRouts - 2));
                this.rout();
            }
            case SHATTERED -> this.shatter();
            case SURRENDERED ->
            {
                this.value = 0.0F;
                this.surrender();
            }
        }
        return true;
    }

    /**
     * A death felt by those around it: allies close by take {@code allyDeathShock},
     * and if a commander fell, every ally in {@code commanderDeathRange} takes
     * {@code commanderDeathShock} times its command strength. The killer, if a soldier, takes heart.
     */
    static void onDeath(NpcEntity fallen, float command, @Nullable Entity killer)
    {
        if (!AncientEmpiresConfig.moraleEnabled)
        {
            return;
        }
        if (killer instanceof NpcEntity victor && !victor.isAlly(fallen))
        {
            victor.getMorale().addShock((float) AncientEmpiresConfig.moraleKillBoost);
        }
        boolean commander = command > 0.0F;
        double allyRange = AncientEmpiresConfig.moraleAllyDeathRange;
        double range = commander ? Math.max(allyRange, AncientEmpiresConfig.moraleCommanderDeathRange) : allyRange;
        if (range <= 0.0D)
        {
            return;
        }
        double allySqr = allyRange * allyRange;
        for (NpcEntity ally : fallen.level().getEntitiesOfClass(NpcEntity.class, fallen.getBoundingBox().inflate(range, 6.0D, range), fallen::isAlly))
        {
            float shock = 0.0F;
            if (ally.distanceToSqr(fallen) <= allySqr)
            {
                shock += (float) AncientEmpiresConfig.moraleAllyDeathShock;
            }
            if (commander)
            {
                // Scaled by rank: a sergeant's fall shakes less than a general's.
                shock += (float) AncientEmpiresConfig.moraleCommanderDeathShock * command;
            }
            ally.getMorale().addShock(-shock);
        }
    }

    void save(CompoundTag tag)
    {
        if (this.value >= 0.0F)
        {
            tag.putFloat(VALUE_KEY, this.value);
        }
        if (this.state != State.STEADY)
        {
            tag.putByte(STATE_KEY, (byte) this.state.ordinal());
        }
        if (this.routs > 0)
        {
            tag.putInt(ROUTS_KEY, this.routs);
        }
    }

    void load(CompoundTag tag)
    {
        this.value = tag.contains(VALUE_KEY) ? tag.getFloat(VALUE_KEY) : -1.0F;
        int state = tag.getByte(STATE_KEY);
        this.state = state >= 0 && state < State.values().length ? State.values()[state] : State.STEADY;
        this.routs = tag.getInt(ROUTS_KEY);
        if (this.state == State.SURRENDERED)
        {
            this.npc.setPose(Pose.CROUCHING);
        }
    }

    /**
     * The morale a situation is worth. The odds run from -1 (alone against
     * many) to +1 (many against none) and are worth {@code outnumber} either
     * way; wounds cost up to {@code wounds} at the brink of death.
     */
    static float target(Situation situation, Weights weights)
    {
        double total = situation.allies() + situation.enemies();
        double odds = total <= 0.0D ? 0.0D : (situation.allies() - situation.enemies()) / total;
        double target = situation.base()
                + situation.inspired() * weights.inspired()
                + odds * weights.outnumber()
                - (1.0D - Mth.clamp(situation.health(), 0.0F, 1.0F)) * weights.wounds()
                - (situation.flanked() ? weights.flank() : 0.0D)
                - (situation.hungry() ? weights.hunger() : 0.0D)
                - Math.min(weights.routingCap(), situation.routingAllies() * weights.routingAlly())
                + situation.presence();
        return (float) target;
    }

    /** Which terms of {@link #target} (and the shock) matter enough to name, as {@code 1 << REASON_...} bits. */
    static int reasons(Situation situation, Weights weights, float shock)
    {
        double total = situation.allies() + situation.enemies();
        double odds = total <= 0.0D ? 0.0D : (situation.allies() - situation.enemies()) / total * weights.outnumber();
        int bits = 0;
        bits |= odds <= -REASON_THRESHOLD ? 1 << REASON_OUTNUMBERED : 0;
        bits |= odds >= REASON_THRESHOLD && situation.enemies() > 0.0D ? 1 << REASON_OUTNUMBERING : 0;
        bits |= (1.0D - Mth.clamp(situation.health(), 0.0F, 1.0F)) * weights.wounds() >= REASON_THRESHOLD ? 1 << REASON_WOUNDED : 0;
        bits |= situation.flanked() && weights.flank() > 0.0D ? 1 << REASON_FLANKED : 0;
        bits |= situation.hungry() && weights.hunger() > 0.0D ? 1 << REASON_HUNGRY : 0;
        bits |= situation.routingAllies() > 0 && weights.routingAlly() > 0.0D ? 1 << REASON_PANIC : 0;
        bits |= shock <= -REASON_THRESHOLD ? 1 << REASON_SHOCK : 0;
        bits |= shock >= REASON_THRESHOLD ? 1 << REASON_HEARTENED : 0;
        bits |= situation.inspired() > 0.0F && weights.inspired() > 0.0D ? 1 << REASON_INSPIRED : 0;
        bits |= situation.presence() > 0.0F ? 1 << REASON_OWNER : 0;
        return bits;
    }

    /** Morale closes {@code rise} or {@code fall} of the gap to the target per check, kept within 0 to 100. */
    static float drift(float value, float target, double rise, double fall)
    {
        float gap = target - value;
        return Mth.clamp(value + gap * (float) (gap > 0.0F ? rise : fall), 0.0F, 100.0F);
    }

    /** A shock wears off toward 0 by {@code amount}. */
    static float decay(float shock, float amount)
    {
        return shock > 0.0F ? Math.max(0.0F, shock - amount) : Math.min(0.0F, shock + amount);
    }

    /**
     * Whether a hit from ({@code dx}, {@code dz}) off a body facing {@code yaw}
     * (Minecraft degrees: 0 south, 90 west) is more than {@code angle} degrees
     * off the facing: from the side or behind.
     */
    static boolean fromBehind(float yaw, double dx, double dz, double angle)
    {
        if (dx * dx + dz * dz < 1.0E-6D)
        {
            return false;
        }
        double toward = Math.toDegrees(Math.atan2(-dx, dz));
        return Math.abs(Mth.wrapDegrees(toward - yaw)) > angle / 2.0D;
    }
}
