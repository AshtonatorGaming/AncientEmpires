package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A tree farm. Woodcutters ask it for trees to fell (a log standing on soil
 * inside the square), dropped saplings, apples, and sticks to pick up, and grid
 * tiles to plant ({@code work.treeSpacing} apart, so trees have room). Ground
 * from two blocks below the farm block up to one above it counts.
 */
public final class TreeFarmBlockEntity extends WorksiteBlockEntity
{
    public enum Task
    {
        FELL, COLLECT, PLANT
    }

    private final List<BlockPos> trees = new ArrayList<>();
    private final List<BlockPos> drops = new ArrayList<>();
    private final List<BlockPos> plant = new ArrayList<>();

    public TreeFarmBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.TREE_FARM.get(), pos, state);
    }

    @Override
    protected int defaultRadius()
    {
        return AncientEmpiresConfig.treeDefaultRadius;
    }

    @Override
    protected int maxRadius()
    {
        return AncientEmpiresConfig.treeMaxRadius;
    }

    @Nullable
    public BlockPos claim(Task task, int workerId, BlockPos from)
    {
        this.refresh();
        return this.claimNearest(this.list(task), workerId, from);
    }

    public boolean hasWork(Task task)
    {
        this.refresh();
        return !this.list(task).isEmpty();
    }

    private List<BlockPos> list(Task task)
    {
        return switch (task)
        {
            case FELL -> this.trees;
            case COLLECT -> this.drops;
            case PLANT -> this.plant;
        };
    }

    @Override
    protected void forget(BlockPos tile)
    {
        this.trees.remove(tile);
        this.drops.remove(tile);
        this.plant.remove(tile);
    }

    @Override
    protected void scan(Level level)
    {
        this.trees.clear();
        this.drops.clear();
        this.plant.clear();
        int r = this.radius();
        int spacing = Math.max(1, AncientEmpiresConfig.treeSpacing);
        int baseY = this.worldPosition.getY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++)
        {
            for (int dz = -r; dz <= r; dz++)
            {
                int x = this.worldPosition.getX() + dx;
                int z = this.worldPosition.getZ() + dz;
                for (int y = baseY - 2; y <= baseY + 1; y++)
                {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    BlockState below = level.getBlockState(cursor.below());
                    if (!below.is(BlockTags.DIRT))
                    {
                        continue;
                    }
                    if (state.is(BlockTags.LOGS))
                    {
                        this.trees.add(cursor.immutable());
                        break;
                    }
                    boolean onGrid = Math.floorMod(dx, spacing) == 0 && Math.floorMod(dz, spacing) == 0;
                    if (onGrid && state.isAir() && level.getBlockState(cursor.above()).isAir() && !cursor.equals(this.worldPosition))
                    {
                        this.plant.add(cursor.immutable());
                        break;
                    }
                }
            }
        }
        AABB area = new AABB(this.worldPosition).inflate(r, 2, r).expandTowards(0, 8, 0);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, item -> item.isAlive() && !item.hasPickUpDelay()))
        {
            BlockPos at = item.blockPosition();
            if (!this.drops.contains(at))
            {
                this.drops.add(at);
            }
        }
    }

    /**
     * The logs of the tree standing on {@code base}: connected logs, diagonals
     * included, within {@code work.treeReach} blocks sideways of the base so a
     * neighbouring tree is not taken too, at most {@code treeMaxLogs}.
     */
    public static List<BlockPos> trunk(Level level, BlockPos base)
    {
        List<BlockPos> logs = new ArrayList<>();
        if (!level.getBlockState(base).is(BlockTags.LOGS))
        {
            return logs;
        }
        int reach = AncientEmpiresConfig.treeReach;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(base);
        seen.add(base);
        while (!open.isEmpty() && logs.size() < AncientEmpiresConfig.treeMaxLogs)
        {
            BlockPos at = open.poll();
            logs.add(at);
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dy = 0; dy <= 1; dy++)
                {
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        BlockPos next = at.offset(dx, dy, dz);
                        if (Math.abs(next.getX() - base.getX()) > reach || Math.abs(next.getZ() - base.getZ()) > reach
                                || next.getY() < base.getY() || !seen.add(next))
                        {
                            continue;
                        }
                        if (level.getBlockState(next).is(BlockTags.LOGS))
                        {
                            open.add(next);
                        }
                    }
                }
            }
        }
        return logs;
    }

    /** The log to chop next: the highest, so the trunk comes down from the top. */
    @Nullable
    public static BlockPos topLog(Level level, BlockPos base)
    {
        BlockPos top = null;
        for (BlockPos log : trunk(level, base))
        {
            if (top == null || log.getY() > top.getY())
            {
                top = log;
            }
        }
        return top;
    }
}
