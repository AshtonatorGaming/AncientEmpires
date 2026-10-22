package com.ancientempires.entity.npc;

import com.ancientempires.block.WorksiteBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A worker at its worksite, by hand. Subclasses say which tool marks the
 * trade, which worksite it serves, and what each job does; this class walks
 * the NPC to one tile at a time, swings, waits out the action (showing break
 * progress for digging), carries what it gathers in its backpack, walks it back
 * to the worksite to unload, and fetches supplies there. Work is by day, in
 * dry weather, when the NPC is at liberty; threats, orders, and following come
 * first. Nothing reaches the store that the worker did not carry.
 */
public abstract class NpcWorkGoal<T extends WorksiteBlockEntity> extends Goal
{
    /** Job kinds this class handles itself; subclasses use 0 and up. */
    protected static final int UNLOAD = -1;
    protected static final int FETCH = -2;

    private static final int CHECK_INTERVAL = 40;
    private static final int GIVE_UP_TICKS = 240;
    private static final int STORE_FULL_WAIT = 600;

    protected final NpcEntity npc;
    private final Class<T> siteType;
    @Nullable
    protected T site;
    private int kind;
    @Nullable
    private BlockPos target;
    /** The creature a job is on, followed as it moves; null for block jobs. */
    @Nullable
    private Entity entityTarget;
    private int jobTicks;
    private int actionTicks;
    private int actionLength;
    /** The block showing break progress, if the job digs. */
    @Nullable
    private BlockPos crack;
    private int repath;
    private int nextCheck;
    private int storeFullUntil;

    protected NpcWorkGoal(NpcEntity npc, Class<T> siteType)
    {
        this.npc = npc;
        this.siteType = siteType;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** The trade's tool, in the main hand. */
    protected abstract boolean isTool(ItemStack stack);

    /** Picks the next job with {@link #assign}; returns false when there is none. */
    protected abstract boolean pickJob();

    /**
     * Does a job once the worker is in reach and the action time has run.
     * Returns true to go again on the same target (another log of the same tree).
     */
    protected abstract boolean act(ServerLevel level, int kind, BlockPos target);

    /** Supplies the worker keeps on hand and fetches from the store (seeds, saplings); none by default. */
    protected boolean isSupply(ItemStack stack)
    {
        return false;
    }

    /** Ticks from swing to effect. Digging jobs scale with the block's hardness. */
    protected int actionTicks(int kind, BlockPos target)
    {
        return AncientEmpiresConfig.farmActionTicks;
    }

    /** Whether the job breaks the target block, so a crack overlay shows while it works. */
    protected boolean digs(int kind)
    {
        return false;
    }

    /** The block a digging job is breaking right now; the target by default (a woodcutter's is the top log). */
    protected BlockPos digPos(int kind, BlockPos target)
    {
        return target;
    }

    /**
     * Player mining time for a block with the main-hand tool, as a player
     * would take: hardness times 30 over the tool's speed, at least the
     * normal action time. Blocks that cannot be broken take the normal time.
     */
    protected final int digTicks(BlockPos pos)
    {
        net.minecraft.world.level.block.state.BlockState state = this.npc.level().getBlockState(pos);
        float hardness = state.getDestroySpeed(this.npc.level(), pos);
        float speed = Math.max(1.0F, this.npc.getMainHandItem().getDestroySpeed(state));
        int ticks = hardness <= 0.0F ? 0 : (int) Math.ceil(hardness * 30.0F / speed);
        return Math.max(AncientEmpiresConfig.farmActionTicks, ticks);
    }

    /** Where to walk for a job; the target by default. */
    protected BlockPos walkTarget(int kind, BlockPos target)
    {
        return target;
    }

    /** How close, squared, from the NPC's feet to the target's center, counts as in reach. */
    protected double reachSqr(int kind)
    {
        return 2.4D * 2.4D;
    }

    @Override
    public boolean canUse()
    {
        if (this.npc.tickCount < this.nextCheck)
        {
            return false;
        }
        this.nextCheck = this.npc.tickCount + CHECK_INTERVAL + (this.npc.getId() & 15);
        if (!this.mayWork())
        {
            return false;
        }
        this.site = this.findSite();
        return this.site != null && this.pickJob();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.target != null && this.site != null && !this.site.isRemoved() && this.mayWork();
    }

    /** A worker by trade (worker path, tool in hand), at liberty, by day, in dry weather. */
    protected boolean mayWork()
    {
        return this.npc.getNpcPath() == NpcPath.WORKER && this.isTool(this.npc.getMainHandItem())
                && this.npc.isOwnedNpc() && !this.npc.isFollowing() && this.npc.getTarget() == null
                && !this.npc.isPassenger() && !this.npc.isHungry()
                && this.npc.level().isDay() && !this.npc.level().isRaining();
    }

    @Override
    public void start()
    {
        this.jobTicks = 0;
        this.actionTicks = 0;
        this.repath = 0;
    }

    @Override
    public void stop()
    {
        this.clearTarget();
        this.npc.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        Entity animal = this.entityTarget;
        if (animal != null)
        {
            if (!animal.isAlive())
            {
                this.onCreatureGone(this.kind, animal);
                this.clearTarget();
                this.next();
                return;
            }
            // A moving target: follow where it is now.
            this.target = animal.blockPosition();
        }
        BlockPos at = this.target;
        if (at == null || this.site == null)
        {
            return;
        }
        if (animal != null)
        {
            this.npc.getLookControl().setLookAt(animal, 30.0F, 30.0F);
        }
        else
        {
            this.npc.getLookControl().setLookAt(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D);
        }
        if (!this.inReach(this.kind, at, animal))
        {
            if (++this.jobTicks > GIVE_UP_TICKS)
            {
                // Cannot reach it. Leave the tile for the next scan and do something else.
                this.clearTarget();
                this.next();
                return;
            }
            if (this.actionTicks > 0)
            {
                // Out of reach mid-action (the animal moved): start over when close again.
                this.actionTicks = 0;
                this.onActionCancelled(this.kind);
            }
            if (--this.repath <= 0)
            {
                this.repath = animal != null ? 10 : 20;
                if (animal != null)
                {
                    this.npc.getNavigation().moveTo(animal, AncientEmpiresConfig.farmWalkSpeed);
                }
                else
                {
                    BlockPos walk = this.kind < 0 ? at : this.walkTarget(this.kind, at);
                    this.npc.getNavigation().moveTo(walk.getX() + 0.5D, walk.getY(), walk.getZ() + 0.5D, AncientEmpiresConfig.farmWalkSpeed);
                }
            }
            return;
        }
        this.npc.getNavigation().stop();
        boolean digging = this.kind >= 0 && this.digs(this.kind);
        if (this.actionTicks == 0)
        {
            this.actionLength = this.kind < 0 ? AncientEmpiresConfig.farmActionTicks : Math.max(1, this.actionTicks(this.kind, at));
            this.crack = digging ? this.digPos(this.kind, at) : null;
            if (this.kind >= 0)
            {
                this.onActionStart(this.kind);
            }
        }
        if (this.crack != null)
        {
            this.npc.getLookControl().setLookAt(this.crack.getX() + 0.5D, this.crack.getY() + 0.5D, this.crack.getZ() + 0.5D);
        }
        if (this.actionTicks % 8 == 0 && (this.kind < 0 || this.swings(this.kind)))
        {
            // Swing at the start, and again through a long dig, as a player would.
            this.npc.swing(InteractionHand.MAIN_HAND);
        }
        this.actionTicks++;
        if (this.kind >= 0)
        {
            this.onActionTick(this.kind, at, this.actionTicks);
        }
        if (this.crack != null)
        {
            this.npc.level().destroyBlockProgress(this.npc.getId(), this.crack, Math.min(9, this.actionTicks * 10 / this.actionLength));
        }
        if (this.actionTicks < this.actionLength)
        {
            return;
        }
        this.clearCrack();
        ServerLevel level = (ServerLevel) this.npc.level();
        boolean again = false;
        switch (this.kind)
        {
            case UNLOAD -> this.unload(this.site.store());
            case FETCH -> this.fetch(this.site.store());
            default -> again = this.act(level, this.kind, at);
        }
        if (again)
        {
            this.actionTicks = 0;
            this.jobTicks = 0;
            return;
        }
        this.clearTarget();
        this.next();
    }

    private void next()
    {
        this.jobTicks = 0;
        this.actionTicks = 0;
        this.repath = 0;
        this.pickJob();
    }

    protected final boolean assign(int kind, BlockPos target)
    {
        this.kind = kind;
        this.target = target;
        this.entityTarget = null;
        return true;
    }

    /** A job on a moving creature: the worker follows it, and the action lands on it. */
    protected final boolean assignEntity(int kind, Entity creature)
    {
        this.kind = kind;
        this.target = creature.blockPosition();
        this.entityTarget = creature;
        return true;
    }

    /** The creature the current job is on, or null. */
    @Nullable
    protected final Entity entityTarget()
    {
        return this.entityTarget;
    }

    /** Whether the worker can act from where it stands. Distance by default; a hunter also needs a clear shot. */
    protected boolean inReach(int kind, BlockPos target, @Nullable Entity creature)
    {
        if (kind < 0)
        {
            return this.npc.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) <= 2.4D * 2.4D;
        }
        if (creature != null)
        {
            return this.npc.distanceToSqr(creature) <= this.reachSqr(kind);
        }
        return this.npc.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) <= this.reachSqr(kind);
    }

    /** Whether the job swings the main hand; a bow is drawn instead. */
    protected boolean swings(int kind)
    {
        return true;
    }

    /** The action begins (a hunter draws). */
    protected void onActionStart(int kind)
    {
    }

    /** The action was cut short or the job dropped (a hunter lowers the bow). */
    protected void onActionCancelled(int kind)
    {
    }

    /** Each tick of an action, after its start (a fisher's bobbing water). */
    protected void onActionTick(int kind, BlockPos target, int tick)
    {
    }

    /** The creature a job was on died or vanished (a hunter goes to pick up what it dropped). */
    protected void onCreatureGone(int kind, Entity creature)
    {
    }

    /** Picks up the goods lying around a spot, as far as the backpack holds them, with the pickup animation. */
    protected final void collectAt(ServerLevel level, BlockPos spot, double range)
    {
        net.minecraft.world.phys.AABB reach = new net.minecraft.world.phys.AABB(spot).inflate(range);
        for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, reach,
                item -> item.isAlive() && !item.hasPickUpDelay()))
        {
            ItemStack stack = item.getItem();
            if (!isGoods(stack))
            {
                continue;
            }
            int before = stack.getCount();
            ItemStack rest = this.pocket(stack);
            if (rest.getCount() < before)
            {
                this.npc.take(item, before - rest.getCount());
                if (rest.isEmpty())
                {
                    item.discard();
                }
                else
                {
                    item.setItem(rest);
                }
            }
        }
    }

    /** The trip back to the worksite to unload, when the backpack is full or there is nothing else to do. */
    protected final boolean tripToUnload(boolean nothingElse)
    {
        if (this.site != null && this.npc.tickCount >= this.storeFullUntil && (this.backpackFull() || (nothingElse && this.carriesGoods())))
        {
            return this.assign(UNLOAD, this.site.getBlockPos());
        }
        return false;
    }

    protected final boolean tripToFetch()
    {
        if (this.site != null && this.storeHas(this.site.store()))
        {
            return this.assign(FETCH, this.site.getBlockPos());
        }
        return false;
    }

    private void clearTarget()
    {
        if (this.site != null && this.kind >= 0)
        {
            if (this.entityTarget != null)
            {
                this.site.releaseEntity(this.entityTarget.getUUID());
            }
            else if (this.target != null)
            {
                this.site.release(this.target);
            }
        }
        if (this.kind >= 0 && this.target != null)
        {
            this.onActionCancelled(this.kind);
        }
        this.clearCrack();
        this.target = null;
        this.entityTarget = null;
    }

    private void clearCrack()
    {
        if (this.crack != null)
        {
            this.npc.level().destroyBlockProgress(this.npc.getId(), this.crack, -1);
            this.crack = null;
        }
    }

    /** Puts the yield in the store, keeping {@code work.seedReserve} of the supplies. */
    private void unload(Container store)
    {
        NpcInventory inventory = this.npc.getInventory();
        int kept = 0;
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !isGoods(stack))
            {
                continue;
            }
            ItemStack moving = stack.copy();
            if (this.isSupply(stack) && kept < AncientEmpiresConfig.farmSeedReserve)
            {
                int keep = Math.min(stack.getCount(), AncientEmpiresConfig.farmSeedReserve - kept);
                kept += keep;
                moving.shrink(keep);
                if (moving.isEmpty())
                {
                    continue;
                }
            }
            int moved = moving.getCount();
            ItemStack rest = HopperBlockEntity.addItem(null, store, moving, null);
            stack.shrink(moved - rest.getCount());
            inventory.setItem(slot, stack);
            if (!rest.isEmpty())
            {
                // The store is full: keep the rest and stop coming back for a while.
                this.storeFullUntil = this.npc.tickCount + STORE_FULL_WAIT;
            }
        }
        this.npc.level().playSound(null, this.npc.blockPosition(), SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, 1.2F);
    }

    /** Takes supplies from the store, up to {@code work.seedReserve}. */
    private void fetch(Container store)
    {
        int wanted = AncientEmpiresConfig.farmSeedReserve;
        for (int slot = 0; slot < store.getContainerSize() && wanted > 0; slot++)
        {
            ItemStack stack = store.getItem(slot);
            if (stack.isEmpty() || !this.isSupply(stack))
            {
                continue;
            }
            ItemStack taken = stack.split(Math.min(wanted, stack.getCount()));
            wanted -= taken.getCount();
            ItemStack rest = this.pocket(taken);
            if (!rest.isEmpty())
            {
                stack.grow(rest.getCount());
                wanted = 0;
            }
            store.setItem(slot, stack);
        }
        this.npc.level().playSound(null, this.npc.blockPosition(), SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 1.2F);
    }

    /** Adds to the backpack, filling matching stacks first. Returns what did not fit. */
    protected final ItemStack pocket(ItemStack stack)
    {
        NpcInventory inventory = this.npc.getInventory();
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++)
        {
            for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT && !rest.isEmpty(); slot++)
            {
                ItemStack held = inventory.getItem(slot);
                if (pass == 0 && !held.isEmpty() && ItemStack.isSameItemSameTags(held, rest) && held.getCount() < held.getMaxStackSize())
                {
                    int move = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
                    held.grow(move);
                    rest.shrink(move);
                    inventory.setItem(slot, held);
                }
                else if (pass == 1 && held.isEmpty())
                {
                    inventory.setItem(slot, rest.copy());
                    rest = ItemStack.EMPTY;
                }
            }
        }
        return rest;
    }

    protected final boolean backpackFull()
    {
        NpcInventory inventory = this.npc.getInventory();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || (isGoods(stack) && stack.getCount() < stack.getMaxStackSize()))
            {
                return false;
            }
        }
        return true;
    }

    /** Carries anything to unload: yield, or supplies beyond the reserve. */
    protected final boolean carriesGoods()
    {
        NpcInventory inventory = this.npc.getInventory();
        int supplies = 0;
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !isGoods(stack))
            {
                continue;
            }
            if (!this.isSupply(stack))
            {
                return true;
            }
            supplies += stack.getCount();
        }
        return supplies > AncientEmpiresConfig.farmSeedReserve;
    }

    /** The backpack slot of a supply, or -1. */
    protected final int supplySlot()
    {
        NpcInventory inventory = this.npc.getInventory();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            if (this.isSupply(inventory.getItem(slot)))
            {
                return slot;
            }
        }
        return -1;
    }

    private boolean storeHas(Container store)
    {
        for (int slot = 0; slot < store.getContainerSize(); slot++)
        {
            if (this.isSupply(store.getItem(slot)))
            {
                return true;
            }
        }
        return false;
    }

    /** What a worker carries back: goods, not its gear (tools, weapons, armor, anything unstackable). */
    protected static boolean isGoods(ItemStack stack)
    {
        return !stack.isDamageableItem() && stack.getMaxStackSize() > 1;
    }

    /**
     * The worksite this NPC worked last if still there, else its owner's nearest
     * one of this kind within {@code work.searchRange} of its home. Looks only in
     * loaded chunks, at block entities, not blocks.
     */
    @Nullable
    private T findSite()
    {
        BlockPos known = this.npc.getWorksite();
        if (known != null)
        {
            BlockEntity entity = this.npc.level().getBlockEntity(known);
            if (this.siteType.isInstance(entity) && this.ownedBySame(this.siteType.cast(entity)))
            {
                return this.siteType.cast(entity);
            }
        }
        BlockPos center = this.npc.getHome() != null ? this.npc.getHome() : this.npc.blockPosition();
        int range = AncientEmpiresConfig.farmSearchRange;
        T best = null;
        double bestDistance = (double) range * range;
        for (int cx = (center.getX() - range) >> 4; cx <= (center.getX() + range) >> 4; cx++)
        {
            for (int cz = (center.getZ() - range) >> 4; cz <= (center.getZ() + range) >> 4; cz++)
            {
                LevelChunk chunk = this.npc.level().getChunkSource().getChunkNow(cx, cz);
                if (chunk == null)
                {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values())
                {
                    if (this.siteType.isInstance(entity) && this.ownedBySame(this.siteType.cast(entity)))
                    {
                        double distance = entity.getBlockPos().distSqr(center);
                        if (distance <= bestDistance)
                        {
                            bestDistance = distance;
                            best = this.siteType.cast(entity);
                        }
                    }
                }
            }
        }
        if (best != null)
        {
            this.npc.setWorksite(best.getBlockPos());
        }
        return best;
    }

    private boolean ownedBySame(WorksiteBlockEntity site)
    {
        return !site.isRemoved() && site.owner() != null && site.owner().equals(this.npc.getOwner());
    }
}
