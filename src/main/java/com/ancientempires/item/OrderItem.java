package com.ancientempires.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ancient Warfare's orders (Routing, Work, Upkeep, Combat, Trade): a written
 * list of stops in the world an NPC acts on. One base for all of them: use the
 * order on a block to add it as a stop; on a stop again to cycle what is done
 * there; sneaking to strike it off. The item in the player's off hand when
 * clicking becomes the stop's filter (empty: none). An NPC reads its orders
 * from what it holds or carries; each kind says which blocks it takes and what
 * its modes mean.
 */
public abstract class OrderItem extends Item
{
    private static final String STOPS = "AeStops";
    /** Most stops one order holds. */
    public static final int MAX_STOPS = 16;

    /** One stop: where, what is done there (a mode of the order's kind), and the filter item. */
    public record Stop(BlockPos pos, String dimension, int mode, ItemStack filter)
    {
        CompoundTag save()
        {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", this.pos.asLong());
            tag.putString("Dim", this.dimension);
            tag.putInt("Mode", this.mode);
            if (!this.filter.isEmpty())
            {
                tag.put("Filter", this.filter.save(new CompoundTag()));
            }
            return tag;
        }

        static Stop load(CompoundTag tag)
        {
            return new Stop(BlockPos.of(tag.getLong("Pos")), tag.getString("Dim"), tag.getInt("Mode"), ItemStack.of(tag.getCompound("Filter")));
        }
    }

    protected OrderItem(Properties properties)
    {
        super(properties.stacksTo(1));
    }

    /** How many modes a stop of this order cycles through. */
    protected abstract int modes();

    /** A mode's name, for messages and the tooltip. */
    protected abstract Component modeName(int mode);

    /** Whether a block can be a stop of this order (a container, a worksite...). */
    protected abstract boolean accepts(Level level, BlockPos pos);

    /** Most stops this kind of order holds (an upkeep order names one place). */
    protected int maxStops()
    {
        return MAX_STOPS;
    }

    public static List<Stop> stops(ItemStack order)
    {
        List<Stop> stops = new ArrayList<>();
        if (order.hasTag())
        {
            for (Tag entry : order.getTag().getList(STOPS, Tag.TAG_COMPOUND))
            {
                stops.add(Stop.load((CompoundTag) entry));
            }
        }
        return stops;
    }

    /** Adds a stop to {@code order} as a click would (tests, commands). */
    public static void addStop(ItemStack order, BlockPos pos, String dimension, int mode, ItemStack filter)
    {
        List<Stop> stops = stops(order);
        stops.add(new Stop(pos.immutable(), dimension, mode, filter));
        write(order, stops);
    }

    private static void write(ItemStack order, List<Stop> stops)
    {
        ListTag list = new ListTag();
        for (Stop stop : stops)
        {
            list.add(stop.save());
        }
        order.getOrCreateTag().put(STOPS, list);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null || !this.accepts(level, pos))
        {
            return InteractionResult.PASS;
        }
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        ItemStack order = context.getItemInHand();
        String dimension = level.dimension().location().toString();
        ItemStack filter = player.getItemInHand(InteractionHand.OFF_HAND).copyWithCount(1);
        List<Stop> stops = stops(order);
        int at = -1;
        for (int i = 0; i < stops.size(); i++)
        {
            if (stops.get(i).pos().equals(pos) && stops.get(i).dimension().equals(dimension))
            {
                at = i;
            }
        }
        if (at >= 0 && player.isShiftKeyDown())
        {
            stops.remove(at);
            player.displayClientMessage(Component.translatable("item.ancientempires.order.removed", at + 1), true);
        }
        else if (at >= 0)
        {
            Stop stop = stops.get(at);
            Stop next = new Stop(pos, dimension, (stop.mode() + 1) % this.modes(), filter);
            stops.set(at, next);
            player.displayClientMessage(describe(at, next, this), true);
        }
        else if (stops.size() >= this.maxStops())
        {
            player.displayClientMessage(Component.translatable("item.ancientempires.order.full", this.maxStops()), true);
            return InteractionResult.CONSUME;
        }
        else
        {
            Stop added = new Stop(pos.immutable(), dimension, 0, filter);
            stops.add(added);
            player.displayClientMessage(describe(stops.size() - 1, added, this), true);
        }
        write(order, stops);
        return InteractionResult.CONSUME;
    }

    /** Each stop of {@code order} as a line ("1. 120, 64, -30: take everything (any item)"), for screens. */
    public static List<Component> lines(ItemStack order)
    {
        List<Component> lines = new ArrayList<>();
        if (order.getItem() instanceof OrderItem kind)
        {
            List<Stop> stops = stops(order);
            for (int i = 0; i < stops.size(); i++)
            {
                lines.add(describe(i, stops.get(i), kind));
            }
        }
        return lines;
    }

    private static Component describe(int index, Stop stop, OrderItem kind)
    {
        Component filter = stop.filter().isEmpty() ? Component.translatable("item.ancientempires.order.any") : stop.filter().getHoverName();
        return Component.translatable("item.ancientempires.order.stop", index + 1, stop.pos().toShortString(), kind.modeName(stop.mode()), filter);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        List<Stop> stops = stops(stack);
        if (stops.isEmpty())
        {
            tooltip.add(Component.translatable("item.ancientempires.order.empty").withStyle(ChatFormatting.GRAY));
        }
        for (int i = 0; i < Math.min(stops.size(), 8); i++)
        {
            tooltip.add(describe(i, stops.get(i), this).copy().withStyle(ChatFormatting.GRAY));
        }
        if (stops.size() > 8)
        {
            tooltip.add(Component.translatable("item.ancientempires.order.more", stops.size() - 8).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.ancientempires.order.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
