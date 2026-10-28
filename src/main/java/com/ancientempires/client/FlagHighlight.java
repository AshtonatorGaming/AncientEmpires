package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.OptionalDouble;

/**
 * A structure's flag, outlined in gold through walls for a few seconds after
 * the server points it out (using a chest of an unclaimed faction structure),
 * so the banner to claim can be found.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, value = Dist.CLIENT)
public final class FlagHighlight
{
    private static final int SHOW_TICKS = 160;

    private static BlockPos pos;
    private static long until;

    private FlagHighlight()
    {
    }

    public static void show(BlockPos flag)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null)
        {
            pos = flag;
            until = minecraft.level.getGameTime() + SHOW_TICKS;
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || pos == null || minecraft.level == null)
        {
            return;
        }
        long left = until - minecraft.level.getGameTime();
        if (left <= 0)
        {
            pos = null;
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(Types.LINES_THROUGH);
        // Pulses, and fades over the last second.
        float alpha = Math.min(1.0F, left / 20.0F) * (0.6F + 0.4F * (float) Math.sin((minecraft.level.getGameTime() + event.getPartialTick()) * 0.3F));
        AABB box = new AABB(pos).expandTowards(0.0D, 0.75D, 0.0D).inflate(0.05D);
        LevelRenderer.renderLineBox(pose, lines, box, 1.0F, 0.8F, 0.2F, alpha);
        buffers.endBatch(Types.LINES_THROUGH);
        pose.popPose();
    }

    /** Lines drawn over everything (no depth test), as the outline must show through the walls between. */
    private static final class Types extends RenderType
    {
        static final RenderType LINES_THROUGH = create("ancientempires_lines_through", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 256, false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(3.0D)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear)
        {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }
}
