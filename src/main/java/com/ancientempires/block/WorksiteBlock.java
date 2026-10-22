package com.ancientempires.block;

import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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

/**
 * A worksite block (crop farm, tree farm, quarry). Right-click opens its store
 * (supplies in, yield out; hoppers work too). Sneak-right-click empty-handed
 * grows the area by one ring, wrapping back to the smallest, and outlines it.
 * Only the owner or an admin can use it. Workers do the work by hand and carry
 * what they gather back here.
 */
public abstract class WorksiteBlock extends BaseEntityBlock
{
    protected WorksiteBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WorksiteBlockEntity farm)
        {
            farm.setOwner(placer instanceof Player player ? player.getUUID() : null);
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
        if (!(level.getBlockEntity(pos) instanceof WorksiteBlockEntity farm))
        {
            return InteractionResult.PASS;
        }
        if (!NpcEntity.mayToggleCreative(player) && farm.owner() != null && !player.getUUID().equals(farm.owner()))
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.farm.not_owner"), true);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty())
        {
            if (!farm.canResize())
            {
                player.displayClientMessage(Component.translatable("message.ancientempires.farm.locked"), true);
                outline((ServerLevel) level, farm.center(), farm.radius());
                return InteractionResult.CONSUME;
            }
            int radius = farm.cycleRadius();
            int width = radius * 2 + 1;
            player.displayClientMessage(Component.translatable("message.ancientempires.farm.area", width, width), true);
            outline((ServerLevel) level, farm.center(), radius);
            return InteractionResult.CONSUME;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) ->
                new ChestMenu(MenuType.GENERIC_9x2, id, inventory, farm.store(), 2), state.getBlock().getName()));
        return InteractionResult.CONSUME;
    }

    /** Green sparks along the area's edge, at crop height. */
    private static void outline(ServerLevel level, BlockPos pos, int radius)
    {
        double y = pos.getY() + 0.2D;
        for (int step = -radius; step <= radius; step++)
        {
            double a = step + 0.5D;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + a, y, pos.getZ() - radius + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + a, y, pos.getZ() + radius + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() - radius + 0.5D, y, pos.getZ() + a, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + radius + 0.5D, y, pos.getZ() + a, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WorksiteBlockEntity farm)
        {
            for (ItemStack stack : farm.contents())
            {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
