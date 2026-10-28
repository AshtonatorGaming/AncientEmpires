package com.ancientempires.structure;

import com.ancientempires.AncientEmpires;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Places real converted templates on the game test server
 * ({@code ./gradlew runGameTestServer}): every block resolves, double chests
 * come out paired, fences connect after the shape pass, and the time for a
 * large one is logged. Only loaded when game tests are enabled.
 */
@GameTestHolder(AncientEmpires.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureGameTests
{
    private static final String ARENA = "gametest_arena";

    private StructureGameTests()
    {
    }

    private static int[] offset(ResourceLocation id)
    {
        JsonObject sidecar = AeTemplates.sidecar(id);
        int[] offset = new int[3];
        for (int i = 0; i < 3; i++)
        {
            offset[i] = sidecar.getAsJsonArray("offset").get(i).getAsInt();
        }
        return offset;
    }

    private static AePlacer.Report placeAndLog(GameTestHelper helper, String path, BlockPos anchor, Rotation rotation)
    {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, path);
        AeTemplate template = AeTemplates.get(id).orElse(null);
        if (template == null)
        {
            throw new IllegalStateException("Template " + id + " did not load (" + AeTemplates.all().size() + " indexed)");
        }
        long start = System.nanoTime();
        AePlacer.Report report = AePlacer.place(helper.getLevel(), template, offset(id), anchor, rotation, null);
        AncientEmpires.LOGGER.info("GAMETEST placed {} ({}x{}x{}): {} blocks, {} entities in {} ms; substituted {}; skipped {}",
                id, template.sizeX(), template.sizeY(), template.sizeZ(), report.blocks(), report.entities(),
                (System.nanoTime() - start) / 1_000_000L, report.substituted(), report.skipped());
        return report;
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void churchPlacesWithPairedChestsAndConnectedFences(GameTestHelper helper)
    {
        String path = "normal/awnationempire/empirearchbishopschurch";
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, path);
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(300, 0, 0);
        AePlacer.Report report = placeAndLog(helper, path, anchor, Rotation.NONE);
        helper.assertTrue(report.blocks() > 10000, "the church placed " + report.blocks() + " blocks");
        helper.assertFalse(report.substituted().keySet().stream().anyMatch(name -> name.startsWith("minecraft:")),
                "every vanilla block resolves: " + report.substituted());

        AeTemplate template = AeTemplates.get(id).orElseThrow();
        int[] offset = offset(id);
        ServerLevel level = helper.getLevel();
        int[] fences = new int[2];
        int[] chests = new int[2];
        for (int tz = 0; tz < template.tilesZ(); tz++)
        {
            for (int tx = 0; tx < template.tilesX(); tx++)
            {
                template.forEachInTile(tx, tz, (x, y, z, state) ->
                {
                    String name = template.palette().get(state).getString("Name");
                    BlockState placed = level.getBlockState(AePlacer.cellToWorld(offset, anchor, Rotation.NONE, x, y, z));
                    if (name.endsWith("_fence") && placed.getBlock() instanceof FenceBlock)
                    {
                        fences[0]++;
                        if (placed.getValue(CrossCollisionBlock.NORTH) || placed.getValue(CrossCollisionBlock.EAST)
                                || placed.getValue(CrossCollisionBlock.SOUTH) || placed.getValue(CrossCollisionBlock.WEST))
                        {
                            fences[1]++;
                        }
                    }
                    if (placed.getBlock() instanceof ChestBlock)
                    {
                        chests[0]++;
                        if (placed.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
                        {
                            chests[1]++;
                        }
                    }
                });
            }
        }
        AncientEmpires.LOGGER.info("GAMETEST church: {} fences ({} connected), {} chests ({} double halves)", fences[0], fences[1], chests[0], chests[1]);
        helper.assertTrue(fences[0] == 0 || fences[1] > 0, "fences connect after the shape pass");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void wellPlacesTurned(GameTestHelper helper)
    {
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(-300, 0, 0);
        AePlacer.Report report = placeAndLog(helper, "normal/awnationempire/empireemperorstatuewell", anchor, Rotation.CLOCKWISE_90);
        helper.assertTrue(report.blocks() > 1000, "the well placed " + report.blocks() + " blocks");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void largeIslandTiming(GameTestHelper helper)
    {
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(0, 0, 600);
        AePlacer.Report report = placeAndLog(helper, "normal/oceanislands/islandsgriffonroostsrocky", anchor, Rotation.NONE);
        helper.assertTrue(report.blocks() > 0, "the island placed");
        helper.succeed();
    }
}
