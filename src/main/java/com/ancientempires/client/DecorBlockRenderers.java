package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.decor.AwDecorBlockEntity;
import com.ancientempires.block.decor.AwSoundBlockEntity;
import com.ancientempires.block.decor.StatueBlock;
import com.ancientempires.registry.ModBlockEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The client side of AW's sound block (its disguise, drawn as that block) and
 * of stakes and statues (their mob, frozen, as AW's {@code StakeRenderer}
 * drew it: on a stake 0.3 in front and 0.6 up, burning if set).
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DecorBlockRenderers
{
    private DecorBlockRenderers()
    {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModBlockEntities.SOUND_BLOCK.get(), context -> new Disguise());
        event.registerBlockEntityRenderer(ModBlockEntities.DECOR.get(), context -> new Mob());
    }

    private static final class Disguise implements BlockEntityRenderer<AwSoundBlockEntity>
    {
        @Override
        public void render(AwSoundBlockEntity sound, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
        {
            // The block is solid, so the light inside it is 0 and the disguise drew black; take the brightest side's.
            int lit = light;
            if (sound.getLevel() != null)
            {
                for (Direction side : Direction.values())
                {
                    int around = LevelRenderer.getLightColor(sound.getLevel(), sound.getBlockPos().relative(side));
                    lit = Math.max(LightTexture.block(lit), LightTexture.block(around)) << 4 | Math.max(LightTexture.sky(lit), LightTexture.sky(around)) << 20;
                }
            }
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(sound.disguise(), pose, buffers, lit, overlay, ModelData.EMPTY, null);
        }
    }

    private static final class Mob implements BlockEntityRenderer<AwDecorBlockEntity>
    {
        /** One frozen mob per block, made on first sight. */
        private final Map<AwDecorBlockEntity, Entity> mobs = new WeakHashMap<>();

        @Override
        public void render(AwDecorBlockEntity decor, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
        {
            if (decor.mob().isEmpty() || decor.getLevel() == null)
            {
                return;
            }
            Entity mob = this.mobs.get(decor);
            if (mob == null || !EntityType.getKey(mob.getType()).toString().equals(decor.mob()))
            {
                mob = EntityType.byString(decor.mob()).map(type -> type.create(decor.getLevel())).orElse(null);
                if (mob == null)
                {
                    return;
                }
                this.mobs.put(decor, mob);
            }
            float yaw = decor.yaw();
            double x = 0.5D;
            double y = 0.0D;
            double z = 0.5D;
            if (!(decor.getBlockState().getBlock() instanceof StatueBlock) && decor.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            {
                Direction facing = decor.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
                yaw = facing.toYRot();
                x += facing.getStepX() * 0.3D;
                y = 0.6D;
                z += facing.getStepZ() * 0.3D;
            }
            mob.setSharedFlagOnFire(decor.burning());
            mob.setYRot(yaw);
            mob.yRotO = yaw;
            mob.setXRot(0.0F);
            if (mob instanceof LivingEntity living)
            {
                living.yBodyRot = yaw;
                living.yBodyRotO = yaw;
                living.yHeadRot = yaw;
                living.yHeadRotO = yaw;
            }
            Minecraft.getInstance().getEntityRenderDispatcher().render(mob, x, y, z, yaw, 0.0F, pose, buffers, light);
        }
    }
}
