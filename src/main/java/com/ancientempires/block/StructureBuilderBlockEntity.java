package com.ancientempires.block;

import com.ancientempires.registry.ModBlockEntities;
import com.ancientempires.structure.AeBuilds;
import com.ancientempires.structure.AePlacer;
import com.ancientempires.structure.AeTemplate;
import com.ancientempires.structure.AeTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A structure being built in survival (AW's structure builder), one layer at a
 * time from the bottom: its template, how it is turned, and the next layer.
 * The block stands on the build key; its store takes the materials (by hand,
 * a hopper, or a courier), and a builder working it lays the next layer once
 * the store holds what that layer takes ({@link AeBuilds#layerCost}). The
 * key's own cell is laid last, when the builder block gives way (dropping
 * what is left in its store).
 */
public final class StructureBuilderBlockEntity extends WorksiteBlockEntity
{
    @Nullable
    private ResourceLocation template;
    private Rotation rotation = Rotation.NONE;
    private int layer;
    /** The next layer's cost, kept for the template object and layer it was worked out for (a builder asks every few seconds). */
    @Nullable
    private AeTemplate costTemplate;
    private int costLayer = -1;
    private Map<Item, Integer> cost = Map.of();

    public StructureBuilderBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.STRUCTURE_BUILDER.get(), pos, state);
    }

    /** Starts a build of {@code template} turned by {@code rotation}, its key on this block. */
    public void start(ResourceLocation template, Rotation rotation)
    {
        this.costLayer = -1;
        this.template = template;
        this.rotation = rotation;
        this.layer = 0;
        this.setChanged();
    }

    @Override
    protected int defaultSize()
    {
        return 1;
    }

    @Override
    protected List<? extends Integer> maxSizes()
    {
        return List.of(1);
    }

    @Override
    public UpgradeKind upgradeKind()
    {
        return UpgradeKind.NONE;
    }

    @Override
    public boolean canResize()
    {
        return false;
    }

    @Override
    protected void scan(Level level)
    {
    }

    @Override
    protected void forget(BlockPos tile)
    {
    }

    private Optional<AeTemplate> loaded()
    {
        return this.template == null ? Optional.empty() : AeTemplates.get(this.template);
    }

    /** The template's name, or empty. */
    public String templateName()
    {
        return this.template == null ? "" : AeTemplates.name(this.template);
    }

    public int layer()
    {
        return this.layer;
    }

    public int layers()
    {
        return this.loaded().map(AeTemplate::sizeY).orElse(0);
    }

    /** What the next layer still lacks from the store (empty: ready). */
    public Map<Item, Integer> missing()
    {
        Map<Item, Integer> missing = new LinkedHashMap<>();
        Optional<AeTemplate> template = this.loaded();
        if (template.isEmpty() || this.layer >= template.get().sizeY())
        {
            return missing;
        }
        Map<Item, Integer> stored = this.stored();
        this.layerCost(template.get()).forEach((item, count) ->
        {
            int lacking = count - stored.getOrDefault(item, 0);
            if (lacking > 0)
            {
                missing.put(item, lacking);
            }
        });
        return missing;
    }

    /** The next layer's cost, worked out once per layer (and again after templates reload). */
    private Map<Item, Integer> layerCost(AeTemplate template)
    {
        if (template != this.costTemplate || this.layer != this.costLayer)
        {
            this.costTemplate = template;
            this.costLayer = this.layer;
            this.cost = AeBuilds.layerCost(template, this.layer);
        }
        return this.cost;
    }

    private Map<Item, Integer> stored()
    {
        Map<Item, Integer> stored = new LinkedHashMap<>();
        SimpleContainer store = this.store();
        for (int slot = 0; slot < store.getContainerSize(); slot++)
        {
            ItemStack stack = store.getItem(slot);
            if (!stack.isEmpty())
            {
                stored.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return stored;
    }

    /** Whether the next layer can be laid: a template, layers left, and its materials in store. */
    public boolean canBuild()
    {
        return this.loaded().isPresent() && this.layer < this.layers() && this.missing().isEmpty();
    }

    /**
     * Lays the next layer: takes its materials from the store and places its
     * cells (sparing this block's own cell). After the last, the builder gives
     * way and the key's cell is placed. Returns false when it could not.
     */
    public boolean buildLayer()
    {
        if (!(this.level instanceof ServerLevel level) || !this.canBuild())
        {
            return false;
        }
        AeTemplate template = this.loaded().orElseThrow();
        String name = AeTemplates.name(this.template);
        int[] offset = AeBuilds.offset(this.template);
        this.layerCost(template).forEach(this::take);
        BoundingBox bounds = AePlacer.bounds(template, offset, this.worldPosition, this.rotation);
        int y = bounds.minY() + this.layer;
        for (BoundingBox box : around(bounds, y, this.worldPosition))
        {
            AePlacer.placeSurvival(level, template, name, offset, this.worldPosition, this.rotation, box);
        }
        this.layer++;
        this.setChanged();
        if (this.layer >= template.sizeY())
        {
            this.finish(level, template, name, offset, bounds);
        }
        return true;
    }

    /** The builder gives way, its store dropped, and the key's cell is built. */
    private void finish(ServerLevel level, AeTemplate template, String name, int[] offset, BoundingBox bounds)
    {
        BlockPos key = this.worldPosition;
        Rotation rotation = this.rotation;
        Player owner = this.owner() == null ? null : level.getPlayerByUUID(this.owner());
        level.removeBlock(key, false);
        if (bounds.isInside(key))
        {
            AePlacer.placeSurvival(level, template, name, offset, key, rotation, BoundingBox.fromCorners(key, key));
        }
        if (owner != null)
        {
            owner.displayClientMessage(Component.translatable("message.ancientempires.structure_builder.done", name), false);
        }
    }

    /** Layer {@code y} of {@code bounds} as boxes that leave out {@code key}'s cell. */
    private static List<BoundingBox> around(BoundingBox bounds, int y, BlockPos key)
    {
        List<BoundingBox> boxes = new ArrayList<>();
        if (key.getY() != y || !bounds.isInside(key))
        {
            boxes.add(new BoundingBox(bounds.minX(), y, bounds.minZ(), bounds.maxX(), y, bounds.maxZ()));
            return boxes;
        }
        int x = key.getX();
        int z = key.getZ();
        if (x > bounds.minX())
        {
            boxes.add(new BoundingBox(bounds.minX(), y, bounds.minZ(), x - 1, y, bounds.maxZ()));
        }
        if (x < bounds.maxX())
        {
            boxes.add(new BoundingBox(x + 1, y, bounds.minZ(), bounds.maxX(), y, bounds.maxZ()));
        }
        if (z > bounds.minZ())
        {
            boxes.add(new BoundingBox(x, y, bounds.minZ(), x, y, z - 1));
        }
        if (z < bounds.maxZ())
        {
            boxes.add(new BoundingBox(x, y, z + 1, x, y, bounds.maxZ()));
        }
        return boxes;
    }

    private void take(Item item, int count)
    {
        SimpleContainer store = this.store();
        for (int slot = 0; slot < store.getContainerSize() && count > 0; slot++)
        {
            ItemStack stack = store.getItem(slot);
            if (stack.is(item))
            {
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                store.setItem(slot, stack);
                count -= taken;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (this.template != null)
        {
            tag.putString("Template", this.template.toString());
        }
        tag.putString("Rotation", this.rotation.name());
        tag.putInt("Layer", this.layer);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.template = tag.contains("Template") ? ResourceLocation.tryParse(tag.getString("Template")) : null;
        try
        {
            this.rotation = Rotation.valueOf(tag.getString("Rotation"));
        }
        catch (IllegalArgumentException exception)
        {
            this.rotation = Rotation.NONE;
        }
        this.layer = tag.getInt("Layer");
    }
}
