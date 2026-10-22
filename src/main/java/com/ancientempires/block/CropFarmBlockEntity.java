package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
 * A crop farm: its owner, its square area, and a store for seeds and harvest.
 * It does no farming itself. Farmers ask it for work; it answers from lists of
 * tiles rebuilt at most every {@code work.scanTicks}, and only while asked, so
 * an idle farm costs nothing. A tile a farmer is on is claimed so two farmers
 * do not walk to the same crop.
 */
public final class CropFarmBlockEntity extends BlockEntity
{
    public static final int STORE_SIZE = 18;

    public enum Task
    {
        HARVEST, PLANT, TILL
    }

    private final SimpleContainer store = new SimpleContainer(STORE_SIZE)
    {
        @Override
        public void setChanged()
        {
            super.setChanged();
            CropFarmBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player)
        {
            return net.minecraft.world.Container.stillValidBlockEntity(CropFarmBlockEntity.this, player);
        }
    };
    private final LazyOptional<IItemHandler> storeHandler = LazyOptional.of(() -> new InvWrapper(this.store));
    @Nullable
    private UUID owner;
    private int radius = -1;

    private final List<BlockPos> harvest = new ArrayList<>();
    private final List<BlockPos> plant = new ArrayList<>();
    private final List<BlockPos> till = new ArrayList<>();
    private long scannedAt = Long.MIN_VALUE;
    /** Tile to the id of the farmer working it, and the game time the claim lapses. */
    private final Map<BlockPos, long[]> claims = new HashMap<>();

    public CropFarmBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.CROP_FARM.get(), pos, state);
    }

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
        return this.radius < 0 ? AncientEmpiresConfig.farmDefaultRadius : this.radius;
    }

    /** Steps the area to the next size, wrapping from the largest back to 2. */
    int cycleRadius()
    {
        int next = this.radius() + 1;
        this.radius = next > AncientEmpiresConfig.farmMaxRadius ? 2 : next;
        this.scannedAt = Long.MIN_VALUE;
        this.setChanged();
        return this.radius;
    }

    /** Whether a position is in the worked area: the square, one layer below the farm block to one above. */
    public boolean inArea(BlockPos pos)
    {
        int r = this.radius();
        return Math.abs(pos.getX() - this.worldPosition.getX()) <= r && Math.abs(pos.getZ() - this.worldPosition.getZ()) <= r
                && pos.getY() >= this.worldPosition.getY() - 2 && pos.getY() <= this.worldPosition.getY() + 1;
    }

    /**
     * The nearest unclaimed tile needing this kind of work, claimed for the
     * farmer, or null. Lists are rebuilt when stale; a tile is checked again
     * when the farmer reaches it.
     */
    @Nullable
    public BlockPos claim(Task task, int farmerId, BlockPos from)
    {
        this.refresh();
        long now = this.level == null ? 0L : this.level.getGameTime();
        List<BlockPos> tiles = switch (task)
        {
            case HARVEST -> this.harvest;
            case PLANT -> this.plant;
            case TILL -> this.till;
        };
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos tile : tiles)
        {
            long[] claim = this.claims.get(tile);
            if (claim != null && claim[0] != farmerId && claim[1] > now)
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
            this.claims.put(best, new long[] {farmerId, now + AncientEmpiresConfig.farmClaimTicks});
        }
        return best;
    }

    /** The farmer is done with a tile, or gave up on it. The tile leaves the list until the next scan. */
    public void release(BlockPos tile)
    {
        this.claims.remove(tile);
        this.harvest.remove(tile);
        this.plant.remove(tile);
        this.till.remove(tile);
    }

    public boolean hasWork(Task task)
    {
        this.refresh();
        return !(switch (task)
        {
            case HARVEST -> this.harvest;
            case PLANT -> this.plant;
            case TILL -> this.till;
        }).isEmpty();
    }

    private void refresh()
    {
        Level level = this.level;
        if (level == null || level.getGameTime() - this.scannedAt < AncientEmpiresConfig.farmScanTicks)
        {
            return;
        }
        this.scannedAt = level.getGameTime();
        this.harvest.clear();
        this.plant.clear();
        this.till.clear();
        int r = this.radius();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseY = this.worldPosition.getY();
        for (int dx = -r; dx <= r; dx++)
        {
            for (int dz = -r; dz <= r; dz++)
            {
                for (int y = baseY - 2; y <= baseY; y++)
                {
                    cursor.set(this.worldPosition.getX() + dx, y, this.worldPosition.getZ() + dz);
                    this.scanTile(level, cursor);
                }
            }
        }
        this.claims.entrySet().removeIf(entry -> entry.getValue()[1] <= level.getGameTime());
    }

    /** Sorts one ground tile: a ripe crop above it, bare farmland, or soil a hoe can turn. */
    private void scanTile(Level level, BlockPos.MutableBlockPos ground)
    {
        if (ground.equals(this.worldPosition.below()))
        {
            return;
        }
        BlockState soil = level.getBlockState(ground);
        BlockPos above = ground.above();
        BlockState crop = level.getBlockState(above);
        if (soil.getBlock() instanceof FarmBlock)
        {
            if (crop.isAir())
            {
                this.plant.add(above.immutable());
            }
            else if (isRipe(crop))
            {
                this.harvest.add(above.immutable());
            }
            return;
        }
        if (crop.is(Blocks.PUMPKIN) || crop.is(Blocks.MELON))
        {
            this.harvest.add(above.immutable());
            return;
        }
        if (crop.isAir() && (soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.DIRT_PATH)))
        {
            this.till.add(ground.immutable());
        }
    }

    public static boolean isRipe(BlockState state)
    {
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
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
        net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(STORE_SIZE, ItemStack.EMPTY);
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
        net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(STORE_SIZE, ItemStack.EMPTY);
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
