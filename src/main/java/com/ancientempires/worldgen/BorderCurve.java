package com.ancientempires.worldgen;

/**
 * AW's border curve (SmoothingMatrixBuilder, its gentle formula), kept pure for
 * tests: a border column's height, eased from the floor halfway up the
 * difference, then into the land just past the ring.
 */
public final class BorderCurve
{
    private BorderCurve()
    {
    }

    /** The height at {@code distance} from the footprint, with the land {@code outer} at {@code outerDistance}. */
    public static int eased(int floor, int outer, int distance, int outerDistance)
    {
        double half = outerDistance / 2.0D;
        int halfRise = (outer - floor) / 2;
        if (distance <= half)
        {
            double ratio = distance / half;
            return floor + (int) Math.round(halfRise * ratio * ratio);
        }
        double ratio = (outerDistance - distance) / half;
        return outer - (int) Math.round(halfRise * ratio * ratio);
    }
}
