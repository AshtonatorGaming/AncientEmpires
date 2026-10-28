package com.ancientempires.worldgen;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AW's territories, made safe for parallel world generation. AW flood-filled
 * a territory from the first chunk generated in it; here the world is split
 * into cells of {@code structures.territoryCellChunks} chunks around seeded,
 * jittered centres (a Voronoi grid), and each cell holds one territory, picked
 * from those with a template for the biome at its centre, weighted as AW
 * weighed them ({@link AwSiteIndex#territoryWeight}). The same seed gives the
 * same territories, whichever chunk generates first.
 */
public final class Territories
{
    private static final Map<Long, String> CACHE = new ConcurrentHashMap<>();

    private Territories()
    {
    }

    public static void clear()
    {
        CACHE.clear();
    }

    /** The territory cell a chunk belongs to: the nearest jittered centre among the 3x3 cells around it. */
    public static long cell(long seed, int chunkX, int chunkZ)
    {
        int size = Math.max(2, AncientEmpiresConfig.territoryCellChunks);
        int cellX = Math.floorDiv(chunkX, size);
        int cellZ = Math.floorDiv(chunkZ, size);
        long best = 0L;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                int[] centre = centre(seed, cellX + dx, cellZ + dz, size);
                double distance = Math.pow(centre[0] - chunkX, 2) + Math.pow(centre[1] - chunkZ, 2);
                if (distance < bestDistance)
                {
                    bestDistance = distance;
                    best = pack(cellX + dx, cellZ + dz);
                }
            }
        }
        return best;
    }

    /** A cell's centre in blocks (x, z), for AW's pull of big structures toward the middle. */
    public static int[] centreBlock(long seed, long cell)
    {
        int[] centre = centre(seed, (int) (cell >> 32), (int) cell, Math.max(2, AncientEmpiresConfig.territoryCellChunks));
        return new int[] {centre[0] * 16 + 8, centre[1] * 16 + 8};
    }

    /** A cell's jittered centre, in chunks. */
    private static int[] centre(long seed, int cellX, int cellZ, int size)
    {
        long hash = mix(seed ^ pack(cellX, cellZ) * 0x9E3779B97F4A7C15L);
        return new int[] {cellX * size + (int) Math.floorMod(hash, size), cellZ * size + (int) Math.floorMod(hash >>> 32, size)};
    }

    /** The cell's territory (empty when no territory's templates list the biome at its centre). */
    public static String territory(long seed, long cell, BiomeSource biomes, Climate.Sampler sampler, AwSiteIndex index)
    {
        return CACHE.computeIfAbsent(cell ^ seed * 31L, key ->
        {
            int[] centre = centreBlock(seed, cell);
            Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(centre[0]), QuartPos.fromBlock(64), QuartPos.fromBlock(centre[1]), sampler);
            String id = biome.unwrapKey().map(found -> found.location().toString()).orElse("");
            List<String> names = index.territoriesIn(id).stream().sorted().toList();
            int total = 0;
            for (String name : names)
            {
                total += index.territoryWeight(name);
            }
            if (total <= 0)
            {
                return "";
            }
            int pick = (int) Math.floorMod(mix(seed + cell * 0xD1B54A32D192ED03L), total);
            for (String name : names)
            {
                pick -= index.territoryWeight(name);
                if (pick < 0)
                {
                    return name;
                }
            }
            return "";
        });
    }

    static long pack(int x, int z)
    {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    private static long mix(long value)
    {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
