package com.ancientempires.worldgen;

import com.ancientempires.registry.ModWorldGen;
import com.ancientempires.structure.AePlacer;
import com.ancientempires.structure.AeTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One AW template placed by world generation, a chunk at a time: each chunk
 * it reaches places its own cells ({@link AePlacer#placeInChunk}), so a
 * structure many chunks wide never loads more than vanilla's own would.
 * On land the ground is shaped first, as AW's ground validator did before
 * building: cleared above the footprint, filled under it up to the floor,
 * and a border ring around it eased from the floor's height out to the
 * natural land ({@link #shapeGround}).
 */
public final class AwSitePiece extends StructurePiece
{
    private final ResourceLocation template;
    private final String name;
    private final int[] offset;
    private final BlockPos anchor;
    private final Rotation rotation;
    private final String faction;
    /** Land only: the floor's top block (the ground layer under the template), or none. */
    private final int ground;
    /** Land only: the ring around the footprint that is eased into the land, in blocks. */
    private final int border;
    private final BoundingBox footprint;

    public AwSitePiece(BoundingBox box, ResourceLocation template, String name, int[] offset, BlockPos anchor, Rotation rotation, String faction,
            int ground, int border)
    {
        super(ModWorldGen.AW_SITE_PIECE.get(), 0, widen(box, border));
        this.template = template;
        this.name = name;
        this.offset = offset.clone();
        this.anchor = anchor;
        this.rotation = rotation;
        this.faction = faction;
        this.ground = ground;
        this.border = border;
        this.footprint = box;
    }

    public AwSitePiece(CompoundTag tag)
    {
        super(ModWorldGen.AW_SITE_PIECE.get(), tag);
        this.template = ResourceLocation.tryParse(tag.getString("Template"));
        this.name = tag.getString("Name");
        this.offset = tag.getIntArray("Offset");
        this.anchor = BlockPos.of(tag.getLong("Anchor"));
        this.rotation = Rotation.values()[Math.floorMod(tag.getInt("Rotation"), Rotation.values().length)];
        this.faction = tag.getString("Faction");
        // Pieces saved before ground shaping have none.
        this.ground = tag.contains("Ground") ? tag.getInt("Ground") : NO_GROUND;
        this.border = tag.getInt("Border");
        this.footprint = widen(this.boundingBox, -this.border);
    }

    public static final int NO_GROUND = Integer.MIN_VALUE;

    /** A box widened (or narrowed) sideways only. */
    private static BoundingBox widen(BoundingBox box, int by)
    {
        return new BoundingBox(box.minX() - by, box.minY(), box.minZ() - by, box.maxX() + by, box.maxY(), box.maxZ() + by);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        tag.putString("Template", this.template.toString());
        tag.putString("Name", this.name);
        tag.putIntArray("Offset", this.offset);
        tag.putLong("Anchor", this.anchor.asLong());
        tag.putInt("Rotation", this.rotation.ordinal());
        tag.putString("Faction", this.faction);
        if (this.ground != NO_GROUND)
        {
            tag.putInt("Ground", this.ground);
        }
        tag.putInt("Border", this.border);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
            ChunkPos chunk, BlockPos pivot)
    {
        if (this.ground != NO_GROUND)
        {
            this.shapeGround(level, generator, chunkBox);
        }
        AeTemplates.get(this.template).ifPresent(found -> AePlacer.placeInChunk(level, found, this.name, this.offset, this.anchor, this.rotation,
                chunkBox, this.faction));
    }

    /**
     * AW's site preparation (StructureValidatorGround.preGeneration) for the
     * columns of this chunk: inside the footprint the land is cleared above the
     * floor and filled up to it; in the border ring each column is eased from
     * the floor's height to the land's natural height just beyond the ring
     * (AW's SmoothingMatrixBuilder curve, half up and half down), cut or
     * built up with the column's own surface block on top. Water columns
     * outside the footprint are left alone. The natural height beyond the ring
     * comes from the generator's noise, so every chunk works out the same curve.
     */
    private void shapeGround(WorldGenLevel level, ChunkGenerator generator, BoundingBox chunkBox)
    {
        RandomState noise = level.getLevel().getChunkSource().randomState();
        BoundingBox foot = this.footprint;
        int reach = this.border;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Math.max(chunkBox.minX(), foot.minX() - reach); x <= Math.min(chunkBox.maxX(), foot.maxX() + reach); x++)
        {
            for (int z = Math.max(chunkBox.minZ(), foot.minZ() - reach); z <= Math.min(chunkBox.maxZ(), foot.maxZ() + reach); z++)
            {
                int nearX = Mth.clamp(x, foot.minX(), foot.maxX());
                int nearZ = Mth.clamp(z, foot.minZ(), foot.maxZ());
                int distance = Math.max(Math.abs(x - nearX), Math.abs(z - nearZ));
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                BlockState surface = level.getBlockState(cursor.set(x, top, z));
                boolean wet = !surface.getFluidState().isEmpty();
                if (distance > 0 && wet)
                {
                    continue;
                }
                int target = this.ground;
                if (distance > 0)
                {
                    // Straight out from the nearest footprint block to just past the ring.
                    int outerX = nearX + (x - nearX) * (reach + 1) / distance;
                    int outerZ = nearZ + (z - nearZ) * (reach + 1) / distance;
                    int outer = generator.getBaseHeight(outerX, outerZ, Heightmap.Types.WORLD_SURFACE_WG, level, noise) - 1;
                    target = BorderCurve.eased(this.ground, outer, distance, reach + 1);
                }
                if (wet || surface.isAir() || !surface.getFluidState().isEmpty())
                {
                    surface = Blocks.GRASS_BLOCK.defaultBlockState();
                }
                this.column(level, cursor, x, z, top, target, surface);
            }
        }
    }

    /** Cuts a column down to {@code target}, or builds it up with what lies under its surface, its surface block on top. */
    private void column(WorldGenLevel level, BlockPos.MutableBlockPos cursor, int x, int z, int top, int target, BlockState surface)
    {
        if (top > target)
        {
            for (int y = top; y > target; y--)
            {
                level.setBlock(cursor.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        else if (top < target)
        {
            BlockState under = level.getBlockState(cursor.set(x, top - 1, z));
            BlockState fill = surface.is(Blocks.GRASS_BLOCK) || surface.is(Blocks.MYCELIUM) || surface.is(Blocks.PODZOL) || under.isAir()
                    || !under.getFluidState().isEmpty() ? Blocks.DIRT.defaultBlockState() : under;
            for (int y = top; y < target; y++)
            {
                level.setBlock(cursor.set(x, y, z), fill, Block.UPDATE_CLIENTS);
            }
        }
        if (top != target)
        {
            level.setBlock(cursor.set(x, target, z), surface, Block.UPDATE_CLIENTS);
        }
    }
}
