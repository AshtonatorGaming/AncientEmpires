package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Ancient Warfare's coin stacks (Copper Owls, Silver Stags, Gold Dragons,
 * Ancient Coins): decor and a store of coin. Each layer is one stack item (8
 * coins); placing another stack on one adds a layer, as snow does, up to 8 (64
 * coins, AW's tallest). Structures place them by AW's size (8 to 64 coins).
 */
public class CoinStackBlock extends Block
{
    public static final int MAX_LAYERS = 8;
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, MAX_LAYERS);
    private static final VoxelShape[] SHAPES = new VoxelShape[MAX_LAYERS + 1];

    static
    {
        for (int layers = 1; layers <= MAX_LAYERS; layers++)
        {
            SHAPES[layers] = Block.box(2.0D, 0.0D, 2.0D, 14.0D, Math.min(16.0D, 1.0D + layers), 14.0D);
        }
    }

    public CoinStackBlock(Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(LAYERS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        // Lenient: AW piled coins on tables, chests and altars, which do not all have a full top.
        return !level.isEmptyBlock(pos.below());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context)
    {
        return context.getItemInHand().is(this.asItem()) && state.getValue(LAYERS) < MAX_LAYERS || super.canBeReplaced(state, context);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockState there = context.getLevel().getBlockState(context.getClickedPos());
        if (there.is(this))
        {
            return there.setValue(LAYERS, Math.min(MAX_LAYERS, there.getValue(LAYERS) + 1));
        }
        return this.defaultBlockState();
    }

    /** AW's stack size (8 to 64 coins, eight a step) as layers. */
    public static int layersForCoins(int coins)
    {
        return Math.max(1, Math.min(MAX_LAYERS, (coins + 7) / 8));
    }
}
