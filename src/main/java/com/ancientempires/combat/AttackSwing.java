package com.ancientempires.combat;

import org.jetbrains.annotations.Nullable;

/**
 * One committed swing. The melee goal winds up for {@code windupTicks}, applies
 * the hit, then waits {@code recoveryTicks} before the next swing can start.
 * {@code animationUpswing} is Better Combat's raw upswing fraction, before its
 * config multiplier. The animation player applies that multiplier itself.
 */
public record AttackSwing(
        double range,
        int windupTicks,
        int recoveryTicks,
        float damageMultiplier,
        double angleDegrees,
        @Nullable String animationId,
        float animationUpswing,
        @Nullable String swingSoundId,
        float swingVolume,
        float swingPitch,
        float swingPitchRandomness,
        boolean unarmed,
        boolean offHand,
        String source
)
{
    public AttackSwing
    {
        if (range < 0.5D)
        {
            range = 0.5D;
        }
        if (windupTicks < 1)
        {
            windupTicks = 1;
        }
        if (recoveryTicks < 0)
        {
            recoveryTicks = 0;
        }
        if (damageMultiplier <= 0.0F)
        {
            damageMultiplier = 1.0F;
        }
        if (angleDegrees < 0.0D)
        {
            angleDegrees = 0.0D;
        }
        if (animationUpswing < 0.0F)
        {
            animationUpswing = 0.0F;
        }
        else if (animationUpswing > 1.0F)
        {
            animationUpswing = 1.0F;
        }
        if (swingVolume <= 0.0F)
        {
            swingVolume = 1.0F;
        }
        if (swingPitch <= 0.0F)
        {
            swingPitch = 1.0F;
        }
        if (source == null || source.isEmpty())
        {
            source = "internal";
        }
    }

    public int cooldownTicks()
    {
        return this.windupTicks + this.recoveryTicks;
    }
}
