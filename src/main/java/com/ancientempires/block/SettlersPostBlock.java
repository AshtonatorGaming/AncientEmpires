package com.ancientempires.block;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Dwarf Fortress style immigration: while open, a post draws settlers to its
 * owner, one per free bed nearby, each eating a stored food bundle. Right-click
 * with food bundles to stock it, right-click empty-handed to open or close it,
 * sneak-right-click for its status. Kept apart from the Town Hall, which will
 * handle upkeep and revival.
 */
public final class SettlersPostBlock extends BaseEntityBlock
{
    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    public SettlersPostBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
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
        return new SettlersPostBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof SettlersPostBlockEntity post)
        {
            post.setOwner(placer instanceof Player player ? player.getUUID() : null);
            NpcImmigration.register(server, pos);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof SettlersPostBlockEntity post) || !(level instanceof ServerLevel server))
        {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.FOOD_BUNDLE.get()))
        {
            int taken = post.addFood(held.getCount());
            if (!player.getAbilities().instabuild)
            {
                held.shrink(taken);
            }
            player.displayClientMessage(Component.translatable(taken > 0
                    ? "message.ancientempires.post.food_added"
                    : "message.ancientempires.post.food_full", taken, post.food()), true);
            return InteractionResult.CONSUME;
        }
        boolean mayManage = NpcEntity.mayToggleCreative(player) || player.getUUID().equals(post.owner());
        if (player.isShiftKeyDown() || !mayManage)
        {
            player.displayClientMessage(NpcImmigration.status(server, pos, post), false);
            return InteractionResult.CONSUME;
        }
        post.setOpen(!post.isOpen());
        player.displayClientMessage(Component.translatable(post.isOpen()
                ? "message.ancientempires.post.opened"
                : "message.ancientempires.post.closed"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock()))
        {
            if (level.getBlockEntity(pos) instanceof SettlersPostBlockEntity post && post.food() > 0)
            {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        new ItemStack(ModItems.FOOD_BUNDLE.get(), post.food()));
            }
            if (level instanceof ServerLevel server)
            {
                NpcImmigration.unregister(server, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
