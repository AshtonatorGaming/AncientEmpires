package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    /** Every distinct skin of each type, for a random pick when an NPC is first placed. */
    private static final Map<String, List<Entry>> POOLS;
    private static final int MAX_POOL;

    static
    {
        Loaded loaded = load();
        FACTIONS = List.copyOf(loaded.factions);
        BY_ID = Map.copyOf(loaded.byId);
        BY_TYPE = Map.copyOf(loaded.byType);
        Map<String, List<Entry>> pools = new HashMap<>();
        loaded.pools.forEach((type, entries) -> pools.put(type, List.copyOf(entries)));
        POOLS = Map.copyOf(pools);
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
     * A pinned id, a removed duplicate such as {@code id~2}, or a type id.
     * A type id resolves to the first skin in that pool.
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
        int tilde = skinId.lastIndexOf('~');
        if (tilde > 0 && tilde + 1 < skinId.length())
        {
            boolean digits = true;
            for (int index = tilde + 1; index < skinId.length(); index++)
            {
                if (!Character.isDigit(skinId.charAt(index)))
                {
                    digits = false;
                    break;
                }
            }
            if (digits)
            {
                entry = BY_ID.get(skinId.substring(0, tilde));
                if (entry != null)
                {
                    return entry;
                }
            }
        }
        return BY_TYPE.get(skinId);
    }

    /**
     * A random skin of a type, pinned by its id, so a fresh NPC of a class does not
     * always wear the first of the many variants drawn for it. A pinned id, or a type
     * with one skin, comes back as it is.
     */
    public static String randomVariant(String skinId, RandomSource random)
    {
        List<Entry> pool = skinId == null ? null : POOLS.get(skinId);
        if (pool == null || pool.size() < 2)
        {
            return skinId;
        }
        return pool.get(random.nextInt(pool.size())).id();
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
            LinkedHashMap<String, ClassBuild> owned = new LinkedHashMap<>();
            for (JsonElement factionElement : root.getAsJsonArray("factions"))
            {
                JsonObject factionObject = factionElement.getAsJsonObject();
                String factionId = text(factionObject, "id");
                LinkedHashMap<String, ClassBuild> classes = new LinkedHashMap<>();
                if (factionObject.has("classes"))
                {
                    for (JsonElement classElement : factionObject.getAsJsonArray("classes"))
                    {
                        JsonObject classObject = classElement.getAsJsonObject();
                        String classId = text(classObject, "id");
                        String className = text(classObject, "name");
                        boolean ownedJob = "misc".equals(factionId) && isOwnedJob(classId);
                        boolean customPool = "misc".equals(factionId) && !ownedJob;
                        String storedFaction = ownedJob ? "owned" : factionId;
                        String storedClass = classId;
                        String storedName = className;
                        if (customPool)
                        {
                            storedClass = "custom";
                            storedName = "Custom";
                        }
                        else if (!ownedJob && isSpellcaster(classId))
                        {
                            storedClass = "spellcaster";
                            storedName = "Spellcaster";
                        }
                        if (ownedJob)
                        {
                            String renamed = ownedClassName(classId);
                            if (!renamed.isEmpty())
                            {
                                storedName = renamed;
                            }
                        }
                        String bucketName = storedName;
                        ClassBuild build = (ownedJob ? owned : classes).computeIfAbsent(storedClass, id -> new ClassBuild(id, bucketName));
                        if ("spellcaster".equals(storedClass))
                        {
                            build.name = "Spellcaster";
                        }
                        if (customPool)
                        {
                            build.name = "Custom";
                        }
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
                                ResourceLocation texture = skinTexture(file);
                                String name = text(skinObject, "name");
                                if (customPool)
                                {
                                    name = customSkinName(id, file, name);
                                }
                                Entry entry = new Entry(id, typeId, name, storedFaction, storedClass, texture);
                                loaded.byId.put(id, entry);
                                loaded.byType.putIfAbsent(typeId, entry);
                                if (build.listFile(file))
                                {
                                    build.skins.add(entry);
                                    loaded.pools.computeIfAbsent(typeId, key -> new ArrayList<>()).add(entry);
                                }
                            }
                        }
                    }
                }
                String factionName = text(factionObject, "name");
                List<NpcClass> finished = finishClasses(classes, loaded);
                if ("misc".equals(factionId))
                {
                    if (finished.isEmpty())
                    {
                        continue;
                    }
                    factionName = "Custom";
                }
                loaded.factions.add(new Faction(factionId, factionName, finished));
            }
            if (!owned.isEmpty())
            {
                loaded.factions.add(0, new Faction("owned", "Player Default", finishOwned(owned, loaded)));
            }
        }
        catch (IOException | RuntimeException exception)
        {
            AncientEmpires.LOGGER.error("Could not read the NPC skin catalog", exception);
        }
        return loaded;
    }

    /**
     * Texture paths must be lowercase. A bad name used to abort the rest of the
     * catalog, which dropped the Custom tab.
     */
    @Nullable
    private static ResourceLocation skinTexture(String file)
    {
        if (file == null || file.isEmpty())
        {
            return null;
        }
        String path = "textures/entity/npc/" + legalPath(file);
        if (!ResourceLocation.isValidPath(path))
        {
            AncientEmpires.LOGGER.warn("Skipping NPC skin texture {}", file);
            return null;
        }
        return ResourceLocation.parse(AncientEmpires.MOD_ID + ":" + path);
    }

    /**
     * Custom rows were numbered "Extra Custom N". The readable name is the
     * original file stem, with the pack prefix removed.
     */
    private static String customSkinName(String id, String file, String fallback)
    {
        String stem = id;
        int hash = id.lastIndexOf('#');
        if (hash >= 0 && hash + 1 < id.length())
        {
            stem = id.substring(hash + 1);
        }
        else if (file != null && !file.isEmpty())
        {
            stem = file;
            if (stem.toLowerCase(Locale.ROOT).endsWith(".png"))
            {
                stem = stem.substring(0, stem.length() - 4);
            }
        }
        if (stem.toLowerCase(Locale.ROOT).startsWith("custom_"))
        {
            stem = stem.substring("custom_".length());
        }
        String pretty = prettyStem(stem);
        if (pretty.isEmpty())
        {
            return fallback;
        }
        return pretty;
    }

    private static String prettyStem(String stem)
    {
        String spaced = stem.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        spaced = spaced.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        spaced = spaced.replace('_', ' ').replace('.', ' ');
        spaced = spaced.replaceAll("([A-Za-z])(\\d)", "$1 $2");
        spaced = spaced.replaceAll("(\\d)([A-Za-z])", "$1 $2");
        StringBuilder builder = new StringBuilder(spaced.length());
        int index = 0;
        while (index < spaced.length())
        {
            while (index < spaced.length() && spaced.charAt(index) == ' ')
            {
                index++;
            }
            if (index >= spaced.length())
            {
                break;
            }
            int end = index;
            while (end < spaced.length() && spaced.charAt(end) != ' ')
            {
                end++;
            }
            if (builder.length() > 0)
            {
                builder.append(' ');
            }
            builder.append(prettyWord(spaced.substring(index, end)));
            index = end;
        }
        return builder.toString();
    }

    private static String prettyWord(String word)
    {
        int letters = 0;
        boolean allCaps = true;
        for (int index = 0; index < word.length(); index++)
        {
            char character = word.charAt(index);
            if (Character.isLetter(character))
            {
                letters++;
                if (!Character.isUpperCase(character))
                {
                    allCaps = false;
                }
            }
        }
        if (letters >= 2 && allCaps)
        {
            return word;
        }
        if (word.length() <= 1)
        {
            return word.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String legalPath(String file)
    {
        String lower = file.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        for (int index = 0; index < lower.length(); index++)
        {
            char character = lower.charAt(index);
            if (ResourceLocation.validPathChar(character))
            {
                builder.append(character);
            }
            else
            {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    private static boolean isSpellcaster(String classId)
    {
        return "spellcaster".equals(classId) || classId.startsWith("spellcaster.");
    }

    private static boolean isOwnedJob(String classId)
    {
        return "bard".equals(classId)
                || "combat".equals(classId)
                || classId.startsWith("combat.")
                || "courier".equals(classId)
                || "priest".equals(classId)
                || "siege_engineer".equals(classId)
                || "trader".equals(classId)
                || "worker".equals(classId)
                || classId.startsWith("worker.");
    }

    private static String ownedClassName(String classId)
    {
        if ("worker".equals(classId))
        {
            return "Worker";
        }
        if ("combat".equals(classId))
        {
            return "Combat";
        }
        if ("worker.lumberjack".equals(classId))
        {
            return "Woodcutter";
        }
        if ("worker.miner".equals(classId))
        {
            return "Miner";
        }
        if ("worker.farmer".equals(classId))
        {
            return "Farmer";
        }
        if ("worker.craftsman".equals(classId))
        {
            return "Craftsman";
        }
        if ("worker.researcher".equals(classId))
        {
            return "Researcher";
        }
        if ("combat.medic".equals(classId))
        {
            return "Medic";
        }
        if ("combat.engineer".equals(classId))
        {
            return "Engineer";
        }
        if ("combat.archer".equals(classId))
        {
            return "Archer";
        }
        if ("combat.commander".equals(classId))
        {
            return "Commander";
        }
        if ("combat.soldier".equals(classId))
        {
            return "Soldier";
        }
        if ("siege_engineer".equals(classId))
        {
            return "Siege Engineer";
        }
        return "";
    }

    private static List<NpcClass> finishClasses(LinkedHashMap<String, ClassBuild> classes, Loaded loaded)
    {
        List<NpcClass> finished = new ArrayList<>();
        for (ClassBuild build : classes.values())
        {
            if (!"custom".equals(build.id) && build.skins.size() > loaded.maxPool)
            {
                loaded.maxPool = build.skins.size();
            }
            finished.add(build.toClass());
        }
        return finished;
    }

    private static List<NpcClass> finishOwned(LinkedHashMap<String, ClassBuild> owned, Loaded loaded)
    {
        String[] order = {
                "worker",
                "combat",
                "worker.lumberjack",
                "worker.miner",
                "worker.farmer",
                "worker.craftsman",
                "worker.researcher",
                "courier",
                "trader",
                "priest",
                "bard",
                "siege_engineer",
                "combat.medic",
                "combat.engineer",
                "combat.archer",
                "combat.commander",
                "combat.soldier"
        };
        List<NpcClass> finished = new ArrayList<>();
        for (String classId : order)
        {
            ClassBuild build = owned.remove(classId);
            if (build == null)
            {
                continue;
            }
            if (build.skins.size() > loaded.maxPool)
            {
                loaded.maxPool = build.skins.size();
            }
            finished.add(build.toClass());
        }
        for (ClassBuild build : owned.values())
        {
            if (build.skins.size() > loaded.maxPool)
            {
                loaded.maxPool = build.skins.size();
            }
            finished.add(build.toClass());
        }
        return finished;
    }

    private static final class ClassBuild
    {
        private final String id;
        private String name;
        private final List<Entry> skins = new ArrayList<>();
        private final Set<String> files = new HashSet<>();

        private ClassBuild(String id, String name)
        {
            this.id = id;
            this.name = name;
        }

        /**
         * @return whether this texture should appear in the list
         */
        private boolean listFile(String file)
        {
            if (file == null || file.isEmpty())
            {
                return true;
            }
            String key = legalPath(file);
            if (key.isEmpty())
            {
                return true;
            }
            return this.files.add(key);
        }

        private NpcClass toClass()
        {
            return new NpcClass(this.id, this.name, this.skins);
        }
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

        /**
         * The custom tab is one skin list. Real factions keep their classes.
         */
        public boolean flat()
        {
            return "misc".equals(this.id);
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
        private final Map<String, List<Entry>> pools = new HashMap<>();
        private int maxPool;
    }
}
