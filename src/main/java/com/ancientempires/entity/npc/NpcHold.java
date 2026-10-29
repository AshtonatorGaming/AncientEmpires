package com.ancientempires.entity.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Hold position, the archer sentry: an owner's command that keeps an archer
 * on its post ({@link NpcHoldPositionGoal}) within {@link #LEASH} blocks; a
 * sword or an empty hand ignores the flag (melee NPCs get no hold). The flag
 * is synced (the client only needs that); the post and its dimension are
 * server-side, taken where the NPC stands when it is first needed. Saved as
 * {@code HoldPosition}, {@code HoldPos}, {@code HoldDim}, and packed.
 */
public final class NpcHold
{
    /** Blocks an archer may stand from its post before walking back. */
    public static final double LEASH = 4.0D;
    private static final String HOLDING_KEY = "HoldPosition";
    private static final String HOLD_KEY = "HoldPos";
    private static final String HOLD_DIM_KEY = "HoldDim";

    private final NpcEntity npc;
    @Nullable
    private BlockPos anchor;
    private String dimension = "";

    NpcHold(NpcEntity npc)
    {
        this.npc = npc;
    }

    public boolean holding()
    {
        return this.npc.getEntityData().get(NpcEntity.DATA_HOLDING);
    }

    public void setHolding(boolean holding)
    {
        this.npc.getEntityData().set(NpcEntity.DATA_HOLDING, holding);
    }

    /** Hold is the archer sentry. A sword or an empty hand ignores the flag. */
    public boolean archerHolding()
    {
        return this.npc.isHoldingBow() && this.holding();
    }

    @Nullable
    public BlockPos anchor()
    {
        return this.anchor;
    }

    /** Takes the post where the NPC stands now. */
    public void capture()
    {
        this.anchor = this.npc.blockPosition();
        this.dimension = this.npc.level().dimension().location().toString();
    }

    public void clear()
    {
        this.anchor = null;
        this.dimension = "";
    }

    /** Whether a holding NPC has strayed past {@link #LEASH} from its post. */
    public boolean outside()
    {
        if (!this.holding())
        {
            return false;
        }
        this.ensureAnchor();
        BlockPos post = this.anchor;
        if (post == null)
        {
            return false;
        }
        return this.npc.distanceToSqr(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D) > LEASH * LEASH;
    }

    public void moveToward(double speed)
    {
        this.ensureAnchor();
        BlockPos post = this.anchor;
        if (post != null)
        {
            this.npc.getNavigation().moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, speed);
        }
    }

    /** A post from another dimension (or none yet) is taken afresh where the NPC is. */
    private void ensureAnchor()
    {
        String here = this.npc.level().dimension().location().toString();
        if (this.anchor == null || this.dimension.isEmpty() || !this.dimension.equals(here))
        {
            this.capture();
        }
    }

    void save(CompoundTag tag)
    {
        tag.putBoolean(HOLDING_KEY, this.holding());
        if (this.holding() && this.anchor != null)
        {
            tag.putLong(HOLD_KEY, this.anchor.asLong());
            tag.putString(HOLD_DIM_KEY, this.dimension);
        }
    }

    void load(CompoundTag tag)
    {
        this.setHolding(tag.getBoolean(HOLDING_KEY));
        if (!this.holding())
        {
            this.clear();
            return;
        }
        if (tag.contains(HOLD_KEY))
        {
            this.anchor = BlockPos.of(tag.getLong(HOLD_KEY));
            this.dimension = tag.getString(HOLD_DIM_KEY);
        }
        else
        {
            this.capture();
        }
    }
}
