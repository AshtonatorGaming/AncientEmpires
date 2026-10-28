package com.ancientempires.structure;

import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.DSL;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Upgrades Minecraft 1.12 data (block states, block entities, items, entities,
 * biomes) to the running version through Mojang's own DataFixer, and fills in
 * what that fixer cannot do outside a chunk.
 * <p>
 * Mojang's state table works on 1.12 metadata, as AW saved its states, so
 * connections (fences, panes, walls, stair corners) come out unconnected and
 * placing works them out from the neighbours; doors and leaves are mended in
 * {@link #overlay}. States that 1.12 kept in a block entity (skull
 * type, bed and banner colour, flower pot contents) are rebuilt from AW's rule
 * data. The 1.20 fixer also leaves 1.12 block entity contents alone, so items
 * and spawner entities inside them are upgraded one by one here.
 * <p>
 * Runs without the game (no bootstrap), so the converter can run as a build
 * task. Only {@code minecraft:} data goes through the fixer; other namespaces
 * are kept as written and resolved when placed.
 */
public final class LegacyFixer
{
    /** Minecraft 1.12.2's data version. */
    public static final int FROM = 1343;
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final String[] SKULLS = {"skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head", "dragon_head"};
    private static final String DOOR = "facing,half,hinge,open,powered";

    private final int to;
    private final Map<CompoundTag, CompoundTag> states = new HashMap<>();
    private final Map<String, String> entityIds = new HashMap<>();
    private final Map<String, String> biomes = new HashMap<>();

    public LegacyFixer()
    {
        SharedConstants.tryDetectVersion();
        this.to = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    public int dataVersion()
    {
        return this.to;
    }

    private Tag fix(DSL.TypeReference type, Tag tag)
    {
        return DataFixers.getDataFixer().update(type, new Dynamic<>(NbtOps.INSTANCE, tag), FROM, this.to).getValue();
    }

    /**
     * A 1.12 block state as AW wrote it ({@code blockName}, {@code properties})
     * to a current one ({@code Name}, {@code Properties}). Other mods' states
     * come back renamed to the vanilla keys only. The result may still hold a
     * property the block no longer has; placing drops those.
     */
    public CompoundTag state(CompoundTag awState)
    {
        String name = awState.getString("blockName");
        CompoundTag properties = awState.getCompound("properties");
        CompoundTag legacy = new CompoundTag();
        legacy.putString("Name", name);
        if (!properties.isEmpty())
        {
            legacy.put("Properties", properties.copy());
        }
        if (!name.startsWith("minecraft:"))
        {
            return legacy;
        }
        CompoundTag known = this.states.get(legacy);
        if (known != null)
        {
            return known.copy();
        }
        CompoundTag fixed = (CompoundTag) this.fix(References.BLOCK_STATE, legacy.copy());
        overlay(fixed, properties);
        this.states.put(legacy, fixed);
        return fixed.copy();
    }

    /**
     * Fixes what Mojang's table cannot. A door's halves each kept only part of
     * the door in 1.12 metadata (the lower its facing, the upper its hinge),
     * and AW saved both halves whole, so its properties are laid back. Leaves
     * kept 1.12's decay flags, which 1.13 replaced: a structure's leaves become
     * persistent. Connections (fences, panes, walls, stair corners) are not here:
     * AW saved them unconnected, and placing works them out from the neighbours.
     */
    static void overlay(CompoundTag fixed, CompoundTag old)
    {
        if (!fixed.contains("Properties"))
        {
            return;
        }
        CompoundTag properties = fixed.getCompound("Properties");
        String name = fixed.getString("Name");
        if (name.endsWith("_leaves") && properties.contains("decayable"))
        {
            CompoundTag leaves = new CompoundTag();
            leaves.putString("distance", "7");
            leaves.putString("persistent", "true");
            fixed.put("Properties", leaves);
            return;
        }
        if (!name.endsWith("_door") || old.isEmpty())
        {
            return;
        }
        for (String key : DOOR.split(","))
        {
            if (properties.contains(key) && old.contains(key))
            {
                properties.putString(key, old.getString(key));
            }
        }
    }

    /** A bed: AW kept the colour as {@code bedColor} (1.12 dye metadata, white first). */
    public CompoundTag bed(CompoundTag rule)
    {
        CompoundTag state = this.state(rule.getCompound("blockState"));
        state.putString("Name", "minecraft:" + color(rule.getInt("bedColor")) + "_bed");
        return state;
    }

    /**
     * A skull, from the 1.12 skull type in the block entity (a player head when
     * it names an owner); floor skulls take AW's {@code skullRotation}.
     */
    public CompoundTag skullState(CompoundTag rule)
    {
        CompoundTag blockEntity = rule.getCompound("teData");
        int type = blockEntity.contains("SkullType") ? blockEntity.getByte("SkullType")
                : blockEntity.contains("Owner") ? 3 : 0;
        String skull = SKULLS[Math.max(0, Math.min(SKULLS.length - 1, type))];
        String facing = rule.getCompound("blockState").getCompound("properties").getString("facing");
        CompoundTag state = new CompoundTag();
        CompoundTag properties = new CompoundTag();
        if (facing.isEmpty() || facing.equals("up") || facing.equals("down"))
        {
            state.putString("Name", "minecraft:" + skull);
            int rotation = rule.contains("skullRotation") ? rule.getInt("skullRotation") : blockEntity.getByte("Rot");
            properties.putString("rotation", Integer.toString(Math.floorMod(rotation, 16)));
        }
        else
        {
            state.putString("Name", "minecraft:" + skull.replace("_skull", "_wall_skull").replace("_head", "_wall_head"));
            properties.putString("facing", facing);
        }
        state.put("Properties", properties);
        return state;
    }

    /** A player head's owner in 1.20's form ({@code SkullOwner}, the UUID as ints), or null. */
    public CompoundTag skullBlockEntity(CompoundTag rule)
    {
        CompoundTag owner = rule.getCompound("teData").getCompound("Owner");
        if (owner.isEmpty())
        {
            return null;
        }
        CompoundTag skullOwner = new CompoundTag();
        if (owner.contains("Id", Tag.TAG_STRING))
        {
            try
            {
                UUID id = UUID.fromString(owner.getString("Id"));
                skullOwner.put("Id", new IntArrayTag(uuidInts(id)));
            }
            catch (IllegalArgumentException ignored)
            {
                // A broken id: the textures still draw the head.
            }
        }
        if (owner.contains("Name"))
        {
            skullOwner.putString("Name", owner.getString("Name"));
        }
        if (owner.contains("Properties"))
        {
            skullOwner.put("Properties", owner.getCompound("Properties").copy());
        }
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.putString("id", "minecraft:skull");
        blockEntity.put("SkullOwner", skullOwner);
        return blockEntity;
    }

    private static int[] uuidInts(UUID id)
    {
        long most = id.getMostSignificantBits();
        long least = id.getLeastSignificantBits();
        return new int[] {(int) (most >> 32), (int) most, (int) (least >> 32), (int) least};
    }

    /**
     * A banner's block from its 1.12 block entity's {@code Base} (dye damage,
     * black first), standing or on a wall.
     */
    public CompoundTag bannerState(CompoundTag rule)
    {
        CompoundTag state = this.state(rule.getCompound("blockState"));
        boolean wall = rule.getCompound("blockState").getString("blockName").equals("minecraft:wall_banner");
        String color = color(15 - rule.getCompound("teData").getInt("Base"));
        state.putString("Name", "minecraft:" + color + (wall ? "_wall_banner" : "_banner"));
        return state;
    }

    /** A banner's patterns with 1.20's colour numbering (white first). */
    public CompoundTag bannerBlockEntity(CompoundTag rule)
    {
        CompoundTag old = rule.getCompound("teData");
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.putString("id", "minecraft:banner");
        ListTag patterns = new ListTag();
        for (Tag entry : old.getList("Patterns", Tag.TAG_COMPOUND))
        {
            CompoundTag pattern = ((CompoundTag) entry).copy();
            pattern.putInt("Color", 15 - pattern.getInt("Color"));
            patterns.add(pattern);
        }
        blockEntity.put("Patterns", patterns);
        if (old.contains("CustomName"))
        {
            blockEntity.putString("CustomName", old.getString("CustomName"));
        }
        return blockEntity;
    }

    /** A flower pot holding AW's {@code itemName} and {@code itemMeta}; empty, or a modded plant, stays a pot. */
    public CompoundTag flowerPot(CompoundTag rule)
    {
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:flower_pot");
        String item = rule.getString("itemName");
        if (item.isEmpty() || item.equals("minecraft:air") || !item.startsWith("minecraft:"))
        {
            return state;
        }
        CompoundTag stack = new CompoundTag();
        stack.putString("id", item);
        stack.putByte("Count", (byte) 1);
        stack.putShort("Damage", (short) rule.getInt("itemMeta"));
        String fixed = ((CompoundTag) this.fix(References.ITEM_STACK, stack)).getString("id");
        state.putString("Name", "minecraft:potted_" + fixed.substring(fixed.indexOf(':') + 1));
        return state;
    }

    /** A sign's block entity in 1.20's form from AW's four plain lines. */
    public CompoundTag signBlockEntity(CompoundTag rule)
    {
        CompoundTag text = new CompoundTag();
        ListTag messages = new ListTag();
        for (int line = 0; line < 4; line++)
        {
            messages.add(StringTag.valueOf(new JsonPrimitive(rule.getString("signContents" + line)).toString()));
        }
        text.put("messages", messages);
        text.putString("color", "black");
        text.putBoolean("has_glowing_text", false);
        CompoundTag back = text.copy();
        ListTag blank = new ListTag();
        for (int line = 0; line < 4; line++)
        {
            blank.add(StringTag.valueOf("\"\""));
        }
        back.put("messages", blank);
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.putString("id", "minecraft:sign");
        blockEntity.put("front_text", text);
        blockEntity.put("back_text", back);
        blockEntity.putBoolean("is_waxed", false);
        return blockEntity;
    }

    /**
     * A vanilla block entity: its items upgraded wherever they sit, a mob
     * spawner's entities upgraded and moved into 1.18's layout, and the
     * position dropped (placing sets it).
     */
    public CompoundTag blockEntity(CompoundTag old)
    {
        CompoundTag blockEntity = old.copy();
        blockEntity.remove("x");
        blockEntity.remove("y");
        blockEntity.remove("z");
        if (blockEntity.getString("id").equals("minecraft:mob_spawner"))
        {
            blockEntity.putString("id", "minecraft:mob_spawner");
            if (blockEntity.contains("SpawnData", Tag.TAG_COMPOUND))
            {
                CompoundTag data = new CompoundTag();
                data.put("entity", this.entity(blockEntity.getCompound("SpawnData")));
                blockEntity.put("SpawnData", data);
            }
            ListTag potentials = new ListTag();
            for (Tag entry : blockEntity.getList("SpawnPotentials", Tag.TAG_COMPOUND))
            {
                CompoundTag old112 = (CompoundTag) entry;
                CompoundTag data = new CompoundTag();
                data.put("entity", this.entity(old112.getCompound("Entity")));
                CompoundTag potential = new CompoundTag();
                potential.put("data", data);
                potential.putInt("weight", Math.max(1, old112.getInt("Weight")));
                potentials.add(potential);
            }
            blockEntity.put("SpawnPotentials", potentials);
            return blockEntity;
        }
        return (CompoundTag) this.items(blockEntity);
    }

    /** Upgrades every item stack (a compound with a string id and a Count) anywhere inside. */
    public Tag items(Tag tag)
    {
        if (tag instanceof CompoundTag compound)
        {
            if (compound.contains("id", Tag.TAG_STRING) && compound.get("Count") instanceof NumericTag)
            {
                return this.item(compound);
            }
            CompoundTag copy = new CompoundTag();
            for (String key : compound.getAllKeys())
            {
                copy.put(key, this.items(compound.get(key)));
            }
            return copy;
        }
        if (tag instanceof ListTag list)
        {
            ListTag copy = new ListTag();
            for (Tag entry : list)
            {
                copy.add(this.items(entry));
            }
            return copy;
        }
        return tag;
    }

    /** One 1.12 item stack ({@code id}, {@code Count}, {@code Damage}); other mods' items are kept. */
    public CompoundTag item(CompoundTag stack)
    {
        if (!stack.getString("id").startsWith("minecraft:"))
        {
            return stack.copy();
        }
        return (CompoundTag) this.fix(References.ITEM_STACK, stack.copy());
    }

    /**
     * A 1.12 entity (id included) upgraded: vanilla ids renamed, and its gear
     * and other items too. Other mods' entities keep their data as written.
     */
    public CompoundTag entity(CompoundTag old)
    {
        CompoundTag entity = old.copy();
        String id = entity.getString("id");
        if (!id.startsWith("minecraft:"))
        {
            return (CompoundTag) this.items(entity);
        }
        return (CompoundTag) this.fix(References.ENTITY, entity);
    }

    /** A vanilla entity id renamed as 1.12 to now did ({@code vindication_illager} to {@code vindicator}). */
    public String entityId(String id)
    {
        if (!id.startsWith("minecraft:"))
        {
            return id;
        }
        return this.entityIds.computeIfAbsent(id, key ->
        {
            CompoundTag entity = new CompoundTag();
            entity.putString("id", key);
            return ((CompoundTag) this.fix(References.ENTITY, entity)).getString("id");
        });
    }

    /** A vanilla biome id upgraded (1.18 merged many: {@code forest_hills} became {@code forest}). */
    public String biome(String id)
    {
        if (!id.startsWith("minecraft:"))
        {
            return id;
        }
        return this.biomes.computeIfAbsent(id, key -> this.fix(References.BIOME, StringTag.valueOf(key)).getAsString());
    }

    /** A 1.12 dye colour by metadata (white first). */
    public static String color(int metadata)
    {
        return COLORS[Math.floorMod(metadata, 16)];
    }
}
