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

/**
 * Shared by worksites (crop farm, tree farm, quarry): an owner, a square area
 * around the block, an 18-slot store workers unload into and hoppers can reach,
 * and a to-do list the subclass rebuilds in {@link #scan}. The list is rebuilt
 * at most every {@code work.scanTicks}, and only when a worker asks, so an idle
 * worksite costs nothing. A worker claims the tile it walks to, so two workers
 * do not take the same one.
 */
public abstract class WorksiteBlockEntity extends BlockEntity
{
    public static final int STORE_SIZE = 18;

    private final SimpleContainer store = new SimpleContainer(STORE_SIZE)
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
    };
    private final LazyOptional<IItemHandler> storeHandler = LazyOptional.of(() -> new InvWrapper(this.store));
    @Nullable
    private UUID owner;
    private int radius = -1;
    /** Game time of the last scan; -1 means never, so the next request scans. */
    private long scannedAt = -1L;
    /** Tile to the id of the worker on it, and the game time the claim lapses. */
    private final Map<BlockPos, long[]> claims = new HashMap<>();
    private final Map<UUID, long[]> entityClaims = new HashMap<>();

    protected WorksiteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        super(type, pos, state);
    }

    protected abstract int defaultRadius();

    protected abstract int maxRadius();

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

    public int radius()
    {
        return this.radius < 0 ? this.defaultRadius() : this.radius;
    }

    /** Steps the area to the next size, wrapping from the largest back to 2. */
    int cycleRadius()
    {
        int next = this.radius() + 1;
        this.radius = next > this.maxRadius() ? 2 : next;
        this.scannedAt = -1L;
        this.setChanged();
        return this.radius;
    }

    /** The middle of the square: the block itself, except where the area lies in front of it (a quarry). */
    public BlockPos center()
    {
        return this.worldPosition;
    }

    /** Whether the area can change size now. A quarry cannot once digging starts. */
    public boolean canResize()
    {
        return true;
    }

    /** Whether a column is inside the square. */
    public boolean inSquare(BlockPos pos)
    {
        int r = this.radius();
        BlockPos center = this.center();
        return Math.abs(pos.getX() - center.getX()) <= r && Math.abs(pos.getZ() - center.getZ()) <= r;
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
        tag.putInt("Radius", this.radius);
        NonNullList<ItemStack> items = NonNullList.withSize(STORE_SIZE, ItemStack.EMPTY);
        for (int slot = 0; slot < STORE_SIZE; slot++)
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
        this.radius = tag.contains("Radius") ? tag.getInt("Radius") : -1;
        NonNullList<ItemStack> items = NonNullList.withSize(STORE_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        for (int slot = 0; slot < STORE_SIZE; slot++)
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
