package com.ancientempires.gate;

import com.ancientempires.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * AW's Gate Spawner, one item per gate type. Right-click a block to mark the
 * first bottom corner of the opening (the cell in front of the face clicked),
 * again for the opposite top corner (same x or same z: a gate is one block
 * thick), and once more to build it; the opening must be empty. The gate faces
 * away from the builder (a drawbridge falls that way) and belongs to them.
 * Sneak-use clears the marks.
 */
public class GateSpawnerItem extends Item
{
    private static final String TAG = "AeGate";
    private final GateType type;

    public GateSpawnerItem(GateType type, Properties properties)
    {
        super(properties);
        this.type = type;
    }

    public GateType type()
    {
        return this.type;
    }

    public static Item itemFor(GateType type)
    {
        return ModItems.GATE_SPAWNERS.get(type.ordinal()).get();
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown())
        {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        CompoundTag marks = stack.getOrCreateTagElement(TAG);
        BlockPos at = context.getClickedPos().relative(context.getClickedFace());
        if (!marks.contains("Pos1"))
        {
            marks.putLong("Pos1", at.asLong());
            player.displayClientMessage(Component.translatable("message.ancientempires.gate.pos1"), true);
            return InteractionResult.CONSUME;
        }
        if (!marks.contains("Pos2"))
        {
            BlockPos pos1 = BlockPos.of(marks.getLong("Pos1"));
            if (pos1.getX() != at.getX() && pos1.getZ() != at.getZ())
            {
                player.displayClientMessage(Component.translatable("message.ancientempires.gate.invalid"), true);
                return InteractionResult.FAIL;
            }
            marks.putLong("Pos2", at.asLong());
            player.displayClientMessage(Component.translatable("message.ancientempires.gate.pos2"), true);
            return InteractionResult.CONSUME;
        }
        return this.build(context.getLevel(), player, stack) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown())
        {
            stack.removeTagKey(TAG);
            if (!level.isClientSide)
            {
                player.displayClientMessage(Component.translatable("message.ancientempires.gate.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        CompoundTag marks = stack.getTagElement(TAG);
        if (marks != null && marks.contains("Pos2"))
        {
            if (!level.isClientSide)
            {
                this.build(level, player, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private boolean build(Level level, Player player, ItemStack stack)
    {
        CompoundTag marks = stack.getOrCreateTagElement(TAG);
        BlockPos pos1 = BlockPos.of(marks.getLong("Pos1"));
        BlockPos pos2 = BlockPos.of(marks.getLong("Pos2"));
        for (BlockPos pos : BlockPos.betweenClosed(pos1, pos2))
        {
            if (!level.getBlockState(pos).canBeReplaced())
            {
                player.displayClientMessage(Component.translatable("message.ancientempires.gate.not_clear"), true);
                return false;
            }
        }
        GateEntity gate = GateEntity.create(level, pos1, pos2, this.type, player.getDirection(), player.getUUID());
        if (gate == null)
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.gate.invalid"), true);
            return false;
        }
        level.addFreshEntity(gate);
        stack.removeTagKey(TAG);
        if (!player.isCreative())
        {
            stack.shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.ancientempires.gate.built"), true);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.ancientempires.gate_spawner.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
