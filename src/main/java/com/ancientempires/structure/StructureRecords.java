package com.ancientempires.structure;

import com.ancientempires.AncientEmpires;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The faction structures placed in a dimension: each one's bounds, owning
 * faction, protection flag, and who claimed it (AW's {@code StructureMap}).
 * Kept with the world; a chunk index answers "which structure is this block
 * in" without a scan, for block protection and the loot lock.
 */
public final class StructureRecords extends SavedData
{
    private static final String NAME = AncientEmpires.MOD_ID + "_structures";

    /** One placed structure. {@code flag} and {@code claimedBy} may be null. */
    public static final class Record
    {
        public final int id;
        public final String template;
        public final BoundingBox bounds;
        public final String faction;
        @Nullable
        public BlockPos flag;
        @Nullable
        public UUID claimedBy;
        public String claimedName = "";

        Record(int id, String template, BoundingBox bounds, String faction, @Nullable BlockPos flag)
        {
            this.id = id;
            this.template = template;
            this.bounds = bounds;
            this.faction = faction;
            this.flag = flag;
        }

        public boolean claimed()
        {
            return this.claimedBy != null;
        }
    }

    private final Map<Integer, Record> records = new HashMap<>();
    private final Map<Long, List<Record>> byChunk = new HashMap<>();
    private int nextId = 1;

    public static StructureRecords get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(StructureRecords::load, StructureRecords::new, NAME);
    }

    /** A new record for a placed faction structure; its id is what the flag remembers. */
    public Record add(String template, BoundingBox bounds, String faction, @Nullable BlockPos flag)
    {
        Record record = new Record(this.nextId++, template, bounds, faction, flag);
        this.index(record);
        this.setDirty();
        return record;
    }

    @Nullable
    public Record byId(int id)
    {
        return this.records.get(id);
    }

    /** The structure a block is in (the innermost, if two overlap), or null. */
    @Nullable
    public Record at(BlockPos pos)
    {
        List<Record> here = this.byChunk.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (here == null)
        {
            return null;
        }
        Record best = null;
        for (Record record : here)
        {
            if (record.bounds.isInside(pos) && (best == null || volume(record.bounds) < volume(best.bounds)))
            {
                best = record;
            }
        }
        return best;
    }

    public void claim(Record record, UUID player, String name)
    {
        record.claimedBy = player;
        record.claimedName = name;
        this.setDirty();
    }

    private static long volume(BoundingBox box)
    {
        return (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
    }

    private void index(Record record)
    {
        this.records.put(record.id, record);
        for (int x = record.bounds.minX() >> 4; x <= record.bounds.maxX() >> 4; x++)
        {
            for (int z = record.bounds.minZ() >> 4; z <= record.bounds.maxZ() >> 4; z++)
            {
                this.byChunk.computeIfAbsent(ChunkPos.asLong(x, z), key -> new ArrayList<>()).add(record);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putInt("NextId", this.nextId);
        ListTag list = new ListTag();
        for (Record record : this.records.values())
        {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Id", record.id);
            entry.putString("Template", record.template);
            BoundingBox box = record.bounds;
            entry.putIntArray("Bounds", new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
            entry.putString("Faction", record.faction);
            if (record.flag != null)
            {
                entry.putLong("Flag", record.flag.asLong());
            }
            if (record.claimedBy != null)
            {
                entry.putUUID("ClaimedBy", record.claimedBy);
                entry.putString("ClaimedName", record.claimedName);
            }
            list.add(entry);
        }
        tag.put("Records", list);
        return tag;
    }

    private static StructureRecords load(CompoundTag tag)
    {
        StructureRecords records = new StructureRecords();
        records.nextId = Math.max(1, tag.getInt("NextId"));
        for (Tag element : tag.getList("Records", Tag.TAG_COMPOUND))
        {
            CompoundTag entry = (CompoundTag) element;
            int[] b = entry.getIntArray("Bounds");
            if (b.length != 6)
            {
                continue;
            }
            Record record = new Record(entry.getInt("Id"), entry.getString("Template"), new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]),
                    entry.getString("Faction"), entry.contains("Flag") ? BlockPos.of(entry.getLong("Flag")) : null);
            if (entry.hasUUID("ClaimedBy"))
            {
                record.claimedBy = entry.getUUID("ClaimedBy");
                record.claimedName = entry.getString("ClaimedName");
            }
            records.index(record);
            records.nextId = Math.max(records.nextId, record.id + 1);
        }
        return records;
    }
}
