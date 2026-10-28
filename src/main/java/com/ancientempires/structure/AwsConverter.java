package com.ancientempires.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns one AW template into an {@link AeTemplate} and its sidecar (AW's header
 * and validation, upgraded): vanilla blocks, block entities, items, entities,
 * and biomes through {@link LegacyFixer}; AW's own blocks and other mods'
 * content kept as written, with AW's whole rule saved in the block entity under
 * {@code aeRule}, for placing to resolve (stand-ins now, real blocks later).
 * Rule 0 becomes air, or void when the template preserves blocks, as AW built it.
 */
public final class AwsConverter
{
    /** The result: the template, its sidecar, and what went wrong or was kept for later. */
    public record Result(AeTemplate template, JsonObject sidecar, List<String> problems, Map<String, Integer> kept)
    {
    }

    private static final Map<String, String> DIMENSIONS = Map.of("0", "minecraft:overworld", "-1", "minecraft:the_nether", "1", "minecraft:the_end");
    /** Entity data AW saved from the world that would misplace or duplicate the entity. */
    private static final String[] WORLD_ONLY = {"Pos", "Motion", "UUID", "UUIDMost", "UUIDLeast", "Dimension", "PortalCooldown",
            "TileX", "TileY", "TileZ", "WorldUUIDMost", "WorldUUIDLeast", "Leashed", "Leash"};

    private final LegacyFixer fixer;

    public AwsConverter(LegacyFixer fixer)
    {
        this.fixer = fixer;
    }

    public Result convert(AwsTemplate aws, String source)
    {
        List<String> problems = new ArrayList<>();
        Map<String, Integer> kept = new TreeMap<>();
        AeTemplate.Builder builder = new AeTemplate.Builder(aws.sizeX(), aws.sizeY(), aws.sizeZ(), this.fixer.dataVersion());
        CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        int empty = aws.preservesBlocks() ? 0 : builder.intern(air);

        // Each rule once: its palette index, its block entity, and its raw data.
        Map<Integer, Integer> stateOf = new HashMap<>();
        Map<Integer, CompoundTag> blockEntityOf = new HashMap<>();
        Map<Integer, CompoundTag> ruleData = new HashMap<>();
        for (Map.Entry<Integer, AwsTemplate.Entry> entry : aws.rules().entrySet())
        {
            AwsTemplate.Entry rule = entry.getValue();
            CompoundTag data;
            try
            {
                data = rule.data().isEmpty() ? new CompoundTag() : TagParser.parseTag(rule.data());
            }
            catch (CommandSyntaxException exception)
            {
                problems.add("rule " + rule.number() + " (" + rule.plugin() + "): unreadable data, left empty");
                stateOf.put(entry.getKey(), 0);
                continue;
            }
            ruleData.put(entry.getKey(), data);
            this.convertRule(rule.plugin(), data, builder, stateOf, blockEntityOf, entry.getKey(), problems, kept);
        }

        Map<Integer, Integer> upperHalves = new HashMap<>();
        for (int y = 0; y < aws.sizeY(); y++)
        {
            for (int z = 0; z < aws.sizeZ(); z++)
            {
                for (int x = 0; x < aws.sizeX(); x++)
                {
                    int rule = aws.ruleAt(x, y, z);
                    if (rule == 0)
                    {
                        builder.set(x, y, z, empty);
                        continue;
                    }
                    Integer state = stateOf.get(rule);
                    if (state == null)
                    {
                        problems.add("cell " + x + "," + y + "," + z + ": rule " + rule + " is missing");
                        stateOf.put(rule, 0);
                        state = 0;
                    }
                    if (y > 0 && isUpperTallPlant(ruleData.get(rule)))
                    {
                        // 1.12 kept a tall plant's kind on its lower half only; take it from the cell below.
                        int below = builder.stateAt(x, y - 1, z);
                        state = upperHalves.computeIfAbsent(below, lower -> upperOf(builder, lower, stateOf.get(rule)));
                    }
                    builder.set(x, y, z, state);
                    CompoundTag blockEntity = blockEntityOf.get(rule);
                    if (blockEntity != null)
                    {
                        builder.setBlockEntity(x, y, z, blockEntity.copy());
                    }
                }
            }
        }

        pairChests(builder, aws.sizeX(), aws.sizeY(), aws.sizeZ());
        for (AwsTemplate.Entry entity : aws.entities())
        {
            this.convertEntity(entity, aws, builder, problems, kept);
        }
        return new Result(builder.build(), this.sidecar(aws, source, kept), problems, kept);
    }

    private static final String[] CLOCKWISE = {"north", "east", "south", "west"};
    private static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /**
     * 1.12 joined two side-by-side chests by itself; 1.13 stores which half is
     * which. A single chest with a like one on its clockwise side (as it faces)
     * becomes the left half, that one the right, as vanilla's chest reads them.
     */
    static void pairChests(AeTemplate.Builder builder, int sizeX, int sizeY, int sizeZ)
    {
        for (int y = 0; y < sizeY; y++)
        {
            for (int z = 0; z < sizeZ; z++)
            {
                for (int x = 0; x < sizeX; x++)
                {
                    CompoundTag state = builder.paletteEntry(builder.stateAt(x, y, z));
                    String name = state.getString("Name");
                    if (!name.equals("minecraft:chest") && !name.equals("minecraft:trapped_chest"))
                    {
                        continue;
                    }
                    CompoundTag properties = state.getCompound("Properties");
                    int facing = List.of(CLOCKWISE).indexOf(properties.getString("facing"));
                    if (facing < 0 || !properties.getString("type").equals("single"))
                    {
                        continue;
                    }
                    int[] step = STEP[(facing + 1) % 4];
                    int otherX = x + step[0];
                    int otherZ = z + step[1];
                    if (otherX < 0 || otherZ < 0 || otherX >= sizeX || otherZ >= sizeZ)
                    {
                        continue;
                    }
                    CompoundTag other = builder.paletteEntry(builder.stateAt(otherX, y, otherZ));
                    if (!other.getString("Name").equals(name) || !other.getCompound("Properties").getString("facing").equals(properties.getString("facing"))
                            || !other.getCompound("Properties").getString("type").equals("single"))
                    {
                        continue;
                    }
                    builder.set(x, y, z, builder.intern(withType(state, "left")));
                    builder.set(otherX, y, otherZ, builder.intern(withType(other, "right")));
                }
            }
        }
    }

    private static CompoundTag withType(CompoundTag state, String type)
    {
        CompoundTag copy = state.copy();
        copy.getCompound("Properties").putString("type", type);
        return copy;
    }

    private void convertRule(String plugin, CompoundTag data, AeTemplate.Builder builder, Map<Integer, Integer> stateOf,
            Map<Integer, CompoundTag> blockEntityOf, int number, List<String> problems, Map<String, Integer> kept)
    {
        if (!data.contains("blockState"))
        {
            problems.add("rule " + number + " (" + plugin + "): no block state, left empty");
            stateOf.put(number, 0);
            return;
        }
        String name = data.getCompound("blockState").getString("blockName");
        boolean vanilla = name.startsWith("minecraft:");
        CompoundTag state;
        CompoundTag blockEntity = null;
        switch (plugin)
        {
            case "bed" -> state = this.fixer.bed(data);
            case "vanillaSkull" ->
            {
                state = this.fixer.skullState(data);
                blockEntity = this.fixer.skullBlockEntity(data);
            }
            case "banner" ->
            {
                state = this.fixer.bannerState(data);
                blockEntity = this.fixer.bannerBlockEntity(data);
            }
            case "vanillaFlowerPot" -> state = this.fixer.flowerPot(data);
            case "vanillaSign" ->
            {
                state = this.fixer.state(data.getCompound("blockState"));
                blockEntity = this.fixer.signBlockEntity(data);
            }
            default ->
            {
                state = this.fixer.state(data.getCompound("blockState"));
                if (vanilla && data.contains("teData"))
                {
                    blockEntity = this.fixer.blockEntity(data.getCompound("teData"));
                }
            }
        }
        if (!vanilla)
        {
            // AW's blocks and other mods': keep the whole rule for placing to resolve.
            blockEntity = new CompoundTag();
            blockEntity.put("aeRule", data);
            blockEntity.putString("aePlugin", plugin);
            kept.merge(name, 1, Integer::sum);
        }
        stateOf.put(number, builder.intern(state));
        if (blockEntity != null)
        {
            blockEntityOf.put(number, blockEntity);
        }
    }

    private static boolean isUpperTallPlant(CompoundTag rule)
    {
        if (rule == null)
        {
            return false;
        }
        CompoundTag state = rule.getCompound("blockState");
        return state.getString("blockName").equals("minecraft:double_plant")
                && state.getCompound("properties").getString("half").equals("upper");
    }

    /** The upper half matching a fixed lower half, or the fixer's own guess when there is none below. */
    private static int upperOf(AeTemplate.Builder builder, int lower, int fallback)
    {
        CompoundTag below = builder.paletteEntry(lower);
        if (!below.getCompound("Properties").getString("half").equals("lower"))
        {
            return fallback;
        }
        CompoundTag upper = below.copy();
        upper.getCompound("Properties").putString("half", "upper");
        return builder.intern(upper);
    }

    private void convertEntity(AwsTemplate.Entry entry, AwsTemplate aws, AeTemplate.Builder builder, List<String> problems, Map<String, Integer> kept)
    {
        CompoundTag data;
        try
        {
            data = TagParser.parseTag(entry.data());
        }
        catch (CommandSyntaxException exception)
        {
            problems.add("entity (" + entry.plugin() + "): unreadable data, dropped");
            return;
        }
        int[] cell = AwsTemplate.legacyPos(data.getLong("position"));
        double x = cell[0] + data.getFloat("xOffset");
        double y = cell[1] + data.getFloat("yOffset");
        double z = cell[2] + data.getFloat("zOffset");
        if (cell[0] < 0 || cell[1] < 0 || cell[2] < 0 || cell[0] >= aws.sizeX() || cell[1] >= aws.sizeY() || cell[2] >= aws.sizeZ())
        {
            problems.add("entity (" + entry.plugin() + ") at " + cell[0] + "," + cell[1] + "," + cell[2] + " is outside the template");
        }
        String id = data.getString("mobID");
        CompoundTag nbt;
        if (entry.plugin().equals("entity") || entry.plugin().equals("vanillaHangingEntity"))
        {
            nbt = data.getCompound("entityData").copy();
            for (String key : WORLD_ONLY)
            {
                nbt.remove(key);
            }
            nbt.remove("ForgeData");
            nbt.putString("id", id);
            ListTag rotation = new ListTag();
            rotation.add(FloatTag.valueOf(data.getFloat("rotation")));
            rotation.add(FloatTag.valueOf(0.0F));
            nbt.put("Rotation", rotation);
            nbt = this.fixer.entity(nbt);
            if (!id.startsWith("minecraft:"))
            {
                kept.merge(id, 1, Integer::sum);
            }
        }
        else
        {
            // AW's own (NPCs, vehicles, gates): the whole rule, for placing to resolve.
            nbt = new CompoundTag();
            nbt.put("aeRule", data);
            if (!id.isEmpty())
            {
                nbt.putString("id", id);
            }
            kept.merge(entry.plugin() + (id.isEmpty() ? "" : " " + id), 1, Integer::sum);
        }
        builder.addEntity(x, y, z, entry.plugin(), nbt);
    }

    /** AW's header and validation as JSON, with dimensions named and vanilla biomes upgraded. */
    private JsonObject sidecar(AwsTemplate aws, String source, Map<String, Integer> kept)
    {
        JsonObject json = new JsonObject();
        json.addProperty("name", aws.name());
        json.addProperty("source", source);
        JsonArray size = new JsonArray();
        size.add(aws.sizeX());
        size.add(aws.sizeY());
        size.add(aws.sizeZ());
        json.add("size", size);
        JsonArray offset = new JsonArray();
        offset.add(aws.offsetX());
        offset.add(aws.offsetY());
        offset.add(aws.offsetZ());
        json.add("offset", offset);
        JsonArray requiredMods = new JsonArray();
        for (String mod : aws.header().getOrDefault("mods", "").split(","))
        {
            if (!mod.isBlank())
            {
                requiredMods.add(mod.strip());
            }
        }
        json.add("requiredMods", requiredMods);
        TreeSet<String> namespaces = new TreeSet<>();
        for (String id : kept.keySet())
        {
            String last = id.substring(id.lastIndexOf(' ') + 1);
            int colon = last.indexOf(':');
            if (colon > 0)
            {
                namespaces.add(last.substring(0, colon));
            }
        }
        JsonArray used = new JsonArray();
        namespaces.forEach(used::add);
        json.add("usesNamespaces", used);
        JsonObject validation = new JsonObject();
        for (Map.Entry<String, String> entry : aws.validation().entrySet())
        {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.equals("biomeList") || key.equals("dimensionList") || key.equals("biomeGroupList"))
            {
                JsonArray list = new JsonArray();
                TreeSet<String> seen = new TreeSet<>();
                for (String item : value.split(","))
                {
                    String id = item.strip();
                    if (id.isEmpty())
                    {
                        continue;
                    }
                    if (key.equals("biomeList"))
                    {
                        id = this.fixer.biome(id);
                    }
                    else if (key.equals("dimensionList"))
                    {
                        id = DIMENSIONS.getOrDefault(id, id);
                    }
                    if (seen.add(id))
                    {
                        list.add(id);
                    }
                }
                validation.add(key, list);
            }
            else
            {
                validation.addProperty(key, value);
            }
        }
        json.add("validation", validation);
        JsonObject header = new JsonObject();
        aws.header().forEach(header::addProperty);
        json.add("header", header);
        return json;
    }
}
