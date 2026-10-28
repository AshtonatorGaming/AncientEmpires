package com.ancientempires.worldgen;

import com.ancientempires.registry.ModWorldGen;
import com.ancientempires.structure.AePlacer;
import com.ancientempires.structure.AeTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One AW template placed by world generation, a chunk at a time: each chunk
 * it reaches places its own cells ({@link AePlacer#placeInChunk}), so a
 * structure many chunks wide never loads more than vanilla's own would.
 */
public final class AwSitePiece extends StructurePiece
{
    private final ResourceLocation template;
    private final String name;
    private final int[] offset;
    private final BlockPos anchor;
    private final Rotation rotation;
    private final String faction;

    public AwSitePiece(BoundingBox box, ResourceLocation template, String name, int[] offset, BlockPos anchor, Rotation rotation, String faction)
    {
        super(ModWorldGen.AW_SITE_PIECE.get(), 0, box);
        this.template = template;
        this.name = name;
        this.offset = offset.clone();
        this.anchor = anchor;
        this.rotation = rotation;
        this.faction = faction;
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
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
            ChunkPos chunk, BlockPos pivot)
    {
        AeTemplates.get(this.template).ifPresent(found -> AePlacer.placeInChunk(level, found, this.name, this.offset, this.anchor, this.rotation,
                chunkBox, this.faction));
    }
}
