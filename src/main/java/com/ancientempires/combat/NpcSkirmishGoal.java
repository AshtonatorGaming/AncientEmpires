package com.ancientempires.combat;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcSafety;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Skirmisher (DESIGN Tier 2, from AncientTactics' NpcAISkirmishAttack): an NPC
 * holding a Spartan throwing weapon throws it from {@code skirmish.minRange}
 * to {@code maxRange} (AncientTactics' 4.5 to 28), standing still to wind up
 * (never while moving), with {@code throwCooldownTicks} between throws. A foe
 * inside {@code dangerRange} is kited: the skirmisher steps back to a safe spot
 * instead of throwing. Cornered (inside the sidearm range), or out of throws, it
 * draws its best backpack melee weapon ({@code NpcSidearm}) and the melee goal
 * takes over; the throwing weapon comes back after the fight. Throws are aimed
 * with the archers' gravity lift and ammo is Spartan's own count on the stack,
 * restocked one at a time out of a fight.
 */
public final class NpcSkirmishGoal extends Goal
{
    private final NpcEntity npc;
    private int charge;
    private int cooldown;
    private int repath;

    public NpcSkirmishGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        LivingEntity target = this.npc.getTarget();
        return AncientEmpiresConfig.skirmishEnabled && target != null && target.isAlive()
                && SpartanThrowing.isThrowing(this.npc.getMainHandItem()) && !this.npc.avoidsFights();
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
    public void stop()
    {
        this.abortCharge();
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        LivingEntity target = this.npc.getTarget();
        ItemStack weapon = this.npc.getMainHandItem();
        if (target == null)
        {
            return;
        }
        this.npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.cooldown > 0)
        {
            this.cooldown--;
        }
        double distance = Math.sqrt(this.npc.distanceToSqr(target));
        boolean empty = SpartanThrowing.ammoLeft(weapon) <= 0;
        if (empty || distance <= AncientEmpiresConfig.sidearmCorneredRange)
        {
            // Out of throws, or caught up with: out comes the sidearm, and the melee goal takes over.
            int slot = empty ? this.npc.getSidearm().anySlot() : this.npc.getSidearm().corneredSlot(target);
            if (slot >= 0)
            {
                this.abortCharge();
                this.npc.getSidearm().draw(slot);
                return;
            }
        }
        if (distance < AncientEmpiresConfig.skirmishDangerRange && !empty)
        {
            this.abortCharge();
            this.kite(target);
            return;
        }
        boolean moving = !this.npc.getNavigation().isDone();
        if (distance > AncientEmpiresConfig.skirmishMaxRange || !this.npc.getSensing().hasLineOfSight(target))
        {
            this.abortCharge();
            if (--this.repath <= 0)
            {
                this.repath = 10;
                this.npc.getNavigation().moveTo(target, AncientEmpiresConfig.approachSpeed);
            }
            return;
        }
        if (moving)
        {
            // Never wind up while moving: settle first.
            this.npc.getNavigation().stop();
            return;
        }
        if (this.cooldown > 0 || distance < AncientEmpiresConfig.skirmishMinRange || empty)
        {
            this.abortCharge();
            return;
        }
        if (!this.npc.isUsingItem())
        {
            this.npc.startUsingItem(InteractionHand.MAIN_HAND);
            this.charge = 0;
            return;
        }
        if (++this.charge < SpartanThrowing.chargeTicks(weapon))
        {
            return;
        }
        this.release(target, weapon);
    }

    private void release(LivingEntity target, ItemStack weapon)
    {
        this.npc.stopUsingItem();
        this.charge = 0;
        this.cooldown = AncientEmpiresConfig.skirmishCooldownTicks;
        AbstractArrow thrown = SpartanThrowing.createThrow(this.npc.level(), this.npc, weapon);
        if (thrown == null)
        {
            return;
        }
        thrown.setBaseDamage(thrown.getBaseDamage() * this.npc.getRangedProficiency());
        float speed = (float) AncientEmpiresConfig.skirmishThrowSpeed;
        double dx = target.getX() - this.npc.getX();
        double dz = target.getZ() - this.npc.getZ();
        double dy = target.getY(0.5D) - thrown.getY();
        thrown.shoot(dx, dy + NpcBowAttackGoal.gravityLift(dx, dy, dz, speed), dz, speed, 2.0F);
        this.npc.level().addFreshEntity(thrown);
        this.npc.swing(InteractionHand.MAIN_HAND);
        this.npc.playSound(SoundEvents.TRIDENT_THROW, 0.6F, 0.9F + this.npc.getRandom().nextFloat() * 0.2F);
    }

    /** Steps back from a foe that got too close, to a safe spot, as AncientTactics' skirmishers did. */
    private void kite(LivingEntity target)
    {
        if (this.npc.isHoldingPosition() || --this.repath > 0 && !this.npc.getNavigation().isDone())
        {
            return;
        }
        this.repath = 10;
        int step = (int) Math.max(3.0D, AncientEmpiresConfig.skirmishDangerRange * 0.65D);
        Vec3 away = NpcSafety.posAway(this.npc, step, 4, target.position());
        if (away != null)
        {
            this.npc.getNavigation().moveTo(away.x, away.y, away.z, AncientEmpiresConfig.approachSpeed);
        }
    }

    private void abortCharge()
    {
        this.charge = 0;
        if (this.npc.isUsingItem() && SpartanThrowing.isThrowing(this.npc.getUseItem()))
        {
            this.npc.stopUsingItem();
        }
    }
}
