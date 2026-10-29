package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The leader holding a unit's order goes to the order's spot and stands
 * there facing the order's way (Hold, Defend, and Fall back; its unit forms
 * on it through {@link NpcFormationGoal}). A fight takes it off the spot for
 * now, except falling back, which runs on regardless; at the rally point the
 * order becomes Hold ({@link NpcChain}). Above following, home, and wandering.
 */
public class NpcOrderGoal extends Goal
{
    private static final int REPATH_TICKS = 10;
    /** Blocks from the spot that count as there. */
    private static final double THERE = 1.2D;

    private final NpcEntity npc;
    private int repath;

    public NpcOrderGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        NpcChain chain = this.npc.getChain();
        if (!AncientEmpiresConfig.chainEnabled || !chain.holdsOrder() || chain.posted() || chain.orderPos() == null || this.npc.isSleeping())
        {
            return false;
        }
        NpcOrder order = chain.effectiveOrder();
        return order == NpcOrder.FALL_BACK || order.anchored() && this.npc.getTarget() == null && !this.npc.avoidsFights();
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
    }

    @Override
    public void stop()
    {
        this.npc.getNavigation().stop();
        if (this.npc.getTarget() == null)
        {
            this.npc.getGuard().lower();
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
        NpcChain chain = this.npc.getChain();
        BlockPos pos = chain.orderPos();
        if (pos == null)
        {
            return;
        }
        NpcChain.March march = chain.march();
        if (march == NpcChain.March.FORMING || march == NpcChain.March.MARCHING && chain.straggling())
        {
            // Forming up, or waiting for stragglers: stand, facing the way to go.
            this.halt();
            this.face(chain.facing());
            return;
        }
        Vec3 spot = Vec3.atBottomCenterOf(pos);
        double dx = spot.x - this.npc.getX();
        double dz = spot.z - this.npc.getZ();
        double off = Math.sqrt(dx * dx + dz * dz);
        if (off > THERE)
        {
            if (--this.repath <= 0)
            {
                if (!NpcPathBudget.take(this.npc))
                {
                    this.repath = 1;
                    return;
                }
                this.repath = this.adjustedTickDelay(REPATH_TICKS);
                double speed;
                if (march == NpcChain.March.MARCHING)
                {
                    speed = AncientEmpiresConfig.chainMarchSpeed;
                }
                else
                {
                    boolean hurry = chain.effectiveOrder() == NpcOrder.FALL_BACK || off > AncientEmpiresConfig.chainCatchUpDistance;
                    speed = hurry ? AncientEmpiresConfig.chainCatchUpSpeed : AncientEmpiresConfig.chainWalkSpeed;
                }
                this.npc.getNavigation().moveTo(spot.x, spot.y, spot.z, speed);
            }
            return;
        }
        this.halt();
        this.face(chain.facing());
        if (chain.formation().shieldsUp() || chain.stance().shieldsUp())
        {
            this.npc.getGuard().raise();
        }
    }

    private void halt()
    {
        if (!this.npc.getNavigation().isDone())
        {
            this.npc.getNavigation().stop();
            this.repath = 0;
        }
    }

    private void face(float facing)
    {
        double radians = facing * Mth.DEG_TO_RAD;
        this.npc.getLookControl().setLookAt(this.npc.getX() - Math.sin(radians) * 8.0D, this.npc.getEyeY(), this.npc.getZ() + Math.cos(radians) * 8.0D);
        this.npc.setYBodyRot(Mth.approachDegrees(this.npc.yBodyRot, facing, 10.0F));
    }
}
