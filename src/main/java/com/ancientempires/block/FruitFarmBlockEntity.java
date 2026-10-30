package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ancient Warfare's fruit farm (WorkSiteFruitFarm). It does no picking itself;
 * pickers ask it for ripe fruit inside its square, from two blocks below the
 * farm block to {@link #REACH_UP} above it (AW searched seven up): cocoa pods,
 * sweet berry bushes, glow berries on cave vines, melons and pumpkins off their
 * stems; and for the sides of jungle logs where cocoa beans can go.
 */
public final class FruitFarmBlockEntity extends WorksiteBlockEntity
{
    /** Blocks above the farm block searched for fruit. */
    public static final int REACH_UP = 7;

    public enum Task
    {
        PICK, PLANT
    }

    private final List<BlockPos> pick = new ArrayList<>();
    private final List<BlockPos> plant = new ArrayList<>();

    public FruitFarmBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.FRUIT_FARM.get(), pos, state);
    }

    @Override
    protected int defaultSize()
    {
        return AncientEmpiresConfig.farmDefaultSize;
    }

    @Override
    protected List<? extends Integer> maxSizes()
    {
        return AncientEmpiresConfig.farmMaxSize;
    }

    private static final List<Supply> SUPPLIES = List.of(
            new Supply("cocoa", stack -> stack.is(Items.COCOA_BEANS), () -> new ItemStack(Items.COCOA_BEANS), 2));

    /** Cocoa beans for pickers to set on jungle logs. */
    @Override
    public List<Supply> supplies()
    {
        return SUPPLIES;
    }

    @Nullable
    public BlockPos claim(Task task, int workerId, BlockPos from)
    {
        this.refresh();
        return this.claimNearest(task == Task.PICK ? this.pick : this.plant, workerId, from);
    }

    public boolean hasWork(Task task)
    {
        this.refresh();
        return !(task == Task.PICK ? this.pick : this.plant).isEmpty();
    }

    @Override
    protected void forget(BlockPos tile)
    {
        this.pick.remove(tile);
        this.plant.remove(tile);
    }

    @Override
    protected void scan(Level level)
    {
        this.pick.clear();
        this.plant.clear();
        // Every block of the area and a one-block rim read once into a grid, so the neighbour tests
        // (a cocoa spot's log, a melon's stem) read the grid instead of the level: about one read per cell.
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        int baseY = this.worldPosition.getY();
        int x0 = this.minX() - 1;
        int z0 = this.minZ() - 1;
        int y0 = baseY - 2;
        int width = this.maxX() - this.minX() + 3;
        int depth = this.maxZ() - this.minZ() + 3;
        int height = REACH_UP + 3;
        BlockState[] grid = new BlockState[width * depth * height];
        for (int x = 0; x < width; x++)
        {
            for (int z = 0; z < depth; z++)
            {
                for (int y = 0; y < height; y++)
                {
                    grid[(x * depth + z) * height + y] = level.getBlockState(cell.set(x0 + x, y0 + y, z0 + z));
                }
            }
        }
        for (int x = 1; x < width - 1; x++)
        {
            for (int z = 1; z < depth - 1; z++)
            {
                for (int y = 0; y < height; y++)
                {
                    BlockState state = grid[(x * depth + z) * height + y];
                    if (isRipe(state) && (!isGourd(state) || hasStem(grid, depth, height, x, y, z)))
                    {
                        this.pick.add(new BlockPos(x0 + x, y0 + y, z0 + z));
                    }
                    else if (state.isAir() && hasLog(grid, depth, height, x, y, z))
                    {
                        this.plant.add(new BlockPos(x0 + x, y0 + y, z0 + z));
                    }
                }
            }
        }
    }

    /** A melon or pumpkin: fruit only where a stem grew it, never one set down as a block. */
    public static boolean isGourd(BlockState state)
    {
        return state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN);
    }

    private static boolean hasStem(BlockState[] grid, int depth, int height, int x, int y, int z)
    {
        for (Direction side : Direction.Plane.HORIZONTAL)
        {
            BlockState next = grid[((x + side.getStepX()) * depth + z + side.getStepZ()) * height + y];
            if (next.getBlock() instanceof AttachedStemBlock && next.getValue(AttachedStemBlock.FACING) == side.getOpposite())
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLog(BlockState[] grid, int depth, int height, int x, int y, int z)
    {
        for (Direction side : Direction.Plane.HORIZONTAL)
        {
            if (grid[((x + side.getStepX()) * depth + z + side.getStepZ()) * height + y].is(BlockTags.JUNGLE_LOGS))
            {
                return true;
            }
        }
        return false;
    }

    /** A melon or pumpkin beside the stem that grew it (the picker's check at the block). */
    public static boolean stemGrown(Level level, BlockPos pos)
    {
        for (Direction side : Direction.Plane.HORIZONTAL)
        {
            BlockState next = level.getBlockState(pos.relative(side));
            if (next.getBlock() instanceof AttachedStemBlock && next.getValue(AttachedStemBlock.FACING) == side.getOpposite())
            {
                return true;
            }
        }
        return false;
    }

    /** Fruit ready to pick: a grown cocoa pod, a ripe berry bush, glow berries, a melon or pumpkin. */
    public static boolean isRipe(BlockState state)
    {
        if (state.getBlock() instanceof CocoaBlock)
        {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }
        if (state.getBlock() instanceof SweetBerryBushBlock)
        {
            return state.getValue(SweetBerryBushBlock.AGE) >= SweetBerryBushBlock.MAX_AGE;
        }
        if (state.getBlock() instanceof CaveVines)
        {
            return CaveVines.hasGlowBerries(state);
        }
        return state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN);
    }

    /** The way a cocoa pod set in this air cell would face: toward a jungle log beside it, or null. */
    @Nullable
    public static Direction cocoaFacing(Level level, BlockPos cell)
    {
        for (Direction side : Direction.Plane.HORIZONTAL)
        {
            if (level.getBlockState(cell.relative(side)).is(BlockTags.JUNGLE_LOGS))
            {
                return side;
            }
        }
        return null;
    }
}
