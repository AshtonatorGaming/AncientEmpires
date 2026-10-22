package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
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
    private final NpcEntity npc;
    @Nullable
    private LivingEntity threat;
    private int reactionLeft;
    private int holdLeft;

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
        return target != null && target.isAlive() && this.threatening(target);
    }

    @Override
    public boolean canContinueToUse()
    {
        LivingEntity threat = this.threat;
        return this.ready() && threat != null && threat.isAlive() && threat == this.npc.getTarget() && this.holdLeft > 0;
    }

    @Override
    public void start()
    {
        this.threat = this.npc.getTarget();
        int reaction = Math.max(0, AncientEmpiresConfig.shieldReactionTicks);
        this.reactionLeft = reaction == 0 ? 0 : this.npc.getRandom().nextInt(reaction + 1);
        this.holdLeft = AncientEmpiresConfig.shieldHoldTicks;
    }

    @Override
    public void stop()
    {
        this.npc.lowerShield();
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
            this.holdLeft--;
        }
        if (this.npc.isSwingCommitted())
        {
            this.npc.lowerShield();
            return;
        }
        if (this.reactionLeft > 0)
        {
            this.reactionLeft--;
            return;
        }
        this.npc.raiseShield();
        this.npc.getLookControl().setLookAt(threat, 30.0F, 30.0F);
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
        if (aiming(attacker))
        {
            return true;
        }
        double range = AncientEmpiresConfig.shieldBlockRange;
        return swinging(attacker) && this.npc.distanceToSqr(attacker) < range * range;
    }

    private static boolean swinging(LivingEntity attacker)
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
