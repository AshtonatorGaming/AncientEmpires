package com.ancientempires.structure;

import com.ancientempires.block.CoinStackBlock;
import com.ancientempires.block.FlagBlock;
import com.ancientempires.block.FlagBlockEntity;
import com.ancientempires.block.decor.AwDecorBlock;
import com.ancientempires.block.decor.AwDecorBlockEntity;
import com.ancientempires.block.decor.AwSoundBlockEntity;
import com.ancientempires.block.decor.AwStringProperty;
import com.ancientempires.block.decor.CoffinBlock;
import com.ancientempires.block.decor.CoffinBlockEntity;
import com.ancientempires.block.decor.StatueBlock;
import com.ancientempires.gate.GateEntity;
import com.ancientempires.gate.GateType;
import com.ancientempires.registry.ModBlocks;
import com.ancientempires.siege.SiegeEngineEntity;
import com.ancientempires.worldgen.AwSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Places an {@link AeTemplate} in the world: the template's build key (AW's
 * offset) on {@code anchor}, turned by {@code rotation} about it. Blocks are
 * set without neighbour updates, then each one's shape is worked out from its
 * neighbours (fences, panes, walls, stair corners), as AW's states were saved
 * unconnected. Vanilla block entities and entities are restored; AW's own
 * (spawners, gates, NPCs, vehicles) and blocks of absent mods are counted for
 * the report and left for later stages. With a {@code limit}, only cells
 * inside it are touched: how world generation will place one chunk at a time.
 */
public final class AePlacer
{
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /**
     * What happened: cells set, palette entries that needed a table or fallback, AW blocks made real
     * (spawners), entities placed, and what was skipped or went wrong.
     */
    public record Report(int blocks, Map<String, Integer> substituted, Map<String, Integer> aw, int entities, Map<String, Integer> skipped)
    {
    }

    private AePlacer()
    {
    }

    /** Where a template cell lands. */
    public static BlockPos cellToWorld(int[] offset, BlockPos anchor, Rotation rotation, int x, int y, int z)
    {
        return anchor.offset(new BlockPos(x - offset[0], y - offset[1], z - offset[2]).rotate(rotation));
    }

    /** The world area a placement covers. */
    public static BoundingBox bounds(AeTemplate template, int[] offset, BlockPos anchor, Rotation rotation)
    {
        return bounds(new int[] {template.sizeX(), template.sizeY(), template.sizeZ()}, offset, anchor, rotation);
    }

    /** The world area a template of {@code size} would cover, without loading it (world generation plans with the sidecar). */
    public static BoundingBox bounds(int[] size, int[] offset, BlockPos anchor, Rotation rotation)
    {
        BlockPos a = cellToWorld(offset, anchor, rotation, 0, 0, 0);
        BlockPos b = cellToWorld(offset, anchor, rotation, size[0] - 1, size[1] - 1, size[2] - 1);
        return BoundingBox.fromCorners(a, b);
    }

    /**
     * Places {@code template} (named {@code name}, for the structure record).
     * A whole placement (no {@code limit}) of a faction structure is recorded in
     * {@link StructureRecords}: its owner is its protection flag's faction, or
     * else the faction most of its garrison belongs to, and the flag is tied to
     * the record, so claiming and protection work.
     */
    public static Report place(ServerLevelAccessor level, AeTemplate template, String name, int[] offset, BlockPos anchor, Rotation rotation,
            @Nullable BoundingBox limit)
    {
        return place(level, template, name, offset, anchor, rotation, limit, null);
    }

    /**
     * World generation's share of a template: the cells inside one chunk's
     * {@code chunkBox}, on a generation thread. The structure is recorded
     * when it is chosen, not here; protection flags are marked as such and
     * found by position when claimed ({@code faction} fills a flag that
     * names none). Blocks on the chunk's edge are left to vanilla's
     * post-processing, which shapes them once the neighbours exist.
     */
    public static Report placeInChunk(WorldGenLevel level, AeTemplate template, String name, int[] offset, BlockPos anchor, Rotation rotation,
            BoundingBox chunkBox, String faction)
    {
        return place(level, template, name, offset, anchor, rotation, chunkBox, faction);
    }

    /**
     * The faction a template belongs to, read from its blocks without placing
     * it: its protection flag's, else the one most of its garrison belongs to
     * (empty: none), as {@link #place} decides for a whole placement.
     */
    public static String faction(AeTemplate template)
    {
        Map<String, Integer> factions = new HashMap<>();
        for (Map.Entry<Integer, CompoundTag> entry : template.blockEntities().entrySet())
        {
            CompoundTag rule = entry.getValue().getCompound("aeRule");
            if (rule.isEmpty())
            {
                continue;
            }
            int[] cell = template.cellOf(entry.getKey());
            String block = template.palette().get(template.stateAt(cell[0], cell[1], cell[2])).getString("Name");
            if (block.equals("ancientwarfarestructure:protection_flag") && !rule.getCompound("teData").getString("name").isEmpty())
            {
                return rule.getCompound("teData").getString("name");
            }
            if (block.equals("ancientwarfarestructure:advanced_spawner"))
            {
                countGarrison(rule, factions);
            }
        }
        return factions.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
    }

    private static void countGarrison(CompoundTag rule, Map<String, Integer> factions)
    {
        for (Tag group : rule.getCompound("teData").getCompound("spawnerSettings").getList("spawnGroups", Tag.TAG_COMPOUND))
        {
            for (Tag entry : ((CompoundTag) group).getList("settingsList", Tag.TAG_COMPOUND))
            {
                String faction = ((CompoundTag) entry).getCompound("customTag").getString("factionName");
                if (!faction.isEmpty())
                {
                    factions.merge(faction, 1, Integer::sum);
                }
            }
        }
    }

    private static Report place(ServerLevelAccessor level, AeTemplate template, String name, int[] offset, BlockPos anchor, Rotation rotation,
            @Nullable BoundingBox limit, @Nullable String worldGenFaction)
    {
        BlockState proxy = ModBlocks.GATE_PROXY.get().defaultBlockState();
        Map<BlockPos, String> protectionFlags = new LinkedHashMap<>();
        Map<String, Integer> factions = new HashMap<>();
        int size = template.palette().size();
        BlockState[] states = new BlockState[size];
        String[] notes = new String[size];
        for (int i = 1; i < size; i++)
        {
            AeRemap.Resolved resolved = AeRemap.block(template.palette().get(i));
            states[i] = resolved.state().rotate(level, BlockPos.ZERO, rotation);
            notes[i] = resolved.note();
        }
        int[] uses = new int[size];
        Map<String, Integer> aw = new TreeMap<>();
        Map<String, Integer> skipped = new TreeMap<>();
        int[] placed = new int[1];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int tz = 0; tz < template.tilesZ(); tz++)
        {
            for (int tx = 0; tx < template.tilesX(); tx++)
            {
                template.forEachInTile(tx, tz, (x, y, z, state) ->
                {
                    BlockPos pos = cellToWorld(offset, anchor, rotation, x, y, z);
                    if (limit != null && !limit.isInside(pos))
                    {
                        return;
                    }
                    if (states[state].isAir() && level.getBlockState(pos).is(proxy.getBlock()))
                    {
                        // A gate built from a neighbouring chunk already closes this cell.
                        return;
                    }
                    BlockEntity old = level.getBlockEntity(pos);
                    if (old != null)
                    {
                        Clearable.tryClear(old);
                    }
                    level.setBlock(pos, states[state], FLAGS);
                    uses[state]++;
                    placed[0]++;
                    CompoundTag nbt = template.blockEntityAt(x, y, z);
                    if (nbt != null && nbt.contains("aeRule"))
                    {
                        String blockName = template.palette().get(state).getString("Name");
                        if (placeAw(level, pos, blockName, nbt.getCompound("aeRule"), rotation, skipped, protectionFlags, factions))
                        {
                            aw.merge(blockName, 1, Integer::sum);
                        }
                    }
                    else if (nbt != null)
                    {
                        BlockEntity blockEntity = level.getBlockEntity(pos);
                        if (blockEntity != null)
                        {
                            CompoundTag data = nbt.copy();
                            data.putInt("x", pos.getX());
                            data.putInt("y", pos.getY());
                            data.putInt("z", pos.getZ());
                            blockEntity.load(data);
                            blockEntity.setChanged();
                            if (blockEntity instanceof FlagBlockEntity flag && flag.isProtection())
                            {
                                // A scanned protection flag guards the structure it came with, as AW's did.
                                protectionFlags.put(pos.immutable(), flag.faction());
                            }
                        }
                    }
                });
            }
        }
        // Shapes once everything stands, so each block sees its finished neighbours.
        for (int tz = 0; tz < template.tilesZ(); tz++)
        {
            for (int tx = 0; tx < template.tilesX(); tx++)
            {
                template.forEachInTile(tx, tz, (x, y, z, state) ->
                {
                    cursor.set(cellToWorld(offset, anchor, rotation, x, y, z));
                    if (limit != null && !limit.isInside(cursor))
                    {
                        return;
                    }
                    BlockState current = level.getBlockState(cursor);
                    BlockState shaped = Block.updateFromNeighbourShapes(current, level, cursor);
                    if (shaped != current)
                    {
                        level.setBlock(cursor, shaped, FLAGS);
                    }
                    if (worldGenFaction != null && !current.isAir() && onEdge(limit, cursor))
                    {
                        level.getChunk(cursor).markPosForPostprocessing(cursor);
                    }
                });
            }
        }
        Map<String, Integer> substituted = new TreeMap<>();
        for (int i = 1; i < size; i++)
        {
            if (notes[i] != null && uses[i] > 0)
            {
                substituted.merge(template.palette().get(i).getString("Name") + " (" + notes[i] + ")", uses[i], Integer::sum);
            }
        }
        int entities = placeEntities(level, template, offset, anchor, rotation, limit, skipped);
        if (worldGenFaction != null)
        {
            guard(level, protectionFlags, worldGenFaction, 0);
        }
        else if (limit == null)
        {
            record(level, name, bounds(template, offset, anchor, rotation), protectionFlags, factions);
        }
        return new Report(placed[0], substituted, aw, entities, skipped);
    }

    private static boolean onEdge(BoundingBox box, BlockPos pos)
    {
        return pos.getX() == box.minX() || pos.getX() == box.maxX() || pos.getZ() == box.minZ() || pos.getZ() == box.maxZ();
    }

    /**
     * Records a placed structure (one with no faction only so world
     * generation keeps clear of it) and ties its protection flags to it.
     */
    private static void record(ServerLevelAccessor level, String name, BoundingBox bounds, Map<BlockPos, String> protectionFlags, Map<String, Integer> factions)
    {
        String faction = protectionFlags.values().stream().filter(id -> !id.isEmpty()).findFirst()
                .orElseGet(() -> factions.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(""));
        BlockPos flag = protectionFlags.isEmpty() ? null : protectionFlags.keySet().iterator().next();
        StructureRecords.Record record = StructureRecords.get(level.getLevel()).add(name, bounds, faction, flag);
        AwSites.remember(level.getLevel().dimension(), name, bounds);
        guard(level, protectionFlags, faction, record.id);
    }

    /** Marks a structure's protection flags as guarding {@code record} (0: found by position when claimed). */
    private static void guard(ServerLevelAccessor level, Map<BlockPos, String> protectionFlags, String faction, int record)
    {
        for (BlockPos pos : protectionFlags.keySet())
        {
            if (level.getBlockEntity(pos) instanceof FlagBlockEntity entity)
            {
                if (entity.faction().isEmpty())
                {
                    entity.setFaction(faction);
                }
                entity.setProtection(true, record);
            }
        }
    }

    /** AW's own blocks that have become ours; false leaves the table's stand-in. */
    private static boolean placeAw(ServerLevelAccessor level, BlockPos pos, String name, CompoundTag rule, Rotation rotation, Map<String, Integer> problems,
            Map<BlockPos, String> protectionFlags, Map<String, Integer> factions)
    {
        switch (name)
        {
            case "ancientwarfarestructure:advanced_spawner" ->
            {
                AwSpawnerImport.place(level, pos, rule, problems);
                countGarrison(rule, factions);
                return true;
            }
            case "ancientwarfarestructure:protection_flag", "ancientwarfarestructure:decorative_flag" ->
            {
                int turn = Integer.parseInt("0" + rule.getCompound("blockState").getCompound("properties").getString("rotation"));
                level.setBlock(pos, ModBlocks.FLAG.get().defaultBlockState().setValue(FlagBlock.ROTATION, rotation.rotate(turn & 15, 16)), FLAGS);
                if (level.getBlockEntity(pos) instanceof FlagBlockEntity flag)
                {
                    String faction = rule.getCompound("teData").getString("name");
                    flag.setFaction(faction);
                    if (name.endsWith("protection_flag"))
                    {
                        protectionFlags.put(pos.immutable(), faction);
                    }
                }
                return true;
            }
            case "ancientwarfarestructure:advanced_loot_chest", "ancientwarfarestructure:loot_basket", "ancientwarfarestructure:urn" ->
            {
                AwLoot.place(level, pos, rule);
                return true;
            }
            case "ancientwarfarestructure:coin_stack_copper", "ancientwarfarestructure:coin_stack_silver", "ancientwarfarestructure:coin_stack_gold",
                    "ancientwarfarestructure:coin_stack_ancient" ->
            {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof CoinStackBlock)
                {
                    int coins = Integer.parseInt("0" + rule.getCompound("blockState").getCompound("properties").getString("size"));
                    level.setBlock(pos, state.setValue(CoinStackBlock.LAYERS, CoinStackBlock.layersForCoins(coins)), FLAGS);
                    return true;
                }
                return false;
            }
            default ->
            {
                return placeDecor(level, pos, rule.getCompound("teData"), rule, rotation);
            }
        }
    }

    /**
     * AW's decor pieces that kept something in their tile: a chair's facing (its
     * block state facing is stale), a coffin's lie and loot, a gravestone's loot
     * (AW's variants 6 to 8 only), a stake's or statue's mob, a sound block's
     * disguise and tune. False for anything else, leaving the stand-in.
     */
    private static boolean placeDecor(ServerLevelAccessor level, BlockPos pos, CompoundTag teData, CompoundTag rule, Rotation rotation)
    {
        BlockState placed = level.getBlockState(pos);
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof CoffinBlockEntity coffin)
        {
            coffin.importAw(teData, rotation);
            AwLoot.place(level, pos, rule);
            return true;
        }
        if (entity instanceof AwSoundBlockEntity sound)
        {
            sound.importAw(teData);
            return true;
        }
        if (placed.getBlock() instanceof StatueBlock && entity instanceof AwDecorBlockEntity statue)
        {
            statue.setMob(AwDecorBlockEntity.mobFrom(teData), false, rotation.rotate(Direction.from2DDataValue(teData.getByte("facing"))).toYRot());
            return true;
        }
        if (placed.getBlock() instanceof CoffinBlock)
        {
            return true;
        }
        if (!(placed.getBlock() instanceof AwDecorBlock decor))
        {
            return false;
        }
        Direction facing = Direction.byName(teData.getString("facing"));
        if (facing != null && facing.getAxis().isHorizontal() && placed.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
        {
            level.setBlock(pos, placed.setValue(BlockStateProperties.HORIZONTAL_FACING, rotation.rotate(facing)), FLAGS);
        }
        if (entity instanceof AwDecorBlockEntity held)
        {
            if (decor.decorName().equals("altar_candle") || decor.decorName().endsWith("_cloth"))
            {
                held.setColor(AwDecorBlockEntity.colorFrom(teData), teData.contains("flameColor") ? teData.getInt("flameColor") : -1);
            }
            else if (decor.decorName().equals("stake"))
            {
                held.setMob(AwDecorBlockEntity.mobFrom(teData), teData.getBoolean("entityOnFire"), 0.0F);
            }
            else if (decor.decorName().equals("gravestone") && placed.getBlock().getStateDefinition().getProperty("variant") instanceof AwStringProperty variant
                    && List.of("6", "7", "8").contains(placed.getValue(variant)))
            {
                AwLoot.place(level, pos, rule);
            }
        }
        return true;
    }

    private static int placeEntities(ServerLevelAccessor level, AeTemplate template, int[] offset, BlockPos anchor, Rotation rotation,
            @Nullable BoundingBox limit, Map<String, Integer> skipped)
    {
        int count = 0;
        for (CompoundTag entry : template.entities())
        {
            CompoundTag nbt = entry.getCompound("nbt").copy();
            if (entry.getString("plugin").equals("awGate"))
            {
                if (placeGate(level, entry, nbt.getCompound("aeRule"), offset, anchor, rotation, limit))
                {
                    count++;
                }
                else
                {
                    skipped.merge("awGate (opening blocked)", 1, Integer::sum);
                }
                continue;
            }
            if (entry.getString("plugin").equals("AWVehicle"))
            {
                if (placeVehicle(level, entry, nbt.getCompound("aeRule"), offset, anchor, rotation, limit))
                {
                    count++;
                }
                continue;
            }
            if (nbt.contains("aeRule"))
            {
                skipped.merge(entry.getString("plugin") + " " + nbt.getString("id"), 1, Integer::sum);
                continue;
            }
            ListTag pos = entry.getList("pos", Tag.TAG_DOUBLE);
            double[] local = rotate(pos.getDouble(0) - offset[0], pos.getDouble(2) - offset[2], rotation);
            double worldX = anchor.getX() + local[0];
            double worldY = anchor.getY() + pos.getDouble(1) - offset[1];
            double worldZ = anchor.getZ() + local[1];
            BlockPos block = BlockPos.containing(worldX, worldY, worldZ);
            if (limit != null && !limit.isInside(block))
            {
                continue;
            }
            nbt.putString("id", AeRemap.entityId(nbt.getString("id")));
            if (nbt.contains("Facing"))
            {
                // A hanging entity (painting, item frame) stands on its block, not its position.
                nbt.putInt("TileX", block.getX());
                nbt.putInt("TileY", block.getY());
                nbt.putInt("TileZ", block.getZ());
            }
            Optional<Entity> made = EntityType.create(nbt, level.getLevel());
            if (made.isEmpty())
            {
                skipped.merge("missing " + nbt.getString("id"), 1, Integer::sum);
                continue;
            }
            Entity entity = made.get();
            float yaw = entity.rotate(rotation);
            entity.moveTo(worldX, worldY, worldZ, yaw, entity.getXRot());
            if (entity instanceof Mob mob)
            {
                mob.setPersistenceRequired();
            }
            level.addFreshEntityWithPassengers(entity);
            count++;
        }
        return count;
    }

    /**
     * One of AW's siege engines: a placeholder with its type, material level,
     * health, and turret as AW saved them, turned with the structure.
     */
    private static boolean placeVehicle(ServerLevelAccessor level, CompoundTag entry, CompoundTag rule, int[] offset, BlockPos anchor, Rotation rotation,
            @Nullable BoundingBox limit)
    {
        ListTag pos = entry.getList("pos", Tag.TAG_DOUBLE);
        double[] local = rotate(pos.getDouble(0) - offset[0], pos.getDouble(2) - offset[2], rotation);
        double x = anchor.getX() + local[0];
        double y = anchor.getY() + pos.getDouble(1) - offset[1];
        double z = anchor.getZ() + local[1];
        if (limit != null && !limit.isInside(BlockPos.containing(x, y, z)))
        {
            return false;
        }
        CompoundTag data = rule.getCompound("entityData");
        float yaw = rule.getFloat("rotation") + 90.0F * rotation.ordinal();
        SiegeEngineEntity engine = SiegeEngineEntity.of(level.getLevel(), data.getInt("vehType"), data.getInt("matLvl"), yaw,
                Mth.wrapDegrees(rule.getFloat("rotation") - rule.getFloat("turretRotation")), data.getFloat("health"));
        engine.moveTo(x, y, z, yaw, 0.0F);
        level.addFreshEntity(engine);
        return true;
    }

    /**
     * An AW gate: its corners are its position cell plus AW's {@code pos1} and
     * {@code pos2} offsets, turned with the structure, as is its facing. The
     * opening is cleared (AW's gate proxies were placed as air) and the gate
     * built closed, with no owner: a structure's gate answers to its faction.
     */
    private static boolean placeGate(ServerLevelAccessor level, CompoundTag entry, CompoundTag rule, int[] offset, BlockPos anchor, Rotation rotation,
            @Nullable BoundingBox limit)
    {
        ListTag pos = entry.getList("pos", Tag.TAG_DOUBLE);
        int x = (int) Math.floor(pos.getDouble(0));
        int y = (int) Math.floor(pos.getDouble(1));
        int z = (int) Math.floor(pos.getDouble(2));
        CompoundTag one = rule.getCompound("pos1");
        CompoundTag two = rule.getCompound("pos2");
        BlockPos pos1 = cellToWorld(offset, anchor, rotation, x + one.getInt("x"), y + one.getInt("y"), z + one.getInt("z"));
        BlockPos pos2 = cellToWorld(offset, anchor, rotation, x + two.getInt("x"), y + two.getInt("y"), z + two.getInt("z"));
        if (limit != null && !limit.isInside(pos1))
        {
            return false;
        }
        for (BlockPos cell : BlockPos.betweenClosed(pos1, pos2))
        {
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        Direction facing = rotation.rotate(Direction.from2DDataValue(rule.getByte("orientation")));
        GateEntity gate = GateEntity.create(level.getLevel(), level, pos1, pos2, GateType.byAwName(rule.getString("gateType")), facing, null);
        if (gate == null)
        {
            return false;
        }
        level.addFreshEntity(gate);
        return true;
    }

    /**
     * A point inside the template turned about the build key, as vanilla turns
     * template entities: a cell spans [x, x + 1), so turning maps x to 1 - x.
     */
    static double[] rotate(double x, double z, Rotation rotation)
    {
        return switch (rotation)
        {
            case CLOCKWISE_90 -> new double[] {1.0D - z, x};
            case CLOCKWISE_180 -> new double[] {1.0D - x, 1.0D - z};
            case COUNTERCLOCKWISE_90 -> new double[] {z, 1.0D - x};
            default -> new double[] {x, z};
        };
    }
}
