package com.ancientempires.entity.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An NPC's service record and journal (docs/npc-screen-design.md): counts of
 * kills, battles, wounds, routs, and times raised, its best kill, and the last
 * {@link #JOURNAL_SIZE} notable things that happened to it, as translation keys
 * and arguments. Written only when those things happen and read when its screen
 * opens (sent with it); nothing per tick, nothing synced in battle. Saved and
 * packed with the NPC, so a repacked veteran keeps its story.
 */
public final class NpcRecord
{
    private static final String KEY = "AeRecord";
    public static final int JOURNAL_SIZE = 10;
    /** Ticks without a target after which a new fight counts as another battle (a minute). */
    private static final long BATTLE_GAP = 1200L;
    /** Health shares at which a wound is counted (below) and healed (above). */
    private static final float WOUNDED = 0.3F;
    private static final float HEALED = 0.7F;

    /** One line of the journal: the world day, and what happened (a key under gui.ancientempires.journal, with its arguments). */
    public record Entry(long day, String key, List<Component> args)
    {
        public Component text()
        {
            return Component.translatable("gui.ancientempires.journal." + this.key, this.args.toArray());
        }
    }

    private int kills;
    private int battles;
    private int wounds;
    private int routs;
    private int raised;
    @Nullable
    private Component bestKill;
    private int bestScore = -1;
    private final List<Entry> journal = new ArrayList<>();
    /** Not saved: the last tick a target was taken, and whether the current wound has been counted. */
    private long lastFight = Long.MIN_VALUE / 2;
    private boolean wounded;

    public int kills()
    {
        return this.kills;
    }

    public int battles()
    {
        return this.battles;
    }

    public int wounds()
    {
        return this.wounds;
    }

    public int routs()
    {
        return this.routs;
    }

    public int raised()
    {
        return this.raised;
    }

    @Nullable
    public Component bestKill()
    {
        return this.bestKill;
    }

    /** Newest first. */
    public List<Entry> journal()
    {
        List<Entry> newest = new ArrayList<>(this.journal);
        Collections.reverse(newest);
        return newest;
    }

    void note(NpcEntity npc, String key, Component... args)
    {
        this.journal.add(new Entry(npc.level().getDayTime() / 24000L + 1L, key, List.of(args)));
        while (this.journal.size() > JOURNAL_SIZE)
        {
            this.journal.remove(0);
        }
    }

    /** Entered its owner's service (first placed or claimed). */
    void entered(NpcEntity npc)
    {
        if (this.journal.isEmpty())
        {
            this.note(npc, "entered");
        }
    }

    /**
     * A kill: counted, kept as the best kill if it is the most notable yet
     * (rank and level of an NPC, a player, a named creature, else its health),
     * and journaled when it is worth telling (an officer, a player, a named or
     * great creature, or the first kill).
     */
    void killed(NpcEntity npc, LivingEntity victim)
    {
        this.kills++;
        Component name = victim.getDisplayName();
        int score = (int) victim.getMaxHealth();
        boolean notable = this.kills == 1 || victim instanceof Player || victim.hasCustomName() || victim.getMaxHealth() >= 100.0F;
        if (victim instanceof NpcEntity other)
        {
            score = other.getRank().ordinal() * 100 + other.getLevel() * 5 + score;
            if (other.getRank() != NpcRank.SOLDIER)
            {
                name = Component.translatable("gui.ancientempires.record.ranked", name, other.getRankName());
                notable = true;
            }
        }
        if (victim instanceof Player || victim.hasCustomName())
        {
            score += 200;
        }
        if (score > this.bestScore)
        {
            this.bestScore = score;
            this.bestKill = name;
        }
        if (notable)
        {
            this.note(npc, this.kills == 1 ? "first_kill" : "slew", name);
        }
        else if (this.kills == 10 || this.kills == 25 || this.kills == 50 || this.kills % 100 == 0)
        {
            this.note(npc, "kills", Component.literal(Integer.toString(this.kills)));
        }
    }

    /** A target taken: a new battle when the last fight was over a minute ago. */
    void fought(NpcEntity npc)
    {
        long now = npc.level().getGameTime();
        if (now - this.lastFight > BATTLE_GAP)
        {
            this.battles++;
        }
        this.lastFight = now;
    }

    /** After a hit: a wound when brought under 30% health, counted once until it heals past 70%. */
    void hurt(NpcEntity npc, float healthBefore, @Nullable LivingEntity by)
    {
        float max = npc.getMaxHealth();
        if (healthBefore > max * HEALED)
        {
            this.wounded = false;
        }
        if (!this.wounded && npc.isAlive() && npc.getHealth() < max * WOUNDED)
        {
            this.wounded = true;
            this.wounds++;
            if (by != null)
            {
                this.note(npc, "wounded_by", by.getDisplayName());
            }
            else
            {
                this.note(npc, "wounded");
            }
        }
    }

    public void promoted(NpcEntity npc, Component rank)
    {
        this.note(npc, "promoted", rank);
    }

    void routed(NpcEntity npc)
    {
        this.routs++;
        this.note(npc, "routed");
    }

    void raisedBy(NpcEntity npc, Component priest)
    {
        this.raised++;
        this.note(npc, "raised", priest);
    }

    public void save(CompoundTag tag)
    {
        tag.put(KEY, this.write());
    }

    /** The record alone, also what the screen is sent. */
    public CompoundTag write()
    {
        CompoundTag record = new CompoundTag();
        record.putInt("Kills", this.kills);
        record.putInt("Battles", this.battles);
        record.putInt("Wounds", this.wounds);
        record.putInt("Routs", this.routs);
        record.putInt("Raised", this.raised);
        if (this.bestKill != null)
        {
            record.putString("Best", Component.Serializer.toJson(this.bestKill));
            record.putInt("BestScore", this.bestScore);
        }
        ListTag entries = new ListTag();
        for (Entry entry : this.journal)
        {
            CompoundTag line = new CompoundTag();
            line.putLong("Day", entry.day());
            line.putString("Key", entry.key());
            ListTag args = new ListTag();
            for (Component arg : entry.args())
            {
                args.add(StringTag.valueOf(Component.Serializer.toJson(arg)));
            }
            line.put("Args", args);
            entries.add(line);
        }
        record.put("Journal", entries);
        return record;
    }

    public void load(CompoundTag tag)
    {
        this.read(tag.getCompound(KEY));
    }

    public void read(CompoundTag record)
    {
        this.kills = record.getInt("Kills");
        this.battles = record.getInt("Battles");
        this.wounds = record.getInt("Wounds");
        this.routs = record.getInt("Routs");
        this.raised = record.getInt("Raised");
        this.bestKill = record.contains("Best") ? Component.Serializer.fromJson(record.getString("Best")) : null;
        this.bestScore = record.contains("Best") ? record.getInt("BestScore") : -1;
        this.journal.clear();
        for (Tag line : record.getList("Journal", Tag.TAG_COMPOUND))
        {
            CompoundTag entry = (CompoundTag) line;
            List<Component> args = new ArrayList<>();
            for (Tag arg : entry.getList("Args", Tag.TAG_STRING))
            {
                Component parsed = Component.Serializer.fromJson(arg.getAsString());
                args.add(parsed == null ? Component.empty() : parsed);
            }
            this.journal.add(new Entry(entry.getLong("Day"), entry.getString("Key"), args));
        }
    }
}
