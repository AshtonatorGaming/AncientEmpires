package com.ancientempires.entity.npc;

import net.minecraft.nbt.CompoundTag;
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
 * storage only. Older saves stored eight backpack stacks under {@code Inventory};
 * those still land in the first eight slots of the nine-slot container.
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
    public static final int SLOT_COUNT = BACKPACK_START + BACKPACK_SLOTS;
    private static final String ITEMS_KEY = "Inventory";

    private final NpcEntity npc;
    private final SimpleContainer backpack = new SimpleContainer(BACKPACK_SLOTS);

    public NpcInventory(NpcEntity npc)
    {
        this.npc = npc;
    }

    public void save(CompoundTag tag)
    {
        tag.put(ITEMS_KEY, this.backpack.createTag());
    }

    public void load(CompoundTag tag)
    {
        this.backpack.clearContent();
        if (tag.contains(ITEMS_KEY, Tag.TAG_LIST))
        {
            this.backpack.fromTag(tag.getList(ITEMS_KEY, Tag.TAG_COMPOUND));
        }
    }

    /**
     * Merges identical backpack stacks and slides the result to the front.
     * Hands and armor stay where they are.
     */
    public void repack()
    {
        List<ItemStack> merged = new ArrayList<>();
        for (int slot = 0; slot < this.backpack.getContainerSize(); slot++)
        {
            ItemStack stack = this.backpack.getItem(slot).copy();
            if (stack.isEmpty())
            {
                continue;
            }
            for (ItemStack existing : merged)
            {
                if (existing.getCount() >= existing.getMaxStackSize())
                {
                    continue;
                }
                if (!ItemStack.isSameItemSameTags(existing, stack))
                {
                    continue;
                }
                int room = existing.getMaxStackSize() - existing.getCount();
                int move = Math.min(room, stack.getCount());
                existing.grow(move);
                stack.shrink(move);
                if (stack.isEmpty())
                {
                    break;
                }
            }
            if (!stack.isEmpty())
            {
                merged.add(stack);
            }
        }
        this.backpack.clearContent();
        for (int slot = 0; slot < merged.size(); slot++)
        {
            this.backpack.setItem(slot, merged.get(slot));
        }
    }

    public List<ItemStack> removeBackpack()
    {
        List<ItemStack> dropped = new ArrayList<>();
        for (int slot = 0; slot < this.backpack.getContainerSize(); slot++)
        {
            ItemStack stack = this.backpack.removeItemNoUpdate(slot);
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
        return SLOT_COUNT;
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
        return this.backpack.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot)
    {
        EquipmentSlot equipment = equipmentSlot(slot);
        if (equipment != null)
        {
            return this.npc.getItemBySlot(equipment);
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
            ItemStack existing = this.npc.getItemBySlot(equipment);
            if (existing.isEmpty())
            {
                return ItemStack.EMPTY;
            }
            this.npc.setItemSlot(equipment, ItemStack.EMPTY);
            return existing;
        }
        int backpackSlot = slot - BACKPACK_START;
        if (backpackSlot < 0 || backpackSlot >= BACKPACK_SLOTS)
        {
            return ItemStack.EMPTY;
        }
        return this.backpack.removeItemNoUpdate(backpackSlot);
    }

    @Override
    public void setItem(int slot, ItemStack stack)
    {
        EquipmentSlot equipment = equipmentSlot(slot);
        if (equipment != null)
        {
            this.npc.setItemSlot(equipment, stack);
        }
        else
        {
            int backpackSlot = slot - BACKPACK_START;
            if (backpackSlot >= 0 && backpackSlot < BACKPACK_SLOTS)
            {
                this.backpack.setItem(backpackSlot, stack);
            }
        }
        this.setChanged();
    }

    @Override
    public void setChanged()
    {
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
        this.backpack.clearContent();
    }
}
