package com.ancientempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A structure under construction (AW's structure builder), placed by a
 * Blueprint. Click to open its store and fill it with materials; sneak-click
 * empty-handed for the layer it is on and what that layer still lacks. A
 * Worker-path NPC holding a shovel builds it a layer at a time.
 */
public final class StructureBuilderBlock extends WorksiteBlock
{
    /** Kinds of missing item named in the status, at most. */
    private static final int SHOWN = 6;

    public StructureBuilderBlock(Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new StructureBuilderBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()
                || !(level.getBlockEntity(pos) instanceof StructureBuilderBlockEntity builder))
        {
            return super.use(state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide)
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.structure_builder.status", builder.templateName(),
                    builder.layer() + 1, builder.layers()), false);
            Map<Item, Integer> missing = builder.missing();
            if (missing.isEmpty())
            {
                player.displayClientMessage(Component.translatable("message.ancientempires.structure_builder.ready"), false);
            }
            missing.entrySet().stream().limit(SHOWN).forEach(entry -> player.displayClientMessage(
                    Component.translatable("message.ancientempires.structure_builder.needs", entry.getValue(), entry.getKey().getDescription()), false));
            if (missing.size() > SHOWN)
            {
                player.displayClientMessage(Component.translatable("message.ancientempires.structure_builder.more", missing.size() - SHOWN), false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
