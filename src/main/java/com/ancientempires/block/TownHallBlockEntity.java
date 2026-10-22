package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import com.ancientempires.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Ancient Warfare's town hall: its owner's NPCs nearby eat from its store once
 * a day ({@link NpcUpkeep}), and owned NPCs that die nearby are recorded here,
 * packed as their scroll without the gear they dropped, for a priest to raise
 * later. Food is kept as a running stock of nutrition: a food bundle is worth
 * {@code upkeep.bundleRations} rations, other food its own nutrition.
 */
public final class TownHallBlockEntity extends WorksiteBlockEntity
{
    private int pantry;
    private final List<ItemStack> fallen = new ArrayList<>();

    public TownHallBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.TOWN_HALL.get(), pos, state);
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

    @Override
    public void onLoad()
    {
        super.onLoad();
        if (this.level instanceof ServerLevel server)
        {
            NpcUpkeep.register(server, this.worldPosition);
        }
    }

    /**
     * Takes one day's ration for one NPC from the stock, opening food from the
     * store as needed. Returns false when there is not enough food.
     */
    boolean feedOne()
    {
        int ration = Math.max(1, AncientEmpiresConfig.upkeepRation);
        SimpleContainer store = this.store();
        for (int slot = 0; slot < store.getContainerSize() && this.pantry < ration; slot++)
        {
            ItemStack stack = store.getItem(slot);
            while (!stack.isEmpty() && this.pantry < ration)
            {
                int value = nutrition(stack);
                if (value <= 0)
                {
                    break;
                }
                this.pantry += value;
                stack.shrink(1);
            }
            store.setItem(slot, stack);
        }
        if (this.pantry < ration)
        {
            return false;
        }
        this.pantry -= ration;
        this.setChanged();
        return true;
    }

    private static int nutrition(ItemStack stack)
    {
        if (stack.is(ModItems.FOOD_BUNDLE.get()))
        {
            return AncientEmpiresConfig.upkeepBundleRations * Math.max(1, AncientEmpiresConfig.upkeepRation);
        }
        FoodProperties food = stack.getFoodProperties(null);
        return food == null ? 0 : food.getNutrition();
    }

    /** Records a fallen NPC, oldest first out past {@code upkeep.fallenRecords}. */
    void recordFallen(ItemStack packed)
    {
        if (packed.isEmpty())
        {
            return;
        }
        this.fallen.add(packed.copy());
        while (this.fallen.size() > Math.max(1, AncientEmpiresConfig.upkeepFallenRecords))
        {
            this.fallen.remove(0);
        }
        this.setChanged();
    }

    public List<ItemStack> fallen()
    {
        return List.copyOf(this.fallen);
    }

    public int pantry()
    {
        return this.pantry;
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putInt("Pantry", this.pantry);
        ListTag list = new ListTag();
        for (ItemStack stack : this.fallen)
        {
            list.add(stack.save(new CompoundTag()));
        }
        tag.put("Fallen", list);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.pantry = tag.getInt("Pantry");
        this.fallen.clear();
        for (Tag element : tag.getList("Fallen", Tag.TAG_COMPOUND))
        {
            ItemStack stack = ItemStack.of((CompoundTag) element);
            if (!stack.isEmpty())
            {
                this.fallen.add(stack);
            }
        }
    }
}
