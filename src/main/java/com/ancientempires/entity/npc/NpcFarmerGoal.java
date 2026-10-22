package com.ancientempires.entity.npc;

import com.ancientempires.block.CropFarmBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.PlantType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * A farmer at work, by hand. A Worker-path NPC with a hoe in its main hand
 * finds its owner's nearest crop farm and, by day and in dry weather, walks to
 * one tile at a time: it harvests ripe crops into its backpack, plants seeds
 * it carries, and tills bare soil, swinging its hoe at each. When its backpack
 * fills, or the work runs out, it walks the harvest back to the farm block and
 * unloads it; when it has no seeds and the farm does, it fetches some. Nothing
 * is moved that the farmer did not carry.
 */
public final class NpcFarmerGoal extends Goal
{
    private static final int CHECK_INTERVAL = 40;
    private static final double REACH_SQR = 2.4D * 2.4D;
    private static final int GIVE_UP_TICKS = 200;
    private static final int STORE_FULL_WAIT = 600;

    private enum Job
    {
        HARVEST, PLANT, TILL, UNLOAD, FETCH
    }

    private final NpcEntity npc;
    @Nullable
    private CropFarmBlockEntity farm;
    @Nullable
    private Job job;
    @Nullable
    private BlockPos target;
    private int jobTicks;
    private int actionDelay;
    private int repath;
    private int nextCheck;
    /** The farm's store was full at the last unload; no trips back until this tick. */
    private int storeFullUntil;

    public NpcFarmerGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
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
        this.farm = this.findFarm();
        return this.farm != null && this.pickJob();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.job != null && this.farm != null && !this.farm.isRemoved() && this.mayWork();
    }

    /** A farmer by trade (worker path, hoe in hand), at liberty, by day, in dry weather. */
    private boolean mayWork()
    {
        return this.npc.getNpcPath() == NpcPath.WORKER && this.npc.getMainHandItem().getItem() instanceof HoeItem
                && this.npc.isOwnedNpc() && !this.npc.isFollowing() && this.npc.getTarget() == null
                && !this.npc.isPassenger() && this.npc.level().isDay() && !this.npc.level().isRaining();
    }

    @Override
    public void start()
    {
        this.jobTicks = 0;
        this.actionDelay = 0;
        this.repath = 0;
    }

    @Override
    public void stop()
    {
        this.dropTarget();
        this.job = null;
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
        if (this.job == null || this.target == null || this.farm == null)
        {
            return;
        }
        this.npc.getLookControl().setLookAt(this.target.getX() + 0.5D, this.target.getY() + 0.5D, this.target.getZ() + 0.5D);
        if (++this.jobTicks > GIVE_UP_TICKS)
        {
            // Cannot reach it. Leave the tile for the next scan and do something else.
            this.dropTarget();
            this.nextJob();
            return;
        }
        if (this.npc.distanceToSqr(this.target.getX() + 0.5D, this.target.getY() + 0.5D, this.target.getZ() + 0.5D) > REACH_SQR)
        {
            if (--this.repath <= 0)
            {
                this.repath = 20;
                this.npc.getNavigation().moveTo(this.target.getX() + 0.5D, this.target.getY(), this.target.getZ() + 0.5D, AncientEmpiresConfig.farmWalkSpeed);
            }
            return;
        }
        this.npc.getNavigation().stop();
        if (this.actionDelay == 0)
        {
            // The swing starts the action; the effect lands when the delay runs out.
            this.npc.swing(InteractionHand.MAIN_HAND);
        }
        if (++this.actionDelay < AncientEmpiresConfig.farmActionTicks)
        {
            return;
        }
        this.act((ServerLevel) this.npc.level(), this.target);
        this.dropTarget();
        this.nextJob();
    }

    private void nextJob()
    {
        this.jobTicks = 0;
        this.actionDelay = 0;
        this.repath = 0;
        if (!this.pickJob())
        {
            this.job = null;
        }
    }

    /**
     * Harvest first, then plant (fetching seeds if needed), then till. A full
     * backpack, or nothing left to do while carrying a harvest, means a trip home.
     */
    private boolean pickJob()
    {
        CropFarmBlockEntity site = this.farm;
        if (site == null)
        {
            return false;
        }
        int id = this.npc.getId();
        BlockPos at = this.npc.blockPosition();
        boolean full = this.backpackFull();
        if (!full)
        {
            BlockPos tile = site.claim(CropFarmBlockEntity.Task.HARVEST, id, at);
            if (tile != null)
            {
                return this.assign(Job.HARVEST, tile);
            }
            if (site.hasWork(CropFarmBlockEntity.Task.PLANT))
            {
                if (this.seedSlot() >= 0)
                {
                    tile = site.claim(CropFarmBlockEntity.Task.PLANT, id, at);
                    if (tile != null)
                    {
                        return this.assign(Job.PLANT, tile);
                    }
                }
                else if (hasSeeds(site.store()))
                {
                    return this.assign(Job.FETCH, site.getBlockPos());
                }
            }
            tile = site.claim(CropFarmBlockEntity.Task.TILL, id, at);
            if (tile != null)
            {
                return this.assign(Job.TILL, tile);
            }
        }
        if ((full || this.carriesHarvest()) && this.npc.tickCount >= this.storeFullUntil)
        {
            return this.assign(Job.UNLOAD, site.getBlockPos());
        }
        return false;
    }

    private boolean assign(Job job, BlockPos tile)
    {
        this.job = job;
        this.target = tile;
        return true;
    }

    private void dropTarget()
    {
        if (this.target != null && this.farm != null && this.job != null && this.job != Job.UNLOAD && this.job != Job.FETCH)
        {
            this.farm.release(this.target);
        }
        this.target = null;
    }

    private void act(ServerLevel level, BlockPos pos)
    {
        CropFarmBlockEntity site = this.farm;
        if (site == null || this.job == null)
        {
            return;
        }
        switch (this.job)
        {
            case HARVEST -> this.harvest(level, pos);
            case PLANT -> this.plant(level, pos);
            case TILL -> this.till(level, pos);
            case UNLOAD -> this.unload(site.store());
            case FETCH -> this.fetch(site.store());
        }
    }

    /** Breaks a ripe crop (or a pumpkin or melon) and pockets the drops; what does not fit falls. */
    private void harvest(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (!CropFarmBlockEntity.isRipe(state) && !state.is(Blocks.PUMPKIN) && !state.is(Blocks.MELON))
        {
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), this.npc, this.npc.getMainHandItem());
        level.destroyBlock(pos, false, this.npc);
        for (ItemStack drop : drops)
        {
            ItemStack rest = this.pocket(drop);
            if (!rest.isEmpty())
            {
                Block.popResource(level, pos, rest);
            }
        }
    }

    /** Sets a carried seed on bare farmland. */
    private void plant(ServerLevel level, BlockPos pos)
    {
        int slot = this.seedSlot();
        if (slot < 0 || !level.getBlockState(pos).isAir())
        {
            return;
        }
        ItemStack seeds = this.npc.getInventory().getItem(slot);
        BlockState crop = ((BlockItem) seeds.getItem()).getBlock().defaultBlockState();
        if (!crop.canSurvive(level, pos))
        {
            return;
        }
        level.setBlock(pos, crop, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.npc, crop));
        level.playSound(null, pos, crop.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        seeds.shrink(1);
        this.npc.getInventory().setItem(slot, seeds);
    }

    /** Turns grass or dirt into farmland, as a hoe would. */
    private void till(ServerLevel level, BlockPos pos)
    {
        BlockState soil = level.getBlockState(pos);
        if (!level.getBlockState(pos.above()).isAir()
                || !(soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.DIRT_PATH)))
        {
            return;
        }
        BlockState farmland = Blocks.FARMLAND.defaultBlockState();
        level.setBlock(pos, farmland, Block.UPDATE_ALL_IMMEDIATE);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(this.npc, farmland));
        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.npc.getMainHandItem().hurtAndBreak(1, this.npc, npc -> npc.broadcastBreakEvent(InteractionHand.MAIN_HAND));
    }

    /** Puts the harvest in the farm's store, keeping {@code work.seedReserve} seeds for planting. */
    private void unload(Container store)
    {
        NpcInventory inventory = this.npc.getInventory();
        int seedsKept = 0;
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !isHarvest(stack))
            {
                continue;
            }
            ItemStack moving = stack.copy();
            if (isSeed(stack) && seedsKept < AncientEmpiresConfig.farmSeedReserve)
            {
                int keep = Math.min(stack.getCount(), AncientEmpiresConfig.farmSeedReserve - seedsKept);
                seedsKept += keep;
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

    /** Takes seeds from the store, up to {@code work.seedReserve}. */
    private void fetch(Container store)
    {
        int wanted = AncientEmpiresConfig.farmSeedReserve;
        for (int slot = 0; slot < store.getContainerSize() && wanted > 0; slot++)
        {
            ItemStack stack = store.getItem(slot);
            if (!isSeed(stack))
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
    private ItemStack pocket(ItemStack stack)
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

    private boolean backpackFull()
    {
        NpcInventory inventory = this.npc.getInventory();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || (isHarvest(stack) && stack.getCount() < stack.getMaxStackSize()))
            {
                return false;
            }
        }
        return true;
    }

    /** Carries anything to unload: produce, or seeds beyond the reserve. */
    private boolean carriesHarvest()
    {
        NpcInventory inventory = this.npc.getInventory();
        int seeds = 0;
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !isHarvest(stack))
            {
                continue;
            }
            if (!isSeed(stack))
            {
                return true;
            }
            seeds += stack.getCount();
        }
        return seeds > AncientEmpiresConfig.farmSeedReserve;
    }

    private int seedSlot()
    {
        NpcInventory inventory = this.npc.getInventory();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            if (isSeed(inventory.getItem(slot)))
            {
                return slot;
            }
        }
        return -1;
    }

    private static boolean hasSeeds(Container store)
    {
        for (int slot = 0; slot < store.getContainerSize(); slot++)
        {
            if (isSeed(store.getItem(slot)))
            {
                return true;
            }
        }
        return false;
    }

    /** Anything that plants as a crop: wheat, carrot, potato, beetroot, pumpkin and melon seeds, modded crops. */
    static boolean isSeed(ItemStack stack)
    {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof IPlantable plant))
        {
            return false;
        }
        try
        {
            // Vanilla answers from the block alone; a modded plant that reads the world is not a seed here.
            return plant.getPlantType(null, BlockPos.ZERO) == PlantType.CROP;
        }
        catch (RuntimeException exception)
        {
            return false;
        }
    }

    /** What a farmer carries back: farm goods, not its gear (tools, weapons, armor). */
    private static boolean isHarvest(ItemStack stack)
    {
        return !stack.isDamageableItem() && stack.getMaxStackSize() > 1;
    }

    /**
     * This NPC's farm: the one it worked last if still there, else its owner's
     * nearest crop farm within {@code work.searchRange} of its home. Looks only
     * in loaded chunks, at block entities, not blocks.
     */
    @Nullable
    private CropFarmBlockEntity findFarm()
    {
        BlockPos known = this.npc.getWorksite();
        if (known != null && this.npc.level().getBlockEntity(known) instanceof CropFarmBlockEntity farm && this.ownedBySame(farm))
        {
            return farm;
        }
        BlockPos center = this.npc.getHome() != null ? this.npc.getHome() : this.npc.blockPosition();
        int range = AncientEmpiresConfig.farmSearchRange;
        CropFarmBlockEntity best = null;
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
                    if (entity instanceof CropFarmBlockEntity farm && this.ownedBySame(farm))
                    {
                        double distance = farm.getBlockPos().distSqr(center);
                        if (distance <= bestDistance)
                        {
                            bestDistance = distance;
                            best = farm;
                        }
                    }
                }
            }
        }
        this.npc.setWorksite(best == null ? null : best.getBlockPos());
        return best;
    }

    private boolean ownedBySame(CropFarmBlockEntity farm)
    {
        return !farm.isRemoved() && farm.owner() != null && farm.owner().equals(this.npc.getOwner());
    }
}
