package com.ancientempires.combat;

import org.jetbrains.annotations.Nullable;

/**
 * One committed swing. The melee goal winds up for {@code windupTicks}, applies
 * the hit, then waits {@code recoveryTicks} before the next swing can start.
 */
public record AttackSwing(
        double range,
        int windupTicks,
        int recoveryTicks,
        float damageMultiplier,
        double angleDegrees,
        @Nullable String animationId,
        @Nullable String swingSoundId,
        float swingVolume,
        float swingPitch,
        float swingPitchRandomness,
        boolean unarmed,
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
