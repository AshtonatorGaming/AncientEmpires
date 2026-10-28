package com.ancientempires.worldgen;

import com.ancientempires.AncientEmpires;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs world generation's own checks over the chunks around a point without
 * building anything, and counts why sites were turned down and which were
 * chosen ({@code /ancientempires structure survey}): for finding out why
 * structures are rare. Only reads; nothing is claimed.
 */
public final class AwSurvey
{
    private AwSurvey()
    {
    }

    public static Map<String, Integer> around(ServerLevel level, ChunkPos centre, int radius)
    {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Map<String, Integer> reasons = new TreeMap<>();
        for (String name : List.of("aw_site", "aw_sea_site"))
        {
            Structure structure = structures.get(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, name));
            if (structure == null)
            {
                reasons.merge("missing structure " + name, 1, Integer::sum);
                continue;
            }
            Map<String, Integer> counts = new TreeMap<>();
            for (int dx = -radius; dx <= radius; dx++)
            {
                for (int dz = -radius; dz <= radius; dz++)
                {
                    ChunkPos chunk = new ChunkPos(centre.x + dx, centre.z + dz);
                    Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                            level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), chunk, level, biome -> true);
                    AwSiteStructure.survey(counts, () -> structure.findValidGenerationPoint(context));
                }
            }
            counts.forEach((reason, count) -> reasons.merge(name + ": " + reason, count, Integer::sum));
        }
        return reasons;
    }
}
