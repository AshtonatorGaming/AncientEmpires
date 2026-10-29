package com.ancientempires.item;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcTrades;
import com.ancientempires.inventory.TradeOrderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ancient Warfare's Trade Order (POTrade): a player's trader's goods and its
 * round. A Worker-path NPC holding it (main hand or orders slot) is a trader:
 * anyone not its owner trades with it by right-click (its owner sneaks), and it
 * sells from its own backpack: what a trade gives must be in there, and what it
 * takes goes in. Used in the air, the order opens its goods editor
 * ({@link TradeOrderMenu}); used on blocks, it lists the round: at a
 * container the trader banks its takings and restocks the goods it sells, and
 * at any stop it keeps shop a while ({@code trade.shopTicks}). An admin uses it
 * on a faction trader to give it these goods, sneaking to copy that trader's goods
 * into the order: AW's creative trade editor.
 */
public class TradeOrderItem extends OrderItem
{
    private static final String GOODS = "AeGoods";
    /** Most trades one order lists. */
    public static final int MAX_TRADES = 16;

    /** What the trader does at a stop. */
    public enum Mode
    {
        /** Bank the takings, restock, keep shop. */
        TRADE,
        /** Keep shop only (any block). */
        SHOP,
        /** Bank the takings and move on. */
        BANK,
        /** Restock and move on. */
        RESTOCK;

        public boolean banks()
        {
            return this == TRADE || this == BANK;
        }

        public boolean restocks()
        {
            return this == TRADE || this == RESTOCK;
        }

        public boolean keepsShop()
        {
            return this == TRADE || this == SHOP;
        }

        public static Mode of(int index)
        {
            Mode[] all = values();
            return all[Math.floorMod(index, all.length)];
        }
    }

    /** One trade: what the customer gives, and what the trader gives. */
    public record Goods(List<ItemStack> input, List<ItemStack> output)
    {
        public boolean valid()
        {
            return !this.input.isEmpty() && !this.output.isEmpty();
        }
    }

    public TradeOrderItem(Properties properties)
    {
        super(properties);
    }

    @Override
    protected int modes()
    {
        return Mode.values().length;
    }

    @Override
    protected Component modeName(int mode)
    {
        return Component.translatable("item.ancientempires.trade_order.mode." + Mode.of(mode).name().toLowerCase(Locale.ROOT));
    }

    /** Any block: a container to bank and restock at, or any spot to keep shop at. */
    @Override
    protected boolean accepts(Level level, BlockPos pos)
    {
        return !level.getBlockState(pos).isAir();
    }

    /** The order's trades, each side's empty stacks left out (empty sides kept, for the editor's pages). */
    public static List<Goods> goods(ItemStack order)
    {
        List<Goods> goods = new ArrayList<>();
        if (order.hasTag())
        {
            for (Tag entry : order.getTag().getList(GOODS, Tag.TAG_COMPOUND))
            {
                CompoundTag tag = (CompoundTag) entry;
                goods.add(new Goods(read(tag.getList("In", Tag.TAG_COMPOUND)), read(tag.getList("Out", Tag.TAG_COMPOUND))));
            }
        }
        return goods;
    }

    public static void setGoods(ItemStack order, List<Goods> goods)
    {
        ListTag list = new ListTag();
        for (Goods trade : goods.subList(0, Math.min(goods.size(), MAX_TRADES)))
        {
            CompoundTag tag = new CompoundTag();
            tag.put("In", write(trade.input()));
            tag.put("Out", write(trade.output()));
            list.add(tag);
        }
        order.getOrCreateTag().put(GOODS, list);
    }

    private static List<ItemStack> read(ListTag list)
    {
        List<ItemStack> stacks = new ArrayList<>();
        for (Tag entry : list)
        {
            ItemStack stack = ItemStack.of((CompoundTag) entry);
            if (!stack.isEmpty() && stacks.size() < NpcTrades.SIDE)
            {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static ListTag write(List<ItemStack> stacks)
    {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks)
        {
            if (!stack.isEmpty())
            {
                list.add(stack.save(new CompoundTag()));
            }
        }
        return list;
    }

    /** Opens the goods editor (not while pointing at a block: that adds a stop). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack order = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer)
        {
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider((id, inventory, opener) -> new TradeOrderMenu(id, inventory, hand),
                    order.getHoverName()), buffer -> buffer.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(order, level.isClientSide);
    }

    /**
     * AW's creative trade editor: an admin gives a faction trader this order's
     * goods, or, sneaking, copies the trader's goods into the order.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand)
    {
        if (!(target instanceof NpcEntity npc) || npc.getFaction().isEmpty() || !NpcEntity.mayToggleCreative(player))
        {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide)
        {
            // The stack passed in may be a copy in creative: write to the hand's own.
            ItemStack order = player.getItemInHand(hand);
            if (player.isShiftKeyDown())
            {
                List<Goods> copied = new ArrayList<>();
                for (NpcTrades.View view : npc.getTrades().views())
                {
                    copied.add(new Goods(new ArrayList<>(view.input()), new ArrayList<>(view.output())));
                }
                setGoods(order, copied);
                player.displayClientMessage(Component.translatable("item.ancientempires.trade_order.copied", copied.size(), npc.getDisplayName()), true);
            }
            else
            {
                List<Goods> goods = goods(order);
                npc.getTrades().setGoods(goods);
                player.displayClientMessage(Component.translatable("item.ancientempires.trade_order.written", npc.getTrades().list().size(), npc.getDisplayName()), true);
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        long valid = goods(stack).stream().filter(Goods::valid).count();
        tooltip.add(Component.translatable("item.ancientempires.trade_order.goods", valid));
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.ancientempires.trade_order.hint"));
    }
}
