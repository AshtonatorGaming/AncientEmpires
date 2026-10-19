package com.ancientempires.inventory;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcInventory;
import com.ancientempires.registry.ModMenus;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Hands, armor, and a 3x3 backpack, then the player's inventory. Slot positions
 * match {@link NpcLayout} because the fields are final. Clicks still travel on
 * the vanilla container packet.
 */
public class NpcMenu extends AbstractContainerMenu
{
    public static final int BUTTON_FOLLOW = 0;
    public static final int BUTTON_SET_HOME = 1;
    public static final int BUTTON_CLEAR_HOME = 2;
    public static final int BUTTON_REPACK = 3;
    public static final int BUTTON_CREATIVE = 4;

    @Nullable
    private final NpcEntity npc;
    private final Container inventory;

    public NpcMenu(int containerId, Inventory playerInventory, @Nullable NpcEntity npc)
    {
        super(ModMenus.NPC_INVENTORY.get(), containerId);
        this.npc = npc;
        this.inventory = npc == null ? new SimpleContainer(NpcInventory.SLOT_COUNT) : npc.getInventory();
        checkContainerSize(this.inventory, NpcInventory.SLOT_COUNT);
        this.inventory.startOpen(playerInventory.player);

        this.addGear(NpcInventory.MAIN_HAND_SLOT, EquipmentSlot.MAINHAND, false, null);
        this.addGear(NpcInventory.OFFHAND_SLOT, EquipmentSlot.OFFHAND, false, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
        this.addGear(NpcInventory.HEAD_SLOT, EquipmentSlot.HEAD, true, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);
        this.addGear(NpcInventory.CHEST_SLOT, EquipmentSlot.CHEST, true, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE);
        this.addGear(NpcInventory.LEGS_SLOT, EquipmentSlot.LEGS, true, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS);
        this.addGear(NpcInventory.FEET_SLOT, EquipmentSlot.FEET, true, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            this.addSlot(new Slot(this.inventory, slot, NpcLayout.slotX(slot), NpcLayout.slotY(slot)));
        }
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                int index = NpcInventory.SLOT_COUNT + column + row * 9;
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, NpcLayout.slotX(index), NpcLayout.slotY(index)));
            }
        }
        for (int column = 0; column < 9; column++)
        {
            int index = NpcInventory.SLOT_COUNT + 27 + column;
            this.addSlot(new Slot(playerInventory, column, NpcLayout.slotX(index), NpcLayout.slotY(index)));
        }
    }

    public static NpcMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf data)
    {
        Entity entity = playerInventory.player.level().getEntity(data.readVarInt());
        NpcEntity npc = entity instanceof NpcEntity found ? found : null;
        return new NpcMenu(containerId, playerInventory, npc);
    }

    private void addGear(int index, EquipmentSlot equipmentSlot, boolean armorOnly, @Nullable ResourceLocation icon)
    {
        this.addSlot(new GearSlot(this.inventory, index, this.npc, equipmentSlot, armorOnly, icon, NpcLayout.slotX(index), NpcLayout.slotY(index)));
    }

    @Nullable
    public NpcEntity getNpc()
    {
        return this.npc;
    }

    @Override
    public boolean stillValid(Player player)
    {
        if (this.npc == null || !this.npc.isAlive() || this.npc.distanceToSqr(player) > 64.0D)
        {
            return false;
        }
        if (player.level().isClientSide)
        {
            return true;
        }
        return this.npc.canOpenInventory(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        if (this.npc == null)
        {
            return false;
        }
        return this.npc.handleMenuButton(player, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem())
        {
            return ItemStack.EMPTY;
        }
        // Hands and armor share the entity's stack. Shift-click moves a copy
        // and writes the remainder back, so the offhand is replaced and not dropped.
        boolean gear = slot instanceof GearSlot;
        ItemStack stack = gear ? slot.getItem().copy() : slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < NpcInventory.SLOT_COUNT)
        {
            moved = this.moveItemStackTo(stack, NpcInventory.SLOT_COUNT, this.slots.size(), true);
        }
        else
        {
            moved = this.moveToNpc(stack);
        }
        if (!moved)
        {
            return ItemStack.EMPTY;
        }
        if (gear || stack.isEmpty())
        {
            slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        else
        {
            slot.setChanged();
        }
        return original;
    }

    /**
     * Armor and shields prefer their equipment slot. Anything left over tries
     * the main hand, then the offhand, then the backpack. {@code mayPlace}
     * keeps a sword out of the helmet slot.
     */
    private boolean moveToNpc(ItemStack stack)
    {
        int before = stack.getCount();
        EquipmentSlot preferred = Mob.getEquipmentSlotForItem(stack);
        if (preferred.getType() == EquipmentSlot.Type.ARMOR || preferred == EquipmentSlot.OFFHAND)
        {
            int index = this.equipmentIndex(preferred);
            this.moveItemStackTo(stack, index, index + 1, false);
        }
        if (!stack.isEmpty())
        {
            this.moveItemStackTo(stack, NpcInventory.MAIN_HAND_SLOT, NpcInventory.MAIN_HAND_SLOT + 1, false);
        }
        if (!stack.isEmpty() && preferred != EquipmentSlot.OFFHAND)
        {
            this.moveItemStackTo(stack, NpcInventory.OFFHAND_SLOT, NpcInventory.OFFHAND_SLOT + 1, false);
        }
        if (!stack.isEmpty())
        {
            this.moveItemStackTo(stack, NpcInventory.BACKPACK_START, NpcInventory.SLOT_COUNT, false);
        }
        return stack.getCount() != before;
    }

    private int equipmentIndex(EquipmentSlot slot)
    {
        if (slot == EquipmentSlot.OFFHAND)
        {
            return NpcInventory.OFFHAND_SLOT;
        }
        if (slot == EquipmentSlot.HEAD)
        {
            return NpcInventory.HEAD_SLOT;
        }
        if (slot == EquipmentSlot.CHEST)
        {
            return NpcInventory.CHEST_SLOT;
        }
        if (slot == EquipmentSlot.LEGS)
        {
            return NpcInventory.LEGS_SLOT;
        }
        if (slot == EquipmentSlot.FEET)
        {
            return NpcInventory.FEET_SLOT;
        }
        return NpcInventory.MAIN_HAND_SLOT;
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        this.inventory.stopOpen(player);
    }

    /**
     * Armor slots accept only the matching equipment and stack to one. Hands
     * accept any item. The empty icon is the vanilla armor sprite.
     * The slot index and this equipment value stay paired: menu index 1 is
     * {@link EquipmentSlot#OFFHAND}. Clicks and shift-clicks write that slot
     * by copying the stack, so the cursor and the entity do not share one.
     */
    private static final class GearSlot extends Slot
    {
        @Nullable
        private final NpcEntity npc;
        private final EquipmentSlot equipmentSlot;
        private final boolean armorOnly;
        @Nullable
        private final Pair<ResourceLocation, ResourceLocation> icon;

        private GearSlot(Container container, int index, @Nullable NpcEntity npc, EquipmentSlot equipmentSlot, boolean armorOnly, @Nullable ResourceLocation icon, int x, int y)
        {
            super(container, index, x, y);
            this.npc = npc;
            this.equipmentSlot = equipmentSlot;
            this.armorOnly = armorOnly;
            this.icon = icon == null ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
        }

        @Override
        public ItemStack getItem()
        {
            if (this.npc == null)
            {
                return super.getItem();
            }
            // Live stack so a shift-click can grow an existing offhand stack in place.
            // set and remove copy before they replace it.
            return this.npc.getItemBySlot(this.equipmentSlot);
        }

        @Override
        public void set(ItemStack stack)
        {
            if (this.npc == null)
            {
                super.set(stack);
                return;
            }
            this.npc.setItemSlot(this.equipmentSlot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            this.setChanged();
        }

        @Override
        public ItemStack remove(int amount)
        {
            if (this.npc == null)
            {
                return super.remove(amount);
            }
            ItemStack existing = this.npc.getItemBySlot(this.equipmentSlot);
            if (existing.isEmpty() || amount <= 0)
            {
                return ItemStack.EMPTY;
            }
            ItemStack remainder = existing.copy();
            ItemStack taken = remainder.split(amount);
            this.set(remainder);
            return taken;
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            if (!this.armorOnly)
            {
                return true;
            }
            return Mob.getEquipmentSlotForItem(stack) == this.equipmentSlot;
        }

        @Override
        public int getMaxStackSize()
        {
            return this.armorOnly ? 1 : super.getMaxStackSize();
        }

        @Override
        @Nullable
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon()
        {
            return this.icon;
        }
    }
}
