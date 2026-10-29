package com.ancientempires.registry;

import com.ancientempires.block.FruitFarmBlockEntity;
import com.ancientempires.AncientEmpires;
import com.ancientempires.block.FlagBlockEntity;
import com.ancientempires.block.AnimalPenBlockEntity;
import com.ancientempires.block.CropFarmBlockEntity;
import com.ancientempires.block.FishingDockBlockEntity;
import com.ancientempires.block.HuntingLodgeBlockEntity;
import com.ancientempires.block.KitchenBlockEntity;
import com.ancientempires.block.NpcSpawnerBlockEntity;
import com.ancientempires.block.QuarryBlockEntity;
import com.ancientempires.block.SettlersPostBlockEntity;
import com.ancientempires.block.TownHallBlockEntity;
import com.ancientempires.block.TreeFarmBlockEntity;
import com.ancientempires.block.decor.AwDecorBlockEntity;
import com.ancientempires.block.decor.AwSoundBlockEntity;
import com.ancientempires.block.decor.CoffinBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block entity types for the blocks in {@link ModBlocks} that keep state: each
 * worksite's store and area, the Town Hall, the Settlers' Post, the NPC
 * spawner, and flags.
 */
public final class ModBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AncientEmpires.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SettlersPostBlockEntity>> SETTLERS_POST = BLOCK_ENTITIES.register("settlers_post",
            () -> BlockEntityType.Builder.of(SettlersPostBlockEntity::new, ModBlocks.SETTLERS_POST.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<FruitFarmBlockEntity>> FRUIT_FARM = BLOCK_ENTITIES.register("fruit_farm",
            () -> BlockEntityType.Builder.of(FruitFarmBlockEntity::new, ModBlocks.FRUIT_FARM.get()).build(null));
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

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<AnimalPenBlockEntity>> ANIMAL_PEN = BLOCK_ENTITIES.register("animal_pen",
            () -> BlockEntityType.Builder.of(AnimalPenBlockEntity::new, ModBlocks.ANIMAL_PEN.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<HuntingLodgeBlockEntity>> HUNTING_LODGE = BLOCK_ENTITIES.register("hunting_lodge",
            () -> BlockEntityType.Builder.of(HuntingLodgeBlockEntity::new, ModBlocks.HUNTING_LODGE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<FishingDockBlockEntity>> FISHING_DOCK = BLOCK_ENTITIES.register("fishing_dock",
            () -> BlockEntityType.Builder.of(FishingDockBlockEntity::new, ModBlocks.FISHING_DOCK.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<NpcSpawnerBlockEntity>> NPC_SPAWNER = BLOCK_ENTITIES.register("npc_spawner",
            () -> BlockEntityType.Builder.of(NpcSpawnerBlockEntity::new, ModBlocks.NPC_SPAWNER.get()).build(null));
    public static final RegistryObject<BlockEntityType<CoffinBlockEntity>> COFFIN = BLOCK_ENTITIES.register("coffin",
            () -> BlockEntityType.Builder.of(CoffinBlockEntity::new, ModBlocks.WOODEN_COFFIN.get(), ModBlocks.STONE_COFFIN.get()).build(null));
    public static final RegistryObject<BlockEntityType<AwSoundBlockEntity>> SOUND_BLOCK = BLOCK_ENTITIES.register("sound_block",
            () -> BlockEntityType.Builder.of(AwSoundBlockEntity::new, ModBlocks.SOUND_BLOCK.get()).build(null));
    /** Gravestones' loot and the mobs on stakes and statues. */
    public static final RegistryObject<BlockEntityType<AwDecorBlockEntity>> DECOR = BLOCK_ENTITIES.register("decor",
            () -> BlockEntityType.Builder.of(AwDecorBlockEntity::new, ModBlocks.DECOR.get("gravestone").get(), ModBlocks.DECOR.get("stake").get(),
                    ModBlocks.DECOR.get("altar_candle").get(), ModBlocks.DECOR.get("altar_long_cloth").get(), ModBlocks.DECOR.get("altar_short_cloth").get(),
                    ModBlocks.STATUE.get()).build(null));
    public static final RegistryObject<BlockEntityType<FlagBlockEntity>> FLAG = BLOCK_ENTITIES.register("flag",
            () -> BlockEntityType.Builder.of(FlagBlockEntity::new, ModBlocks.FLAG.get()).build(null));
    public static final RegistryObject<BlockEntityType<KitchenBlockEntity>> KITCHEN = BLOCK_ENTITIES.register("kitchen",
            () -> BlockEntityType.Builder.of(KitchenBlockEntity::new, ModBlocks.KITCHEN.get()).build(null));

    private ModBlockEntities()
    {
    }
}
