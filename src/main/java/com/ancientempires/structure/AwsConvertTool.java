package com.ancientempires.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtIo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Converts every {@code .aws} template in an Ancient Warfare 1.12 jar into
 * {@code ae_templates}: a compact {@code .nbt} ({@link AeTemplate}) and a
 * {@code .json} sidecar per template, under its pack path in lower case. Run
 * by the {@code convertAwsTemplates} Gradle task; it needs no game, since
 * {@link LegacyFixer} runs Mojang's fixer directly. Writes a report of what
 * went wrong and every non-vanilla id kept for placing to resolve.
 * <p>
 * Arguments: the AW jar, the output folder, the report file.
 */
public final class AwsConvertTool
{
    private static final String TEMPLATES = "assets/ancientwarfare/template/";

    private AwsConvertTool()
    {
    }

    public static void main(String[] args) throws IOException
    {
        Path jar = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Path reportFile = Path.of(args[2]);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        AwsConverter converter = new AwsConverter(new LegacyFixer());
        Map<String, Integer> keptTotal = new TreeMap<>();
        List<String> report = new ArrayList<>();
        int converted = 0;
        int failed = 0;
        long start = System.currentTimeMillis();
        try (ZipFile zip = new ZipFile(jar.toFile()))
        {
            List<ZipEntry> entries = new ArrayList<>();
            for (Enumeration<? extends ZipEntry> all = zip.entries(); all.hasMoreElements(); )
            {
                ZipEntry entry = all.nextElement();
                if (entry.getName().startsWith(TEMPLATES) && entry.getName().endsWith(".aws"))
                {
                    entries.add(entry);
                }
            }
            entries.sort((a, b) -> a.getName().compareTo(b.getName()));
            for (ZipEntry entry : entries)
            {
                String relative = entry.getName().substring(TEMPLATES.length());
                // Drop the pack folder (MohawkyPackTweaked1.12.2V75/); keep the rest as the template's path.
                String inPack = relative.substring(relative.indexOf('/') + 1);
                String path = resourcePath(inPack.substring(0, inPack.length() - ".aws".length()));
                List<String> lines;
                try (InputStream stream = zip.getInputStream(entry))
                {
                    lines = text(stream.readAllBytes()).lines().toList();
                }
                try
                {
                    AwsTemplate aws = AwsTemplate.parse(inPack, lines);
                    AwsConverter.Result result = converter.convert(aws, inPack);
                    Path nbt = output.resolve(path + ".nbt");
                    Files.createDirectories(nbt.getParent());
                    NbtIo.writeCompressed(result.template().save(), nbt.toFile());
                    Files.writeString(output.resolve(path + ".json"), gson.toJson(result.sidecar()), StandardCharsets.UTF_8);
                    result.kept().forEach((id, count) -> keptTotal.merge(id, count, Integer::sum));
                    if (!result.problems().isEmpty())
                    {
                        report.add(inPack + ":");
                        result.problems().stream().limit(20).forEach(problem -> report.add("  " + problem));
                        if (result.problems().size() > 20)
                        {
                            report.add("  ... " + (result.problems().size() - 20) + " more");
                        }
                    }
                    converted++;
                }
                catch (RuntimeException exception)
                {
                    failed++;
                    report.add(inPack + ": FAILED " + exception);
                }
                if ((converted + failed) % 100 == 0)
                {
                    System.out.println("Converted " + (converted + failed) + " of " + entries.size());
                }
            }
        }
        int tables = convertLoot(jar, output.getParent().resolve("loot_tables").resolve("aw"), gson, new AwsLootConverter(new LegacyFixer()), report);
        System.out.println("Converted " + tables + " loot tables");
        Files.createDirectories(reportFile.toAbsolutePath().getParent());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8)))
        {
            writer.println("Converted " + converted + " templates, " + failed + " failed, in " + (System.currentTimeMillis() - start) / 1000 + " s.");
            writer.println();
            writer.println("== Problems");
            report.forEach(writer::println);
            writer.println();
            writer.println("== Kept for placing to resolve (non-vanilla blocks and entities, by uses)");
            keptTotal.forEach((id, count) -> writer.println(count + "\t" + id));
        }
        System.out.println("Converted " + converted + ", failed " + failed + "; report at " + reportFile);
    }

    /**
     * AW's loot tables (chests, compat chests, NPC drops) into
     * {@code loot_tables/aw/}, as {@link AwsLootConverter#tableId} names them.
     */
    private static int convertLoot(Path jar, Path output, Gson gson, AwsLootConverter loot, List<String> report) throws IOException
    {
        String prefix = "assets/ancientwarfare/loot_tables/";
        int count = 0;
        try (ZipFile zip = new ZipFile(jar.toFile()))
        {
            for (Enumeration<? extends ZipEntry> all = zip.entries(); all.hasMoreElements(); )
            {
                ZipEntry entry = all.nextElement();
                if (!entry.getName().startsWith(prefix) || !entry.getName().endsWith(".json"))
                {
                    continue;
                }
                String path = resourcePath(entry.getName().substring(prefix.length()));
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)))
                {
                    JsonObject old = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonObject table = loot.convert(old, path.startsWith("entities/") ? "minecraft:entity" : "minecraft:chest");
                    Path file = output.resolve(path);
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, gson.toJson(table), StandardCharsets.UTF_8);
                    count++;
                }
                catch (RuntimeException exception)
                {
                    report.add("loot " + path + ": FAILED " + exception);
                }
            }
        }
        return count;
    }

    /**
     * A template's text. Most are UTF-8, but 240 of AW's were saved in Latin-1,
     * where a sign's formatting code (the section sign) read as UTF-8 became a
     * replacement character; those are read as Latin-1.
     */
    static String text(byte[] bytes)
    {
        try
        {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException exception)
        {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /** A path safe for a resource location: lower case, anything else an underscore. */
    static String resourcePath(String path)
    {
        StringBuilder builder = new StringBuilder();
        for (char c : path.toLowerCase(Locale.ROOT).toCharArray())
        {
            builder.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-' ? c : '_');
        }
        return builder.toString();
    }
}
