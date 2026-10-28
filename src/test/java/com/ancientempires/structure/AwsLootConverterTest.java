package com.ancientempires.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AwsLootConverterTest
{
    private static final String TABLE = """
            {"pools": [{"name": "main", "rolls": {"min": 1, "max": 3}, "entries": [
              {"type": "item", "name": "wool", "weight": 2, "functions": [{"function": "set_data", "data": 14}, {"function": "set_count", "count": 4}]},
              {"type": "item", "name": "ancientwarfarenpc:coin", "entryName": "coin_gold",
               "functions": [{"function": "minecraft:set_nbt", "tag": "{metal:\\"gold\\"}"}]},
              {"type": "item", "name": "ancientwarfarenpc:iron_spear"},
              {"type": "item", "name": "harvestcraft:beetsoupitem", "conditions": [{"condition": "entity_properties", "entity": "this"}]},
              {"type": "loot_table", "name": "ancientwarfare:chests/gold_nuggets"},
              {"type": "empty", "weight": 3}
            ]}]}
            """;

    private static JsonArray entries()
    {
        JsonObject table = new AwsLootConverter(new LegacyFixer()).convert(JsonParser.parseString(TABLE).getAsJsonObject(), "minecraft:chest");
        assertEquals("minecraft:chest", table.get("type").getAsString());
        return table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
    }

    @Test
    void flattensVanillaItemsByTheirOldData()
    {
        JsonObject wool = entries().get(0).getAsJsonObject();
        assertEquals("minecraft:item", wool.get("type").getAsString());
        assertEquals("minecraft:red_wool", wool.get("name").getAsString());
        assertEquals(1, wool.getAsJsonArray("functions").size(), "set_data is gone, set_count stays");
        assertEquals("minecraft:set_count", wool.getAsJsonArray("functions").get(0).getAsJsonObject().get("function").getAsString());
    }

    @Test
    void coinsBecomeOurCoinsByMetal()
    {
        JsonObject coin = entries().get(1).getAsJsonObject();
        assertEquals("ancientempires:coin_gold", coin.get("name").getAsString());
        assertFalse(coin.has("functions"), "the metal tag is spent");
    }

    @Test
    void otherItemsResolveWhenRolledAndReferencesPointAtOurCopies()
    {
        JsonArray entries = entries();
        assertEquals("ancientempires:item", entries.get(2).getAsJsonObject().get("type").getAsString());
        assertEquals("ancientwarfarenpc:iron_spear", entries.get(2).getAsJsonObject().get("name").getAsString());
        assertFalse(entries.get(3).getAsJsonObject().has("conditions"), "1.12 entity_properties is dropped");
        assertEquals("ancientempires:aw/chests/gold_nuggets", entries.get(4).getAsJsonObject().get("name").getAsString());
        assertEquals("minecraft:empty", entries.get(5).getAsJsonObject().get("type").getAsString());
        assertEquals("", AwsLootConverter.tableId("minecraft:"));
    }
}
