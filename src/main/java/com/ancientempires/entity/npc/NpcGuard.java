package com.ancientempires.entity.npc;

import com.ancientempires.combat.WeaponClassifier;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Everything an NPC does with its shield, in one place: raising and lowering
 * it, the commitment of a block (no swing with the shield up, a minimum block,
 * a beat before the swing), who goes first between two guards, the riposte,
 * the guard that blocked hits wear down and that breaks, the bash and its
 * brace, the stagger a perfect block deals, and the plan to time a perfect
 * block. {@link NpcEntity} keeps only the vanilla hooks, which call in here.
 * Server side; the stagger is synced for the recoil pose.
 * <p>
 * The rules, and Ancient Warfare's parts of them, are in CLAUDE.md under
 * Shields, Bash, and Perfect block.
 */
public final class NpcGuard
{
    private final NpcEntity npc;
    /** tickCount until which an axe hit or a broken guard keeps the shield down. */
    private int disabledUntil;
    /** tickCount until which the shield stays down after a bash. */
    private int downUntil;
    /** tickCount the shield last went up and came down, for the block commitment. */
    private int raisedAt;
    private int loweredAt = -1000;
    /** The melee goal asked to strike: the shield stays down until the swing ends. */
    private boolean wantsToStrike;
    /** tickCount the current strike began readying, for who-goes-first between guards. */
    private int strikeSince;
    /** tickCount before which no swing may start (a bash's recovery, a stagger). */
    private int strikeReadyAt;
    /** tickCount until which a riposte after a blocked hit may skip the minimum block. */
    private int riposteUntil;
    /** Bracing for a shield bash: no strike until it lands. */
    private boolean bracing;
    /** Attacker to shield-bash after a blocked hit, and when the next bash may come. */
    @Nullable
    private LivingEntity pendingBash;
    private int bashReadyTick;
    /** tickCount of the last blocked hit, so it does not also knock the NPC back. */
    private int blockedAt = -1;
    /** The shield's guard; below zero means full. */
    private float points = -1.0F;
    /** tickCount until which a perfect block keeps this NPC staggered. */
    private int staggerUntil;
    /** Perfect-block plan against one swing: the attacker's id and window serial, and the wind-up left at which to raise. */
    int planFor = -1;
    int planSerial = -1;
    int planAt = Integer.MIN_VALUE;
    /** The current raise was timed by the planner, so a perfect block may come of it. */
    private boolean plannedRaise;
    /**
     * Duel mode (set by the melee goal): the shield goes up in timed spells, as
     * ToroQuest's guards did, instead of whenever an enemy is in reach. A spell
     * lasts until {@code spellUntil}; after it the NPC stays open (and attacks)
     * until {@code openUntil}. Spells start only once the foe has hit it.
     */
    private boolean duel;
    private int spellUntil;
    private int openUntil;
    /** The shove on the first blocked hit of a spell has been spent. */
    private boolean shoved;
    /** tickCount of the last perfect block, -1 for never. */
    private int perfectAt = -1;

    NpcGuard(NpcEntity npc)
    {
        this.npc = npc;
    }

    /** Once per server tick: the guard recovers and a stagger runs out. */
    void tick()
    {
        if (this.points >= 0.0F && this.points < this.max())
        {
            // Recovers between blows, a quarter as fast while still holding the shield up.
            float regen = (float) AncientEmpiresConfig.shieldGuardRegen;
            this.points = Math.min(this.max(), this.points + (this.npc.isRaisingShield() ? regen * 0.25F : regen));
        }
        if (this.staggerUntil > 0 && this.npc.tickCount >= this.staggerUntil)
        {
            this.staggerUntil = 0;
            this.npc.setStaggeredFlag(false);
        }
        this.tickSpells();
    }

    /** Ends a spent block spell into an open stretch, and in a duel starts the next spell when due. */
    private void tickSpells()
    {
        int now = this.npc.tickCount;
        if (this.spellUntil > 0 && now >= this.spellUntil)
        {
            this.spellUntil = 0;
            int gap = AncientEmpiresConfig.shieldDuelOpenMin
                    + this.npc.getRandom().nextInt(Math.max(1, AncientEmpiresConfig.shieldDuelOpenMax - AncientEmpiresConfig.shieldDuelOpenMin + 1));
            this.openUntil = now + gap;
        }
        if (!this.duel || this.spellUntil > 0 || now < this.openUntil || !this.npc.hasShieldInOffhand() || this.isDisabled())
        {
            return;
        }
        LivingEntity target = this.npc.getTarget();
        double reach = AncientEmpiresConfig.shieldBlockRange;
        // Only once the foe has struck at it: an untested foe is pressed, not feared.
        if (target != null && target.isAlive() && this.npc.getLastHurtByMob() == target && this.npc.distanceToSqr(target) < reach * reach)
        {
            double distance = Math.sqrt(this.npc.distanceToSqr(target));
            int span = AncientEmpiresConfig.shieldDuelBlockMin
                    + this.npc.getRandom().nextInt(Math.max(1, AncientEmpiresConfig.shieldDuelBlockMax - AncientEmpiresConfig.shieldDuelBlockMin + 1));
            // Shorter up close, where the next blow comes sooner.
            this.blockFor(Math.max(AncientEmpiresConfig.shieldDuelBlockMin, span - (int) (distance * 8.0D)));
        }
    }

    /** Holds the shield up for {@code ticks} (a duel spell, or an arrow on its way). */
    public void blockFor(int ticks)
    {
        if (this.spellUntil <= 0)
        {
            this.shoved = false;
        }
        this.spellUntil = Math.max(this.spellUntil, this.npc.tickCount + ticks);
    }

    /** In a block spell: the shield goal keeps the shield up and no strike is begun. */
    public boolean inSpell()
    {
        return this.spellUntil > 0 && this.npc.tickCount < this.spellUntil;
    }

    public boolean isDuel()
    {
        return this.duel;
    }

    /** The melee goal's stance: a duel uses spells, a line the steady guard. */
    public void setDuel(boolean duel)
    {
        if (this.duel && !duel)
        {
            this.spellUntil = 0;
            this.openUntil = 0;
        }
        this.duel = duel;
    }

    /** True while an axe hit or a broken guard keeps the shield down. */
    public boolean isDisabled()
    {
        return this.npc.tickCount < this.disabledUntil;
    }

    public void raise()
    {
        if (!this.npc.isUsingItem() && this.npc.hasShieldInOffhand() && !this.isDisabled() && this.npc.tickCount >= this.downUntil
                && !this.wantsToStrike)
        {
            this.npc.startUsingItem(InteractionHand.OFF_HAND);
            this.raisedAt = this.npc.tickCount;
            // A raise counts as a perfect-block attempt only if the planner chose to try one for this swing.
            this.plannedRaise = this.planAt != Integer.MIN_VALUE;
        }
    }

    /** Keeps the shield down for {@code ticks} (a swing's follow-through), on top of any longer hold. */
    /**
     * A player's blow on the raised shield: no wind-up to time against, so a
     * skill roll ({@code perfectBlock.vsPlayerScale} of the parry chance) decides
     * whether it is turned aside as a perfect block, at most once per cooldown.
     */
    public boolean rollsPerfectAgainstPlayer()
    {
        if (!this.npc.isRaisingShield() || this.perfectAt >= 0 && this.npc.tickCount - this.perfectAt < AncientEmpiresConfig.parryCooldownTicks)
        {
            return false;
        }
        return this.npc.getRandom().nextDouble() < this.npc.getParryChance() * AncientEmpiresConfig.parryVsPlayerScale;
    }

    /** Whether this raise was a planned perfect-block attempt (NPCs only perfect-block on purpose). */
    public boolean isPlannedRaise()
    {
        return this.plannedRaise;
    }

    public void holdDown(int ticks)
    {
        this.downUntil = Math.max(this.downUntil, this.npc.tickCount + ticks);
    }

    public void lower()
    {
        if (this.npc.isRaisingShield())
        {
            this.npc.stopUsingItem();
            this.loweredAt = this.npc.tickCount;
        }
    }

    /**
     * The melee goal wants to attack. A block is a commitment: the shield comes
     * down only once it has been up {@code shield.minBlockTicks}, stays down
     * until the swing is over, and a swing may begin only
     * {@code shield.strikeDelayTicks} after it came down. True once the NPC may
     * begin its swing.
     */
    /** A mob with no wind-up that is after this NPC and close: bait it rather than open up to it. */
    private boolean baitsMob(@Nullable LivingEntity target)
    {
        if (!(target instanceof Mob mob) || target instanceof NpcEntity || mob.getTarget() != this.npc)
        {
            return false;
        }
        double reach = AncientEmpiresConfig.shieldBlockRange;
        return this.npc.distanceToSqr(target) < reach * reach;
    }

    public boolean readyToStrike()
    {
        int now = this.npc.tickCount;
        if (this.bracing || now < this.strikeReadyAt)
        {
            return false;
        }
        if (!this.npc.hasShieldInOffhand() || this.isDisabled())
        {
            if (!this.wantsToStrike)
            {
                this.strikeSince = now;
            }
            this.wantsToStrike = true;
            return now - this.loweredAt >= AncientEmpiresConfig.shieldStrikeDelayTicks;
        }
        // Ancient Warfare kept blocking and attacking apart: strike only into an opening,
        // never while an NPC target is winding up at this NPC, nor while it is already lowering
        // its own shield to strike (whoever started first goes; the other blocks, then ripostes).
        // A vanilla mob or player has no wind-up: its swing is the hit, so just after it is the opening.
        if (this.npc.getTarget() instanceof NpcEntity other && (other.isAttackWindowOpen() || other.getGuard().strikesBefore(this)))
        {
            // Caught lowering it: cancel the strike, so the shield goes straight back up.
            this.wantsToStrike = false;
            return false;
        }
        boolean riposte = now < this.riposteUntil;
        if (!riposte && this.inSpell())
        {
            // A duel's block spell: the shield stays up until it ends or a blow cuts it short.
            return false;
        }
        if (this.npc.isRaisingShield())
        {
            if (!riposte && baitsMob(this.npc.getTarget()) && now - Math.max(this.raisedAt, this.blockedAt) < AncientEmpiresConfig.shieldMobPatienceTicks)
            {
                // A mob shows no wind-up: let its blow land on the shield, then riposte while it recovers.
                return false;
            }
            if (!riposte && now - this.raisedAt < AncientEmpiresConfig.shieldMinBlockTicks)
            {
                return false;
            }
            // Not on the first allowed tick: a random beat later, so strikes are hard to time
            // and two guards do not open together.
            if (!riposte && this.npc.getRandom().nextFloat() >= AncientEmpiresConfig.shieldStrikeChance)
            {
                return false;
            }
            this.lower();
        }
        if (!this.wantsToStrike)
        {
            this.wantsToStrike = true;
            this.strikeSince = now;
        }
        int delay = riposte ? AncientEmpiresConfig.shieldStrikeDelayTicks / 2 : AncientEmpiresConfig.shieldStrikeDelayTicks;
        return now - this.loweredAt >= delay;
    }

    /** This NPC began readying a strike before {@code other} did (or {@code other} has not). */
    private boolean strikesBefore(NpcGuard other)
    {
        return strikesFirst(this.wantsToStrike && !this.npc.isAttackWindowOpen(), this.strikeSince, other.wantsToStrike, other.strikeSince);
    }

    /**
     * The tie-break between two guards, as a rule: one readying a strike goes first if
     * the other is not readying one, or began later. A tie cancels neither; it is rare,
     * since NPCs tick one after another and the second already sees the first readying.
     */
    public static boolean strikesFirst(boolean readying, int since, boolean otherReadying, int otherSince)
    {
        return readying && (!otherReadying || since < otherSince);
    }

    /** The swing is over, or the melee goal gave up: the shield may go back up. */
    public void clearStrike()
    {
        this.wantsToStrike = false;
    }

    /** Thrown off balance by a perfect block: no swing, and any wind-up is broken off. Shown as a recoil. */
    public void stagger(int ticks)
    {
        this.staggerUntil = this.npc.tickCount + ticks;
        this.npc.setStaggeredFlag(true);
        this.strikeReadyAt = Math.max(this.strikeReadyAt, this.staggerUntil);
    }

    boolean isStaggered()
    {
        return this.npc.tickCount < this.staggerUntil;
    }

    /** A perfect block landed: the attacker reels, so the NPC may strike straight back. */
    public void onPerfectBlock()
    {
        this.perfectAt = this.npc.tickCount;
        this.strikeReadyAt = 0;
        this.riposteUntil = this.npc.tickCount + AncientEmpiresConfig.shieldRiposteTicks;
    }

    /** What is left of the guard, out of {@link #max()}. */
    public float points()
    {
        return this.points < 0.0F ? this.max() : this.points;
    }

    /** Full guard: {@code shield.guardMax} times the skill tier's {@code skill.guardByTier}. */
    public float max()
    {
        return (float) (AncientEmpiresConfig.shieldGuardPoints * this.npc.getGuardScale());
    }

    /**
     * A hit taken on the raised shield. Guard, as Chivalry's stamina: every blocked
     * hit wears it, so a shield wall can be broken; at zero the shield is knocked down.
     */
    void onShieldHit(float amount)
    {
        this.blockedAt = this.npc.tickCount;
        if (this.inSpell() && this.spellUntil - this.npc.tickCount > 10 && this.npc.getRandom().nextBoolean())
        {
            // Half the time a blow on the shield cuts the spell short: half a second, then the answer.
            this.spellUntil = this.npc.tickCount + 10;
        }
        this.points = this.points() - Math.max(1.0F, amount);
        if (this.points <= 0.0F && WeaponClassifier.isShield(this.npc.getUseItem()))
        {
            this.points = 0.0F;
            this.disabledUntil = this.npc.tickCount + AncientEmpiresConfig.shieldGuardBreakTicks;
            this.lower();
            this.npc.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.7F + this.npc.getRandom().nextFloat() * 0.2F);
        }
        else
        {
            // The hit rings on the shield. Vanilla sends this as an entity event, but the client plays
            // an entity's own sounds only for the local player, so an NPC's block was silent.
            this.npc.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + this.npc.getRandom().nextFloat() * 0.4F);
        }
    }

    /** A hit just taken on the shield does not also shove the NPC back. */
    boolean blockedThisTick()
    {
        return this.blockedAt == this.npc.tickCount;
    }

    /** After vanilla blocked a melee hit: an axe disables the shield; otherwise a riposte opens, and maybe a bash. */
    void onBlocked(LivingEntity attacker)
    {
        this.npc.addXp(AncientEmpiresConfig.levelXpPerBlock);
        if (this.isDisabled())
        {
            // The guard broke on this hit: no riposte or bash from behind a shield that is down.
            return;
        }
        // A player's axe always knocks the shield out (vanilla); another NPC's only sometimes.
        if (attacker.getMainHandItem().canDisableShield(this.npc.getUseItem(), this.npc, attacker)
                && (attacker instanceof Player || this.npc.getRandom().nextDouble() < AncientEmpiresConfig.shieldNpcAxeChance))
        {
            this.disabledUntil = this.npc.tickCount + AncientEmpiresConfig.shieldDisableTicks;
            this.lower();
            this.npc.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.npc.getRandom().nextFloat() * 0.4F);
            return;
        }
        // A blocked hit leaves the attacker open: a riposte may skip the minimum block.
        this.riposteUntil = this.npc.tickCount + AncientEmpiresConfig.shieldRiposteTicks;
        if (this.duel && this.inSpell() && !this.shoved && AncientEmpiresConfig.shieldDuelShove > 0.0D)
        {
            // The first blow of a spell is met with a shove off the shield (ToroQuest's shield push).
            this.shoved = true;
            attacker.knockback(AncientEmpiresConfig.shieldDuelShove, this.npc.getX() - attacker.getX(), this.npc.getZ() - attacker.getZ());
            attacker.hurtMarked = true;
        }
        // A blocked hit is the moment to shove back, as a player would with Spartan's bash key.
        if (NpcShieldBash.canBash(this.npc) && this.npc.getRandom().nextDouble() < this.npc.getBashChance())
        {
            this.pendingBash = attacker;
        }
    }

    void setBracing(boolean bracing)
    {
        this.bracing = bracing;
    }

    /** The attacker to counter-bash, once; the shield goal calls this each tick. */
    @Nullable
    LivingEntity takePendingBash()
    {
        LivingEntity attacker = this.pendingBash;
        this.pendingBash = null;
        return attacker;
    }

    boolean isBashReady()
    {
        return this.npc.tickCount >= this.bashReadyTick;
    }

    void startBashCooldown(int ticks)
    {
        int now = this.npc.tickCount;
        this.bashReadyTick = now + ticks;
        this.downUntil = now + AncientEmpiresConfig.shieldBashRecoveryTicks;
        // The bash spends the riposte; no swing follows straight on.
        this.strikeReadyAt = now + AncientEmpiresConfig.shieldBashRecoveryTicks;
        this.riposteUntil = 0;
    }
}
