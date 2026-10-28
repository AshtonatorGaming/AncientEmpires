package com.ancientempires.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The invisible thing a player rides to sit on one of AW's seats (AW's
 * {@code EntitySeat}). It goes away once nobody sits on it or its seat is gone,
 * and is never saved.
 */
public class SeatEntity extends Entity
{
    private BlockPos seat = BlockPos.ZERO;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;
    }

    public void configure(Vec3 at, BlockPos seat)
    {
        this.setPos(at.x, at.y, at.z);
        this.seat = seat.immutable();
    }

    public BlockPos seat()
    {
        return this.seat;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!this.level().isClientSide && (this.getPassengers().isEmpty() || !(this.level().getBlockState(this.seat).getBlock() instanceof AwDecorBlock)))
        {
            this.ejectPassengers();
            this.discard();
        }
    }

    @Override
    public double getPassengersRidingOffset()
    {
        return 0.0D;
    }

    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }

    @Override
    protected void defineSynchedData()
    {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
    }
}
