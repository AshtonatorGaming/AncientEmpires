package com.ancientempires.entity.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who has left a chain of command, per dimension, and who took their place.
 * A link names a UUID that may sit in an unloaded chunk; when it cannot be
 * found, this says whether it is merely away (not here: wait) or gone for
 * good (fell, or was packed into a scroll), and if gone, its heir. Entries
 * are dropped after {@link #KEEP_TICKS}, long after any link could still ask.
 */
public final class NpcChainLedger extends SavedData
{
    private static final String NAME = "ancientempires_chain";
    /** Seven in-game days. */
    private static final long KEEP_TICKS = 24000L * 7L;

    /** A departure: its heir (null when the unit was left leaderless) and when. */
    public record Gone(@Nullable UUID heir, long at)
    {
    }

    private final Map<UUID, Gone> gone = new HashMap<>();

    public static NpcChainLedger get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(NpcChainLedger::load, NpcChainLedger::new, NAME);
    }

    public void record(UUID who, @Nullable UUID heir, long now)
    {
        this.gone.put(who, new Gone(heir, now));
        this.prune(now);
        this.setDirty();
    }

    /** The departure of {@code who}, or null when it has not left (it may just be unloaded). */
    @Nullable
    public Gone gone(UUID who)
    {
        return this.gone.get(who);
    }

    private void prune(long now)
    {
        this.gone.values().removeIf(entry -> now - entry.at() > KEEP_TICKS || now < entry.at());
    }

    private static NpcChainLedger load(CompoundTag tag)
    {
        NpcChainLedger ledger = new NpcChainLedger();
        for (Tag entry : tag.getList("Gone", Tag.TAG_COMPOUND))
        {
            CompoundTag one = (CompoundTag) entry;
            ledger.gone.put(one.getUUID("Who"), new Gone(one.hasUUID("Heir") ? one.getUUID("Heir") : null, one.getLong("At")));
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        this.gone.forEach((who, entry) ->
        {
            CompoundTag one = new CompoundTag();
            one.putUUID("Who", who);
            if (entry.heir() != null)
            {
                one.putUUID("Heir", entry.heir());
            }
            one.putLong("At", entry.at());
            list.add(one);
        });
        tag.put("Gone", list);
        return tag;
    }
}
