package com.ancientempires.inventory;

import com.ancientempires.block.WorksiteBlockEntity;
import com.ancientempires.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A worksite's or Town Hall's storage: its filtered supply slots in one row
 * (seeds, bone meal, feed, fuel...), a double chest of store below, and the
 * player's inventory. The layout lives here so the screen draws what the
 * slots are. Shift-click puts supplies in their own slots first.
 */
public class WorksiteMenu extends AbstractContainerMenu
{
    public static final int WIDTH = 176;
    public static final int SLOT = 18;
    public static final int STORE_ROWS = WorksiteBlockEntity.STORE_SIZE / 9;
    /** Gap between supply groups. */
    public static final int GROUP_GAP = 6;
    public static final int SUPPLY_LABEL_Y = 18;
    public static final int SUPPLY_Y = 28;

    private final Container container;
    private final BlockPos pos;
    private final List<WorksiteBlockEntity.Supply> supplies;
    private final int supplyCount;

    /** Server side, on the worksite's own store. */
    public WorksiteMenu(int id, Inventory inventory, WorksiteBlockEntity site)
    {
        this(id, inventory, site.store(), site.getBlockPos(), site.supplies());
    }

    private WorksiteMenu(int id, Inventory inventory, Container container, BlockPos pos, List<WorksiteBlockEntity.Supply> supplies)
    {
        super(ModMenus.WORKSITE.get(), id);
        this.container = container;
        this.pos = pos;
        this.supplies = supplies;
        this.supplyCount = WorksiteBlockEntity.supplySlots(supplies);
        checkContainerSize(container, WorksiteBlockEntity.STORE_SIZE + this.supplyCount);
        container.startOpen(inventory.player);
        int index = 0;
        for (int group = 0; group < supplies.size(); group++)
        {
            for (int slot = 0; slot < supplies.get(group).slots(); slot++)
            {
                this.addSlot(new FilteredSlot(container, WorksiteBlockEntity.STORE_SIZE + index, this.supplyX(group, slot), SUPPLY_Y));
                index++;
            }
        }
        int storeY = this.storeY();
        for (int row = 0; row < STORE_ROWS; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                this.addSlot(new Slot(container, row * 9 + column, 8 + column * SLOT, storeY + row * SLOT));
            }
        }
        int playerY = this.playerY();
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                this.addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * SLOT, playerY + row * SLOT));
            }
        }
        for (int column = 0; column < 9; column++)
        {
            this.addSlot(new Slot(inventory, column, 8 + column * SLOT, playerY + 58));
        }
    }

    /** Client side: the worksite at the position the server sent, for its supply layout and filters. */
    public static WorksiteMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer)
    {
        BlockPos pos = buffer.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos) instanceof WorksiteBlockEntity site)
        {
            SimpleContainer container = new SimpleContainer(site.store().getContainerSize())
            {
                @Override
                public boolean canPlaceItem(int slot, ItemStack stack)
                {
                    return site.canPlace(slot, stack);
                }
            };
            return new WorksiteMenu(id, inventory, container, pos, site.supplies());
        }
        return new WorksiteMenu(id, inventory, new SimpleContainer(WorksiteBlockEntity.STORE_SIZE), pos, List.of());
    }

    public List<WorksiteBlockEntity.Supply> supplies()
    {
        return this.supplies;
    }

    public BlockPos pos()
    {
        return this.pos;
    }

    /** The supply row's left edge, centered. */
    public int supplyLeft()
    {
        int width = this.supplyCount * SLOT + Math.max(0, this.supplies.size() - 1) * GROUP_GAP;
        return (WIDTH - width) / 2 + 1;
    }

    /** Slot position of the {@code slot}th slot of supply group {@code group}. */
    public int supplyX(int group, int slot)
    {
        int x = this.supplyLeft();
        for (int earlier = 0; earlier < group; earlier++)
        {
            x += this.supplies.get(earlier).slots() * SLOT + GROUP_GAP;
        }
        return x + slot * SLOT;
    }

    public int storeY()
    {
        return this.supplies.isEmpty() ? 18 : SUPPLY_Y + SLOT + 12;
    }

    public int playerY()
    {
        return this.storeY() + STORE_ROWS * SLOT + 14;
    }

    public int height()
    {
        return this.playerY() + 58 + SLOT + 6;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem())
        {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int siteSlots = this.supplyCount + WorksiteBlockEntity.STORE_SIZE;
        if (index < siteSlots)
        {
            if (!this.moveItemStackTo(stack, siteSlots, this.slots.size(), true))
            {
                return ItemStack.EMPTY;
            }
        }
        // From the player: its supply slots first (they take only their kind), then the store.
        else if (!this.moveItemStackTo(stack, 0, this.supplyCount, false) && !this.moveItemStackTo(stack, this.supplyCount, siteSlots, false))
        {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty())
        {
            slot.setByPlayer(ItemStack.EMPTY);
        }
        else
        {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        this.container.stopOpen(player);
    }

    /** A supply slot: takes only what its group takes. */
    private static final class FilteredSlot extends Slot
    {
        private FilteredSlot(Container container, int index, int x, int y)
        {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            return this.container.canPlaceItem(this.getContainerSlot(), stack);
        }
    }
}
