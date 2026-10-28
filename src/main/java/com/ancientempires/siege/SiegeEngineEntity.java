package com.ancientempires.siege;

import com.ancientempires.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A placeholder for one of AW's siege engines (ballista, cannon, catapult,
 * trebuchet, ram, hwacha, chest cart, boats) as structures hold them: AW's own
 * model and texture by vehicle type and material level, turned as placed, its
 * turret as AW left it. It stands and can be broken (AW's health), but does not
 * move or fire yet; that is a later tier.
 */
public class SiegeEngineEntity extends Entity
{
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MATERIAL = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TURRET = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.FLOAT);

    private float health = 100.0F;

    public SiegeEngineEntity(EntityType<? extends SiegeEngineEntity> type, Level level)
    {
        super(type, level);
    }

    public static SiegeEngineEntity of(Level level, int vehicleType, int material, float yaw, float turret, float health)
    {
        SiegeEngineEntity engine = new SiegeEngineEntity(ModEntities.SIEGE_ENGINE.get(), level);
        engine.entityData.set(TYPE, vehicleType);
        engine.entityData.set(MATERIAL, Math.max(0, Math.min(4, material)));
        engine.entityData.set(TURRET, turret);
        engine.health = health > 0.0F ? health : 100.0F;
        engine.setYRot(yaw);
        engine.refreshDimensions();
        return engine;
    }

    public AwVehicles.Type vehicleType()
    {
        return AwVehicles.type(this.entityData.get(TYPE));
    }

    /** AW's material levels 0 to 4 pick its texture (wood to iron). */
    public int material()
    {
        return this.entityData.get(MATERIAL);
    }

    /** The turret's turn from the body, in degrees (AW's yaw less its turret rotation). */
    public float turret()
    {
        return this.entityData.get(TURRET);
    }

    @Override
    protected void defineSynchedData()
    {
        this.entityData.define(TYPE, 5);
        this.entityData.define(MATERIAL, 0);
        this.entityData.define(TURRET, 0.0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);
        if (TYPE.equals(key))
        {
            this.refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        AwVehicles.Type type = this.vehicleType();
        return EntityDimensions.fixed(type.width(), type.height());
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!this.isNoGravity() && !this.onGround())
        {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, 0.98D, 0.5D));
        }
    }

    @Override
    public boolean isPickable()
    {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (this.isInvulnerableTo(source) || this.level().isClientSide || this.isRemoved())
        {
            return false;
        }
        boolean creative = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
        this.health -= amount;
        this.playSound(SoundEvents.WOOD_HIT, 1.0F, 1.0F);
        if (creative || this.health <= 0.0F)
        {
            this.playSound(SoundEvents.WOOD_BREAK, 1.0F, 1.0F);
            this.discard();
        }
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        this.entityData.set(TYPE, tag.getInt("VehicleType"));
        this.entityData.set(MATERIAL, tag.getInt("Material"));
        this.entityData.set(TURRET, tag.getFloat("Turret"));
        this.health = tag.contains("Health") ? tag.getFloat("Health") : 100.0F;
        this.refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        tag.putInt("VehicleType", this.entityData.get(TYPE));
        tag.putInt("Material", this.entityData.get(MATERIAL));
        tag.putFloat("Turret", this.entityData.get(TURRET));
        tag.putFloat("Health", this.health);
    }
}
