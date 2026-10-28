package com.ancientempires.structure;

import com.ancientempires.block.NpcSpawnerBlock;
import com.ancientempires.block.NpcSpawnerBlockEntity;
import com.ancientempires.entity.npc.NpcRoster;
import com.ancientempires.entity.npc.NpcSkins;
import com.ancientempires.item.MobScrollItem;
import com.ancientempires.registry.ModBlocks;
import com.ancientempires.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns an AW advanced spawner (its rule's {@code teData.spawnerSettings})
 * into our {@link NpcSpawnerBlockEntity}, by writing our own saved form and
 * loading it. AW's one-shot spawners (the library's 33,756 of 35,355) become
 * garrisons; ones with more to give than they hold out at once, repeating; a
 * redstone one, redstone. Each entity entry becomes a slot: AW's NPCs their
 * roster scroll ({@code factionName} plus the type after {@code faction.}),
 * with the spawner's custom name, gear (when {@code hasCustomEquipment}) and
 * skin; anything else a {@link MobScrollItem}. AW's own trigger range is kept
 * per spawner, since builders used it to stage the fight.
 */
public final class AwSpawnerImport
{
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final String NPC_PREFIX = "ancientwarfarenpc:faction.";

    private AwSpawnerImport()
    {
    }

    /** Places the spawner at {@code pos}; problems (an unknown roster row, more than nine entries) go to {@code report}. */
    public static void place(ServerLevelAccessor level, BlockPos pos, CompoundTag rule, Map<String, Integer> report)
    {
        CompoundTag settings = rule.getCompound("teData").getCompound("spawnerSettings");
        // AW's transparent spawners (nearly all) are hidden; the rest players are meant to see.
        level.setBlock(pos, ModBlocks.NPC_SPAWNER.get().defaultBlockState().setValue(NpcSpawnerBlock.SHOWN, !settings.getBoolean("transparent")), FLAGS);
        if (!(level.getBlockEntity(pos) instanceof NpcSpawnerBlockEntity spawner))
        {
            return;
        }
        spawner.load(saved(settings, report));
        spawner.setChanged();
    }

    /** Our spawner's saved form for AW's settings. */
    static CompoundTag saved(CompoundTag settings, Map<String, Integer> report)
    {
        List<CompoundTag> entries = new ArrayList<>();
        ListTag groups = settings.getList("spawnGroups", Tag.TAG_COMPOUND);
        for (Tag group : groups)
        {
            for (Tag entry : ((CompoundTag) group).getList("settingsList", Tag.TAG_COMPOUND))
            {
                entries.add((CompoundTag) entry);
            }
        }
        boolean endless = false;
        boolean repeats = false;
        boolean hostile = false;
        int total = 0;
        for (CompoundTag entry : entries)
        {
            int remaining = entry.getInt("remainingSpawnCount");
            int most = Math.max(1, entry.getInt("maxToSpawn"));
            endless |= remaining < 0;
            repeats |= remaining > most;
            hostile |= entry.getBoolean("hostile");
            total += Math.max(0, remaining);
        }
        String mode = settings.getBoolean("respondToRedstone") ? "REDSTONE" : endless || repeats ? "REPEATING" : "GARRISON";
        ListTag items = new ListTag();
        for (CompoundTag entry : entries)
        {
            if (items.size() >= NpcSpawnerBlockEntity.SLOTS)
            {
                report.merge("spawner entries past nine, dropped", 1, Integer::sum);
                break;
            }
            ItemStack stack = scroll(entry, mode.equals("GARRISON"), report);
            if (stack.isEmpty())
            {
                continue;
            }
            CompoundTag saved = stack.save(new CompoundTag());
            saved.putByte("Slot", (byte) items.size());
            items.add(saved);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Items", items);
        tag.putString("Mode", mode);
        int playerRange = Math.max(1, settings.getInt("playerRange"));
        tag.putInt("PlayerRange", playerRange);
        tag.putInt("SpawnRadius", Math.max(0, settings.getInt("spawnRange")));
        tag.putInt("ReturnRange", Math.max(48, playerRange + 16));
        tag.putInt("MinDelay", Math.max(20, settings.getInt("minDelay")));
        tag.putInt("MaxDelay", Math.max(20, Math.max(settings.getInt("minDelay"), settings.getInt("maxDelay"))));
        int nearby = settings.getInt("maxNearbyMonsters");
        tag.putInt("MaxNearby", nearby > 0 ? nearby : 6);
        tag.putInt("TotalLimit", endless ? 0 : total);
        tag.putBoolean("LightSensitive", settings.getBoolean("lightSensitive"));
        tag.putInt("ClearXp", Math.max(0, settings.getInt("xpToDrop")));
        tag.putBoolean("Pool", groups.size() > 1);
        tag.putBoolean("Hostile", hostile || entries.isEmpty());
        return tag;
    }

    /** One entry as a slot's stack: the count is how many come out at once, with AW's least as the roll's floor. */
    private static ItemStack scroll(CompoundTag entry, boolean garrison, Map<String, Integer> report)
    {
        String id = entry.getString("entityId");
        CompoundTag custom = entry.getCompound("customTag");
        int most = garrison ? Math.max(1, entry.getInt("maxToSpawn")) : 1;
        int least = garrison ? Math.max(0, Math.min(most, entry.getInt("minToSpawn"))) : 1;
        ItemStack stack;
        if (id.startsWith(NPC_PREFIX))
        {
            String rosterId = custom.getString("factionName") + "." + id.substring(NPC_PREFIX.length());
            Item egg = ModItems.eggFor(rosterId);
            if (egg == null)
            {
                report.merge("unknown roster row " + rosterId, 1, Integer::sum);
                return ItemStack.EMPTY;
            }
            stack = new ItemStack(egg, most);
            CompoundTag entityTag = npcData(custom, rosterId);
            if (!entityTag.isEmpty())
            {
                stack.getOrCreateTag().put("EntityTag", entityTag);
            }
        }
        else if (id.startsWith("ancientwarfare"))
        {
            report.merge("AW entity " + id, 1, Integer::sum);
            return ItemStack.EMPTY;
        }
        else
        {
            CompoundTag data = custom.copy();
            data.remove("ForgeCaps");
            data.remove("ForgeData");
            data.remove("id");
            stack = MobScrollItem.of(ModItems.MOB_SCROLL.get(), id, data, most);
        }
        if (least < most)
        {
            stack.getOrCreateTag().putInt(NpcSpawnerBlockEntity.MIN_COUNT_KEY, least);
        }
        return stack;
    }

    /**
     * What the spawner set on an AW NPC beyond its row: a custom name, custom
     * gear, and a skin (an AW skin pack file, or another row's look).
     */
    private static CompoundTag npcData(CompoundTag custom, String rosterId)
    {
        CompoundTag tag = new CompoundTag();
        String name = custom.getString("CustomName");
        if (!name.isBlank())
        {
            tag.putString("CustomName", Component.Serializer.toJson(Component.literal(name)));
        }
        if (custom.getBoolean("hasCustomEquipment"))
        {
            tag.put("HandItems", custom.getList("HandItems", Tag.TAG_COMPOUND).copy());
            tag.put("ArmorItems", custom.getList("ArmorItems", Tag.TAG_COMPOUND).copy());
        }
        CompoundTag skin = custom.getCompound("skinSettings");
        if (skin.getString("skinTypeSelected").equals("npc_type"))
        {
            NpcSkins.Entry file = NpcSkins.byFile(skin.getString("npcTypeSkin"));
            String typeName = skin.getString("npcTypeName");
            NpcRoster.Entry row = typeName.isEmpty() || typeName.equals("custom") || typeName.equals(rosterId) ? null : NpcRoster.byId(typeName);
            if (file != null)
            {
                tag.putString("Skin", file.id());
            }
            else if (row != null && !row.skin().isEmpty())
            {
                tag.putString("Skin", row.skin());
            }
        }
        return tag;
    }
}
