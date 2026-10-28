package com.ancientempires.structure;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.gate.GateEntity;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * AW's Structure Scanner, saving in our own format: a box and a build key
 * (where the player stood and which way they faced) become an
 * {@link AeTemplate} and its sidecar, turned so the build key's facing is the
 * template's front, as AW's scanner did. Scans go in a pack in the game folder
 * ({@code ancientempires/scanned}, AW's {@code config/template}), loaded in
 * every world, so a scanned building can be placed anywhere and shared as files.
 * Generation is off for a scan until its sidecar says otherwise. Blocks and
 * their data are kept as they stand; gates become AW's gate rule; NPCs are left
 * out (garrisons go in spawners, as in AW's own structures).
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StructureScanner
{
    public static final String CATEGORY = "scanned";
    private static final int MAX_SIDE = 256;

    private StructureScanner()
    {
    }

    /** The game folder pack that holds scans. */
    public static Path packRoot()
    {
        return FMLPaths.GAMEDIR.get().resolve(AncientEmpires.MOD_ID).resolve(CATEGORY);
    }

    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event)
    {
        if (event.getPackType() != PackType.SERVER_DATA)
        {
            return;
        }
        Path root = packRoot();
        try
        {
            Files.createDirectories(root.resolve("data").resolve(AncientEmpires.MOD_ID).resolve(AeTemplates.FOLDER).resolve(CATEGORY));
            Path meta = root.resolve("pack.mcmeta");
            if (!Files.exists(meta))
            {
                Files.writeString(meta, "{\"pack\": {\"pack_format\": 15, \"description\": \"Structures scanned with Ancient Empires' Structure Scanner\"}}\n");
            }
        }
        catch (IOException exception)
        {
            AncientEmpires.LOGGER.error("Could not make the scanned structures folder {}", root, exception);
            return;
        }
        event.addRepositorySource(packs -> packs.accept(Pack.readMetaAndCreate(AncientEmpires.MOD_ID + "_scanned",
                Component.literal("Ancient Empires: scanned structures"), true, name -> new PathPackResources(name, root, false),
                PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN)));
    }

    /** A file-safe template name, or empty if nothing is left. */
    public static String cleanName(String name)
    {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
    }

    /**
     * Scans the box between {@code a} and {@code b} with the build key at
     * {@code key} (the first layer above ground, where a player would stand),
     * saves it, and reloads the data packs so it can be placed at once.
     */
    public static Component scan(ServerLevel level, Player player, BlockPos a, BlockPos b, BlockPos key, Direction facing, String rawName)
    {
        return scan(level, player.getGameProfile().getName(), a, b, key, facing, rawName, true);
    }

    /** As {@link #scan}, by name; {@code reload} false leaves the data packs alone (tests read the file back themselves). */
    public static Component scan(ServerLevel level, String scannedBy, BlockPos a, BlockPos b, BlockPos key, Direction facing, String rawName, boolean reload)
    {
        String name = cleanName(rawName);
        if (name.isEmpty())
        {
            return Component.translatable("message.ancientempires.scanner.bad_name");
        }
        BoundingBox box = BoundingBox.fromCorners(a, b);
        if (box.getXSpan() > MAX_SIDE || box.getYSpan() > MAX_SIDE || box.getZSpan() > MAX_SIDE)
        {
            return Component.translatable("message.ancientempires.scanner.too_big", MAX_SIDE);
        }
        Rotation turn = switch (facing)
        {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        Rotation back = switch (turn)
        {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> turn;
        };
        // Template cells: the world box turned back, shifted so its corner is 0.
        int minX = Integer.MAX_VALUE;
        int minY = box.minY() - key.getY();
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x : new int[] {box.minX(), box.maxX()})
        {
            for (int z : new int[] {box.minZ(), box.maxZ()})
            {
                BlockPos local = new BlockPos(x - key.getX(), 0, z - key.getZ()).rotate(back);
                minX = Math.min(minX, local.getX());
                minZ = Math.min(minZ, local.getZ());
                maxX = Math.max(maxX, local.getX());
                maxZ = Math.max(maxZ, local.getZ());
            }
        }
        int[] offset = {-minX, -minY, -minZ};
        int sizeX = maxX - minX + 1;
        int sizeY = box.getYSpan();
        int sizeZ = maxZ - minZ + 1;
        AeTemplate.Builder builder = new AeTemplate.Builder(sizeX, sizeY, sizeZ, NbtUtils.addCurrentDataVersion(new CompoundTag()).getInt("DataVersion"));
        Set<String> mods = new TreeSet<>();
        for (int x = 0; x < sizeX; x++)
        {
            for (int y = 0; y < sizeY; y++)
            {
                for (int z = 0; z < sizeZ; z++)
                {
                    BlockPos pos = AePlacer.cellToWorld(offset, key, turn, x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() && !state.is(Blocks.AIR))
                    {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    CompoundTag written = NbtUtils.writeBlockState(state.rotate(level, pos, back));
                    String namespace = written.getString("Name").substring(0, written.getString("Name").indexOf(':'));
                    if (!namespace.equals("minecraft") && !namespace.equals(AncientEmpires.MOD_ID))
                    {
                        mods.add(namespace);
                    }
                    builder.set(x, y, z, builder.intern(written));
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null)
                    {
                        CompoundTag nbt = blockEntity.saveWithFullMetadata();
                        nbt.remove("x");
                        nbt.remove("y");
                        nbt.remove("z");
                        builder.setBlockEntity(x, y, z, nbt);
                    }
                }
            }
        }
        AABB area = AABB.of(box).inflate(0.01D);
        for (Entity entity : level.getEntities((Entity) null, area, entity -> !(entity instanceof Player) && !(entity instanceof NpcEntity)))
        {
            double[] local = templateCell(entity.getX() - key.getX(), entity.getZ() - key.getZ(), back);
            double x = local[0] + offset[0];
            double y = entity.getY() - key.getY() + offset[1];
            double z = local[1] + offset[2];
            if (entity instanceof GateEntity gate)
            {
                BlockPos cell = gate.pos1().subtract(key).rotate(back).offset(offset[0], offset[1], offset[2]);
                builder.addEntity(cell.getX(), cell.getY(), cell.getZ(), "awGate", gateRule(gate, back));
                continue;
            }
            CompoundTag nbt = new CompoundTag();
            if (!entity.save(nbt))
            {
                continue;
            }
            nbt.remove("UUID");
            nbt.remove("Pos");
            // Turned back with the template; placing turns it again.
            ListTag rotation = new ListTag();
            rotation.add(FloatTag.valueOf(entity.getYRot() - 90.0F * turn.ordinal()));
            rotation.add(FloatTag.valueOf(entity.getXRot()));
            nbt.put("Rotation", rotation);
            builder.addEntity(x, y, z, "entity", nbt);
        }
        Path dir = packRoot().resolve("data").resolve(AncientEmpires.MOD_ID).resolve(AeTemplates.FOLDER).resolve(CATEGORY);
        try
        {
            Files.createDirectories(dir);
            NbtIo.writeCompressed(builder.build().save(), dir.resolve(name + ".nbt").toFile());
            Files.writeString(dir.resolve(name + ".json"), sidecar(name, scannedBy, new int[] {sizeX, sizeY, sizeZ}, offset, mods), StandardCharsets.UTF_8);
        }
        catch (IOException exception)
        {
            AncientEmpires.LOGGER.error("Could not save scanned structure {}", name, exception);
            return Component.translatable("message.ancientempires.scanner.failed", exception.getMessage());
        }
        if (reload)
        {
            level.getServer().reloadResources(level.getServer().getPackRepository().getSelectedIds());
        }
        return Component.translatable("message.ancientempires.scanner.saved", name, sizeX, sizeY, sizeZ);
    }

    /** A point turned back into the template's frame (cells span [x, x + 1), as the placer turns them). */
    private static double[] templateCell(double x, double z, Rotation back)
    {
        return AePlacer.rotate(x, z, back);
    }

    /** A gate as AW's gate rule (corners from the entity's cell, which is its first corner), rebuilt where the structure goes. */
    private static CompoundTag gateRule(GateEntity gate, Rotation back)
    {
        CompoundTag rule = new CompoundTag();
        rule.put("pos1", relative(BlockPos.ZERO));
        rule.put("pos2", relative(gate.pos2().subtract(gate.pos1()).rotate(back)));
        rule.putString("gateType", gate.gateType().awName);
        rule.putByte("orientation", (byte) back.rotate(gate.facing()).get2DDataValue());
        CompoundTag nbt = new CompoundTag();
        nbt.put("aeRule", rule);
        return nbt;
    }

    private static CompoundTag relative(BlockPos pos)
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    private static String sidecar(String name, String scannedBy, int[] size, int[] offset, Set<String> mods)
    {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("source", "scanned by " + scannedBy);
        json.add("size", array(size));
        json.add("offset", array(offset));
        JsonArray required = new JsonArray();
        mods.forEach(required::add);
        json.add("requiredMods", required);
        JsonObject validation = new JsonObject();
        validation.addProperty("type", "ground");
        validation.addProperty("worldGenEnabled", "false");
        validation.addProperty("unique", "false");
        validation.addProperty("selectionWeight", "10");
        validation.addProperty("clusterValue", "10");
        validation.addProperty("minDuplicateDistance", "0");
        validation.addProperty("biomeWhiteList", "false");
        validation.add("biomeList", new JsonArray());
        validation.addProperty("dimensionWhiteList", "true");
        validation.add("dimensionList", new JsonArray());
        validation.addProperty("leveling", "0");
        validation.addProperty("fill", "0");
        validation.addProperty("border", "0");
        validation.addProperty("territoryName", "");
        json.add("validation", validation);
        return new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n";
    }

    private static JsonArray array(int[] values)
    {
        JsonArray array = new JsonArray();
        for (int value : values)
        {
            array.add(value);
        }
        return array;
    }
}
