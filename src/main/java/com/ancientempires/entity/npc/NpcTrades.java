package com.ancientempires.entity.npc;

import com.ancientempires.item.Coins;
import com.ancientempires.item.TradeOrderItem;
import com.ancientempires.structure.AeRemap;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A trader's goods, as Ancient Warfare's faction traders had them
 * (FactionTrade): each trade takes up to nine stacks from the player and gives
 * up to nine, a few times before it runs out, one use coming back every
 * {@code refill} ticks (0: never runs out). Trades come with the trader from a
 * structure template (AW's {@code tradeList}); the faction's {@code trade}
 * standing change is applied for each one made.
 */
public final class NpcTrades
{
    private static final String KEY = "AeTrades";
    /** Stacks a side of one trade can hold (AW's nine). */
    public static final int SIDE = 9;
    /** AW's default refill (five minutes) and stock. */
    private static final int DEFAULT_REFILL = 6000;

    /** One trade: what the player gives, what the trader gives, and how many are left. */
    public static final class Trade
    {
        final List<ItemStack> input = new ArrayList<>();
        final List<ItemStack> output = new ArrayList<>();
        int refill = DEFAULT_REFILL;
        int max = 1;
        int available = 1;
        /** Game time the next use comes back; -1 to start counting, 0 for full. */
        long refillAt = -1L;

        public List<ItemStack> input()
        {
            return Collections.unmodifiableList(this.input);
        }

        public List<ItemStack> output()
        {
            return Collections.unmodifiableList(this.output);
        }

        public int available()
        {
            return this.available;
        }

        public int max()
        {
            return this.max;
        }

        /** AW's refill: every use due since the last is restored, up to the stock. */
        void update(long now)
        {
            if (this.refill <= 0)
            {
                this.available = this.max;
                return;
            }
            if (this.refillAt < 0L)
            {
                this.refillAt = now + this.refill;
            }
            if (this.refillAt > 0L && this.refillAt <= now)
            {
                for (long behind = now - this.refillAt; this.available < this.max && behind >= 0L; behind -= this.refill)
                {
                    this.available++;
                }
                this.refillAt = this.available < this.max ? now + this.refill : 0L;
            }
        }

        boolean valid()
        {
            return !this.input.isEmpty() && !this.output.isEmpty();
        }
    }

    private final List<Trade> trades = new ArrayList<>();

    public boolean isEmpty()
    {
        return this.trades.isEmpty();
    }

    public List<Trade> list()
    {
        return Collections.unmodifiableList(this.trades);
    }

    /**
     * Replaces the goods (AW's creative trade editor, through a Trade Order): a
     * trade keeps the stock and refill of the one it replaces, a new one AW's
     * default (one use, back every five minutes).
     */
    public void setGoods(List<TradeOrderItem.Goods> goods)
    {
        List<Trade> old = new ArrayList<>(this.trades);
        this.trades.clear();
        for (int i = 0; i < goods.size(); i++)
        {
            TradeOrderItem.Goods entry = goods.get(i);
            if (!entry.valid())
            {
                continue;
            }
            Trade trade = new Trade();
            for (ItemStack stack : entry.input())
            {
                trade.input.add(stack.copy());
            }
            for (ItemStack stack : entry.output())
            {
                trade.output.add(stack.copy());
            }
            if (i < old.size())
            {
                trade.refill = old.get(i).refill;
                trade.max = old.get(i).max;
                trade.available = Math.min(trade.max, old.get(i).available);
            }
            this.trades.add(trade);
        }
    }

    /** Brings every trade's stock up to date (AW did this when the trade screen opened). */
    public void update(long now)
    {
        for (Trade trade : this.trades)
        {
            trade.update(now);
        }
    }

    /**
     * Makes trade {@code index} for {@code player} if it has stock left and the
     * player carries everything asked: takes the input, gives the output
     * (dropped at the player's feet if it does not fit).
     */
    public boolean perform(Player player, int index, long now)
    {
        if (index < 0 || index >= this.trades.size())
        {
            return false;
        }
        Trade trade = this.trades.get(index);
        trade.update(now);
        if (trade.available <= 0)
        {
            return false;
        }
        if (!canPay(player, trade.input))
        {
            return false;
        }
        takeFrom(player, trade.input);
        giveTo(player, trade.output);
        if (trade.refill > 0)
        {
            trade.available--;
            if (trade.refillAt <= 0L)
            {
                // A refill already on its way keeps its time: restarting it on every sale meant a busy trade never refilled.
                trade.refillAt = now + trade.refill;
            }
        }
        return true;
    }

    /** Takes a trade's price from the player (check {@link #canPay} first): the goods, then the coins by worth. */
    static void takeFrom(Player player, List<ItemStack> input)
    {
        for (ItemStack stack : compact(input))
        {
            if (!Coins.isCoin(stack))
            {
                take(player, stack, stack.getCount());
            }
        }
        Coins.pay(player, Coins.worth(input));
    }

    /** Hands a trade's goods to the player: coins into its purse, goods (barter) into the inventory or at its feet. */
    static void giveTo(Player player, List<ItemStack> output)
    {
        long earned = Coins.worth(output);
        if (earned > 0L)
        {
            Coins.receive(player, earned);
        }
        for (ItemStack stack : output)
        {
            if (Coins.isCoin(stack))
            {
                continue;
            }
            ItemStack given = stack.copy();
            if (!player.getInventory().add(given) && !given.isEmpty())
            {
                player.drop(given, false);
            }
        }
    }

    /**
     * Whether the player can pay {@code input}: the goods asked, counted in its
     * inventory, and any coins asked by worth against its purses and loose coins
     * together. The trade screen asks the same.
     */
    public static boolean canPay(Player player, List<ItemStack> input)
    {
        for (ItemStack stack : compact(input))
        {
            if (!Coins.isCoin(stack) && count(player, stack) < stack.getCount())
            {
                return false;
            }
        }
        return Coins.wealth(player) >= Coins.worth(input);
    }

    /** Same items asked in several slots counted together, so one stack is not counted twice. */
    static List<ItemStack> compact(List<ItemStack> stacks)
    {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : stacks)
        {
            boolean added = false;
            for (ItemStack into : merged)
            {
                if (ItemStack.isSameItemSameTags(into, stack))
                {
                    into.grow(stack.getCount());
                    added = true;
                    break;
                }
            }
            if (!added)
            {
                merged.add(stack.copy());
            }
        }
        return merged;
    }

    private static int count(Player player, ItemStack wanted)
    {
        int total = 0;
        for (ItemStack stack : player.getInventory().items)
        {
            if (ItemStack.isSameItemSameTags(stack, wanted))
            {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void take(Player player, ItemStack wanted, int amount)
    {
        NonNullList<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size() && amount > 0; i++)
        {
            ItemStack stack = items.get(i);
            if (ItemStack.isSameItemSameTags(stack, wanted))
            {
                int taken = Math.min(amount, stack.getCount());
                stack.shrink(taken);
                amount -= taken;
            }
        }
        player.getInventory().setChanged();
    }

    /**
     * Reads AW's {@code tradeList} from a template's trader: its item stacks
     * (already upgraded where vanilla) resolved here, AW's coins by metal.
     */
    public void loadAw(CompoundTag tradeList)
    {
        this.trades.clear();
        for (Tag entry : tradeList.getList("tradeList", Tag.TAG_COMPOUND))
        {
            CompoundTag tag = (CompoundTag) entry;
            Trade trade = new Trade();
            readAwSide(tag.getList("inputItems", Tag.TAG_COMPOUND), trade.input);
            readAwSide(tag.getList("outputItems", Tag.TAG_COMPOUND), trade.output);
            trade.refill = tag.getInt("refillFrequency");
            trade.max = Math.max(1, tag.getInt("maxAvailable"));
            trade.available = Math.min(trade.max, Math.max(0, tag.getInt("currentAvailable")));
            if (trade.valid())
            {
                this.trades.add(trade);
            }
        }
    }

    private static void readAwSide(ListTag list, List<ItemStack> into)
    {
        for (Tag entry : list)
        {
            ItemStack stack = awStack((CompoundTag) entry);
            if (!stack.isEmpty() && into.size() < SIDE)
            {
                into.add(stack);
            }
        }
    }

    /** One AW item stack here: AW's coin by its metal, anything else through the remap tables. */
    static ItemStack awStack(CompoundTag tag)
    {
        CompoundTag copy = tag.copy();
        copy.remove("slot");
        String id = copy.getString("id");
        if (id.equals("ancientwarfarenpc:coin"))
        {
            String metal = copy.getCompound("tag").getString("metal");
            copy.putString("id", "ancientempires:coin_" + (metal.isEmpty() ? "copper" : metal));
            copy.remove("tag");
        }
        else
        {
            copy.putString("id", AeRemap.itemId(id));
        }
        return ItemStack.of(copy);
    }

    public void save(CompoundTag tag)
    {
        if (this.trades.isEmpty())
        {
            return;
        }
        ListTag list = new ListTag();
        for (Trade trade : this.trades)
        {
            CompoundTag entry = new CompoundTag();
            entry.put("In", stacks(trade.input));
            entry.put("Out", stacks(trade.output));
            entry.putInt("Refill", trade.refill);
            entry.putInt("Max", trade.max);
            entry.putInt("Available", trade.available);
            entry.putLong("RefillAt", trade.refillAt);
            list.add(entry);
        }
        tag.put(KEY, list);
    }

    public void load(CompoundTag tag)
    {
        this.trades.clear();
        for (Tag entry : tag.getList(KEY, Tag.TAG_COMPOUND))
        {
            CompoundTag saved = (CompoundTag) entry;
            Trade trade = new Trade();
            readStacks(saved.getList("In", Tag.TAG_COMPOUND), trade.input);
            readStacks(saved.getList("Out", Tag.TAG_COMPOUND), trade.output);
            trade.refill = saved.getInt("Refill");
            trade.max = Math.max(1, saved.getInt("Max"));
            trade.available = saved.getInt("Available");
            trade.refillAt = saved.getLong("RefillAt");
            if (trade.valid())
            {
                this.trades.add(trade);
            }
        }
    }

    private static ListTag stacks(List<ItemStack> stacks)
    {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks)
        {
            list.add(stack.save(new CompoundTag()));
        }
        return list;
    }

    private static void readStacks(ListTag list, List<ItemStack> into)
    {
        for (Tag entry : list)
        {
            ItemStack stack = ItemStack.of((CompoundTag) entry);
            if (!stack.isEmpty())
            {
                into.add(stack);
            }
        }
    }

    /** The trades as the trade screen shows them: each side's stacks, and the uses left of the stock. */
    public record View(List<ItemStack> input, List<ItemStack> output, int available, int max)
    {
        public static void write(FriendlyByteBuf buffer, View view)
        {
            buffer.writeCollection(view.input, FriendlyByteBuf::writeItem);
            buffer.writeCollection(view.output, FriendlyByteBuf::writeItem);
            buffer.writeVarInt(view.available);
            buffer.writeVarInt(view.max);
        }

        public static View read(FriendlyByteBuf buffer)
        {
            return new View(buffer.readList(FriendlyByteBuf::readItem), buffer.readList(FriendlyByteBuf::readItem), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public List<View> views()
    {
        List<View> views = new ArrayList<>();
        for (Trade trade : this.trades)
        {
            views.add(new View(trade.input(), trade.output(), trade.available, trade.max));
        }
        return views;
    }
}
