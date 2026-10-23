package com.ancientempires.combat;

import com.ancientempires.entity.npc.NpcGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldRulesTest
{
    private static final int WINDOW = 6;
    private static final int COOLDOWN = 24;

    @Test
    void aHitJustAfterTheRaiseIsAPerfectBlock()
    {
        assertTrue(Parry.perfectWindow(100, 100, 0, WINDOW, COOLDOWN), "the same tick");
        assertTrue(Parry.perfectWindow(106, 100, 0, WINDOW, COOLDOWN), "the last tick of the window");
        assertFalse(Parry.perfectWindow(107, 100, 0, WINDOW, COOLDOWN), "held too long: an ordinary block");
    }

    @Test
    void tappingTheShieldDoesNotFarmPerfectBlocks()
    {
        assertFalse(Parry.perfectWindow(100, 100, 90, WINDOW, COOLDOWN), "raised again ten ticks after the last raise");
        assertTrue(Parry.perfectWindow(100, 100, 76, WINDOW, COOLDOWN), "exactly the cooldown after");
    }

    @Test
    void theFirstRaiseEverCounts()
    {
        assertTrue(Parry.perfectWindow(100, 100, Long.MIN_VALUE / 2, WINDOW, COOLDOWN));
    }

    @Test
    void whoeverStartsReadyingFirstStrikesFirst()
    {
        assertTrue(NpcGuard.strikesFirst(true, 10, true, 12), "began earlier");
        assertFalse(NpcGuard.strikesFirst(true, 12, true, 10), "began later: hold the guard");
        assertTrue(NpcGuard.strikesFirst(true, 12, false, 0), "the other is not readying");
        assertFalse(NpcGuard.strikesFirst(false, 0, true, 5), "not readying at all");
    }

    @Test
    void theRuleNeverLetsBothGoFirst()
    {
        for (int mine = 0; mine < 20; mine++)
        {
            for (int theirs = 0; theirs < 20; theirs++)
            {
                assertFalse(NpcGuard.strikesFirst(true, mine, true, theirs) && NpcGuard.strikesFirst(true, theirs, true, mine),
                        "both first at " + mine + " and " + theirs);
            }
        }
    }
}
