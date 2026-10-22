package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import com.ancientempires.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;

import java.util.Optional;

/**
 * A kitchen: one store for raw food, fuel, cooked food, and the bundles made
 * from it. A cook working it smokes raw food by vanilla's smoker recipes,
 * burning fuel from the store, and packs cooked food into food bundles at
 * {@code work.cookBundleNutrition} hunger each (the crafting recipe takes
 * about 37 and a water bottle), up to {@code cookBundleTarget} in store.
 */
public final class KitchenBlockEntity extends WorksiteBlockEntity
{
    /** Smoker fuel left, in burn ticks. */
    private int fuel;

    public KitchenBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.KITCHEN.get(), pos, state);
    }

    @Override
    protected int defaultRadius()
    {
        return 1;
    }

    @Override
    protected int maxRadius()
    {
        return 1;
    }

    @Override
    public boolean canResize()
    {
        return false;
    }

    @Override
    protected void scan(Level level)
    {
    }

    @Override
    protected void forget(BlockPos tile)
    {
    }

    /** Whether there is raw food to smoke and the fuel to do it. */
    public boolean canCook()
    {
        return this.rawSlot() >= 0 && (this.fuel >= AncientEmpiresConfig.cookTicks || this.fuelSlot() >= 0);
    }

    /** Whether there is cooked food for a bundle and room for more bundles. */
    public boolean canBundle()
    {
        return this.bundles() < AncientEmpiresConfig.cookBundleTarget && this.cookedNutrition() >= AncientEmpiresConfig.cookBundleNutrition;
    }

    /** Smokes one raw food. Returns false if there was nothing to cook, no fuel, or no room. */
    public boolean cookOne()
    {
        Level level = this.level;
        int raw = this.rawSlot();
        if (level == null || raw < 0)
        {
            return false;
        }
        SimpleContainer store = this.store();
        ItemStack input = store.getItem(raw);
        Optional<SmokingRecipe> recipe = smoking(level, input);
        if (recipe.isEmpty())
        {
            return false;
        }
        while (this.fuel < AncientEmpiresConfig.cookTicks)
        {
            int slot = this.fuelSlot();
            if (slot < 0)
            {
                return false;
            }
            ItemStack fuelStack = store.getItem(slot);
            this.fuel += ForgeHooks.getBurnTime(fuelStack, RecipeType.SMOKING);
            ItemStack remainder = fuelStack.getCraftingRemainingItem();
            fuelStack.shrink(1);
            store.setItem(slot, fuelStack.isEmpty() ? remainder : fuelStack);
        }
        ItemStack result = recipe.get().getResultItem(level.registryAccess()).copy();
        ItemStack rest = HopperBlockEntity.addItem(null, store, result, null);
        if (!rest.isEmpty())
        {
            return false;
        }
        this.fuel -= AncientEmpiresConfig.cookTicks;
        input.shrink(1);
        store.setItem(raw, input);
        this.setChanged();
        return true;
    }

    /** Packs cooked food worth a bundle into one food bundle. */
    public boolean bundleOne()
    {
        if (!this.canBundle())
        {
            return false;
        }
        SimpleContainer store = this.store();
        int needed = AncientEmpiresConfig.cookBundleNutrition;
        for (int slot = 0; slot < store.getContainerSize() && needed > 0; slot++)
        {
            ItemStack stack = store.getItem(slot);
            int value = cookedValue(stack);
            while (value > 0 && !stack.isEmpty() && needed > 0)
            {
                needed -= value;
                stack.shrink(1);
            }
            store.setItem(slot, stack);
        }
        HopperBlockEntity.addItem(null, store, new ItemStack(ModItems.FOOD_BUNDLE.get()), null);
        this.setChanged();
        return true;
    }

    private int rawSlot()
    {
        Level level = this.level;
        SimpleContainer store = this.store();
        for (int slot = 0; level != null && slot < store.getContainerSize(); slot++)
        {
            ItemStack stack = store.getItem(slot);
            if (!stack.isEmpty() && smoking(level, stack).isPresent())
            {
                return slot;
            }
        }
        return -1;
    }

    private int fuelSlot()
    {
        SimpleContainer store = this.store();
        for (int slot = 0; slot < store.getContainerSize(); slot++)
        {
            ItemStack stack = store.getItem(slot);
            if (!stack.isEmpty() && ForgeHooks.getBurnTime(stack, RecipeType.SMOKING) > 0 && stack.getFoodProperties(null) == null)
            {
                return slot;
            }
        }
        return -1;
    }

    private int bundles()
    {
        int count = 0;
        SimpleContainer store = this.store();
        for (int slot = 0; slot < store.getContainerSize(); slot++)
        {
            if (store.getItem(slot).is(ModItems.FOOD_BUNDLE.get()))
            {
                count += store.getItem(slot).getCount();
            }
        }
        return count;
    }

    private int cookedNutrition()
    {
        int total = 0;
        SimpleContainer store = this.store();
        for (int slot = 0; slot < store.getContainerSize(); slot++)
        {
            ItemStack stack = store.getItem(slot);
            total += cookedValue(stack) * stack.getCount();
        }
        return total;
    }

    /** Hunger a ready-to-eat food is worth for a bundle; raw food (it can still be smoked) and bundles count nothing. */
    private int cookedValue(ItemStack stack)
    {
        if (stack.isEmpty() || stack.is(ModItems.FOOD_BUNDLE.get()) || (this.level != null && smoking(this.level, stack).isPresent()))
        {
            return 0;
        }
        FoodProperties food = stack.getFoodProperties(null);
        return food == null ? 0 : food.getNutrition();
    }

    private static Optional<SmokingRecipe> smoking(Level level, ItemStack stack)
    {
        return level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, new SimpleContainer(stack), level);
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putInt("Fuel", this.fuel);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.fuel = tag.getInt("Fuel");
    }
}
