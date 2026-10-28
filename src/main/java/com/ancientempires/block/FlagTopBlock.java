package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The invisible upper part of a flag: the banner stands about one and three
 * quarter blocks tall, so the cloth above the flag's own block is here to be
 * clicked and broken. Everything is handed to the flag below, and it goes
 * when that does.
 */
public class FlagTopBlock extends Block
{
    private static final VoxelShape CLOTH = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);

    public FlagTopBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return CLOTH;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        BlockPos below = pos.below();
        BlockState flag = level.getBlockState(below);
        return flag.getBlock() instanceof FlagBlock ? flag.use(level, player, hand, hit.withPosition(below)) : InteractionResult.PASS;
    }

    /** Mined as fast as the flag itself (structure protection included), and breaking it breaks the flag. */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos)
    {
        BlockState flag = level.getBlockState(pos.below());
        return flag.getBlock() instanceof FlagBlock ? flag.getDestroyProgress(player, level, pos.below()) : 1.0F;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        if (!level.isClientSide && level.getBlockState(pos.below()).getBlock() instanceof FlagBlock)
        {
            level.destroyBlock(pos.below(), !player.isCreative(), player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos)
    {
        return direction == Direction.DOWN && !(neighbour.getBlock() instanceof FlagBlock) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player)
    {
        BlockState flag = level.getBlockState(pos.below());
        return flag.getBlock() instanceof FlagBlock ? flag.getCloneItemStack(target, level, pos.below(), player) : ItemStack.EMPTY;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        return level.getBlockState(pos.below()).getBlock() instanceof FlagBlock;
    }
}
