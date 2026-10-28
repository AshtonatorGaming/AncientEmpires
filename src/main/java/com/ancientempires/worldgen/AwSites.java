package com.ancientempires.worldgen;

import com.ancientempires.AncientEmpires;
import com.ancientempires.structure.StructureRecords;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Every structure standing in each dimension, readable from world generation
 * threads: bounds by region (for overlap and AW's minimum duplicate distance),
 * the unique templates already placed, and the cluster value each territory
 * cell has spent. Filled from {@link StructureRecords} as each level loads,
 * and kept current by hand placements and world generation. A site chosen by
 * world generation is claimed here at once ({@link #claim}, one at a time, so
 * two threads never take the same ground) and recorded in
 * {@link StructureRecords} on the main thread, as saved data is not safe to
 * touch from generation threads. Also maps each chunk generator to its level,
 * since a structure's generation context does not say which dimension it is.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class AwSites
{
    /** Regions of this many blocks across index the sites. */
    private static final int REGION = 256;

    private record Placed(String template, BoundingBox bounds)
    {
    }

    private record Pending(ResourceKey<Level> dimension, String template, BoundingBox bounds, String faction)
    {
    }

    /** One dimension's sites; guarded by its own lock. */
    private static final class Sites
    {
        final Map<Long, List<Placed>> byRegion = new HashMap<>();
        final Set<String> templates = new HashSet<>();
        /** Cluster value spent per territory cell. */
        final Map<Long, Integer> spent = new HashMap<>();

        void add(Placed site)
        {
            this.templates.add(site.template());
            for (long region : regions(site.bounds(), 0))
            {
                this.byRegion.computeIfAbsent(region, key -> new ArrayList<>()).add(site);
            }
        }

        boolean overlaps(BoundingBox bounds, int margin)
        {
            BoundingBox wide = bounds.inflatedBy(margin);
            for (long region : regions(wide, 0))
            {
                for (Placed site : this.byRegion.getOrDefault(region, List.of()))
                {
                    if (site.bounds().intersects(wide))
                    {
                        return true;
                    }
                }
            }
            return false;
        }

        boolean anyNear(int x, int z, int distance)
        {
            long limit = (long) distance * distance;
            for (long region : regions(new BoundingBox(x, 0, z, x, 0, z), distance))
            {
                for (Placed site : this.byRegion.getOrDefault(region, List.of()))
                {
                    long dx = site.bounds().getCenter().getX() - x;
                    long dz = site.bounds().getCenter().getZ() - z;
                    if (dx * dx + dz * dz < limit)
                    {
                        return true;
                    }
                }
            }
            return false;
        }

        boolean nearby(String template, BoundingBox bounds, int distance)
        {
            if (!this.templates.contains(template))
            {
                return false;
            }
            long limit = (long) distance * distance;
            int cx = bounds.getCenter().getX();
            int cz = bounds.getCenter().getZ();
            for (long region : regions(new BoundingBox(cx, 0, cz, cx, 0, cz), distance))
            {
                for (Placed site : this.byRegion.getOrDefault(region, List.of()))
                {
                    long dx = site.bounds().getCenter().getX() - cx;
                    long dz = site.bounds().getCenter().getZ() - cz;
                    if (site.template().equals(template) && dx * dx + dz * dz < limit)
                    {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static final Map<ResourceKey<Level>, Sites> SITES = new ConcurrentHashMap<>();
    /** By identity: some mods give generators a hash that changes, which lost every lookup in a hash map. */
    private static final Map<ChunkGenerator, ServerLevel> LEVELS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Queue<Pending> PENDING = new ConcurrentLinkedQueue<>();

    private AwSites()
    {
    }

    /** The level a chunk generator belongs to (null before it loads); asks the server when the generator was not seen loading. */
    @Nullable
    public static ServerLevel level(ChunkGenerator generator)
    {
        ServerLevel known = LEVELS.get(generator);
        if (known != null)
        {
            return known;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null)
        {
            for (ServerLevel level : server.getAllLevels())
            {
                if (level.getChunkSource().getGenerator() == generator)
                {
                    LEVELS.put(generator, level);
                    return level;
                }
            }
        }
        return null;
    }

    private static Sites sites(ResourceKey<Level> dimension)
    {
        return SITES.computeIfAbsent(dimension, key -> new Sites());
    }

    /** Whether a site's bounds, widened by {@code margin}, would touch a standing structure. */
    public static boolean overlaps(ResourceKey<Level> dimension, BoundingBox bounds, int margin)
    {
        Sites sites = sites(dimension);
        synchronized (sites)
        {
            return sites.overlaps(bounds, margin);
        }
    }

    /** Whether any structure stands within {@code distance} blocks (centre to centre) of a point. */
    public static boolean anyNear(ResourceKey<Level> dimension, int x, int z, int distance)
    {
        Sites sites = sites(dimension);
        synchronized (sites)
        {
            return sites.anyNear(x, z, distance);
        }
    }

    /** Cluster value already spent in a territory cell. */
    public static int spent(ResourceKey<Level> dimension, long cell)
    {
        Sites sites = sites(dimension);
        synchronized (sites)
        {
            return sites.spent.getOrDefault(cell, 0);
        }
    }

    /** Whether a copy of {@code template} already stands within {@code minDistance} of a point (anywhere, if {@code unique}). */
    public static boolean duplicated(ResourceKey<Level> dimension, String template, int x, int z, int minDistance, boolean unique)
    {
        Sites sites = sites(dimension);
        synchronized (sites)
        {
            return unique ? sites.templates.contains(template)
                    : minDistance > 0 && sites.nearby(template, new BoundingBox(x, 0, z, x, 0, z), minDistance);
        }
    }

    /** A structure placed by hand: world generation keeps clear of it from now on. */
    public static void remember(ResourceKey<Level> dimension, String template, BoundingBox bounds)
    {
        Sites sites = sites(dimension);
        synchronized (sites)
        {
            sites.add(new Placed(template, bounds));
        }
    }

    /**
     * Claims a site for world generation if it is still free: clear of other
     * structures by {@code margin}, no copy of the template within
     * {@code minDistance} (anywhere, if {@code unique}), and the cell's budget
     * not spent. Checked and taken in one step; the record follows on the
     * main thread.
     */
    public static boolean claim(ResourceKey<Level> dimension, String template, BoundingBox bounds, String faction, int margin, int minDistance,
            boolean unique, long cell, int clusterValue, int budget)
    {
        Sites sites = sites(dimension);
        synchronized (sites)
        {
            if (sites.overlaps(bounds, margin) || unique && sites.templates.contains(template)
                    || minDistance > 0 && sites.nearby(template, bounds, minDistance))
            {
                return false;
            }
            int spent = sites.spent.getOrDefault(cell, 0);
            if (clusterValue > 0 && spent + clusterValue > budget)
            {
                return false;
            }
            sites.spent.put(cell, spent + clusterValue);
            sites.add(new Placed(template, bounds));
        }
        PENDING.add(new Pending(dimension, template, bounds, faction));
        return true;
    }

    /** The region keys a box (widened by {@code reach}) touches. */
    private static List<Long> regions(BoundingBox box, int reach)
    {
        List<Long> regions = new ArrayList<>();
        for (int x = Math.floorDiv(box.minX() - reach, REGION); x <= Math.floorDiv(box.maxX() + reach, REGION); x++)
        {
            for (int z = Math.floorDiv(box.minZ() - reach, REGION); z <= Math.floorDiv(box.maxZ() + reach, REGION); z++)
            {
                regions.add(ChunkPos.asLong(x, z));
            }
        }
        return regions;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty())
        {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Pending pending = PENDING.poll(); pending != null; pending = PENDING.poll())
        {
            ServerLevel level = server.getLevel(pending.dimension());
            if (level != null)
            {
                StructureRecords.get(level).add(pending.template(), pending.bounds(), pending.faction(), null);
            }
        }
    }

    /** Before any chunk of the level generates: its generator, and the structures already in it. */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event)
    {
        if (!(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        LEVELS.put(level.getChunkSource().getGenerator(), level);
        Sites sites = new Sites();
        AwSiteIndex index = AwSiteIndex.get();
        long seed = level.getSeed();
        StructureRecords.get(level).forEach(record ->
        {
            sites.add(new Placed(record.template, record.bounds));
            AwSiteIndex.Site site = index.byName(record.template);
            if (site != null && site.clusterValue() > 0)
            {
                long cell = Territories.cell(seed, record.bounds.getCenter().getX() >> 4, record.bounds.getCenter().getZ() >> 4);
                sites.spent.merge(cell, site.clusterValue(), Integer::sum);
            }
        });
        SITES.put(level.dimension(), sites);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof ServerLevel level)
        {
            LEVELS.remove(level.getChunkSource().getGenerator());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        SITES.clear();
        LEVELS.clear();
        PENDING.clear();
        Territories.clear();
    }
}
