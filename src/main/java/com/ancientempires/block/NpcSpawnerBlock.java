package com.ancientempires.block;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.inventory.NpcSpawnerMenu;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * The NPC spawner (see {@link NpcSpawnerBlockEntity}). Admins (creative)
 * right-click it to set it up. Hidden, it is not drawn and has no collision,
 * so it can sit in a structure's floor, but an admin can still target it.
 */
public final class NpcSpawnerBlock extends BaseEntityBlock
{
    public static final BooleanProperty HIDDEN = BooleanProperty.create("hidden");

    public NpcSpawnerBlock(Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HIDDEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(HIDDEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return state.getValue(HIDDEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(HIDDEN) ? Shapes.empty() : super.getCollisionShape(state, level, pos, context);
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
        if (!NpcEntity.mayToggleCreative(player))
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.spawner.admin_only"), true);
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof NpcSpawnerBlockEntity spawner)
        {
            NetworkHooks.openScreen((ServerPlayer) player, new SimpleMenuProvider((id, inventory, opener) ->
                    new NpcSpawnerMenu(id, inventory, spawner), state.getBlock().getName()), pos);
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new NpcSpawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.NPC_SPAWNER.get(), NpcSpawnerBlockEntity::serverTick);
    }
}
