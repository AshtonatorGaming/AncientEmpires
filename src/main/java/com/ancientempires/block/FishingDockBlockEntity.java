package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A fishing dock. Fishers ask it for open water to cast into: water with air
 * above inside its square, from three blocks below the dock to its level, and
 * for a shore spot (dry ground with room to stand) within casting reach of it.
 */
public final class FishingDockBlockEntity extends WorksiteBlockEntity
{
    private static final int SHORE_REACH = 4;

    private final List<BlockPos> water = new ArrayList<>();

    public FishingDockBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.FISHING_DOCK.get(), pos, state);
    }

    @Override
    protected int defaultRadius()
    {
        return AncientEmpiresConfig.fishDefaultRadius;
    }

    @Override
    protected int maxRadius()
    {
        return AncientEmpiresConfig.fishMaxRadius;
    }

    @Nullable
    public BlockPos claimWater(int fisherId, BlockPos from)
    {
        this.refresh();
        return this.claimNearest(this.water, fisherId, from);
    }

    @Override
    protected void forget(BlockPos tile)
    {
        this.water.remove(tile);
    }

    @Override
    protected void scan(Level level)
    {
        this.water.clear();
        int r = this.radius();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++)
        {
            for (int dz = -r; dz <= r; dz++)
            {
                for (int y = this.worldPosition.getY(); y >= this.worldPosition.getY() - 3; y--)
                {
                    cursor.set(this.worldPosition.getX() + dx, y, this.worldPosition.getZ() + dz);
                    if (level.getFluidState(cursor).is(FluidTags.WATER) && level.getBlockState(cursor.above()).isAir())
                    {
                        this.water.add(cursor.immutable());
                        break;
                    }
                }
            }
        }
    }

    /**
     * Dry ground near the water with room to stand (two clear blocks above
     * something solid), nearest the water, or null if the water is out of
     * reach from any bank.
     */
    @Nullable
    public static BlockPos shoreFor(Level level, BlockPos water)
    {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(water.offset(-SHORE_REACH, -1, -SHORE_REACH), water.offset(SHORE_REACH, 2, SHORE_REACH)))
        {
            BlockPos below = pos.below();
            if (!level.getFluidState(pos).isEmpty() || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
            {
                continue;
            }
            double distance = pos.distSqr(water);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }
}
