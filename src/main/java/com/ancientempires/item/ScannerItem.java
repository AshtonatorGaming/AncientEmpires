package com.ancientempires.item;

import com.ancientempires.client.ClientScreens;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * AW's Structure Scanner (admins): right-click two corners of a building, then
 * sneak-right-click the block a player would stand on to enter it (the build
 * key; the way you face is the front). Right-click the air to name and save it
 * ({@link com.ancientempires.structure.StructureScanner}); sneak-use in the air
 * clears the selection.
 */
public class ScannerItem extends Item
{
    public ScannerItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        if (player == null || !player.isCreative())
        {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide)
        {
            CompoundTag tag = context.getItemInHand().getOrCreateTag();
            BlockPos pos = context.getClickedPos();
            if (player.isSecondaryUseActive())
            {
                tag.put("Key", NbtUtils.writeBlockPos(pos.above()));
                tag.putString("Facing", player.getDirection().getName());
                player.displayClientMessage(Component.translatable("message.ancientempires.scanner.key", pos.above().toShortString(),
                        player.getDirection().getName()), true);
            }
            else
            {
                boolean second = tag.getBoolean("NextIsSecond");
                tag.put(second ? "Pos2" : "Pos1", NbtUtils.writeBlockPos(pos));
                tag.putBoolean("NextIsSecond", !second);
                player.displayClientMessage(Component.translatable(second ? "message.ancientempires.scanner.pos2" : "message.ancientempires.scanner.pos1",
                        pos.toShortString()), true);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCreative())
        {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isSecondaryUseActive())
        {
            if (!level.isClientSide)
            {
                stack.setTag(null);
                player.displayClientMessage(Component.translatable("message.ancientempires.scanner.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!ready(stack))
        {
            if (!level.isClientSide)
            {
                player.displayClientMessage(Component.translatable("message.ancientempires.scanner.not_ready"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide)
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientScreens.openScanner(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static boolean ready(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("Pos1") && tag.contains("Pos2") && tag.contains("Key");
    }

    public static BlockPos pos(ItemStack stack, String key)
    {
        return NbtUtils.readBlockPos(stack.getOrCreateTag().getCompound(key));
    }

    public static Direction facing(ItemStack stack)
    {
        Direction facing = Direction.byName(stack.getOrCreateTag().getString("Facing"));
        return facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.ancientempires.structure_scanner.help").withStyle(ChatFormatting.GRAY));
        CompoundTag tag = stack.getTag();
        if (tag != null)
        {
            for (String key : new String[] {"Pos1", "Pos2", "Key"})
            {
                if (tag.contains(key))
                {
                    tooltip.add(Component.literal(key + ": " + pos(stack, key).toShortString()).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
    }
}
