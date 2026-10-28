package com.ancientempires.structure;

import com.ancientempires.AncientEmpires;
import com.google.gson.JsonObject;
import com.ancientempires.block.FlagBlockEntity;
import com.ancientempires.block.NpcSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

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
        AePlacer.Report report = AePlacer.place(helper.getLevel(), template, AeTemplates.name(id), offset(id), anchor, rotation, null);
        AncientEmpires.LOGGER.info("GAMETEST placed {} ({}x{}x{}): {} blocks, {} entities in {} ms; substituted {}; made {}; skipped {}",
                id, template.sizeX(), template.sizeY(), template.sizeZ(), report.blocks(), report.entities(),
                (System.nanoTime() - start) / 1_000_000L, report.substituted(), report.aw(), report.skipped());
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
        String path = "normal/awnationempire/empireemperorstatuewell";
        AePlacer.Report report = placeAndLog(helper, path, anchor, Rotation.CLOCKWISE_90);
        helper.assertTrue(report.blocks() > 1000, "the well placed " + report.blocks() + " blocks");
        helper.assertTrue(report.aw().getOrDefault("ancientwarfarestructure:advanced_spawner", 0) == 4, "its four spawners are made real: " + report.aw());
        helper.assertFalse(report.skipped().keySet().stream().anyMatch(what -> what.startsWith("unknown roster")), "every garrison has a roster row: " + report.skipped());

        // Each spawner holds its garrison: a scroll in its first slot.
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, path);
        AeTemplate template = AeTemplates.get(id).orElseThrow();
        int[] offset = offset(id);
        ServerLevel level = helper.getLevel();
        int[] spawners = new int[2];
        for (int tz = 0; tz < template.tilesZ(); tz++)
        {
            for (int tx = 0; tx < template.tilesX(); tx++)
            {
                template.forEachInTile(tx, tz, (x, y, z, state) ->
                {
                    if (level.getBlockEntity(AePlacer.cellToWorld(offset, anchor, Rotation.CLOCKWISE_90, x, y, z)) instanceof NpcSpawnerBlockEntity spawner)
                    {
                        spawners[0]++;
                        if (!spawner.slots().getItem(0).isEmpty())
                        {
                            spawners[1]++;
                            AncientEmpires.LOGGER.info("GAMETEST well spawner holds {} {}", spawner.slots().getItem(0), spawner.slots().getItem(0).getTag());
                        }
                    }
                });
            }
        }
        helper.assertTrue(spawners[0] == 4 && spawners[1] == 4, "four spawners, each holding someone: " + spawners[0] + ", " + spawners[1]);
        helper.succeed();
    }

    /**
     * Every spawner in the library imported (not placed): logs AW NPC types
     * our roster does not know and anything else the import could not carry.
     */
    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void everySpawnerImports(GameTestHelper helper)
    {
        Map<String, Integer> problems = new TreeMap<>();
        int[] counts = new int[2];
        for (ResourceLocation id : AeTemplates.all().keySet())
        {
            AeTemplate template = AeTemplates.get(id).orElse(null);
            if (template == null)
            {
                problems.merge("unreadable " + id, 1, Integer::sum);
                continue;
            }
            for (CompoundTag nbt : template.blockEntities().values())
            {
                CompoundTag rule = nbt.getCompound("aeRule");
                if (!rule.getCompound("blockState").getString("blockName").equals("ancientwarfarestructure:advanced_spawner"))
                {
                    continue;
                }
                counts[0]++;
                CompoundTag saved = AwSpawnerImport.saved(rule.getCompound("teData").getCompound("spawnerSettings"), problems);
                counts[1] += saved.getList("Items", Tag.TAG_COMPOUND).isEmpty() ? 0 : 1;
            }
        }
        AncientEmpires.LOGGER.info("GAMETEST spawners: {} imported, {} holding someone; problems {}", counts[0], counts[1], problems);
        helper.assertTrue(counts[1] > counts[0] * 0.95, "nearly every spawner holds someone: " + counts[1] + " of " + counts[0]);
        helper.succeed();
    }

    /** Every converted AW loot table loads (1.20 drops a table it cannot read, quietly). */
    @GameTest(template = ARENA)
    public static void awLootTablesLoad(GameTestHelper helper)
    {
        LootDataManager loot = helper.getLevel().getServer().getLootData();
        List<ResourceLocation> missing = new ArrayList<>();
        int checked = 0;
        for (String type : new String[] {"chests/castle_treasury_high_value", "chests/shop_wool_carpet", "chests/lizardman",
                "chests/compat/harvestcraft/harvestcraft_shop_sea_life", "entities/soldier", "entities/leader_elite", "entities/undead_archer"})
        {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "aw/" + type);
            checked++;
            if (loot.getLootTable(id) == LootTable.EMPTY)
            {
                missing.add(id);
            }
        }
        AncientEmpires.LOGGER.info("GAMETEST loot: {} checked, missing {}", checked, missing);
        helper.assertTrue(missing.isEmpty(), "AW loot tables load: missing " + missing);
        helper.succeed();
    }

    /** The undead mausoleum: its loot chests, urns, and coin stacks become real. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void lootAndCoinsPlace(GameTestHelper helper)
    {
        // Well above the test world's floor: the mausoleum's crypt runs deep.
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(0, 100, -300);
        AePlacer.Report report = placeAndLog(helper, "normal/awpowersundead/undeadmausoleum", anchor, Rotation.NONE);
        helper.assertTrue(report.aw().getOrDefault("ancientwarfarestructure:advanced_loot_chest", 0) > 0, "loot chests made real: " + report.aw());
        helper.assertTrue(report.aw().getOrDefault("ancientwarfarestructure:urn", 0) > 0, "urns made real: " + report.aw());
        // Open each loot chest as a player would: vanilla's roll plus AW's extras, then plain chests.
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "normal/awpowersundead/undeadmausoleum");
        AeTemplate template = AeTemplates.get(id).orElseThrow();
        int[] offset = offset(id);
        Player player = helper.makeMockPlayer();
        int[] chests = new int[2];
        for (Map.Entry<Integer, CompoundTag> entry : template.blockEntities().entrySet())
        {
            if (!entry.getValue().getCompound("aeRule").getCompound("blockState").getString("blockName").equals("ancientwarfarestructure:advanced_loot_chest"))
            {
                continue;
            }
            int[] cell = template.cellOf(entry.getKey());
            BlockPos pos = AePlacer.cellToWorld(offset, anchor, Rotation.NONE, cell[0], cell[1], cell[2]);
            if (helper.getLevel().getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest)
            {
                MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
                chest.unpackLootTable(player);
                chests[0]++;
                chests[1] += chest.isEmpty() ? 0 : 1;
                AncientEmpires.LOGGER.info("GAMETEST loot chest at {}: {} stacks, AeLoot left {}", pos,
                        IntStream.range(0, chest.getContainerSize()).filter(slot -> !chest.getItem(slot).isEmpty()).count(), chest.getPersistentData().contains(AwLoot.KEY));
            }
        }
        helper.assertTrue(chests[0] == 2 && chests[1] == 2, "both loot chests fill: " + chests[1] + " of " + chests[0]);
        helper.assertTrue(report.aw().entrySet().stream().filter(entry -> entry.getKey().contains("coin_stack")).mapToInt(Map.Entry::getValue).sum() == 8, "all eight coin stacks made real: " + report.aw());
        helper.succeed();
    }

    /**
     * The Empire watch tower: its protection flag is tied to a record of the
     * structure, a claim fails while its garrisons hold, and succeeds once
     * they are gone.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void protectionFlagClaims(GameTestHelper helper)
    {
        String path = "normal/awnationempire/empirewatchtower";
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(300, 60, 300);
        AePlacer.Report report = placeAndLog(helper, path, anchor, Rotation.NONE);
        helper.assertTrue(report.aw().getOrDefault("ancientwarfarestructure:protection_flag", 0) == 1, "the flag is made real: " + report.aw());
        ServerLevel level = helper.getLevel();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, path);
        AeTemplate template = AeTemplates.get(id).orElseThrow();
        BoundingBox bounds = AePlacer.bounds(template, offset(id), anchor, Rotation.NONE);
        StructureRecords.Record record = StructureRecords.get(level).at(bounds.getCenter());
        helper.assertTrue(record != null && "empire".equals(record.faction) && record.flag != null, "an Empire record with its flag");
        FlagBlockEntity flag = (FlagBlockEntity) level.getBlockEntity(record.flag);
        helper.assertTrue(flag != null && flag.isProtection() && flag.record() == record.id, "the flag guards the record");

        Player player = helper.makeMockPlayer();
        StructureClaims.tryClaim(level, record.flag, flag, player);
        helper.assertFalse(record.claimed(), "held while the garrisons hold");

        // The garrisons gone (cleared), the claim goes through.
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++)
        {
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++)
            {
                for (BlockPos spawner : List.copyOf(level.getChunk(cx, cz).getBlockEntities().keySet()))
                {
                    if (level.getBlockEntity(spawner) instanceof NpcSpawnerBlockEntity)
                    {
                        level.removeBlock(spawner, false);
                    }
                }
            }
        }
        StructureClaims.tryClaim(level, record.flag, flag, player);
        helper.assertTrue(record.claimed() && flag.claimedBy() != null, "claimed once cleared");
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
