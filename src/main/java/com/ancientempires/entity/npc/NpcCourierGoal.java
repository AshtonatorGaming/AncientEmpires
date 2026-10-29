package com.ancientempires.entity.npc;

import com.ancientempires.block.WorksiteBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.item.OrderItem;
import com.ancientempires.item.RoutingOrderItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.EnumSet;
import java.util.List;

/**
 * Ancient Warfare's courier (NpcAIPlayerOwnedCourier): a Worker-path NPC
 * holding a Routing Order walks its stops in order, round and round, by day
 * and in dry weather. At each container it moves items between its backpack
 * and the container as the stop says, takes a moment over it (shorter with
 * level, longer the more it moved), and goes on. A stop it cannot reach, or
 * that is gone, another player's worksite, or in another dimension, is passed by.
 */
final class NpcCourierGoal extends Goal
{
    private static final int CHECK_INTERVAL = 40;
    /** Blocks from a stop's block at which the courier works it. */
    private static final double AT_STOP = 2.5D;
    /** Ticks walking toward one stop before passing it by. */
    private static final int GIVE_UP_TICKS = 600;
    private static final int REPATH_TICKS = 20;
    /** Ticks per item moved, and the most and least a stop takes. */
    private static final int TICKS_PER_ITEM = 2;
    private static final int MIN_WORK = 10;
    private static final int MAX_WORK = 80;

    private final NpcEntity npc;
    private int nextCheck;
    private int stopIndex;
    private int walking;
    private int working = -1;
    private int repath;

    NpcCourierGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private List<OrderItem.Stop> route()
    {
        return OrderItem.stops(this.npc.getMainHandItem());
    }

    private boolean onDuty()
    {
        return this.npc.getNpcPath() == NpcPath.WORKER && this.npc.getMainHandItem().getItem() instanceof RoutingOrderItem && this.npc.isOwnedNpc()
                && this.npc.getTarget() == null && !this.npc.isFollowing() && !this.npc.getNeeds().hungry() && this.npc.level().isDay()
                && !this.npc.level().isRaining();
    }

    @Override
    public boolean canUse()
    {
        if (this.npc.tickCount < this.nextCheck)
        {
            return false;
        }
        this.nextCheck = this.npc.tickCount + CHECK_INTERVAL + (this.npc.getId() & 15);
        return this.onDuty() && !this.route().isEmpty();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.onDuty() && !this.route().isEmpty();
    }

    @Override
    public void start()
    {
        this.walking = 0;
        this.working = -1;
        this.repath = 0;
    }

    @Override
    public void stop()
    {
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        List<OrderItem.Stop> route = this.route();
        if (this.stopIndex >= route.size())
        {
            this.stopIndex = 0;
        }
        OrderItem.Stop stop = route.get(this.stopIndex);
        if (!stop.dimension().equals(this.npc.level().dimension().location().toString()))
        {
            this.next();
            return;
        }
        if (this.working >= 0)
        {
            if (this.working % 10 == 0)
            {
                this.npc.swing(InteractionHand.MAIN_HAND);
            }
            this.npc.getLookControl().setLookAt(Vec3.atCenterOf(stop.pos()));
            if (--this.working < 0)
            {
                this.next();
            }
            return;
        }
        BlockPos pos = stop.pos();
        if (this.npc.position().distanceToSqr(Vec3.atCenterOf(pos)) > AT_STOP * AT_STOP + 1.0D)
        {
            if (++this.walking > GIVE_UP_TICKS)
            {
                this.next();
                return;
            }
            if (--this.repath <= 0)
            {
                this.repath = REPATH_TICKS;
                // A container is solid: a path into it finds none, so the path ends beside it.
                this.npc.getNavigation().moveTo(this.npc.getNavigation().createPath(pos, 1), 1.0D);
            }
            return;
        }
        this.npc.getNavigation().stop();
        IItemHandler container = this.container(pos);
        if (container == null)
        {
            this.next();
            return;
        }
        int moved = transfer(this.npc.getInventory(), container, RoutingOrderItem.Mode.of(stop.mode()), stop.filter());
        if (moved > 0)
        {
            this.npc.addXp(AncientEmpiresConfig.levelXpPerWork);
        }
        // AW: the work time shrinks with level; ours with work speed, which levels raise.
        double speed = Math.max(0.25D, this.npc.getWorkSpeed());
        this.working = (int) Math.max(MIN_WORK, Math.min(MAX_WORK, moved * TICKS_PER_ITEM / speed));
    }

    private void next()
    {
        this.stopIndex++;
        this.walking = 0;
        this.working = -1;
        this.repath = 0;
    }

    /** The stop's item handler, unless the block is gone or is another player's worksite. */
    private IItemHandler container(BlockPos pos)
    {
        BlockEntity entity = this.npc.level().getBlockEntity(pos);
        if (entity == null || entity instanceof WorksiteBlockEntity site && site.owner() != null && !site.owner().equals(this.npc.getOwner()))
        {
            return null;
        }
        return entity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
    }

    /**
     * Moves what {@code mode} says between the backpack and {@code container}:
     * deposits every backpack stack it covers (but orders, which are the
     * courier's papers), or withdraws every container stack it covers into the
     * backpack's room. Returns the items moved.
     */
    static int transfer(NpcInventory backpack, IItemHandler container, RoutingOrderItem.Mode mode, ItemStack filter)
    {
        int moved = 0;
        if (mode.deposits())
        {
            for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
            {
                ItemStack held = backpack.getItem(slot);
                if (held.isEmpty() || held.getItem() instanceof OrderItem || !mode.moves(held, filter))
                {
                    continue;
                }
                ItemStack rest = ItemHandlerHelper.insertItemStacked(container, held.copy(), false);
                moved += held.getCount() - rest.getCount();
                backpack.setItem(slot, rest);
            }
            return moved;
        }
        for (int slot = 0; slot < container.getSlots(); slot++)
        {
            ItemStack inside = container.getStackInSlot(slot);
            if (inside.isEmpty() || !mode.moves(inside, filter))
            {
                continue;
            }
            ItemStack taken = container.extractItem(slot, inside.getCount(), true);
            ItemStack rest = backpack.addToBackpack(taken);
            int fits = taken.getCount() - rest.getCount();
            if (fits > 0)
            {
                container.extractItem(slot, fits, false);
                moved += fits;
            }
            if (!rest.isEmpty())
            {
                // The backpack is full.
                break;
            }
        }
        return moved;
    }
}
