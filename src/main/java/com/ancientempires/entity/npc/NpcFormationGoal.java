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
 * soldier does none of those.
 */
public class NpcFormationGoal extends Goal
{
    private static final int REPATH_TICKS = 10;

    private final NpcEntity npc;
    private int repath;

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
    }

    @Override
    public void stop()
    {
        this.npc.getNavigation().stop();
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
        if (off > AncientEmpiresConfig.chainSlotTolerance)
        {
            if (--this.repath <= 0)
            {
                this.repath = this.adjustedTickDelay(REPATH_TICKS);
                double speed = off > AncientEmpiresConfig.chainCatchUpDistance ? AncientEmpiresConfig.chainCatchUpSpeed : AncientEmpiresConfig.chainWalkSpeed;
                this.npc.getNavigation().moveTo(slot.x, slot.y, slot.z, speed);
            }
            return;
        }
        if (!this.npc.getNavigation().isDone())
        {
            this.npc.getNavigation().stop();
            this.repath = 0;
        }
        // In its slot: face the way the unit faces.
        float facing = leader.getChain().facing();
        double radians = facing * Mth.DEG_TO_RAD;
        this.npc.getLookControl().setLookAt(this.npc.getX() - Math.sin(radians) * 8.0D, this.npc.getEyeY(), this.npc.getZ() + Math.cos(radians) * 8.0D);
        this.npc.setYBodyRot(Mth.approachDegrees(this.npc.yBodyRot, facing, 10.0F));
    }
}
