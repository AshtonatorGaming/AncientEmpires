package com.ancientempires.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ancient Empires' structure template: vanilla's palette and data version, but
 * the blocks packed as palette indices in 16x16 column tiles, so the giant AW
 * templates (up to 27 million cells) fit in memory at about a byte a cell and
 * placing one chunk unpacks only its own tile. Vanilla's {@code .nbt} keeps a
 * compound per block, about 2 GB for the largest island.
 * <p>
 * Palette entry 0 is always {@code minecraft:structure_void}: the cell is left
 * as the world has it. Block entities and entities are kept sparse. Positions
 * are template cells; rotation happens when placing, through each block's own
 * {@code rotate}, so facing stays right for modded blocks too.
 * <p>
 * The NBT: {@code DataVersion}, {@code size} [x, y, z], {@code palette} (block
 * states as vanilla writes them), {@code bits}, {@code tiles} (one long array
 * per tile, x-major then z; empty for a tile that is all void),
 * {@code blockEntities} ({@code pos}, {@code nbt}), {@code entities}
 * ({@code pos} as doubles inside the template, AW's {@code plugin}, {@code nbt}).
 */
public final class AeTemplate
{
    public static final int TILE = 16;
    public static final String VOID = "minecraft:structure_void";

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int dataVersion;
    private final List<CompoundTag> palette;
    private final int bits;
    private final long[][] tiles;
    private final Map<Integer, CompoundTag> blockEntities;
    private final List<CompoundTag> entities;

    private AeTemplate(int sizeX, int sizeY, int sizeZ, int dataVersion, List<CompoundTag> palette, int bits, long[][] tiles,
            Map<Integer, CompoundTag> blockEntities, List<CompoundTag> entities)
    {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.dataVersion = dataVersion;
        this.palette = palette;
        this.bits = bits;
        this.tiles = tiles;
        this.blockEntities = blockEntities;
        this.entities = entities;
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

    public int dataVersion()
    {
        return this.dataVersion;
    }

    public List<CompoundTag> palette()
    {
        return this.palette;
    }

    public int tilesX()
    {
        return (this.sizeX + TILE - 1) / TILE;
    }

    public int tilesZ()
    {
        return (this.sizeZ + TILE - 1) / TILE;
    }

    /** The palette index at a cell (0 is void). */
    public int stateAt(int x, int y, int z)
    {
        long[] tile = this.tiles[(z / TILE) * this.tilesX() + x / TILE];
        if (tile.length == 0)
        {
            return 0;
        }
        return storage(tile).get(tileIndex(x % TILE, y, z % TILE));
    }

    /**
     * Calls {@code visitor} for every non-void cell of one tile, unpacking it
     * once; how a chunk's share of a structure is placed.
     */
    public void forEachInTile(int tileX, int tileZ, CellVisitor visitor)
    {
        long[] tile = this.tiles[tileZ * this.tilesX() + tileX];
        if (tile.length == 0)
        {
            return;
        }
        SimpleBitStorage storage = storage(tile);
        int baseX = tileX * TILE;
        int baseZ = tileZ * TILE;
        for (int y = 0; y < this.sizeY; y++)
        {
            for (int lz = 0; lz < TILE && baseZ + lz < this.sizeZ; lz++)
            {
                for (int lx = 0; lx < TILE && baseX + lx < this.sizeX; lx++)
                {
                    int state = storage.get(tileIndex(lx, y, lz));
                    if (state != 0)
                    {
                        visitor.visit(baseX + lx, y, baseZ + lz, state);
                    }
                }
            }
        }
    }

    /** About how much memory the packed blocks take, for the template cache. */
    public long packedBytes()
    {
        long longs = 0L;
        for (long[] tile : this.tiles)
        {
            longs += tile.length;
        }
        return longs * 8L + this.blockEntities.size() * 256L + this.entities.size() * 256L;
    }

    /** A cell's block entity data, or null. */
    public CompoundTag blockEntityAt(int x, int y, int z)
    {
        return this.blockEntities.get(cellKey(x, y, z));
    }

    public Map<Integer, CompoundTag> blockEntities()
    {
        return this.blockEntities;
    }

    public int[] cellOf(int key)
    {
        int x = key % this.sizeX;
        int rest = key / this.sizeX;
        return new int[] {x, rest / this.sizeZ, rest % this.sizeZ};
    }

    /** Entities, each {@code pos} (doubles inside the template) and {@code nbt}. */
    public List<CompoundTag> entities()
    {
        return this.entities;
    }

    private int cellKey(int x, int y, int z)
    {
        return (y * this.sizeZ + z) * this.sizeX + x;
    }

    private int tileIndex(int lx, int y, int lz)
    {
        return (y * TILE + lz) * TILE + lx;
    }

    private SimpleBitStorage storage(long[] tile)
    {
        return new SimpleBitStorage(this.bits, TILE * TILE * this.sizeY, tile);
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", this.dataVersion);
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(this.sizeX));
        size.add(IntTag.valueOf(this.sizeY));
        size.add(IntTag.valueOf(this.sizeZ));
        tag.put("size", size);
        ListTag paletteTag = new ListTag();
        paletteTag.addAll(this.palette);
        tag.put("palette", paletteTag);
        tag.putInt("bits", this.bits);
        ListTag tileList = new ListTag();
        for (long[] tile : this.tiles)
        {
            tileList.add(new LongArrayTag(tile));
        }
        tag.put("tiles", tileList);
        ListTag blockEntityList = new ListTag();
        for (Map.Entry<Integer, CompoundTag> entry : this.blockEntities.entrySet())
        {
            int[] cell = this.cellOf(entry.getKey());
            CompoundTag blockEntity = new CompoundTag();
            blockEntity.putIntArray("pos", cell);
            blockEntity.put("nbt", entry.getValue());
            blockEntityList.add(blockEntity);
        }
        tag.put("blockEntities", blockEntityList);
        ListTag entityList = new ListTag();
        entityList.addAll(this.entities);
        tag.put("entities", entityList);
        return tag;
    }

    public static AeTemplate load(CompoundTag tag)
    {
        ListTag size = tag.getList("size", Tag.TAG_INT);
        int sizeX = size.getInt(0);
        int sizeY = size.getInt(1);
        int sizeZ = size.getInt(2);
        List<CompoundTag> palette = new ArrayList<>();
        for (Tag entry : tag.getList("palette", Tag.TAG_COMPOUND))
        {
            palette.add((CompoundTag) entry);
        }
        ListTag tileList = tag.getList("tiles", Tag.TAG_LONG_ARRAY);
        long[][] tiles = new long[tileList.size()][];
        for (int i = 0; i < tiles.length; i++)
        {
            tiles[i] = ((LongArrayTag) tileList.get(i)).getAsLongArray();
        }
        Map<Integer, CompoundTag> blockEntities = new HashMap<>();
        for (Tag entry : tag.getList("blockEntities", Tag.TAG_COMPOUND))
        {
            CompoundTag blockEntity = (CompoundTag) entry;
            int[] cell = blockEntity.getIntArray("pos");
            blockEntities.put((cell[1] * sizeZ + cell[2]) * sizeX + cell[0], blockEntity.getCompound("nbt"));
        }
        List<CompoundTag> entities = new ArrayList<>();
        for (Tag entry : tag.getList("entities", Tag.TAG_COMPOUND))
        {
            entities.add((CompoundTag) entry);
        }
        return new AeTemplate(sizeX, sizeY, sizeZ, tag.getInt("DataVersion"), palette, tag.getInt("bits"), tiles, blockEntities, entities);
    }

    @FunctionalInterface
    public interface CellVisitor
    {
        void visit(int x, int y, int z, int state);
    }

    /**
     * Builds a template cell by cell. States are interned into the palette by
     * their NBT, so equal states share an index.
     */
    public static final class Builder
    {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int dataVersion;
        private final short[] cells;
        private final List<CompoundTag> palette = new ArrayList<>();
        private final Map<CompoundTag, Integer> indices = new HashMap<>();
        private final Map<Integer, CompoundTag> blockEntities = new LinkedHashMap<>();
        private final List<CompoundTag> entities = new ArrayList<>();

        public Builder(int sizeX, int sizeY, int sizeZ, int dataVersion)
        {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.dataVersion = dataVersion;
            this.cells = new short[sizeX * sizeY * sizeZ];
            CompoundTag voidState = new CompoundTag();
            voidState.putString("Name", VOID);
            this.intern(voidState);
        }

        /** The palette index for a state, adding it if new. */
        public int intern(CompoundTag state)
        {
            Integer known = this.indices.get(state);
            if (known != null)
            {
                return known;
            }
            int index = this.palette.size();
            if (index > Short.MAX_VALUE)
            {
                throw new IllegalStateException("More than " + Short.MAX_VALUE + " block states in one template");
            }
            CompoundTag copy = state.copy();
            this.palette.add(copy);
            this.indices.put(copy, index);
            return index;
        }

        public int stateAt(int x, int y, int z)
        {
            return this.cells[this.cellKey(x, y, z)];
        }

        public void set(int x, int y, int z, int paletteIndex)
        {
            this.cells[this.cellKey(x, y, z)] = (short) paletteIndex;
        }

        public CompoundTag paletteEntry(int index)
        {
            return this.palette.get(index);
        }

        public void setBlockEntity(int x, int y, int z, CompoundTag nbt)
        {
            this.blockEntities.put(this.cellKey(x, y, z), nbt);
        }

        /**
         * An entity at a position inside the template (cell units, fractions
         * allowed). {@code plugin} is AW's rule kind ({@code entity},
         * {@code AWNpc}, {@code awGate}, ...), so placing knows how to read it.
         */
        public void addEntity(double x, double y, double z, String plugin, CompoundTag nbt)
        {
            CompoundTag entity = new CompoundTag();
            entity.putString("plugin", plugin);
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(x));
            pos.add(DoubleTag.valueOf(y));
            pos.add(DoubleTag.valueOf(z));
            entity.put("pos", pos);
            entity.put("nbt", nbt);
            this.entities.add(entity);
        }

        private int cellKey(int x, int y, int z)
        {
            return (y * this.sizeZ + z) * this.sizeX + x;
        }

        public AeTemplate build()
        {
            int bits = Math.max(1, Mth.ceillog2(this.palette.size()));
            int tilesX = (this.sizeX + TILE - 1) / TILE;
            int tilesZ = (this.sizeZ + TILE - 1) / TILE;
            long[][] tiles = new long[tilesX * tilesZ][];
            int perTile = TILE * TILE * this.sizeY;
            for (int tz = 0; tz < tilesZ; tz++)
            {
                for (int tx = 0; tx < tilesX; tx++)
                {
                    SimpleBitStorage storage = new SimpleBitStorage(bits, perTile);
                    boolean any = false;
                    for (int y = 0; y < this.sizeY; y++)
                    {
                        for (int lz = 0; lz < TILE; lz++)
                        {
                            int z = tz * TILE + lz;
                            if (z >= this.sizeZ)
                            {
                                break;
                            }
                            for (int lx = 0; lx < TILE; lx++)
                            {
                                int x = tx * TILE + lx;
                                if (x >= this.sizeX)
                                {
                                    break;
                                }
                                int state = this.cells[this.cellKey(x, y, z)];
                                if (state != 0)
                                {
                                    storage.set((y * TILE + lz) * TILE + lx, state);
                                    any = true;
                                }
                            }
                        }
                    }
                    tiles[tz * tilesX + tx] = any ? storage.getRaw() : new long[0];
                }
            }
            return new AeTemplate(this.sizeX, this.sizeY, this.sizeZ, this.dataVersion, List.copyOf(this.palette), bits, tiles,
                    Map.copyOf(this.blockEntities), List.copyOf(this.entities));
        }
    }
}
