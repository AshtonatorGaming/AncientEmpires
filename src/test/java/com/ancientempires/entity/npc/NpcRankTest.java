package com.ancientempires.entity.npc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NpcRankTest
{
    @Test
    void rosterTiersGiveRanks()
    {
        // Elite soldiers are better troops, not sergeants by row.
        assertEquals(NpcRank.SOLDIER, NpcRank.ofTier(0));
        assertEquals(NpcRank.SOLDIER, NpcRank.ofTier(1));
        assertEquals(NpcRank.CAPTAIN, NpcRank.ofTier(2));
        assertEquals(NpcRank.GENERAL, NpcRank.ofTier(3));
    }

    @Test
    void promotionGoesOneStepAtATime()
    {
        assertEquals(NpcRank.SERGEANT, NpcRank.SOLDIER.next());
        assertEquals(NpcRank.GENERAL, NpcRank.CAPTAIN.next());
        assertNull(NpcRank.GENERAL.next());
        assertEquals(NpcRank.CAPTAIN, NpcRank.byName("captain"));
        assertNull(NpcRank.byName("jarl"));
    }

    @Test
    void auraListsAreReadByRank()
    {
        List<Double> strength = List.of(0.0D, 0.5D, 1.0D, 1.25D);
        assertEquals(0.0D, NpcCommand.byRank(strength, NpcRank.SOLDIER));
        assertEquals(1.25D, NpcCommand.byRank(strength, NpcRank.GENERAL));
        // A short list holds its last entry for the higher ranks.
        assertEquals(1.0D, NpcCommand.byRank(List.of(0.0D, 1.0D), NpcRank.GENERAL));
    }
}
