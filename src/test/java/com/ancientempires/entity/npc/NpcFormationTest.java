package com.ancientempires.entity.npc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcFormationTest
{
    @Test
    void aLineOfSixteenIsTwoCentredRowsBehindTheLeader()
    {
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 16; i++)
        {
            double[] slot = NpcFormation.LINE.soldierSlot(i, 16, 8, 2, 1.5D);
            assertTrue(slot[1] > 0.0D, "behind the leader");
            assertTrue(taken.add(slot[0] + "," + slot[1]), "one soldier per slot");
        }
        // First row centred on the leader.
        assertEquals(-5.25D, NpcFormation.LINE.soldierSlot(0, 16, 8, 2, 1.5D)[0], 1.0E-9);
        assertEquals(5.25D, NpcFormation.LINE.soldierSlot(7, 16, 8, 2, 1.5D)[0], 1.0E-9);
        assertEquals(2, NpcFormation.LINE.rows(16, 8, 2));
    }

    @Test
    void aShortLastRowIsCentredToo()
    {
        // 10 soldiers: a row of 8, then 2 centred behind.
        assertEquals(-0.75D, NpcFormation.LINE.soldierSlot(8, 10, 8, 2, 1.5D)[0], 1.0E-9);
        assertEquals(0.75D, NpcFormation.LINE.soldierSlot(9, 10, 8, 2, 1.5D)[0], 1.0E-9);
    }

    @Test
    void aColumnIsNarrowAndDeepAndLooseIsWider()
    {
        assertEquals(8, NpcFormation.COLUMN.rows(16, 8, 2));
        double[] last = NpcFormation.COLUMN.soldierSlot(15, 16, 8, 2, 1.5D);
        assertEquals(12.0D, last[1], 1.0E-9);
        assertEquals(2.0D * Math.abs(NpcFormation.LINE.soldierSlot(0, 16, 8, 2, 1.5D)[0]),
                Math.abs(NpcFormation.LOOSE.soldierSlot(0, 16, 8, 2, 1.5D)[0]), 1.0E-9);
    }

    @Test
    void sergeantsStandBehindTheCaptainsOwnSoldiers()
    {
        // A captain with one row of its own: its sergeants stand side by side behind it in a line, one after another in a column.
        double[] first = NpcFormation.LINE.officerSlot(0, 2, 1, 12.0D, 3.0D, 1.5D);
        double[] second = NpcFormation.LINE.officerSlot(1, 2, 1, 12.0D, 3.0D, 1.5D);
        assertEquals(first[1], second[1], 1.0E-9);
        assertTrue(first[1] > NpcFormation.LINE.soldierSlot(0, 8, 8, 2, 1.5D)[1]);
        assertEquals(13.5D, second[0] - first[0], 1.0E-9);
        double[] behind = NpcFormation.COLUMN.officerSlot(1, 2, 1, 3.0D, 12.0D, 1.5D);
        assertEquals(0.0D, behind[0], 1.0E-9);
        assertTrue(behind[1] > NpcFormation.COLUMN.officerSlot(0, 2, 1, 3.0D, 12.0D, 1.5D)[1] + 12.0D);
    }
}
