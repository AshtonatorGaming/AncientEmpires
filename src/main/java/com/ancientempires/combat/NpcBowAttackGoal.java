package com.ancientempires.combat;

import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.EnumSet;

/**
 * Archer role for a bow in the main hand. The goal stays up for the whole
 * fight so melee never starts. In range with line of sight, it keeps drawing
 * and firing, including when the target is close. It does not path into melee
 * to acquire a shot. A later sidearm swap would read the backpack. This goal
 * does not switch weapons. Crossbows are not bows.
 */
public class NpcBowAttackGoal extends Goal
{
    /** Closest an approach may path. A target already inside this is still shot. */
    private static final double MELEE_RANGE = 4.0D;
    /** Ticks after a shot before the next draw. The draw itself is separate. */
    private static final int COOLDOWN_TICKS = 20;
    /** Arrow drag and gravity, matching {@link AbstractArrow}. */
    private static final double ARROW_DRAG = 0.99D;
    private static final double ARROW_GRAVITY = 0.05D;
    private static final double APPROACH_SPEED = 1.0D;
    private static final double WANDER_SPEED = 0.6D;

    private final NpcEntity npc;

    private boolean drawing;
    private int drawTicks;
    private int drawGoal;
    private int cooldownTicks;
    private int repath;
    private int wanderDelay;
    /** True while the current path is a close-to-range walk, so idle can drop it. */
    private boolean closing;
    /** True while the current path walks back to the hold post. */
    private boolean returning;

    public NpcBowAttackGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        return this.engaged();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.engaged();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void start()
    {
        this.drawing = false;
        this.drawTicks = 0;
        this.drawGoal = BowItem.MAX_DRAW_DURATION;
        this.cooldownTicks = 0;
        this.repath = 0;
        this.wanderDelay = 0;
        this.closing = false;
        this.returning = false;
        this.npc.getNavigation().stop();
    }

    @Override
    public void stop()
    {
        this.stopDraw();
        this.cooldownTicks = 0;
        this.closing = false;
        this.returning = false;
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        LivingEntity target = this.npc.getTarget();
        if (target == null)
        {
            return;
        }
        if (this.npc.isHoldingPosition() && this.npc.isOutsideHold())
        {
            this.stopDraw();
            if (!this.returning)
            {
                this.closing = false;
                this.returning = true;
                this.repath = 0;
                this.npc.getNavigation().stop();
            }
            this.look(target);
            this.pathTowardHold();
            return;
        }
        this.returning = false;
        if (this.canShoot(target))
        {
            this.closing = false;
            this.face(target);
            this.tickShot(target);
            return;
        }
        this.stopDraw();
        if (this.shouldClose(target))
        {
            this.face(target);
            this.approach(target);
            return;
        }
        this.look(target);
        this.idle();
    }

    /**
     * A living target and a bow. Range and sight change the tick, not whether
     * this goal owns the fight.
     */
    private boolean engaged()
    {
        LivingEntity target = this.npc.getTarget();
        return target != null && target.isAlive() && this.npc.isHoldingBow();
    }

    private boolean canShoot(LivingEntity target)
    {
        double range = this.bowRange();
        return this.npc.distanceToSqr(target) <= range * range
                && this.npc.getSensing().hasLineOfSight(target);
    }

    /**
     * Walk in only while the target is past bow range, in sight, and hold is off.
     * The stop stays outside melee. A target that walks in is shot from where the NPC stands.
     */
    private boolean shouldClose(LivingEntity target)
    {
        if (this.npc.isHoldingPosition() || !this.npc.getSensing().hasLineOfSight(target))
        {
            return false;
        }
        return this.npc.distanceToSqr(target) > this.bowRange() * this.bowRange();
    }

    private double bowRange()
    {
        Item item = this.npc.getMainHandItem().getItem();
        if (item instanceof BowItem bow)
        {
            return bow.getDefaultProjectileRange();
        }
        return BowItem.DEFAULT_RANGE;
    }

    private void tickShot(LivingEntity target)
    {
        this.npc.getNavigation().stop();
        if (this.cooldownTicks > 0)
        {
            this.cooldownTicks--;
            if (this.cooldownTicks > 0)
            {
                return;
            }
        }
        if (!this.drawing)
        {
            this.beginDraw();
        }
        this.drawTicks++;
        if (this.drawTicks >= this.drawGoal)
        {
            this.release(target);
            this.cooldownTicks = COOLDOWN_TICKS;
        }
    }

    private void approach(LivingEntity target)
    {
        double desired = Math.max(MELEE_RANGE + 1.0D, this.bowRange() - 1.0D);
        double dx = target.getX() - this.npc.getX();
        double dy = target.getY() - this.npc.getY();
        double dz = target.getZ() - this.npc.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= desired)
        {
            this.closing = false;
            this.npc.getNavigation().stop();
            return;
        }
        this.closing = true;
        if (this.repath > 0)
        {
            this.repath--;
            return;
        }
        this.repath = 10;
        double scale = (distance - desired) / distance;
        this.npc.getNavigation().moveTo(
                this.npc.getX() + dx * scale,
                this.npc.getY() + dy * scale,
                this.npc.getZ() + dz * scale,
                APPROACH_SPEED);
    }

    private void idle()
    {
        if (this.closing)
        {
            this.closing = false;
            this.npc.getNavigation().stop();
            this.wanderDelay = 15;
        }
        if (this.npc.isHoldingPosition())
        {
            this.npc.getNavigation().stop();
            return;
        }
        this.wander();
    }

    /**
     * A few blocks nearby. The spot is not the target, so this is not a chase.
     */
    private void wander()
    {
        if (this.npc.getNavigation().isInProgress())
        {
            return;
        }
        if (this.wanderDelay > 0)
        {
            this.wanderDelay--;
            return;
        }
        Vec3 spot = DefaultRandomPos.getPos(this.npc, 3, 2);
        if (spot == null)
        {
            this.wanderDelay = 20;
            return;
        }
        this.npc.getNavigation().moveTo(spot.x, spot.y, spot.z, WANDER_SPEED);
        this.wanderDelay = 40 + this.npc.getRandom().nextInt(40);
    }

    private void pathTowardHold()
    {
        if (this.repath > 0)
        {
            this.repath--;
            return;
        }
        this.repath = 10;
        this.npc.moveTowardHold(0.9D);
    }

    private void beginDraw()
    {
        if (this.npc.isUsingItem())
        {
            this.npc.stopUsingItem();
        }
        this.drawing = true;
        this.drawTicks = 0;
        this.drawGoal = drawLength(this.npc.getMainHandItem());
        this.npc.startUsingItem(InteractionHand.MAIN_HAND);
    }

    private void stopDraw()
    {
        this.drawing = false;
        this.drawTicks = 0;
        if (this.npc.isUsingItem())
        {
            this.npc.stopUsingItem();
        }
    }

    private void release(LivingEntity target)
    {
        ItemStack bow = this.npc.getMainHandItem();
        int drawn = this.drawTicks;
        this.stopDraw();
        if (this.npc.level().isClientSide || !(bow.getItem() instanceof BowItem bowItem))
        {
            return;
        }
        float power = BowItem.getPowerForTime(drawn);
        if (power < 0.1F)
        {
            return;
        }
        Level level = this.npc.level();
        ItemStack ammo = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ((ArrowItem) Items.ARROW).createArrow(level, ammo, this.npc);
        arrow = bowItem.customArrow(arrow);
        if (arrow == null)
        {
            return;
        }
        this.applyBowEnchantments(arrow, bow);
        if (power >= 1.0F)
        {
            arrow.setCritArrow(true);
        }
        // The NPC does not spend ammo, so the arrow is not picked back up.
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        double dx = target.getX() - this.npc.getX();
        double dz = target.getZ() - this.npc.getZ();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        float speed = power * 3.0F;
        arrow.shoot(dx, dy + gravityLift(dx, dy, dz, speed), dz, speed, 1.0F);
        level.addFreshEntity(arrow);
        // Vanilla bows have no separate nock sound. This is the release.
        float pitch = 1.0F / (this.npc.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F;
        this.npc.playSound(SoundEvents.ARROW_SHOOT, 1.0F, pitch);
    }

    /**
     * Power, Punch, and Flame on the bow. Base damage stays the arrow's own 2.0,
     * and the shot speed is a player's full draw, so the hit is a normal bow shot.
     */
    private static void applyBowEnchantments(AbstractArrow arrow, ItemStack bow)
    {
        int power = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (power > 0)
        {
            arrow.setBaseDamage(arrow.getBaseDamage() + (double) power * 0.5D + 0.5D);
        }
        int punch = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (punch > 0)
        {
            arrow.setKnockback(punch);
        }
        if (EnchantmentHelper.getTagEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0)
        {
            arrow.setSecondsOnFire(100);
        }
    }

    /**
     * A short full draw. {@link BowItem#getUseDuration} is the hold cap, 72000,
     * so it is only used when a bow reports a real draw length. Spartan longbows
     * store a multiplier in {@code drawTime}; 1.25 is 25 ticks.
     */
    private static int drawLength(ItemStack stack)
    {
        int reported = stack.getUseDuration();
        if (reported > 0 && reported <= 60)
        {
            return reported;
        }
        Float multiplier = readDrawTime(stack.getItem());
        if (multiplier != null && multiplier.floatValue() >= 0.5F && multiplier.floatValue() <= 3.0F)
        {
            return Math.max(1, Math.round(BowItem.MAX_DRAW_DURATION * multiplier.floatValue()));
        }
        return BowItem.MAX_DRAW_DURATION;
    }

    private static Float readDrawTime(Item item)
    {
        Class<?> type = item.getClass();
        while (type != null && type != Item.class)
        {
            try
            {
                Field field = type.getDeclaredField("drawTime");
                if (field.getType() != float.class)
                {
                    return null;
                }
                field.setAccessible(true);
                return field.getFloat(item);
            }
            catch (NoSuchFieldException exception)
            {
                type = type.getSuperclass();
            }
            catch (Exception exception)
            {
                return null;
            }
        }
        return null;
    }

    /**
     * Extra upward aim so the arrow arrives at the torso. The step matches
     * vanilla arrow motion: move, drag by 0.99, then drop 0.05.
     */
    private static double gravityLift(double dx, double targetDy, double dz, float speed)
    {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-3D || speed < 0.1F)
        {
            return 0.0D;
        }
        double lift = 0.0D;
        for (int pass = 0; pass < 4; pass++)
        {
            double arrived = flightHeight(horizontal, targetDy + lift, speed);
            lift += targetDy - arrived;
        }
        return lift;
    }

    private static double flightHeight(double horizontal, double aimDy, float speed)
    {
        double length = Math.sqrt(horizontal * horizontal + aimDy * aimDy);
        if (length < 1.0E-4D)
        {
            return 0.0D;
        }
        double vx = horizontal / length * speed;
        double vy = aimDy / length * speed;
        double x = 0.0D;
        double y = 0.0D;
        for (int tick = 0; tick < 60 && x < horizontal; tick++)
        {
            x += vx;
            y += vy;
            vx *= ARROW_DRAG;
            vy = vy * ARROW_DRAG - ARROW_GRAVITY;
        }
        return y;
    }

    private void face(LivingEntity target)
    {
        double dx = target.getX() - this.npc.getX();
        double dz = target.getZ() - this.npc.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI) - 90.0D);
        this.npc.setYRot(yaw);
        this.npc.setYBodyRot(yaw);
        this.npc.setYHeadRot(yaw);
        this.look(target);
    }

    private void look(LivingEntity target)
    {
        this.npc.getLookControl().setLookAt(target.getX(), target.getY(0.3333333333333333D), target.getZ(), 30.0F, 30.0F);
    }
}
