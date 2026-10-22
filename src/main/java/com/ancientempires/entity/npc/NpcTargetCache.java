package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Short-lived candidate lists shared by every NPC standing in the same cell.
 * A list holds each {@link Enemy}, {@link NpcEntity}, player, and townsfolk mob near the cell. It is
 * not filtered by faction or owner: that test is per NPC and cheap, so one
 * query serves every side in the cell. Entries can be a few ticks stale, so
 * readers skip dead or removed entities and measure distance live.
 * Server thread only.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class NpcTargetCache
{
    /** Cells are 16 blocks across and 8 tall. The query box adds the scan range around the cell. */
    private static final int CELL_SHIFT_XZ = 4;
    private static final int CELL_SHIFT_Y = 3;
    /** Vertical reach past the cell, the same 4 blocks vanilla's nearest-target box uses. */
    private static final double VERTICAL_REACH = 4.0D;
    private static final int PRUNE_SIZE = 512;
    private static final int STATS_PERIOD = 200;

    private static final Map<Level, Long2ObjectOpenHashMap<Entry>> LEVELS = new WeakHashMap<>();

    private static long boxQueries;
    private static long scans;
    private static long sightChecks;
    private static long legacyCandidates;
    private static int statsTicks;

    private NpcTargetCache()
    {
    }

    /**
     * Candidates around the NPC's cell, at most {@code cacheTicks} old. The list
     * is shared; callers must not change it.
     */
    static List<LivingEntity> candidates(NpcEntity npc, double range)
    {
        int ttl = AncientEmpiresConfig.targetCacheTicks;
        if (ttl <= 0)
        {
            boxQueries++;
            return query(npc.level(), npc.getBoundingBox().inflate(range, VERTICAL_REACH, range));
        }
        int cellX = Mth.floor(npc.getX()) >> CELL_SHIFT_XZ;
        int cellY = Mth.floor(npc.getY()) >> CELL_SHIFT_Y;
        int cellZ = Mth.floor(npc.getZ()) >> CELL_SHIFT_XZ;
        long key = BlockPos.asLong(cellX, cellY, cellZ);
        long now = npc.level().getGameTime();
        Long2ObjectOpenHashMap<Entry> cells = LEVELS.computeIfAbsent(npc.level(), level -> new Long2ObjectOpenHashMap<>());
        Entry entry = cells.get(key);
        if (entry != null && entry.expires > now && entry.range == range)
        {
            return entry.list;
        }
        if (cells.size() > PRUNE_SIZE)
        {
            cells.values().removeIf(stale -> stale.expires <= now);
        }
        double minX = (double) (cellX << CELL_SHIFT_XZ);
        double minY = (double) (cellY << CELL_SHIFT_Y);
        double minZ = (double) (cellZ << CELL_SHIFT_XZ);
        AABB box = new AABB(minX, minY, minZ,
                minX + (1 << CELL_SHIFT_XZ), minY + (1 << CELL_SHIFT_Y), minZ + (1 << CELL_SHIFT_XZ))
                .inflate(range, VERTICAL_REACH, range);
        boxQueries++;
        List<LivingEntity> list = query(npc.level(), box);
        cells.put(key, new Entry(list, now + ttl, range));
        return list;
    }

    private static List<LivingEntity> query(Level level, AABB box)
    {
        // Anything some NPC might hunt: monsters, NPCs, players (by standing), and the
        // townsfolk evil factions go after. Each NPC filters the shared list itself.
        return level.getEntitiesOfClass(LivingEntity.class, box,
                living -> living instanceof Enemy || living instanceof NpcEntity || living instanceof Player
                        || NpcFactions.isTownsfolk(living));
    }

    static void countScan()
    {
        scans++;
    }

    static void countSightCheck()
    {
        sightChecks++;
    }

    /**
     * Legacy goals only. Counts a candidate that passed the selector and will
     * reach vanilla's range and sight tests, so it is an upper bound on raycasts.
     */
    static boolean countLegacyCandidate()
    {
        legacyCandidates++;
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        if (event.getServer().getTickCount() % STATS_PERIOD == 0)
        {
            // Expired cells would otherwise keep their entities until the cell is visited again.
            for (Map.Entry<Level, Long2ObjectOpenHashMap<Entry>> level : LEVELS.entrySet())
            {
                long now = level.getKey().getGameTime();
                level.getValue().values().removeIf(stale -> stale.expires <= now);
            }
        }
        if (!AncientEmpiresConfig.targetDebugStats)
        {
            statsTicks = 0;
            return;
        }
        if (++statsTicks < STATS_PERIOD)
        {
            return;
        }
        double seconds = STATS_PERIOD / 20.0D;
        if (AncientEmpiresConfig.targetLegacyScan)
        {
            AncientEmpires.LOGGER.info("NPC target scan (legacy): {}/s candidates reaching the sight test (upper bound on raycasts).",
                    String.format("%.1f", legacyCandidates / seconds));
        }
        else
        {
            AncientEmpires.LOGGER.info("NPC target scan: {}/s scans, {}/s box queries, {}/s sight checks.",
                    String.format("%.1f", scans / seconds),
                    String.format("%.1f", boxQueries / seconds),
                    String.format("%.1f", sightChecks / seconds));
        }
        statsTicks = 0;
        scans = 0;
        boxQueries = 0;
        sightChecks = 0;
        legacyCandidates = 0;
    }

    private record Entry(List<LivingEntity> list, long expires, double range)
    {
    }
}
