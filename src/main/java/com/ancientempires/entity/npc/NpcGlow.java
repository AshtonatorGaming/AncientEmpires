package com.ancientempires.entity.npc;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * NPCs the local player's command tools point out, outlined through walls on
 * that player's screen only: the units picked on the battle wheel and while
 * placing them. Filled on the client (entity ids), read by
 * {@link NpcEntity#isCurrentlyGlowing}; the server never touches it.
 */
public final class NpcGlow
{
    /** The outline's colour: the parchment gold of the army screens. */
    public static final int COLOR = 0xE2C27A;

    private static IntSet ids = new IntOpenHashSet();

    private NpcGlow()
    {
    }

    public static boolean has(int id)
    {
        return !ids.isEmpty() && ids.contains(id);
    }

    public static void set(IntSet glowing)
    {
        ids = glowing;
    }

    public static void clear()
    {
        ids = new IntOpenHashSet();
    }
}
