package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Town Hall. Right-click opens its food store (food bundles or any food;
 * hoppers work too). Sneak-right-click empty-handed shows how many of the
 * owner's NPCs it reaches, how many went hungry, its food, and the fallen it
 * has recorded. Owner or admin only.
 */
public final class TownHallBlock extends BaseEntityBlock
{
    public TownHallBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new TownHallBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof TownHallBlockEntity hall)
        {
            hall.setOwner(placer instanceof Player player ? player.getUUID() : null);
            NpcUpkeep.register(server, pos);
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
        if (!(level.getBlockEntity(pos) instanceof TownHallBlockEntity hall) || !(level instanceof ServerLevel server))
        {
            return InteractionResult.PASS;
        }
        if (!NpcEntity.mayToggleCreative(player) && hall.owner() != null && !player.getUUID().equals(hall.owner()))
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.farm.not_owner"), true);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty())
        {
            List<Integer> status = NpcUpkeep.status(server, pos, hall);
            int rations = hall.pantry() / Math.max(1, AncientEmpiresConfig.upkeepRation);
            player.displayClientMessage(Component.translatable("message.ancientempires.hall.status",
                    status.get(0), status.get(1), rations, status.get(2)), false);
            return InteractionResult.CONSUME;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) ->
                new ChestMenu(MenuType.GENERIC_9x2, id, inventory, hall.store(), 2), state.getBlock().getName()));
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock()))
        {
            if (level.getBlockEntity(pos) instanceof TownHallBlockEntity hall)
            {
                for (ItemStack stack : hall.contents())
                {
                    Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
                }
            }
            if (level instanceof ServerLevel server)
            {
                NpcUpkeep.unregister(server, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
