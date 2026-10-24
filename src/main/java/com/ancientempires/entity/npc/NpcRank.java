package com.ancientempires.entity.npc;

import net.minecraft.network.chat.Component;

/**
 * Leadership, apart from skill (level): soldier, sergeant, captain, general.
 * Faction leaders are captains and elite leaders generals (roster rank);
 * elite soldiers are better troops, not sergeants by row (squads will pick a
 * sergeant from them). A player's soldier is promoted one rank at a time with
 * that rank's baton once it has the level ({@code command.promoteLevels}).
 * Each rank commands with its own aura strength and reach ({@link NpcCommand}).
 * A faction may name its ranks (factions.json {@code ranks}), and a pack
 * faction's leaders are alphas ({@code command: pack}).
 */
public enum NpcRank
{
    SOLDIER("soldier"),
    SERGEANT("sergeant"),
    CAPTAIN("captain"),
    GENERAL("general");

    private static final NpcRank[] VALUES = values();

    private final String id;

    NpcRank(String id)
    {
        this.id = id;
    }

    public String id()
    {
        return this.id;
    }

    public static NpcRank byId(int ordinal)
    {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : SOLDIER;
    }

    /** By name, for commands; null if unknown. */
    public static NpcRank byName(String id)
    {
        for (NpcRank rank : VALUES)
        {
            if (rank.id.equals(id))
            {
                return rank;
            }
        }
        return null;
    }

    /** The rank a roster tier gives: leaders are captains, elite leaders generals. */
    static NpcRank ofTier(int tier)
    {
        return tier >= 3 ? GENERAL : tier == 2 ? CAPTAIN : SOLDIER;
    }

    /** The next rank up, or null at the top. */
    public NpcRank next()
    {
        return this.ordinal() + 1 < VALUES.length ? VALUES[this.ordinal() + 1] : null;
    }

    /** The rank's name for a faction: its own name for it, a pack's alpha, or the default. */
    public Component displayName(String faction)
    {
        String named = NpcFactions.rankName(faction, this);
        if (named != null)
        {
            return Component.literal(named);
        }
        if (this != SOLDIER && NpcFactions.commandStyle(faction) == NpcFactions.CommandStyle.PACK)
        {
            return Component.translatable("npc.ancientempires.rank.pack." + this.id);
        }
        return Component.translatable("npc.ancientempires.rank." + this.id);
    }
}
