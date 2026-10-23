package com.ancientempires.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorksiteAreaTest
{
    @Test
    void centeredOddSizeIsSymmetric()
    {
        WorksiteArea area = WorksiteArea.centered(9, 9);
        assertEquals(new WorksiteArea(-4, -4, 4, 4), area);
        assertEquals(9, area.width());
        assertEquals(9, area.length());
        assertTrue(area.containsBlock());
    }

    @Test
    void centeredEvenSizePutsTheExtraColumnEastAndSouth()
    {
        WorksiteArea area = WorksiteArea.centered(4, 6);
        assertEquals(4, area.width());
        assertEquals(6, area.length());
        assertEquals(-1, area.minX());
        assertEquals(2, area.maxX());
        assertEquals(-2, area.minZ());
        assertEquals(3, area.maxZ());
    }

    @Test
    void movingKeepsTheSize()
    {
        WorksiteArea area = WorksiteArea.centered(5, 5).moved(10, -3);
        assertEquals(5, area.width());
        assertEquals(5, area.length());
        assertFalse(area.containsBlock());
    }

    @Test
    void resizingMovesOnlyTheEastAndSouthEdges()
    {
        WorksiteArea area = WorksiteArea.centered(5, 5).resized(2, -1);
        assertEquals(-2, area.minX());
        assertEquals(4, area.maxX());
        assertEquals(-2, area.minZ());
        assertEquals(1, area.maxZ());
    }

    @Test
    void gapIsZeroOverTheBlockAndCountsFromTheNearEdge()
    {
        assertEquals(0, WorksiteArea.centered(3, 3).gap());
        // Touching the block's east side.
        assertEquals(1, new WorksiteArea(1, -1, 5, 1).gap());
        assertEquals(6, new WorksiteArea(-10, -1, -6, 1).gap());
        // The farther axis decides.
        assertEquals(4, new WorksiteArea(2, 4, 3, 5).gap());
    }

    @Test
    void fitsChecksSizeReachAndTheBlockColumn()
    {
        WorksiteArea square = WorksiteArea.centered(9, 9);
        assertTrue(square.fits(9, 8, true));
        assertFalse(square.fits(8, 8, true), "too wide for the limit");
        assertFalse(square.fits(9, 8, false), "a quarry may not cover its own column");
        WorksiteArea far = new WorksiteArea(20, 0, 24, 4);
        assertFalse(far.fits(16, 8, true), "beyond the reach");
        assertTrue(new WorksiteArea(9, 0, 12, 3).fits(16, 8, true), "exactly at reach plus touching");
        assertFalse(new WorksiteArea(1, 1, 0, 1).fits(16, 8, true), "no width");
    }
}
