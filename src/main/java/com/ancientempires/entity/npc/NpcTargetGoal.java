package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Replaces the two vanilla nearest-target goals (monsters, then hostile NPCs)
 * with one scan. The work per scan, cheapest first:
 * <ol>
 * <li>a shared candidate list from {@link NpcTargetCache} instead of a box query per NPC,</li>
 * <li>the faction and owner test, then range and vanilla's combat checks, with no raycast,</li>
 * <li>a threat score per candidate ({@link NpcThreat}: distance, less bonuses for attacking
 * the owner or allies, being wounded, standing in front, and role match), then line of sight
 * best first, stopping at the first visible candidate.</li>
 * </ol>
 * Vanilla raycast every candidate that passed step 2 before picking the nearest.
 * Scans are staggered by entity id. While a scanned target is held, a slower
 * rescan switches only to one that scores clearly better. Monsters and NPCs are one
 * pool, so the best-scoring hostile wins; vanilla preferred any monster.
 */
public final class NpcTargetGoal extends TargetGoal
{
    private final NpcEntity npc;
    private final List<LivingEntity> passed = new ArrayList<>();
    /** Threat scores of {@link #passed}, lowest best ({@link NpcThreat}). */
    private final DoubleArrayList scores = new DoubleArrayList();

    private TargetingConditions conditions;
    private double conditionsRange = -1.0D;
    private int nextScan;
    private int nextRetarget;
    @Nullable
    private LivingEntity found;

    public NpcTargetGoal(NpcEntity npc)
    {
        super(npc, true, false);
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.TARGET));
        // Spread NPCs that load together across the scan interval.
        this.nextScan = Math.floorMod(npc.getId(), Math.max(1, AncientEmpiresConfig.targetScanInterval));
    }

    @Override
    public boolean canUse()
    {
        if (this.npc.avoidsFights() || (this.npc.isStandingGround() && !this.npc.isLevied()))
        {
            return false;
        }
        if (this.npc.getChain().grunt())
        {
            // A soldier in formation does not look for fights: its leader hands them out (NpcChain.plan).
            return false;
        }
        if (this.keepsHandedTarget())
        {
            return false;
        }
        if (this.npc.tickCount < this.nextScan)
        {
            return false;
        }
        this.nextScan = this.npc.tickCount + Math.max(1, AncientEmpiresConfig.targetScanInterval);
        this.found = this.scan(Double.MAX_VALUE);
        return this.found != null;
    }

    @Override
    public void start()
    {
        this.mob.setTarget(this.found);
        this.targetMob = this.found;
        this.found = null;
        this.nextRetarget = this.npc.tickCount + AncientEmpiresConfig.targetRetargetInterval;
        super.start();
        // NpcTargetMemory decides when an unseen target is given up, not vanilla's 3 seconds.
        this.unseenMemoryTicks = AncientEmpiresConfig.targetPursuitTicks;
    }

    @Override
    public void stop()
    {
        super.stop();
        this.found = null;
        // A lost target is replaced on the next evaluation, not a full interval later.
        this.nextScan = this.npc.tickCount;
    }

    @Override
    public void tick()
    {
        int interval = AncientEmpiresConfig.targetRetargetInterval;
        if (interval <= 0 || this.npc.tickCount < this.nextRetarget)
        {
            return;
        }
        this.nextRetarget = this.npc.tickCount + interval;
        LivingEntity current = this.mob.getTarget();
        if (current == null)
        {
            return;
        }
        // Switch only to a target that scores clearly better (switchMargin, in effective blocks).
        double margin = AncientEmpiresConfig.targetSwitchMargin;
        double currentScore = NpcThreat.score(this.npc, current, this.npc.distanceToSqr(current));
        LivingEntity better = this.scan(Double.MAX_VALUE);
        if (better != null && better != current
                && NpcThreat.score(this.npc, better, this.npc.distanceToSqr(better)) < currentScore - margin)
        {
            this.mob.setTarget(better);
            this.targetMob = better;
        }
    }

    /**
     * A target set from outside the target goals (an ally's alert) is kept while
     * it is alive, attackable, and inside follow range. Nothing else watches it,
     * so a lost one is cleared here and the scan takes over.
     */
    private boolean keepsHandedTarget()
    {
        LivingEntity current = this.mob.getTarget();
        if (current == null)
        {
            return false;
        }
        double range = this.getFollowDistance();
        if (current.isAlive() && this.npc.canAttack(current) && this.npc.distanceToSqr(current) <= range * range)
        {
            return true;
        }
        this.mob.setTarget(null);
        return false;
    }

    /**
     * The best-scoring hostile in sight within {@code limitSqr}, or null.
     */
    @Nullable
    private LivingEntity scan(double limitSqr)
    {
        NpcTargetCache.countScan();
        this.npc.level().getProfiler().push("ancientempires_target_scan");
        double range = Math.min(AncientEmpiresConfig.targetScanRange, this.getFollowDistance());
        TargetingConditions test = this.conditions(range);
        double limit = Math.min(range * range, limitSqr);
        List<LivingEntity> candidates = NpcTargetCache.candidates(this.npc, AncientEmpiresConfig.targetScanRange);
        this.passed.clear();
        this.scores.clear();
        for (int index = 0; index < candidates.size(); index++)
        {
            LivingEntity candidate = candidates.get(index);
            if (candidate == this.npc || !candidate.isAlive() || candidate.isRemoved()
                    || !this.npc.getRelations().scanTarget(candidate) || !this.npc.getChain().allowsTarget(candidate))
            {
                continue;
            }
            double distance = this.npc.distanceToSqr(candidate);
            if (distance > limit || !test.test(this.npc, candidate))
            {
                continue;
            }
            this.passed.add(candidate);
            this.scores.add(NpcThreat.score(this.npc, candidate, distance));
        }
        LivingEntity seen = this.bestInSight();
        this.passed.clear();
        this.npc.level().getProfiler().pop();
        return seen;
    }

    /**
     * Picks the next-best candidate each round instead of sorting, since
     * only a few rounds run and the first visible one ends the search.
     */
    @Nullable
    private LivingEntity bestInSight()
    {
        int rounds = Math.min(this.passed.size(), Math.max(1, AncientEmpiresConfig.targetMaxSightChecks));
        for (int round = 0; round < rounds; round++)
        {
            int best = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int index = 0; index < this.scores.size(); index++)
            {
                double distance = this.scores.getDouble(index);
                if (distance < bestDistance)
                {
                    bestDistance = distance;
                    best = index;
                }
            }
            if (best < 0)
            {
                return null;
            }
            this.scores.set(best, Double.MAX_VALUE);
            LivingEntity candidate = this.passed.get(best);
            NpcTargetCache.countSightCheck();
            if (this.npc.getSensing().hasLineOfSight(candidate))
            {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Vanilla's combat checks (attackable, allied, invisibility-scaled range)
     * without the sight test, which this goal does itself in distance order.
     */
    private TargetingConditions conditions(double range)
    {
        if (this.conditions == null || this.conditionsRange != range)
        {
            this.conditions = TargetingConditions.forCombat().range(range).ignoreLineOfSight();
            this.conditionsRange = range;
        }
        return this.conditions;
    }
}
