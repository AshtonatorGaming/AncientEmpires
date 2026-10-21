package com.ancientempires.entity.npc;

import java.util.Set;

/**
 * Which faction ids hunt each other. Same-faction pairs are not hostile.
 * This is a fixed list for the starter factions, not a standing or revenge table.
 * An owned NPC still hunts any faction NPC, including one that is not in this set.
 */
public final class NpcFactions
{
    public static final String EMPIRE = "empire";
    public static final String NORSKA = "norska";
    public static final String BRIGAND = "brigand";
    public static final String PIRATE = "pirate";
    public static final String XOLTEC = "xoltec";

    private static final Set<String> WARRING = Set.of(EMPIRE, NORSKA, BRIGAND, PIRATE, XOLTEC);

    private NpcFactions()
    {
    }

    public static boolean hostile(String attacker, String target)
    {
        if (attacker == null || target == null || attacker.isEmpty() || attacker.equals(target))
        {
            return false;
        }
        return WARRING.contains(attacker) && WARRING.contains(target);
    }
}
