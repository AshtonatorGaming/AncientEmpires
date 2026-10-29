package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.item.CoinPurseItem;
import com.ancientempires.item.Coins;
import com.ancientempires.item.OrderItem;
import com.ancientempires.item.TradeOrderItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's trader (Ancient Warfare's POTrade): a Worker-path NPC with a Trade
 * Order sells its order's goods from its own backpack. A trade's goods must be
 * in the backpack, and what the customer pays goes into it (coins into a purse
 * there, else as coins); nothing is made from thin air, as AW's player-owned
 * traders worked. On its round it banks its takings and restocks the goods it
 * sells ({@link NpcCourierGoal}).
 */
public final class NpcShop
{
    /** Uses shown at most, so a deep store does not read as a number to count. */
    private static final int MOST_SHOWN = 99;

    private NpcShop()
    {
    }

    /** The Trade Order it trades by: in the main hand, else in the orders slot (empty for none). */
    public static ItemStack order(NpcEntity npc)
    {
        ItemStack hand = npc.getMainHandItem();
        if (hand.getItem() instanceof TradeOrderItem)
        {
            return hand;
        }
        ItemStack slot = npc.getInventory().ordersItem();
        return slot.getItem() instanceof TradeOrderItem ? slot : ItemStack.EMPTY;
    }

    /** Whether the NPC keeps a player's shop: owned, on the Worker path, with a Trade Order. */
    public static boolean isShop(NpcEntity npc)
    {
        return npc.isOwnedNpc() && npc.getNpcPath() == NpcPath.WORKER && !order(npc).isEmpty();
    }

    private static List<TradeOrderItem.Goods> goods(NpcEntity npc)
    {
        List<TradeOrderItem.Goods> valid = new ArrayList<>();
        for (TradeOrderItem.Goods trade : TradeOrderItem.goods(order(npc)))
        {
            if (trade.valid())
            {
                valid.add(trade);
            }
        }
        return valid;
    }

    /** The backpack's stacks, copied, for working out a trade before it is made. */
    private static List<ItemStack> pack(NpcEntity npc)
    {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            stacks.add(npc.getInventory().getItem(slot).copy());
        }
        return stacks;
    }

    /** The trades as the trade screen shows them; the stock is how many times the backpack can give the goods (max -1: no fixed stock). */
    public static List<NpcTrades.View> views(NpcEntity npc)
    {
        List<ItemStack> pack = pack(npc);
        List<NpcTrades.View> views = new ArrayList<>();
        for (TradeOrderItem.Goods trade : goods(npc))
        {
            views.add(new NpcTrades.View(trade.input(), trade.output(), uses(pack, trade.output()), -1));
        }
        return views;
    }

    /** How many times {@code pack} can give {@code output}. */
    static int uses(List<ItemStack> pack, List<ItemStack> output)
    {
        int uses = MOST_SHOWN;
        for (ItemStack wanted : NpcTrades.compact(output))
        {
            if (Coins.isCoin(wanted))
            {
                continue;
            }
            uses = Math.min(uses, count(pack, wanted) / wanted.getCount());
        }
        long price = Coins.worth(output);
        if (price > 0L)
        {
            uses = (int) Math.min(uses, wealth(pack) / price);
        }
        return uses;
    }

    /**
     * Makes trade {@code index} for {@code player}: the customer pays, the
     * trader gives from its backpack, and the payment goes into it. Refused
     * when the customer cannot pay, the goods are not in the backpack, or the
     * payment does not fit.
     */
    public static boolean perform(NpcEntity npc, Player player, int index)
    {
        List<TradeOrderItem.Goods> goods = goods(npc);
        if (index < 0 || index >= goods.size())
        {
            return false;
        }
        TradeOrderItem.Goods trade = goods.get(index);
        List<ItemStack> pack = pack(npc);
        if (!NpcTrades.canPay(player, trade.input()) || uses(pack, trade.output()) <= 0)
        {
            return false;
        }
        // Worked out on a copy of the backpack first: the goods out, then the payment in.
        for (ItemStack wanted : NpcTrades.compact(trade.output()))
        {
            if (!Coins.isCoin(wanted))
            {
                take(pack, wanted, wanted.getCount());
            }
        }
        if (!pay(pack, Coins.worth(trade.output())))
        {
            return false;
        }
        for (ItemStack stack : trade.input())
        {
            if (!Coins.isCoin(stack) && !insert(pack, stack.copy()))
            {
                return false;
            }
        }
        if (!receive(pack, Coins.worth(trade.input())))
        {
            return false;
        }
        // It fits: the customer pays, the backpack takes the copy's contents, and the goods are handed over.
        NpcTrades.takeFrom(player, trade.input());
        for (int i = 0; i < pack.size(); i++)
        {
            npc.getInventory().setItem(NpcInventory.BACKPACK_START + i, pack.get(i));
        }
        NpcTrades.giveTo(player, trade.output());
        return true;
    }

    /**
     * Banks the takings at a container: every backpack stack that is not a good
     * it sells (coins and purses always), orders excepted. Returns the items moved.
     */
    static int bank(NpcEntity npc, IItemHandler container)
    {
        List<TradeOrderItem.Goods> goods = goods(npc);
        int moved = 0;
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack held = npc.getInventory().getItem(slot);
            boolean money = Coins.isCoin(held) || held.getItem() instanceof CoinPurseItem;
            if (held.isEmpty() || held.getItem() instanceof OrderItem || !money && sells(goods, held))
            {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItemStacked(container, held.copy(), false);
            moved += held.getCount() - rest.getCount();
            npc.getInventory().setItem(slot, rest);
        }
        return moved;
    }

    /**
     * Restocks at a container: of each good it sells, enough for
     * {@code trade.restockUses} trades, and coins enough to buy that many times,
     * as far as the container has them and the backpack has room. Returns the items moved.
     */
    static int restock(NpcEntity npc, IItemHandler container)
    {
        int moved = 0;
        long coinsWanted = 0L;
        List<ItemStack> wanted = new ArrayList<>();
        for (TradeOrderItem.Goods trade : goods(npc))
        {
            coinsWanted += Coins.worth(trade.output()) * AncientEmpiresConfig.tradeRestockUses;
            for (ItemStack stack : NpcTrades.compact(trade.output()))
            {
                if (!Coins.isCoin(stack))
                {
                    wanted.add(stack.copyWithCount(stack.getCount() * AncientEmpiresConfig.tradeRestockUses));
                }
            }
        }
        for (ItemStack want : NpcTrades.compact(wanted))
        {
            int missing = want.getCount() - count(pack(npc), want);
            for (int slot = 0; slot < container.getSlots() && missing > 0; slot++)
            {
                if (!ItemStack.isSameItemSameTags(container.getStackInSlot(slot), want))
                {
                    continue;
                }
                int fits = withdraw(npc, container, slot, missing);
                moved += fits;
                missing -= fits;
                if (fits == 0)
                {
                    break;
                }
            }
        }
        long coinsShort = coinsWanted - wealth(pack(npc));
        for (int slot = 0; slot < container.getSlots() && coinsShort > 0L; slot++)
        {
            long value = Coins.value(container.getStackInSlot(slot).getItem());
            if (value > 0L)
            {
                int fits = withdraw(npc, container, slot, (int) Math.min(64L, (coinsShort + value - 1L) / value));
                moved += fits;
                coinsShort -= fits * value;
            }
        }
        return moved;
    }

    /** Moves up to {@code amount} from a container slot into the backpack's room; returns how many moved. */
    private static int withdraw(NpcEntity npc, IItemHandler container, int slot, int amount)
    {
        ItemStack taken = container.extractItem(slot, amount, true);
        ItemStack rest = npc.getInventory().addToBackpack(taken);
        int fits = taken.getCount() - rest.getCount();
        if (fits > 0)
        {
            container.extractItem(slot, fits, false);
        }
        return fits;
    }

    private static boolean sells(List<TradeOrderItem.Goods> goods, ItemStack stack)
    {
        for (TradeOrderItem.Goods trade : goods)
        {
            for (ItemStack out : trade.output())
            {
                if (ItemStack.isSameItemSameTags(out, stack))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static int count(List<ItemStack> pack, ItemStack wanted)
    {
        int total = 0;
        for (ItemStack stack : pack)
        {
            if (ItemStack.isSameItemSameTags(stack, wanted))
            {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void take(List<ItemStack> pack, ItemStack wanted, int amount)
    {
        for (ItemStack stack : pack)
        {
            if (amount > 0 && ItemStack.isSameItemSameTags(stack, wanted))
            {
                int taken = Math.min(amount, stack.getCount());
                stack.shrink(taken);
                amount -= taken;
            }
        }
    }

    /** Purses and loose coins in the pack, in copper. */
    private static long wealth(List<ItemStack> pack)
    {
        long total = 0L;
        for (ItemStack stack : pack)
        {
            total += stack.getItem() instanceof CoinPurseItem ? CoinPurseItem.held(stack) : Coins.value(stack.getItem()) * stack.getCount();
        }
        return total;
    }

    /** Takes {@code amount} copper from the pack, purses first, then coins smallest first, change back in; false if short or the change will not fit. */
    private static boolean pay(List<ItemStack> pack, long amount)
    {
        if (amount <= 0L)
        {
            return true;
        }
        if (wealth(pack) < amount)
        {
            return false;
        }
        for (ItemStack stack : pack)
        {
            if (amount > 0L && stack.getItem() instanceof CoinPurseItem)
            {
                long taken = Math.min(amount, CoinPurseItem.held(stack));
                CoinPurseItem.set(stack, CoinPurseItem.held(stack) - taken);
                amount -= taken;
            }
        }
        while (amount > 0L)
        {
            // The smallest coin left in the pack.
            ItemStack smallest = ItemStack.EMPTY;
            for (ItemStack stack : pack)
            {
                long value = Coins.value(stack.getItem());
                if (value > 0L && !stack.isEmpty() && (smallest.isEmpty() || value < Coins.value(smallest.getItem())))
                {
                    smallest = stack;
                }
            }
            amount -= Coins.value(smallest.getItem());
            smallest.shrink(1);
        }
        return receive(pack, -amount);
    }

    /** Puts {@code amount} copper in the pack: into its first purse, else as coins; false if they will not fit. */
    private static boolean receive(List<ItemStack> pack, long amount)
    {
        if (amount <= 0L)
        {
            return true;
        }
        for (ItemStack stack : pack)
        {
            if (stack.getItem() instanceof CoinPurseItem)
            {
                CoinPurseItem.set(stack, CoinPurseItem.held(stack) + amount);
                return true;
            }
        }
        for (ItemStack coin : Coins.asCoins(amount))
        {
            if (!insert(pack, coin))
            {
                return false;
            }
        }
        return true;
    }

    /** Puts a stack in the pack, matching stacks first, then empty slots; false if it does not all fit. */
    private static boolean insert(List<ItemStack> pack, ItemStack stack)
    {
        for (ItemStack held : pack)
        {
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(held, stack) && held.getCount() < held.getMaxStackSize())
            {
                int move = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(move);
                stack.shrink(move);
            }
        }
        for (int i = 0; i < pack.size() && !stack.isEmpty(); i++)
        {
            if (pack.get(i).isEmpty())
            {
                pack.set(i, stack.copy());
                stack.setCount(0);
            }
        }
        return stack.isEmpty();
    }
}
