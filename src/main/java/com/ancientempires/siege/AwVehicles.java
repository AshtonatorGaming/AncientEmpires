package com.ancientempires.siege;

import com.ancientempires.AncientEmpires;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * AW's siege engines as data ({@code aw_vehicles/models.json}, made from AW's
 * own model classes): per vehicle type (AW's {@code vehType} numbers) its model,
 * texture, and scale, and per model its part tree, turret parts, and rough size.
 * Read from the jar, so the server knows sizes too.
 */
public final class AwVehicles
{
    /** One of AW's vehicle types. */
    public record Type(int id, String model, String texture, float scale, float width, float height)
    {
    }

    private static final JsonObject DATA = load();
    private static final Type FALLBACK = new Type(5, "BallistaStand", "ballista_stand", 1.0F, 1.5F, 1.5F);

    private AwVehicles()
    {
    }

    public static Type type(int id)
    {
        JsonObject types = DATA.getAsJsonObject("types");
        if (types == null || !types.has(Integer.toString(id)))
        {
            return FALLBACK;
        }
        var entry = types.getAsJsonArray(Integer.toString(id));
        String model = entry.get(0).getAsString();
        float scale = entry.get(2).getAsFloat();
        var size = DATA.getAsJsonObject("models").getAsJsonObject(model).getAsJsonArray("size");
        return new Type(id, model, entry.get(1).getAsString(), scale, Math.min(2.0F, size.get(0).getAsFloat()) * scale,
                Math.max(0.5F, size.get(1).getAsFloat()) * scale);
    }

    /** A model's part tree as written by the converter. */
    public static JsonObject model(String name)
    {
        return DATA.getAsJsonObject("models").getAsJsonObject(name);
    }

    public static Map<String, JsonElement> models()
    {
        return DATA.getAsJsonObject("models").asMap();
    }

    private static JsonObject load()
    {
        try (InputStream stream = AwVehicles.class.getResourceAsStream("/assets/" + AncientEmpires.MOD_ID + "/aw_vehicles/models.json"))
        {
            if (stream != null)
            {
                return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        catch (Exception exception)
        {
            AncientEmpires.LOGGER.error("Could not read aw_vehicles/models.json", exception);
        }
        JsonObject empty = new JsonObject();
        empty.add("types", new JsonObject());
        empty.add("models", new JsonObject());
        return empty;
    }
}
