package com.ancientempires.entity.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormationLayoutTest
{
    private static final FormationLayout.Settings SETTINGS = new FormationLayout.Settings(8, 2, 1.5D, 2.0D, 16);

    @Test
    void archersStandBehindTheMeleeAndRidersBehindThem()
    {
        FormationLayout layout = FormationLayout.of(NpcFormation.LINE, 10, 4, 2, 0, SETTINGS);
        double meleeRear = 0.0D;
        for (double[] slot : layout.melee())
        {
            meleeRear = Math.max(meleeRear, slot[1]);
        }
        double archerFront = Double.MAX_VALUE;
        double archerRear = 0.0D;
        for (double[] slot : layout.archers())
        {
            archerFront = Math.min(archerFront, slot[1]);
            archerRear = Math.max(archerRear, slot[1]);
        }
        assertEquals(meleeRear + 1.5D, archerFront, 1.0E-9, "archers a row behind the last melee row");
        for (double[] slot : layout.riders())
        {
            assertTrue(slot[1] > archerRear, "riders behind the archers");
        }
        assertEquals(0.9D, layout.front(NpcFormation.LINE, 1.5D), 1.0E-9, "the front rank a lead ahead");
    }

    @Test
    void horseAloneRidesInFront()
    {
        FormationLayout layout = FormationLayout.of(NpcFormation.WEDGE, 0, 0, 9, 0, SETTINGS);
        assertTrue(layout.mounted());
        assertEquals(9, layout.melee().length);
        assertTrue(layout.melee()[0][1] < 0.0D, "the point ahead of its leader");
    }

    @Test
    void aDraggedLineIsSharedByFrontRanks()
    {
        double[] shared = FormationLayout.shareLine(12.0D, new int[] {8, 8}, new double[] {1.5D, 1.5D}, 3.0D);
        assertEquals(4.0D, shared[0], 1.0E-9);
        assertEquals(4.0D, shared[1], 1.0E-9);
        assertEquals(-4.5D, shared[2], 1.0E-9);
        assertEquals(4.5D, shared[3], 1.0E-9);
        // Never fewer than two abreast, never more than it has.
        double[] narrow = FormationLayout.shareLine(1.0D, new int[] {5}, new double[] {1.5D}, 3.0D);
        assertEquals(2.0D, narrow[0], 1.0E-9);
        double[] wide = FormationLayout.shareLine(60.0D, new int[] {5}, new double[] {1.5D}, 3.0D);
        assertEquals(5.0D, wide[0], 1.0E-9);
    }

    @Test
    void aClickedSpotPutsTheFrontOnItAndTheLeaderBehind()
    {
        FormationLayout.Unit unit = new FormationLayout.Unit(NpcFormation.LINE, 8, 0, 0, 0, SETTINGS);
        // Facing south (+z): the leader stands a lead north of the spot.
        FormationLayout.Placed[] placed = FormationLayout.place(new FormationLayout.Unit[] {unit}, 10.0D, 20.0D, 0.0F, 0.0D, 3.0D);
        assertEquals(10.0D, placed[0].x(), 1.0E-9);
        assertEquals(20.0D - 0.9D, placed[0].z(), 1.0E-9);
        assertEquals(0, placed[0].width(), "a click keeps the unit's width");
        // Two units abreast across the spot, the first on the left (east, facing south).
        FormationLayout.Placed[] two = FormationLayout.place(new FormationLayout.Unit[] {unit, unit}, 0.0D, 0.0D, 0.0F, 0.0D, 3.0D);
        assertTrue(two[0].x() > 0.0D && two[1].x() < 0.0D, "unit 1 on the left, at " + two[0].x());
        assertEquals(-two[0].x(), two[1].x(), 1.0E-9);
    }
}
