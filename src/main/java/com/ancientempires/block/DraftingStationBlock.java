package com.ancientempires.block;

import com.ancientempires.network.DraftingPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * AW's Drafting Station: click it to see the structures AW marked for survival
 * building and draft a Blueprint of one ({@code structures.blueprintPaper}
 * paper). Unlike AW's, the materials are not paid here but delivered to the
 * Structure Builder the Blueprint sets down, layer by layer, so couriers can
 * carry them.
 */
public final class DraftingStationBlock extends Block
{
    public DraftingStationBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer)
        {
            DraftingPacket.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
