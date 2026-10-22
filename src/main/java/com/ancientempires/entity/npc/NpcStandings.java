package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Each player's standing with each faction, saved with the world (overworld
 * data, {@code ancientempires_standings}). A player who has never met a faction
 * has its default from {@link NpcFactions}; only changed values are stored.
 * Below zero, the faction is hostile to that player and to the player's NPCs,
 * as in Ancient Warfare.
 */
public final class NpcStandings extends SavedData
{
    private static final String NAME = "ancientempires_standings";

    private final Map<UUID, Object2IntMap<String>> standings = new HashMap<>();

    public static NpcStandings get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(NpcStandings::load, NpcStandings::new, NAME);
    }

    public int get(UUID player, String faction)
    {
        Object2IntMap<String> entries = this.standings.get(player);
        if (entries != null && entries.containsKey(faction))
        {
            return entries.getInt(faction);
        }
        return NpcFactions.defaultStanding(faction);
    }

    public boolean hostile(UUID player, String faction)
    {
        return this.get(player, faction) < 0;
    }

    /** Sets a standing, clamped to {@code relations.standingLimit}. Returns the stored value. */
    public int set(UUID player, String faction, int value)
    {
        int limit = AncientEmpiresConfig.relationsStandingLimit;
        int clamped = Mth.clamp(value, -limit, limit);
        this.standings.computeIfAbsent(player, ignored -> new Object2IntOpenHashMap<>()).put(faction, clamped);
        this.setDirty();
        return clamped;
    }

    public int adjust(UUID player, String faction, int delta)
    {
        return this.set(player, faction, this.get(player, faction) + delta);
    }

    /** Back to every faction's default. */
    public void reset(UUID player)
    {
        if (this.standings.remove(player) != null)
        {
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Object2IntMap<String>> player : this.standings.entrySet())
        {
            CompoundTag factions = new CompoundTag();
            for (Object2IntMap.Entry<String> entry : player.getValue().object2IntEntrySet())
            {
                factions.putInt(entry.getKey(), entry.getIntValue());
            }
            players.put(player.getKey().toString(), factions);
        }
        tag.put("Players", players);
        return tag;
    }

    private static NpcStandings load(CompoundTag tag)
    {
        NpcStandings data = new NpcStandings();
        CompoundTag players = tag.getCompound("Players");
        for (String key : players.getAllKeys())
        {
            UUID player;
            try
            {
                player = UUID.fromString(key);
            }
            catch (IllegalArgumentException exception)
            {
                continue;
            }
            CompoundTag factions = players.getCompound(key);
            Object2IntMap<String> entries = new Object2IntOpenHashMap<>();
            for (String faction : factions.getAllKeys())
            {
                entries.put(faction, factions.getInt(faction));
            }
            data.standings.put(player, entries);
        }
        return data;
    }
}
