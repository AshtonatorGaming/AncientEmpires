package com.ancientempires.entity.npc;

import com.ancientempires.block.WorksiteBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

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
    private static final int NO_SITE_RETRY = 600;
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
    /** The last place walked toward, so reaching a new waypoint resets the give-up count. */
    @Nullable
    private BlockPos lastWaypoint;
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

    /** Whether this trade's tool is in hand, for {@link NpcJobGoal}. */
    public final boolean isTrade(ItemStack stack)
    {
        return this.isTool(stack);
    }

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
        return AncientEmpiresConfig.workActionTicks;
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
        BlockState state = this.npc.level().getBlockState(pos);
        float hardness = state.getDestroySpeed(this.npc.level(), pos);
        float speed = Math.max(1.0F, this.npc.getMainHandItem().getDestroySpeed(state));
        int ticks = hardness <= 0.0F ? 0 : (int) Math.ceil(hardness * 30.0F / speed);
        return Math.max(AncientEmpiresConfig.workActionTicks, ticks);
    }

    /**
     * Where to walk next on the way to {@code walk}; {@code walk} itself by default. A
     * miner breaks a long way through its quarry into stair steps.
     */
    protected BlockPos waypoint(BlockPos walk)
    {
        return walk;
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
        if (this.site == null)
        {
            // No worksite of this trade near home: the chunk search is not repeated for a while.
            this.nextCheck = this.npc.tickCount + NO_SITE_RETRY + (this.npc.getId() & 63);
            return false;
        }
        return this.pickJob();
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
                && !this.npc.isPassenger() && !this.npc.getNeeds().hungry()
                && this.npc.level().isDay() && !this.npc.level().isRainingAt(this.npc.blockPosition().above());
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
            // Arrived at a waypoint: plan the next leg now rather than standing out the repath wait.
            boolean arrived = animal == null && this.lastWaypoint != null && this.npc.getNavigation().isDone();
            if (--this.repath <= 0 || arrived)
            {
                this.repath = animal != null ? 10 : 20;
                if (animal != null)
                {
                    this.npc.getNavigation().moveTo(animal, AncientEmpiresConfig.workWalkSpeed);
                }
                else
                {
                    BlockPos walk = this.waypoint(this.kind < 0 ? at : this.walkTarget(this.kind, at));
                    if (arrived && walk.equals(this.lastWaypoint))
                    {
                        // Still the same leg (it could not get closer): wait out the repath as usual.
                        return;
                    }
                    if (!walk.equals(this.lastWaypoint))
                    {
                        // A new step of the way is progress: a long climb is not given up on.
                        this.lastWaypoint = walk;
                        this.jobTicks = 0;
                    }
                    this.npc.getNavigation().moveTo(walk.getX() + 0.5D, walk.getY(), walk.getZ() + 0.5D, AncientEmpiresConfig.workWalkSpeed);
                }
            }
            return;
        }
        this.npc.getNavigation().stop();
        boolean digging = this.kind >= 0 && this.digs(this.kind);
        if (this.actionTicks == 0)
        {
            int length = this.kind < 0 ? AncientEmpiresConfig.workActionTicks : this.actionTicks(this.kind, at);
            // Skilled hands are quicker (leveling.workSpeedPerLevel).
            this.actionLength = Math.max(1, (int) Math.round(length / this.npc.getWorkSpeed()));
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
            case UNLOAD -> this.unload(this.site);
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
        AABB reach = new AABB(spot).inflate(range);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, reach,
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
        return this.tripToFetch(this::isSupply);
    }

    /** The trip to fetch supplies, only when the store has what is needed (seeds, not just bone meal). */
    protected final boolean tripToFetch(Predicate<ItemStack> needed)
    {
        if (this.site != null && this.storeHas(this.site.store(), needed))
        {
            return this.assign(FETCH, this.site.getBlockPos());
        }
        return false;
    }

    private void clearTarget()
    {
        this.lastWaypoint = null;
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

    /**
     * Puts the yield in the worksite (supply slots first for supplies), keeping
     * {@code work.supplyReserve} of each supply. Gathered gear (a fished bow, a
     * saddle) goes too; the worker's own gear stays.
     */
    private void unload(WorksiteBlockEntity site)
    {
        NpcInventory inventory = this.npc.getInventory();
        Map<Item, Integer> kept = new HashMap<>();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            boolean gathered = isGathered(stack);
            if (stack.isEmpty() || (!isGoods(stack) && !gathered))
            {
                continue;
            }
            ItemStack moving = stack.copy();
            if (gathered)
            {
                moving.removeTagKey(GATHERED_KEY);
            }
            else if (this.isSupply(stack))
            {
                int already = kept.getOrDefault(stack.getItem(), 0);
                int keep = Math.min(stack.getCount(), Math.max(0, AncientEmpiresConfig.workSupplyReserve - already));
                kept.put(stack.getItem(), already + keep);
                moving.shrink(keep);
                if (moving.isEmpty())
                {
                    continue;
                }
            }
            int moved = moving.getCount();
            ItemStack rest = site.deposit(moving);
            stack.shrink(moved - rest.getCount());
            inventory.setItem(slot, stack);
            if (!rest.isEmpty())
            {
                // The store is full: keep the rest and stop coming back for a while.
                this.storeFullUntil = this.npc.tickCount + STORE_FULL_WAIT;
            }
        }
        if (this.backpackFull())
        {
            // Nothing could go (all gear, or the store is full): do not walk straight back.
            this.storeFullUntil = this.npc.tickCount + STORE_FULL_WAIT;
        }
        this.npc.level().playSound(null, this.npc.blockPosition(), SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, 1.2F);
    }

    /** Takes supplies from the store, up to {@code work.supplyReserve} of each. */
    private void fetch(Container store)
    {
        Map<Item, Integer> carried = new HashMap<>();
        NpcInventory inventory = this.npc.getInventory();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (this.isSupply(stack))
            {
                carried.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        for (int slot = 0; slot < store.getContainerSize(); slot++)
        {
            ItemStack stack = store.getItem(slot);
            if (stack.isEmpty() || !this.isSupply(stack))
            {
                continue;
            }
            int wanted = AncientEmpiresConfig.workSupplyReserve - carried.getOrDefault(stack.getItem(), 0);
            if (wanted <= 0)
            {
                continue;
            }
            ItemStack taken = stack.split(Math.min(wanted, stack.getCount()));
            ItemStack rest = this.pocket(taken);
            carried.merge(taken.getItem(), taken.getCount() - rest.getCount(), Integer::sum);
            stack.grow(rest.getCount());
            store.setItem(slot, stack);
            if (!rest.isEmpty())
            {
                break;
            }
        }
        this.npc.level().playSound(null, this.npc.blockPosition(), SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 1.2F);
    }

    /** Marks gear a worker gathered (a fished bow or saddle) so it is unloaded, not kept as its own. */
    private static final String GATHERED_KEY = "AeGathered";

    private static boolean isGathered(ItemStack stack)
    {
        return !stack.isEmpty() && stack.getTag() != null && stack.getTag().getBoolean(GATHERED_KEY);
    }

    /**
     * Adds gathered goods to the backpack, filling matching stacks first.
     * Returns what did not fit. Gathered gear is marked to be unloaded.
     */
    protected final ItemStack pocket(ItemStack stack)
    {
        NpcInventory inventory = this.npc.getInventory();
        ItemStack rest = stack.copy();
        if (!isGoods(rest) && !rest.isEmpty())
        {
            rest.getOrCreateTag().putBoolean(GATHERED_KEY, true);
        }
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

    /**
     * Time to unload: at most one empty backpack slot left. One is kept free
     * because a single action can bring several new kinds at once (a catch, a
     * mined ore's drops), and what does not fit would fall on the ground.
     */
    protected final boolean backpackFull()
    {
        NpcInventory inventory = this.npc.getInventory();
        int empty = 0;
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            if (inventory.getItem(slot).isEmpty())
            {
                empty++;
            }
        }
        return empty <= 1;
    }

    /** Carries anything to unload: yield, or supplies beyond the reserve. */
    protected final boolean carriesGoods()
    {
        NpcInventory inventory = this.npc.getInventory();
        Map<Item, Integer> supplies = new HashMap<>();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (isGathered(stack))
            {
                return true;
            }
            if (stack.isEmpty() || !isGoods(stack))
            {
                continue;
            }
            if (!this.isSupply(stack))
            {
                return true;
            }
            if (supplies.merge(stack.getItem(), stack.getCount(), Integer::sum) > AncientEmpiresConfig.workSupplyReserve)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Wears the main-hand tool by one, at {@code work.toolWear}'s chance per
     * action: a worker swings all day, so a player's rate eats tools fast.
     */
    protected final void wearTool()
    {
        // Every trade wears its tool once per action, so this is where work earns XP.
        this.npc.addXp(AncientEmpiresConfig.levelXpPerWork);
        double wear = AncientEmpiresConfig.workToolWear * this.npc.getToolWearScale();
        if (wear > 0.0D && this.npc.getRandom().nextDouble() < wear)
        {
            this.npc.getMainHandItem().hurtAndBreak(1, this.npc, npc -> npc.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }
    }

    /** The backpack slot of the first item that matches, or -1. */
    protected final int slotOf(Predicate<ItemStack> wanted)
    {
        NpcInventory inventory = this.npc.getInventory();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            if (!inventory.getItem(slot).isEmpty() && wanted.test(inventory.getItem(slot)))
            {
                return slot;
            }
        }
        return -1;
    }

    protected static boolean isBoneMeal(ItemStack stack)
    {
        return stack.is(Items.BONE_MEAL);
    }

    /** One dose of carried bone meal on a fresh planting, as a player would give it. */
    protected final void boneMeal(ServerLevel level, BlockPos pos)
    {
        int slot = this.slotOf(NpcWorkGoal::isBoneMeal);
        if (slot < 0)
        {
            return;
        }
        ItemStack meal = this.npc.getInventory().getItem(slot);
        if (BoneMealItem.growCrop(meal, level, pos))
        {
            // The same green sparks a player's bone meal makes.
            level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 0);
            this.npc.getInventory().setItem(slot, meal);
        }
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

    private boolean storeHas(Container store, Predicate<ItemStack> needed)
    {
        for (int slot = 0; slot < store.getContainerSize(); slot++)
        {
            if (needed.test(store.getItem(slot)))
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
        BlockPos known = this.npc.getNeeds().worksite();
        if (known != null)
        {
            BlockEntity entity = this.npc.level().getBlockEntity(known);
            if (this.siteType.isInstance(entity) && this.ownedBySame(this.siteType.cast(entity)))
            {
                return this.siteType.cast(entity);
            }
        }
        BlockPos center = this.npc.getHome() != null ? this.npc.getHome() : this.npc.blockPosition();
        int range = AncientEmpiresConfig.workSearchRange;
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
            this.npc.getNeeds().setWorksite(best.getBlockPos());
        }
        return best;
    }

    private boolean ownedBySame(WorksiteBlockEntity site)
    {
        return !site.isRemoved() && site.owner() != null && site.owner().equals(this.npc.getOwner());
    }
}
