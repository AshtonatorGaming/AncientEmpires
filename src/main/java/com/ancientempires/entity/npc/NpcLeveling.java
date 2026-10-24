package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * NPC levels, 1 to {@code leveling.maxLevel} (20), from Ancient Warfare's
 * leveling (its cap was 10, its only XP source kills, and each level gave 5%
 * damage and half a heart). Here XP comes from what the NPC does: damage dealt
 * to enemies, kills (more for a higher-level victim), blocked hits and
 * perfect blocks, and work. A level is mostly skill: the {@code skill} lists
 * (four bands) are read at a position worked out from the level, so every
 * level counts, and the stat gains are small ({@code healthPerLevel},
 * {@code damagePerLevel}; workers {@code workSpeedPerLevel} and less tool
 * wear). Bands: soldiers Recruit, Regular, Veteran, Champion; workers
 * Apprentice, Journeyman, Expert, Master. Settlers do not level.
 * Nothing runs per tick: XP is added as things happen.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class NpcLeveling
{
    public static final int BANDS = 4;

    private NpcLeveling()
    {
    }

    /** XP from {@code level} to the next: {@code base} plus {@code step} per level past the first. */
    static double xpToNext(int level, double base, double step)
    {
        return base + step * Math.max(0, level - 1);
    }

    /** The skill band, 0 to 3, of a level: four even bands up to the cap (1-5, 6-10, 11-15, 16-20). */
    public static int band(int level, int maxLevel)
    {
        return Mth.clamp((level - 1) * BANDS / Math.max(1, maxLevel), 0, BANDS - 1);
    }

    /** Where a level sits along the four-entry skill lists, 0 at level 1 to 3 at the cap. */
    static double position(int level, int maxLevel)
    {
        if (maxLevel <= 1)
        {
            return 0.0D;
        }
        return Mth.clamp((level - 1) * (BANDS - 1.0D) / (maxLevel - 1.0D), 0.0D, BANDS - 1.0D);
    }

    /** A list read at a fractional position, between its two neighbouring entries. */
    static double lerp(List<? extends Number> values, double position)
    {
        if (values.isEmpty())
        {
            return 0.0D;
        }
        double at = Mth.clamp(position, 0.0D, values.size() - 1.0D);
        int low = (int) Math.floor(at);
        int high = Math.min(values.size() - 1, low + 1);
        double t = at - low;
        return values.get(low).doubleValue() * (1.0D - t) + values.get(high).doubleValue() * t;
    }

    /** A faction NPC's starting level for its rank tier, from the {@code factionMinByTier} and {@code factionMaxByTier} lists. */
    static int roll(int tier, RandomSource random)
    {
        List<? extends Integer> mins = AncientEmpiresConfig.levelFactionMin;
        List<? extends Integer> maxes = AncientEmpiresConfig.levelFactionMax;
        if (mins.isEmpty() || maxes.isEmpty())
        {
            return 1;
        }
        int min = mins.get(Mth.clamp(tier, 0, mins.size() - 1));
        int max = Math.max(min, maxes.get(Mth.clamp(tier, 0, maxes.size() - 1)));
        return Mth.clamp(min + random.nextInt(max - min + 1), 1, AncientEmpiresConfig.levelMax);
    }

    /** XP for damage that landed on an enemy, after armour. Lowest priority, so cancelled or reduced damage counts as it ended. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event)
    {
        if (event.getAmount() > 0.0F && event.getSource().getEntity() instanceof NpcEntity npc && isFoe(npc, event.getEntity()))
        {
            npc.addXp(event.getAmount() * AncientEmpiresConfig.levelXpPerDamage);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event)
    {
        if (event.isCanceled() || !(event.getSource().getEntity() instanceof NpcEntity npc) || !isFoe(npc, event.getEntity()))
        {
            return;
        }
        int victimLevel = event.getEntity() instanceof NpcEntity victim ? victim.getLevel() : 1;
        npc.addXp(AncientEmpiresConfig.levelXpPerKill + victimLevel * AncientEmpiresConfig.levelXpPerKillLevel);
    }

    /** Fighting counts against monsters, players, and NPCs that are not allies; not against livestock or friends. */
    static boolean isFoe(NpcEntity npc, Entity victim)
    {
        if (victim == npc || !(victim instanceof LivingEntity))
        {
            return false;
        }
        if (victim instanceof NpcEntity other)
        {
            return !npc.isAlly(other) && !NpcEntity.sameOwner(npc, other);
        }
        if (victim instanceof Player player)
        {
            return !player.getUUID().equals(npc.getOwner());
        }
        return victim instanceof Enemy || npc.isScanTarget((LivingEntity) victim);
    }
}
