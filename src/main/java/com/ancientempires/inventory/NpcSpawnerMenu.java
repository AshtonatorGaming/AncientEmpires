package com.ancientempires.inventory;

import com.ancientempires.block.NpcSpawnerBlockEntity;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The NPC spawner's screen: nine scroll slots (what it spawns, the stack being
 * how many), its settings synced as numbers, and the player's inventory. A
 * settings button is a menu button ({@link NpcSpawnerBlockEntity#press}).
 */
public class NpcSpawnerMenu extends AbstractContainerMenu
{
    public static final int WIDTH = 248;
    public static final int SLOTS_X = 12;
    public static final int SLOTS_Y = 30;
    public static final int PLAYER_X = 44;
    public static final int PLAYER_Y = 150;
    public static final int HEIGHT = PLAYER_Y + 58 + 18 + 8;

    private final Container container;
    private final ContainerData data;
    @Nullable
    private final NpcSpawnerBlockEntity spawner;

    public NpcSpawnerMenu(int id, Inventory inventory, NpcSpawnerBlockEntity spawner)
    {
        this(id, inventory, spawner.slots(), spawner.data(), spawner);
    }

    private NpcSpawnerMenu(int id, Inventory inventory, Container container, ContainerData data, @Nullable NpcSpawnerBlockEntity spawner)
    {
        super(ModMenus.NPC_SPAWNER.get(), id);
        this.container = container;
        this.data = data;
        this.spawner = spawner;
        checkContainerSize(container, NpcSpawnerBlockEntity.SLOTS);
        checkContainerDataCount(data, NpcSpawnerBlockEntity.DATA_COUNT);
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 3; column++)
            {
                this.addSlot(new Slot(container, row * 3 + column, SLOTS_X + column * 18, SLOTS_Y + row * 18)
                {
                    @Override
                    public boolean mayPlace(ItemStack stack)
                    {
                        return NpcSpawnerBlockEntity.spawns(stack);
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
            this.addSlot(new Slot(inventory, column, PLAYER_X + column * 18, PLAYER_Y + 58));
        }
        this.addDataSlots(data);
    }

    public static NpcSpawnerMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer)
    {
        buffer.readBlockPos();
        return new NpcSpawnerMenu(id, inventory, new SimpleContainer(NpcSpawnerBlockEntity.SLOTS),
                new SimpleContainerData(NpcSpawnerBlockEntity.DATA_COUNT), null);
    }

    public int get(int setting)
    {
        return this.data.get(setting);
    }

    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        if (this.spawner == null || !NpcEntity.mayToggleCreative(player))
        {
            return false;
        }
        this.spawner.press(id);
        return true;
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
        int own = NpcSpawnerBlockEntity.SLOTS;
        if (index < own)
        {
            if (!this.moveItemStackTo(stack, own, this.slots.size(), true))
            {
                return ItemStack.EMPTY;
            }
        }
        else if (!NpcSpawnerBlockEntity.spawns(stack) || !this.moveItemStackTo(stack, 0, own, false))
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
}
