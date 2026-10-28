package com.ancientempires.structure;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Every structure template the datapacks hold ({@code data/<namespace>/ae_templates/**}):
 * the sidecars ({@code .json}, AW's header and validation) are read at each
 * reload and kept, all of them, since they are small. A template's blocks
 * ({@code .nbt}) are read only when it is placed, and kept in a cache capped at
 * {@code structures.templateCacheMb}, the least recently used dropped first.
 * Ids are the file path without the folder or extension, such as
 * {@code ancientempires:normal/awnationempire/empireemperorstatuewell}.
 */
public final class AeTemplates extends SimpleJsonResourceReloadListener
{
    public static final String FOLDER = "ae_templates";
    private static final Gson GSON = new Gson();
    private static volatile Map<ResourceLocation, JsonObject> sidecars = Map.of();
    /** Each template by its short name (the file name; unique across the library). */
    private static volatile Map<String, ResourceLocation> byName = Map.of();
    @Nullable
    private static volatile ResourceManager resources;
    private static final Map<ResourceLocation, AeTemplate> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static long cachedBytes;

    public AeTemplates()
    {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler)
    {
        Map<ResourceLocation, JsonObject> loaded = new TreeMap<>();
        files.forEach((id, json) ->
        {
            if (json.isJsonObject())
            {
                loaded.put(id, json.getAsJsonObject());
            }
        });
        sidecars = Collections.unmodifiableMap(loaded);
        Map<String, ResourceLocation> names = new TreeMap<>();
        loaded.keySet().forEach(id -> names.putIfAbsent(name(id), id));
        byName = Collections.unmodifiableMap(names);
        resources = manager;
        synchronized (CACHE)
        {
            CACHE.clear();
            cachedBytes = 0L;
        }
        AncientEmpires.LOGGER.info("Structure templates: {} indexed", loaded.size());
    }

    /** Every template's sidecar, by id. */
    public static Map<ResourceLocation, JsonObject> all()
    {
        return sidecars;
    }

    /** A template's short name: its file name, such as {@code empireballistaetower}. */
    public static String name(ResourceLocation id)
    {
        return id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
    }

    /** Its category: the folder it sits in (AW's nation or set: {@code awnationempire}, {@code oceanislands}). */
    public static String category(ResourceLocation id)
    {
        String path = id.getPath();
        int last = path.lastIndexOf('/');
        return last < 0 ? "" : path.substring(path.lastIndexOf('/', last - 1) + 1, last);
    }

    /** Every short name, for command suggestions. */
    public static Collection<String> names()
    {
        return byName.keySet();
    }

    /** The template a short name (or a full id) names, or null. */
    @Nullable
    public static ResourceLocation byName(String name)
    {
        String lower = name.toLowerCase(Locale.ROOT);
        ResourceLocation id = byName.get(lower);
        if (id == null)
        {
            ResourceLocation full = ResourceLocation.tryParse(lower);
            id = full != null && sidecars.containsKey(full) ? full : null;
        }
        return id;
    }

    @Nullable
    public static JsonObject sidecar(ResourceLocation id)
    {
        return sidecars.get(id);
    }

    /** A template's blocks, read on first use; empty if it is missing or unreadable. */
    public static Optional<AeTemplate> get(ResourceLocation id)
    {
        synchronized (CACHE)
        {
            AeTemplate cached = CACHE.get(id);
            if (cached != null)
            {
                return Optional.of(cached);
            }
        }
        ResourceManager manager = resources;
        if (manager == null || !sidecars.containsKey(id))
        {
            return Optional.empty();
        }
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), FOLDER + "/" + id.getPath() + ".nbt");
        Optional<Resource> resource = manager.getResource(file);
        if (resource.isEmpty())
        {
            return Optional.empty();
        }
        AeTemplate template;
        try (InputStream stream = resource.get().open())
        {
            template = AeTemplate.load(NbtIo.readCompressed(stream));
        }
        catch (IOException | RuntimeException exception)
        {
            AncientEmpires.LOGGER.error("Could not read structure template {}", file, exception);
            return Optional.empty();
        }
        long bytes = template.packedBytes();
        long budget = AncientEmpiresConfig.structuresTemplateCacheMb * 1024L * 1024L;
        synchronized (CACHE)
        {
            CACHE.put(id, template);
            cachedBytes += bytes;
            Iterator<Map.Entry<ResourceLocation, AeTemplate>> iterator = CACHE.entrySet().iterator();
            while (cachedBytes > budget && CACHE.size() > 1 && iterator.hasNext())
            {
                Map.Entry<ResourceLocation, AeTemplate> eldest = iterator.next();
                if (eldest.getKey().equals(id))
                {
                    continue;
                }
                cachedBytes -= eldest.getValue().packedBytes();
                iterator.remove();
            }
        }
        return Optional.of(template);
    }
}
