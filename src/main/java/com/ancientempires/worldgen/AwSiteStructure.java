package com.ancientempires.worldgen;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModWorldGen;
import com.ancientempires.structure.AePlacer;
import com.ancientempires.structure.AeTemplate;
import com.ancientempires.structure.AeTemplates;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AW's world generation as one vanilla structure, so {@code /locate}, the
 * structure checks, and terrain blending all work. Each chunk is a candidate
 * (the structure set spaces them one apart); like AW it then rolls
 * {@code structures.generationChance} times the territory's multiplier,
 * finds the chunk's territory ({@link Territories}), and picks a template by
 * AW's rules ({@code WorldGenStructureManager}): the territory's own and the
 * generic ones that list the biome and dimension, fit the territory's
 * remaining cluster budget, are not unique and already placed or within
 * their minimum duplicate distance, and suit the ground; weighted by
 * selection weight, big ones (cluster value over 50) less so away from the
 * territory's centre. The ground is then checked around the template's
 * border as AW's validators did ({@code StructureValidatorGround},
 * {@code Water}, {@code Island}), with {@code structures.maxSlope} as the
 * least leveling and fill allowed, since vanilla's terrain blending (land
 * sites, {@code beard_thin}) smooths what AW's border smoothing did.
 * <p>
 * {@code surface} is {@code land} (AW's ground type) or {@code sea} (water
 * and island types; no terrain blending). Sky, underground, harbor, and lava
 * types are not placed yet.
 */
public final class AwSiteStructure extends Structure
{
    public static final Codec<AwSiteStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(settingsCodec(instance),
            Codec.STRING.fieldOf("surface").forGetter(structure -> structure.surface)).apply(instance, AwSiteStructure::new));

    private static final String LAND = "land";
    /** AW's line between small structures and big ones that keep to their territory's middle. */
    private static final int BIG_CLUSTER = 50;
    /** The border ring AW checked is capped here, in blocks. */
    private static final int MAX_BORDER = 16;

    /** The height of the land (water counts) and of the ground under any water, each the first free block. */
    private record Ground(int surface, int floor)
    {
        boolean wet()
        {
            return this.floor < this.surface;
        }
    }

    private final String surface;

    public AwSiteStructure(StructureSettings settings, String surface)
    {
        super(settings);
        this.surface = surface;
    }

    @Override
    public StructureType<?> type()
    {
        return ModWorldGen.AW_SITE.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context)
    {
        if (!AncientEmpiresConfig.structuresWorldGen)
        {
            return Optional.empty();
        }
        ServerLevel level = AwSites.level(context.chunkGenerator());
        if (level == null)
        {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        long seed = context.seed();
        AwSiteIndex index = AwSiteIndex.get();
        long cell = Territories.cell(seed, chunk.x, chunk.z);
        String territory = Territories.territory(seed, cell, context.biomeSource(), context.randomState().sampler(), index);
        if (territory.isEmpty())
        {
            return Optional.empty();
        }
        TerritorySettings.Settings settings = TerritorySettings.of(territory);
        WorldgenRandom random = context.random();
        if (random.nextDouble() >= AncientEmpiresConfig.structuresChance * settings.chanceMultiplier())
        {
            return Optional.empty();
        }
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        if (level.dimension() == Level.OVERWORLD && nearSpawn(level, x, z))
        {
            return Optional.empty();
        }
        boolean land = this.surface.equals(LAND);
        Ground centre = ground(context, x, z);
        if (land == centre.wet())
        {
            return Optional.empty();
        }
        String biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(centre.surface()), QuartPos.fromBlock(z),
                context.randomState().sampler()).unwrapKey().map(key -> key.location().toString()).orElse("");
        ResourceKey<Level> dimension = level.dimension();
        String dimensionId = dimension.location().toString();
        int budget = budget(settings);
        int left = budget - AwSites.spent(dimension, cell);
        int[] territoryCentre = Territories.centreBlock(seed, cell);
        List<AwSiteIndex.Site> candidates = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (AwSiteIndex.Site site : index.sites())
        {
            if (land != site.kind().equals("ground") || !land && !site.kind().equals("water") && !site.kind().equals("island"))
            {
                continue;
            }
            if (!site.territory().equals(territory) && !site.territory().equals(AwSiteIndex.GENERIC) || site.clusterValue() > left
                    || !site.allowsDimension(dimensionId) || !site.allowsBiome(biome) || !selectable(site, centre))
            {
                continue;
            }
            if (AwSites.duplicated(dimension, site.name(), x, z, site.minDistance(), site.unique()))
            {
                continue;
            }
            int weight = weight(site, settings, x, z, territoryCentre);
            if (weight > 0)
            {
                candidates.add(site);
                weights.add(weight);
                total += weight;
            }
        }
        if (total <= 0)
        {
            return Optional.empty();
        }
        int pick = random.nextInt(total);
        AwSiteIndex.Site site = candidates.get(candidates.size() - 1);
        for (int i = 0; i < candidates.size(); i++)
        {
            pick -= weights.get(i);
            if (pick < 0)
            {
                site = candidates.get(i);
                break;
            }
        }
        Rotation rotation = Rotation.getRandom(random);
        int y = land ? centre.surface() : centre.surface() - 1;
        BlockPos middle = AePlacer.bounds(site.size(), site.offset(), BlockPos.ZERO, rotation).getCenter();
        BlockPos anchor = new BlockPos(x - middle.getX(), y, z - middle.getZ());
        BoundingBox bounds = AePlacer.bounds(site.size(), site.offset(), anchor, rotation);
        if (bounds.minY() <= context.heightAccessor().getMinBuildHeight() || bounds.maxY() >= context.heightAccessor().getMaxBuildHeight()
                || !borderFits(context, site, bounds, y) || AwSites.overlaps(dimension, bounds, AncientEmpiresConfig.structuresSiteMargin))
        {
            return Optional.empty();
        }
        AwSiteIndex.Site chosen = site;
        return Optional.of(new GenerationStub(new BlockPos(x, y, z), builder ->
        {
            // Run only when the structure is really generated (not for /locate), so this is where the site is taken.
            Optional<AeTemplate> template = AeTemplates.get(chosen.id());
            if (template.isEmpty())
            {
                AncientEmpires.LOGGER.debug("World generation chose {} at {} but its template did not load", chosen.name(), bounds);
                return;
            }
            String faction = AePlacer.faction(template.get());
            if (!AwSites.claim(dimension, chosen.name(), bounds, faction, AncientEmpiresConfig.structuresSiteMargin, chosen.minDistance(),
                    chosen.unique(), cell, chosen.clusterValue(), budget))
            {
                AncientEmpires.LOGGER.debug("World generation chose {} at {} but it was taken meanwhile", chosen.name(), bounds);
            }
            else
            {
                AncientEmpires.LOGGER.debug("World generation places {} ({}) at {} in territory {}", chosen.name(), faction, bounds, territory);
                // On land the box starts at ground level: below it the terrain is filled up to the floor, inside it cleared.
                BoundingBox box = land ? new BoundingBox(bounds.minX(), y, bounds.minZ(), bounds.maxX(), Math.max(y, bounds.maxY()), bounds.maxZ())
                        : bounds;
                builder.addPiece(new AwSitePiece(box, chosen.id(), chosen.name(), chosen.offset(), anchor, rotation, faction));
            }
        }));
    }

    private static Ground ground(GenerationContext context, int x, int z)
    {
        ChunkGenerator generator = context.chunkGenerator();
        return new Ground(generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()),
                generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState()));
    }

    private static boolean nearSpawn(ServerLevel level, int x, int z)
    {
        BlockPos spawn = level.getSharedSpawnPos();
        long dx = x - spawn.getX();
        long dz = z - spawn.getZ();
        long radius = AncientEmpiresConfig.structuresSpawnProtection;
        return dx * dx + dz * dz < radius * radius;
    }

    /** A territory cell's cluster budget: AW's per-chunk value over the chunks it holds, at most its radius. */
    private static int budget(TerritorySettings.Settings settings)
    {
        int size = Math.max(2, AncientEmpiresConfig.territoryCellChunks);
        double chunks = Math.min(size * size, Math.PI * settings.maxRadiusChunks() * settings.maxRadiusChunks());
        return (int) (AncientEmpiresConfig.structuresChunkClusterValue * settings.clusterMultiplier() * chunks);
    }

    /** AW's selection weight; a big structure's falls away from the territory's centre. */
    private static int weight(AwSiteIndex.Site site, TerritorySettings.Settings settings, int x, int z, int[] centre)
    {
        if (site.clusterValue() <= BIG_CLUSTER)
        {
            return site.weight();
        }
        double dx = x - centre[0];
        double dz = z - centre[1];
        double reach = settings.maxRadiusChunks() * 16.0D;
        return Math.max(0, (int) (site.weight() - (dx * dx + dz * dz) / (reach * reach) * site.weight()));
    }

    /** AW's check of the spot itself ({@code shouldIncludeForSelection}). */
    private static boolean selectable(AwSiteIndex.Site site, Ground centre)
    {
        return switch (site.kind())
        {
            case "ground" -> centre.surface() >= site.minHeight() && centre.surface() <= site.maxHeight();
            case "island" -> centre.surface() - centre.floor() >= site.minWater() && centre.surface() - centre.floor() <= site.maxWater();
            default -> true;
        };
    }

    /**
     * AW's border check: around the template, out to its border (at most
     * {@link #MAX_BORDER}), the land must lie within its leveling above and
     * fill below the build key (at least {@code structures.maxSlope} each way)
     * and be dry; a water structure needs water deeper than its fill all round;
     * an island needs sea floor within its water depths.
     */
    private static boolean borderFits(GenerationContext context, AwSiteIndex.Site site, BoundingBox bounds, int y)
    {
        int border = Math.min(site.border(), MAX_BORDER);
        int minX = bounds.minX() - border;
        int maxX = bounds.maxX() + border;
        int minZ = bounds.minZ() - border;
        int maxZ = bounds.maxZ() + border;
        int midX = (minX + maxX) / 2;
        int midZ = (minZ + maxZ) / 2;
        int[][] points = {{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}, {midX, minZ}, {midX, maxZ}, {minX, midZ}, {maxX, midZ}};
        int slope = AncientEmpiresConfig.structuresMaxSlope;
        for (int[] point : points)
        {
            Ground ground = ground(context, point[0], point[1]);
            boolean fits = switch (site.kind())
            {
                case "ground" -> !ground.wet() && ground.surface() >= y - Math.max(site.fill(), slope) - 1
                        && ground.surface() <= y + Math.max(site.leveling(), slope);
                case "water" -> ground.wet() && ground.floor() <= y - site.fill();
                default -> ground.wet() && ground.floor() - 1 >= y - site.maxWater() && ground.floor() - 1 <= y - site.minWater();
            };
            if (!fits)
            {
                return false;
            }
        }
        return true;
    }
}
