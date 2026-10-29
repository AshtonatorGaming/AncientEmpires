package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caps the path requests one owner's units start in a tick
 * ({@code chain.pathsPerTick}), as when a whole army is given an order at
 * once: the rest wait a tick and ask again. Faction NPCs (no owner) are not
 * capped. Server thread only.
 */
public final class NpcPathBudget
{
    /** Per owner: the game time counted, and the requests started then. */
    private static final Map<UUID, long[]> SPENT = new HashMap<>();

    private NpcPathBudget()
    {
    }

    /** Whether {@code npc} may start a path now; counts it if so. */
    public static boolean take(NpcEntity npc)
    {
        UUID owner = npc.getOwner();
        if (owner == null)
        {
            return true;
        }
        long now = npc.level().getGameTime();
        long[] spent = SPENT.computeIfAbsent(owner, id -> new long[] {-1L, 0L});
        if (spent[0] != now)
        {
            spent[0] = now;
            spent[1] = 0L;
        }
        if (spent[1] >= AncientEmpiresConfig.chainPathsPerTick)
        {
            return false;
        }
        spent[1]++;
        return true;
    }
}
