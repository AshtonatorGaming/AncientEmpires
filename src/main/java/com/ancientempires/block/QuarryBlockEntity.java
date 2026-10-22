package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A quarry. The pit is the square in front of the block (the way the placer
 * faced), dug one layer at a time from just below the block's level down to
 * {@code work.quarryMaxDepth}. Each layer keeps one block of a spiral staircase
 * around the rim, a step lower per layer, so miners walk down to dig and climb
 * back up to unload at the block. Left in place: blocks touching lava or water,
 * block entities, unbreakable blocks, and what a miner's pickaxe cannot harvest.
 * The area is fixed once digging starts.
 */
public final class QuarryBlockEntity extends WorksiteBlockEntity
{
    private final List<BlockPos> mine = new ArrayList<>();
    /** Blocks on the current layer a miner could not take; cleared with each new layer. */
    private final Set<BlockPos> skipped = new HashSet<>();
    private int layer;
    private boolean finished;
    /** A block has been dug; the stair layout depends on the size from here on. */
    private boolean started;

    public QuarryBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.QUARRY.get(), pos, state);
    }

    @Override
    protected int defaultRadius()
    {
        return AncientEmpiresConfig.quarryDefaultRadius;
    }

    @Override
    protected int maxRadius()
    {
        return AncientEmpiresConfig.quarryMaxRadius;
    }

    @Override
    public boolean canResize()
    {
        return this.layer == 0 && !this.started;
    }

    private Direction facing()
    {
        BlockState state = this.getBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    @Override
    public BlockPos center()
    {
        return this.worldPosition.relative(this.facing(), this.radius() + 1);
    }

    /** A miner took a block. */
    public void markDug()
    {
        if (!this.started)
        {
            this.started = true;
            this.setChanged();
        }
    }

    public boolean finished()
    {
        return this.finished;
    }

    public int layer()
    {
        return this.layer;
    }

    @Nullable
    public BlockPos claim(int workerId, BlockPos from)
    {
        this.refresh();
        return this.claimNearest(this.mine, workerId, from);
    }

    /** A block the miner cannot take (its pickaxe is too weak); it stays, and the layer moves on without it. */
    public void skip(BlockPos pos)
    {
        this.skipped.add(pos.immutable());
        this.mine.remove(pos);
        this.setChanged();
    }

    @Override
    protected void forget(BlockPos tile)
    {
        this.mine.remove(tile);
    }

    private int layerY(int layer)
    {
        return this.worldPosition.getY() - 1 - layer;
    }

    /**
     * Fills the to-do list from the current layer. A layer with nothing left to
     * take moves the quarry one down; a few empty layers (a cave) are passed
     * in one scan.
     */
    @Override
    protected void scan(Level level)
    {
        this.mine.clear();
        for (int pass = 0; pass < 4 && !this.finished; pass++)
        {
            this.scanLayer(level);
            if (!this.mine.isEmpty())
            {
                return;
            }
            this.nextLayer(level);
        }
    }

    private void nextLayer(Level level)
    {
        this.layer++;
        this.skipped.clear();
        if (this.layer >= AncientEmpiresConfig.quarryMaxDepth || this.layerY(this.layer) <= level.getMinBuildHeight() + 1)
        {
            this.finished = true;
        }
        this.setChanged();
    }

    private void scanLayer(Level level)
    {
        int r = this.radius();
        BlockPos center = this.center();
        BlockPos step = this.stairStep(this.layer);
        int y = this.layerY(this.layer);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++)
        {
            for (int dz = -r; dz <= r; dz++)
            {
                cursor.set(center.getX() + dx, y, center.getZ() + dz);
                if ((cursor.getX() == step.getX() && cursor.getZ() == step.getZ()) || this.skipped.contains(cursor))
                {
                    continue;
                }
                if (mineable(level, cursor))
                {
                    this.mine.add(cursor.immutable());
                }
            }
        }
    }

    /** Solid, breakable, no block entity, and no lava or water touching it. */
    public static boolean mineable(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0.0F || state.hasBlockEntity())
        {
            return false;
        }
        for (Direction side : Direction.values())
        {
            if (!level.getFluidState(pos.relative(side)).isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * The column kept as this layer's stair step: the rim tiles in order,
     * starting beside the block and running clockwise, one tile further per
     * layer, wrapping round.
     */
    private BlockPos stairStep(int layer)
    {
        List<BlockPos> rim = this.rim();
        BlockPos entrance = this.worldPosition.relative(this.facing());
        int start = 0;
        for (int index = 0; index < rim.size(); index++)
        {
            BlockPos tile = rim.get(index);
            if (tile.getX() == entrance.getX() && tile.getZ() == entrance.getZ())
            {
                start = index;
                break;
            }
        }
        return rim.get(Math.floorMod(start + layer, rim.size()));
    }

    /** The square's edge, clockwise from the north-west corner, at y 0. */
    private List<BlockPos> rim()
    {
        int r = this.radius();
        BlockPos center = this.center();
        int minX = center.getX() - r;
        int maxX = center.getX() + r;
        int minZ = center.getZ() - r;
        int maxZ = center.getZ() + r;
        List<BlockPos> rim = new ArrayList<>();
        for (int x = minX; x < maxX; x++)
        {
            rim.add(new BlockPos(x, 0, minZ));
        }
        for (int z = minZ; z < maxZ; z++)
        {
            rim.add(new BlockPos(maxX, 0, z));
        }
        for (int x = maxX; x > minX; x--)
        {
            rim.add(new BlockPos(x, 0, maxZ));
        }
        for (int z = maxZ; z > minZ; z--)
        {
            rim.add(new BlockPos(minX, 0, z));
        }
        return rim;
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putInt("Layer", this.layer);
        tag.putBoolean("Finished", this.finished);
        tag.putBoolean("Started", this.started);
        long[] skipped = new long[this.skipped.size()];
        int index = 0;
        for (BlockPos pos : this.skipped)
        {
            skipped[index++] = pos.asLong();
        }
        tag.putLongArray("Skipped", skipped);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.layer = tag.getInt("Layer");
        this.finished = tag.getBoolean("Finished");
        this.started = tag.getBoolean("Started") || this.layer > 0;
        this.skipped.clear();
        for (long packed : tag.getLongArray("Skipped"))
        {
            this.skipped.add(BlockPos.of(packed));
        }
    }
}
