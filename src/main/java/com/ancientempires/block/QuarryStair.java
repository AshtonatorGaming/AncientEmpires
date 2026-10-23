package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a quarry's spiral stair runs, as pure arithmetic on the pit's bounds:
 * one rim tile per layer, starting at the rim tile nearest the quarry block and
 * one further clockwise each layer down, widened inward to the stair width.
 * Columns of the two steps above are left out, so a step never sits under
 * another (at the corners), which would block the head of anyone climbing.
 * Columns are packed as {@link BlockPos#asLong} at y 0.
 */
public final class QuarryStair
{
    private QuarryStair()
    {
    }

    /**
     * The stair columns of {@code layer} in the pit {@code minX..maxX},
     * {@code minZ..maxZ} of a quarry block at ({@code blockX}, {@code blockZ}),
     * at most {@code wantedWidth} wide (a small pit keeps room to dig).
     */
    public static Set<Long> columns(int minX, int minZ, int maxX, int maxZ, int blockX, int blockZ, int layer, int wantedWidth)
    {
        List<BlockPos> rim = rim(minX, minZ, maxX, maxZ);
        if (rim.isEmpty())
        {
            // A one-column pit has no rim to spare: no stair.
            return Set.of();
        }
        int start = 0;
        long best = Long.MAX_VALUE;
        for (int index = 0; index < rim.size(); index++)
        {
            BlockPos tile = rim.get(index);
            long dx = tile.getX() - blockX;
            long dz = tile.getZ() - blockZ;
            if (dx * dx + dz * dz < best)
            {
                best = dx * dx + dz * dz;
                start = index;
            }
        }
        int narrowest = Math.min(maxX - minX + 1, maxZ - minZ + 1);
        int width = Math.max(1, Math.min(wantedWidth, (narrowest - 1) / 2));
        Set<Long> columns = stepColumns(rim, start + layer, width);
        for (int above = 1; above <= 2 && layer - above >= 0; above++)
        {
            columns.removeAll(stepColumns(rim, start + layer - above, width));
        }
        return columns;
    }

    /**
     * Where to walk next on the way from {@code feet} to {@code goal} in a quarry
     * dug down to {@code dugLayer}: the stair step one level nearer the goal, so a
     * long climb or descent is many short paths instead of one the pathfinder runs
     * out of search for (it then walks to the nearest point, the pit wall). The goal
     * itself once it is within a block of the same level, or off the stair's span.
     * A step of layer {@code k} is stood on at {@code blockY - k}.
     */
    public static BlockPos waypoint(int minX, int minZ, int maxX, int maxZ, int blockX, int blockY, int blockZ, int dugLayer, int wantedWidth,
            BlockPos feet, BlockPos goal)
    {
        int rise = goal.getY() - feet.getY();
        if (Math.abs(rise) <= 1)
        {
            return goal;
        }
        int standY = feet.getY() + Integer.signum(rise);
        int layer = blockY - standY;
        if (layer < 0 || layer > dugLayer)
        {
            // Above the top step (the surface) or below the dig: nothing to climb.
            return goal;
        }
        Set<Long> columns = columns(minX, minZ, maxX, maxZ, blockX, blockZ, layer, wantedWidth);
        BlockPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (long packed : columns)
        {
            BlockPos column = BlockPos.of(packed);
            long dx = column.getX() - feet.getX();
            long dz = column.getZ() - feet.getZ();
            if (dx * dx + dz * dz < bestDistance)
            {
                bestDistance = dx * dx + dz * dz;
                best = new BlockPos(column.getX(), standY, column.getZ());
            }
        }
        return best == null ? goal : best;
    }

    /** Rim tile {@code index} (wrapping) and the tiles inward from it, to {@code width} in all. */
    private static Set<Long> stepColumns(List<BlockPos> rim, int index, int width)
    {
        BlockPos tile = rim.get(Math.floorMod(index, rim.size()));
        BlockPos next = rim.get(Math.floorMod(index + 1, rim.size()));
        // The rim runs clockwise, so inward is a quarter turn clockwise from the way it runs.
        Direction along = Direction.fromDelta(Integer.signum(next.getX() - tile.getX()), 0, Integer.signum(next.getZ() - tile.getZ()));
        Direction inward = (along == null ? Direction.EAST : along).getClockWise();
        Set<Long> columns = new HashSet<>();
        for (int step = 0; step < width; step++)
        {
            columns.add(BlockPos.asLong(tile.getX() + inward.getStepX() * step, 0, tile.getZ() + inward.getStepZ() * step));
        }
        return columns;
    }

    /** The pit's edge, clockwise from the north-west corner, at y 0. */
    static List<BlockPos> rim(int minX, int minZ, int maxX, int maxZ)
    {
        List<BlockPos> rim = new ArrayList<>();
        for (int x = minX; x < maxX; x++)
        {
            rim.add(new BlockPos(x, 0, minZ));
        }
        for (int z = minZ; z < maxZ; z++)
        {
            rim.add(new BlockPos(maxX, 0, z));
        }
        for (int x = maxX; x > minX; x--)
        {
            rim.add(new BlockPos(x, 0, maxZ));
        }
        for (int z = maxZ; z > minZ; z--)
        {
            rim.add(new BlockPos(minX, 0, z));
        }
        return rim;
    }
}
