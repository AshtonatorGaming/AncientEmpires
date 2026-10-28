package com.ancientempires.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

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

    /** What happened: cells set, palette entries that needed a table or fallback, entities placed and skipped. */
    public record Report(int blocks, Map<String, Integer> substituted, int entities, Map<String, Integer> skipped)
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
        BlockPos a = cellToWorld(offset, anchor, rotation, 0, 0, 0);
        BlockPos b = cellToWorld(offset, anchor, rotation, template.sizeX() - 1, template.sizeY() - 1, template.sizeZ() - 1);
        return BoundingBox.fromCorners(a, b);
    }

    public static Report place(ServerLevelAccessor level, AeTemplate template, int[] offset, BlockPos anchor, Rotation rotation, @Nullable BoundingBox limit)
    {
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
                    BlockEntity old = level.getBlockEntity(pos);
                    if (old != null)
                    {
                        Clearable.tryClear(old);
                    }
                    level.setBlock(pos, states[state], FLAGS);
                    uses[state]++;
                    placed[0]++;
                    CompoundTag nbt = template.blockEntityAt(x, y, z);
                    if (nbt != null && !nbt.contains("aeRule"))
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
        Map<String, Integer> skipped = new TreeMap<>();
        int entities = placeEntities(level, template, offset, anchor, rotation, limit, skipped);
        return new Report(placed[0], substituted, entities, skipped);
    }

    private static int placeEntities(ServerLevelAccessor level, AeTemplate template, int[] offset, BlockPos anchor, Rotation rotation,
            @Nullable BoundingBox limit, Map<String, Integer> skipped)
    {
        int count = 0;
        for (CompoundTag entry : template.entities())
        {
            CompoundTag nbt = entry.getCompound("nbt").copy();
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
