package com.ancientempires.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorderCurveTest
{
    @Test
    void easesFromTheFloorToTheLand()
    {
        // At the footprint the floor, just past the ring the land, in between never backwards.
        assertEquals(64, BorderCurve.eased(64, 72, 0, 8));
        assertEquals(72, BorderCurve.eased(64, 72, 8, 8));
        int last = 64;
        for (int distance = 1; distance <= 8; distance++)
        {
            int y = BorderCurve.eased(64, 72, distance, 8);
            assertTrue(y >= last, "rising at " + distance);
            last = y;
        }
        // Downhill the same way.
        assertEquals(56, BorderCurve.eased(64, 56, 8, 8));
        assertTrue(BorderCurve.eased(64, 56, 4, 8) < 64);
    }
}
