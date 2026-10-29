package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * An NPC's growth: its level and XP ({@link NpcLeveling}, the {@code levels}
 * config) and its rank ({@link NpcRank}), with what they bring: health per
 * level (a saved modifier), damage per level (read by the proficiencies), and
 * for workers speed and gentler tool wear, quickened while a governor
 * oversees them. Level and XP are synced for the inventory; the rank too.
 * Saved as {@code AeLevel}, {@code AeXp}, {@code AeRank} and packed with the NPC.
 */
public final class NpcProgress
{
    private static final String RANK_KEY = "AeRank";
    private static final String LEVEL_KEY = "AeLevel";
    private static final String XP_KEY = "AeXp";
    private static final UUID LEVEL_HEALTH_ID = UUID.fromString("3c2b8f4e-71d9-4a8b-9e57-0f6a2d1c5b93");

    private final NpcEntity npc;
    /** A promoted rank ({@code AeRank}); null for the roster's. */
    @Nullable
    private NpcRank rankOverride;
    /** tickCount until which a governor oversees this worker. */
    private int governedUntil;

    NpcProgress(NpcEntity npc)
    {
        this.npc = npc;
    }

    /** A roster subtype's rank tier: 0 ordinary, 1 elite, 2 leader, 3 elite leader. */
    static int tierOf(String subtype)
    {
        boolean elite = subtype.endsWith(".elite");
        if (subtype.startsWith("leader"))
        {
            return elite ? 3 : 2;
        }
        return elite ? 1 : 0;
    }

    public int level()
    {
        return Math.max(1, this.npc.getEntityData().get(NpcEntity.DATA_LEVEL));
    }

    /** XP toward the next level. */
    public float xp()
    {
        return this.npc.getEntityData().get(NpcEntity.DATA_XP);
    }

    /** XP from this level to the next, or 0 at the cap. */
    public double xpToNext()
    {
        int level = this.level();
        return level >= AncientEmpiresConfig.levelMax ? 0.0D
                : NpcLeveling.xpToNext(level, AncientEmpiresConfig.levelXpBase, AncientEmpiresConfig.levelXpStep);
    }

    /** Settlers do not level; workers and soldiers do. */
    public boolean levels()
    {
        return this.npc.getNpcPath() != NpcPath.SETTLER;
    }

    /** The level's band, 0 to 3: Recruit, Regular, Veteran, Champion (soldiers) or Apprentice to Master (workers). */
    public int band()
    {
        return NpcLeveling.band(this.level(), AncientEmpiresConfig.levelMax);
    }

    /**
     * Leadership: a promoted rank ({@code AeRank}), else the roster's (faction
     * leaders are captains, elite leaders generals). The client reads the synced copy.
     */
    public NpcRank rank()
    {
        if (this.npc.level().isClientSide)
        {
            return NpcRank.byId(this.npc.getEntityData().get(NpcEntity.DATA_RANK));
        }
        return this.rankOverride != null ? this.rankOverride : NpcRank.ofTier(this.npc.getSkillTier());
    }

    /** Promotes (or, for admins, sets) the rank. */
    public void setRank(NpcRank rank)
    {
        this.rankOverride = rank;
        this.npc.getEntityData().set(NpcEntity.DATA_RANK, (byte) rank.ordinal());
    }

    /** Sets the level (admins, faction rolls, loading) and its stat gains. */
    public void setLevel(int level)
    {
        int clamped = Mth.clamp(level, 1, Math.max(1, AncientEmpiresConfig.levelMax));
        this.npc.getEntityData().set(NpcEntity.DATA_LEVEL, (byte) clamped);
        this.applyLevelStats();
    }

    /**
     * XP for something done (a hit, a kill, a block, a work action). Levels
     * come one at a time, each with a little health; an owner hears of it.
     */
    public void addXp(double amount)
    {
        if (amount <= 0.0D || !AncientEmpiresConfig.levelEnabled || this.npc.level().isClientSide || !this.levels() || this.npc.isCreative())
        {
            return;
        }
        int level = this.level();
        if (level >= AncientEmpiresConfig.levelMax)
        {
            return;
        }
        double xp = this.xp() + amount;
        double need = this.xpToNext();
        int gained = 0;
        while (need > 0.0D && xp >= need && level < AncientEmpiresConfig.levelMax)
        {
            xp -= need;
            level++;
            gained++;
            need = NpcLeveling.xpToNext(level, AncientEmpiresConfig.levelXpBase, AncientEmpiresConfig.levelXpStep);
        }
        if (level >= AncientEmpiresConfig.levelMax)
        {
            xp = 0.0D;
        }
        this.npc.getEntityData().set(NpcEntity.DATA_XP, (float) xp);
        if (gained > 0)
        {
            float before = this.npc.getMaxHealth();
            int bandBefore = this.band();
            this.setLevel(level);
            if (this.band() != bandBefore)
            {
                this.npc.getRecord().note(this.npc, "band", Component.translatable(bandKey(this.npc.getNpcPath(), this.band())));
            }
            // The health a level brings comes with it, as a fresh wind.
            this.npc.heal(this.npc.getMaxHealth() - before);
            this.onLevelUp(level);
        }
    }

    private void onLevelUp(int level)
    {
        if (!this.npc.isOwnedNpc())
        {
            // A faction NPC grows quietly; only a player's own get the fanfare.
            return;
        }
        if (this.npc.level() instanceof ServerLevel server)
        {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.npc.getX(), this.npc.getY() + this.npc.getBbHeight() * 0.6D, this.npc.getZ(),
                    10, 0.4D, 0.5D, 0.4D, 0.0D);
            this.npc.playSound(SoundEvents.PLAYER_LEVELUP, 0.4F, 1.2F);
        }
        UUID owner = this.npc.getOwner();
        Player player = owner == null || !AncientEmpiresConfig.levelAnnounce ? null : this.npc.level().getPlayerByUUID(owner);
        if (player != null)
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.npc.level_up", this.npc.getDisplayName(),
                    Component.translatable(bandKey(this.npc.getNpcPath(), this.band())), level), true);
        }
    }

    /** Translation key of a band name for the path: soldiers and workers name them differently. */
    public static String bandKey(NpcPath path, int band)
    {
        return "npc.ancientempires.band." + (path.combat() ? "soldier" : "worker") + "." + Mth.clamp(band, 0, NpcLeveling.BANDS - 1);
    }

    /** Health per level, as a saved modifier so a reload keeps the health it allows. Damage is in the proficiencies. */
    private void applyLevelStats()
    {
        this.npc.invalidateProficiency();
        AttributeInstance health = this.npc.getAttribute(Attributes.MAX_HEALTH);
        if (health == null)
        {
            return;
        }
        double bonus = (this.level() - 1) * AncientEmpiresConfig.levelHealthPerLevel;
        AttributeModifier current = health.getModifier(LEVEL_HEALTH_ID);
        if (current != null && current.getAmount() == bonus)
        {
            return;
        }
        health.removeModifier(LEVEL_HEALTH_ID);
        if (bonus > 0.0D)
        {
            health.addPermanentModifier(new AttributeModifier(LEVEL_HEALTH_ID, "Ancient Empires level", bonus, AttributeModifier.Operation.ADDITION));
        }
        if (this.npc.getHealth() > this.npc.getMaxHealth())
        {
            this.npc.setHealth(this.npc.getMaxHealth());
        }
    }

    /** Damage multiplier from level ({@code levels.damagePerLevel}). */
    double levelDamage()
    {
        return 1.0D + (this.level() - 1) * AncientEmpiresConfig.levelDamagePerLevel;
    }

    /** A governor at the Town Hall oversees this worker for {@code ticks}: it works faster ({@code governor.workBonus}). */
    void govern(int ticks)
    {
        this.governedUntil = this.npc.tickCount + ticks;
    }

    /** Work speed multiplier from level, for workers' action times. */
    public double workSpeed()
    {
        return (1.0D + (this.level() - 1) * AncientEmpiresConfig.levelWorkSpeedPerLevel)
                * (this.npc.tickCount < this.governedUntil ? 1.0D + AncientEmpiresConfig.governorWorkBonus : 1.0D)
                * (this.npc.hearsSong() ? 1.0D + AncientEmpiresConfig.bardWorkBonus : 1.0D);
    }

    /** Tool wear multiplier from level: skilled hands wear tools less. */
    public double toolWearScale()
    {
        return Math.max(0.1D, 1.0D - (this.level() - 1) * AncientEmpiresConfig.levelToolWearCutPerLevel);
    }

    /** A faction NPC's starting level by rank; the roster row rolls it on first placement. */
    void roll(String subtype)
    {
        this.setLevel(NpcLeveling.roll(tierOf(subtype), this.npc.getRandom()));
    }

    void save(CompoundTag tag)
    {
        tag.putInt(LEVEL_KEY, this.level());
        tag.putFloat(XP_KEY, this.xp());
        if (this.rankOverride != null)
        {
            tag.putString(RANK_KEY, this.rankOverride.id());
        }
    }

    void load(CompoundTag tag)
    {
        if (tag.contains(RANK_KEY))
        {
            NpcRank rank = NpcRank.byName(tag.getString(RANK_KEY));
            if (rank != null)
            {
                this.setRank(rank);
            }
        }
        if (tag.contains(LEVEL_KEY))
        {
            this.npc.getEntityData().set(NpcEntity.DATA_XP, tag.getFloat(XP_KEY));
            this.setLevel(tag.getInt(LEVEL_KEY));
        }
    }

    /** Whether the save had a level (older saves of faction NPCs roll one). */
    boolean loaded(CompoundTag tag)
    {
        return tag.contains(LEVEL_KEY);
    }
}
