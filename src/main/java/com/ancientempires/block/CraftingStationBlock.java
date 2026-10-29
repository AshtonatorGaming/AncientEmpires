package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The crafting station worksite (AW's auto crafting station). Worker-path NPCs
 * holding a crafting table craft at it. Sneak-click with an item to have it
 * make that item; sneak-click empty-handed to clear the recipe. A plain click
 * opens its store.
 */
public final class CraftingStationBlock extends WorksiteBlock
{
    public CraftingStationBlock(Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CraftingStationBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof CraftingStationBlockEntity station))
        {
            return super.use(state, level, pos, player, hand, hit);
        }
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        if (!mayManage(player, station))
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.farm.not_owner"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty())
        {
            station.clearRecipe();
            player.displayClientMessage(Component.translatable("message.ancientempires.crafting_station.cleared"), true);
            return InteractionResult.CONSUME;
        }
        Optional<CraftingRecipe> recipe = station.setRecipeFor(held);
        player.displayClientMessage(recipe.isPresent()
                ? Component.translatable("message.ancientempires.crafting_station.makes", recipe.get().getResultItem(level.registryAccess()).getHoverName())
                : Component.translatable("message.ancientempires.crafting_station.no_recipe", held.getHoverName()), true);
        return InteractionResult.CONSUME;
    }
}
