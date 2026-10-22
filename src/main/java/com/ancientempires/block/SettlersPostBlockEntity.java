package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import com.ancientempires.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Settlers' Post's state: its owner, whether immigration is on, and the food
 * bundles stored for newcomers. It does not tick; {@link NpcImmigration} looks
 * at it once a day. Hoppers can feed it food bundles.
 */
public final class SettlersPostBlockEntity extends BlockEntity
{
    @Nullable
    private UUID owner;
    private boolean open = true;
    private int food;
    private final LazyOptional<IItemHandler> foodInput = LazyOptional.of(FoodInput::new);

    public SettlersPostBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.SETTLERS_POST.get(), pos, state);
    }

    @Nullable
    public UUID owner()
    {
        return this.owner;
    }

    void setOwner(@Nullable UUID owner)
    {
        this.owner = owner;
        this.setChanged();
    }

    public boolean isOpen()
    {
        return this.open;
    }

    void setOpen(boolean open)
    {
        this.open = open;
        this.setChanged();
    }

    public int food()
    {
        return this.food;
    }

    /** Takes up to {@code count} food bundles, as far as the post has room. Returns how many it took. */
    int addFood(int count)
    {
        int taken = Math.max(0, Math.min(count, AncientEmpiresConfig.immigrationFoodCapacity - this.food));
        if (taken > 0)
        {
            this.food += taken;
            this.setChanged();
        }
        return taken;
    }

    public void useFood(int count)
    {
        this.food = Math.max(0, this.food - count);
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (this.owner != null)
        {
            tag.putUUID("Owner", this.owner);
        }
        tag.putBoolean("Open", this.open);
        tag.putInt("Food", this.food);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.open = !tag.contains("Open") || tag.getBoolean("Open");
        this.food = Math.max(0, tag.getInt("Food"));
    }

    @Override
    public void onLoad()
    {
        super.onLoad();
        // Posts placed before a save, or moved by other mods, rejoin the daily list.
        if (this.level instanceof net.minecraft.server.level.ServerLevel server)
        {
            NpcImmigration.register(server, this.worldPosition);
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side)
    {
        if (capability == ForgeCapabilities.ITEM_HANDLER)
        {
            return this.foodInput.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        this.foodInput.invalidate();
    }

    /** One insert-only slot for hoppers and pipes: food bundles go into the store. */
    private final class FoodInput implements IItemHandler
    {
        @Override
        public int getSlots()
        {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot)
        {
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate)
        {
            if (!this.isItemValid(slot, stack))
            {
                return stack;
            }
            int room = Math.max(0, AncientEmpiresConfig.immigrationFoodCapacity - SettlersPostBlockEntity.this.food);
            int taken = Math.min(room, stack.getCount());
            if (taken <= 0)
            {
                return stack;
            }
            if (!simulate)
            {
                SettlersPostBlockEntity.this.addFood(taken);
            }
            ItemStack rest = stack.copy();
            rest.shrink(taken);
            return rest;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate)
        {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot)
        {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack)
        {
            return stack.is(ModItems.FOOD_BUNDLE.get());
        }
    }
}
