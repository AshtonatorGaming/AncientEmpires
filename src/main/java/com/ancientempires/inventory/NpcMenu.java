package com.ancientempires.inventory;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcInventory;
import com.ancientempires.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Dispenser-sized menu. The first slot is the NPC's main hand. The other eight
 * are the backpack. The lower slots are the player's own inventory.
 */
public class NpcMenu extends AbstractContainerMenu
{
    private static final int PLAYER_START_Y = 84;
    private static final int HOTBAR_Y = 142;

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

        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 3; column++)
            {
                this.addSlot(new Slot(this.inventory, column + row * 3, 62 + column * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, PLAYER_START_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++)
        {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    public static NpcMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf data)
    {
        Entity entity = playerInventory.player.level().getEntity(data.readVarInt());
        NpcEntity npc = entity instanceof NpcEntity found ? found : null;
        return new NpcMenu(containerId, playerInventory, npc);
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
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem())
        {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int npcSlots = this.inventory.getContainerSize();
        if (index < npcSlots)
        {
            if (!this.moveItemStackTo(stack, npcSlots, this.slots.size(), true))
            {
                return ItemStack.EMPTY;
            }
        }
        else if (!this.moveItemStackTo(stack, 0, npcSlots, false))
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
    public void removed(Player player)
    {
        super.removed(player);
        this.inventory.stopOpen(player);
    }
}
