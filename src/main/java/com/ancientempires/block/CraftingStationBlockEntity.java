package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Ancient Warfare's auto crafting station, worked by hand: one store, and one
 * crafting recipe it makes (set by sneak-clicking with the item wanted). A
 * craftsman working it takes the recipe's ingredients from the store and puts
 * the result (and what the recipe leaves behind, an empty bucket and the like)
 * back, until the store holds {@code work.craftTarget} of it.
 */
public final class CraftingStationBlockEntity extends WorksiteBlockEntity
{
    @Nullable
    private ResourceLocation recipe;

    public CraftingStationBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.CRAFTING_STATION.get(), pos, state);
    }

    @Override
    protected int defaultSize()
    {
        return 1;
    }

    @Override
    protected List<? extends Integer> maxSizes()
    {
        return List.of(1);
    }

    /** No area: it works in place. */
    @Override
    public UpgradeKind upgradeKind()
    {
        return UpgradeKind.NONE;
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

    /** The recipe it makes, if it is set and still exists (datapacks change). */
    public Optional<CraftingRecipe> recipe()
    {
        if (this.recipe == null || this.level == null)
        {
            return Optional.empty();
        }
        return this.level.getRecipeManager().byKey(this.recipe).filter(CraftingRecipe.class::isInstance).map(CraftingRecipe.class::cast);
    }

    /**
     * Makes {@code wanted} from now on: the crafting recipe for that item named
     * after it (minecraft:stick for sticks), else the first one found. Returns
     * the recipe, or empty when nothing crafts it.
     */
    public Optional<CraftingRecipe> setRecipeFor(ItemStack wanted)
    {
        Level level = this.level;
        if (level == null || wanted.isEmpty())
        {
            return Optional.empty();
        }
        ResourceLocation name = ForgeRegistries.ITEMS.getKey(wanted.getItem());
        CraftingRecipe found = null;
        for (CraftingRecipe candidate : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING))
        {
            ItemStack result = candidate.getResultItem(level.registryAccess());
            if (candidate.isSpecial() || !ItemStack.isSameItem(result, wanted))
            {
                continue;
            }
            if (found == null || candidate.getId().equals(name))
            {
                found = candidate;
            }
        }
        if (found != null)
        {
            this.recipe = found.getId();
            this.setChanged();
        }
        return Optional.ofNullable(found);
    }

    public void clearRecipe()
    {
        this.recipe = null;
        this.setChanged();
    }

    /** Whether it has a recipe, the ingredients for it, the room for what comes out, and less than the target made. */
    public boolean canCraft()
    {
        Optional<CraftingRecipe> recipe = this.recipe();
        return recipe.isPresent() && this.stock(recipe.get()) < AncientEmpiresConfig.craftTarget && this.craft(recipe.get(), true);
    }

    /** Crafts once. Returns false if the ingredients or the room were missing. */
    public boolean craftOne()
    {
        Optional<CraftingRecipe> recipe = this.recipe();
        return recipe.isPresent() && this.craft(recipe.get(), false);
    }

    private int stock(CraftingRecipe recipe)
    {
        ItemStack result = recipe.getResultItem(this.level.registryAccess());
        int count = 0;
        SimpleContainer store = this.store();
        for (int slot = 0; slot < store.getContainerSize(); slot++)
        {
            if (ItemStack.isSameItemSameTags(store.getItem(slot), result))
            {
                count += store.getItem(slot).getCount();
            }
        }
        return count;
    }

    /**
     * One craft, worked out on a copy of the store: each ingredient taken from
     * a stack it accepts, then the result and the leftovers put in. Only when it
     * all fits ({@code simulate} false) is the store changed.
     */
    private boolean craft(CraftingRecipe recipe, boolean simulate)
    {
        SimpleContainer store = this.store();
        SimpleContainer copy = new SimpleContainer(store.getContainerSize());
        for (int slot = 0; slot < store.getContainerSize(); slot++)
        {
            copy.setItem(slot, store.getItem(slot).copy());
        }
        List<ItemStack> leftovers = new ArrayList<>();
        // The pickiest ingredients choose first: taken in recipe order, one that accepts several items could
        // take the only stack a stricter one needed, and a craftable recipe would read as not.
        List<Ingredient> needed = new ArrayList<>(recipe.getIngredients());
        needed.removeIf(Ingredient::isEmpty);
        needed.sort(Comparator.comparingInt(ingredient -> ingredient.getItems().length));
        for (Ingredient ingredient : needed)
        {
            int found = -1;
            for (int slot = 0; slot < copy.getContainerSize() && found < 0; slot++)
            {
                if (!copy.getItem(slot).isEmpty() && ingredient.test(copy.getItem(slot)))
                {
                    found = slot;
                }
            }
            if (found < 0)
            {
                return false;
            }
            ItemStack used = copy.getItem(found);
            if (used.hasCraftingRemainingItem())
            {
                leftovers.add(used.getCraftingRemainingItem());
            }
            used.shrink(1);
        }
        leftovers.add(0, recipe.getResultItem(this.level.registryAccess()).copy());
        for (ItemStack out : leftovers)
        {
            if (!HopperBlockEntity.addItem(null, copy, out, null).isEmpty())
            {
                return false;
            }
        }
        if (!simulate)
        {
            for (int slot = 0; slot < store.getContainerSize(); slot++)
            {
                store.setItem(slot, copy.getItem(slot));
            }
            this.setChanged();
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (this.recipe != null)
        {
            tag.putString("Recipe", this.recipe.toString());
        }
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.recipe = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
    }
}
