package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where an NPC last saw its target. A target out of sight is chased to that
 * spot, not to where it really is, and searched for there for
 * {@code targeting.memoryTicks}; after that, or {@code pursuitTicks} without a
 * sighting in all, it is dropped. Gaining a target counts as a sighting, so a
 * target handed over by an ally's alert sends the NPC to where the fight was.
 * <p>
 * One line-of-sight check per tick while a target is held. {@code Sensing}
 * caches it for the tick, so the combat goals asking the same reuse it.
 */
public final class NpcTargetMemory
{
    /** Close enough to the last sighting to start searching, squared. */
    private static final double ARRIVED_SQR = 2.0D * 2.0D;
    /** Ticks without a path before an NPC that cannot reach the spot searches from where it stands. */
    private static final int STUCK_TICKS = 40;

    private final NpcEntity npc;
    @Nullable
    private LivingEntity tracked;
    private Vec3 lastSeen = Vec3.ZERO;
    private int lastSeenTick;
    /** Tick the search at the last sighting began; -1 while still on the way. */
    private int searchStart = -1;
    private boolean visible;

    NpcTargetMemory(NpcEntity npc)
    {
        this.npc = npc;
    }

    /** Server tick. Clears the NPC's target once the memory has run out. */
    void tick()
    {
        LivingEntity target = this.npc.getTarget();
        int now = this.npc.tickCount;
        if (target == null)
        {
            this.tracked = null;
            return;
        }
        if (target != this.tracked)
        {
            this.tracked = target;
            this.sighted(target, now);
            return;
        }
        this.visible = this.npc.getSensing().hasLineOfSight(target);
        if (this.visible)
        {
            this.sighted(target, now);
            return;
        }
        // Searching starts at the spot, or once the NPC has stopped short of it
        // (no path, or holding position) for a moment.
        boolean arrived = this.npc.position().distanceToSqr(this.lastSeen) <= ARRIVED_SQR;
        boolean stuck = this.npc.getNavigation().isDone() && now - this.lastSeenTick > STUCK_TICKS;
        if (this.searchStart < 0 && (arrived || stuck))
        {
            this.searchStart = now;
        }
        boolean searchedOut = this.searchStart >= 0 && now - this.searchStart > AncientEmpiresConfig.targetMemoryTicks;
        if (searchedOut || now - this.lastSeenTick > AncientEmpiresConfig.targetPursuitTicks)
        {
            this.tracked = null;
            this.npc.setTarget(null);
        }
    }

    private void sighted(LivingEntity target, int now)
    {
        this.visible = true;
        this.lastSeen = target.position();
        this.lastSeenTick = now;
        this.searchStart = -1;
    }

    /** Whether the target was in sight this tick. True for a target just gained. */
    public boolean targetVisible()
    {
        return this.visible;
    }

    /** Where the target was last seen. */
    public Vec3 lastSeen()
    {
        return this.lastSeen;
    }
}
