package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ancient Warfare's NpcAIBlockWithShield. The NPC raises an off-hand shield
 * (any item that can shield-block, modded ones included) against its own
 * target when that target is a player or a mob targeting this NPC, and is
 * either swinging within {@code shieldBlockRange} or drawing a bow at it.
 * After a short random reaction delay the shield stays up at least
 * {@code shieldHoldTicks}, and longer while the threat keeps up.
 * <p>
 * Our NPCs often swing with a Better Combat clip and no vanilla swing flag, so
 * an NPC attacker counts as swinging while its attack window is open, which is
 * an earlier and better telegraph. The shield drops while this NPC commits to
 * its own swing, as a player cannot block and attack at once. No flags: it
 * runs beside the melee goal.
 */
public final class NpcShieldGoal extends Goal
{
    /** Goals are checked on alternate ticks, so a multiple of 2 keeps this to every 4. */
    private static final int AIM_WATCH_INTERVAL = 4;
    /** About 15 degrees: a player whose view is this close to the NPC is aiming at it. */
    private static final double PLAYER_AIM_DOT = 0.965D;

    private final NpcEntity npc;
    @Nullable
    private LivingEntity threat;
    @Nullable
    private LivingEntity candidate;
    private int reactionLeft;
    private int holdLeft;
    /** Who the NPC is bracing to bash, and the ticks left until it lands. */
    @Nullable
    private LivingEntity bashTarget;
    private int bashLeft;

    public NpcShieldGoal(NpcEntity npc)
    {
        this.npc = npc;
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public boolean canUse()
    {
        if (!this.ready())
        {
            return false;
        }
        LivingEntity target = this.npc.getTarget();
        if (target != null && target.isAlive() && this.threatening(target))
        {
            this.candidate = target;
            return true;
        }
        // Beyond Ancient Warfare: an archer the NPC is not fighting can still be seen
        // drawing on it. Looked for every few ticks, staggered, not every tick.
        if ((this.npc.tickCount + this.npc.getId()) % AIM_WATCH_INTERVAL != 0)
        {
            return false;
        }
        this.candidate = this.findAimer();
        return this.candidate != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        LivingEntity threat = this.threat;
        return this.ready() && threat != null && threat.isAlive() && this.holdLeft > 0;
    }

    @Override
    public void start()
    {
        this.threat = this.candidate;
        this.candidate = null;
        int reaction = Math.max(0, this.npc.getShieldReactionTicks());
        this.reactionLeft = reaction == 0 ? 0 : this.npc.getRandom().nextInt(reaction + 1);
        this.holdLeft = AncientEmpiresConfig.shieldHoldTicks;
    }

    @Override
    public void stop()
    {
        this.bashTarget = null;
        this.npc.getGuard().lower();
        this.npc.getGuard().setBracing(false);
        this.threat = null;
    }

    @Override
    public void tick()
    {
        LivingEntity threat = this.threat;
        if (threat == null)
        {
            return;
        }
        if (this.threatening(threat))
        {
            this.holdLeft = AncientEmpiresConfig.shieldHoldTicks;
        }
        else
        {
            // In a duel the shield comes down as soon as the spell ends: the open stretch is the point.
            this.holdLeft = this.npc.getGuard().isDuel() ? 0 : this.holdLeft - 1;
        }
        LivingEntity pending = this.npc.getGuard().takePendingBash();
        if (pending != null && this.bashTarget == null)
        {
            // Brace first, shield still up, with a sound a player can learn to read.
            this.bashTarget = pending;
            this.bashLeft = AncientEmpiresConfig.shieldBashWindupTicks;
            this.npc.getGuard().setBracing(true);
            NpcShieldBash.brace(this.npc, pending);
        }
        if (this.bashTarget != null)
        {
            this.npc.getLookControl().setLookAt(this.bashTarget, 30.0F, 30.0F);
            if (--this.bashLeft > 0)
            {
                this.npc.getGuard().raise();
                return;
            }
            NpcShieldBash.bash(this.npc, this.bashTarget);
            this.bashTarget = null;
            this.npc.getGuard().setBracing(false);
            return;
        }
        if (this.npc.isSwingCommitted())
        {
            this.npc.getGuard().lower();
            return;
        }
        if (this.reactionLeft > 0)
        {
            this.reactionLeft--;
            return;
        }
        this.npc.getLookControl().setLookAt(threat, 30.0F, 30.0F);
        if (NpcParryPlanner.waitToRaise(this.npc, threat))
        {
            // Holding the raise for a perfect block, just before the hit.
            return;
        }
        this.npc.getGuard().raise();
    }

    private boolean ready()
    {
        return AncientEmpiresConfig.shieldBlocking
                && this.npc.hasShieldInOffhand()
                && !this.npc.isShieldDisabled()
                && !this.npc.isHoldingBow();
    }

    /**
     * A player, or a mob whose target is this NPC, that is swinging in reach
     * or aiming a bow or crossbow.
     */
    private boolean threatening(LivingEntity attacker)
    {
        if (!(attacker instanceof Player) && !(attacker instanceof Mob mob && mob.getTarget() == this.npc))
        {
            return false;
        }
        if (aiming(attacker) || this.npc.getGuard().inSpell())
        {
            return true;
        }
        double range = AncientEmpiresConfig.shieldBlockRange;
        // Guard stance: an enemy in reach is a threat whether or not it is mid-swing. Not in a
        // duel, whose spells raise the shield in turns (a visible wind-up still raises it).
        boolean guard = AncientEmpiresConfig.shieldGuardInRange && !this.npc.getGuard().isDuel();
        return (guard || swinging(attacker)) && this.npc.distanceToSqr(attacker) < range * range;
    }

    /**
     * The nearest player or mob within {@code aimWatchRange} drawing a bow or
     * crossbow at this NPC: a mob whose target it is, or a player looking
     * straight at it (never its own owner).
     */
    @Nullable
    private LivingEntity findAimer()
    {
        double range = AncientEmpiresConfig.shieldAimWatchRange;
        if (range <= 0.0D)
        {
            return null;
        }
        LivingEntity incoming = this.incomingArrowFrom(range);
        if (incoming != null)
        {
            // ToroQuest's guards: an arrow on its way puts the shield up at once.
            this.npc.getGuard().blockFor(AncientEmpiresConfig.shieldArrowBlockTicks);
            return incoming;
        }
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity other : this.npc.level().getEntitiesOfClass(LivingEntity.class, this.npc.getBoundingBox().inflate(range),
                living -> living != this.npc && living.isAlive() && aiming(living)))
        {
            if (!this.aimsAtMe(other))
            {
                continue;
            }
            double distance = this.npc.distanceToSqr(other);
            if (distance < best)
            {
                best = distance;
                nearest = other;
            }
        }
        return nearest;
    }

    /** The shooter of an arrow in flight within {@code range} heading at this NPC, if not an ally's. */
    @Nullable
    private LivingEntity incomingArrowFrom(double range)
    {
        Vec3 centre = this.npc.getBoundingBox().getCenter();
        for (AbstractArrow arrow : this.npc.level().getEntitiesOfClass(AbstractArrow.class, this.npc.getBoundingBox().inflate(range),
                arrow -> arrow.isAlive() && arrow.getDeltaMovement().lengthSqr() > 0.01D))
        {
            if (!(arrow.getOwner() instanceof LivingEntity shooter) || shooter == this.npc
                    || shooter instanceof NpcEntity other && this.npc.isAlly(other) || shooter.getUUID().equals(this.npc.getOwner()))
            {
                continue;
            }
            Vec3 toMe = centre.subtract(arrow.position()).normalize();
            if (arrow.getDeltaMovement().normalize().dot(toMe) > 0.97D)
            {
                return shooter;
            }
        }
        return null;
    }

    private boolean aimsAtMe(LivingEntity aimer)
    {
        if (aimer instanceof Mob mob)
        {
            return mob.getTarget() == this.npc;
        }
        if (!(aimer instanceof Player player) || player.isSpectator() || player.getUUID().equals(this.npc.getOwner()))
        {
            return false;
        }
        Vec3 toMe = this.npc.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        return player.getViewVector(1.0F).dot(toMe) > PLAYER_AIM_DOT;
    }

    static boolean swinging(LivingEntity attacker)
    {
        if (attacker instanceof NpcEntity other && other.isAttackWindowOpen())
        {
            return true;
        }
        return attacker.swinging;
    }

    private static boolean aiming(LivingEntity attacker)
    {
        if (!attacker.isUsingItem())
        {
            return false;
        }
        UseAnim anim = attacker.getUseItem().getUseAnimation();
        return anim == UseAnim.BOW || anim == UseAnim.CROSSBOW || anim == UseAnim.SPEAR;
    }
}
