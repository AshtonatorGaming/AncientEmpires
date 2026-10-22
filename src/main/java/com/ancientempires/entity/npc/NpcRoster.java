package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
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
 * Placeable NPCs loaded from {@code roster.json}. Faction entries keep the old
 * Ancient Warfare name, skin, hands, scale, and health, and they do not claim
 * a player. Owned entries are the job placeholders and do claim the placer.
 */
public final class NpcRoster
{
    private static final String RESOURCE = "/assets/ancientempires/npc/roster.json";
    private static final String SPARTAN = "spartanweaponry";

    private static final List<Entry> ENTRIES;
    private static final Map<String, Entry> BY_ID;
    private static final Map<String, Integer> FACTION_COLORS = new HashMap<>();

    static
    {
        putColor("empire", 0x800080);
        putColor("norska", 0x9999FF);
        putColor("sarkonid", 0xB20000);
        putColor("xoltec", 0xFFA500);
        putColor("witchbane", 0xFFFFFF);
        putColor("nogg", 0x00CD00);
        putColor("buffloka", 0x008000);
        putColor("reiksgard", 0x22CD22);
        putColor("zimba", 0xFFFF00);
        putColor("kong", 0x004000);
        putColor("orc", 0x404040);
        putColor("brigand", 0x964B00);
        putColor("pirate", 0xCC7722);
        putColor("evil", 0x000000);
        putColor("good", 0x66B266);
        putColor("undead", 0xB2B2B2);
        putColor("demon", 0xFF2400);
        putColor("coven", 0x000000);
        putColor("elf", 0x66B266);
        putColor("barbarian", 0x802B00);
        putColor("dwarf", 0x77B266);
        putColor("shakayana", 0x88B266);
        putColor("smingol", 0xFFD700);
        putColor("lizardman", 0x576500);
        putColor("mindflayer", 0x777500);
        putColor("zamurai", 0x376500);
        putColor("rakshasa", 0xD7BF00);
        putColor("klown", 0xFFC0CB);
        putColor("ishtari", 0xFED000);
        putColor("icelord", 0x3C9F9C);
        putColor("guild", 0x007CC9);
        putColor("wizardly", 0xAA3FD8);
        putColor("amazon", 0xD34848);
        putColor("minossian", 0x456689);
        putColor("sealsker", 0x6666FF);
        putColor("vyncan", 0x6666FF);
        putColor("hobbit", 0x6666FF);
        putColor("monster", 0xFFC0CB);
        putColor("beast", 0xFFC0CB);
        putColor("ent", 0x6666FF);
        putColor("gnome", 0x6666FF);
        putColor("gremlin", 0xFFC0CB);
        putColor("vampire", 0x320000);
        putColor("giant", 0xFFC0CB);
        List<Entry> loaded = load();
        Map<String, Entry> byId = new HashMap<>();
        for (Entry entry : loaded)
        {
            byId.put(entry.id, entry);
        }
        ENTRIES = List.copyOf(loaded);
        BY_ID = Map.copyOf(byId);
    }

    private static void putColor(String factionId, int rgb)
    {
        FACTION_COLORS.put(factionId, 0xFF000000 | (rgb & 0xFFFFFF));
    }

    private NpcRoster()
    {
    }

    public static List<Entry> entries()
    {
        return ENTRIES;
    }

    @Nullable
    public static Entry byId(String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }
        return BY_ID.get(id);
    }

    /**
     * Best guess at the faction row an NPC came from, for NPCs spawned before the
     * row id was saved. A skin such as {@code norska.leader.elite#norska_blood_jarl_1}
     * belongs to the row whose skin is {@code norska.leader.elite}.
     */
    @Nullable
    public static Entry guess(String faction, String skinId)
    {
        if (faction == null || faction.isEmpty() || skinId == null || skinId.isEmpty())
        {
            return null;
        }
        int hash = skinId.indexOf('#');
        String type = hash > 0 ? skinId.substring(0, hash) : skinId;
        for (Entry entry : ENTRIES)
        {
            if (!entry.owned() && entry.faction().equals(faction) && entry.skin().equals(type))
            {
                return entry;
            }
        }
        return null;
    }

    private static List<Entry> load()
    {
        InputStream stream = NpcRoster.class.getResourceAsStream(RESOURCE);
        if (stream == null)
        {
            AncientEmpires.LOGGER.error("Missing NPC roster {}", RESOURCE);
            return List.of();
        }
        try (InputStream in = stream; Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
        {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Entry> entries = new ArrayList<>();
            if (root.has("owned"))
            {
                for (JsonElement element : root.getAsJsonArray("owned"))
                {
                    entries.add(readEntry(element.getAsJsonObject(), "", true));
                }
            }
            if (root.has("factions"))
            {
                for (JsonElement factionElement : root.getAsJsonArray("factions"))
                {
                    JsonObject faction = factionElement.getAsJsonObject();
                    String factionId = text(faction, "id");
                    if (!faction.has("entries"))
                    {
                        continue;
                    }
                    for (JsonElement entryElement : faction.getAsJsonArray("entries"))
                    {
                        entries.add(readEntry(entryElement.getAsJsonObject(), factionId, false));
                    }
                }
            }
            return entries;
        }
        catch (IOException | RuntimeException exception)
        {
            AncientEmpires.LOGGER.error("Could not read the NPC roster", exception);
            return List.of();
        }
    }

    private static Entry readEntry(JsonObject object, String factionId, boolean owned)
    {
        int color = owned ? 0xFFFFFFFF : factionColor(factionId);
        return new Entry(
                text(object, "id"),
                factionId,
                text(object, "subtype"),
                text(object, "name"),
                text(object, "skin"),
                text(object, "item"),
                text(object, "mainhand"),
                text(object, "mainFallback"),
                text(object, "offhand"),
                text(object, "offFallback"),
                decimal(object, "scale"),
                health(object),
                owned,
                color);
    }

    private static int factionColor(String factionId)
    {
        Integer color = FACTION_COLORS.get(factionId);
        if (color == null)
        {
            return 0xFFFFFFFF;
        }
        return color;
    }

    private static String text(JsonObject object, String key)
    {
        if (object == null || !object.has(key) || object.get(key).isJsonNull())
        {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static float decimal(JsonObject object, String key)
    {
        if (object == null || !object.has(key) || object.get(key).isJsonNull())
        {
            return 0.0F;
        }
        return object.get(key).getAsFloat();
    }

    private static double health(JsonObject object)
    {
        if (object == null || !object.has("health") || object.get("health").isJsonNull())
        {
            return 0.0D;
        }
        return object.get("health").getAsDouble();
    }

    /**
     * One roster row. A faction row does not claim the player who placed the egg.
     * An owned row is a job placeholder and does claim that player.
     */
    public static final class Entry
    {
        private final String id;
        private final String faction;
        private final String subtype;
        private final String name;
        private final String skin;
        private final String itemId;
        private final String mainhand;
        private final String mainFallback;
        private final String offhand;
        private final String offFallback;
        private final float scale;
        private final double health;
        private final boolean owned;
        private final int color;

        private Entry(String id, String faction, String subtype, String name, String skin, String itemId, String mainhand, String mainFallback, String offhand, String offFallback, float scale, double health, boolean owned, int color)
        {
            this.id = id;
            this.faction = faction;
            this.subtype = subtype;
            this.name = name;
            this.skin = skin.isEmpty() ? id : skin;
            this.itemId = itemId;
            this.mainhand = mainhand;
            this.mainFallback = mainFallback;
            this.offhand = offhand;
            this.offFallback = offFallback;
            this.scale = scale;
            this.health = health;
            this.owned = owned;
            this.color = color;
        }

        public String id()
        {
            return this.id;
        }

        public String name()
        {
            return this.name;
        }

        public String itemId()
        {
            return this.itemId;
        }

        public String subtype()
        {
            return this.subtype;
        }

        public String faction()
        {
            return this.faction;
        }

        /** The skin id this row assigns, usually a catalog type such as {@code norska.leader}. */
        public String skin()
        {
            return this.skin;
        }

        public boolean owned()
        {
            return this.owned;
        }

        /**
         * Opaque RGB for the scroll overlay. Owned eggs stay white.
         */
        public int color()
        {
            return this.color;
        }

        /**
         * Skin, display name, empty-hand gear, and any stored scale or health.
         * Faction rows also set the faction, which clears the owner.
         */
        public void apply(NpcEntity npc)
        {
            if (!this.owned)
            {
                npc.assignFaction(this.faction);
            }
            npc.assignSkin(this.skin);
            if (!this.name.isEmpty())
            {
                String shown = this.name;
                if (shown.length() > 64)
                {
                    shown = shown.substring(0, 64);
                }
                npc.setCustomName(Component.literal(shown));
                npc.setCustomNameVisible(false);
            }
            if (this.scale > 0.0F)
            {
                npc.setBodyScale(this.scale);
            }
            if (this.health > 0.0D)
            {
                AttributeInstance attribute = npc.getAttribute(Attributes.MAX_HEALTH);
                if (attribute != null)
                {
                    attribute.setBaseValue(this.health);
                }
            }
            this.equip(npc, EquipmentSlot.MAINHAND, this.mainhand, this.mainFallback);
            this.equip(npc, EquipmentSlot.OFFHAND, this.offhand, this.offFallback);
            if (this.health > 0.0D)
            {
                npc.setHealth(npc.getMaxHealth());
            }
        }

        private void equip(NpcEntity npc, EquipmentSlot slot, String preferred, String fallback)
        {
            if (!npc.getItemBySlot(slot).isEmpty())
            {
                return;
            }
            ItemStack stack = stack(preferred, fallback);
            if (!stack.isEmpty())
            {
                npc.setItemSlot(slot, stack);
            }
        }

        private static ItemStack stack(String preferred, String fallback)
        {
            Item item = resolve(preferred);
            if (item == null)
            {
                item = resolve(fallback);
            }
            if (item == null)
            {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        }

        @Nullable
        private static Item resolve(String id)
        {
            if (id == null || id.isEmpty() || "minecraft:air".equals(id))
            {
                return null;
            }
            ResourceLocation location;
            try
            {
                location = ResourceLocation.parse(id);
            }
            catch (IllegalArgumentException exception)
            {
                return null;
            }
            if (SPARTAN.equals(location.getNamespace()) && !ModList.get().isLoaded(SPARTAN))
            {
                return null;
            }
            if (!ForgeRegistries.ITEMS.containsKey(location))
            {
                return null;
            }
            Item item = ForgeRegistries.ITEMS.getValue(location);
            if (item == null || item == Items.AIR)
            {
                return null;
            }
            return item;
        }
    }
}
