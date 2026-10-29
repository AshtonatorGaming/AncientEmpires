package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A soldier in a unit keeps its slot in its leader's formation
 * ({@link NpcChain#slotOf}), and that is all it decides: it walks only when
 * it is out of its slot by more than {@code chain.slotTolerance}, runs to
 * catch up when far behind, and otherwise stands facing the way the unit
 * faces. A fight (a target) takes it out of formation for now; it returns
 * when the fight ends. Above following, home, and wandering, so a formed
 * soldier does none of those. In a shield wall, or under a shields-up stance,
 * it keeps its shield raised while it stands in its slot; in a square it faces
 * out.
 */
public class NpcFormationGoal extends Goal
{
    private static final int REPATH_TICKS = 10;
    /** Blocks off the slot that count as in it, once moving (it sets off again only past the tolerance). */
    private static final double SETTLED = 0.35D;
    /** Blocks off the slot from which it steps straight in, rather than pathing to a block's centre. */
    private static final double STEP_IN = 2.0D;
    /** On the march it steps straight after its slot from further off: pathing to a slot that keeps moving stops and starts. */
    private static final double MARCH_STEP_IN = 4.0D;

    private final NpcEntity npc;
    private int repath;
    private boolean moving;
    /** The slot it last settled in: if the slot itself moves (the leader shifted), it steps over. */
    private Vec3 settledIn;
    /** Whether this goal raised the shield (a shield wall), so it lowers it again. */
    private boolean shieldUp;

    public NpcFormationGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        return this.npc.getChain().formed() && this.npc.getTarget() == null && !this.npc.avoidsFights() && !this.npc.isSleeping();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.canUse();
    }

    @Override
    public void start()
    {
        this.repath = 0;
        this.moving = true;
        this.settledIn = null;
    }

    @Override
    public void stop()
    {
        this.npc.getNavigation().stop();
        this.lowerShield();
    }

    private void lowerShield()
    {
        if (this.shieldUp)
        {
            this.shieldUp = false;
            if (this.npc.getTarget() == null)
            {
                this.npc.getGuard().lower();
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        NpcEntity leader = this.npc.getChain().leader();
        Vec3 slot = leader == null ? null : leader.getChain().slotOf(this.npc);
        if (slot == null)
        {
            return;
        }
        double dx = slot.x - this.npc.getX();
        double dz = slot.z - this.npc.getZ();
        double off = Math.sqrt(dx * dx + dz * dz);
        // Sets off once past the tolerance, then keeps on until it is really in its slot, so the line stays straight.
        this.moving = this.moving ? off > SETTLED
                : off > AncientEmpiresConfig.chainSlotTolerance || this.settledIn != null && this.settledIn.distanceToSqr(slot) > SETTLED * SETTLED;
        if (this.moving)
        {
            this.lowerShield();
            // On the march a soldier a step behind its slot hurries, so the ranks keep ahead of their leader.
            boolean marching = this.npc.getChain().march() == NpcChain.March.MARCHING;
            boolean hurry = off > AncientEmpiresConfig.chainCatchUpDistance || off > 1.0D && marching;
            double speed = hurry ? AncientEmpiresConfig.chainCatchUpSpeed : AncientEmpiresConfig.chainWalkSpeed;
            if (off <= (marching ? MARCH_STEP_IN : STEP_IN))
            {
                // The last step straight onto the spot (a path ends at a block's centre).
                this.npc.getNavigation().stop();
                this.npc.getMoveControl().setWantedPosition(slot.x, this.npc.getY(), slot.z, speed);
            }
            else if (--this.repath <= 0)
            {
                if (!NpcPathBudget.take(this.npc))
                {
                    this.repath = 1;
                    return;
                }
                this.repath = this.adjustedTickDelay(REPATH_TICKS);
                this.npc.getNavigation().moveTo(slot.x, slot.y, slot.z, speed);
            }
            return;
        }
        this.settledIn = slot;
        if (!this.npc.getNavigation().isDone())
        {
            this.npc.getNavigation().stop();
            this.repath = 0;
        }
        if (leader.getChain().formation().shieldsUp() || this.npc.getChain().stance().shieldsUp())
        {
            this.npc.getGuard().raise();
            this.shieldUp = this.npc.isRaisingShield();
        }
        // In its slot: face the way the unit faces (out of a square).
        float facing = leader.getChain().facingOf(this.npc);
        double radians = facing * Mth.DEG_TO_RAD;
        this.npc.getLookControl().setLookAt(this.npc.getX() - Math.sin(radians) * 8.0D, this.npc.getEyeY(), this.npc.getZ() + Math.cos(radians) * 8.0D);
        this.npc.setYBodyRot(Mth.approachDegrees(this.npc.yBodyRot, facing, 10.0F));
    }
}
