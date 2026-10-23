package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
 * {@code work.quarryMaxDepth}. Each layer keeps a step of a spiral staircase
 * around the rim ({@code work.quarryStairWidth} wide, a step lower per layer),
 * so miners walk down to dig and climb back up to unload at the block. Steps
 * that are not plain stone (ore, dirt, sand, gravel, gaps) are dug out and
 * relaid with cheap stone the miners carry ({@code quarryStairStone}). Left
 * in place: blocks touching lava or water, block entities, unbreakable blocks,
 * and what a miner's pickaxe cannot harvest.
 * The area is fixed once digging starts.
 */
public final class QuarryBlockEntity extends WorksiteBlockEntity
{
    /** Blocks a stair step may already be; anything else is relaid. */
    public static final TagKey<Block> STAIR_STONE = BlockTags.create(ResourceLocation.fromNamespaceAndPath("ancientempires", "stair_stone"));
    /** Cheap stone a miner lays in the stair: cobblestone, sandstone, and the like, never smooth stone. */
    public static final TagKey<Item> STAIR_STONE_ITEMS = ItemTags.create(ResourceLocation.fromNamespaceAndPath("ancientempires", "stair_stone"));
    /** Scans a finished layer waits for its stair to be relaid before moving down anyway. */
    private static final int STAIR_WAIT_SCANS = 3;

    private final List<BlockPos> mine = new ArrayList<>();
    /** Stair steps on the current layer to dig out and relay with stone. */
    private final List<BlockPos> stairFix = new ArrayList<>();
    private int stairWaits;
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
    protected int defaultSize()
    {
        return AncientEmpiresConfig.quarryDefaultSize;
    }

    @Override
    protected List<? extends Integer> maxSizes()
    {
        return AncientEmpiresConfig.quarryMaxSize;
    }

    private static final List<Supply> SUPPLIES = List.of(
            new Supply("stair_stone", stack -> stack.is(STAIR_STONE_ITEMS) && stack.getItem() instanceof BlockItem,
                    () -> new ItemStack(Items.COBBLESTONE), 9));

    /** Cheap stone for miners to relay the stair with. */
    @Override
    public List<Supply> supplies()
    {
        return SUPPLIES;
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

    /** The pit starts beside the block, in front of it, centered on it. */
    @Override
    protected WorksiteArea defaultArea()
    {
        int size = Math.min(this.defaultSize(), this.maxSize());
        WorksiteArea square = WorksiteArea.centered(size, size);
        Direction facing = this.facing();
        // Slide the centered square out along the facing until its near edge is beside the block.
        int shift = (size - 1) / 2 + 1;
        return square.moved(facing.getStepX() * (shift + (facing.getStepX() > 0 ? 0 : size % 2 == 0 ? 1 : 0)),
                facing.getStepZ() * (shift + (facing.getStepZ() > 0 ? 0 : size % 2 == 0 ? 1 : 0)));
    }

    @Override
    public UpgradeKind upgradeKind()
    {
        return UpgradeKind.QUARRY;
    }

    /** Miners dig under the pit, so the quarry block cannot stand inside it. */
    @Override
    public boolean mayContainBlock()
    {
        return false;
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

    /** A stair step to relay, for a miner carrying stone, or null. */
    @Nullable
    public BlockPos claimStair(int workerId, BlockPos from)
    {
        this.refresh();
        return this.claimNearest(this.stairFix, workerId, from);
    }

    /** Whether stair steps on this layer wait for stone. */
    public boolean stairNeedsStone()
    {
        this.refresh();
        return !this.stairFix.isEmpty();
    }

    /** A block the miner cannot take (its pickaxe is too weak); it stays, and the layer moves on without it. */
    public void skip(BlockPos pos)
    {
        this.skipped.add(pos.immutable());
        this.mine.remove(pos);
        this.stairFix.remove(pos);
        this.setChanged();
    }

    @Override
    protected void forget(BlockPos tile)
    {
        this.mine.remove(tile);
        this.stairFix.remove(tile);
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
        this.stairFix.clear();
        for (int pass = 0; pass < 4 && !this.finished; pass++)
        {
            this.scanLayer(level);
            if (!this.mine.isEmpty())
            {
                return;
            }
            // Dug out: give the stair a little time to be relaid, then go on regardless.
            if (!this.stairFix.isEmpty() && this.stairWaits++ < STAIR_WAIT_SCANS)
            {
                return;
            }
            this.stairFix.clear();
            this.nextLayer(level);
        }
    }

    private void nextLayer(Level level)
    {
        this.layer++;
        this.stairWaits = 0;
        this.skipped.clear();
        if (this.layer >= AncientEmpiresConfig.quarryMaxDepth || this.layerY(this.layer) <= level.getMinBuildHeight() + 1)
        {
            this.finished = true;
        }
        this.setChanged();
    }

    private void scanLayer(Level level)
    {
        Set<Long> stair = this.stairColumns(this.layer);
        int y = this.layerY(this.layer);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = this.minX(); x <= this.maxX(); x++)
        {
            for (int z = this.minZ(); z <= this.maxZ(); z++)
            {
                cursor.set(x, y, z);
                if (this.skipped.contains(cursor))
                {
                    continue;
                }
                if (stair.contains(BlockPos.asLong(x, 0, z)))
                {
                    if (AncientEmpiresConfig.quarryStairStone && needsRelaying(level, cursor))
                    {
                        this.stairFix.add(cursor.immutable());
                    }
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
     * A stair step that is not plain stone and can be dug and relaid: ore,
     * dirt, sand or gravel (which would fall), or a gap. Not beside water or
     * lava, and not unbreakable or a block entity.
     */
    private static boolean needsRelaying(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (state.is(STAIR_STONE) || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F || !state.getFluidState().isEmpty())
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
     * The columns kept as this layer's stair step, packed as {@link BlockPos#asLong}
     * at y 0: one rim tile, starting nearest the block and one tile further
     * clockwise per layer, plus the tiles inward from it to the stair width.
     * Columns of the two steps above are left out, so a step never sits under
     * another (at the corners), which would block the head of anyone climbing.
     */
    private Set<Long> stairColumns(int layer)
    {
        List<BlockPos> rim = this.rim();
        if (rim.isEmpty())
        {
            // A one-column pit has no rim to spare: no stair.
            return Set.of();
        }
        int start = 0;
        long best = Long.MAX_VALUE;
        for (int index = 0; index < rim.size(); index++)
        {
            BlockPos tile = rim.get(index);
            long dx = tile.getX() - this.worldPosition.getX();
            long dz = tile.getZ() - this.worldPosition.getZ();
            if (dx * dx + dz * dz < best)
            {
                best = dx * dx + dz * dz;
                start = index;
            }
        }
        int narrowest = Math.min(this.maxX() - this.minX() + 1, this.maxZ() - this.minZ() + 1);
        // A small pit keeps room to dig: the stair takes under half its width.
        int width = Math.max(1, Math.min(AncientEmpiresConfig.quarryStairWidth, (narrowest - 1) / 2));
        Set<Long> columns = this.stepColumns(rim, start + layer, width);
        for (int above = 1; above <= 2 && layer - above >= 0; above++)
        {
            columns.removeAll(this.stepColumns(rim, start + layer - above, width));
        }
        return columns;
    }

    /** Rim tile {@code index} (wrapping) and the tiles inward from it, to {@code width} in all. */
    private Set<Long> stepColumns(List<BlockPos> rim, int index, int width)
    {
        BlockPos tile = rim.get(Math.floorMod(index, rim.size()));
        BlockPos next = rim.get(Math.floorMod(index + 1, rim.size()));
        // The rim runs clockwise, so inward is a quarter turn clockwise from the way it runs.
        Direction along = Direction.fromDelta(Integer.signum(next.getX() - tile.getX()), 0, Integer.signum(next.getZ() - tile.getZ()));
        Direction inward = (along == null ? Direction.EAST : along).getClockWise();
        Set<Long> columns = new HashSet<>();
        for (int step = 0; step < width; step++)
        {
            columns.add(BlockPos.asLong(tile.getX() + inward.getStepX() * step, 0, tile.getZ() + inward.getStepZ() * step));
        }
        return columns;
    }

    /** The square's edge
, clockwise from the north-west corner, at y 0. */
    private List<BlockPos> rim()
    {
        int minX = this.minX();
        int maxX = this.maxX();
        int minZ = this.minZ();
        int maxZ = this.maxZ();
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
