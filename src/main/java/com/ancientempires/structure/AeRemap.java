package com.ancientempires.structure;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What a template's blocks and entities become in this game: the rename tables
 * in {@code data/<namespace>/ae_remap/*.json} (all files merged, later packs
 * winning), read at every datapack reload. A file may hold:
 * <ul>
 * <li>{@code namespaces}: a whole mod renamed ({@code "xreliquary": "reliquary"}).</li>
 * <li>{@code blocks}: an id to a block id (its properties carried over where the
 * new block has them), or to {@code {"Name": ..., "Properties": {...}}} for an
 * exact state. Used for renamed blocks, dead mods, and AW's own blocks until
 * they exist.</li>
 * <li>{@code entities}: an id to an id.</li>
 * </ul>
 * A block resolves through the live registry first, so a mod installed later
 * just works; then the table; then the namespace rename; then
 * {@code structures.missingBlock}. Each property is checked against the block it
 * lands on, and ones it lacks are dropped.
 */
public final class AeRemap extends SimpleJsonResourceReloadListener
{
    private static final Gson GSON = new Gson();
    private static volatile Tables tables = new Tables(Map.of(), Map.of(), Map.of());

    /** What a palette entry became, and how: null for a direct match, else a short note for reports. */
    public record Resolved(BlockState state, @Nullable String note)
    {
    }

    private record Tables(Map<String, String> namespaces, Map<String, JsonElement> blocks, Map<String, String> entities)
    {
    }

    public AeRemap()
    {
        super(GSON, "ae_remap");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler)
    {
        Map<String, String> namespaces = new HashMap<>();
        Map<String, JsonElement> blocks = new HashMap<>();
        Map<String, String> entities = new HashMap<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file ->
        {
            if (!file.getValue().isJsonObject())
            {
                return;
            }
            JsonObject json = file.getValue().getAsJsonObject();
            if (json.has("namespaces"))
            {
                json.getAsJsonObject("namespaces").entrySet().forEach(entry -> namespaces.put(entry.getKey(), entry.getValue().getAsString()));
            }
            if (json.has("blocks"))
            {
                json.getAsJsonObject("blocks").entrySet().forEach(entry -> blocks.put(entry.getKey(), entry.getValue()));
            }
            if (json.has("entities"))
            {
                json.getAsJsonObject("entities").entrySet().forEach(entry -> entities.put(entry.getKey(), entry.getValue().getAsString()));
            }
        });
        tables = new Tables(Map.copyOf(namespaces), Map.copyOf(blocks), Map.copyOf(entities));
        AncientEmpires.LOGGER.info("Structure remap: {} blocks, {} entities, {} namespaces", blocks.size(), entities.size(), namespaces.size());
    }

    /** A palette entry ({@code Name}, {@code Properties}) as a block state here. */
    public static Resolved block(CompoundTag entry)
    {
        String name = entry.getString("Name");
        CompoundTag properties = entry.getCompound("Properties");
        Block direct = registered(name);
        if (direct != null)
        {
            return new Resolved(withProperties(direct.defaultBlockState(), properties), null);
        }
        Tables current = tables;
        JsonElement mapped = current.blocks().get(name);
        if (mapped != null)
        {
            if (mapped.isJsonPrimitive())
            {
                Block block = registered(mapped.getAsString());
                if (block != null)
                {
                    return new Resolved(withProperties(block.defaultBlockState(), properties), "table");
                }
            }
            else if (mapped.isJsonObject())
            {
                JsonObject target = mapped.getAsJsonObject();
                Block block = registered(target.get("Name").getAsString());
                if (block != null)
                {
                    BlockState state = withProperties(block.defaultBlockState(), properties);
                    if (target.has("Properties"))
                    {
                        CompoundTag fixed = new CompoundTag();
                        target.getAsJsonObject("Properties").entrySet().forEach(property -> fixed.putString(property.getKey(), property.getValue().getAsString()));
                        state = withProperties(state, fixed);
                    }
                    return new Resolved(state, "table");
                }
            }
        }
        int colon = name.indexOf(':');
        if (colon > 0)
        {
            String renamed = current.namespaces().get(name.substring(0, colon));
            if (renamed != null)
            {
                Block block = registered(renamed + name.substring(colon));
                if (block != null)
                {
                    return new Resolved(withProperties(block.defaultBlockState(), properties), "namespace");
                }
            }
        }
        Block fallback = registered(AncientEmpiresConfig.structuresMissingBlock);
        return new Resolved(fallback != null ? fallback.defaultBlockState() : Blocks.AIR.defaultBlockState(), "missing");
    }

    /** An entity id here: the table, then the namespace rename; unchanged otherwise. */
    public static String entityId(String id)
    {
        Tables current = tables;
        String mapped = current.entities().get(id);
        if (mapped != null)
        {
            return mapped;
        }
        int colon = id.indexOf(':');
        if (colon > 0 && !ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.tryParse(id)))
        {
            String renamed = current.namespaces().get(id.substring(0, colon));
            if (renamed != null)
            {
                return renamed + id.substring(colon);
            }
        }
        return id;
    }

    @Nullable
    private static Block registered(String name)
    {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || !ForgeRegistries.BLOCKS.containsKey(id))
        {
            return null;
        }
        return ForgeRegistries.BLOCKS.getValue(id);
    }

    /** The state with each named property set where the block has it and the value is valid. */
    static BlockState withProperties(BlockState state, CompoundTag properties)
    {
        for (String key : properties.getAllKeys())
        {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
            if (property != null)
            {
                state = with(state, property, properties.getString(key));
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value)
    {
        Optional<T> parsed = property.getValue(value);
        return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
    }
}
