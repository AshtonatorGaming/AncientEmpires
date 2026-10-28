package com.ancientempires.command;

import com.ancientempires.AncientEmpires;
import com.ancientempires.structure.AePlacer;
import com.ancientempires.structure.AeRemap;
import com.ancientempires.structure.AeTemplate;
import com.ancientempires.structure.AeTemplates;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /ancientempires structure} (admins): the converted AW library.
 * {@code list [filter]}, {@code place <template> [rotation]} at your feet (the
 * structure's front toward you, unless a rotation is given), {@code check
 * <template>} (what its blocks and entities resolve to here), and
 * {@code export <template>} (a vanilla {@code .nbt} in the world's
 * {@code generated} folder, to open in a Structure Block).
 */
public final class StructureCommands
{
    private static final int SHOWN = 15;

    private StructureCommands()
    {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return Commands.literal("structure")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(context -> list(context, ""))
                        .then(Commands.argument("filter", StringArgumentType.greedyString())
                                .executes(context -> list(context, StringArgumentType.getString(context, "filter")))))
                .then(Commands.literal("place")
                        .then(Commands.argument("template", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(AeTemplates.all().keySet(), builder))
                                .executes(context -> place(context, null))
                                .then(Commands.argument("rotation", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("none", "clockwise_90", "180", "counterclockwise_90"), builder))
                                        .executes(context -> place(context, StringArgumentType.getString(context, "rotation"))))))
                .then(Commands.literal("check")
                        .then(Commands.argument("template", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(AeTemplates.all().keySet(), builder))
                                .executes(StructureCommands::check)))
                .then(Commands.literal("export")
                        .then(Commands.argument("template", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(AeTemplates.all().keySet(), builder))
                                .executes(StructureCommands::export)));
    }

    private static int list(CommandContext<CommandSourceStack> context, String filter)
    {
        String needle = filter.toLowerCase(Locale.ROOT);
        List<ResourceLocation> matches = AeTemplates.all().keySet().stream().filter(id -> id.toString().contains(needle)).toList();
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.structure.list", matches.size(), AeTemplates.all().size()), false);
        matches.stream().limit(SHOWN).forEach(id -> context.getSource().sendSuccess(() -> Component.literal("  " + id), false));
        if (matches.size() > SHOWN)
        {
            context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.structure.more", matches.size() - SHOWN), false);
        }
        return matches.size();
    }

    private static AeTemplate template(CommandContext<CommandSourceStack> context, ResourceLocation id)
    {
        AeTemplate template = AeTemplates.get(id).orElse(null);
        if (template == null)
        {
            context.getSource().sendFailure(Component.translatable("commands.ancientempires.structure.unknown", id.toString()));
        }
        return template;
    }

    private static int[] offset(ResourceLocation id)
    {
        JsonObject sidecar = AeTemplates.sidecar(id);
        int[] offset = new int[3];
        if (sidecar != null && sidecar.has("offset"))
        {
            for (int i = 0; i < 3; i++)
            {
                offset[i] = sidecar.getAsJsonArray("offset").get(i).getAsInt();
            }
        }
        return offset;
    }

    /** The front (the template's +z side, where AW put the build key) toward the player. */
    private static Rotation facingPlayer(ServerPlayer player)
    {
        return switch (player.getDirection())
        {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static int place(CommandContext<CommandSourceStack> context, String rotationName) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "template");
        AeTemplate template = template(context, id);
        if (template == null)
        {
            return 0;
        }
        Rotation rotation = rotationName == null ? facingPlayer(player) : switch (rotationName.toLowerCase(Locale.ROOT))
        {
            case "clockwise_90", "90" -> Rotation.CLOCKWISE_90;
            case "180", "clockwise_180" -> Rotation.CLOCKWISE_180;
            case "counterclockwise_90", "270" -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        BlockPos anchor = player.blockPosition();
        long start = System.nanoTime();
        AePlacer.Report report = AePlacer.place(context.getSource().getLevel(), template, offset(id), anchor, rotation, null);
        long millis = (System.nanoTime() - start) / 1_000_000L;
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.structure.placed",
                id.toString(), report.blocks(), report.entities(), millis, rotation.name().toLowerCase(Locale.ROOT)), true);
        report.substituted().entrySet().stream().limit(SHOWN).forEach(entry -> context.getSource().sendSuccess(
                () -> Component.literal("  " + entry.getValue() + " x " + entry.getKey()), false));
        report.aw().forEach((what, count) -> context.getSource().sendSuccess(
                () -> Component.translatable("commands.ancientempires.structure.made", count, what), false));
        report.skipped().forEach((what, count) -> context.getSource().sendSuccess(
                () -> Component.translatable("commands.ancientempires.structure.skipped", count, what), false));
        AncientEmpires.LOGGER.info("Placed {} at {} ({}): {} blocks, {} entities, {} ms; substituted {}; made {}; skipped {}",
                id, anchor, rotation, report.blocks(), report.entities(), millis, report.substituted(), report.aw(), report.skipped());
        return 1;
    }

    private static int check(CommandContext<CommandSourceStack> context)
    {
        ResourceLocation id = ResourceLocationArgument.getId(context, "template");
        AeTemplate template = template(context, id);
        if (template == null)
        {
            return 0;
        }
        JsonObject sidecar = AeTemplates.sidecar(id);
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("commands.ancientempires.structure.check", id.toString(),
                template.sizeX(), template.sizeY(), template.sizeZ(), template.palette().size() - 1, template.blockEntities().size(), template.entities().size()), false);
        if (sidecar != null)
        {
            for (JsonElement mod : sidecar.getAsJsonArray("requiredMods"))
            {
                boolean loaded = ModList.get().isLoaded(mod.getAsString());
                source.sendSuccess(() -> Component.translatable(loaded ? "commands.ancientempires.structure.mod_present" : "commands.ancientempires.structure.mod_missing",
                        mod.getAsString()), false);
            }
        }
        Map<String, Integer> substituted = new TreeMap<>();
        for (int i = 1; i < template.palette().size(); i++)
        {
            CompoundTag entry = template.palette().get(i);
            AeRemap.Resolved resolved = AeRemap.block(entry);
            if (resolved.note() != null)
            {
                substituted.merge(entry.getString("Name") + " -> " + resolved.state().getBlock().builtInRegistryHolder().key().location()
                        + " (" + resolved.note() + ")", 1, Integer::sum);
            }
        }
        if (substituted.isEmpty())
        {
            source.sendSuccess(() -> Component.translatable("commands.ancientempires.structure.all_resolved"), false);
        }
        substituted.keySet().stream().limit(SHOWN).forEach(line -> source.sendSuccess(() -> Component.literal("  " + line), false));
        if (substituted.size() > SHOWN)
        {
            source.sendSuccess(() -> Component.translatable("commands.ancientempires.structure.more", substituted.size() - SHOWN), false);
        }
        Map<String, Integer> entities = new TreeMap<>();
        for (CompoundTag entity : template.entities())
        {
            String entityId = AeRemap.entityId(entity.getCompound("nbt").getString("id"));
            entities.merge(entity.getString("plugin") + " " + entityId, 1, Integer::sum);
        }
        entities.forEach((what, count) -> source.sendSuccess(() -> Component.literal("  " + count + " x " + what), false));
        return 1;
    }

    /**
     * Writes the template as vanilla's {@code .nbt} to
     * {@code generated/<namespace>/structures/<path>.nbt}, its blocks resolved
     * for this game, for a Structure Block to load. Vanilla's own entities only.
     */
    private static int export(CommandContext<CommandSourceStack> context)
    {
        ResourceLocation id = ResourceLocationArgument.getId(context, "template");
        AeTemplate template = template(context, id);
        if (template == null)
        {
            return 0;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", template.dataVersion());
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(template.sizeX()));
        size.add(IntTag.valueOf(template.sizeY()));
        size.add(IntTag.valueOf(template.sizeZ()));
        tag.put("size", size);
        ListTag palette = new ListTag();
        for (int i = 1; i < template.palette().size(); i++)
        {
            palette.add(NbtUtils.writeBlockState(AeRemap.block(template.palette().get(i)).state()));
        }
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int tz = 0; tz < template.tilesZ(); tz++)
        {
            for (int tx = 0; tx < template.tilesX(); tx++)
            {
                template.forEachInTile(tx, tz, (x, y, z, state) ->
                {
                    CompoundTag block = new CompoundTag();
                    ListTag pos = new ListTag();
                    pos.add(IntTag.valueOf(x));
                    pos.add(IntTag.valueOf(y));
                    pos.add(IntTag.valueOf(z));
                    block.put("pos", pos);
                    block.putInt("state", state - 1);
                    CompoundTag nbt = template.blockEntityAt(x, y, z);
                    if (nbt != null && !nbt.contains("aeRule"))
                    {
                        block.put("nbt", nbt.copy());
                    }
                    blocks.add(block);
                });
            }
        }
        tag.put("blocks", blocks);
        ListTag entities = new ListTag();
        for (CompoundTag entity : template.entities())
        {
            CompoundTag nbt = entity.getCompound("nbt");
            if (nbt.contains("aeRule"))
            {
                continue;
            }
            ListTag pos = entity.getList("pos", Tag.TAG_DOUBLE);
            CompoundTag vanilla = new CompoundTag();
            ListTag exact = new ListTag();
            ListTag blockPos = new ListTag();
            for (int i = 0; i < 3; i++)
            {
                exact.add(DoubleTag.valueOf(pos.getDouble(i)));
                blockPos.add(IntTag.valueOf((int) Math.floor(pos.getDouble(i))));
            }
            vanilla.put("pos", exact);
            vanilla.put("blockPos", blockPos);
            vanilla.put("nbt", nbt.copy());
            entities.add(vanilla);
        }
        tag.put("entities", entities);
        Path file = context.getSource().getServer().getWorldPath(LevelResource.GENERATED_DIR)
                .resolve(id.getNamespace()).resolve("structures").resolve(id.getPath() + ".nbt");
        try
        {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(tag, file.toFile());
        }
        catch (IOException exception)
        {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        boolean large = template.sizeX() > 48 || template.sizeY() > 48 || template.sizeZ() > 48;
        context.getSource().sendSuccess(() -> Component.translatable(large ? "commands.ancientempires.structure.exported_large"
                : "commands.ancientempires.structure.exported", id.toString(), file.toString()), false);
        return 1;
    }
}
