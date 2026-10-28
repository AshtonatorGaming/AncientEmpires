package com.ancientempires.worldgen;

import com.ancientempires.AncientEmpires;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * AW's per-territory tuning ({@code territory_settings.json}), read from
 * {@code data/<namespace>/ae_territories/*.json} in AW's own format: a
 * {@code defaults} block and a {@code territories} map, each entry overriding
 * the defaults. A pack may add a file to retune one territory; later files
 * win per key.
 * <ul>
 * <li>{@code max_radius_in_chunks}: how far a territory reaches; caps its
 * cluster budget, and big structures (cluster value over 50) weigh less
 * the further they are from its centre.</li>
 * <li>{@code per_chunk_cluster_value_multiplier}: budget per chunk, times
 * {@code structures.chunkClusterValue}.</li>
 * <li>{@code structure_generation_chance_multiplier}: times
 * {@code structures.generationChance}.</li>
 * <li>{@code territory_weight_multiplier}: how often the territory is picked
 * where it may stand.</li>
 * </ul>
 */
public final class TerritorySettings extends SimpleJsonResourceReloadListener
{
    public record Settings(int maxRadiusChunks, float clusterMultiplier, float chanceMultiplier, float weightMultiplier)
    {
    }

    private static final Gson GSON = new GsonBuilder().create();
    /** AW's own defaults, used when no file sets them. */
    private static final Settings AW_DEFAULTS = new Settings(11, 1.0F, 1.0F, 1.0F);

    private static volatile Settings defaults = AW_DEFAULTS;
    private static volatile Map<String, Settings> byTerritory = Map.of();

    public TerritorySettings()
    {
        super(GSON, "ae_territories");
    }

    public static Settings of(String territory)
    {
        return byTerritory.getOrDefault(territory, defaults);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler)
    {
        Settings base = AW_DEFAULTS;
        Map<String, JsonObject> overrides = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet())
        {
            if (!file.getValue().isJsonObject())
            {
                continue;
            }
            JsonObject json = file.getValue().getAsJsonObject();
            if (json.has("defaults") && json.get("defaults").isJsonObject())
            {
                base = read(json.getAsJsonObject("defaults"), base);
            }
            if (json.has("territories") && json.get("territories").isJsonObject())
            {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("territories").entrySet())
                {
                    if (entry.getValue().isJsonObject())
                    {
                        JsonObject merged = overrides.computeIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), key -> new JsonObject());
                        entry.getValue().getAsJsonObject().entrySet().forEach(field -> merged.add(field.getKey(), field.getValue()));
                    }
                }
            }
        }
        Map<String, Settings> settings = new HashMap<>();
        for (Map.Entry<String, JsonObject> entry : overrides.entrySet())
        {
            settings.put(entry.getKey(), read(entry.getValue(), base));
        }
        defaults = base;
        byTerritory = Map.copyOf(settings);
        AncientEmpires.LOGGER.info("Loaded settings for {} structure territories", settings.size());
    }

    private static Settings read(JsonObject json, Settings base)
    {
        try
        {
            return new Settings(
                    json.has("max_radius_in_chunks") ? json.get("max_radius_in_chunks").getAsInt() : base.maxRadiusChunks(),
                    json.has("per_chunk_cluster_value_multiplier") ? json.get("per_chunk_cluster_value_multiplier").getAsFloat() : base.clusterMultiplier(),
                    json.has("structure_generation_chance_multiplier") ? json.get("structure_generation_chance_multiplier").getAsFloat() : base.chanceMultiplier(),
                    json.has("territory_weight_multiplier") ? json.get("territory_weight_multiplier").getAsFloat() : base.weightMultiplier());
        }
        catch (RuntimeException exception)
        {
            AncientEmpires.LOGGER.warn("Bad territory setting {}: {}", json, exception.getMessage());
            return base;
        }
    }
}
