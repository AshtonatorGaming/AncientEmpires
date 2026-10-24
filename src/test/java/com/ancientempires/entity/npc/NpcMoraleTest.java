package com.ancientempires.entity.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcMoraleTest
{
    /** The config defaults. */
    private static final NpcMorale.Weights WEIGHTS = new NpcMorale.Weights(25.0D, 30.0D, 15.0D, 10.0D, 6.0D, 24.0D, 20.0D);

    private static float target(double allies, double enemies, float health, boolean flanked, int routing)
    {
        return NpcMorale.target(new NpcMorale.Situation(60.0F, 0.0F, allies, enemies, health, flanked, false, routing, 0.0F), WEIGHTS);
    }

    @Test
    void anEvenFightAtFullHealthHoldsTheBase()
    {
        assertEquals(60.0F, target(3.0D, 3.0D, 1.0F, false, 0), 1.0E-4F);
    }

    @Test
    void aGruntAloneAndWoundedAgainstManyRouts()
    {
        // Alone against three, a third of its health left: below the rout line (20).
        assertTrue(target(1.0D, 3.0D, 0.33F, false, 0) < 40.0F);
        // Add a hit from behind and two allies running past: it breaks.
        assertTrue(target(1.0D, 3.0D, 0.33F, true, 2) < 20.0F);
        // The same soldier with the odds in its favour holds.
        assertTrue(target(4.0D, 1.0D, 0.33F, false, 0) > 50.0F);
    }

    @Test
    void routingAlliesAreCapped()
    {
        assertEquals(target(3.0D, 3.0D, 1.0F, false, 4), target(3.0D, 3.0D, 1.0F, false, 40), 1.0E-4F);
    }

    @Test
    void aCommanderLiftsMorale()
    {
        float plain = target(2.0D, 2.0D, 1.0F, false, 0);
        float inspired = NpcMorale.target(new NpcMorale.Situation(60.0F, 1.0F, 2.0D, 2.0D, 1.0F, false, false, 0, 0.0F), WEIGHTS);
        assertEquals(plain + 20.0F, inspired, 1.0E-4F);
    }

    @Test
    void moraleFallsFasterThanItRisesAndStaysInRange()
    {
        float fell = NpcMorale.drift(60.0F, 20.0F, 0.1D, 0.25D);
        float rose = NpcMorale.drift(20.0F, 60.0F, 0.1D, 0.25D);
        assertEquals(50.0F, fell, 1.0E-4F);
        assertEquals(24.0F, rose, 1.0E-4F);
        assertEquals(0.0F, NpcMorale.drift(5.0F, -500.0F, 0.1D, 1.0D), 1.0E-4F);
        assertEquals(100.0F, NpcMorale.drift(95.0F, 500.0F, 1.0D, 0.25D), 1.0E-4F);
    }

    @Test
    void shocksWearOffTowardZeroWithoutOvershooting()
    {
        assertEquals(-33.5F, NpcMorale.decay(-35.0F, 1.5F), 1.0E-4F);
        assertEquals(0.0F, NpcMorale.decay(1.0F, 1.5F), 1.0E-4F);
        assertEquals(0.0F, NpcMorale.decay(-1.0F, 1.5F), 1.0E-4F);
    }

    @Test
    void hitsFromBehindAndTheSideAreFlanks()
    {
        // Facing south (yaw 0, +z).
        assertFalse(NpcMorale.fromBehind(0.0F, 0.0D, 3.0D, 120.0D), "in front");
        assertFalse(NpcMorale.fromBehind(0.0F, -1.0D, 3.0D, 120.0D), "a little to the side, still in front");
        assertTrue(NpcMorale.fromBehind(0.0F, 3.0D, 0.0D, 120.0D), "side");
        assertTrue(NpcMorale.fromBehind(0.0F, 0.0D, -3.0D, 120.0D), "behind");
        // Facing west (yaw 90, -x): an attacker to the west is in front.
        assertFalse(NpcMorale.fromBehind(90.0F, -3.0D, 0.0D, 120.0D));
        assertTrue(NpcMorale.fromBehind(90.0F, 3.0D, 0.0D, 120.0D));
    }

    @Test
    void reasonsNameWhatMatters()
    {
        int calm = NpcMorale.reasons(new NpcMorale.Situation(60.0F, 0.0F, 3.0D, 3.0D, 1.0F, false, false, 0, 0.0F), WEIGHTS, 0.0F);
        assertEquals(0, calm, "an even fight at full health has nothing to say");
        int bad = NpcMorale.reasons(new NpcMorale.Situation(60.0F, 0.0F, 1.0D, 3.0D, 0.3F, true, false, 2, 0.0F), WEIGHTS, -20.0F);
        assertTrue((bad & 1 << NpcMorale.REASON_OUTNUMBERED) != 0);
        assertTrue((bad & 1 << NpcMorale.REASON_WOUNDED) != 0);
        assertTrue((bad & 1 << NpcMorale.REASON_FLANKED) != 0);
        assertTrue((bad & 1 << NpcMorale.REASON_PANIC) != 0);
        assertTrue((bad & 1 << NpcMorale.REASON_SHOCK) != 0);
        assertFalse((bad & 1 << NpcMorale.REASON_OUTNUMBERING) != 0);
        int owner = NpcMorale.reasons(new NpcMorale.Situation(70.0F, 0.0F, 4.0D, 1.0D, 1.0F, false, false, 0, 15.0F), WEIGHTS, 0.0F);
        assertTrue((owner & 1 << NpcMorale.REASON_OWNER) != 0);
        assertTrue((owner & 1 << NpcMorale.REASON_OUTNUMBERING) != 0);
        assertEquals(60.0F + 15.0F + 15.0F,
                NpcMorale.target(new NpcMorale.Situation(60.0F, 0.0F, 4.0D, 1.0D, 1.0F, false, false, 0, 15.0F), WEIGHTS), 1.0E-4F);
    }
}
