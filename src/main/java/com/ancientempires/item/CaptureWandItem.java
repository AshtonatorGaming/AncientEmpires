package com.ancientempires.item;

import com.ancientempires.block.NpcSpawnerBlockEntity;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.registry.ModBlocks;
import com.ancientempires.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ancient Warfare's Mob Spawner Placement Tool, for admins building structures.
 * Right-click a mob to capture it (one of our NPCs as its scroll, with gear,
 * skin, name and level; anything else as a {@link MobScrollItem}), then
 * right-click a block to place a garrison spawner holding it, or a spawner to
 * add it to the spawner's slots (the same capture again adds one more).
 * Sneak-use in the air clears the capture. The spawner's settings are then
 * edited in its screen as usual.
 */
public class CaptureWandItem extends Item
{
    private static final String CAPTURED_KEY = "Captured";

    public CaptureWandItem(Properties properties)
    {
        super(properties);
    }

    @Nullable
    private static ItemStack captured(ItemStack wand)
    {
        if (!wand.hasTag() || !wand.getTag().contains(CAPTURED_KEY))
        {
            return null;
        }
        ItemStack stack = ItemStack.of(wand.getTag().getCompound(CAPTURED_KEY));
        return stack.isEmpty() ? null : stack;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack wand, Player player, LivingEntity target, InteractionHand hand)
    {
        if (player.level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        if (!NpcEntity.mayToggleCreative(player) || target instanceof Player)
        {
            return InteractionResult.PASS;
        }
        ItemStack scroll = target instanceof NpcEntity npc ? npc.captureScroll() : MobScrollItem.capture(ModItems.MOB_SCROLL.get(), target);
        if (scroll.isEmpty())
        {
            return InteractionResult.FAIL;
        }
        // The hand's own stack: interactLivingEntity may get a copy in creative.
        ItemStack held = player.getItemInHand(hand);
        held.getOrCreateTag().put(CAPTURED_KEY, scroll.save(new CompoundTag()));
        player.displayClientMessage(Component.translatable("message.ancientempires.capture_wand.captured", target.getName()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        ItemStack scroll = captured(context.getItemInHand());
        if (player == null || !NpcEntity.mayToggleCreative(player))
        {
            return InteractionResult.PASS;
        }
        if (scroll == null)
        {
            if (!level.isClientSide)
            {
                player.displayClientMessage(Component.translatable("message.ancientempires.capture_wand.empty"), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        if (level.getBlockEntity(clicked) instanceof NpcSpawnerBlockEntity spawner)
        {
            boolean added = add(spawner.slots(), scroll);
            player.displayClientMessage(Component.translatable(added ? "message.ancientempires.capture_wand.added"
                    : "message.ancientempires.capture_wand.full"), true);
            return added ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        BlockPlaceContext place = new BlockPlaceContext(context);
        if (!place.canPlace())
        {
            return InteractionResult.FAIL;
        }
        BlockPos at = place.getClickedPos();
        level.setBlock(at, ModBlocks.NPC_SPAWNER.get().defaultBlockState(), 3);
        if (level.getBlockEntity(at) instanceof NpcSpawnerBlockEntity spawner)
        {
            spawner.slots().setItem(0, scroll);
            spawner.makePost();
        }
        player.displayClientMessage(Component.translatable("message.ancientempires.capture_wand.placed"), true);
        return InteractionResult.CONSUME;
    }

    /** Into the first slot holding the same capture (one more of it) or else the first empty one. */
    private static boolean add(SimpleContainer slots, ItemStack scroll)
    {
        for (int slot = 0; slot < slots.getContainerSize(); slot++)
        {
            ItemStack there = slots.getItem(slot);
            if (ItemStack.isSameItemSameTags(there, scroll) && there.getCount() < there.getMaxStackSize())
            {
                there.grow(1);
                slots.setChanged();
                return true;
            }
        }
        for (int slot = 0; slot < slots.getContainerSize(); slot++)
        {
            if (slots.getItem(slot).isEmpty())
            {
                slots.setItem(slot, scroll);
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack wand = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && captured(wand) != null)
        {
            wand.getTag().remove(CAPTURED_KEY);
            if (!level.isClientSide)
            {
                player.displayClientMessage(Component.translatable("message.ancientempires.capture_wand.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(wand, level.isClientSide);
        }
        return InteractionResultHolder.pass(wand);
    }

    @Override
    public boolean isFoil(ItemStack stack)
    {
        return captured(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        ItemStack scroll = captured(stack);
        if (scroll != null)
        {
            tooltip.add(Component.translatable("item.ancientempires.capture_wand.holds", scroll.getHoverName()).withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("item.ancientempires.capture_wand.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
