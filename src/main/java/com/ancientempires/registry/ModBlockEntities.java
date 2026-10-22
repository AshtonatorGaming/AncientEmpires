package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.SettlersPostBlockEntity;
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

    private ModBlockEntities()
    {
    }
}
