package com.ancientempires.entity.npc;

import com.ancientempires.item.CombatOrderItem;
import com.ancientempires.item.OrderItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Ancient Warfare's patrol (NpcAIPlayerOwnedPatrol): an owned soldier with a
 * Combat Order in its orders slot walks the points in turn, day and night,
 * waiting {@link #WATCH_TICKS} at each "stand watch" point. Its target scan runs
 * as always, so it fights what it meets and takes up the round again after.
 * Not while a unit order holds it (a chain's order other than Free), following,
 * holding a post, or broken.
 */
final class NpcPatrolGoal extends Goal
{
    private static final int CHECK_INTERVAL = 40;
    /** Blocks from a point at which it counts as reached. */
    private static final double AT_POINT = 2.0D;
    private static final int WATCH_TICKS = 200;
    private static final int GIVE_UP_TICKS = 600;
    private static final int REPATH_TICKS = 20;
    /** AW's patrols walked: slower than the chase. */
    private static final double SPEED = 0.8D;

    private final NpcEntity npc;
    private int nextCheck;
    private int point;
    private int walking;
    private int watching = -1;
    private int repath;

    NpcPatrolGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private List<OrderItem.Stop> route()
    {
        return this.npc.getInventory().ordersItem().getItem() instanceof CombatOrderItem ? OrderItem.stops(this.npc.getInventory().ordersItem()) : List.of();
    }

    private boolean onDuty()
    {
        NpcChain chain = this.npc.getChain();
        return this.npc.getNpcPath().combat() && this.npc.isOwnedNpc() && this.npc.getTarget() == null && !this.npc.isFollowing()
                && !this.npc.getHold().holding() && !this.npc.avoidsFights() && (!chain.inChain() || chain.effectiveOrder() == NpcOrder.FREE);
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
        this.watching = -1;
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
        if (this.point >= route.size())
        {
            this.point = 0;
        }
        OrderItem.Stop stop = route.get(this.point);
        if (!stop.dimension().equals(this.npc.level().dimension().location().toString()))
        {
            this.next();
            return;
        }
        // A point is the block clicked; the soldier stands on it.
        BlockPos stand = stop.pos().above();
        if (this.watching >= 0)
        {
            if (this.npc.tickCount % 40 == 0)
            {
                // Standing watch: looks about.
                this.npc.getLookControl().setLookAt(this.npc.getX() + this.npc.getRandom().nextGaussian() * 8.0D, this.npc.getEyeY(),
                        this.npc.getZ() + this.npc.getRandom().nextGaussian() * 8.0D);
            }
            if (--this.watching < 0)
            {
                this.next();
            }
            return;
        }
        if (this.npc.position().distanceToSqr(Vec3.atBottomCenterOf(stand)) > AT_POINT * AT_POINT)
        {
            if (++this.walking > GIVE_UP_TICKS)
            {
                this.next();
                return;
            }
            if (--this.repath <= 0)
            {
                this.repath = REPATH_TICKS;
                this.npc.getNavigation().moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, SPEED);
            }
            return;
        }
        this.npc.getNavigation().stop();
        if (CombatOrderItem.Mode.of(stop.mode()) == CombatOrderItem.Mode.WATCH)
        {
            this.watching = WATCH_TICKS;
        }
        else
        {
            this.next();
        }
    }

    private void next()
    {
        this.point++;
        this.walking = 0;
        this.watching = -1;
        this.repath = 0;
    }
}
