package com.ancientempires.worldgen;

import com.ancientempires.structure.AeTemplates;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The templates world generation may place, read from their sidecars (AW's
 * validation): those with {@code worldGenEnabled}, their mods present. Also
 * which territories (AW's factions by region) each biome can hold, weighted as
 * AW weighed them: by how many templates a territory has there. Rebuilt when
 * the datapacks reload; read from world generation threads, so immutable.
 */
public final class AwSiteIndex
{
    /**
     * One placeable template and AW's validation for it. Biome and dimension
     * sets hold ids as strings. {@code leveling} and {@code fill} are how far
     * the land may rise above and fall below the build key; the water depths
     * are an island's (AW's default: up to its offset height, at least half
     * that); {@code minHeight} and {@code maxHeight} bound the build key's y.
     */
    public record Site(ResourceLocation id, String name, String kind, String territory, int weight, int clusterValue,
            boolean unique, int minDistance, int leveling, int fill, int border, int minWater, int maxWater, int minHeight, int maxHeight,
            Set<String> biomes, boolean biomeWhitelist, Set<String> dimensions, boolean dimensionWhitelist, int[] size, int[] offset)
    {
        public boolean allowsBiome(String biome)
        {
            return this.biomeWhitelist ? this.biomes.contains(biome) : !this.biomes.contains(biome);
        }

        public boolean allowsDimension(String dimension)
        {
            if (this.dimensions.isEmpty())
            {
                return dimension.equals("minecraft:overworld");
            }
            return this.dimensionWhitelist == this.dimensions.contains(dimension);
        }
    }

    public static final String GENERIC = "generic";
    /**
     * Widest template world generation places. Vanilla links a structure to the
     * chunks within 8 of the chunk it starts in, so wider ones would be cut off;
     * AW's giant islands (up to 382 across) wait for the town and mega-structure
     * work, which needs the same thing.
     */
    public static final int MAX_WIDTH = 256;

    private static volatile AwSiteIndex current;
    private static volatile Map<ResourceLocation, JsonObject> builtFrom;

    private final List<Site> sites;
    private final Map<String, Set<String>> territoriesByBiome;
    private final List<Site> blacklisting;
    private final Map<String, Integer> templatesPerTerritory;
    private final Map<String, Site> byName;
    private final Map<String, Set<String>> biomeCache = new ConcurrentHashMap<>();

    private AwSiteIndex(List<Site> sites)
    {
        this.sites = List.copyOf(sites);
        Map<String, Set<String>> byBiome = new HashMap<>();
        List<Site> blacklisting = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Site site : sites)
        {
            counts.merge(site.territory(), 1, Integer::sum);
            if (!site.biomeWhitelist())
            {
                blacklisting.add(site);
                continue;
            }
            for (String biome : site.biomes())
            {
                byBiome.computeIfAbsent(biome, key -> new HashSet<>()).add(site.territory());
            }
        }
        Map<String, Set<String>> frozen = new HashMap<>();
        byBiome.forEach((biome, territories) -> frozen.put(biome, Set.copyOf(territories)));
        this.territoriesByBiome = Map.copyOf(frozen);
        this.blacklisting = List.copyOf(blacklisting);
        this.templatesPerTerritory = Map.copyOf(counts);
        Map<String, Site> names = new HashMap<>();
        sites.forEach(site -> names.put(site.name(), site));
        this.byName = Map.copyOf(names);
    }

    /** The index for the loaded templates, rebuilt after a datapack reload. */
    public static AwSiteIndex get()
    {
        Map<ResourceLocation, JsonObject> sidecars = AeTemplates.all();
        AwSiteIndex index = current;
        if (index == null || builtFrom != sidecars)
        {
            synchronized (AwSiteIndex.class)
            {
                if (current == null || builtFrom != sidecars)
                {
                    current = build(sidecars);
                    builtFrom = sidecars;
                    Territories.clear();
                }
                index = current;
            }
        }
        return index;
    }

    public List<Site> sites()
    {
        return this.sites;
    }

    /** A placeable template by its short name, or null. */
    @Nullable
    public Site byName(String name)
    {
        return this.byName.get(name);
    }

    /** Territories with a template that may stand in this biome (empty: none). */
    public Set<String> territoriesIn(String biome)
    {
        return this.biomeCache.computeIfAbsent(biome, key ->
        {
            Set<String> territories = new HashSet<>(this.territoriesByBiome.getOrDefault(key, Set.of()));
            for (Site site : this.blacklisting)
            {
                if (site.allowsBiome(key))
                {
                    territories.add(site.territory());
                }
            }
            return Set.copyOf(territories);
        });
    }

    /**
     * AW's territory weight: its templates plus the generic ones (which may
     * stand in any territory), times its {@code territory_weight_multiplier}.
     */
    public int territoryWeight(String territory)
    {
        int templates = this.templatesPerTerritory.getOrDefault(territory, 0)
                + (territory.equals(GENERIC) ? 0 : this.templatesPerTerritory.getOrDefault(GENERIC, 0));
        return (int) (templates * TerritorySettings.of(territory).weightMultiplier());
    }

    private static AwSiteIndex build(Map<ResourceLocation, JsonObject> sidecars)
    {
        List<Site> sites = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : sidecars.entrySet())
        {
            JsonObject json = entry.getValue();
            JsonObject validation = json.has("validation") ? json.getAsJsonObject("validation") : new JsonObject();
            if (!bool(validation, "worldGenEnabled") || !modsPresent(json))
            {
                continue;
            }
            int[] size = triple(json, "size");
            if (Math.max(size[0], size[2]) > MAX_WIDTH)
            {
                continue;
            }
            String territory = text(validation, "territoryName").toLowerCase(Locale.ROOT);
            territory = territory.isEmpty() ? GENERIC : territory;
            Set<String> biomes = new HashSet<>();
            for (String biome : list(validation, "biomeList"))
            {
                biomes.add(biome(biome));
            }
            int[] offset = triple(json, "offset");
            int maxWater = number(validation, "maxWaterDepth", offset[1]);
            sites.add(new Site(entry.getKey(), AeTemplates.name(entry.getKey()), text(validation, "type").toLowerCase(Locale.ROOT), territory,
                    Math.max(1, number(validation, "selectionWeight", 1)), Math.max(0, number(validation, "clusterValue", 0)), bool(validation, "unique"),
                    Math.max(0, number(validation, "minDuplicateDistance", 0)), Math.max(0, number(validation, "leveling", 0)),
                    Math.max(0, number(validation, "fill", 0)), Math.max(0, number(validation, "border", 0)),
                    number(validation, "minWaterDepth", maxWater / 2), maxWater,
                    number(validation, "minGenerationHeight", Integer.MIN_VALUE), number(validation, "maxGenerationHeight", Integer.MAX_VALUE),
                    Collections.unmodifiableSet(biomes), !validation.has("biomeWhiteList") || bool(validation, "biomeWhiteList"),
                    Set.copyOf(list(validation, "dimensionList")), !validation.has("dimensionWhiteList") || bool(validation, "dimensionWhiteList"),
                    size, offset));
        }
        return new AwSiteIndex(sites);
    }

    /**
     * A 1.12 biome id as this game names it: Oh The Biomes You'll Go's 1.12 ids
     * carried a {@code b} prefix and the mod is now Oh The Biomes We've Gone.
     */
    static String biome(String id)
    {
        if (id.startsWith("byg:"))
        {
            String path = id.substring(4);
            return "biomeswevegone:" + (path.startsWith("b") ? path.substring(1) : path);
        }
        return id;
    }

    private static boolean modsPresent(JsonObject json)
    {
        if (!json.has("requiredMods"))
        {
            return true;
        }
        for (JsonElement mod : json.getAsJsonArray("requiredMods"))
        {
            if (!ModList.get().isLoaded(mod.getAsString()))
            {
                return false;
            }
        }
        return true;
    }

    private static String text(JsonObject json, String key)
    {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
    }

    private static boolean bool(JsonObject json, String key)
    {
        return Boolean.parseBoolean(text(json, key));
    }

    private static int number(JsonObject json, String key, int fallback)
    {
        try
        {
            return Integer.parseInt(text(json, key).strip());
        }
        catch (NumberFormatException exception)
        {
            return fallback;
        }
    }

    private static List<String> list(JsonObject json, String key)
    {
        List<String> values = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray())
        {
            for (JsonElement element : json.getAsJsonArray(key))
            {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    private static int[] triple(JsonObject json, String key)
    {
        int[] values = new int[3];
        if (json.has(key))
        {
            JsonArray array = json.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < array.size(); i++)
            {
                values[i] = array.get(i).getAsInt();
            }
        }
        return values;
    }
}
