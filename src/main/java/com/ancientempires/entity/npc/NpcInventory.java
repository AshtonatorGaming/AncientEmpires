package com.ancientempires.entity.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Main hand plus a small backpack. Slot 0 is the vanilla main-hand equipment
 * slot, so weapon attributes and the attack goal keep reading it. The other
 * slots are storage only. There are no size categories and no job filters.
 */
public class NpcInventory implements Container
{
    public static final int BACKPACK_SLOTS = 8;
    public static final int SLOT_COUNT = 1 + BACKPACK_SLOTS;
    public static final int MAIN_HAND_SLOT = 0;
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

    @Override
    public int getContainerSize()
    {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty()
    {
        return this.npc.getMainHandItem().isEmpty() && this.backpack.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot)
    {
        if (slot == MAIN_HAND_SLOT)
        {
            return this.npc.getMainHandItem();
        }
        if (slot < 1 || slot >= SLOT_COUNT)
        {
            return ItemStack.EMPTY;
        }
        return this.backpack.getItem(slot - 1);
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
        ItemStack existing = this.getItem(slot);
        if (existing.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        if (slot == MAIN_HAND_SLOT)
        {
            this.npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        else
        {
            this.backpack.setItem(slot - 1, ItemStack.EMPTY);
        }
        return existing;
    }

    @Override
    public void setItem(int slot, ItemStack stack)
    {
        if (slot == MAIN_HAND_SLOT)
        {
            this.npc.setItemSlot(EquipmentSlot.MAINHAND, stack);
        }
        else if (slot >= 1 && slot < SLOT_COUNT)
        {
            this.backpack.setItem(slot - 1, stack);
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
        this.npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.backpack.clearContent();
    }
}
