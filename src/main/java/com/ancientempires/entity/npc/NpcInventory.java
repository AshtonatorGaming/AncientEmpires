package com.ancientempires.entity.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Hands, armor, and a 3x3 backpack. Slots 0 through 5 are vanilla equipment,
 * so attributes, combat, and the renderer keep reading them. The backpack is
 * storage only. Each occupied backpack slot is saved with its index, the same
 * way a chest saves items. Older saves have no Slot key; those stacks were
 * already compacted and load in list order.
 */
public class NpcInventory implements Container
{
    public static final int MAIN_HAND_SLOT = 0;
    public static final int OFFHAND_SLOT = 1;
    public static final int HEAD_SLOT = 2;
    public static final int CHEST_SLOT = 3;
    public static final int LEGS_SLOT = 4;
    public static final int FEET_SLOT = 5;
    public static final int BACKPACK_START = 6;
    public static final int BACKPACK_SLOTS = 9;
    /** The end of the backpack (equipment and backpack); every backpack loop runs to it. */
    public static final int SLOT_COUNT = BACKPACK_START + BACKPACK_SLOTS;
    /**
     * AW's order slots, after the backpack: the orders (a Routing, Work, Combat,
     * or Trade order) and the upkeep order. Order items only; saved apart, so
     * older saves load as they were.
     */
    public static final int ORDERS_SLOT = SLOT_COUNT;
    public static final int UPKEEP_SLOT = SLOT_COUNT + 1;
    /** Every slot: equipment, backpack, and the two order slots. */
    public static final int CONTAINER_SIZE = SLOT_COUNT + 2;
    private static final String ITEMS_KEY = "Inventory";
    private static final String ORDERS_KEY = "AeOrderSlots";

    private final NpcEntity npc;
    private final SimpleContainer backpack = new SimpleContainer(BACKPACK_SLOTS);
    private final SimpleContainer orders = new SimpleContainer(2);
    /**
     * Bumped on every write through this container, including menu clicks that
     * change a stack in place. Not saved. Sidearm lookups rescan only when it moves.
     */
    private int backpackVersion;

    public NpcInventory(NpcEntity npc)
    {
        this.npc = npc;
    }

    public void save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        for (int slot = 0; slot < this.backpack.getContainerSize(); slot++)
        {
            ItemStack stack = this.backpack.getItem(slot);
            if (stack.isEmpty())
            {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) slot);
            stack.save(entry);
            list.add(entry);
        }
        tag.put(ITEMS_KEY, list);
        ListTag orders = new ListTag();
        for (int slot = 0; slot < this.orders.getContainerSize(); slot++)
        {
            if (!this.orders.getItem(slot).isEmpty())
            {
                CompoundTag entry = new CompoundTag();
                entry.putByte("Slot", (byte) slot);
                this.orders.getItem(slot).save(entry);
                orders.add(entry);
            }
        }
        tag.put(ORDERS_KEY, orders);
    }

    /** The order in the orders slot (empty for none). */
    public ItemStack ordersItem()
    {
        return this.orders.getItem(0);
    }

    /** The order in the upkeep slot (empty for none). */
    public ItemStack upkeepItem()
    {
        return this.orders.getItem(1);
    }

    public void load(CompoundTag tag)
    {
        this.backpackVersion++;
        this.backpack.clearContent();
        this.orders.clearContent();
        for (Tag order : tag.getList(ORDERS_KEY, Tag.TAG_COMPOUND))
        {
            CompoundTag entry = (CompoundTag) order;
            int slot = entry.getByte("Slot") & 255;
            if (slot < this.orders.getContainerSize())
            {
                this.orders.setItem(slot, ItemStack.of(entry));
            }
        }
        if (!tag.contains(ITEMS_KEY, Tag.TAG_LIST))
        {
            return;
        }
        ListTag list = tag.getList(ITEMS_KEY, Tag.TAG_COMPOUND);
        boolean indexed = false;
        for (int index = 0; index < list.size(); index++)
        {
            if (list.getCompound(index).contains("Slot", Tag.TAG_BYTE))
            {
                indexed = true;
                break;
            }
        }
        for (int index = 0; index < list.size(); index++)
        {
            CompoundTag entry = list.getCompound(index);
            ItemStack stack = ItemStack.of(entry);
            if (stack.isEmpty())
            {
                continue;
            }
            int slot;
            if (indexed)
            {
                if (!entry.contains("Slot", Tag.TAG_BYTE))
                {
                    continue;
                }
                slot = entry.getByte("Slot") & 255;
            }
            else
            {
                slot = index;
            }
            if (slot >= 0 && slot < this.backpack.getContainerSize())
            {
                this.backpack.setItem(slot, stack);
            }
        }
    }

    public List<ItemStack> removeBackpack()
    {
        this.backpackVersion++;
        List<ItemStack> dropped = new ArrayList<>();
        for (int slot = 0; slot < this.backpack.getContainerSize(); slot++)
        {
            ItemStack stack = this.backpack.removeItemNoUpdate(slot);
            if (!stack.isEmpty())
            {
                dropped.add(stack);
            }
        }
        for (int slot = 0; slot < this.orders.getContainerSize(); slot++)
        {
            ItemStack stack = this.orders.removeItemNoUpdate(slot);
            if (!stack.isEmpty())
            {
                dropped.add(stack);
            }
        }
        return dropped;
    }

    @Nullable
    public static EquipmentSlot equipmentSlot(int slot)
    {
        if (slot == MAIN_HAND_SLOT)
        {
            return EquipmentSlot.MAINHAND;
        }
        if (slot == OFFHAND_SLOT)
        {
            return EquipmentSlot.OFFHAND;
        }
        if (slot == HEAD_SLOT)
        {
            return EquipmentSlot.HEAD;
        }
        if (slot == CHEST_SLOT)
        {
            return EquipmentSlot.CHEST;
        }
        if (slot == LEGS_SLOT)
        {
            return EquipmentSlot.LEGS;
        }
        if (slot == FEET_SLOT)
        {
            return EquipmentSlot.FEET;
        }
        return null;
    }

    @Override
    public int getContainerSize()
    {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty()
    {
        for (int slot = 0; slot < BACKPACK_START; slot++)
        {
            if (!this.getItem(slot).isEmpty())
            {
                return false;
            }
        }
        return this.backpack.isEmpty() && this.orders.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot)
    {
        EquipmentSlot equipment = equipmentSlot(slot);
        if (equipment != null)
        {
            return this.npc.getItemBySlot(equipment);
        }
        if (slot == ORDERS_SLOT || slot == UPKEEP_SLOT)
        {
            return this.orders.getItem(slot - ORDERS_SLOT);
        }
        int backpackSlot = slot - BACKPACK_START;
        if (backpackSlot < 0 || backpackSlot >= BACKPACK_SLOTS)
        {
            return ItemStack.EMPTY;
        }
        return this.backpack.getItem(backpackSlot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount)
    {
        EquipmentSlot equipment = equipmentSlot(slot);
        if (equipment != null)
        {
            return this.replaceEquipment(equipment, amount);
        }
        ItemStack existing = this.getItem(slot);
        if (existing.isEmpty() || amount <= 0)
        {
            return ItemStack.EMPTY;
        }
        ItemStack taken = existing.split(amount);
        if (existing.isEmpty())
        {
            this.setItem(slot, ItemStack.EMPTY);
        }
        else
        {
            this.setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot)
    {
        EquipmentSlot equipment = equipmentSlot(slot);
        if (equipment != null)
        {
            return this.replaceEquipment(equipment, Integer.MAX_VALUE);
        }
        if (slot == ORDERS_SLOT || slot == UPKEEP_SLOT)
        {
            return this.orders.removeItemNoUpdate(slot - ORDERS_SLOT);
        }
        int backpackSlot = slot - BACKPACK_START;
        if (backpackSlot < 0 || backpackSlot >= BACKPACK_SLOTS)
        {
            return ItemStack.EMPTY;
        }
        this.backpackVersion++;
        return this.backpack.removeItemNoUpdate(backpackSlot);
    }

    /** Adds to the backpack, filling matching stacks first. Returns what did not fit. */
    public ItemStack addToBackpack(ItemStack stack)
    {
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++)
        {
            for (int slot = BACKPACK_START; slot < SLOT_COUNT && !rest.isEmpty(); slot++)
            {
                ItemStack held = this.getItem(slot);
                if (pass == 0 && !held.isEmpty() && ItemStack.isSameItemSameTags(held, rest) && held.getCount() < held.getMaxStackSize())
                {
                    int move = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
                    held.grow(move);
                    rest.shrink(move);
                    this.setItem(slot, held);
                }
                else if (pass == 1 && held.isEmpty())
                {
                    this.setItem(slot, rest.copy());
                    rest = ItemStack.EMPTY;
                }
            }
        }
        return rest;
    }

    public int getBackpackVersion()
    {
        return this.backpackVersion;
    }

    /**
     * Puts the main-hand stack in a backpack slot and that slot's stack in the
     * main hand. Slot to slot: nothing merges, splits, or moves elsewhere.
     */
    public void swapMainHandWithBackpack(int backpackSlot)
    {
        if (backpackSlot < 0 || backpackSlot >= BACKPACK_SLOTS)
        {
            return;
        }
        int slot = BACKPACK_START + backpackSlot;
        ItemStack hand = this.getItem(MAIN_HAND_SLOT).copy();
        ItemStack stored = this.getItem(slot).copy();
        this.setItem(MAIN_HAND_SLOT, stored);
        this.setItem(slot, hand);
    }

    @Override
    public void setItem(int slot, ItemStack stack)
    {
        EquipmentSlot equipment = equipmentSlot(slot);
        if (equipment != null)
        {
            this.npc.setItemSlot(equipment, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        else
        {
            int backpackSlot = slot - BACKPACK_START;
            if (slot == ORDERS_SLOT || slot == UPKEEP_SLOT)
            {
                this.orders.setItem(slot - ORDERS_SLOT, stack);
            }
            else if (backpackSlot >= 0 && backpackSlot < BACKPACK_SLOTS)
            {
                this.backpack.setItem(backpackSlot, stack);
            }
        }
        this.setChanged();
    }

    /**
     * Takes up to {@code amount} off a copy of the worn stack, then writes the
     * remainder back to the same {@link EquipmentSlot}. The cursor never shares
     * the entity's stack, so a click cannot zero the offhand and drop it.
     */
    private ItemStack replaceEquipment(EquipmentSlot equipment, int amount)
    {
        ItemStack existing = this.npc.getItemBySlot(equipment);
        if (existing.isEmpty() || amount <= 0)
        {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = existing.copy();
        ItemStack taken = remainder.split(amount);
        this.npc.setItemSlot(equipment, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
        this.setChanged();
        return taken;
    }

    @Override
    public void setChanged()
    {
        this.backpackVersion++;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return this.npc.isAlive();
    }

    @Override
    public void clearContent()
    {
        for (int slot = 0; slot < BACKPACK_START; slot++)
        {
            this.setItem(slot, ItemStack.EMPTY);
        }
        this.backpackVersion++;
        this.backpack.clearContent();
        this.orders.clearContent();
    }
}
