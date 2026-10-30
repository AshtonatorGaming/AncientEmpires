package com.ancientempires.entity.npc;

import net.minecraftforge.items.IItemHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.ancientempires.item.OrderItem;
import com.ancientempires.item.UpkeepOrderItem;
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
    /** A container named by an Upkeep Order (not a hall) to fetch from, while walking there. */
    @Nullable
    private BlockPos pantryAt;
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
        if (!this.npc.getNeeds().mealDue() || !this.free())
        {
            return false;
        }
        if (this.npc.getNeeds().mealFromCredit())
        {
            return false;
        }
        if (this.foodSlot() >= 0)
        {
            return true;
        }
        BlockPos pantry = this.pantry();
        if (pantry != null)
        {
            IItemHandler store = this.handlerAt(pantry);
            if (store == null || storedRations(store) <= 0.0D)
            {
                this.npc.getNeeds().setHungry(true);
                this.nextCheck = this.npc.tickCount + RETRY_TICKS;
                return false;
            }
            if (AncientEmpiresConfig.upkeepDelivery == AncientEmpiresConfig.Delivery.TELEPORT)
            {
                this.takeFrom(store);
                return this.foodSlot() >= 0;
            }
            this.pantryAt = pantry;
            return true;
        }
        TownHallBlockEntity hall = this.hall();
        if (hall == null && !AncientEmpiresConfig.upkeepRequireHall)
        {
            // No Town Hall in reach: this NPC lives off the land, as before halls existed.
            this.npc.getNeeds().skipMeal();
            return false;
        }
        if (hall == null || hall.storedRations() <= 0.0D)
        {
            this.npc.getNeeds().setHungry(true);
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
        return this.fetchFrom != null || this.pantryAt != null || (this.npc.getNeeds().mealDue() && this.foodSlot() >= 0);
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
        this.pantryAt = null;
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
            if (!this.npc.getNeeds().mealDue())
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
        if (this.pantryAt != null)
        {
            this.walkToPantry();
            return;
        }
        int slot = this.foodSlot();
        if (slot >= 0 && this.npc.getNeeds().mealDue())
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

    /** Walks to the ordered container and takes its food there, as at a hall. */
    private void walkToPantry()
    {
        BlockPos pos = this.pantryAt;
        if (++this.walkTicks > GIVE_UP_TICKS)
        {
            this.pantryAt = null;
            return;
        }
        if (this.npc.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > REACH_SQR)
        {
            if (--this.repath <= 0)
            {
                this.repath = 20;
                // A chest is solid: the path ends beside it.
                this.npc.getNavigation().moveTo(this.npc.getNavigation().createPath(pos, 1), 1.0D);
            }
            return;
        }
        this.npc.getNavigation().stop();
        this.npc.swing(InteractionHand.MAIN_HAND);
        IItemHandler store = this.handlerAt(pos);
        if (store != null)
        {
            this.takeFrom(store);
        }
        this.pantryAt = null;
    }

    /**
     * The container an Upkeep Order names, when it is not a Town Hall (halls keep
     * their own path): in this dimension, within {@code upkeep.range} as a hall must be, in a loaded chunk (a
     * stored spot is never read otherwise: that would load its chunk), and a container the NPC may use ({@link NpcStores}).
     */
    @Nullable
    private BlockPos pantry()
    {
        ItemStack order = this.npc.getInventory().upkeepItem();
        if (!(order.getItem() instanceof UpkeepOrderItem))
        {
            return null;
        }
        double range = AncientEmpiresConfig.upkeepRange;
        for (OrderItem.Stop stop : OrderItem.stops(order))
        {
            if (stop.pos().distToCenterSqr(this.npc.position()) > range * range)
            {
                continue;
            }
            BlockEntity entity = NpcStores.at(this.npc.level(), stop.dimension(), stop.pos());
            if (entity != null && !(entity instanceof TownHallBlockEntity) && NpcStores.mayUse(this.npc, entity))
            {
                return stop.pos();
            }
        }
        return null;
    }

    @Nullable
    private IItemHandler handlerAt(BlockPos pos)
    {
        return NpcStores.handler(this.npc, pos);
    }

    /** Rations in a container (food counted as the hall counts it). */
    private static double storedRations(IItemHandler store)
    {
        double total = 0.0D;
        for (int slot = 0; slot < store.getSlots(); slot++)
        {
            ItemStack stack = store.getStackInSlot(slot);
            total += TownHallBlockEntity.rations(stack) * stack.getCount();
        }
        return total;
    }

    /** Takes {@code upkeep.carryRations} of food from a container into the backpack; what does not fit stays. */
    private void takeFrom(IItemHandler store)
    {
        double got = 0.0D;
        for (int slot = 0; slot < store.getSlots() && got < AncientEmpiresConfig.upkeepCarryRations; slot++)
        {
            ItemStack stack = store.getStackInSlot(slot);
            double each = TownHallBlockEntity.rations(stack);
            if (each <= 0.0D)
            {
                continue;
            }
            int count = 0;
            while (count < stack.getCount() && got + count * each < AncientEmpiresConfig.upkeepCarryRations)
            {
                count++;
            }
            ItemStack taken = store.extractItem(slot, Math.max(1, count), true);
            ItemStack rest = this.npc.getInventory().addToBackpack(taken);
            int fits = taken.getCount() - rest.getCount();
            if (fits > 0)
            {
                store.extractItem(slot, fits, false);
                got += fits * each;
            }
            if (!rest.isEmpty())
            {
                break;
            }
        }
    }

    /** Takes {@code upkeep.carryRations} of food into the backpack; what does not fit goes back. */
    private void takeFrom(TownHallBlockEntity hall)
    {
        for (ItemStack food : hall.takeFood(AncientEmpiresConfig.upkeepCarryRations))
        {
            ItemStack rest = this.npc.getInventory().addToBackpack(food);
            if (!rest.isEmpty())
            {
                // Back into the hall; what its store will not take falls beside it rather than vanishing.
                ItemStack left = HopperBlockEntity.addItem(null, hall.store(), rest, null);
                if (!left.isEmpty())
                {
                    Block.popResource(this.npc.level(), hall.getBlockPos().above(), left);
                }
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
        ItemStack order = this.npc.getInventory().upkeepItem();
        if (order.getItem() instanceof UpkeepOrderItem)
        {
            // An Upkeep Order names the hall: its owner's, in this dimension.
            for (OrderItem.Stop stop : OrderItem.stops(order))
            {
                if (NpcStores.at(level, stop.dimension(), stop.pos()) instanceof TownHallBlockEntity hall
                        && this.npc.getOwner().equals(hall.owner()))
                {
                    return hall;
                }
            }
        }
        return NpcUpkeep.hallFor(level, this.npc.blockPosition(), this.npc.getOwner());
    }
}
