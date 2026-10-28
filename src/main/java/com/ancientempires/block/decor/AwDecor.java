package com.ancientempires.block.decor;

import com.ancientempires.AncientEmpires;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ancient Warfare's decor blocks (chairs, thrones, tables, gravestones, totems,
 * braziers, altars, cages...), described by {@code aw_decor/blocks.json}: each
 * block's state properties and its collision boxes per state. That file, the
 * blockstates, and the models are generated from AW's own 1.12 assets (its Forge
 * blockstates turned into vanilla multipart ones), so the look is AW's. The table
 * is read from the jar at registration, on the server too.
 */
public final class AwDecor
{
    /** One decor block: its properties (value lists, in AW's order) and boxes in pixels per state ({@code "facing=north,visible=true"}). */
    public record Spec(String name, Map<String, List<String>> properties, Map<String, List<double[]>> shapes)
    {
    }

    private static final Map<String, Spec> SPECS = load();
    private static final Set<String> METAL = Set.of("golden_idol", "golden_throne", "iron_cage", "gibbet", "brazier_ember", "brazier_flame", "altar_sun");
    private static final Set<String> STONE = Set.of("gravestone", "altar_long_cloth", "altar_short_cloth", "altar_lectern", "fire_pit");

    private AwDecor()
    {
    }

    public static Map<String, Spec> specs()
    {
        return SPECS;
    }

    /** Block settings by AW's material: metal, stone, or wood; lit things glow. */
    public static BlockBehaviour.Properties properties(String name)
    {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().noOcclusion().lightLevel(state -> light(name, state));
        if (METAL.contains(name))
        {
            return properties.mapColor(MapColor.GOLD).strength(3.0F, 6.0F).sound(SoundType.METAL);
        }
        if (STONE.contains(name))
        {
            return properties.mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE);
        }
        return properties.mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava();
    }

    private static int light(String name, BlockState state)
    {
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT))
        {
            return 0;
        }
        if (name.equals("brazier_ember"))
        {
            return 10;
        }
        if (state.hasProperty(BlockStateProperties.LIT) || name.endsWith("_lit"))
        {
            return 15;
        }
        for (var property : state.getProperties())
        {
            if (property.getName().equals("on_fire") && state.getValue(property).equals(Boolean.TRUE))
            {
                return 15;
            }
        }
        return name.equals("altar_candle") ? 12 : 0;
    }

    private static Map<String, Spec> load()
    {
        Map<String, Spec> specs = new LinkedHashMap<>();
        try (InputStream stream = AwDecor.class.getResourceAsStream("/assets/" + AncientEmpires.MOD_ID + "/aw_decor/blocks.json"))
        {
            if (stream == null)
            {
                AncientEmpires.LOGGER.error("aw_decor/blocks.json is missing; no decor blocks");
                return specs;
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet())
            {
                JsonObject block = entry.getValue().getAsJsonObject();
                Map<String, List<String>> properties = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> property : block.getAsJsonObject("properties").entrySet())
                {
                    List<String> values = new ArrayList<>();
                    property.getValue().getAsJsonArray().forEach(value -> values.add(value.getAsString()));
                    properties.put(property.getKey(), List.copyOf(values));
                }
                Map<String, List<double[]>> shapes = new HashMap<>();
                for (Map.Entry<String, JsonElement> shape : block.getAsJsonObject("shapes").entrySet())
                {
                    List<double[]> boxes = new ArrayList<>();
                    for (JsonElement box : shape.getValue().getAsJsonArray())
                    {
                        JsonArray array = box.getAsJsonArray();
                        double[] values = new double[6];
                        for (int i = 0; i < 6; i++)
                        {
                            values[i] = array.get(i).getAsDouble();
                        }
                        boxes.add(values);
                    }
                    shapes.put(shape.getKey(), boxes);
                }
                specs.put(entry.getKey(), new Spec(entry.getKey(), Collections.unmodifiableMap(properties), shapes));
            }
        }
        catch (Exception exception)
        {
            AncientEmpires.LOGGER.error("Could not read aw_decor/blocks.json", exception);
        }
        return Collections.unmodifiableMap(specs);
    }
}
