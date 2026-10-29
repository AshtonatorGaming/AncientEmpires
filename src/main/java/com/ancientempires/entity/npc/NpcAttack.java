package com.ancientempires.entity.npc;

import com.ancientempires.combat.AttackSwing;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.network.syncher.SynchedEntityData;
import org.jetbrains.annotations.Nullable;

/**
 * An NPC's swing as it happens: the combo step, the attack window (open from
 * the start of a wind-up to the hit, with the ticks left), and what the client
 * plays for it (the presentation: animation id, length, upswing, the server's
 * wind-up so the clip's hit frame lands on the hit, the off-hand beat). The
 * window and presentation are synced; the combo resets after a long enough
 * pause or a new weapon. Not saved.
 */
public final class NpcAttack
{
    private final NpcEntity npc;
    private int comboIndex;
    private int ticksSinceSwing;
    private int lastSwingCooldown;
    private boolean windowOpen;
    private int windupRemaining;
    @Nullable
    private String animation;
    /** Counts attack windows, so a defender can tell one swing from the next. */
    private int windowSerial;

    NpcAttack(NpcEntity npc)
    {
        this.npc = npc;
    }

    private SynchedEntityData data()
    {
        return this.npc.getEntityData();
    }

    public int comboIndex()
    {
        return this.comboIndex;
    }

    /** Called when a swing finishes, hit or miss. The next swing advances the combo. */
    public void completeSwing(int cooldownTicks)
    {
        this.npc.getGuard().clearStrike();
        this.comboIndex++;
        this.ticksSinceSwing = 0;
        this.lastSwingCooldown = Math.max(1, cooldownTicks);
    }

    /** Each server tick: the combo starts over after a pause of {@code comboResetRate} cooldowns. */
    void tick()
    {
        this.ticksSinceSwing++;
        if (this.comboIndex != 0 && this.lastSwingCooldown > 0
                && this.ticksSinceSwing > this.lastSwingCooldown * AncientEmpiresConfig.comboResetRate)
        {
            this.comboIndex = 0;
        }
    }

    /** A new weapon in the main hand starts the combo over. */
    void resetCombo()
    {
        this.comboIndex = 0;
        this.ticksSinceSwing = 0;
    }

    public void open(AttackSwing swing)
    {
        this.windowSerial++;
        this.windowOpen = true;
        this.windupRemaining = swing.windupTicks();
        this.animation = swing.animationId();
        String id = swing.animationId() == null ? "" : swing.animationId();
        this.data().set(NpcEntity.DATA_ATTACK_WINDOW, true);
        this.data().set(NpcEntity.DATA_ATTACK_ANIMATION, id);
        if (id.isEmpty())
        {
            this.clearPresentation();
            return;
        }
        this.data().set(NpcEntity.DATA_PRESENTATION_ID, id);
        this.data().set(NpcEntity.DATA_PRESENTATION_LENGTH, Math.max(1, swing.cooldownTicks()));
        this.data().set(NpcEntity.DATA_PRESENTATION_UPSWING, swing.animationUpswing());
        this.data().set(NpcEntity.DATA_PRESENTATION_WINDUP, swing.windupTicks());
        this.data().set(NpcEntity.DATA_ATTACK_OFFHAND, swing.offHand());
        this.data().set(NpcEntity.DATA_PRESENTATION_SERIAL, this.data().get(NpcEntity.DATA_PRESENTATION_SERIAL) + 1);
    }

    public void tickWindow()
    {
        if (this.windupRemaining > 0)
        {
            this.windupRemaining--;
        }
    }

    public void close()
    {
        this.npc.getGuard().clearStrike();
        // The swing landed; the arm follows through before the shield can come back up.
        this.npc.getGuard().holdDown(AncientEmpiresConfig.shieldFollowThroughTicks);
        this.windowOpen = false;
        this.windupRemaining = 0;
        this.animation = null;
        this.data().set(NpcEntity.DATA_ATTACK_WINDOW, false);
        this.data().set(NpcEntity.DATA_ATTACK_ANIMATION, "");
    }

    /**
     * Stops the swing and the animation that was started for it. A finished
     * wind-up uses {@link #close()} instead, so the follow-through can keep
     * playing for the rest of the cooldown.
     */
    public void cancel()
    {
        this.close();
        this.clearPresentation();
    }

    private void clearPresentation()
    {
        if (this.data().get(NpcEntity.DATA_PRESENTATION_ID).isEmpty() && this.data().get(NpcEntity.DATA_PRESENTATION_SERIAL) == 0)
        {
            return;
        }
        this.data().set(NpcEntity.DATA_PRESENTATION_ID, "");
        this.data().set(NpcEntity.DATA_PRESENTATION_LENGTH, 0);
        this.data().set(NpcEntity.DATA_PRESENTATION_UPSWING, 0.0F);
        this.data().set(NpcEntity.DATA_PRESENTATION_WINDUP, 0);
        this.data().set(NpcEntity.DATA_ATTACK_OFFHAND, false);
        this.data().set(NpcEntity.DATA_PRESENTATION_SERIAL, this.data().get(NpcEntity.DATA_PRESENTATION_SERIAL) + 1);
    }

    public boolean windowOpen()
    {
        if (this.npc.level().isClientSide)
        {
            return this.data().get(NpcEntity.DATA_ATTACK_WINDOW);
        }
        return this.windowOpen;
    }

    public int windupRemaining()
    {
        return this.windupRemaining;
    }

    public int windowSerial()
    {
        return this.windowSerial;
    }

    /**
     * Animation id from the swing that is currently winding up. Empty when the
     * NPC is not in an attack window. The client copy reads the synced value.
     */
    @Nullable
    public String animation()
    {
        if (this.npc.level().isClientSide)
        {
            String synced = this.data().get(NpcEntity.DATA_ATTACK_ANIMATION);
            return synced.isEmpty() ? null : synced;
        }
        return this.animation;
    }

    /**
     * Animation started when the attack window opened. It stays set through the
     * recovery so the client can finish the same clip Better Combat would play
     * for the full cooldown. Empty after a cancelled swing.
     */
    public String presentationId()
    {
        return this.data().get(NpcEntity.DATA_PRESENTATION_ID);
    }

    public int presentationLength()
    {
        return this.data().get(NpcEntity.DATA_PRESENTATION_LENGTH);
    }

    public float presentationUpswing()
    {
        return this.data().get(NpcEntity.DATA_PRESENTATION_UPSWING);
    }

    /**
     * Server wind-up ticks of the current swing. The client times the clip so
     * its hit frame lands on this tick, even when NPC wind-ups are scaled.
     */
    public int presentationWindup()
    {
        return this.data().get(NpcEntity.DATA_PRESENTATION_WINDUP);
    }

    public int presentationSerial()
    {
        return this.data().get(NpcEntity.DATA_PRESENTATION_SERIAL);
    }

    /**
     * True when the current presentation is the off-hand beat of an empty-hand
     * fist combo. A held weapon never sets this. It stays set through recovery
     * and clears only when the presentation itself is cleared.
     */
    public boolean offHand()
    {
        return this.data().get(NpcEntity.DATA_ATTACK_OFFHAND);
    }
}
