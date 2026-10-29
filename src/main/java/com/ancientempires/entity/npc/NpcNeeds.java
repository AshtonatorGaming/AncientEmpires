package com.ancientempires.entity.npc;

import com.ancientempires.block.TownHallBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What an NPC's daily life holds on to: its meals ({@link NpcUpkeepGoal}:
 * when the next is due, rations eaten ahead, hungry when a hall had none),
 * the bed it claimed in vanilla's bed index ({@link NpcSleepGoal}), and the
 * worksite it last worked at. Saved as {@code AeWorksite}, {@code AeBed},
 * {@code AeHungry}, {@code AeNextMeal}, {@code AeMealCredit}; not packed.
 */
public final class NpcNeeds
{
    private static final String WORKSITE_KEY = "AeWorksite";
    private static final String HUNGRY_KEY = "AeHungry";
    private static final String NEXT_MEAL_KEY = "AeNextMeal";
    private static final String MEAL_CREDIT_KEY = "AeMealCredit";
    private static final String BED_KEY = "AeBed";

    private final NpcEntity npc;
    @Nullable
    private BlockPos worksite;
    @Nullable
    private BlockPos bed;
    private boolean hungry;
    /** Game time the next meal is due; unset until first asked (a new NPC arrives fed). */
    private long nextMeal = -1L;
    /** Rations eaten ahead (a food bundle covers several days). */
    private double mealCredit;

    NpcNeeds(NpcEntity npc)
    {
        this.npc = npc;
    }

    /** The worksite this NPC last worked at, such as its crop farm; checked again before each shift. */
    @Nullable
    public BlockPos worksite()
    {
        return this.worksite;
    }

    public void setWorksite(@Nullable BlockPos worksite)
    {
        this.worksite = worksite == null ? null : worksite.immutable();
    }

    /** The bed this NPC has claimed in vanilla's bed index, or null. */
    @Nullable
    public BlockPos bed()
    {
        return this.bed;
    }

    void setBed(@Nullable BlockPos bed)
    {
        this.bed = bed == null ? null : bed.immutable();
    }

    /** Gives the claimed bed back to the bed index (death, repack, or the bed is gone). */
    void releaseBed()
    {
        if (this.bed != null && this.npc.level() instanceof ServerLevel level)
        {
            if (this.npc.isSleeping())
            {
                this.npc.stopSleeping();
            }
            // A broken bed is already gone from the index, and releasing it there throws.
            if (level.getPoiManager().existsAtPosition(PoiTypes.HOME, this.bed))
            {
                level.getPoiManager().release(this.bed);
            }
        }
        this.bed = null;
    }

    /** Missed its last upkeep: a hungry worker does not work. Set by the Town Hall. */
    public boolean hungry()
    {
        return this.hungry;
    }

    public void setHungry(boolean hungry)
    {
        this.hungry = hungry;
    }

    /** An owned NPC whose meal is due ({@code upkeep.mealInterval}). Faction NPCs live off their faction. */
    public boolean mealDue()
    {
        if (!AncientEmpiresConfig.upkeepEnabled || !this.npc.isOwnedNpc() || this.npc.isCreative())
        {
            return false;
        }
        long now = this.npc.level().getGameTime();
        if (this.nextMeal < 0L)
        {
            // A new NPC arrives fed.
            this.nextMeal = now + AncientEmpiresConfig.upkeepMealInterval;
        }
        return now >= this.nextMeal;
    }

    /** Takes a meal from rations already eaten ahead. Returns false if there are none. */
    public boolean mealFromCredit()
    {
        if (this.mealCredit < 1.0D)
        {
            return false;
        }
        this.mealCredit -= 1.0D;
        this.nextMeal = this.npc.level().getGameTime() + AncientEmpiresConfig.upkeepMealInterval;
        this.hungry = false;
        return true;
    }

    /** A meal that needs no food (no Town Hall to depend on): the NPC is fed until the next one. */
    public void skipMeal()
    {
        this.nextMeal = this.npc.level().getGameTime() + AncientEmpiresConfig.upkeepMealInterval;
        this.hungry = false;
    }

    /** Food the NPC ate (the vanilla way): it counts toward its rations, and a due meal is taken from them. */
    void ate(ItemStack food)
    {
        this.mealCredit += TownHallBlockEntity.rations(food);
        this.mealFromCredit();
    }

    void save(CompoundTag tag)
    {
        if (this.worksite != null)
        {
            tag.putLong(WORKSITE_KEY, this.worksite.asLong());
        }
        if (this.hungry)
        {
            tag.putBoolean(HUNGRY_KEY, true);
        }
        if (this.nextMeal >= 0L)
        {
            tag.putLong(NEXT_MEAL_KEY, this.nextMeal);
        }
        if (this.mealCredit > 0.0D)
        {
            tag.putDouble(MEAL_CREDIT_KEY, this.mealCredit);
        }
        if (this.bed != null)
        {
            tag.putLong(BED_KEY, this.bed.asLong());
        }
    }

    void load(CompoundTag tag)
    {
        this.worksite = tag.contains(WORKSITE_KEY) ? BlockPos.of(tag.getLong(WORKSITE_KEY)) : null;
        this.hungry = tag.getBoolean(HUNGRY_KEY);
        this.nextMeal = tag.contains(NEXT_MEAL_KEY) ? tag.getLong(NEXT_MEAL_KEY) : -1L;
        this.mealCredit = tag.getDouble(MEAL_CREDIT_KEY);
        this.bed = tag.contains(BED_KEY) ? BlockPos.of(tag.getLong(BED_KEY)) : null;
    }
}
