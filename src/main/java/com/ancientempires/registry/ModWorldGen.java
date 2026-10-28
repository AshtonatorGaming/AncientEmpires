package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.worldgen.AwSitePiece;
import com.ancientempires.worldgen.AwSiteStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** World generation's types: AW's structures as one vanilla structure type and its piece. */
public final class ModWorldGen
{
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, AncientEmpires.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE, AncientEmpires.MOD_ID);

    public static final RegistryObject<StructureType<AwSiteStructure>> AW_SITE = STRUCTURE_TYPES.register("aw_site", () -> () -> AwSiteStructure.CODEC);
    public static final RegistryObject<StructurePieceType> AW_SITE_PIECE = PIECE_TYPES.register("aw_site",
            () -> (StructurePieceType.ContextlessType) AwSitePiece::new);

    private ModWorldGen()
    {
    }
}
