package com.ancientempires.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns an AW 1.12 loot table into a current one. Vanilla items go through
 * {@link LegacyFixer} with their {@code set_data} as the 1.12 damage value
 * (wool 14 is red wool now); AW's coins become our coins by their
 * {@code metal}; every other item (AW's own, other mods') becomes an
 * {@code ancientempires:item} entry, resolved when rolled through the
 * {@code ae_remap} item tables, so a missing mod drops one entry and not the
 * whole table. Functions and conditions 1.20 reads differently
 * ({@code set_data}, {@code set_attributes}, {@code entity_properties}) are
 * dropped. References to AW's tables point at the converted ones under
 * {@code ancientempires:aw/}.
 */
public final class AwsLootConverter
{
    /** Where AW's tables land: {@code ancientwarfare:chests/x} is {@code ancientempires:aw/chests/x}. */
    public static final String PREFIX = "ancientempires:aw/";

    private final LegacyFixer fixer;

    public AwsLootConverter(LegacyFixer fixer)
    {
        this.fixer = fixer;
    }

    /** An AW table id as ours; vanilla ids are kept, and {@code minecraft:} alone (AW's "none") is empty. */
    public static String tableId(String awId)
    {
        if (awId.isEmpty() || awId.equals("minecraft:"))
        {
            return "";
        }
        return awId.startsWith("ancientwarfare:") ? PREFIX + awId.substring("ancientwarfare:".length()) : awId;
    }

    public JsonObject convert(JsonObject old, String type)
    {
        JsonObject table = new JsonObject();
        table.addProperty("type", type);
        JsonArray pools = new JsonArray();
        for (JsonElement poolElement : old.getAsJsonArray("pools"))
        {
            JsonObject oldPool = poolElement.getAsJsonObject();
            JsonObject pool = new JsonObject();
            pool.add("rolls", oldPool.get("rolls"));
            if (oldPool.has("bonus_rolls"))
            {
                pool.add("bonus_rolls", oldPool.get("bonus_rolls"));
            }
            // An entry with a random 1.12 data value (wool of any colour) becomes one entry per value;
            // the pool's other weights are scaled to match, so the odds stay as they were.
            List<List<JsonObject>> groups = new ArrayList<>();
            int scale = 1;
            for (JsonElement entry : oldPool.getAsJsonArray("entries"))
            {
                List<JsonObject> converted = this.entries(entry.getAsJsonObject());
                if (!converted.isEmpty())
                {
                    groups.add(converted);
                    scale = lcm(scale, converted.size());
                }
            }
            JsonArray entries = new JsonArray();
            for (List<JsonObject> group : groups)
            {
                int share = scale / group.size();
                for (JsonObject entry : group)
                {
                    if (share > 1)
                    {
                        entry.addProperty("weight", (entry.has("weight") ? entry.get("weight").getAsInt() : 1) * share);
                    }
                    entries.add(entry);
                }
            }
            pool.add("entries", entries);
            JsonArray conditions = conditions(oldPool);
            if (!conditions.isEmpty())
            {
                pool.add("conditions", conditions);
            }
            pools.add(pool);
        }
        table.add("pools", pools);
        return table;
    }

    private static int lcm(int a, int b)
    {
        int x = a;
        int y = b;
        while (y != 0)
        {
            int t = x % y;
            x = y;
            y = t;
        }
        return a / x * b;
    }

    /** One AW entry as one or more: a random data range gives one per value. */
    private List<JsonObject> entries(JsonObject old)
    {
        int[] range = dataRange(old);
        List<JsonObject> out = new ArrayList<>();
        for (int data = range[0]; data <= range[1]; data++)
        {
            JsonObject one = old.deepCopy();
            if (range[1] > range[0])
            {
                for (JsonElement function : one.getAsJsonArray("functions"))
                {
                    if (withNamespace(function.getAsJsonObject().get("function").getAsString()).equals("minecraft:set_data"))
                    {
                        function.getAsJsonObject().addProperty("data", data);
                    }
                }
            }
            JsonObject converted = this.entry(one);
            if (converted != null)
            {
                out.add(converted);
            }
        }
        return out;
    }

    /** The 1.12 data values an entry may take: one, or a range from a random {@code set_data}. */
    private static int[] dataRange(JsonObject old)
    {
        if (old.has("functions"))
        {
            for (JsonElement element : old.getAsJsonArray("functions"))
            {
                JsonObject function = element.getAsJsonObject();
                if (withNamespace(function.get("function").getAsString()).equals("minecraft:set_data") && function.get("data").isJsonObject())
                {
                    JsonObject data = function.getAsJsonObject("data");
                    int min = data.get("min").getAsInt();
                    return new int[] {min, Math.max(min, data.get("max").getAsInt())};
                }
            }
        }
        return new int[] {0, 0};
    }

    private JsonObject entry(JsonObject old)
    {
        String type = withNamespace(old.has("type") ? old.get("type").getAsString() : "item");
        JsonObject entry = new JsonObject();
        if (old.has("weight"))
        {
            entry.add("weight", old.get("weight"));
        }
        if (old.has("quality"))
        {
            entry.add("quality", old.get("quality"));
        }
        JsonArray conditions = conditions(old);
        if (!conditions.isEmpty())
        {
            entry.add("conditions", conditions);
        }
        switch (type)
        {
            case "minecraft:empty" ->
            {
                entry.addProperty("type", "minecraft:empty");
                return entry;
            }
            case "minecraft:loot_table" ->
            {
                String reference = tableId(withNamespace(old.get("name").getAsString()));
                if (reference.isEmpty())
                {
                    return null;
                }
                entry.addProperty("type", "minecraft:loot_table");
                entry.addProperty("name", reference);
                return entry;
            }
            case "minecraft:item" ->
            {
            }
            default ->
            {
                return null;
            }
        }
        String name = withNamespace(old.get("name").getAsString());
        int data = 0;
        String metal = null;
        JsonArray functions = new JsonArray();
        if (old.has("functions"))
        {
            for (JsonElement element : old.getAsJsonArray("functions"))
            {
                JsonObject function = element.getAsJsonObject().deepCopy();
                String id = withNamespace(function.get("function").getAsString());
                function.addProperty("function", id);
                switch (id)
                {
                    case "minecraft:set_data" -> data = function.get("data").getAsInt();
                    case "minecraft:set_attributes" ->
                    {
                        // 1.12's attribute names; dropped rather than guessed.
                    }
                    case "minecraft:set_nbt" ->
                    {
                        String tag = function.get("tag").getAsString();
                        String found = metalOf(tag);
                        if (found != null && name.equals("ancientwarfarenpc:coin"))
                        {
                            metal = found;
                        }
                        else
                        {
                            functions.add(function);
                        }
                    }
                    default -> functions.add(function);
                }
            }
        }
        if (name.equals("ancientwarfarenpc:coin"))
        {
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", "ancientempires:coin_" + (metal == null ? "copper" : metal));
        }
        else if (name.startsWith("minecraft:"))
        {
            CompoundTag stack = new CompoundTag();
            stack.putString("id", name);
            stack.putByte("Count", (byte) 1);
            stack.putShort("Damage", (short) data);
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", this.fixer.item(stack).getString("id"));
        }
        else
        {
            entry.addProperty("type", "ancientempires:item");
            entry.addProperty("name", name);
        }
        if (!functions.isEmpty())
        {
            entry.add("functions", functions);
        }
        return entry;
    }

    private static JsonArray conditions(JsonObject old)
    {
        JsonArray conditions = new JsonArray();
        if (old.has("conditions"))
        {
            for (JsonElement element : old.getAsJsonArray("conditions"))
            {
                JsonObject condition = element.getAsJsonObject().deepCopy();
                String id = withNamespace(condition.get("condition").getAsString());
                if (id.equals("minecraft:entity_properties"))
                {
                    continue;
                }
                condition.addProperty("condition", id);
                conditions.add(condition);
            }
        }
        return conditions;
    }

    /** AW's coin metal from a {@code set_nbt} tag ({@code {metal:"gold"}}), or null. */
    static String metalOf(String tag)
    {
        try
        {
            CompoundTag parsed = TagParser.parseTag(tag);
            String metal = parsed.getString("metal");
            return metal.isEmpty() ? null : metal;
        }
        catch (CommandSyntaxException exception)
        {
            return null;
        }
    }

    private static String withNamespace(String id)
    {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }
}
