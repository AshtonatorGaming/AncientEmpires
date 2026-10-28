package com.ancientempires.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * An Ancient Warfare structure template ({@code .aws}) as written, read without
 * the game: the header and validation sections as text, the block grid as rule
 * numbers, and each rule and entity with its plugin and raw data (AW wrote that
 * data as SNBT behind a {@code JSON:} prefix). Nothing is converted here; that
 * is {@link AwsConverter}'s job.
 * <p>
 * The layer grid follows AW's {@code TemplateParser}: {@code layer: 3} or
 * {@code layer: 3-4} (the same rows for every y in the range), then one line
 * per z row. A row line {@code 5x1,2|3} repeats the row five times; within a
 * row, {@code 2|3} is rule 2 three times along x. Rule 0 is an empty cell: AW
 * placed air there unless the template preserves blocks.
 */
public final class AwsTemplate
{
    /** A rule or entity entry: AW's plugin name, its number, and its data (SNBT, without {@code JSON:}). */
    public record Entry(String plugin, int number, String data)
    {
    }

    private final String fileName;
    private final Map<String, String> header;
    private final Map<String, String> validation;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;
    private final short[] cells;
    private final Map<Integer, Entry> rules;
    private final List<Entry> entities;

    private AwsTemplate(String fileName, Map<String, String> header, Map<String, String> validation, int[] size, int[] offset,
            short[] cells, Map<Integer, Entry> rules, List<Entry> entities)
    {
        this.fileName = fileName;
        this.header = header;
        this.validation = validation;
        this.sizeX = size[0];
        this.sizeY = size[1];
        this.sizeZ = size[2];
        this.offsetX = offset[0];
        this.offsetY = offset[1];
        this.offsetZ = offset[2];
        this.cells = cells;
        this.rules = rules;
        this.entities = entities;
    }

    public String fileName()
    {
        return this.fileName;
    }

    public String name()
    {
        return this.header.getOrDefault("name", this.fileName);
    }

    public Map<String, String> header()
    {
        return this.header;
    }

    public Map<String, String> validation()
    {
        return this.validation;
    }

    public int sizeX()
    {
        return this.sizeX;
    }

    public int sizeY()
    {
        return this.sizeY;
    }

    public int sizeZ()
    {
        return this.sizeZ;
    }

    /** AW's build key: the template cell that sits on the chosen ground position. */
    public int offsetX()
    {
        return this.offsetX;
    }

    public int offsetY()
    {
        return this.offsetY;
    }

    public int offsetZ()
    {
        return this.offsetZ;
    }

    /** The rule number at a cell; 0 is empty. */
    public int ruleAt(int x, int y, int z)
    {
        return this.cells[index(x, y, z)];
    }

    public Map<Integer, Entry> rules()
    {
        return this.rules;
    }

    public List<Entry> entities()
    {
        return this.entities;
    }

    public boolean preservesBlocks()
    {
        return Boolean.parseBoolean(this.validation.getOrDefault("preserveBlocks", "false"));
    }

    private int index(int x, int y, int z)
    {
        return (y * this.sizeZ + z) * this.sizeX + x;
    }

    /**
     * Reads a template from its lines. Throws {@link IllegalArgumentException}
     * on a template it cannot make sense of (no size, a broken number).
     */
    public static AwsTemplate parse(String fileName, List<String> lines)
    {
        Map<String, String> header = new LinkedHashMap<>();
        Map<String, String> validation = new LinkedHashMap<>();
        Map<Integer, Entry> rules = new TreeMap<>();
        List<Entry> entities = new ArrayList<>();
        List<String> layerRows = new ArrayList<>();
        int[] size = null;
        short[] cells = null;
        int layerFrom = 0;
        int layerTo = 0;
        String section = "";
        String plugin = null;
        int number = 0;
        StringBuilder data = null;
        for (String raw : lines)
        {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#"))
            {
                continue;
            }
            if (data != null)
            {
                // Inside a rule's data block; AW prefixes the SNBT with JSON:.
                if (line.startsWith(":enddata"))
                {
                    continue;
                }
                if (line.startsWith(":endrule") || line.startsWith(":endentity"))
                {
                    Entry entry = new Entry(plugin, number, data.toString());
                    if (section.equals("rule"))
                    {
                        rules.put(number, entry);
                    }
                    else
                    {
                        entities.add(entry);
                    }
                    data = null;
                    plugin = null;
                    section = "";
                    continue;
                }
                data.append(line.startsWith("JSON:") ? line.substring(5) : line);
                continue;
            }
            switch (section)
            {
                case "header", "validation" ->
                {
                    if (line.startsWith(":end"))
                    {
                        section = "";
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq > 0)
                    {
                        (section.equals("header") ? header : validation).put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
                    }
                    if (section.equals("header") && line.startsWith("size="))
                    {
                        size = triple(line.substring(5));
                    }
                    continue;
                }
                case "layer" ->
                {
                    if (line.startsWith(":endlayer"))
                    {
                        if (size == null)
                        {
                            throw new IllegalArgumentException(fileName + ": layer before size");
                        }
                        if (cells == null)
                        {
                            cells = new short[size[0] * size[1] * size[2]];
                        }
                        fillLayer(cells, size, layerFrom, layerTo, layerRows);
                        layerRows.clear();
                        section = "";
                    }
                    else
                    {
                        layerRows.add(line);
                    }
                    continue;
                }
                case "rule", "entity" ->
                {
                    if (line.startsWith("plugin="))
                    {
                        plugin = line.substring(7).strip();
                    }
                    else if (line.startsWith("number="))
                    {
                        number = Integer.parseInt(line.substring(7).strip());
                    }
                    else if (line.startsWith("data:"))
                    {
                        data = new StringBuilder();
                    }
                    else if (line.startsWith(":endrule") || line.startsWith(":endentity"))
                    {
                        // A rule with no data block.
                        Entry entry = new Entry(plugin, number, "");
                        if (section.equals("rule"))
                        {
                            rules.put(number, entry);
                        }
                        else
                        {
                            entities.add(entry);
                        }
                        section = "";
                    }
                    continue;
                }
                default ->
                {
                }
            }
            if (line.startsWith("header:"))
            {
                section = "header";
            }
            else if (line.startsWith("validation:"))
            {
                section = "validation";
            }
            else if (line.startsWith("layer:"))
            {
                String[] range = line.substring(6).split("-");
                layerFrom = Integer.parseInt(range[0].strip());
                layerTo = range.length > 1 ? Integer.parseInt(range[1].strip()) : layerFrom;
                section = "layer";
            }
            else if (line.startsWith("rule:"))
            {
                section = "rule";
                number = 0;
            }
            else if (line.startsWith("entity:"))
            {
                section = "entity";
                number = 0;
            }
        }
        if (size == null)
        {
            throw new IllegalArgumentException(fileName + ": no size in the header");
        }
        if (cells == null)
        {
            cells = new short[size[0] * size[1] * size[2]];
        }
        int[] offset = header.containsKey("offset") ? triple(header.get("offset")) : new int[3];
        return new AwsTemplate(fileName, Collections.unmodifiableMap(header), Collections.unmodifiableMap(validation), size, offset,
                cells, Collections.unmodifiableMap(rules), Collections.unmodifiableList(entities));
    }

    private static int[] triple(String text)
    {
        String[] parts = text.split(",");
        return new int[] {Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()), Integer.parseInt(parts[2].strip())};
    }

    /** Writes one layer block (every y from {@code from} to {@code to}) into the grid. */
    private static void fillLayer(short[] cells, int[] size, int from, int to, List<String> rowLines)
    {
        List<short[]> rows = new ArrayList<>();
        for (String line : rowLines)
        {
            int cross = line.indexOf('x');
            int repeat = 1;
            String body = line;
            if (cross > 0)
            {
                repeat = Integer.parseInt(line.substring(0, cross));
                body = line.substring(cross + 1);
            }
            short[] row = parseRow(body);
            for (int i = 0; i < repeat; i++)
            {
                rows.add(row);
            }
        }
        for (int y = Math.max(0, from); y <= to && y < size[1]; y++)
        {
            for (int z = 0; z < rows.size() && z < size[2]; z++)
            {
                short[] row = rows.get(z);
                for (int x = 0; x < size[0] && x < row.length; x++)
                {
                    cells[(y * size[2] + z) * size[0] + x] = row[x];
                }
            }
        }
    }

    private static short[] parseRow(String body)
    {
        List<Short> values = new ArrayList<>();
        for (String token : body.split(","))
        {
            String part = token.strip();
            if (part.isEmpty())
            {
                continue;
            }
            int bar = part.indexOf('|');
            short value = Short.parseShort(bar < 0 ? part : part.substring(0, bar));
            int count = bar < 0 ? 1 : Integer.parseInt(part.substring(bar + 1));
            for (int i = 0; i < count; i++)
            {
                values.add(value);
            }
        }
        short[] row = new short[values.size()];
        for (int i = 0; i < row.length; i++)
        {
            row[i] = values.get(i);
        }
        return row;
    }

    /**
     * A block position packed by Minecraft 1.12 ({@code BlockPos.toLong}: 26 bits
     * of x from bit 38, 12 of y from bit 26, 26 of z), which AW stores for
     * entities. 1.20 packs differently, so its {@code BlockPos.of} would misread it.
     */
    public static int[] legacyPos(long packed)
    {
        int x = (int) (packed << (64 - 38 - 26) >> (64 - 26));
        int y = (int) (packed << (64 - 26 - 12) >> (64 - 12));
        int z = (int) (packed << (64 - 26) >> (64 - 26));
        return new int[] {x, y, z};
    }
}
