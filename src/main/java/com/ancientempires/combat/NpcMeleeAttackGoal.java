package com.ancientempires.combat;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcTargetMemory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Closes to weapon range, winds up, then applies one hit. Better Mob Combat,
 * when present, owns the upswing clock and the attack clip. This goal still
 * applies the damage. Without that mod, the goal's own timer is the clock.
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
    /** Which way the next sidestep goes (1 or -1). */
    private int strafeSide = 1;
    /** Duel mode, judged every 0.7 seconds: one foe, nobody else close. */
    private boolean duel;
    /** Backpedalling out of reach: a duel stance, or after the NPC's own blow. */
    private boolean backingOff;
    /** Ticks until the next duel check and stance roll. */
    private int duelCheck;
    /** The swing (attacker id and window serial) a dodge was decided for, and ticks of backstep left. */
    private int dodgeFor = -1;
    private int dodgeSerial = -1;
    private int dodgeLeft;
    /** Footwork: ticks left in the current sidestep, then ticks of standing. */
    private int stepLeft;
    private int stepRest;
    private int pathCooldown;
    private boolean stoppedForWindup;
    private boolean mobCombatSwing;
    private boolean mobCombatHitDone;
    private boolean mobCombatCancelled;
    /** Hands version when the Better Mob Combat swing started. */
    private int swingHands;
    private int facingWait;
    private int facedOnTick = -1;
    private boolean forcedSwing;
    private boolean fistClip;
    /** This wind-up breaks off halfway. */
    private boolean feintPlanned;
    /** The last wind-up was a feint, so the next one is real. */
    private boolean feintedLast;
    private AttackSwing active;

    private AttackSwing preview;
    private int previewCombo = -1;
    private int previewHands;

    public NpcMeleeAttackGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        LivingEntity target = this.npc.getTarget();
        // A bow stays on the archer goal. This goal must not punch with it.
        return target != null && target.isAlive() && !this.npc.isHoldingBow();
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
        this.mobCombatSwing = false;
        this.mobCombatHitDone = false;
        this.mobCombatCancelled = false;
        this.facingWait = 0;
        this.forcedSwing = false;
        this.fistClip = false;
        this.facedOnTick = -1;
        this.preview = null;
    }

    @Override
    public void stop()
    {
        this.npc.getNavigation().stop();
        this.stopStepping();
        this.stepLeft = 0;
        this.backingOff = false;
        this.npc.setSwingCommitted(false);
        this.npc.getGuard().clearStrike();
        this.feintPlanned = false;
        if (this.mobCombatSwing)
        {
            AttackSwing swing = this.active;
            boolean landed = this.mobCombatHitDone;
            this.mobCombatCancelled = true;
            MobCombatBridge.cancel(this.npc);
            if (landed && swing != null)
            {
                this.npc.completeSwing(swing.cooldownTicks());
            }
        }
        else if (this.phase == Phase.WINDUP)
        {
            this.stopFistClip();
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
        this.mobCombatSwing = false;
        this.facingWait = 0;
        this.forcedSwing = false;
        this.fistClip = false;
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
        if (this.npc.isStaggered())
        {
            // Parried: the swing is broken off and the NPC reels for a moment.
            this.interrupt();
            this.npc.getNavigation().stop();
            return;
        }
        this.faceTarget(target);
        if (this.dodging(target))
        {
            return;
        }

        if (this.recoveryLeft > 0)
        {
            this.recoveryLeft--;
            AttackSwing upcoming = this.preview();
            if (this.backingOff || this.canEngage(target, upcoming))
            {
                this.footwork(target, upcoming);
            }
            else
            {
                this.moveToward(target, AncientEmpiresConfig.approachSpeed);
            }
            return;
        }

        if (this.phase == Phase.WINDUP)
        {
            this.tickWindup(target);
            return;
        }

        AttackSwing next = this.preview();
        if (!this.canEngage(target, next) || !this.hasLineOfSight(target))
        {
            this.facingWait = 0;
            this.moveToward(target, AncientEmpiresConfig.approachSpeed);
            return;
        }
        if (!this.canCommitFacing(target, next))
        {
            this.npc.getNavigation().stop();
            return;
        }
        if (MobCombatBridge.owns(this.npc) && !MobCombatBridge.attackReady(this.npc))
        {
            this.npc.getNavigation().stop();
            return;
        }

        if (target.isBlocking() && !this.npc.hasShieldInOffhand() && this.npc.getRandom().nextFloat() >= AncientEmpiresConfig.pressureChance)
        {
            // Against a raised shield, pick a moment rather than hammer it at weapon speed.
            this.footwork(target, next);
            return;
        }
        if (!this.npc.getGuard().readyToStrike())
        {
            // Behind the shield: it comes down only after a committed block, then a beat before the swing.
            this.footwork(target, next);
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
            if (this.mobCombatSwing)
            {
                this.mobCombatCancelled = true;
                MobCombatBridge.cancel(this.npc);
                this.mobCombatSwing = false;
            }
            else
            {
                this.stopFistClip();
                this.npc.cancelAttackPresentation();
            }
            return;
        }

        // Close in until well inside reach, not just barely: a target drifting a little would
        // otherwise leave it before the hit and make the swing a sure miss.
        if (this.within(target, swing.range() * AncientEmpiresConfig.windupKeepRange) && AncientEmpiresConfig.stopMovingDuringWindup)
        {
            if (!this.stoppedForWindup)
            {
                this.npc.getNavigation().stop();
                this.stoppedForWindup = true;
            }
            if (this.npc.distanceToSqr(target) < Mth.square(this.keepOff(target, swing)))
            {
                // Pressed too close mid-swing: ease back rather than swing from inside the foe.
                this.npc.getMoveControl().strafe(-0.3F, 0.0F);
            }
        }
        else
        {
            this.stoppedForWindup = false;
            this.moveToward(target, AncientEmpiresConfig.windupMoveSpeed);
        }

        if (this.mobCombatSwing)
        {
            if (MobCombatBridge.upswinging(this.npc))
            {
                return;
            }
            this.finishMobCombatSwing(swing);
            return;
        }

        this.windupLeft--;
        this.npc.tickAttackWindow();
        if (this.feintPlanned && this.windupLeft <= swing.windupTicks() / 2)
        {
            this.feint();
            return;
        }
        if (this.windupLeft > 0)
        {
            return;
        }

        this.feintedLast = false;
        // Always sent: a client playing a Better Combat clip ignores it (NpcModel), and one
        // whose clip is missing or failed shows the vanilla swing on the hit instead.
        this.npc.swing(swing.offHandWeapon() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        boolean inRange =this.within(target, swing.range()) || !AncientEmpiresConfig.cancelIfOutOfRange;
        if (inRange && this.hasLineOfSight(target) && this.hitFacing(target, swing))
        {
            this.applyHit(target, swing, true);
        }

        this.npc.completeSwing(swing.cooldownTicks());
        this.npc.closeAttackWindow();
        this.stepOut();
        // Plus a short pause between swings so weapons are not spammed (combat.attackPause, 0 off).
        this.recoveryLeft = swing.recoveryTicks() + AncientEmpiresConfig.attackPause;
        // The shield stays down until the attack animation has played out, as a player's would.
        this.npc.getGuard().holdDown(Math.max(AncientEmpiresConfig.shieldFollowThroughTicks, swing.recoveryTicks()));
        this.phase = Phase.APPROACH;
        this.active = null;
        this.stoppedForWindup = false;
        // The punch clip is timed to the whole cooldown, so recovery leaves it playing.
        this.fistClip = false;
        this.preview = null;
    }

    /**
     * Better Combat's feint: a player cancels the swing mid-upswing and pays a
     * full cooldown for it. NPCs feint only against a guarded target (a raised
     * shield, a shield in hand, or a player who can react), never twice in a
     * row, and only as often as their own feint chance, which is zero unless a
     * role sets it. The internal path only; Better Mob Combat owns its swings.
     */
    /** Breaks off a wind-up in progress (a parry staggered the NPC). */
    private void interrupt()
    {
        if (this.phase != Phase.WINDUP)
        {
            return;
        }
        if (this.mobCombatSwing)
        {
            this.mobCombatCancelled = true;
            MobCombatBridge.cancel(this.npc);
            this.mobCombatSwing = false;
        }
        else
        {
            this.stopFistClip();
            this.npc.cancelAttackPresentation();
        }
        this.npc.setSwingCommitted(false);
        this.phase = Phase.APPROACH;
        this.active = null;
        this.windupLeft = 0;
        this.stoppedForWindup = false;
        this.feintPlanned = false;
    }

    private boolean planFeint(@Nullable LivingEntity target)
    {
        float chance = this.npc.getFeintChance();
        if (!AncientEmpiresConfig.feints || chance <= 0.0F || this.feintedLast || target == null)
        {
            return false;
        }
        // Worth baiting: a raised or carried shield, or a player. A shield timed for a perfect block
        // goes up for the feint, so the real swing finds it held too long: an ordinary block.
        boolean guarded = target.isBlocking()
                || target instanceof Player
                || target instanceof NpcEntity other && other.hasShieldInOffhand();
        return guarded && this.npc.getRandom().nextFloat() < chance;
    }

    /**
     * Breaks off the wind-up halfway: no hit, the clip stops, the combo does not
     * advance, and a short recovery follows before the real swing.
     */
    private void feint()
    {
        this.feintPlanned = false;
        this.feintedLast = true;
        this.stopFistClip();
        this.npc.cancelAttackPresentation();
        this.recoveryLeft = AncientEmpiresConfig.feintRecoveryTicks;
        this.phase = Phase.APPROACH;
        this.active = null;
        this.stoppedForWindup = false;
        this.windupLeft = 0;
    }

    private void beginWindup(AttackSwing swing)
    {
        // A sidestep's sideways input outlives the step; left on, the swing slid the attacker out of reach.
        this.stopStepping();
        this.backingOff = false;
        // A player cannot block and swing at once; neither can an NPC.
        this.npc.getGuard().lower();
        if (MobCombatBridge.owns(this.npc))
        {
            this.mobCombatCancelled = false;
            this.mobCombatHitDone = false;
            if (!MobCombatBridge.tryStart(this.npc, this::onMobCombatHit))
            {
                return;
            }
            this.forcedSwing = !this.bodyAligned(this.npc.getTarget(), swing);
            this.facingWait = 0;
            this.active = swing;
            this.phase = Phase.WINDUP;
            this.mobCombatSwing = true;
            this.npc.setSwingCommitted(true);
            this.swingHands = this.npc.getHandsVersion();
            this.windupLeft = 0;
            this.stoppedForWindup = false;
            this.pathCooldown = 0;
            // No vanilla swing. That flag makes Better Mob Combat drop the hold pose mid-clip.
            return;
        }
        this.forcedSwing = !this.bodyAligned(this.npc.getTarget(), swing);
        this.facingWait = 0;
        this.active = swing;
        this.phase = Phase.WINDUP;
        this.mobCombatSwing = false;
        this.windupLeft = swing.windupTicks();
        this.stoppedForWindup = false;
        this.pathCooldown = 0;
        this.feintPlanned = this.planFeint(this.npc.getTarget());
        this.npc.openAttackWindow(swing);
        this.fistClip = false;
        if (this.usesFistClip(swing))
        {
            // Better Mob Combat will not start this swing: an empty hand has no
            // item attributes, so its upswing bails and the vanilla arm swing is
            // all that remains (sent at the hit). The id is the fist preset's own attack animation.
            this.fistClip = MobCombatBridge.playAttackAnimation(
                    this.npc,
                    swing.animationId(),
                    swing.cooldownTicks(),
                    swing.animationUpswing(),
                    swing.offHand());
        }
        // The vanilla arm swing comes at the hit (tickWindup); clients drop it while a clip plays.
        this.playSwingSound(swing);
    }

    /**
     * Combat NPCs with nothing in either hand use the fist preset. The melee
     * goal is the combat path; an NPC that is not attacking never reaches this.
     */
    private boolean usesFistClip(AttackSwing swing)
    {
        return swing.unarmed()
                && swing.animationId() != null
                && this.npc.getMainHandItem().isEmpty()
                && this.npc.getOffhandItem().isEmpty();
    }

    private void stopFistClip()
    {
        if (!this.fistClip)
        {
            return;
        }
        this.fistClip = false;
        MobCombatBridge.stopAttackAnimation(this.npc, 4);
    }

    /**
     * Better Mob Combat runs its hit on the tick its upswing reaches zero, before
     * this goal ticks, so by now the callback has either landed or will not
     * come. Its target finder can leave our target out: with its default
     * {@code mobs_check_for_same_entity_type}, every NPC skips every other NPC,
     * because they share one entity type. The goal's own target is then hit
     * here with the same checks, unless the hands changed mid-swing (that mod
     * cancels the swing then, and the hit should not land).
     */
    private void finishMobCombatSwing(AttackSwing swing)
    {
        this.npc.setSwingCommitted(false);
        if (!this.mobCombatHitDone && !this.mobCombatCancelled && this.npc.getHandsVersion() == this.swingHands)
        {
            LivingEntity target = this.npc.getTarget();
            if (target != null && target.isAlive())
            {
                // The combo multiplier is no longer on the attribute, so scale here.
                this.tryLandHit(target, swing, true);
            }
        }
        this.npc.completeSwing(swing.cooldownTicks());
        this.recoveryLeft = Math.max(0, MobCombatBridge.attackCooldown(this.npc));
        this.phase = Phase.APPROACH;
        this.active = null;
        this.mobCombatSwing = false;
        this.stoppedForWindup = false;
        this.preview = null;
    }

    /**
     * Better Mob Combat calls this when its upswing reaches the hit. It has
     * already put the combo multiplier on the attack damage attribute, and it
     * has already chosen the targets. Only the goal's own target is hurt.
     */
    private void onMobCombatHit(Mob mob, Entity struck)
    {
        if (mob != this.npc || this.mobCombatCancelled || this.mobCombatHitDone || !this.mobCombatSwing)
        {
            return;
        }
        if (!(struck instanceof LivingEntity target) || target != this.npc.getTarget())
        {
            return;
        }
        AttackSwing swing = this.active;
        if (swing == null)
        {
            return;
        }
        this.faceTarget(target);
        this.tryLandHit(target, swing, false);
    }

    /**
     * Range, sight, and facing, then the hit. Marks the Better Mob Combat swing
     * as landed so neither path hits twice.
     */
    private void tryLandHit(LivingEntity target, AttackSwing swing, boolean scaleDamage)
    {
        boolean inRange = this.within(target, swing.range()) || !AncientEmpiresConfig.cancelIfOutOfRange;
        if (!inRange || !this.hasLineOfSight(target) || !this.hitFacing(target, swing))
        {
            return;
        }
        this.applyHit(target, swing, scaleDamage);
        this.mobCombatHitDone = true;
    }

    private void applyHit(LivingEntity target, AttackSwing swing, boolean scaleDamage)
    {
        // A dual-wield off-hand swing hits with the off-hand weapon's damage and enchantments.
        boolean offHand = swing.offHandWeapon();
        float damage = offHand
                ? AttackProfiles.withOffHandModifiers(this.npc, () -> (float) this.npc.getAttributeValue(Attributes.ATTACK_DAMAGE))
                : (float) this.npc.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (scaleDamage)
        {
            damage *= swing.damageMultiplier();
        }
        ItemStack weapon = offHand ? this.npc.getOffhandItem() : this.npc.getMainHandItem();
        damage += EnchantmentHelper.getDamageBonus(weapon, target.getMobType());
        // Role proficiency: archers and skirmishers in melee, and levies, hit softer.
        damage *= this.npc.getMeleeProficiency();
        if (damage < 0.0F)
        {
            damage = 0.0F;
        }

        int previousInvulnerability = target.invulnerableTime;
        // Players keep their half second of hit immunity, as against any vanilla mob.
        boolean bypass = AncientEmpiresConfig.bypassHurtCooldown && !(target instanceof Player);
        if (bypass)
        {
            target.invulnerableTime = 0;
        }
        boolean hurt = target.hurt(this.npc.damageSources().mobAttack(this.npc), damage);
        if (bypass)
        {
            // Skipping the immunity must not grant a fresh one either: otherwise an NPC's hit
            // eats a player's (or another NPC's) swing that lands just after it.
            target.invulnerableTime = previousInvulnerability;
        }
        if (!hurt)
        {
            return;
        }
        // As Player.attack does: the hit sound only for a hit that landed. A blocked one gives the shield's thud.
        this.npc.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                swing.unarmed() ? SoundEvents.PLAYER_ATTACK_WEAK : SoundEvents.PLAYER_ATTACK_STRONG, this.npc.getSoundSource(), 1.0F, 1.0F);

        double knockback = this.npc.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        if (knockback > 0.0D)
        {
            double yaw = this.npc.getYRot() * ((float) Math.PI / 180.0F);
            target.knockback(knockback * 0.5D, Math.sin(yaw), -Math.cos(yaw));
        }
        this.npc.setLastHurtMob(target);
        this.npc.doEnchantDamageEffects(this.npc, target);
        if (!this.mobCombatSwing)
        {
            this.sweepEffect(swing);
        }
    }

    /**
     * The sweep sound and crescent a player's sword slash makes. Better Mob
     * Combat plays these on its own swings, so this only runs on the internal
     * path. A sweeping attack is one wider than the forward cone used for stabs
     * and punches. Swords only, the same rule Better Combat uses by default.
     */
    private void sweepEffect(AttackSwing swing)
    {
        if (!AncientEmpiresConfig.sweepEffects || swing.unarmed()
                || swing.angleDegrees() <= AncientEmpiresConfig.forwardAngle
                || !((swing.offHandWeapon() ? this.npc.getOffhandItem() : this.npc.getMainHandItem()).getItem() instanceof SwordItem)
                || !(this.npc.level() instanceof ServerLevel level))
        {
            return;
        }
        double yaw = this.npc.getYRot() * ((float) Math.PI / 180.0F);
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);
        level.playSound(null, this.npc.getX(), this.npc.getY(), this.npc.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, this.npc.getSoundSource(), 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.npc.getX() + dx, this.npc.getY(0.5D), this.npc.getZ() + dz,
                0, dx, 0.0D, dz, 0.0D);
    }

    /**
     * The next swing, resolved again only when the combo step or either hand
     * changes. The hands are tracked by {@link NpcEntity#getHandsVersion()}.
     */
    private AttackSwing preview()
    {
        int combo = this.npc.getComboIndex();
        int hands = this.npc.getHandsVersion();
        if (this.preview == null || combo != this.previewCombo || hands != this.previewHands)
        {
            this.previewCombo = combo;
            this.previewHands = hands;
            this.preview = AttackProfiles.resolve(this.npc);
        }
        return this.preview;
    }

    /**
     * A swing starts only inside {@code engageRangeFactor} of the weapon's
     * reach, so a target that steps back during the wind-up is still inside
     * the full reach at the hit. The hit itself checks the full reach. An NPC
     * that cannot path any closer (blocked, or its path has ended) swings from
     * the full reach instead of waiting there forever.
     */
    private boolean canEngage(LivingEntity target, AttackSwing swing)
    {
        if (this.within(target, swing.range() * AncientEmpiresConfig.engageRangeFactor))
        {
            return true;
        }
        return this.within(target, swing.range()) && this.npc.getNavigation().isDone() && this.pathCooldown > 0;
    }

    private boolean within(LivingEntity target, double range)
    {
        double allowed = range + target.getBbWidth() * 0.5D;
        if (this.npc.isPassenger())
        {
            // A rider sits higher, so a ground target is farther away than the swing really is.
            allowed += AncientEmpiresConfig.cavalryReachBonus;
        }
        return this.npc.distanceToSqr(target) <= allowed * allowed;
    }

    private boolean hasLineOfSight(LivingEntity target)
    {
        return !AncientEmpiresConfig.requireLineOfSight || this.npc.getSensing().hasLineOfSight(target);
    }

    /**
     * Steps body yaw toward the target. Look control only handles pitch, so it
     * does not add a second yaw step. In range the step is the faster config.
     */
    private void faceTarget(LivingEntity target)
    {
        if (this.npc.tickCount == this.facedOnTick)
        {
            return;
        }
        this.facedOnTick = this.npc.tickCount;

        AttackSwing swing = this.phase == Phase.WINDUP && this.active != null ? this.active : this.preview();
        boolean inRange = this.within(target, swing.range());
        float speed = (float) (inRange ? AncientEmpiresConfig.turnSpeedInRange : AncientEmpiresConfig.turnSpeed);
        float desired = this.desiredYaw(target);
        float delta = Mth.wrapDegrees(desired - this.npc.getYRot());
        float step = Mth.clamp(delta, -speed, speed);
        float yaw = this.npc.getYRot() + step;
        this.npc.setYRot(yaw);
        this.npc.setYBodyRot(yaw);
        this.npc.setYHeadRot(yaw);
        this.npc.getLookControl().setLookAt(target.getX(), AttackProfiles.aimY(this.npc, target), target.getZ(), 0.0F, 40.0F);
    }

    private boolean canCommitFacing(LivingEntity target, AttackSwing swing)
    {
        if (this.bodyAligned(target, swing))
        {
            this.facingWait = 0;
            return true;
        }
        this.facingWait++;
        int force = AncientEmpiresConfig.forceSwingTicks;
        return force > 0 && this.facingWait >= force;
    }

    private boolean hitFacing(LivingEntity target, AttackSwing swing)
    {
        return this.forcedSwing || this.bodyAligned(target, swing);
    }

    private boolean bodyAligned(LivingEntity target, AttackSwing swing)
    {
        if (target == null || swing.angleDegrees() >= 360.0D)
        {
            return true;
        }
        return this.yawError(target) <= this.yawLimit(swing) + this.targetSpread(target);
    }

    /**
     * Half the angle the target's body covers, in degrees. The yaw test aims at
     * the target's center, but a swing that reaches any part of the body hits,
     * and up close the body covers a wide angle. Two touching bodies always pass:
     * pushing makes the center angle jump around at that distance.
     */
    private double targetSpread(LivingEntity target)
    {
        double dx = target.getX() - this.npc.getX();
        double dz = target.getZ() - this.npc.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double halfWidth = target.getBbWidth() * 0.5D;
        if (horizontal <= halfWidth + this.npc.getBbWidth() * 0.5D)
        {
            return 180.0D;
        }
        return Math.toDegrees(Math.atan2(halfWidth, horizontal));
    }

    private double yawLimit(AttackSwing swing)
    {
        double halfArc = swing.angleDegrees() * 0.5D;
        return Math.min(halfArc, AncientEmpiresConfig.maxYawError);
    }

    private float yawError(LivingEntity target)
    {
        return Math.abs(Mth.wrapDegrees(this.npc.yBodyRot - this.desiredYaw(target)));
    }

    private float desiredYaw(LivingEntity target)
    {
        double dx = target.getX() - this.npc.getX();
        double dz = target.getZ() - this.npc.getZ();
        return (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI) - 90.0D);
    }

    /**
     * Footwork while waiting inside reach (behind the shield, between swings). In a
     * duel (no one else close) the NPC circles, mostly keeping to one side, and steps
     * out of reach after its own blow; in a line it mostly holds its place with a
     * rare sidestep, and none when crowded. Either way it steps in at the edge of
     * reach and gives a little ground when pressed. Vanilla's strafe control
     * refuses steps onto unwalkable blocks, so this does not walk off ledges.
     */
    private void footwork(LivingEntity target, AttackSwing swing)
    {
        this.npc.getNavigation().stop();
        if (!AncientEmpiresConfig.footwork || this.npc.isPassenger())
        {
            return;
        }
        if (--this.duelCheck <= 0)
        {
            // Every second: still a duel? And, a quarter of the time, a new stance (ToroQuest's guards).
            this.duelCheck = 20;
            boolean wasDuel = this.duel;
            this.duel = this.isDuel(target);
            this.npc.getGuard().setDuel(this.duel);
            if (this.duel && (!wasDuel || this.npc.getRandom().nextInt(4) == 0))
            {
                this.rollStance();
            }
            if (wasDuel && !this.duel)
            {
                this.stopStepping();
                this.backingOff = false;
            }
        }
        if (this.shouldFlank(target))
        {
            this.flank(target, swing);
            return;
        }
        if (this.duel)
        {
            this.duelMove(target, swing);
            return;
        }
        // Line mode: mostly standing; now and then one quick sidestep at a real walking pace.
        if (this.stepRest > 0)
        {
            this.stepRest--;
            this.stopStepping();
            return;
        }
        if (this.stepLeft <= 0)
        {
            this.stopStepping();
            this.stepRest = 30 + this.npc.getRandom().nextInt(40);
            double chance = AncientEmpiresConfig.sidestepChance;
            if (this.npc.hasShieldInOffhand() && WeaponClassifier.isShield(target.getOffhandItem()))
            {
                // Two shields in a line: a wary standoff, not a dance.
                chance *= AncientEmpiresConfig.shieldSidestepScale;
            }
            if (this.npc.getRandom().nextDouble() >= chance || this.crowded(target))
            {
                // In a press of bodies a soldier holds its place; footwork is for open ground.
                return;
            }
            this.stepLeft = 5 + this.npc.getRandom().nextInt(4);
            if (this.npc.getRandom().nextBoolean())
            {
                this.strafeSide = -this.strafeSide;
            }
        }
        this.stepLeft--;
        double reach = swing.range() + target.getBbWidth() * 0.5D;
        double distance = Math.sqrt(this.npc.distanceToSqr(target));
        float forward = distance > reach * 0.8D ? 0.5F : distance < this.keepOff(target, swing) ? -0.5F : 0.0F;
        this.npc.getMoveControl().strafe(forward, this.strafeSide * (float) AncientEmpiresConfig.sidestepSpeed);
    }

    /**
     * The backstep dodge, as a player steps out of a swing: an NPC without a
     * shield reads an NPC's wind-up aimed at it and, once per swing,
     * {@code combat.dodgeLeadTicks} before the hit, steps back out of reach at its
     * skill's {@code skill.dodgeChance}. The whiff leaves the attacker open. A
     * player's swing shows no wind-up, so players are never dodged this way.
     */
    private boolean dodging(LivingEntity target)
    {
        if (this.dodgeLeft > 0)
        {
            this.dodgeLeft--;
            this.npc.getNavigation().stop();
            this.npc.getMoveControl().strafe(-0.9F, this.strafeSide * 0.2F);
            if (this.dodgeLeft == 0)
            {
                this.stopStepping();
            }
            return true;
        }
        if (this.phase == Phase.WINDUP || this.npc.hasShieldInOffhand() || this.npc.isPassenger()
                || !(target instanceof NpcEntity attacker) || !attacker.isAttackWindowOpen() || attacker.getTarget() != this.npc
                || attacker.getAttackWindupRemaining() > AncientEmpiresConfig.dodgeLeadTicks)
        {
            return false;
        }
        int serial = attacker.getAttackWindowSerial();
        if (this.dodgeFor == attacker.getId() && this.dodgeSerial == serial)
        {
            return false;
        }
        this.dodgeFor = attacker.getId();
        this.dodgeSerial = serial;
        if (this.npc.getRandom().nextDouble() >= this.npc.getDodgeChance())
        {
            return false;
        }
        this.dodgeLeft = AncientEmpiresConfig.dodgeTicks;
        return this.dodging(target);
    }

    /** A raised shield faces this NPC, which has none, and there is room to go around it. */
    private boolean shouldFlank(LivingEntity target)
    {
        if (!AncientEmpiresConfig.flank || this.npc.hasShieldInOffhand() || !target.isBlocking() || this.crowded(target))
        {
            return false;
        }
        Vec3 facing = Vec3.directionFromRotation(0.0F, target.getYHeadRot());
        Vec3 toMe = this.npc.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        return facing.dot(toMe) > Math.cos(Math.toRadians(AncientEmpiresConfig.flankAngle));
    }

    /**
     * Circles toward the shield holder's side, the shorter way round, keeping
     * striking distance. Footwork only runs while the NPC waits, so a shield
     * that comes down is still struck at once from wherever it stands.
     */
    private void flank(LivingEntity target, AttackSwing swing)
    {
        Vec3 facing = Vec3.directionFromRotation(0.0F, target.getYHeadRot());
        Vec3 toMe = this.npc.position().subtract(target.position());
        // A positive sideways input (left, facing the target) turns the bearing anticlockwise; pick the
        // side that takes it away from the shield's facing.
        double cross = facing.x * toMe.z - facing.z * toMe.x;
        float side = cross >= 0.0D ? 1.0F : -1.0F;
        double reach = swing.range() + target.getBbWidth() * 0.5D;
        double distance = Math.sqrt(this.npc.distanceToSqr(target));
        float forward = distance > reach * 0.85D ? 0.3F : distance < this.keepOff(target, swing) ? -0.3F : 0.0F;
        this.npc.getMoveControl().strafe(forward, side * 0.6F);
    }

    /**
     * A new duel stance: which way to circle, and now and then a backpedal
     * (ToroQuest's guards picked among such stances every few seconds).
     */
    private void rollStance()
    {
        this.strafeSide = this.npc.getRandom().nextBoolean() ? 1 : -1;
        this.backingOff = this.npc.getRandom().nextDouble() < AncientEmpiresConfig.duelBackpedalChance;
    }

    /**
     * Duel movement, every tick: always drifting sideways in the stance's
     * direction (harder behind a raised shield), closing to the edge of reach,
     * and backpedalling out of it in a backpedal stance or after a blow. Steering
     * is set each tick, so it walks rather than glides.
     */
    private void duelMove(LivingEntity target, AttackSwing swing)
    {
        double reach = swing.range() + target.getBbWidth() * 0.5D;
        double distance = Math.sqrt(this.npc.distanceToSqr(target));
        float forward;
        if (this.backingOff)
        {
            if (distance >= reach * 1.6D)
            {
                // Clear of the foe's reach: the backpedal is over; come in again.
                this.backingOff = false;
                forward = 0.0F;
            }
            else
            {
                forward = -0.4F;
            }
        }
        else
        {
            // Unhurried: a duelist measures distance, it does not dart.
            // Holding the gap: no creeping forward inside reach, which walked shield duelists into each other.
            forward = distance > reach * 0.85D ? 0.35F : distance < this.keepOff(target, swing) ? -0.3F : 0.0F;
        }
        float side = (float) (this.strafeSide * AncientEmpiresConfig.duelStrafe * (this.npc.isRaisingShield() ? 1.2D : 1.0D));
        this.npc.getMoveControl().strafe(forward, Mth.clamp(side, -1.0F, 1.0F));
    }

    /**
     * The closest an NPC lets a foe be, centre to centre: {@code combat.minGapReach}
     * of its reach, and never less than the two bodies plus {@code combat.minGap}.
     * Fighters stand off at weapon length instead of hugging or pushing through each other.
     */
    private double keepOff(LivingEntity target, AttackSwing swing)
    {
        double reach = swing.range() + target.getBbWidth() * 0.5D;
        double bodies = (this.npc.getBbWidth() + target.getBbWidth()) * 0.5D + AncientEmpiresConfig.minGap;
        return Math.max(bodies, reach * AncientEmpiresConfig.minGapReach);
    }

    /** One foe and no other NPC within {@code combat.duelRange} of either: a duel. Judged per step decision. */
    private boolean isDuel(LivingEntity target)
    {
        double range = AncientEmpiresConfig.duelRange;
        if (range <= 0.0D)
        {
            return false;
        }
        AABB around = this.npc.getBoundingBox().minmax(target.getBoundingBox()).inflate(range, 2.0D, range);
        return this.npc.level().getEntitiesOfClass(NpcEntity.class, around, other -> other != this.npc && other != target && other.isAlive()).isEmpty();
    }

    /** After the NPC's own blow lands in a duel: step back out of reach. */
    private void stepOut()
    {
        if (this.duel && AncientEmpiresConfig.footwork && !this.npc.isPassenger() && this.npc.getRandom().nextBoolean())
        {
            this.backingOff = true;
        }
    }


    /**
     * Ends a sidestep. Vanilla's move control clears the forward input when it
     * stops strafing but never the sideways one, which only fades 2% a tick, so a
     * fighter left between steps (or after the fight) glided on sideways.
     */
    private void stopStepping()
    {
        this.npc.setXxa(0.0F);
        this.npc.setZza(0.0F);
    }

    /** Other NPCs (not the target) close enough that stepping about would only jostle them. Checked once per step decision. */
    private boolean crowded(LivingEntity target)
    {
        List<NpcEntity> near = this.npc.level().getEntitiesOfClass(NpcEntity.class, this.npc.getBoundingBox().inflate(2.5D, 1.0D, 2.5D),
                other -> other != this.npc && other != target && other.isAlive());
        return near.size() >= AncientEmpiresConfig.footworkCrowd;
    }

    private void moveToward(LivingEntity target, double speed)
    {
        if (this.pathCooldown > 0)
        {
            this.pathCooldown--;
            return;
        }
        this.pathCooldown = AncientEmpiresConfig.pathRecalcTicks;
        if (this.npc.isRaisingShield())
        {
            // Ancient Warfare's guards pressed forward behind the shield, slower.
            speed *= AncientEmpiresConfig.shieldAdvanceSpeed;
        }
        NpcTargetMemory memory = this.npc.getTargetMemory();
        if (!memory.targetVisible())
        {
            // Out of sight: go where it was last seen, not where it really is.
            Vec3 spot = memory.lastSeen();
            this.npc.getNavigation().moveTo(spot.x, spot.y, spot.z, speed);
            return;
        }
        this.npc.getNavigation().moveTo(target, speed);
    }

    /** Swing sounds by id, resolved once. Empty for an id that names no sound. */
    private static final Map<String, Optional<SoundEvent>> SWING_SOUNDS = new ConcurrentHashMap<>();

    private static Optional<SoundEvent> lookupSound(String id)
    {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : Optional.ofNullable(ForgeRegistries.SOUND_EVENTS.getValue(location));
    }

    private void playSwingSound(AttackSwing swing)
    {
        if (swing.swingSoundId() == null)
        {
            // No Better Combat swing sound: silent until it lands (applyHit plays the hit sound then).
            // Vanilla's only stand-in is the no-damage thump, which sounds like an impact.
            return;
        }
        // Looked up once per sound name, not on every swing.
        SoundEvent event = SWING_SOUNDS.computeIfAbsent(swing.swingSoundId(), NpcMeleeAttackGoal::lookupSound).orElse(null);
        if (event == null)
        {
            return;
        }
        float pitch = swing.swingPitch();
        float randomness = swing.swingPitchRandomness();
        if (randomness > 0.0F)
        {
            pitch += (this.npc.getRandom().nextFloat() - 0.5F) * 2.0F * randomness;
        }
        this.npc.playSound(event, swing.swingVolume(), pitch);
    }
}
