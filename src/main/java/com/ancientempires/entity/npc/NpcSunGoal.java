package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Ancient Warfare's vampires avoided the sun: a faction with {@code sunlight:
 * avoid} keeps to shade by day whenever it is not fighting, and one with
 * {@code burn} (the undead) heads for shade once the sun has set it alight,
 * as zombies do. The burning itself is in {@link NpcEntity}. Looked for every
 * half second, and only by such factions in the open by day. Their burning-panic
 * run for water stands aside for it by day.
 */
final class NpcSunGoal extends Goal
{
    private static final int CHECK_INTERVAL = 10;
    private static final int SHADE_TRIES = 24;

    private final NpcEntity npc;
    private int nextCheck;
    @Nullable
    private Vec3 shade;

    NpcSunGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        if (!AncientEmpiresConfig.sunlightEnabled || this.npc.tickCount < this.nextCheck)
        {
            return false;
        }
        this.nextCheck = this.npc.tickCount + CHECK_INTERVAL;
        NpcFactions.Sunlight sunlight = NpcFactions.sunlight(this.npc.getFaction());
        if (sunlight == NpcFactions.Sunlight.NONE || !this.npc.level().isDay() || !this.inSun())
        {
            return false;
        }
        boolean wants = this.npc.isOnFire() || sunlight == NpcFactions.Sunlight.AVOID && this.npc.getTarget() == null;
        if (!wants)
        {
            return false;
        }
        this.shade = this.findShade();
        return this.shade != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return !this.npc.getNavigation().isDone();
    }

    @Override
    public void start()
    {
        if (this.shade != null)
        {
            this.npc.getNavigation().moveTo(this.shade.x, this.shade.y, this.shade.z, AncientEmpiresConfig.approachSpeed);
        }
    }

    private boolean inSun()
    {
        return this.npc.level().canSeeSky(BlockPos.containing(this.npc.getX(), this.npc.getEyeY(), this.npc.getZ()));
    }

    /** A safe spot within ten blocks with a roof over it. */
    @Nullable
    private Vec3 findShade()
    {
        if (this.npc.hasRestriction())
        {
            // The home leash would keep the search to sunny ground near home; lift it for the search.
            BlockPos center = this.npc.getRestrictCenter();
            int radius = (int) this.npc.getRestrictRadius();
            this.npc.clearRestriction();
            try
            {
                return this.searchShade();
            }
            finally
            {
                this.npc.restrictTo(center, radius);
            }
        }
        return this.searchShade();
    }

    @Nullable
    private Vec3 searchShade()
    {
        for (int attempt = 0; attempt < SHADE_TRIES; attempt++)
        {
            Vec3 spot = NpcSafety.pos(this.npc, 10, 4);
            if (spot != null && !this.npc.level().canSeeSky(BlockPos.containing(spot).above()))
            {
                return spot;
            }
        }
        return null;
    }
}
