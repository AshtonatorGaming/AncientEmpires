package com.ancientempires.block.decor;

import com.ancientempires.registry.ModBlockEntities;
import com.ancientempires.client.ClientScreens;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * AW's Sound Block: a block dressed as another (stone bricks in a wall, say)
 * that plays a structure's ambience, horns, bells, or hauntings to players
 * nearby ({@link AwSoundBlockEntity}). The disguise is drawn by its renderer.
 */
public class AwSoundBlock extends BaseEntityBlock
{
    public AwSoundBlock(Properties properties)
    {
        super(properties);
    }

    /**
     * As in AW: a block in hand becomes the disguise; otherwise the tune editor opens
     * (sounds, ranges, pause, order, once or while near).
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        // Admins only (creative): structures hide these in their walls, and any player could re-skin or retune them.
        if (!(level.getBlockEntity(pos) instanceof AwSoundBlockEntity sound) || !NpcEntity.mayToggleCreative(player))
        {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof BlockItem block && !(block.getBlock() instanceof AwSoundBlock))
        {
            if (!level.isClientSide)
            {
                sound.setDisguise(block.getBlock().defaultBlockState());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (hand == InteractionHand.MAIN_HAND && level.isClientSide)
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientScreens.openSoundBlock(pos, sound.tune()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new AwSoundBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.SOUND_BLOCK.get(), AwSoundBlockEntity::serverTick);
    }
}
