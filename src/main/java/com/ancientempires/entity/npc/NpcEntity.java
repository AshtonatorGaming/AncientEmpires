package com.ancientempires.entity.npc;

import com.ancientempires.combat.AttackSwing;
import com.ancientempires.combat.NpcMeleeAttackGoal;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The base soldier. Equipment uses the vanilla slots, which already apply weapon
 * attributes. A storage inventory, jobs, and upkeep stay off this class until the
 * attack path is in place. Ownership is an NBT id so later orders can attach
 * without a subclass.
 */
public class NpcEntity extends PathfinderMob
{
    private static final String OWNER_KEY = "Owner";
    private static final EntityDataAccessor<Boolean> DATA_ATTACK_WINDOW = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_ATTACK_ANIMATION = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_PRESENTATION_ID = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_PRESENTATION_LENGTH = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PRESENTATION_UPSWING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_PRESENTATION_SERIAL = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID owner;

    private int comboIndex;
    private int ticksSinceSwing;
    private int lastSwingCooldown;

    private boolean attackWindowOpen;
    private int attackWindupRemaining;
    @Nullable
    private String attackAnimation;

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level)
    {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        this.xpReward = 0;
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            this.setDropChance(slot, 1.0F);
        }
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_ATTACK_WINDOW, false);
        this.entityData.define(DATA_ATTACK_ANIMATION, "");
        this.entityData.define(DATA_PRESENTATION_ID, "");
        this.entityData.define(DATA_PRESENTATION_LENGTH, 0);
        this.entityData.define(DATA_PRESENTATION_UPSWING, 0.0F);
        this.entityData.define(DATA_PRESENTATION_SERIAL, 0);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        // Player-like attack damage and attack speed so held weapons apply their
        // modifiers the same way they do for a player. Attack speed is not on mobs
        // by default, so it has to be added here.
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new NpcMeleeAttackGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions)
    {
        return 1.62F;
    }

    @Nullable
    public UUID getOwner()
    {
        return this.owner;
    }

    public void setOwner(@Nullable UUID owner)
    {
        this.owner = owner;
    }

    public int getComboIndex()
    {
        return this.comboIndex;
    }

    /**
     * Called when a swing finishes, hit or miss. The next swing advances the combo.
     */
    public void completeSwing(int cooldownTicks)
    {
        this.comboIndex++;
        this.ticksSinceSwing = 0;
        this.lastSwingCooldown = Math.max(1, cooldownTicks);
    }

    public void openAttackWindow(AttackSwing swing)
    {
        this.attackWindowOpen = true;
        this.attackWindupRemaining = swing.windupTicks();
        this.attackAnimation = swing.animationId();
        String id = swing.animationId() == null ? "" : swing.animationId();
        this.entityData.set(DATA_ATTACK_WINDOW, true);
        this.entityData.set(DATA_ATTACK_ANIMATION, id);
        if (id.isEmpty())
        {
            this.clearPresentation();
            return;
        }
        this.entityData.set(DATA_PRESENTATION_ID, id);
        this.entityData.set(DATA_PRESENTATION_LENGTH, Math.max(1, swing.cooldownTicks()));
        this.entityData.set(DATA_PRESENTATION_UPSWING, swing.animationUpswing());
        this.entityData.set(DATA_PRESENTATION_SERIAL, this.entityData.get(DATA_PRESENTATION_SERIAL) + 1);
    }

    public void tickAttackWindow()
    {
        if (this.attackWindupRemaining > 0)
        {
            this.attackWindupRemaining--;
        }
    }

    public void closeAttackWindow()
    {
        this.attackWindowOpen = false;
        this.attackWindupRemaining = 0;
        this.attackAnimation = null;
        this.entityData.set(DATA_ATTACK_WINDOW, false);
        this.entityData.set(DATA_ATTACK_ANIMATION, "");
    }

    /**
     * Stops the swing and the animation that was started for it. A finished
     * wind-up uses {@link #closeAttackWindow()} instead, so the follow-through
     * can keep playing for the rest of the cooldown.
     */
    public void cancelAttackPresentation()
    {
        this.closeAttackWindow();
        this.clearPresentation();
    }

    private void clearPresentation()
    {
        if (this.entityData.get(DATA_PRESENTATION_ID).isEmpty() && this.entityData.get(DATA_PRESENTATION_SERIAL) == 0)
        {
            return;
        }
        this.entityData.set(DATA_PRESENTATION_ID, "");
        this.entityData.set(DATA_PRESENTATION_LENGTH, 0);
        this.entityData.set(DATA_PRESENTATION_UPSWING, 0.0F);
        this.entityData.set(DATA_PRESENTATION_SERIAL, this.entityData.get(DATA_PRESENTATION_SERIAL) + 1);
    }

    public boolean isAttackWindowOpen()
    {
        if (this.level().isClientSide)
        {
            return this.entityData.get(DATA_ATTACK_WINDOW);
        }
        return this.attackWindowOpen;
    }

    public int getAttackWindupRemaining()
    {
        return this.attackWindupRemaining;
    }

    /**
     * Animation id from the swing that is currently winding up. Empty when the
     * NPC is not in an attack window. The client copy reads the synced value.
     */
    @Nullable
    public String getAttackAnimation()
    {
        if (this.level().isClientSide)
        {
            String synced = this.entityData.get(DATA_ATTACK_ANIMATION);
            return synced.isEmpty() ? null : synced;
        }
        return this.attackAnimation;
    }

    /**
     * Animation started when the attack window opened. It stays set through the
     * recovery so the client can finish the same clip Better Combat would play
     * for the full cooldown. Empty after a cancelled swing.
     */
    public String getAttackPresentationId()
    {
        return this.entityData.get(DATA_PRESENTATION_ID);
    }

    public int getAttackPresentationLength()
    {
        return this.entityData.get(DATA_PRESENTATION_LENGTH);
    }

    public float getAttackPresentationUpswing()
    {
        return this.entityData.get(DATA_PRESENTATION_UPSWING);
    }

    public int getAttackPresentationSerial()
    {
        return this.entityData.get(DATA_PRESENTATION_SERIAL);
    }

    @Override
    protected void customServerAiStep()
    {
        super.customServerAiStep();
        this.ticksSinceSwing++;
        if (this.comboIndex != 0 && this.lastSwingCooldown > 0
                && this.ticksSinceSwing > this.lastSwingCooldown * AncientEmpiresConfig.comboResetRate)
        {
            this.comboIndex = 0;
        }
    }

    @Override
    public void onEquipItem(EquipmentSlot slot, ItemStack previous, ItemStack current)
    {
        super.onEquipItem(slot, previous, current);
        if (slot == EquipmentSlot.MAINHAND && !ItemStack.isSameItemSameTags(previous, current))
        {
            this.comboIndex = 0;
            this.ticksSinceSwing = 0;
        }
    }

    /**
     * Right-click with an item to put one of it in the main hand. Sneak with an
     * empty hand to take the weapon back. This is the test path until an
     * inventory screen exists.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        ItemStack current = this.getMainHandItem();
        boolean takeBack = player.isShiftKeyDown() && held.isEmpty() && !current.isEmpty();
        boolean give = !player.isShiftKeyDown() && !held.isEmpty();
        if (!takeBack && !give)
        {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }

        if (takeBack)
        {
            player.setItemInHand(hand, current.copy());
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        else
        {
            ItemStack given = player.getAbilities().instabuild ? held.copyWithCount(1) : held.split(1);
            this.setItemSlot(EquipmentSlot.MAINHAND, given);
            if (!current.isEmpty() && !player.addItem(current))
            {
                player.drop(current, false);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (this.owner != null)
        {
            tag.putUUID(OWNER_KEY, this.owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.owner = tag.hasUUID(OWNER_KEY) ? tag.getUUID(OWNER_KEY) : null;
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }
}
