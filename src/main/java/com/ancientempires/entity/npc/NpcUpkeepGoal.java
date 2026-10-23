package com.ancientempires.entity.npc;

import com.ancientempires.block.NpcUpkeep;
import com.ancientempires.block.TownHallBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Upkeep, by the NPC itself. When a meal is due it eats a ration it already ate
 * ahead (a food bundle covers several days), or food from its backpack, the
 * vanilla way: the food goes to its hand and it eats it, so animation mods
 * that show players eating can show NPCs too. With no food on it, it walks to
 * its owner's Town Hall and takes {@code upkeep.carryRations} (or has it
 * delivered, {@code upkeep.delivery}), so soldiers on campaign can carry their
 * own. With no food anywhere it goes hungry, and asks again later. A fight,
 * an order, or a flight comes first.
 */
public final class NpcUpkeepGoal extends Goal
{
    private static final int CHECK_INTERVAL = 40;
    private static final int RETRY_TICKS = 600;
    private static final int GIVE_UP_TICKS = 600;
    private static final double REACH_SQR = 2.5D * 2.5D;

    private final NpcEntity npc;
    private int nextCheck;
    @Nullable
    private TownHallBlockEntity fetchFrom;
    private int walkTicks;
    private int repath;
    /** Backpack index of the item swapped into the hand to eat, or -1. */
    private int swappedSlot = -1;

    public NpcUpkeepGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        if (this.npc.tickCount < this.nextCheck)
        {
            return false;
        }
        this.nextCheck = this.npc.tickCount + CHECK_INTERVAL + (this.npc.getId() & 15);
        if (!this.npc.isMealDue() || !this.free())
        {
            return false;
        }
        if (this.npc.mealFromCredit())
        {
            return false;
        }
        if (this.foodSlot() >= 0)
        {
            return true;
        }
        TownHallBlockEntity hall = this.hall();
        if (hall == null && !AncientEmpiresConfig.upkeepRequireHall)
        {
            // No Town Hall in reach: this NPC lives off the land, as before halls existed.
            this.npc.skipMeal();
            return false;
        }
        if (hall == null || hall.storedRations() <= 0.0D)
        {
            this.npc.setHungry(true);
            this.nextCheck = this.npc.tickCount + RETRY_TICKS;
            return false;
        }
        if (AncientEmpiresConfig.upkeepDelivery == AncientEmpiresConfig.Delivery.TELEPORT)
        {
            this.takeFrom(hall);
            return this.foodSlot() >= 0;
        }
        this.fetchFrom = hall;
        return true;
    }

    private boolean free()
    {
        return this.npc.getTarget() == null && !this.npc.isFollowing() && !this.npc.isUsingItem() && !this.npc.isOnFire();
    }

    @Override
    public boolean canContinueToUse()
    {
        if (this.swappedSlot >= 0)
        {
            // Mid-meal: only a fight breaks it off.
            return this.npc.getTarget() == null;
        }
        if (!this.free())
        {
            return false;
        }
        return this.fetchFrom != null || (this.npc.isMealDue() && this.foodSlot() >= 0);
    }

    @Override
    public void start()
    {
        this.walkTicks = 0;
        this.repath = 0;
    }

    @Override
    public void stop()
    {
        if (this.npc.isUsingItem() && this.swappedSlot >= 0)
        {
            this.npc.stopUsingItem();
        }
        this.swapBack();
        this.fetchFrom = null;
        this.npc.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        if (this.npc.isUsingItem())
        {
            // Vanilla runs the meal; NpcEntity.eat counts it.
            return;
        }
        if (this.swappedSlot >= 0)
        {
            this.swapBack();
            if (!this.npc.isMealDue())
            {
                this.nextCheck = 0;
                this.fetchFrom = null;
                return;
            }
        }
        if (this.fetchFrom != null)
        {
            this.walkToHall();
            return;
        }
        int slot = this.foodSlot();
        if (slot >= 0 && this.npc.isMealDue())
        {
            this.startEating(slot);
        }
    }

    private void walkToHall()
    {
        TownHallBlockEntity hall = this.fetchFrom;
        if (hall == null || hall.isRemoved() || ++this.walkTicks > GIVE_UP_TICKS)
        {
            this.fetchFrom = null;
            return;
        }
        BlockPos pos = hall.getBlockPos();
        if (this.npc.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > REACH_SQR)
        {
            if (--this.repath <= 0)
            {
                this.repath = 20;
                this.npc.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 1.0D);
            }
            return;
        }
        this.npc.getNavigation().stop();
        this.npc.swing(InteractionHand.MAIN_HAND);
        this.takeFrom(hall);
        this.fetchFrom = null;
    }

    /** Takes {@code upkeep.carryRations} of food into the backpack; what does not fit goes back. */
    private void takeFrom(TownHallBlockEntity hall)
    {
        for (ItemStack food : hall.takeFood(AncientEmpiresConfig.upkeepCarryRations))
        {
            ItemStack rest = this.npc.getInventory().addToBackpack(food);
            if (!rest.isEmpty())
            {
                HopperBlockEntity.addItem(null, hall.store(), rest, null);
            }
        }
    }

    /** Food goes to the main hand and the NPC eats it, as a player would. */
    private void startEating(int backpackSlot)
    {
        this.npc.getInventory().swapMainHandWithBackpack(backpackSlot);
        this.swappedSlot = backpackSlot;
        this.npc.startUsingItem(InteractionHand.MAIN_HAND);
        if (!this.npc.isUsingItem())
        {
            this.swapBack();
        }
    }

    private void swapBack()
    {
        if (this.swappedSlot >= 0)
        {
            this.npc.getInventory().swapMainHandWithBackpack(this.swappedSlot);
            this.swappedSlot = -1;
        }
    }

    /** The backpack index (0 to 8) of something to eat, or -1. */
    private int foodSlot()
    {
        NpcInventory inventory = this.npc.getInventory();
        for (int index = 0; index < NpcInventory.BACKPACK_SLOTS; index++)
        {
            ItemStack stack = inventory.getItem(NpcInventory.BACKPACK_START + index);
            if (!stack.isEmpty() && stack.isEdible() && TownHallBlockEntity.rations(stack) > 0.0D)
            {
                return index;
            }
        }
        return -1;
    }

    @Nullable
    private TownHallBlockEntity hall()
    {
        if (!(this.npc.level() instanceof ServerLevel level) || this.npc.getOwner() == null)
        {
            return null;
        }
        return NpcUpkeep.hallFor(level, this.npc.blockPosition(), this.npc.getOwner());
    }
}
