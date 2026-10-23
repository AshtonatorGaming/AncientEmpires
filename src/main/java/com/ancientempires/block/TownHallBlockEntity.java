package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import com.ancientempires.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ancient Warfare's town hall: the food store its owner's NPCs nearby come to
 * for their rations, and the record of owned NPCs that died nearby, kept as
 * their packed scroll (with whatever {@code upkeep.keepOnDeath} kept) plus how
 * and where they died, for a priest to raise later.
 */
public final class TownHallBlockEntity extends WorksiteBlockEntity
{
    /** One death: the NPC as a scroll, the death message, where, and the game time. */
    public record Fallen(ItemStack scroll, Component message, BlockPos pos, long time)
    {
    }

    private final List<Fallen> fallen = new ArrayList<>();

    public TownHallBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.TOWN_HALL.get(), pos, state);
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

    private static final List<Supply> SUPPLIES = List.of(
            new Supply("food", stack -> stack.isEdible() && rations(stack) > 0.0D, () -> new ItemStack(ModItems.FOOD_BUNDLE.get()), 6),
            new Supply("offering", TownHallBlockEntity::isOffering, TownHallBlockEntity::offeringIcon, 3));

    /** Food for upkeep, and the priest's offering for a revival ({@code priest.costItem}). */
    @Override
    public List<Supply> supplies()
    {
        return SUPPLIES;
    }

    private static boolean isOffering(ItemStack stack)
    {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.toString().equals(AncientEmpiresConfig.priestCostItem);
    }

    private static ItemStack offeringIcon()
    {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(AncientEmpiresConfig.priestCostItem));
        return new ItemStack(item == null ? Items.GOLD_INGOT : item);
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

    @Override
    public void onLoad()
    {
        super.onLoad();
        if (this.level instanceof ServerLevel server)
        {
            NpcUpkeep.register(server, this.worldPosition);
        }
    }

    /** Rations one piece of food is worth: a food bundle {@code upkeep.bundleRations}, other food its nutrition over a ration. */
    public static double rations(ItemStack stack)
    {
        if (stack.is(ModItems.FOOD_BUNDLE.get()))
        {
            return AncientEmpiresConfig.upkeepBundleRations;
        }
        FoodProperties food = stack.getFoodProperties(null);
        return food == null ? 0.0D : (double) food.getNutrition() / Math.max(1, AncientEmpiresConfig.upkeepRation);
    }

    /**
     * Takes food worth up to {@code wanted} rations from the store, whole items,
     * for an NPC to carry. Returns what was taken.
     */
    public List<ItemStack> takeFood(double wanted)
    {
        List<ItemStack> taken = new ArrayList<>();
        SimpleContainer store = this.store();
        double got = 0.0D;
        for (int slot = 0; slot < store.getContainerSize() && got < wanted; slot++)
        {
            ItemStack stack = store.getItem(slot);
            double each = rations(stack);
            if (each <= 0.0D)
            {
                continue;
            }
            int count = 0;
            while (count < stack.getCount() && got < wanted)
            {
                count++;
                got += each;
            }
            taken.add(stack.split(count));
            store.setItem(slot, stack);
        }
        if (!taken.isEmpty())
        {
            this.setChanged();
        }
        return taken;
    }

    /** Food in the store, in rations. */
    public double storedRations()
    {
        double total = 0.0D;
        SimpleContainer store = this.store();
        for (int slot = 0; slot < store.getContainerSize(); slot++)
        {
            ItemStack stack = store.getItem(slot);
            total += rations(stack) * stack.getCount();
        }
        return total;
    }

    /** Records a fallen NPC, oldest first out past {@code upkeep.fallenRecords}. */
    void recordFallen(ItemStack scroll, Component message, BlockPos pos, long time)
    {
        if (scroll.isEmpty())
        {
            return;
        }
        this.fallen.add(new Fallen(scroll.copy(), message, pos.immutable(), time));
        while (this.fallen.size() > Math.max(1, AncientEmpiresConfig.upkeepFallenRecords))
        {
            this.fallen.remove(0);
        }
        this.setChanged();
    }

    public List<Fallen> fallen()
    {
        return List.copyOf(this.fallen);
    }

    /** Removes and returns the longest-fallen record, for a priest to raise. */
    @Nullable
    public Fallen takeOldestFallen()
    {
        if (this.fallen.isEmpty())
        {
            return null;
        }
        Fallen first = this.fallen.remove(0);
        this.setChanged();
        return first;
    }

    /** Whether the store holds {@code count} of the item. */
    public boolean has(Item item, int count)
    {
        int found = 0;
        SimpleContainer store = this.store();
        for (int slot = 0; slot < store.getContainerSize() && found < count; slot++)
        {
            ItemStack stack = store.getItem(slot);
            if (stack.is(item))
            {
                found += stack.getCount();
            }
        }
        return found >= count;
    }

    /** Takes {@code count} of the item from the store; call {@link #has} first. */
    public void take(Item item, int count)
    {
        SimpleContainer store = this.store();
        for (int slot = 0; slot < store.getContainerSize() && count > 0; slot++)
        {
            ItemStack stack = store.getItem(slot);
            if (stack.is(item))
            {
                int used = Math.min(count, stack.getCount());
                stack.shrink(used);
                count -= used;
                store.setItem(slot, stack);
            }
        }
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (Fallen entry : this.fallen)
        {
            CompoundTag record = new CompoundTag();
            record.put("Scroll", entry.scroll().save(new CompoundTag()));
            record.putString("Message", Component.Serializer.toJson(entry.message()));
            record.putLong("Pos", entry.pos().asLong());
            record.putLong("Time", entry.time());
            list.add(record);
        }
        tag.put("Fallen", list);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.fallen.clear();
        for (Tag element : tag.getList("Fallen", Tag.TAG_COMPOUND))
        {
            CompoundTag record = (CompoundTag) element;
            ItemStack scroll = ItemStack.of(record.getCompound("Scroll"));
            if (scroll.isEmpty())
            {
                continue;
            }
            Component message = parse(record.getString("Message"));
            this.fallen.add(new Fallen(scroll, message == null ? scroll.getHoverName() : message,
                    BlockPos.of(record.getLong("Pos")), record.getLong("Time")));
        }
    }

    @Nullable
    private static Component parse(String json)
    {
        try
        {
            return json.isEmpty() ? null : Component.Serializer.fromJson(json);
        }
        catch (RuntimeException exception)
        {
            return null;
        }
    }
}
