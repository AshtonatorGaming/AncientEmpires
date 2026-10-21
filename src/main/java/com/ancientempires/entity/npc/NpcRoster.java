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
 * Placeable faction NPCs loaded from {@code roster.json}. One entry is an old
 * Ancient Warfare faction and subtype: name, skin, hands, and any scale or health.
 */
public final class NpcRoster
{
    private static final String RESOURCE = "/assets/ancientempires/npc/roster.json";
    private static final String SPARTAN = "spartanweaponry";

    private static final List<Entry> ENTRIES;
    private static final Map<String, Entry> BY_ID;

    static
    {
        List<Entry> loaded = load();
        Map<String, Entry> byId = new HashMap<>();
        for (Entry entry : loaded)
        {
            byId.put(entry.id, entry);
        }
        ENTRIES = List.copyOf(loaded);
        BY_ID = Map.copyOf(byId);
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
                    JsonObject object = entryElement.getAsJsonObject();
                    entries.add(new Entry(
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
                            health(object)));
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
     * One old faction subtype. Spawning it does not claim the player who placed the egg.
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

        private Entry(String id, String faction, String subtype, String name, String skin, String itemId, String mainhand, String mainFallback, String offhand, String offFallback, float scale, double health)
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

        /**
         * Faction, skin, display name, empty-hand gear, and any stored scale or health.
         */
        public void apply(NpcEntity npc)
        {
            npc.assignFaction(this.faction);
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
