package com.ancientempires.entity.npc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcFormationTest
{
    @Test
    void theFrontRankStandsAheadOfItsSergeantAndTheRestBehind()
    {
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 16; i++)
        {
            double[] slot = NpcFormation.LINE.soldierSlot(i, 16, 8, 2, 1.5D);
            assertTrue(taken.add(slot[0] + "," + slot[1]), "one soldier per slot");
            // Front rank ahead (negative back), second rank behind.
            assertTrue(i < 8 ? slot[1] < 0.0D : slot[1] > 0.0D, "rank of soldier " + i);
        }
        // Rows centred on the leader.
        assertEquals(-5.25D, NpcFormation.LINE.soldierSlot(0, 16, 8, 2, 1.5D)[0], 1.0E-9);
        assertEquals(5.25D, NpcFormation.LINE.soldierSlot(7, 16, 8, 2, 1.5D)[0], 1.0E-9);
        assertEquals(2, NpcFormation.LINE.rows(16, 8, 2));
        // The sergeant stands between its ranks, a full spacing apart.
        assertEquals(1.5D, NpcFormation.LINE.soldierSlot(8, 16, 8, 2, 1.5D)[1] - NpcFormation.LINE.soldierSlot(0, 16, 8, 2, 1.5D)[1], 1.0E-9);
    }

    @Test
    void aShortLastRowIsCentredToo()
    {
        assertEquals(-0.75D, NpcFormation.LINE.soldierSlot(8, 10, 8, 2, 1.5D)[0], 1.0E-9);
        assertEquals(0.75D, NpcFormation.LINE.soldierSlot(9, 10, 8, 2, 1.5D)[0], 1.0E-9);
    }

    @Test
    void aColumnIsNarrowAndDeepAndASkirmishLineIsWider()
    {
        assertEquals(8, NpcFormation.COLUMN.rows(16, 8, 2));
        assertEquals(7 * 1.5D - 0.9D, NpcFormation.COLUMN.soldierSlot(15, 16, 8, 2, 1.5D)[1], 1.0E-9);
        assertEquals(2.0D * Math.abs(NpcFormation.LINE.soldierSlot(0, 16, 8, 2, 1.5D)[0]),
                Math.abs(NpcFormation.SKIRMISH.soldierSlot(0, 16, 8, 2, 1.5D)[0]), 1.0E-9);
        assertEquals(NpcFormation.SKIRMISH, NpcFormation.byName("loose"), "old saves' loose order");
    }

    @Test
    void captainsStandBehindTheirSquads()
    {
        double behind = NpcFormation.LINE.depthBehind(16, 8, 2, 1.5D);
        double depth = NpcFormation.LINE.lead(1.5D) + behind;
        // Two sergeants side by side, their squads' rear ranks clear ahead of the captain.
        double[] first = NpcFormation.LINE.officerSlot(0, 2, 0.0D, 12.0D, behind, depth, 1.5D);
        double[] second = NpcFormation.LINE.officerSlot(1, 2, 0.0D, 12.0D, behind, depth, 1.5D);
        assertEquals(first[1], second[1], 1.0E-9);
        assertTrue(first[1] + behind < 0.0D, "the squad's rear is ahead of the captain");
        assertEquals(13.5D, second[0] - first[0], 1.0E-9);
        // With its own guard, the squads stand ahead of the guard's front rank too.
        double[] guarded = NpcFormation.LINE.officerSlot(0, 1, NpcFormation.LINE.lead(1.5D), 12.0D, behind, depth, 1.5D);
        assertTrue(guarded[1] + behind < NpcFormation.LINE.soldierSlot(0, 4, 8, 2, 1.5D)[1]);
        // In a column the squads march one ahead of another.
        double[] lead = NpcFormation.COLUMN.officerSlot(0, 2, 0.0D, 3.0D, behind, depth, 1.5D);
        double[] next = NpcFormation.COLUMN.officerSlot(1, 2, 0.0D, 3.0D, behind, depth, 1.5D);
        assertEquals(0.0D, next[0], 1.0E-9);
        assertTrue(next[1] < lead[1] - depth, "the second squad clear of the first");
    }

    @Test
    void aWedgeHasItsPointAheadAndWidensBackToItsLeader()
    {
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 16; i++)
        {
            double[] slot = NpcFormation.WEDGE.soldierSlot(i, 16, 8, 2, 1.5D);
            assertTrue(taken.add(slot[0] + "," + slot[1]), "one soldier per slot");
            assertTrue(slot[1] < 0.0D, "the whole wedge ahead of its leader");
        }
        assertEquals(4, NpcFormation.WEDGE.rows(16, 8, 2));
        double[] point = NpcFormation.WEDGE.soldierSlot(0, 16, 8, 2, 1.5D);
        assertEquals(0.0D, point[0], 1.0E-9);
        assertEquals(-3 * 1.5D - NpcFormation.WEDGE.lead(1.5D), point[1], 1.0E-9);
        // The last row, seven wide, a lead ahead of the leader.
        assertEquals(-4.5D, NpcFormation.WEDGE.soldierSlot(9, 16, 8, 2, 1.5D)[0], 1.0E-9);
        assertEquals(-NpcFormation.WEDGE.lead(1.5D), NpcFormation.WEDGE.soldierSlot(15, 16, 8, 2, 1.5D)[1], 1.0E-9);
        assertEquals(0.0D, NpcFormation.WEDGE.depthBehind(16, 8, 2, 1.5D), 1.0E-9);
    }

    @Test
    void aSquareRingsItsLeaderAndHoldsTheRestInside()
    {
        assertEquals(5, NpcFormation.squareSide(16));
        assertEquals(2, NpcFormation.squareSide(1));
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 16; i++)
        {
            double[] slot = NpcFormation.SQUARE.soldierSlot(i, 16, 8, 2, 1.5D);
            assertTrue(taken.add(slot[0] + "," + slot[1]), "one soldier per slot");
            double edge = Math.max(Math.abs(slot[0]), Math.abs(slot[1]));
            assertEquals(3.0D, edge, 1.0E-9, "on the rim, two spacings out");
        }
        // Fewer than the rim holds: spread round it, not bunched at the front.
        double[] last = NpcFormation.SQUARE.soldierSlot(9, 10, 8, 2, 1.5D);
        assertTrue(last[0] < 0.0D, "the last of ten on the left side");
        double[] inside = NpcFormation.insideSquare(0, 9, 5, 1.5D);
        assertTrue(Math.abs(inside[0]) < 3.0D && Math.abs(inside[1]) < 3.0D, "archers inside the rim");
        assertEquals(NpcFormation.SQUARE.frontage(16, 8, 2, 1.5D), 6.0D + 1.5D, 1.0E-9);
    }

    @Test
    void forwardAndYawAgree()
    {
        double[] south = NpcFormation.forward(0.0F);
        assertEquals(0.0D, south[0], 1.0E-9);
        assertEquals(1.0D, south[1], 1.0E-9);
        double[] east = NpcFormation.forward(-90.0F);
        assertEquals(1.0D, east[0], 1.0E-9);
        assertEquals(-90.0D, NpcFormation.yawToward(0.0D, 0.0D, 5.0D, 0.0D), 1.0E-4);
        assertEquals(0.0D, NpcFormation.yawToward(0.0D, 0.0D, 0.0D, 5.0D), 1.0E-4);
    }
}
