package com.ancientempires.item;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Money: coins counted by worth in copper ({@code trade} config), and the
 * player's purse. A trade priced in coins is paid from the purses the player
 * carries first, then loose coins, change going back into a purse (or as
 * coins, biggest first, when there is none); coins a trader pays out go into
 * a purse when the player has one. Works the same on both sides, so the trade
 * screen can tell whether the player can pay.
 */
public final class Coins
{
    private Coins()
    {
    }

    /** The coin items, smallest first. */
    private static Item[] coins()
    {
        return new Item[] {ModItems.COIN_COPPER.get(), ModItems.COIN_SILVER.get(), ModItems.COIN_GOLD.get(), ModItems.COIN_ANCIENT.get()};
    }

    private static long[] values()
    {
        return new long[] {1L, AncientEmpiresConfig.tradeSilverValue, AncientEmpiresConfig.tradeGoldValue, AncientEmpiresConfig.tradeAncientValue};
    }

    /** One coin's worth in copper, or 0 for anything that is not a coin. */
    public static long value(Item item)
    {
        Item[] coins = coins();
        long[] values = values();
        for (int i = 0; i < coins.length; i++)
        {
            if (coins[i] == item)
            {
                return values[i];
            }
        }
        return 0L;
    }

    public static boolean isCoin(ItemStack stack)
    {
        return value(stack.getItem()) > 0L;
    }

    /** What a list of stacks is worth in coins (other items count nothing). */
    public static long worth(List<ItemStack> stacks)
    {
        long total = 0L;
        for (ItemStack stack : stacks)
        {
            total += value(stack.getItem()) * stack.getCount();
        }
        return total;
    }

    /** Everything the player has to spend: purses and loose coins. */
    public static long wealth(Player player)
    {
        long total = 0L;
        for (ItemStack stack : player.getInventory().items)
        {
            total += stack.getItem() instanceof CoinPurseItem ? CoinPurseItem.held(stack) : value(stack.getItem()) * stack.getCount();
        }
        return total;
    }

    /** Takes {@code amount} copper's worth from the player (call {@link #wealth} first): purses, then loose coins, with change. */
    public static void pay(Player player, long amount)
    {
        List<ItemStack> items = player.getInventory().items;
        for (ItemStack stack : items)
        {
            if (amount > 0L && stack.getItem() instanceof CoinPurseItem)
            {
                long taken = Math.min(amount, CoinPurseItem.held(stack));
                CoinPurseItem.set(stack, CoinPurseItem.held(stack) - taken);
                amount -= taken;
            }
        }
        Item[] coins = coins();
        long[] values = values();
        // Loose coins, smallest first, until the rest is covered; any overshoot comes back as change.
        for (int kind = 0; kind < coins.length && amount > 0L; kind++)
        {
            for (ItemStack stack : items)
            {
                while (amount > 0L && stack.getItem() == coins[kind] && !stack.isEmpty())
                {
                    stack.shrink(1);
                    amount -= values[kind];
                }
            }
        }
        if (amount < 0L)
        {
            receive(player, -amount);
        }
        player.getInventory().setChanged();
    }

    /** Gives the player {@code amount} copper's worth: into their first purse, else as coins, biggest first. */
    public static void receive(Player player, long amount)
    {
        for (ItemStack stack : player.getInventory().items)
        {
            if (stack.getItem() instanceof CoinPurseItem)
            {
                CoinPurseItem.set(stack, CoinPurseItem.held(stack) + amount);
                return;
            }
        }
        for (ItemStack coin : asCoins(amount))
        {
            if (!player.getInventory().add(coin) && !coin.isEmpty())
            {
                player.drop(coin, false);
            }
        }
    }

    /** {@code amount} copper as the fewest coins, biggest first. */
    public static List<ItemStack> asCoins(long amount)
    {
        List<ItemStack> stacks = new ArrayList<>();
        Item[] coins = coins();
        long[] values = values();
        for (int kind = coins.length - 1; kind >= 0 && amount > 0L; kind--)
        {
            long count = amount / values[kind];
            amount -= count * values[kind];
            while (count > 0L)
            {
                int stack = (int) Math.min(count, 64L);
                stacks.add(new ItemStack(coins[kind], stack));
                count -= stack;
            }
        }
        return stacks;
    }

    /** A sum the way a player reads it: "3 gold, 4 silver, 2 copper" (ancient included when there are any). */
    public static Component describe(long amount)
    {
        if (amount <= 0L)
        {
            return Component.translatable("item.ancientempires.coin_purse.empty");
        }
        String[] keys = {"copper", "silver", "gold", "ancient"};
        long[] values = values();
        List<Component> parts = new ArrayList<>();
        for (int kind = keys.length - 1; kind >= 0; kind--)
        {
            long count = amount / values[kind];
            amount -= count * values[kind];
            if (count > 0L)
            {
                parts.add(Component.translatable("item.ancientempires.coin_purse." + keys[kind], count));
            }
        }
        Component joined = parts.get(0);
        for (int i = 1; i < parts.size(); i++)
        {
            joined = Component.empty().append(joined).append(", ").append(parts.get(i));
        }
        return joined;
    }
}
