package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.FlagBlock;
import com.ancientempires.block.FlagBlockEntity;
import com.ancientempires.registry.ModBlockEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws AW's flags. An unclaimed faction flag is drawn as AW's
 * {@code ProtectionFlagRenderer} drew it: vanilla's banner model (pole, bar,
 * and a cloth that sways) wearing the faction's banner texture
 * ({@code textures/entity/aw/banner}, AW's own). A claimed or plain flag is a fence post with
 * a cloth in the claimer's two colours, as AW drew a player's flag.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class FlagRenderer implements BlockEntityRenderer<FlagBlockEntity>
{
    private static final ResourceLocation CLOTH = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "textures/block/flag_cloth.png");

    private final BlockRenderDispatcher blocks;
    private final ModelPart cloth;
    private final ModelPart pole;
    private final ModelPart bar;

    public FlagRenderer(BlockEntityRendererProvider.Context context)
    {
        this.blocks = context.getBlockRenderDispatcher();
        ModelPart banner = context.bakeLayer(ModelLayers.BANNER);
        this.cloth = banner.getChild("flag");
        this.pole = banner.getChild("pole");
        this.bar = banner.getChild("bar");
    }

    /** The faction's banner texture. */
    public static ResourceLocation bannerTexture(String faction)
    {
        // A plain flag (no faction) is vanilla's white banner cloth.
        return faction.isEmpty() ? ResourceLocation.withDefaultNamespace("textures/entity/banner_base.png")
                : ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "textures/entity/aw/banner/" + faction + ".png");
    }

    /**
     * A faction's standard: AW's banner model at two thirds, its cloth swaying
     * by {@code sway} (world time; 0 still), as vanilla's banner does.
     */
    public static void renderStandard(ModelPart cloth, ModelPart pole, ModelPart bar, String faction, float sway, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay)
    {
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(0.6666667F, -0.6666667F, -0.6666667F);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(bannerTexture(faction)));
        pole.render(pose, vertices, light, overlay);
        bar.render(pose, vertices, light, overlay);
        cloth.y = -32.0F;
        cloth.xRot = sway == 0.0F ? 0.0F : (-0.0125F + 0.01F * Mth.cos(sway * (float) Math.PI * 0.02F)) * (float) Math.PI;
        cloth.render(pose, vertices, light, overlay);
        pose.popPose();
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModBlockEntities.FLAG.get(), FlagRenderer::new);
    }

    @Override
    public void render(FlagBlockEntity flag, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
    {
        BlockState state = flag.getBlockState();
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(FlagBlock.ROTATION) * 22.5F));
        pose.translate(-0.5F, 0.0F, -0.5F);
        if (flag.claimedBy() == null && !flag.faction().isEmpty())
        {
            BlockPos pos = flag.getBlockPos();
            long time = flag.getLevel() == null ? 0L : flag.getLevel().getGameTime();
            float sway = (float) Math.floorMod(pos.getX() * 7L + pos.getY() * 9L + pos.getZ() * 13L + time, 100L) + partialTick;
            renderStandard(this.cloth, this.pole, this.bar, flag.faction(), sway, pose, buffers, light, overlay);
        }
        else
        {
            BlockState post = Blocks.OAK_FENCE.defaultBlockState();
            for (int level = 0; level < 2; level++)
            {
                pose.pushPose();
                pose.translate(0.0F, level, 0.0F);
                this.blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), post, this.blocks.getBlockModel(post),
                        1.0F, 1.0F, 1.0F, light, overlay, ModelData.EMPTY, RenderType.cutout());
                pose.popPose();
            }
            VertexConsumer cloth = buffers.getBuffer(RenderType.entityCutoutNoCull(CLOTH));
            // The cloth hangs from the post's top, upper half in one colour, lower in the other.
            quad(pose, cloth, -0.9F, 1.4F, 0.45F, 2.0F, 0.0F, 0.0F, 1.0F, 0.5F, flag.claimColor(true), light, overlay);
            quad(pose, cloth, -0.9F, 0.8F, 0.45F, 1.4F, 0.0F, 0.5F, 1.0F, 1.0F, flag.claimColor(false), light, overlay);
        }
        pose.popPose();
    }

    /** A vertical quad through the block's centre line (z 0.5), x0..x1 by y0..y1, its texture from (u0, v0) to (u1, v1). */
    private static void quad(PoseStack pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
            int color, int light, int overlay)
    {
        Matrix4f matrix = pose.last().pose();
        int r = color >> 16 & 255;
        int g = color >> 8 & 255;
        int b = color & 255;
        float z = 0.5F;
        buffer.vertex(matrix, x0, y1, z).color(r, g, b, 255).uv(u0, v0).overlayCoords(overlay).uv2(light).normal(pose.last().normal(), 0.0F, 0.0F, 1.0F).endVertex();
        buffer.vertex(matrix, x0, y0, z).color(r, g, b, 255).uv(u0, v1).overlayCoords(overlay).uv2(light).normal(pose.last().normal(), 0.0F, 0.0F, 1.0F).endVertex();
        buffer.vertex(matrix, x1, y0, z).color(r, g, b, 255).uv(u1, v1).overlayCoords(overlay).uv2(light).normal(pose.last().normal(), 0.0F, 0.0F, 1.0F).endVertex();
        buffer.vertex(matrix, x1, y1, z).color(r, g, b, 255).uv(u1, v0).overlayCoords(overlay).uv2(light).normal(pose.last().normal(), 0.0F, 0.0F, 1.0F).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(FlagBlockEntity flag)
    {
        return true;
    }
}
