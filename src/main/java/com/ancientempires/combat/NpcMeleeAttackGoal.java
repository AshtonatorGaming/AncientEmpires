package com.ancientempires.combat;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;

/**
 * Closes to weapon range, winds up, then applies one hit. The goal owns the
 * timer and the damage. Better Combat, when present, has already been reduced
 * to numbers on the {@link AttackSwing}.
 */
public class NpcMeleeAttackGoal extends Goal
{
    private enum Phase
    {
        APPROACH,
        WINDUP
    }

    private final NpcEntity npc;

    private Phase phase = Phase.APPROACH;
    private int windupLeft;
    private int recoveryLeft;
    private int pathCooldown;
    private boolean stoppedForWindup;
    private AttackSwing active;

    private AttackSwing preview;
    private int previewCombo = -1;
    private ItemStack previewStack = ItemStack.EMPTY;

    public NpcMeleeAttackGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        LivingEntity target = this.npc.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void start()
    {
        this.phase = Phase.APPROACH;
        this.pathCooldown = 0;
        this.recoveryLeft = 0;
        this.stoppedForWindup = false;
    }

    @Override
    public void stop()
    {
        this.npc.getNavigation().stop();
        if (this.phase == Phase.WINDUP)
        {
            this.npc.cancelAttackPresentation();
        }
        else
        {
            this.npc.closeAttackWindow();
        }
        this.phase = Phase.APPROACH;
        this.windupLeft = 0;
        this.recoveryLeft = 0;
        this.active = null;
        this.stoppedForWindup = false;
        this.preview = null;
    }

    @Override
    public void tick()
    {
        LivingEntity target = this.npc.getTarget();
        if (target == null)
        {
            return;
        }
        this.npc.getLookControl().setLookAt(target, 40.0F, 40.0F);

        if (this.recoveryLeft > 0)
        {
            this.recoveryLeft--;
            this.moveToward(target, AncientEmpiresConfig.approachSpeed);
            return;
        }

        if (this.phase == Phase.WINDUP)
        {
            this.tickWindup(target);
            return;
        }

        AttackSwing next = this.preview();
        if (!this.within(target, next.range()) || !this.hasLineOfSight(target))
        {
            this.moveToward(target, AncientEmpiresConfig.approachSpeed);
            return;
        }

        this.npc.getNavigation().stop();
        this.beginWindup(next);
    }

    private void tickWindup(LivingEntity target)
    {
        AttackSwing swing = this.active;
        if (swing == null)
        {
            this.phase = Phase.APPROACH;
            this.npc.cancelAttackPresentation();
            return;
        }

        double slackRange = swing.range() + AncientEmpiresConfig.windupRangeSlack;
        if (this.within(target, swing.range()) && AncientEmpiresConfig.stopMovingDuringWindup)
        {
            if (!this.stoppedForWindup)
            {
                this.npc.getNavigation().stop();
                this.stoppedForWindup = true;
            }
        }
        else
        {
            this.stoppedForWindup = false;
            this.moveToward(target, AncientEmpiresConfig.windupMoveSpeed);
        }

        this.windupLeft--;
        this.npc.tickAttackWindow();
        if (this.windupLeft > 0)
        {
            return;
        }

        boolean inRange = this.within(target, swing.range()) || !AncientEmpiresConfig.cancelIfOutOfRange;
        if (inRange && this.hasLineOfSight(target) && this.facing(target, swing.angleDegrees()))
        {
            this.applyHit(target, swing);
        }

        this.npc.completeSwing(swing.cooldownTicks());
        this.npc.closeAttackWindow();
        this.recoveryLeft = swing.recoveryTicks();
        this.phase = Phase.APPROACH;
        this.active = null;
        this.stoppedForWindup = false;
        this.preview = null;
    }

    private void beginWindup(AttackSwing swing)
    {
        this.active = swing;
        this.phase = Phase.WINDUP;
        this.windupLeft = swing.windupTicks();
        this.stoppedForWindup = false;
        this.pathCooldown = 0;
        this.npc.openAttackWindow(swing);
        this.npc.swing(InteractionHand.MAIN_HAND);
        this.playSwingSound(swing);
    }

    private void applyHit(LivingEntity target, AttackSwing swing)
    {
        float damage = (float) this.npc.getAttributeValue(Attributes.ATTACK_DAMAGE);
        damage *= swing.damageMultiplier();
        damage += EnchantmentHelper.getDamageBonus(this.npc.getMainHandItem(), target.getMobType());
        if (damage < 0.0F)
        {
            damage = 0.0F;
        }

        int previousInvulnerability = target.invulnerableTime;
        if (AncientEmpiresConfig.bypassHurtCooldown)
        {
            target.invulnerableTime = 0;
        }
        boolean hurt = target.hurt(this.npc.damageSources().mobAttack(this.npc), damage);
        if (!hurt)
        {
            if (AncientEmpiresConfig.bypassHurtCooldown)
            {
                target.invulnerableTime = previousInvulnerability;
            }
            return;
        }

        double knockback = this.npc.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        if (knockback > 0.0D)
        {
            double yaw = this.npc.getYRot() * ((float) Math.PI / 180.0F);
            target.knockback(knockback * 0.5D, Math.sin(yaw), -Math.cos(yaw));
        }
        this.npc.setLastHurtMob(target);
        this.npc.doEnchantDamageEffects(this.npc, target);
    }

    private AttackSwing preview()
    {
        ItemStack hand = this.npc.getMainHandItem();
        int combo = this.npc.getComboIndex();
        if (this.preview == null || combo != this.previewCombo || !ItemStack.isSameItemSameTags(this.previewStack, hand))
        {
            this.previewCombo = combo;
            this.previewStack = hand.copy();
            this.preview = AttackProfiles.resolve(this.npc);
        }
        return this.preview;
    }

    private boolean within(LivingEntity target, double range)
    {
        double allowed = range + target.getBbWidth() * 0.5D;
        return this.npc.distanceToSqr(target) <= allowed * allowed;
    }

    private boolean hasLineOfSight(LivingEntity target)
    {
        return !AncientEmpiresConfig.requireLineOfSight || this.npc.getSensing().hasLineOfSight(target);
    }

    private boolean facing(LivingEntity target, double angleDegrees)
    {
        if (angleDegrees >= 360.0D)
        {
            return true;
        }
        Vec3 look = this.npc.getLookAngle();
        Vec3 toTarget = target.getEyePosition().subtract(this.npc.getEyePosition());
        double length = toTarget.length();
        if (length < 1.0E-4D)
        {
            return true;
        }
        double dot = look.dot(toTarget.scale(1.0D / length));
        double limit = Math.cos(Math.toRadians(angleDegrees * 0.5D));
        return dot >= limit;
    }

    private void moveToward(LivingEntity target, double speed)
    {
        if (this.pathCooldown > 0)
        {
            this.pathCooldown--;
            return;
        }
        this.pathCooldown = AncientEmpiresConfig.pathRecalcTicks;
        this.npc.getNavigation().moveTo(target, speed);
    }

    private void playSwingSound(AttackSwing swing)
    {
        if (swing.swingSoundId() != null)
        {
            ResourceLocation id = ResourceLocation.tryParse(swing.swingSoundId());
            if (id != null)
            {
                var event = ForgeRegistries.SOUND_EVENTS.getValue(id);
                if (event != null)
                {
                    float pitch = swing.swingPitch();
                    float randomness = swing.swingPitchRandomness();
                    if (randomness > 0.0F)
                    {
                        pitch += (this.npc.getRandom().nextFloat() - 0.5F) * 2.0F * randomness;
                    }
                    this.npc.playSound(event, swing.swingVolume(), pitch);
                    return;
                }
            }
        }
        this.npc.playSound(swing.unarmed() ? SoundEvents.PLAYER_ATTACK_WEAK : SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
    }
}
