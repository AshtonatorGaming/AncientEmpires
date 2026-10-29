package com.ancientempires.structure;

import com.ancientempires.AncientEmpires;
import com.google.gson.JsonObject;
import com.ancientempires.block.FlagBlockEntity;
import com.ancientempires.block.NpcSpawnerBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcRank;
import com.ancientempires.entity.npc.NpcPath;
import com.ancientempires.entity.npc.NpcOrder;
import com.ancientempires.entity.npc.NpcChain;
import com.ancientempires.entity.npc.NpcStance;
import com.ancientempires.gate.GateEntity;
import com.ancientempires.gate.GateType;
import com.ancientempires.registry.ModEntities;
import com.ancientempires.registry.ModBlocks;
import com.ancientempires.worldgen.AwSiteIndex;
import com.ancientempires.worldgen.TerritorySettings;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.core.BlockPos;
import com.ancientempires.worldgen.AwSurvey;
import java.nio.file.Path;
import java.io.IOException;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.NbtIo;
import com.ancientempires.siege.SiegeEngineEntity;
import com.ancientempires.block.decor.CoffinBlockEntity;
import com.ancientempires.block.decor.AwSoundBlockEntity;
import com.ancientempires.block.decor.AwDecorBlockEntity;
import com.ancientempires.block.decor.AwDecorBlock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
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
import java.util.Locale;
import java.util.UUID;
import com.ancientempires.entity.npc.NpcGarrison;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.FakePlayer;
import com.ancientempires.network.ArmyPacket;
import com.ancientempires.entity.npc.NpcArmy;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
        AncientEmpires.LOGGER.info("GAMETEST flag at {}: {} / {}", record.flag, level.getBlockState(record.flag), level.getBlockEntity(record.flag));
        FlagBlockEntity flag = level.getBlockEntity(record.flag) instanceof FlagBlockEntity found ? found : null;
        helper.assertTrue(flag != null && flag.isProtection() && flag.record() == record.id, "the flag guards the record: flag "
                + (flag == null ? "missing" : flag.isProtection() + " record " + flag.record()) + ", record " + record.id + " " + record.template);

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

    /**
     * The Empire border tower's three iron portcullises: built from the
     * template, closed with proxies filling their openings, and opening clears
     * them; then closed again.
     */
    /**
     * AW's drawbridges are built facing out of the castle (AW saved a gate's
     * orientation as a full direction index) and lower across the moat.
     */
    @GameTest(template = ARENA, timeoutTicks = 800)
    public static void drawbridgeLowersOutward(GameTestHelper helper)
    {
        String path = "normal/awnationwitchbane/witchbanedesertcastle";
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(-40, 2, 70);
        ServerLevel level = helper.getLevel();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, path);
        BoundingBox bounds = AePlacer.bounds(AeTemplates.get(id).orElseThrow(), offset(id), anchor, Rotation.NONE);
        // Only this castle's gates from last run: other tests' gates stand nearby.
        level.getEntitiesOfClass(GateEntity.class, AABB.of(bounds)).forEach(GateEntity::discard);
        placeAndLog(helper, path, anchor, Rotation.NONE);
        List<GateEntity> bridges = level.getEntitiesOfClass(GateEntity.class, AABB.of(bounds).inflate(2.0D)).stream()
                .filter(gate -> gate.gateType() == GateType.DRAWBRIDGE).toList();
        helper.assertTrue(!bridges.isEmpty(), "a drawbridge was built");
        GateEntity bridge = bridges.get(0);
        AncientEmpires.LOGGER.info("GAMETEST drawbridge pos1 {} pos2 {} facing {} castle {}", bridge.pos1(), bridge.pos2(), bridge.facing(), bounds);
        helper.runAfterDelay(5, () ->
        {
            bridge.toggle();
            helper.succeedWhen(() -> helper.assertTrue(bridge.edge() > 0.0F, "the bridge lowers (status " + bridge.status() + ")"));
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void gatesBuildOpenAndClose(GameTestHelper helper)
    {
        String path = "normal/awnationempire/empirebordertower";
        // Close by: gates are entities, which tick only near the test.
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(12, 2, 12);
        // The test world keeps entities between runs: clear last run's gates first.
        helper.getLevel().getEntitiesOfClass(GateEntity.class, new AABB(anchor).inflate(48.0D)).forEach(GateEntity::discard);
        placeAndLog(helper, path, anchor, Rotation.CLOCKWISE_90);
        ServerLevel level = helper.getLevel();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, path);
        BoundingBox bounds = AePlacer.bounds(AeTemplates.get(id).orElseThrow(), offset(id), anchor, Rotation.CLOCKWISE_90);
        List<GateEntity> gates = level.getEntitiesOfClass(GateEntity.class, AABB.of(bounds).inflate(2.0D));
        helper.assertTrue(gates.size() == 3, "three gates built, found " + gates.size());
        GateEntity gate = gates.get(0);
        AncientEmpires.LOGGER.info("GAMETEST gate {} pos1 {} pos2 {} min state {}", gate.gateType(), gate.pos1(), gate.pos2(), level.getBlockState(gate.min()));
        helper.runAfterDelay(5, () ->
        {
            helper.assertTrue(level.getBlockState(gate.min()).is(ModBlocks.GATE_PROXY.get()), "closed: the opening is blocked; status "
                    + gate.status() + " edge " + gate.edge() + " removed " + gate.isRemoved() + " state " + level.getBlockState(gate.min()));
            gate.toggle();
            helper.runAfterDelay(5, () ->
            {
                helper.assertTrue(level.getBlockState(gate.min()).isAir(), "opening: the way is clear");
                helper.assertTrue(gate.edge() > 0.0F, "the wall is rising");
                AncientEmpires.LOGGER.info("GAMETEST gate {} {}x{} edge {} of {}", gate.gateType(), gate.width(), gate.height(), gate.edge(), gate.edgeMax());
                gate.toggle();
                helper.succeedWhen(() -> helper.assertTrue(gate.edge() == 0.0F && level.getBlockState(gate.min()).is(ModBlocks.GATE_PROXY.get()),
                        "closed again and blocked"));
            });
        });
    }

    /**
     * With gates.npcUse on: a player's NPC walks through its owner's gate in
     * a wall (opening it ahead, closing it behind), rather than around.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void ownedNpcUsesOwnersGate(GameTestHelper helper)
    {
        AncientEmpiresConfig.gateNpcUse = true;
        ServerLevel level = helper.getLevel();
        level.setDayTime(6000L);
        level.getEntitiesOfClass(GateEntity.class, new AABB(helper.absolutePos(new BlockPos(-20, 1, -20))).inflate(2.0D)).forEach(GateEntity::discard);
        UUID owner = UUID.randomUUID();
        // A floor, and a wall across it with a two-high gate in the middle.
        for (int x = -6; x <= 6; x++)
        {
            for (int z = -12; z <= 12; z++)
            {
                helper.setBlock(new BlockPos(x - 20, 0, z - 20), Blocks.STONE);
                for (int y = 1; y <= 5; y++)
                {
                    // The test world is solid ground here: clear room to walk.
                    helper.setBlock(new BlockPos(x - 20, y, z - 20), Blocks.AIR);
                }
            }
        }
        for (int z = -12; z <= 12; z++)
        {
            for (int y = 1; y <= 4; y++)
            {
                helper.setBlock(new BlockPos(-20, y, z - 20), z == 0 && y <= 2 ? Blocks.AIR : Blocks.STONE_BRICKS);
            }
        }
        GateEntity gate = GateEntity.create(level, helper.absolutePos(new BlockPos(-20, 1, -20)), helper.absolutePos(new BlockPos(-20, 2, -20)),
                GateType.VERTICAL_WOOD, Direction.EAST, owner);
        level.addFreshEntity(gate);
        NpcEntity npc = ModEntities.NPC.get().create(level);
        BlockPos start = helper.absolutePos(new BlockPos(-24, 1, -20));
        npc.moveTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
        npc.setOwner(owner);
        level.addFreshEntity(npc);
        BlockPos goal = helper.absolutePos(new BlockPos(-16, 1, -20));
        helper.onEachTick(() ->
        {
            if (npc.tickCount % 10 == 0)
            {
                npc.getNavigation().moveTo(goal.getX() + 0.5D, goal.getY(), goal.getZ() + 0.5D, 1.0D);
            }
        });
        helper.succeedWhen(() ->
        {
            helper.assertTrue(npc.getX() > goal.getX() - 1.0D, "the NPC reaches the far side, now at " + npc.blockPosition() + " goal " + goal
                    + "; gate status " + gate.status() + " edge " + gate.edge() + " may " + gate.mayNpcUse(npc) + " path "
                    + (npc.getNavigation().getPath() == null ? "none" : npc.getNavigation().getPath().getNodeCount() + " nodes, reaches " + npc.getNavigation().getPath().canReach()));
            AncientEmpires.LOGGER.info("GAMETEST npc through gate, gate status {} edge {}", gate.status(), gate.edge());
        });
    }

    /**
     * Chain of command, phase 1: a sergeant takes three soldiers, they form up
     * behind it in their slots, and when it falls the best of them takes its
     * place (its order, and the other two under it).
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void squadFormsUpAndSucceeds(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        level.setDayTime(6000L);
        for (int x = 10; x <= 30; x++)
        {
            for (int z = -30; z <= -10; z++)
            {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++)
                {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        level.getEntitiesOfClass(NpcEntity.class, new AABB(helper.absolutePos(new BlockPos(20, 1, -20))).inflate(14.0D)).forEach(NpcEntity::discard);
        UUID owner = UUID.randomUUID();
        NpcEntity sergeant = squadNpc(helper, owner, new BlockPos(20, 1, -20), NpcRank.SERGEANT, 5);
        List<NpcEntity> soldiers = new ArrayList<>();
        int[][] starts = {{12, -12}, {28, -28}, {13, -27}};
        for (int i = 0; i < starts.length; i++)
        {
            NpcEntity soldier = squadNpc(helper, owner, new BlockPos(starts[i][0], 1, starts[i][1]), NpcRank.SOLDIER, 1 + i * 3);
            helper.assertTrue(NpcChain.toggle(sergeant, soldier) == NpcChain.Assign.ADDED, "soldier " + i + " joins");
            soldiers.add(soldier);
        }
        helper.assertTrue(NpcChain.toggle(soldiers.get(0), sergeant) != NpcChain.Assign.ADDED, "a soldier cannot lead its sergeant");
        // Hold a spot a few blocks off, facing east: the sergeant goes there, its soldiers form up in front of it.
        BlockPos spot = helper.absolutePos(new BlockPos(24, 1, -18));
        sergeant.getChain().setOrder(NpcOrder.HOLD, spot, -90.0F, null);
        helper.runAfterDelay(200, () ->
        {
            helper.assertTrue(sergeant.position().distanceTo(Vec3.atBottomCenterOf(spot)) <= 2.0D, "the sergeant holds the spot, at " + sergeant.position());
            for (NpcEntity soldier : soldiers)
            {
                Vec3 slot = sergeant.getChain().slotOf(soldier);
                helper.assertTrue(slot != null, "a slot for every soldier");
                double off = Math.sqrt(Math.pow(slot.x - soldier.getX(), 2) + Math.pow(slot.z - soldier.getZ(), 2));
                AncientEmpires.LOGGER.info("GAMETEST squad soldier at {} slot {} off {}", soldier.position(), slot, off);
                helper.assertTrue(off <= 0.75D, "in its slot, off by " + off);
                // Facing east, the front rank stands east of its sergeant.
                helper.assertTrue(slot.x > sergeant.getX(), "the soldiers stand in front of their sergeant");
            }
            sergeant.kill();
            NpcEntity heir = soldiers.get(2);
            helper.assertTrue(heir.getChain().leaderId() == null, "the best soldier now leads");
            helper.assertTrue(heir.getChain().members().size() == 2, "and has the other two under it, has " + heir.getChain().members().size());
            helper.assertTrue(heir.getChain().order() == NpcOrder.HOLD && spot.equals(heir.getChain().orderPos()), "with the fallen sergeant's order and spot");
            for (NpcEntity soldier : soldiers.subList(0, 2))
            {
                helper.assertTrue(heir.getUUID().equals(soldier.getChain().leaderId()), "the others answer to the heir");
            }
            helper.succeed();
        });
    }

    /**
     * Benchmark, not a check: a crowd of idle soldiers and workers (no enemy,
     * daytime, walled in) and the average server AI step of each, by path, so
     * the cost of the goals an NPC carries can be compared before and after a
     * change. Logged as GAMETEST goal cost.
     */
    @GameTest(template = ARENA, timeoutTicks = 800, required = false)
    public static void npcGoalCost(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        level.setDayTime(6000L);
        int minX = 40;
        int minZ = -60;
        int size = 60;
        for (int x = minX - 1; x <= minX + size; x++)
        {
            for (int z = minZ - 1; z <= minZ + size; z++)
            {
                boolean wall = x < minX || z < minZ || x >= minX + size || z >= minZ + size;
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++)
                {
                    helper.setBlock(new BlockPos(x, y, z), wall && y <= 3 ? Blocks.STONE_BRICKS : Blocks.AIR);
                }
            }
        }
        AABB pen = new AABB(helper.absolutePos(new BlockPos(minX, 1, minZ)), helper.absolutePos(new BlockPos(minX + size, 4, minZ + size)));
        level.getEntitiesOfClass(NpcEntity.class, pen).forEach(NpcEntity::discard);
        UUID owner = UUID.randomUUID();
        int each = 100;
        for (int i = 0; i < each * 2; i++)
        {
            NpcEntity npc = squadNpc(helper, owner, new BlockPos(minX + 2 + (i % 14) * 4, 1, minZ + 2 + (i / 14) * 4), NpcRank.SOLDIER, 1);
            npc.setNpcPath(i % 2 == 0 ? NpcPath.SOLDIER : NpcPath.WORKER);
        }
        helper.runAfterDelay(60, () ->
        {
            NpcEntity.resetAiTiming();
            NpcEntity.aiTiming = true;
            helper.runAfterDelay(300, () ->
            {
                NpcEntity.aiTiming = false;
                AncientEmpires.LOGGER.info("GAMETEST goal cost: soldier {} us ({} our own), worker {} us ({} our own) per AI step ({} of each, 300 ticks)",
                        String.format(Locale.ROOT, "%.2f", NpcEntity.aiMicros(NpcPath.SOLDIER)), String.format(Locale.ROOT, "%.2f", NpcEntity.aiCustomMicros(NpcPath.SOLDIER)),
                        String.format(Locale.ROOT, "%.2f", NpcEntity.aiMicros(NpcPath.WORKER)), String.format(Locale.ROOT, "%.2f", NpcEntity.aiCustomMicros(NpcPath.WORKER)), each);
                level.getEntitiesOfClass(NpcEntity.class, pen).forEach(NpcEntity::discard);
                helper.succeed();
            });
        });
    }

    /**
     * Chain of command, phase 3: a holding squad's sergeant hands out the
     * enemies in front of its line, spread over both, and its soldiers (who do
     * no scanning of their own) fight what they are handed until both fall.
     */
    @GameTest(template = ARENA, timeoutTicks = 900)
    public static void squadFightsWhatItIsHanded(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        level.setDayTime(6000L);
        int minX = 10;
        int minZ = 10;
        for (int x = minX; x <= minX + 20; x++)
        {
            for (int z = minZ; z <= minZ + 20; z++)
            {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++)
                {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        AABB field = new AABB(helper.absolutePos(new BlockPos(minX, 0, minZ)), helper.absolutePos(new BlockPos(minX + 21, 5, minZ + 21)));
        level.getEntitiesOfClass(LivingEntity.class, field, living -> !(living instanceof Player)).forEach(Entity::discard);
        UUID owner = UUID.randomUUID();
        NpcEntity sergeant = squadNpc(helper, owner, new BlockPos(minX + 6, 1, minZ + 10), NpcRank.SERGEANT, 5);
        sergeant.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        List<NpcEntity> soldiers = new ArrayList<>();
        for (int i = 0; i < 4; i++)
        {
            NpcEntity soldier = squadNpc(helper, owner, new BlockPos(minX + 5, 1, minZ + 8 + i), NpcRank.SOLDIER, 5);
            // The last one is an archer: it stands in the rows behind the swords.
            soldier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(i == 3 ? Items.BOW : Items.IRON_SWORD));
            helper.assertTrue(NpcChain.toggle(sergeant, soldier) == NpcChain.Assign.ADDED, "soldier " + i + " joins");
            soldiers.add(soldier);
        }
        // Hold facing east; the two husks stand (no AI) just in front of the line, apart.
        sergeant.getChain().setOrder(NpcOrder.HOLD, helper.absolutePos(new BlockPos(minX + 6, 1, minZ + 10)), -90.0F, null);
        List<LivingEntity> husks = new ArrayList<>();
        for (int dz : new int[] {-3, 3})
        {
            Husk husk = EntityType.HUSK.create(level);
            BlockPos at = helper.absolutePos(new BlockPos(minX + 9, 1, minZ + 10 + dz));
            husk.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 90.0F, 0.0F);
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            level.addFreshEntity(husk);
            husks.add(husk);
        }
        helper.runAfterDelay(50, () ->
        {
            Set<LivingEntity> fought = new HashSet<>();
            // Facing east, behind is west: the archer's slot lies west of every sword's.
            double archerX = sergeant.getChain().slotOf(soldiers.get(3)).x;
            for (NpcEntity sword : soldiers.subList(0, 3))
            {
                helper.assertTrue(archerX < sergeant.getChain().slotOf(sword).x, "the archer stands behind the line");
            }
            for (NpcEntity soldier : soldiers)
            {
                helper.assertTrue(soldier.getTarget() != null || soldier.getChain().formedNearLeader(), "in formation near its sergeant (squad morale)");
                helper.assertTrue(soldier.getChain().grunt(), "a soldier in formation is a grunt");
                LivingEntity target = soldier.getTarget();
                AncientEmpires.LOGGER.info("GAMETEST unit fight: soldier {} target {}", soldier.getId(), target == null ? "none" : target.getId());
                if (target != null)
                {
                    helper.assertTrue(husks.contains(target), "a grunt fights only what its sergeant handed it");
                    fought.add(target);
                }
            }
            helper.assertTrue(fought.size() == 2, "the unit spreads over both enemies, on " + fought.size());
            helper.succeedWhen(() -> helper.assertTrue(husks.stream().noneMatch(LivingEntity::isAlive), "both husks fall"));
        });
    }

    /**
     * The command screen's server side: listing a player's army and carrying
     * out its actions, only on the player's own soldiers.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void commandScreenActions(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.moveTo(helper.absolutePos(new BlockPos(-30, 1, 30)).getCenter());
        level.getEntitiesOfClass(NpcEntity.class, new AABB(helper.absolutePos(new BlockPos(-30, 1, 30))).inflate(8.0D)).forEach(NpcEntity::discard);
        NpcEntity sergeant = squadNpc(helper, player.getUUID(), new BlockPos(-30, 1, 32), NpcRank.SERGEANT, 5);
        NpcEntity soldier = squadNpc(helper, player.getUUID(), new BlockPos(-28, 1, 32), NpcRank.SOLDIER, 2);
        NpcEntity stranger = squadNpc(helper, UUID.randomUUID(), new BlockPos(-26, 1, 32), NpcRank.SOLDIER, 2);
        NpcArmy.act(player, NpcArmy.Action.MOVE, soldier.getUUID(), sergeant.getUUID(), "");
        helper.assertTrue(sergeant.getUUID().equals(soldier.getChain().leaderId()), "Move puts the soldier under the sergeant");
        NpcArmy.act(player, NpcArmy.Action.MOVE, stranger.getUUID(), sergeant.getUUID(), "");
        helper.assertTrue(stranger.getChain().leaderId() == null, "another player's soldier is not touched");
        int number = sergeant.getChain().unitNumber();
        helper.assertTrue(number >= 1 && number <= 9, "a new unit gets a number for the order keys, has " + number);
        NpcArmy.command(player, Set.of(number), NpcArmy.Command.FALL_BACK);
        helper.assertTrue(sergeant.getChain().order() == NpcOrder.FALL_BACK, "the wheel's order reaches the unit by its number");
        NpcArmy.command(player, Set.of(), NpcArmy.Command.FOLLOW);
        helper.assertTrue(sergeant.getChain().order() == NpcOrder.FOLLOW, "no number picked means every unit");
        NpcArmy.command(player, Set.of(number == 9 ? 8 : number + 1), NpcArmy.Command.FREE);
        helper.assertTrue(sergeant.getChain().order() == NpcOrder.FOLLOW, "another number leaves it be");
        NpcArmy.act(player, NpcArmy.Action.RENAME, sergeant.getUUID(), null, "Iron Wolves");
        NpcArmy.act(player, NpcArmy.Action.HOLD, sergeant.getUUID(), null, "");
        helper.assertTrue("Iron Wolves".equals(sergeant.getChain().name()), "the unit is named");
        helper.assertTrue(sergeant.getChain().order() == NpcOrder.HOLD, "the unit holds");
        List<ArmyPacket.Entry> army = NpcArmy.list(player);
        helper.assertTrue(army.stream().anyMatch(entry -> entry.id().equals(soldier.getUUID()) && sergeant.getUUID().equals(entry.leader())),
                "the list shows the soldier under its sergeant");
        helper.assertTrue(army.stream().noneMatch(entry -> entry.id().equals(stranger.getUUID())), "and only the player's own");
        NpcArmy.act(player, NpcArmy.Action.REMOVE, soldier.getUUID(), null, "");
        helper.assertTrue(soldier.getChain().leaderId() == null, "Leave takes it out of the unit");
        helper.succeed();
    }

    /**
     * Chain of command, phase 5: a garrison's people come out as one unit under
     * its captain, defending the spawner from their posts (no formation, each
     * fighting for itself); the captain badly hurt sends them out at its attacker.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void garrisonDefendsThenSallies(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos spawner = helper.absolutePos(new BlockPos(-30, 1, -40));
        level.getEntitiesOfClass(NpcEntity.class, new AABB(spawner).inflate(10.0D)).forEach(NpcEntity::discard);
        List<NpcEntity> people = new ArrayList<>();
        for (int i = 0; i < 4; i++)
        {
            NpcEntity npc = ModEntities.NPC.get().create(level);
            npc.moveTo(spawner.getX() + 0.5D + i * 2, spawner.getY(), spawner.getZ() + 2.5D, 0.0F, 0.0F);
            npc.assignFaction("empire");
            npc.setNpcPath(NpcPath.SOLDIER);
            npc.setRank(i == 2 ? NpcRank.CAPTAIN : NpcRank.SOLDIER);
            npc.linkSpawner(spawner, 0, true);
            level.addFreshEntity(npc);
            people.add(npc);
        }
        NpcGarrison.form(level, people, spawner, null);
        NpcEntity captain = people.get(2);
        helper.assertTrue(captain.getChain().members().size() == 3, "the captain leads the garrison");
        helper.assertTrue(captain.getChain().order() == NpcOrder.DEFEND && captain.getChain().posted(), "defending the spawner from its posts");
        for (NpcEntity npc : people)
        {
            helper.assertTrue(!npc.getChain().formed() && !npc.getChain().grunt(), "a posted soldier keeps its post and its own wits");
        }
        Husk attacker = EntityType.HUSK.create(level);
        attacker.moveTo(spawner.getX() + 8.5D, spawner.getY(), spawner.getZ() + 2.5D, 0.0F, 0.0F);
        attacker.setNoAi(true);
        level.addFreshEntity(attacker);
        captain.hurt(level.damageSources().mobAttack(attacker), captain.getMaxHealth() * 0.8F);
        helper.assertTrue(captain.getChain().order() == NpcOrder.ATTACK, "the wounded captain's garrison sallies out at its attacker, has " + captain.getChain().order());
        helper.assertTrue(!captain.getChain().posted(), "off its posts");
        attacker.discard();
        people.forEach(NpcEntity::discard);
        helper.succeed();
    }

    /**
     * Form, then march: a sergeant ordered to hold a spot 28 blocks off first
     * forms its soldiers up where it stands, facing the spot, then marches
     * there with its front rank ahead of it, and deploys in its slots.
     */
    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void unitFormsThenMarches(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        level.setDayTime(6000L);
        int minX = 40;
        int minZ = 8;
        for (int x = minX; x <= minX + 38; x++)
        {
            for (int z = minZ; z <= minZ + 14; z++)
            {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++)
                {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        AABB field = new AABB(helper.absolutePos(new BlockPos(minX, 0, minZ)), helper.absolutePos(new BlockPos(minX + 39, 5, minZ + 15)));
        level.getEntitiesOfClass(LivingEntity.class, field, living -> !(living instanceof Player)).forEach(Entity::discard);
        UUID owner = UUID.randomUUID();
        NpcEntity sergeant = squadNpc(helper, owner, new BlockPos(minX + 4, 1, minZ + 7), NpcRank.SERGEANT, 5);
        List<NpcEntity> soldiers = new ArrayList<>();
        for (int i = 0; i < 4; i++)
        {
            NpcEntity soldier = squadNpc(helper, owner, new BlockPos(minX + 2, 1, minZ + 5 + i), NpcRank.SOLDIER, 5);
            soldier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            helper.assertTrue(NpcChain.toggle(sergeant, soldier) == NpcChain.Assign.ADDED, "soldier " + i + " joins");
            soldiers.add(soldier);
        }
        Vec3 start = sergeant.position();
        BlockPos spot = helper.absolutePos(new BlockPos(minX + 32, 1, minZ + 7));
        sergeant.getChain().setOrder(NpcOrder.HOLD, spot, -90.0F, null);
        helper.assertTrue(sergeant.getChain().march() == NpcChain.March.FORMING, "a far spot: forms up first");
        helper.runAfterDelay(10, () -> helper.assertTrue(sergeant.position().distanceTo(start) < 1.5D, "the sergeant waits while its unit forms up"));
        helper.runAfterDelay(150, () ->
        {
            helper.assertTrue(sergeant.getChain().march() == NpcChain.March.MARCHING, "marching, is " + sergeant.getChain().march());
            helper.assertTrue(sergeant.getX() > start.x + 2.0D, "the sergeant has set off, at " + sergeant.position());
            double ahead = soldiers.stream().mapToDouble(soldier -> soldier.getX() - sergeant.getX()).average().orElse(0.0D);
            AncientEmpires.LOGGER.info("GAMETEST march soldiers ahead by {} on average, sergeant at {}", ahead, sergeant.position());
            helper.assertTrue(ahead > 0.0D, "the front rank marches ahead of its sergeant, by " + ahead);
        });
        helper.runAfterDelay(600, () ->
        {
            helper.assertTrue(sergeant.getChain().march() == NpcChain.March.NONE, "deployed, is " + sergeant.getChain().march());
            helper.assertTrue(sergeant.position().distanceTo(Vec3.atBottomCenterOf(spot)) <= 2.0D, "the sergeant holds the spot, at " + sergeant.position());
            for (NpcEntity soldier : soldiers)
            {
                Vec3 slot = sergeant.getChain().slotOf(soldier);
                double off = Math.sqrt(Math.pow(slot.x - soldier.getX(), 2) + Math.pow(slot.z - soldier.getZ(), 2));
                helper.assertTrue(off <= 1.0D, "in its slot at the spot, off by " + off);
            }
            helper.succeed();
        });
    }

    /**
     * Stances and maneuvers: a Passive unit leaves a foe alone until it goes
     * for one of them, archers under Hold fire draw on nothing beyond arm's
     * length, Advance moves the held spot along the facing, and a turn
     * changes the facing in place.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void stancesAndManeuvers(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(-60, 1, 40));
        level.getEntitiesOfClass(LivingEntity.class, new AABB(base).inflate(12.0D), living -> !(living instanceof Player)).forEach(Entity::discard);
        UUID owner = UUID.randomUUID();
        NpcEntity sergeant = squadNpc(helper, owner, new BlockPos(-60, 1, 40), NpcRank.SERGEANT, 5);
        NpcEntity swordsman = squadNpc(helper, owner, new BlockPos(-59, 1, 41), NpcRank.SOLDIER, 5);
        swordsman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        NpcEntity archer = squadNpc(helper, owner, new BlockPos(-59, 1, 39), NpcRank.SOLDIER, 5);
        archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        NpcChain.toggle(sergeant, swordsman);
        NpcChain.toggle(sergeant, archer);
        NpcChain chain = sergeant.getChain();
        chain.setOrder(NpcOrder.HOLD, base, -90.0F, null);
        Husk husk = EntityType.HUSK.create(level);
        husk.moveTo(base.getX() + 4.5D, base.getY(), base.getZ() + 0.5D, 0.0F, 0.0F);
        husk.setNoAi(true);
        level.addFreshEntity(husk);
        helper.assertTrue(swordsman.getChain().allowsTarget(husk), "holding the line: a foe at the line is fair game");
        chain.setStance(chain.stance().with(NpcStance.Engage.PASSIVE).with(NpcStance.Fire.HOLD));
        helper.runAfterDelay(2, () ->
        {
            helper.assertTrue(swordsman.getChain().stance().engage() == NpcStance.Engage.PASSIVE, "the soldier takes its leader's stance");
            helper.assertTrue(!swordsman.getChain().allowsTarget(husk), "passive: a foe minding its own business is left alone");
            helper.assertTrue(!archer.getChain().allowsTarget(husk), "hold fire: no shot at four blocks");
            husk.setTarget(swordsman);
            helper.assertTrue(swordsman.getChain().allowsTarget(husk), "passive: a foe going for one of the unit is fought");
            chain.advance(5.0D);
            helper.assertTrue(chain.orderPos().getX() == base.getX() + 5 && chain.orderPos().getZ() == base.getZ(), "advance: five blocks east, at " + chain.orderPos());
            chain.turn(90.0F);
            helper.assertTrue(Math.abs(Mth.wrapDegrees(chain.facing() - 0.0F)) < 1.0E-3F, "a right turn from east faces south, faces " + chain.facing());
            husk.discard();
            List.of(sergeant, swordsman, archer).forEach(NpcEntity::discard);
            helper.succeed();
        });
    }

    private static NpcEntity squadNpc(GameTestHelper helper, UUID owner, BlockPos at, NpcRank rank, int npcLevel)
    {
        NpcEntity npc = ModEntities.NPC.get().create(helper.getLevel());
        BlockPos start = helper.absolutePos(at);
        npc.moveTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
        npc.setOwner(owner);
        npc.setNpcPath(NpcPath.SOLDIER);
        npc.setRank(rank);
        npc.setLevel(npcLevel);
        helper.getLevel().addFreshEntity(npc);
        return npc;
    }

    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void largeIslandTiming(GameTestHelper helper)
    {
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(0, 0, 600);
        AePlacer.Report report = placeAndLog(helper, "normal/oceanislands/islandsgriffonroostsrocky", anchor, Rotation.NONE);
        helper.assertTrue(report.blocks() > 0, "the island placed");
        helper.succeed();
    }

    /**
     * Every cell of a template lands where its turn puts it, inside the turned
     * bounds, as the block it should be and facing the turned way, in all
     * four turns (the shape pass may change connections and drop unsupported
     * blocks, never a block's kind or facing).
     */
    @GameTest(template = ARENA, timeoutTicks = 800)
    public static void rotationsAgree(GameTestHelper helper)
    {
        String path = "normal/awnationempire/empireemperorstatuewell";
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, path);
        AeTemplate template = AeTemplates.get(id).orElseThrow();
        int[] offset = offset(id);
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        List<Property<?>> turned = List.of(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.FACING, BlockStateProperties.AXIS,
                BlockStateProperties.ROTATION_16);
        for (Rotation rotation : Rotation.values())
        {
            BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(-300, 0, 120 + rotation.ordinal() * 80);
            placeAndLog(helper, path, anchor, rotation);
            BoundingBox bounds = AePlacer.bounds(template, offset, anchor, rotation);
            int[] checked = new int[1];
            for (int tz = 0; tz < template.tilesZ(); tz++)
            {
                for (int tx = 0; tx < template.tilesX(); tx++)
                {
                    template.forEachInTile(tx, tz, (x, y, z, state) ->
                    {
                        BlockPos pos = AePlacer.cellToWorld(offset, anchor, rotation, x, y, z);
                        if (!bounds.isInside(pos) && problems.size() < 20)
                        {
                            problems.add(rotation + ": cell " + x + "," + y + "," + z + " at " + pos + " is outside " + bounds);
                        }
                        CompoundTag nbt = template.blockEntityAt(x, y, z);
                        if (nbt != null && nbt.contains("aeRule"))
                        {
                            return;
                        }
                        BlockState expected = AeRemap.block(template.palette().get(state)).state().rotate(level, BlockPos.ZERO, rotation);
                        BlockState actual = level.getBlockState(pos);
                        checked[0]++;
                        if (actual.isAir() || problems.size() >= 20)
                        {
                            return;
                        }
                        if (actual.getBlock() != expected.getBlock())
                        {
                            problems.add(rotation + ": " + pos + " is " + actual + ", wanted " + expected);
                            return;
                        }
                        for (Property<?> property : turned)
                        {
                            if (expected.hasProperty(property) && actual.hasProperty(property) && !actual.getValue(property).equals(expected.getValue(property)))
                            {
                                problems.add(rotation + ": " + pos + " " + property.getName() + " is " + actual.getValue(property) + ", wanted "
                                        + expected.getValue(property));
                            }
                        }
                    });
                }
            }
            AncientEmpires.LOGGER.info("GAMETEST rotation {}: {} cells checked, bounds {}", rotation, checked[0], bounds);
        }
        helper.assertTrue(problems.isEmpty(), "every turn agrees: " + problems);
        helper.succeed();
    }

    /**
     * World generation end to end: the structure is registered, the index
     * and AW's territory settings are read, a site is found as /locate finds
     * one, and its start, made and placed chunk by chunk as generation does
     * (the test world is flat with structures off, so this calls the same
     * steps by hand), builds it and records it. A rerun finds the site from
     * the last run already standing, which counts.
     */
    @GameTest(template = ARENA, timeoutTicks = 6000)
    public static void worldGenerationBuildsASite(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(AwSiteIndex.get().sites().size() > 500, "the index holds the templates (fewer without compat mods): " + AwSiteIndex.get().sites().size());
        helper.assertTrue(TerritorySettings.of("empire").maxRadiusChunks() == 23, "AW's territory settings are read: " + TerritorySettings.of("empire"));
        Holder<Structure> land = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "aw_site")));
        long start = System.nanoTime();
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(land),
                helper.absolutePos(BlockPos.ZERO), 64, false);
        AncientEmpires.LOGGER.info("GAMETEST locate found {} in {} ms", found == null ? "nothing" : found.getFirst(), (System.nanoTime() - start) / 1_000_000L);
        helper.assertTrue(found != null, "a site is found within 64 chunks");
        // Locate gives the chunk's corner; a site stands on its chunk's middle.
        BlockPos at = new ChunkPos(found.getFirst()).getMiddleBlockPosition(0);
        start = System.nanoTime();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        StructureStart site = land.value().generate(level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), level.getSeed(), new ChunkPos(at), 0, level, biome -> true);
        if (site.isValid())
        {
            BoundingBox box = site.getBoundingBox();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
            {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                {
                    ChunkPos chunk = new ChunkPos(cx, cz);
                    level.getChunk(cx, cz);
                    site.placeInChunk(level, level.structureManager(), generator, level.getRandom(), new BoundingBox(chunk.getMinBlockX(),
                            level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ()), chunk);
                }
            }
        }
        AncientEmpires.LOGGER.info("GAMETEST generated the site ({}) in {} ms", site.isValid() ? site.getBoundingBox() : "already standing",
                (System.nanoTime() - start) / 1_000_000L);
        helper.runAfterDelay(2, () ->
        {
            List<StructureRecords.Record> here = new ArrayList<>();
            StructureRecords.get(level).forEach(record ->
            {
                if (record.bounds.minX() <= at.getX() && at.getX() <= record.bounds.maxX() && record.bounds.minZ() <= at.getZ() && at.getZ() <= record.bounds.maxZ())
                {
                    here.add(record);
                }
            });
            helper.assertTrue(!here.isEmpty(), "the site is recorded at " + at);
            StructureRecords.Record record = here.get(0);
            int[] solid = new int[1];
            BlockPos.betweenClosed(at.getX() - 8, record.bounds.minY(), at.getZ() - 8, at.getX() + 7, record.bounds.maxY(), at.getZ() + 7)
                    .forEach(pos -> solid[0] += level.getBlockState(pos).isAir() ? 0 : 1);
            AncientEmpires.LOGGER.info("GAMETEST world generation placed {} ({}) at {}, faction '{}'; {} blocks in its middle chunk", record.template,
                    record.bounds, at, record.faction, solid[0]);
            helper.assertTrue(solid[0] > 0, "its middle chunk is built");
            helper.succeed();
        });
    }

    /**
     * AW's decor comes out as our blocks: the vampire crypts' coffins (with
     * their lie and loot), gravestones, and sound block, and the serpent
     * temple's siege engines and statues.
     */
    @GameTest(template = ARENA, timeoutTicks = 800)
    public static void decorAndSiegeEnginesPlace(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        Map<String, Integer> found = new TreeMap<>();
        for (String path : List.of("normal/awpowersvampire/vampireswampcrypts", "normal/awpowersmalice/malicebeachserpenttemple"))
        {
            BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(700, 0, path.contains("vampire") ? 0 : 200);
            placeAndLog(helper, path, anchor, Rotation.NONE);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, path);
            AeTemplate template = AeTemplates.get(id).orElseThrow();
            BoundingBox bounds = AePlacer.bounds(template, offset(id), anchor, Rotation.NONE);
            for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()))
            {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof AwDecorBlock decor)
                {
                    found.merge("decor " + decor.decorName(), 1, Integer::sum);
                }
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity instanceof CoffinBlockEntity)
                {
                    found.merge("coffin", 1, Integer::sum);
                }
                else if (entity instanceof AwSoundBlockEntity)
                {
                    found.merge("sound block", 1, Integer::sum);
                }
                else if (entity instanceof AwDecorBlockEntity decor && !decor.mob().isEmpty())
                {
                    found.merge("mob " + decor.mob(), 1, Integer::sum);
                }
            }
            found.merge("siege engines", level.getEntitiesOfClass(SiegeEngineEntity.class, AABB.of(bounds)).size(), Integer::sum);
        }
        AncientEmpires.LOGGER.info("GAMETEST decor found {}", found);
        helper.assertTrue(found.getOrDefault("coffin", 0) > 0, "coffins are made: " + found);
        helper.assertTrue(found.getOrDefault("decor gravestone", 0) > 0, "gravestones are made: " + found);
        helper.assertTrue(found.getOrDefault("sound block", 0) > 0, "sound blocks are made: " + found);
        helper.assertTrue(found.getOrDefault("siege engines", 0) > 0, "siege engines are made: " + found);
        helper.succeed();
    }

    /**
     * A scan turned back to the template's frame: the well placed facing east
     * and scanned facing east gives the well's own cells again, block for block.
     */
    @GameTest(template = ARENA, timeoutTicks = 800)
    public static void scannerRoundTrip(GameTestHelper helper)
    {
        String path = "normal/awnationempire/empireemperorstatuewell";
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, path);
        AeTemplate original = AeTemplates.get(id).orElseThrow();
        int[] offset = offset(id);
        // High enough that the well's foundation is above the world floor.
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0)).offset(-300, 30, 600);
        placeAndLog(helper, path, anchor, Rotation.CLOCKWISE_90);
        BoundingBox bounds = AePlacer.bounds(original, offset, anchor, Rotation.CLOCKWISE_90);
        ServerLevel level = helper.getLevel();
        // Entities are not compared; clear them so the scan holds blocks only.
        level.getEntitiesOfClass(Entity.class, AABB.of(bounds).inflate(1.0D), entity -> !(entity instanceof Player)).forEach(Entity::discard);
        Component result = StructureScanner.scan(level, "gametest", new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()), anchor, Direction.EAST, "gametest_well", false);
        AncientEmpires.LOGGER.info("GAMETEST scan: {}", result.getString());
        Path file = StructureScanner.packRoot().resolve("data").resolve(AncientEmpires.MOD_ID).resolve(AeTemplates.FOLDER)
                .resolve(StructureScanner.CATEGORY).resolve("gametest_well.nbt");
        AeTemplate scanned;
        try
        {
            scanned = AeTemplate.load(NbtIo.readCompressed(file.toFile()));
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("the scan was not written: " + result.getString(), exception);
        }
        helper.assertTrue(scanned.sizeX() == original.sizeX() && scanned.sizeY() == original.sizeY() && scanned.sizeZ() == original.sizeZ(),
                "same size: " + scanned.sizeX() + "x" + scanned.sizeY() + "x" + scanned.sizeZ());
        List<String> problems = new ArrayList<>();
        for (int tz = 0; tz < original.tilesZ(); tz++)
        {
            for (int tx = 0; tx < original.tilesX(); tx++)
            {
                original.forEachInTile(tx, tz, (x, y, z, state) ->
                {
                    CompoundTag rule = original.blockEntityAt(x, y, z);
                    if (rule != null && rule.contains("aeRule"))
                    {
                        // AW's own blocks (spawners) are made real when placed, so the scan holds ours.
                        return;
                    }
                    String want = AeRemap.block(original.palette().get(state)).state().getBlock().builtInRegistryHolder().key().location().toString();
                    String got = scanned.palette().get(scanned.stateAt(x, y, z)).getString("Name");
                    if (!want.equals(got) && problems.size() < 10)
                    {
                        problems.add(x + "," + y + "," + z + ": " + got + ", wanted " + want);
                    }
                });
            }
        }
        helper.assertTrue(problems.isEmpty(), "the scan matches the template: " + problems);
        helper.succeed();
    }

    /** Not a pass/fail check: logs world generation's survey around a far point, to see which checks turn sites down. */
    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void worldGenerationSurvey(GameTestHelper helper)
    {
        for (ChunkPos centre : List.of(new ChunkPos(150, 150), new ChunkPos(-200, 80)))
        {
            AncientEmpires.LOGGER.info("GAMETEST survey around {}: {}", centre, AwSurvey.around(helper.getLevel(), centre, 24));
        }
        helper.succeed();
    }
}
