package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        int baseY = this.worldPosition.getY();
        for (int x = this.minX(); x <= this.maxX(); x++)
        {
            for (int z = this.minZ(); z <= this.maxZ(); z++)
            {
                for (int y = baseY - 2; y <= baseY + REACH_UP; y++)
                {
                    cell.set(x, y, z);
                    BlockState state = level.getBlockState(cell);
                    if (isRipe(state))
                    {
                        this.pick.add(cell.immutable());
                    }
                    else if (state.isAir() && cocoaFacing(level, cell) != null)
                    {
                        this.plant.add(cell.immutable());
                    }
                }
            }
        }
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
