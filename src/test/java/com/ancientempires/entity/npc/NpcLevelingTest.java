package com.ancientempires.entity.npc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcLevelingTest
{
    @Test
    void theCurveGrowsByTheStep()
    {
        assertEquals(20.0D, NpcLeveling.xpToNext(1, 20.0D, 10.0D));
        assertEquals(30.0D, NpcLeveling.xpToNext(2, 20.0D, 10.0D));
        double toTen = 0.0D;
        double toTwenty = 0.0D;
        for (int level = 1; level < 20; level++)
        {
            toTwenty += NpcLeveling.xpToNext(level, 20.0D, 10.0D);
            if (level < 10)
            {
                toTen += NpcLeveling.xpToNext(level, 20.0D, 10.0D);
            }
        }
        // As the config comment promises.
        assertEquals(540.0D, toTen);
        assertEquals(2090.0D, toTwenty);
    }

    @Test
    void bandsAreFiveLevelsEachAtTwenty()
    {
        assertEquals(0, NpcLeveling.band(1, 20));
        assertEquals(0, NpcLeveling.band(5, 20));
        assertEquals(1, NpcLeveling.band(6, 20));
        assertEquals(2, NpcLeveling.band(11, 20));
        assertEquals(2, NpcLeveling.band(15, 20));
        assertEquals(3, NpcLeveling.band(16, 20));
        assertEquals(3, NpcLeveling.band(20, 20));
        assertEquals(3, NpcLeveling.band(99, 20));
    }

    @Test
    void skillListsAreReadAlongTheLevels()
    {
        List<Double> list = List.of(0.05D, 0.15D, 0.3D, 0.45D);
        assertEquals(0.05D, NpcLeveling.lerp(list, NpcLeveling.position(1, 20)), 1.0E-9D);
        assertEquals(0.45D, NpcLeveling.lerp(list, NpcLeveling.position(20, 20)), 1.0E-9D);
        // Every level moves it a little: level 2 is past the first entry.
        double two = NpcLeveling.lerp(list, NpcLeveling.position(2, 20));
        assertEquals(0.05D + 0.1D * 3.0D / 19.0D, two, 1.0E-9D);
        // Out of range positions stick to the ends.
        assertEquals(0.45D, NpcLeveling.lerp(list, 7.0D), 1.0E-9D);
        assertEquals(0.05D, NpcLeveling.lerp(list, -1.0D), 1.0E-9D);
        assertEquals(0.0D, NpcLeveling.lerp(List.of(), 1.0D), 1.0E-9D);
    }
}
