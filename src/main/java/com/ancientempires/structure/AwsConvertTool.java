package com.ancientempires.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.NbtIo;

import java.io.BufferedReader;
import java.io.IOException;
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
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)))
                {
                    for (String line = reader.readLine(); line != null; line = reader.readLine())
                    {
                        lines.add(line);
                    }
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
