package com.ancientempires.entity.npc;

import java.util.Locale;

/**
 * A unit's standing order (docs/chain-of-command.md), held by a leader and
 * inherited by everyone under it. Phase 1 has the two that need no target:
 * Free (each soldier as today) and Follow (formed up on the leader, the top
 * leader on the player). Hold, Defend, Attack, and Fall back come with phase 2.
 */
public enum NpcOrder
{
    FREE,
    FOLLOW;

    public String id()
    {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static NpcOrder byName(String name)
    {
        for (NpcOrder order : values())
        {
            if (order.id().equals(name))
            {
                return order;
            }
        }
        return FREE;
    }
}
