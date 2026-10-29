package com.ancientempires.entity.npc;

import java.util.Locale;

/**
 * A unit's standing order (docs/chain-of-command.md), held by a leader and
 * inherited by everyone under it unless a unit below was given its own.
 */
public enum NpcOrder
{
    /** No order: each soldier as before the chain (wander near home, defend the owner, chase threats). */
    FREE,
    /** Formed up on the leader; the top of the chain follows its owner. */
    FOLLOW,
    /** Holds a spot in formation, facing a way, fighting only what comes within reach of the line. */
    HOLD,
    /** Guards an area: goes after what comes inside it, then returns to the spot. */
    DEFEND,
    /** Goes for one enemy; when it falls, the unit holds where it stands. */
    ATTACK,
    /** Retreats to a rally point, fighting only when cornered, then holds there. */
    FALL_BACK;

    public String id()
    {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** Whether the unit stands at the order's spot (its leader goes there, the rest form on it). */
    public boolean anchored()
    {
        return this == HOLD || this == DEFEND || this == FALL_BACK;
    }

    /** Whether the soldiers under it keep formation on their leader. */
    public boolean formed()
    {
        return this == FOLLOW || this.anchored();
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
        return null;
    }
}
