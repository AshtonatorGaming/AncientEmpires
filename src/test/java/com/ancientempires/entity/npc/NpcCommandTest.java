package com.ancientempires.entity.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCommandTest
{
    @Test
    void aSuccessorCommandsWeaker()
    {
        assertEquals(0.6F, NpcCommand.inherited(1.0F, 0.6D, 0.3D), 1.0E-6F);
        assertEquals(0.75F, NpcCommand.inherited(1.25F, 0.6D, 0.3D), 1.0E-6F);
    }

    @Test
    void commandPassedOnAndOnNeverFadesBelowTheFloor()
    {
        float strength = 1.25F;
        for (int generation = 0; generation < 10; generation++)
        {
            float next = NpcCommand.inherited(strength, 0.6D, 0.3D);
            assertTrue(next <= strength, "never stronger than the one before");
            assertTrue(next >= 0.3F, "never below the floor");
            strength = next;
        }
        assertEquals(0.3F, strength, 1.0E-6F);
    }
}
