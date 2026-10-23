package com.ancientempires.block;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.inventory.WorksiteMenu;
import com.ancientempires.item.WorksiteUpgradeItem;
import com.ancientempires.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * A worksite block (crop farm, tree farm, quarry). Right-click opens its store
 * (supplies in, yield out; hoppers work too). Sneak-right-click empty-handed
 * opens the bounds screen (move the area, set its width and length) and
 * outlines it; right-click with a size upgrade fits it.
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
        if (!mayManage(player, farm))
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.farm.not_owner"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof WorksiteUpgradeItem upgrade)
        {
            return upgrade.apply(player, farm, held);
        }
        if (player.isShiftKeyDown() && held.isEmpty() && farm.upgradeKind() != WorksiteBlockEntity.UpgradeKind.NONE)
        {
            // Ancient Warfare's bounds screen: move the area and set its width and length.
            outline((ServerLevel) level, farm);
            ModNetwork.sendWorksiteBounds((ServerPlayer) player, farm);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen((ServerPlayer) player, new SimpleMenuProvider((id, inventory, opener) ->
                new WorksiteMenu(id, inventory, farm), state.getBlock().getName()), pos);
        return InteractionResult.CONSUME;
    }

    /** Green sparks along the area's edge, at crop height. */
    public static void outline(ServerLevel level, WorksiteBlockEntity site)
    {
        double y = site.getBlockPos().getY() + 0.2D;
        for (int x = site.minX(); x <= site.maxX(); x++)
        {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + 0.5D, y, site.minZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + 0.5D, y, site.maxZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        for (int z = site.minZ() + 1; z < site.maxZ(); z++)
        {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, site.minX() + 0.5D, y, z + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, site.maxX() + 0.5D, y, z + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Whether a player may change this worksite: its owner, anyone if it has none, or an admin. */
    public static boolean mayManage(Player player, WorksiteBlockEntity site)
    {
        return NpcEntity.mayToggleCreative(player) || site.owner() == null || player.getUUID().equals(site.owner());
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
            ItemStack upgrade = WorksiteUpgradeItem.stackFor(farm.upgradeKind(), farm.sizeTier());
            if (!upgrade.isEmpty())
            {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, upgrade);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
