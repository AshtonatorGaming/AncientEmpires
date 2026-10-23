package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Vanilla's panic on fire, for NPCs: one that is burning and not in a fight
 * (or any civilian) runs for water within a few blocks, or just runs.
 * {@code upkeep.burningPanic} turns it off.
 */
public final class NpcBurningGoal extends Goal
{
    private static final int WATER_RANGE = 8;

    private final NpcEntity npc;

    public NpcBurningGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (!AncientEmpiresConfig.burningPanic || !this.npc.isOnFire() || this.npc.isInWaterOrRain() || this.npc.fireImmune())
        {
            return false;
        }
        return this.npc.getTarget() == null || this.npc.isCivilian();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.canUse() && !this.npc.getNavigation().isDone();
    }

    @Override
    public void start()
    {
        Vec3 spot = null;
        BlockPos water = this.findWater();
        if (water != null)
        {
            spot = Vec3.atBottomCenterOf(water);
        }
        else
        {
            spot = NpcSafety.pos(this.npc, 6, 3);
        }
        if (spot != null)
        {
            this.npc.getNavigation().moveTo(spot.x, spot.y, spot.z, AncientEmpiresConfig.civilianFleeSpeed);
        }
    }

    @Nullable
    private BlockPos findWater()
    {
        BlockPos center = this.npc.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-WATER_RANGE, -2, -WATER_RANGE), center.offset(WATER_RANGE, 1, WATER_RANGE)))
        {
            FluidState fluid = this.npc.level().getFluidState(pos);
            if (fluid.is(FluidTags.WATER))
            {
                double distance = pos.distSqr(center);
                if (distance < bestDistance)
                {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }
}
