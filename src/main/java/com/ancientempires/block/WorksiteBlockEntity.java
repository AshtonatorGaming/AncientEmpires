package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.SimpleContainer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared by every worksite (crop farm, tree farm, quarry, animal pen, hunting
 * lodge, fishing dock, kitchen) and the Town Hall: an owner, a rectangular
 * area near the block (moved and sized in the bounds screen, up to a limit
 * that size upgrades raise, as in Ancient Warfare; the kitchen and Town Hall
 * have none), a double-chest store (plus filtered
 * supply slots) workers unload into and hoppers can reach,
 * and a to-do list the subclass rebuilds in {@link #scan}. The list is rebuilt
 * at most every {@code work.scanTicks}, and only when a worker asks, so an idle
 * worksite costs nothing. A worker claims the tile it walks to, so two workers
 * do not take the same one.
 */
public abstract class WorksiteBlockEntity extends BlockEntity
{
    /** A double chest. Supply slots follow it, so old saves keep their items where they were. */
    public static final int STORE_SIZE = 54;

    /**
     * A group of filtered slots for one kind of supply (seeds, bone meal, feed,
     * fuel...), shown above the store with a ghost of what goes there. Workers
     * unloading put supplies here first, and hoppers can only fill them with it.
     */
    public record Supply(String key, Predicate<ItemStack> accepts, Supplier<ItemStack> icon, int slots)
    {
    }

    private final SimpleContainer store;
    private final LazyOptional<IItemHandler> storeHandler;
    @Nullable
    private UUID owner;
    /** Null until the owner sets one; until then {@link #defaultArea}. */
    @Nullable
    private WorksiteArea area;
    /** 0 without a size upgrade, 1 medium, 2 large. */
    private int sizeTier;
    /** Game time of the last scan; -1 means never, so the next request scans. */
    private long scannedAt = -1L;
    /** Tile to the id of the worker on it, and the game time the claim lapses. */
    private final Map<BlockPos, long[]> claims = new HashMap<>();
    private final Map<UUID, long[]> entityClaims = new HashMap<>();

    protected WorksiteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        super(type, pos, state);
        this.store = new SimpleContainer(STORE_SIZE + supplySlots(this.supplies()))
        {
            @Override
            public void setChanged()
            {
                super.setChanged();
                WorksiteBlockEntity.this.setChanged();
            }

            @Override
            public boolean stillValid(Player player)
            {
                return Container.stillValidBlockEntity(WorksiteBlockEntity.this, player);
            }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack)
            {
                return WorksiteBlockEntity.this.canPlace(slot, stack);
            }
        };
        this.storeHandler = LazyOptional.of(() -> new InvWrapper(this.store));
    }

    /**
     * This site's supply slot groups, in order; none by default. Built from
     * constants only: it is asked during construction.
     */
    public List<Supply> supplies()
    {
        return List.of();
    }

    public static int supplySlots(List<Supply> supplies)
    {
        int count = 0;
        for (Supply supply : supplies)
        {
            count += supply.slots();
        }
        return count;
    }

    /** The supply group a container slot belongs to, or null for a store slot. */
    @Nullable
    public Supply supplyAt(int slot)
    {
        int index = slot - STORE_SIZE;
        for (Supply supply : this.supplies())
        {
            if (index < 0)
            {
                return null;
            }
            if (index < supply.slots())
            {
                return supply;
            }
            index -= supply.slots();
        }
        return null;
    }

    /** Store slots take anything; supply slots only their kind. */
    public boolean canPlace(int slot, ItemStack stack)
    {
        Supply supply = this.supplyAt(slot);
        return supply == null || supply.accepts().test(stack);
    }

    /**
     * Puts a stack away as a worker does: into a matching supply slot first, then
     * the store, topping up stacks before using empty slots. Returns what did not fit.
     */
    public ItemStack deposit(ItemStack stack)
    {
        ItemStack rest = stack.copy();
        int size = this.store.getContainerSize();
        // Supplies first, then the store; in each, matching stacks before empty slots.
        for (int range = 0; range < 2 && !rest.isEmpty(); range++)
        {
            int from = range == 0 ? STORE_SIZE : 0;
            int to = range == 0 ? size : STORE_SIZE;
            for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++)
            {
                for (int slot = from; slot < to && !rest.isEmpty(); slot++)
                {
                    if (!this.canPlace(slot, rest))
                    {
                        continue;
                    }
                    ItemStack held = this.store.getItem(slot);
                    if (pass == 0 && !held.isEmpty() && ItemStack.isSameItemSameTags(held, rest))
                    {
                        int move = Math.min(rest.getCount(), Math.min(held.getMaxStackSize(), this.store.getMaxStackSize()) - held.getCount());
                        if (move > 0)
                        {
                            held.grow(move);
                            rest.shrink(move);
                            this.store.setItem(slot, held);
                        }
                    }
                    else if (pass == 1 && held.isEmpty())
                    {
                        this.store.setItem(slot, rest);
                        rest = ItemStack.EMPTY;
                    }
                }
            }
        }
        return rest;
    }

    /** Which size upgrades a site takes. */
    public enum UpgradeKind
    {
        NONE,
        BOUNDS,
        QUARRY
    }

    /** A new site's width and length. */
    protected abstract int defaultSize();

    /** Largest width or length without an upgrade, with the medium one, and with the large one. */
    protected abstract List<? extends Integer> maxSizes();

    public UpgradeKind upgradeKind()
    {
        return UpgradeKind.BOUNDS;
    }

    /** Rebuilds the to-do lists. */
    protected abstract void scan(Level level);

    /** Drops a finished or abandoned tile from the lists until the next scan. */
    protected abstract void forget(BlockPos tile);

    public SimpleContainer store()
    {
        return this.store;
    }

    @Nullable
    public UUID owner()
    {
        return this.owner;
    }

    void setOwner(@Nullable UUID owner)
    {
        this.owner = owner;
        this.setChanged();
    }

    /** The area relative to the block. */
    public WorksiteArea area()
    {
        return this.area != null ? this.area : this.defaultArea();
    }

    /** A new site's area: {@code defaultSize} square around the block. A quarry's lies in front. */
    protected WorksiteArea defaultArea()
    {
        int size = Math.min(this.defaultSize(), this.maxSize());
        return WorksiteArea.centered(size, size);
    }

    /** West edge, in world coordinates. */
    public int minX()
    {
        return this.worldPosition.getX() + this.area().minX();
    }

    public int maxX()
    {
        return this.worldPosition.getX() + this.area().maxX();
    }

    /** North edge, in world coordinates. */
    public int minZ()
    {
        return this.worldPosition.getZ() + this.area().minZ();
    }

    public int maxZ()
    {
        return this.worldPosition.getZ() + this.area().maxZ();
    }

    /** Whether a column is inside the area. */
    public boolean inArea(BlockPos pos)
    {
        return pos.getX() >= this.minX() && pos.getX() <= this.maxX() && pos.getZ() >= this.minZ() && pos.getZ() <= this.maxZ();
    }

    /** The area's columns, from {@code below} under the block's level to {@code above} over it. */
    public AABB columns(int below, int above)
    {
        int y = this.worldPosition.getY();
        return new AABB(this.minX(), y - below, this.minZ(), this.maxX() + 1, y + above + 1, this.maxZ() + 1);
    }

    /** Largest width or length at the current upgrade. */
    public int maxSize()
    {
        List<? extends Integer> sizes = this.maxSizes();
        if (sizes.isEmpty())
        {
            return 1;
        }
        return Math.max(1, sizes.get(Math.min(this.sizeTier, sizes.size() - 1)));
    }

    /** Whether the area may cover the block's own column. A quarry digs under its area, so not there. */
    public boolean mayContainBlock()
    {
        return true;
    }

    /** Whether the area can change now. A quarry cannot once digging starts; sites without an area never. */
    public boolean canResize()
    {
        return true;
    }

    /** Sets the area if it fits the limits; the to-do lists are rebuilt on the next request. */
    public boolean setArea(WorksiteArea area)
    {
        if (!this.canResize() || !area.fits(this.maxSize(), AncientEmpiresConfig.workAreaReach, this.mayContainBlock()))
        {
            return false;
        }
        this.area = area;
        this.scannedAt = -1L;
        this.setChanged();
        return true;
    }

    /** 0 without a size upgrade, 1 medium, 2 large. */
    public int sizeTier()
    {
        return this.sizeTier;
    }

    public void setSizeTier(int tier)
    {
        this.sizeTier = Math.max(0, Math.min(2, tier));
        this.setChanged();
    }

    protected final void refresh()
    {
        Level level = this.level;
        // Not "now - scannedAt" alone: with an unset time that subtraction would overflow.
        if (level == null || (this.scannedAt >= 0L && level.getGameTime() - this.scannedAt < AncientEmpiresConfig.workScanTicks))
        {
            return;
        }
        this.scannedAt = level.getGameTime();
        level.getProfiler().push("ancientempires_worksite_scan");
        this.scan(level);
        level.getProfiler().pop();
        this.claims.entrySet().removeIf(entry -> entry.getValue()[1] <= level.getGameTime());
        this.entityClaims.entrySet().removeIf(entry -> entry.getValue()[1] <= level.getGameTime());
    }

    /** Forces the next request to rescan, after a change the lists would miss. */
    protected final void invalidateScan()
    {
        this.scannedAt = -1L;
    }

    /** The nearest unclaimed tile in the list, claimed for the worker, or null. */
    @Nullable
    protected final BlockPos claimNearest(List<BlockPos> tiles, int workerId, BlockPos from)
    {
        long now = this.level == null ? 0L : this.level.getGameTime();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos tile : tiles)
        {
            long[] claim = this.claims.get(tile);
            if (claim != null && claim[0] != workerId && claim[1] > now)
            {
                continue;
            }
            double distance = tile.distSqr(from);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = tile;
            }
        }
        if (best != null)
        {
            this.claims.put(best, new long[] {workerId, now + AncientEmpiresConfig.workClaimTicks});
        }
        return best;
    }

    /** The worker is done with a tile, or gave up on it. */
    public void release(BlockPos tile)
    {
        this.claims.remove(tile);
        this.forget(tile);
    }

    /**
     * The nearest creature in the list no other worker is after, claimed for
     * this worker, or null. Animals move, so creature jobs are claimed by the
     * creature, not a tile.
     */
    @Nullable
    protected final <E extends Entity> E claimNearestEntity(List<E> creatures, int workerId, BlockPos from)
    {
        long now = this.level == null ? 0L : this.level.getGameTime();
        E best = null;
        double bestDistance = Double.MAX_VALUE;
        for (E creature : creatures)
        {
            long[] claim = this.entityClaims.get(creature.getUUID());
            if (!creature.isAlive() || (claim != null && claim[0] != workerId && claim[1] > now))
            {
                continue;
            }
            double distance = creature.blockPosition().distSqr(from);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = creature;
            }
        }
        if (best != null)
        {
            this.entityClaims.put(best.getUUID(), new long[] {workerId, now + AncientEmpiresConfig.workClaimTicks});
        }
        return best;
    }

    public void releaseEntity(UUID creature)
    {
        this.entityClaims.remove(creature);
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (this.owner != null)
        {
            tag.putUUID("Owner", this.owner);
        }
        if (this.area != null)
        {
            tag.putIntArray("Area", new int[] {this.area.minX(), this.area.minZ(), this.area.maxX(), this.area.maxZ()});
        }
        tag.putInt("SizeTier", this.sizeTier);
        NonNullList<ItemStack> items = NonNullList.withSize(this.store.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++)
        {
            items.set(slot, this.store.getItem(slot));
        }
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        int[] area = tag.getIntArray("Area");
        // Old saves kept a radius; they start from the default area.
        this.area = area.length == 4 ? new WorksiteArea(area[0], area[1], area[2], area[3]) : null;
        this.sizeTier = tag.getInt("SizeTier");
        NonNullList<ItemStack> items = NonNullList.withSize(this.store.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        for (int slot = 0; slot < items.size(); slot++)
        {
            this.store.setItem(slot, items.get(slot));
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side)
    {
        if (capability == ForgeCapabilities.ITEM_HANDLER)
        {
            return this.storeHandler.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        this.storeHandler.invalidate();
    }

    /** For the break drop. */
    List<ItemStack> contents()
    {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < this.store.getContainerSize(); slot++)
        {
            if (!this.store.getItem(slot).isEmpty())
            {
                items.add(this.store.getItem(slot));
            }
        }
        return items;
    }
}
