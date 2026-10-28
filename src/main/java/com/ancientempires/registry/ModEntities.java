package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.gate.GateEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The two entity types: the NPC ({@code ancientempires:npc}; every role and
 * faction is this one type, told apart by its data and equipment) and the gate.
 */
public final class ModEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AncientEmpires.MOD_ID);

    public static final RegistryObject<EntityType<NpcEntity>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.of(NpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(AncientEmpires.MOD_ID + ":npc"));
    /** AW's gates (vertical, sliding, double, drawbridge), spanning their opening. */
    public static final RegistryObject<EntityType<GateEntity>> GATE = ENTITIES.register("gate",
            () -> EntityType.Builder.<GateEntity>of(GateEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .setShouldReceiveVelocityUpdates(false)
                    .build(AncientEmpires.MOD_ID + ":gate"));

    private ModEntities()
    {
    }
}
