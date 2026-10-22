package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.CropFarmBlockEntity;
import com.ancientempires.block.QuarryBlockEntity;
import com.ancientempires.block.SettlersPostBlockEntity;
import com.ancientempires.block.TownHallBlockEntity;
import com.ancientempires.block.TreeFarmBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AncientEmpires.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SettlersPostBlockEntity>> SETTLERS_POST = BLOCK_ENTITIES.register("settlers_post",
            () -> BlockEntityType.Builder.of(SettlersPostBlockEntity::new, ModBlocks.SETTLERS_POST.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CropFarmBlockEntity>> CROP_FARM = BLOCK_ENTITIES.register("crop_farm",
            () -> BlockEntityType.Builder.of(CropFarmBlockEntity::new, ModBlocks.CROP_FARM.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TreeFarmBlockEntity>> TREE_FARM = BLOCK_ENTITIES.register("tree_farm",
            () -> BlockEntityType.Builder.of(TreeFarmBlockEntity::new, ModBlocks.TREE_FARM.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<QuarryBlockEntity>> QUARRY = BLOCK_ENTITIES.register("quarry",
            () -> BlockEntityType.Builder.of(QuarryBlockEntity::new, ModBlocks.QUARRY.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TownHallBlockEntity>> TOWN_HALL = BLOCK_ENTITIES.register("town_hall",
            () -> BlockEntityType.Builder.of(TownHallBlockEntity::new, ModBlocks.TOWN_HALL.get()).build(null));

    private ModBlockEntities()
    {
    }
}
