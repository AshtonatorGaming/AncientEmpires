package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarryStairTest
{
    /** A 7 by 7 pit north of a quarry block at (0, 0): x -3..3, z -8..-2. */
    private static final int MIN_X = -3;
    private static final int MAX_X = 3;
    private static final int MIN_Z = -8;
    private static final int MAX_Z = -2;

    private static Set<Long> layer(int layer, int width)
    {
        return QuarryStair.columns(MIN_X, MIN_Z, MAX_X, MAX_Z, 0, 0, layer, width);
    }

    @Test
    void rimRunsAllTheWayRoundOnce()
    {
        List<BlockPos> rim = QuarryStair.rim(MIN_X, MIN_Z, MAX_X, MAX_Z);
        // A 7 by 7 square has 24 edge tiles.
        assertEquals(24, rim.size());
        assertEquals(24, new HashSet<>(rim).size(), "no tile twice");
    }

    @Test
    void theFirstStepIsBesideTheBlock()
    {
        Set<Long> first = layer(0, 1);
        assertEquals(1, first.size());
        BlockPos step = BlockPos.of(first.iterator().next());
        // The rim tile nearest (0, 0) is (0, -2), on the south edge.
        assertEquals(0, step.getX());
        assertEquals(-2, step.getZ());
    }

    @Test
    void stepsAreWideAsAskedAndInsideThePit()
    {
        for (int layer = 0; layer < 30; layer++)
        {
            for (long packed : layer(layer, 2))
            {
                BlockPos column = BlockPos.of(packed);
                assertTrue(column.getX() >= MIN_X && column.getX() <= MAX_X && column.getZ() >= MIN_Z && column.getZ() <= MAX_Z,
                        "layer " + layer + " step outside the pit: " + column);
            }
        }
        // Layer 6 is on the west edge's straight run; layer 3 is a corner, narrowed for headroom.
        assertEquals(2, layer(6, 2).size(), "a straight run is the full width");
        assertEquals(1, layer(3, 2).size(), "a corner drops the tile under the step above");
    }

    @Test
    void noStepSitsUnderEitherOfTheTwoAbove()
    {
        // Headroom: a column that is a step on one layer is never a step one or two layers lower.
        for (int layer = 2; layer < 40; layer++)
        {
            Set<Long> here = layer(layer, 3);
            for (int above = 1; above <= 2; above++)
            {
                Set<Long> overlap = new HashSet<>(here);
                overlap.retainAll(layer(layer - above, 3));
                assertTrue(overlap.isEmpty(), "layer " + layer + " shares a column with layer " + (layer - above));
            }
        }
    }

    @Test
    void eachStepIsReachableFromTheOneAbove()
    {
        // Walking down: some column of each step is beside (or diagonal to) a column of the step above.
        for (int layer = 1; layer < 30; layer++)
        {
            boolean adjacent = false;
            for (long below : layer(layer, 2))
            {
                for (long above : layer(layer - 1, 2))
                {
                    BlockPos a = BlockPos.of(below);
                    BlockPos b = BlockPos.of(above);
                    if (Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getZ() - b.getZ()) <= 1)
                    {
                        adjacent = true;
                    }
                }
            }
            assertTrue(adjacent, "layer " + layer + " is cut off from the step above");
        }
    }

    @Test
    void aSmallPitNarrowsTheStairAndAOneColumnPitHasNone()
    {
        // 3 wide: the stair may take at most one column, leaving room to dig.
        assertEquals(1, QuarryStair.columns(0, 0, 2, 2, 1, -1, 0, 3).size());
        assertTrue(QuarryStair.columns(0, 0, 0, 0, 0, -1, 0, 2).isEmpty());
        assertFalse(QuarryStair.columns(0, 0, 1, 1, 0, -1, 0, 2).isEmpty());
    }
}
