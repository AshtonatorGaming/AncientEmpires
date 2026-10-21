package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skin catalog shared by the selector and the renderer. A type id such as
 * {@code brigand.archer} uses the first texture in that class. A pinned texture
 * is {@code brigand.archer#brigand_archer_1}.
 */
public final class NpcSkins
{
    private static final String RESOURCE = "/assets/ancientempires/npc/skins.json";

    private static final List<Faction> FACTIONS;
    private static final Map<String, Entry> BY_ID;
    private static final Map<String, Entry> BY_TYPE;
    private static final int MAX_POOL;

    static
    {
        Loaded loaded = load();
        FACTIONS = List.copyOf(loaded.factions);
        BY_ID = Map.copyOf(loaded.byId);
        BY_TYPE = Map.copyOf(loaded.byType);
        MAX_POOL = loaded.maxPool;
    }

    private NpcSkins()
    {
    }

    public static List<Faction> factions()
    {
        return FACTIONS;
    }

    public static int maxPool()
    {
        return MAX_POOL;
    }

    public static int indexOfFaction(String factionId)
    {
        for (int index = 0; index < FACTIONS.size(); index++)
        {
            if (FACTIONS.get(index).id.equals(factionId))
            {
                return index;
            }
        }
        return 0;
    }

    @Nullable
    public static Faction faction(String factionId)
    {
        if (factionId == null || factionId.isEmpty())
        {
            return null;
        }
        for (Faction faction : FACTIONS)
        {
            if (faction.id.equals(factionId))
            {
                return faction;
            }
        }
        return null;
    }

    @Nullable
    public static NpcClass npcClass(String factionId, String classId)
    {
        Faction faction = faction(factionId);
        if (faction == null)
        {
            return null;
        }
        return faction.npcClass(classId);
    }

    /**
     * A pinned id, or a type id. A type id resolves to the first skin in that pool.
     */
    @Nullable
    public static Entry byId(String skinId)
    {
        if (skinId == null || skinId.isEmpty())
        {
            return null;
        }
        Entry entry = BY_ID.get(skinId);
        if (entry != null)
        {
            return entry;
        }
        return BY_TYPE.get(skinId);
    }

    @Nullable
    public static ResourceLocation texture(String skinId)
    {
        Entry entry = byId(skinId);
        if (entry == null)
        {
            return null;
        }
        return entry.texture;
    }

    private static Loaded load()
    {
        Loaded loaded = new Loaded();
        InputStream stream = NpcSkins.class.getResourceAsStream(RESOURCE);
        if (stream == null)
        {
            AncientEmpires.LOGGER.error("Missing NPC skin catalog {}", RESOURCE);
            return loaded;
        }
        try (InputStream in = stream; Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
        {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("factions"))
            {
                return loaded;
            }
            for (JsonElement factionElement : root.getAsJsonArray("factions"))
            {
                JsonObject factionObject = factionElement.getAsJsonObject();
                String factionId = text(factionObject, "id");
                List<NpcClass> classes = new ArrayList<>();
                if (factionObject.has("classes"))
                {
                    for (JsonElement classElement : factionObject.getAsJsonArray("classes"))
                    {
                        JsonObject classObject = classElement.getAsJsonObject();
                        String classId = text(classObject, "id");
                        List<Entry> skins = new ArrayList<>();
                        if (classObject.has("skins"))
                        {
                            for (JsonElement skinElement : classObject.getAsJsonArray("skins"))
                            {
                                JsonObject skinObject = skinElement.getAsJsonObject();
                                String id = text(skinObject, "id");
                                String typeId = text(skinObject, "type");
                                if (typeId.isEmpty())
                                {
                                    typeId = id;
                                }
                                String file = text(skinObject, "file");
                                ResourceLocation texture = null;
                                if (!file.isEmpty())
                                {
                                    texture = ResourceLocation.parse(AncientEmpires.MOD_ID + ":textures/entity/npc/" + file);
                                }
                                Entry entry = new Entry(id, typeId, text(skinObject, "name"), factionId, classId, texture);
                                skins.add(entry);
                                loaded.byId.put(id, entry);
                                loaded.byType.putIfAbsent(typeId, entry);
                            }
                        }
                        if (skins.size() > loaded.maxPool)
                        {
                            loaded.maxPool = skins.size();
                        }
                        classes.add(new NpcClass(classId, text(classObject, "name"), skins));
                    }
                }
                loaded.factions.add(new Faction(factionId, text(factionObject, "name"), classes));
            }
        }
        catch (IOException | RuntimeException exception)
        {
            AncientEmpires.LOGGER.error("Could not read the NPC skin catalog", exception);
        }
        return loaded;
    }

    private static String text(JsonObject object, String key)
    {
        if (object == null || !object.has(key) || object.get(key).isJsonNull())
        {
            return "";
        }
        return object.get(key).getAsString();
    }

    public static final class Faction
    {
        private final String id;
        private final String name;
        private final List<NpcClass> classes;

        private Faction(String id, String name, List<NpcClass> classes)
        {
            this.id = id;
            this.name = name;
            this.classes = List.copyOf(classes);
        }

        public String id()
        {
            return this.id;
        }

        public String name()
        {
            return this.name;
        }

        public List<NpcClass> classes()
        {
            return this.classes;
        }

        public int indexOfClass(String classId)
        {
            for (int index = 0; index < this.classes.size(); index++)
            {
                if (this.classes.get(index).id.equals(classId))
                {
                    return index;
                }
            }
            return 0;
        }

        @Nullable
        public NpcClass npcClass(String classId)
        {
            if (classId == null)
            {
                return null;
            }
            for (NpcClass npcClass : this.classes)
            {
                if (npcClass.id.equals(classId))
                {
                    return npcClass;
                }
            }
            return null;
        }
    }

    public static final class NpcClass
    {
        private final String id;
        private final String name;
        private final List<Entry> skins;

        private NpcClass(String id, String name, List<Entry> skins)
        {
            this.id = id;
            this.name = name;
            this.skins = List.copyOf(skins);
        }

        public String id()
        {
            return this.id;
        }

        public String name()
        {
            return this.name;
        }

        public List<Entry> skins()
        {
            return this.skins;
        }
    }

    public static final class Entry
    {
        private final String id;
        private final String typeId;
        private final String name;
        private final String factionId;
        private final String classId;
        @Nullable
        private final ResourceLocation texture;

        private Entry(String id, String typeId, String name, String factionId, String classId, @Nullable ResourceLocation texture)
        {
            this.id = id;
            this.typeId = typeId;
            this.name = name;
            this.factionId = factionId;
            this.classId = classId;
            this.texture = texture;
        }

        public String id()
        {
            return this.id;
        }

        public String name()
        {
            return this.name;
        }

        public String factionId()
        {
            return this.factionId;
        }

        public String classId()
        {
            return this.classId;
        }

        @Nullable
        public ResourceLocation texture()
        {
            return this.texture;
        }
    }

    private static final class Loaded
    {
        private final List<Faction> factions = new ArrayList<>();
        private final Map<String, Entry> byId = new HashMap<>();
        private final Map<String, Entry> byType = new HashMap<>();
        private int maxPool;
    }
}
