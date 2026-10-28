package com.ancientempires.block.decor;

import com.ancientempires.structure.AwLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * AW's wooden coffins and stone sarcophagi. The first block holds the coffin
 * ({@link CoffinBlockEntity}: its way, upright or lying, open or shut, and
 * AW's loot settings); the {@code invisible} ones are the rest of its length,
 * placed by the structure. Using any part lifts the lid and, the first time,
 * gives AW's loot, trap, and message (through {@link AwLoot}).
 */
public class CoffinBlock extends BaseEntityBlock
{
    public static final AwStringProperty VARIANT = new AwStringProperty("variant",
            List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "stone", "sandstone", "prismarine", "demonic"));
    public static final BooleanProperty INVISIBLE = BooleanProperty.create("invisible");
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D);

    private final boolean stone;

    public CoffinBlock(boolean stone, Properties properties)
    {
        super(properties);
        this.stone = stone;
        this.registerDefaultState(this.stateDefinition.any().setValue(VARIANT, stone ? "stone" : "oak").setValue(INVISIBLE, false));
    }

    public boolean stone()
    {
        return this.stone;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(VARIANT, INVISIBLE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return state.getValue(INVISIBLE) ? null : new CoffinBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return this.defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        if (placer != null && level.getBlockEntity(pos) instanceof CoffinBlockEntity coffin)
        {
            coffin.setDirection(CoffinBlockEntity.directionFacing(placer.getYRot()));
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        BlockPos main = state.getValue(INVISIBLE) ? this.mainNear(level, pos) : pos;
        if (main != null && level.getBlockEntity(main) instanceof CoffinBlockEntity coffin && !level.isClientSide)
        {
            coffin.open();
            AwLoot.trigger((ServerLevel) level, main, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The coffin a hidden part belongs to: the nearest of this kind within its length. */
    @Nullable
    private BlockPos mainNear(Level level, BlockPos pos)
    {
        BlockPos best = null;
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 0, 2)))
        {
            if (level.getBlockEntity(at) instanceof CoffinBlockEntity && level.getBlockState(at).is(this)
                    && (best == null || at.distSqr(pos) < best.distSqr(pos)))
            {
                best = at.immutable();
            }
        }
        return best;
    }
}
