package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A crop farm. It does no farming itself; farmers ask it for ripe crops, bare
 * farmland, and soil to till inside its square, from two blocks below the farm
 * block up to its level.
 */
public final class CropFarmBlockEntity extends WorksiteBlockEntity
{
    public enum Task
    {
        HARVEST, PLANT, TILL
    }

    private final List<BlockPos> harvest = new ArrayList<>();
    private final List<BlockPos> plant = new ArrayList<>();
    private final List<BlockPos> till = new ArrayList<>();

    public CropFarmBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.CROP_FARM.get(), pos, state);
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
            case HARVEST -> this.harvest;
            case PLANT -> this.plant;
            case TILL -> this.till;
        };
    }

    @Override
    protected void forget(BlockPos tile)
    {
        this.harvest.remove(tile);
        this.plant.remove(tile);
        this.till.remove(tile);
    }

    @Override
    protected void scan(Level level)
    {
        this.harvest.clear();
        this.plant.clear();
        this.till.clear();
        BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos();
        int baseY = this.worldPosition.getY();
        for (int x = this.minX(); x <= this.maxX(); x++)
        {
            for (int z = this.minZ(); z <= this.maxZ(); z++)
            {
                for (int y = baseY - 2; y <= baseY; y++)
                {
                    ground.set(x, y, z);
                    this.scanTile(level, ground);
                }
            }
        }
    }

    /** Sorts one ground tile: a ripe crop above it, bare farmland, or soil a hoe can turn. */
    private void scanTile(Level level, BlockPos.MutableBlockPos ground)
    {
        if (ground.equals(this.worldPosition.below()))
        {
            return;
        }
        BlockState soil = level.getBlockState(ground);
        BlockPos above = ground.above();
        BlockState crop = level.getBlockState(above);
        if (soil.getBlock() instanceof FarmBlock)
        {
            if (crop.isAir())
            {
                this.plant.add(above.immutable());
            }
            else if (isRipe(crop))
            {
                this.harvest.add(above.immutable());
            }
            return;
        }
        if (crop.is(Blocks.PUMPKIN) || crop.is(Blocks.MELON))
        {
            this.harvest.add(above.immutable());
            return;
        }
        if (crop.isAir() && isTillable(soil))
        {
            this.till.add(ground.immutable());
        }
    }

    public static boolean isTillable(BlockState soil)
    {
        return soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.DIRT_PATH);
    }

    public static boolean isRipe(BlockState state)
    {
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }
}
