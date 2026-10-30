package com.ancientempires.inventory;

import com.ancientempires.item.TradeOrderItem;
import com.ancientempires.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Trade Order's goods editor (AW's trade order screen): one trade a page,
 * what the customer pays on the left and what the trader gives on the right,
 * nine ghost slots each. Ghosts hold copies: a click with an item sets the
 * slot to that stack, a right click adds one of it, an empty hand clears
 * (right click takes one off). Buttons turn the pages, add a trade, and strike
 * one off. Every change is written to the order in the player's hand.
 */
public class TradeOrderMenu extends AbstractContainerMenu
{
    public static final int SIDE = 9;
    public static final int GHOSTS = SIDE * 2;
    public static final int WIDTH = 176;
    public static final int IN_X = 17;
    public static final int OUT_X = 107;
    public static final int GRID_Y = 30;
    public static final int BUTTONS_Y = 90;
    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = 132;
    public static final int HEIGHT = PLAYER_Y + 58 + 18 + 8;

    public static final int PREVIOUS = 0;
    public static final int NEXT = 1;
    public static final int ADD = 2;
    public static final int REMOVE = 3;

    private final Player player;
    private final InteractionHand hand;
    private final SimpleContainer ghosts = new SimpleContainer(GHOSTS);
    private final DataSlot page = DataSlot.standalone();
    private final DataSlot pages = DataSlot.standalone();

    public TradeOrderMenu(int id, Inventory inventory, InteractionHand hand)
    {
        super(ModMenus.TRADE_ORDER.get(), id);
        this.player = inventory.player;
        this.hand = hand;
        for (int side = 0; side < 2; side++)
        {
            for (int i = 0; i < SIDE; i++)
            {
                this.addSlot(new Slot(this.ghosts, side * SIDE + i, (side == 0 ? IN_X : OUT_X) + i % 3 * 18, GRID_Y + i / 3 * 18)
                {
                    @Override
                    public boolean mayPickup(Player player)
                    {
                        return false;
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                this.addSlot(new Slot(inventory, 9 + row * 9 + column, PLAYER_X + column * 18, PLAYER_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++)
        {
            boolean held = hand == InteractionHand.MAIN_HAND && column == inventory.selected;
            this.addSlot(new Slot(inventory, column, PLAYER_X + column * 18, PLAYER_Y + 58)
            {
                // The order being edited stays in the hand.
                @Override
                public boolean mayPickup(Player player)
                {
                    return !held;
                }
            });
        }
        this.addDataSlot(this.page);
        this.addDataSlot(this.pages);
        this.load();
    }

    public static TradeOrderMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer)
    {
        return new TradeOrderMenu(id, inventory, buffer.readEnum(InteractionHand.class));
    }

    private ItemStack order()
    {
        return this.player.getItemInHand(this.hand);
    }

    /** The page shown (from 0), and how many there are. */
    public int page()
    {
        return this.page.get();
    }

    public int pages()
    {
        return this.pages.get();
    }

    /** Fills the ghosts from the order's trade on this page. */
    private void load()
    {
        List<TradeOrderItem.Goods> goods = TradeOrderItem.goods(this.order());
        this.pages.set(Math.max(1, goods.size()));
        this.page.set(Math.min(this.page.get(), this.pages.get() - 1));
        this.ghosts.clearContent();
        // Each stack in the slot it was set in, not packed to the front.
        ItemStack[] slots = TradeOrderItem.editorSlots(this.order(), this.page.get());
        for (int i = 0; i < GHOSTS; i++)
        {
            this.ghosts.setItem(i, slots[i]);
        }
    }

    /** Writes this page's ghosts back into the order. */
    private void save()
    {
        List<TradeOrderItem.Goods> goods = new ArrayList<>(TradeOrderItem.goods(this.order()));
        while (goods.size() <= this.page.get())
        {
            goods.add(new TradeOrderItem.Goods(List.of(), List.of()));
        }
        List<ItemStack> input = new ArrayList<>();
        List<ItemStack> output = new ArrayList<>();
        for (int i = 0; i < SIDE; i++)
        {
            input.add(this.ghosts.getItem(i).copy());
            output.add(this.ghosts.getItem(SIDE + i).copy());
        }
        goods.set(this.page.get(), new TradeOrderItem.Goods(input, output));
        TradeOrderItem.setGoods(this.order(), goods);
        this.pages.set(Math.max(1, goods.size()));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player)
    {
        if (slotId < 0 || slotId >= GHOSTS)
        {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        ItemStack carried = this.getCarried();
        ItemStack ghost = this.ghosts.getItem(slotId);
        if (clickType == ClickType.QUICK_MOVE || clickType == ClickType.PICKUP && carried.isEmpty() && button == 0)
        {
            ghost = ItemStack.EMPTY;
        }
        else if (clickType == ClickType.PICKUP && carried.isEmpty())
        {
            ghost.shrink(1);
        }
        else if (clickType == ClickType.PICKUP && button == 0)
        {
            ghost = carried.copy();
        }
        else if (clickType == ClickType.PICKUP)
        {
            ghost = ItemStack.isSameItemSameTags(ghost, carried) ? ghost.copyWithCount(Math.min(ghost.getMaxStackSize(), ghost.getCount() + 1))
                    : carried.copyWithCount(1);
        }
        else
        {
            return;
        }
        this.ghosts.setItem(slotId, ghost);
        if (!player.level().isClientSide)
        {
            this.save();
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        if (!(this.order().getItem() instanceof TradeOrderItem))
        {
            return false;
        }
        List<TradeOrderItem.Goods> goods = new ArrayList<>(TradeOrderItem.goods(this.order()));
        switch (id)
        {
            case PREVIOUS -> this.page.set(Math.max(0, this.page.get() - 1));
            case NEXT -> this.page.set(Math.min(this.pages.get() - 1, this.page.get() + 1));
            case ADD ->
            {
                if (goods.size() >= TradeOrderItem.MAX_TRADES)
                {
                    return false;
                }
                if (goods.isEmpty())
                {
                    // The first page shown was a blank one not yet written.
                    goods.add(new TradeOrderItem.Goods(List.of(), List.of()));
                }
                goods.add(new TradeOrderItem.Goods(List.of(), List.of()));
                TradeOrderItem.setGoods(this.order(), goods);
                this.page.set(goods.size() - 1);
            }
            case REMOVE ->
            {
                if (this.page.get() < goods.size())
                {
                    goods.remove(this.page.get());
                    TradeOrderItem.setGoods(this.order(), goods);
                }
            }
            default ->
            {
                return false;
            }
        }
        this.load();
        return true;
    }

    /** Shift-click from the inventory sets the first empty slot the customer pays with. */
    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        if (index >= GHOSTS && this.slots.get(index).hasItem())
        {
            for (int i = 0; i < GHOSTS; i++)
            {
                if (this.ghosts.getItem(i).isEmpty())
                {
                    this.ghosts.setItem(i, this.slots.get(index).getItem().copy());
                    if (!player.level().isClientSide)
                    {
                        this.save();
                    }
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return this.order().getItem() instanceof TradeOrderItem;
    }
}
