package com.ancientempires.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A coin purse: one sum in copper instead of stacks of four coins (Mount and
 * Blade's one money count, Millénaire's purse). Used, it takes in every loose
 * coin the player carries; used sneaking, it pours the sum back out as coins.
 * Traders draw from it and pay into it ({@link Coins}).
 */
public class CoinPurseItem extends Item
{
    private static final String KEY = "AeCopper";

    public CoinPurseItem(Properties properties)
    {
        super(properties);
    }

    public static long held(ItemStack purse)
    {
        return purse.hasTag() ? purse.getTag().getLong(KEY) : 0L;
    }

    public static void set(ItemStack purse, long copper)
    {
        purse.getOrCreateTag().putLong(KEY, Math.max(0L, copper));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack purse = player.getItemInHand(hand);
        if (!level.isClientSide)
        {
            if (player.isShiftKeyDown())
            {
                long sum = held(purse);
                set(purse, 0L);
                for (ItemStack coin : Coins.asCoins(sum))
                {
                    if (!player.getInventory().add(coin) && !coin.isEmpty())
                    {
                        player.drop(coin, false);
                    }
                }
            }
            else
            {
                long gathered = 0L;
                for (ItemStack stack : player.getInventory().items)
                {
                    long value = Coins.value(stack.getItem());
                    if (value > 0L)
                    {
                        gathered += value * stack.getCount();
                        stack.setCount(0);
                    }
                }
                set(purse, held(purse) + gathered);
            }
            player.displayClientMessage(Component.translatable("item.ancientempires.coin_purse.holds", Coins.describe(held(purse))), true);
        }
        return InteractionResultHolder.sidedSuccess(purse, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Coins.describe(held(stack)));
        tooltip.add(Component.translatable("item.ancientempires.coin_purse.hint"));
    }
}
