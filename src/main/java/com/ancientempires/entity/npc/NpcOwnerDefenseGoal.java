package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Ancient Warfare's owner defense for owned NPCs, both directions in one class:
 * {@link Mode#DEFEND} targets whoever last hurt the owner, {@link Mode#ASSIST}
 * targets whatever the owner last hurt. Each fires once per new hit, read from
 * the owner's own timestamps, so an old fight is not picked up again. Faction
 * NPCs have no owner and never run this.
 */
public final class NpcOwnerDefenseGoal extends TargetGoal
{
    public enum Mode
    {
        DEFEND,
        ASSIST
    }

    private static final TargetingConditions CONDITIONS = TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

    private final NpcEntity npc;
    private final Mode mode;
    private int seenTimestamp;
    @Nullable
    private LivingEntity found;

    public NpcOwnerDefenseGoal(NpcEntity npc, Mode mode)
    {
        super(npc, false, false);
        this.npc = npc;
        this.mode = mode;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse()
    {
        if (!AncientEmpiresConfig.ownerDefense || !this.npc.isOwnedNpc())
        {
            return false;
        }
        Player owner = this.owner();
        if (owner == null)
        {
            return false;
        }
        LivingEntity other = this.mode == Mode.DEFEND ? owner.getLastHurtByMob() : owner.getLastHurtMob();
        int timestamp = this.mode == Mode.DEFEND ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp();
        if (other == null || timestamp == this.seenTimestamp)
        {
            return false;
        }
        double range = AncientEmpiresConfig.ownerDefenseRange;
        if (this.npc.distanceToSqr(owner) > range * range)
        {
            return false;
        }
        if (other == this.npc || !this.npc.canAttack(other) || !this.canAttack(other, CONDITIONS))
        {
            return false;
        }
        this.found = other;
        return true;
    }

    @Override
    public void start()
    {
        Player owner = this.owner();
        if (owner != null)
        {
            this.seenTimestamp = this.mode == Mode.DEFEND ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp();
        }
        this.mob.setTarget(this.found);
        this.targetMob = this.found;
        this.found = null;
        super.start();
    }

    @Nullable
    private Player owner()
    {
        UUID ownerId = this.npc.getOwner();
        if (ownerId == null)
        {
            return null;
        }
        Player player = this.npc.level().getPlayerByUUID(ownerId);
        return player == null || player.isSpectator() ? null : player;
    }
}
