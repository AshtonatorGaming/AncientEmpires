package com.ancientempires.entity.npc;

import com.ancientempires.combat.AttackSwing;
import com.ancientempires.combat.NpcMeleeAttackGoal;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.nbt.CompoundTag;
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
    }

    public boolean isAttackWindowOpen()
    {
        return this.attackWindowOpen;
    }

    public int getAttackWindupRemaining()
    {
        return this.attackWindupRemaining;
    }

    /**
     * Animation id from the swing that is currently winding up. Empty when the
     * NPC is not in an attack window. Later animation mods read this instead of
     * starting their own swing.
     */
    @Nullable
    public String getAttackAnimation()
    {
        return this.attackAnimation;
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
