package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.network.OutlinePacket;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Blocks the server points out, outlined through walls for a few seconds: a
 * structure's flag in gold (using a chest of an unclaimed faction structure),
 * the spawners still holding it in red (a failed claim; NPCs glow instead).
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, value = Dist.CLIENT)
public final class BlockOutlines
{
    private static final int SHOW_TICKS = 160;

    private record Shown(List<BlockPos> blocks, long until)
    {
    }

    private static final Map<OutlinePacket.Kind, Shown> SHOWN = new EnumMap<>(OutlinePacket.Kind.class);

    private BlockOutlines()
    {
    }

    public static void show(List<BlockPos> blocks, OutlinePacket.Kind kind)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && !blocks.isEmpty())
        {
            SHOWN.put(kind, new Shown(List.copyOf(blocks), minecraft.level.getGameTime() + SHOW_TICKS));
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHOWN.isEmpty() || minecraft.level == null)
        {
            return;
        }
        long now = minecraft.level.getGameTime();
        SHOWN.values().removeIf(shown -> shown.until() <= now);
        if (SHOWN.isEmpty())
        {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(Types.LINES_THROUGH);
        float pulse = 0.6F + 0.4F * (float) Math.sin((now + event.getPartialTick()) * 0.3F);
        for (Map.Entry<OutlinePacket.Kind, Shown> entry : SHOWN.entrySet())
        {
            OutlinePacket.Kind kind = entry.getKey();
            // Fades over the last second.
            float alpha = Math.min(1.0F, (entry.getValue().until() - now) / 20.0F) * pulse;
            for (BlockPos pos : entry.getValue().blocks())
            {
                AABB box = new AABB(pos).expandTowards(0.0D, kind.extraHeight, 0.0D).inflate(0.05D);
                LevelRenderer.renderLineBox(pose, lines, box, kind.red, kind.green, kind.blue, alpha);
            }
        }
        buffers.endBatch(Types.LINES_THROUGH);
        pose.popPose();
    }

    /** Lines drawn over everything (no depth test), as the outline must show through the walls between. */
    static final class Types extends RenderType
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
