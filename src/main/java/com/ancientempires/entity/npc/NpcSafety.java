package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hazard avoidance for spots an NPC picks for itself (fleeing, wandering,
 * repositioning, running from fire), the cliff-safe retreat the old
 * AncientTactics {@code FleeNavigation} did. Vanilla's random positions only
 * check that a path exists; these also reject spots near lava, fire, cactus, or
 * powder snow (vanilla's own path danger types), over a drop taller than
 * {@code hazard.maxFall}, or in deep water. Paths themselves are kept safe by
 * the fall height and path costs set on the NPC.
 */
public final class NpcSafety
{
    private NpcSafety()
    {
    }

    /** Whether an NPC could stand at {@code feet} without harm. */
    public static boolean isSafeSpot(Level level, BlockPos feet)
    {
        BlockPos.MutableBlockPos cursor = feet.mutable();
        BlockPathTypes type = WalkNodeEvaluator.getBlockPathTypeStatic(level, cursor);
        if (isHarmful(type))
        {
            return false;
        }
        if (AncientEmpiresConfig.hazardAvoidWaterSpots && !level.getFluidState(feet).isEmpty() && !level.getFluidState(feet.below()).isEmpty())
        {
            // Deep water: wading at the edge is fine, swimming is not a refuge.
            return false;
        }
        int maxFall = Math.max(0, AncientEmpiresConfig.hazardMaxFall);
        for (int drop = 1; drop <= maxFall + 1; drop++)
        {
            BlockPos below = feet.below(drop);
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(below).isEmpty())
            {
                return true;
            }
        }
        // Nothing to land on within a safe fall: a cliff edge or a pit.
        return false;
    }

    private static boolean isHarmful(BlockPathTypes type)
    {
        return switch (type)
        {
            case LAVA, DAMAGE_FIRE, DANGER_FIRE, DAMAGE_OTHER, DANGER_OTHER, POWDER_SNOW, DANGER_POWDER_SNOW, DAMAGE_CAUTIOUS -> true;
            default -> false;
        };
    }

    /** A safe spot about {@code distance} blocks away from {@code from}, or null. */
    @Nullable
    public static Vec3 posAway(PathfinderMob mob, int distance, int vertical, Vec3 from)
    {
        for (int attempt = 0; attempt < Math.max(1, AncientEmpiresConfig.hazardSafeSpotTries); attempt++)
        {
            Vec3 spot = DefaultRandomPos.getPosAway(mob, distance, vertical, from);
            if (spot != null && isSafeSpot(mob.level(), BlockPos.containing(spot)))
            {
                return spot;
            }
        }
        return null;
    }

    /** A safe spot within {@code distance} blocks, or null. */
    @Nullable
    public static Vec3 pos(PathfinderMob mob, int distance, int vertical)
    {
        for (int attempt = 0; attempt < Math.max(1, AncientEmpiresConfig.hazardSafeSpotTries); attempt++)
        {
            Vec3 spot = DefaultRandomPos.getPos(mob, distance, vertical);
            if (spot != null && isSafeSpot(mob.level(), BlockPos.containing(spot)))
            {
                return spot;
            }
        }
        return null;
    }

    /** Path costs from {@code hazard}: water and fire are walked around, lava never entered. */
    public static void applyPathCosts(PathfinderMob mob)
    {
        mob.setPathfindingMalus(BlockPathTypes.WATER, (float) AncientEmpiresConfig.hazardWaterCost);
        mob.setPathfindingMalus(BlockPathTypes.WATER_BORDER, (float) AncientEmpiresConfig.hazardWaterCost);
        mob.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, (float) AncientEmpiresConfig.hazardFireCost);
        mob.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, (float) AncientEmpiresConfig.hazardFireCost * 2.0F);
        mob.setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
        mob.setPathfindingMalus(BlockPathTypes.POWDER_SNOW, -1.0F);
    }
}
