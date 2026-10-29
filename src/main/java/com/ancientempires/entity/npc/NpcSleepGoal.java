package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Sleep at night, as villagers do. An owned worker or settler (and a soldier,
 * if {@code sleep.soldiers}) claims a free bed near its home through vanilla's
 * bed index, the same claim villagers make, so no two sleep in one bed. At
 * night it walks to the bed and lies down; at dawn it gets up. Being hurt wakes
 * it (vanilla), and a fight, an order, or a flight comes first. The claim is
 * kept, saved as {@code AeBed}, and let go when the bed is gone, or the NPC dies
 * or is packed up.
 */
public final class NpcSleepGoal extends Goal
{
    private static final int CHECK_INTERVAL = 40;
    private static final double REACH_SQR = 1.8D * 1.8D;
    private static final int GIVE_UP_TICKS = 600;

    private final NpcEntity npc;
    private int nextCheck;
    private int ticks;
    private int repath;

    public NpcSleepGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private boolean sleepy()
    {
        if (!AncientEmpiresConfig.sleepEnabled || !this.npc.isOwnedNpc() || this.npc.isFollowing() || this.npc.getTarget() != null
                || this.npc.isPassenger() || !this.npc.level().isNight())
        {
            return false;
        }
        return !this.npc.getNpcPath().combat() || AncientEmpiresConfig.sleepSoldiers;
    }

    @Override
    public boolean canUse()
    {
        if (this.npc.isSleeping())
        {
            return true;
        }
        if (this.npc.tickCount < this.nextCheck)
        {
            return false;
        }
        this.nextCheck = this.npc.tickCount + CHECK_INTERVAL + (this.npc.getId() & 15);
        return this.sleepy() && this.bed() != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        if (this.npc.isSleeping())
        {
            return this.sleepy();
        }
        return this.sleepy() && this.ticks < GIVE_UP_TICKS && this.npc.getNeeds().bed() != null;
    }

    @Override
    public void start()
    {
        this.ticks = 0;
        this.repath = 0;
    }

    @Override
    public void stop()
    {
        if (this.npc.isSleeping())
        {
            this.npc.stopSleeping();
        }
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        BlockPos bed = this.npc.getNeeds().bed();
        if (bed == null || this.npc.isSleeping())
        {
            return;
        }
        this.ticks++;
        if (this.npc.distanceToSqr(bed.getX() + 0.5D, bed.getY() + 0.5D, bed.getZ() + 0.5D) > REACH_SQR)
        {
            if (--this.repath <= 0)
            {
                this.repath = 20;
                this.npc.getNavigation().moveTo(bed.getX() + 0.5D, bed.getY(), bed.getZ() + 0.5D, 0.8D);
            }
            return;
        }
        BlockState state = this.npc.level().getBlockState(bed);
        if (state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED))
        {
            this.npc.getNavigation().stop();
            this.npc.startSleeping(bed);
        }
    }

    /**
     * The NPC's bed if it still stands, else a newly claimed free bed within
     * {@code sleep.bedRange} of its home, or null.
     */
    private BlockPos bed()
    {
        BlockPos bed = this.npc.getNeeds().bed();
        if (bed != null && isBedHead(this.npc.level().getBlockState(bed)))
        {
            return bed;
        }
        if (!(this.npc.level() instanceof ServerLevel level))
        {
            return null;
        }
        if (bed != null)
        {
            this.npc.getNeeds().releaseBed();
        }
        BlockPos center = this.npc.getHome() != null ? this.npc.getHome() : this.npc.blockPosition();
        Optional<BlockPos> claimed = level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> true,
                center, AncientEmpiresConfig.sleepBedRange);
        claimed.ifPresent(this.npc.getNeeds()::setBed);
        return claimed.orElse(null);
    }

    private static boolean isBedHead(BlockState state)
    {
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }
}
