package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.decor.CoffinBlock;
import com.ancientempires.block.decor.CoffinBlockEntity;
import com.ancientempires.registry.ModBlockEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * AW's coffins, its {@code ModelCoffin} and {@code ModelStoneCoffin} rebuilt
 * part for part on its own textures, placed as its {@code WoodenCoffinRenderer}
 * and {@code StoneCoffinRenderer} placed them (AW drew at 0.09 and 0.074 of a
 * model unit; vanilla's parts are a sixteenth, hence the factor 16). The lid
 * swings AW's 15 degrees as it opens.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CoffinRenderer implements BlockEntityRenderer<CoffinBlockEntity>
{
    public static final ModelLayerLocation WOODEN = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "wooden_coffin"), "main");
    public static final ModelLayerLocation STONE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "stone_coffin"), "main");
    private static final float LID_DEGREES = 15.0F;

    private final ModelPart wooden;
    private final ModelPart woodenLid;
    private final ModelPart stone;
    private final ModelPart stoneLid;

    public CoffinRenderer(BlockEntityRendererProvider.Context context)
    {
        this.wooden = context.bakeLayer(WOODEN);
        this.woodenLid = this.wooden.getChild("lid");
        this.stone = context.bakeLayer(STONE);
        this.stoneLid = this.stone.getChild("lid");
    }

    @Override
    public void render(CoffinBlockEntity coffin, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
    {
        String variant = coffin.getBlockState().getValue(CoffinBlock.VARIANT);
        boolean stoneCoffin = coffin.getBlockState().getBlock() instanceof CoffinBlock block && block.stone();
        float angle = coffin.angle();
        float lid = LID_DEGREES * coffin.lidProgress(partialTick);
        pose.pushPose();
        if (stoneCoffin)
        {
            pose.translate(0.0D, 1.78D, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees(-angle));
            switch ((int) angle)
            {
                case 0 -> pose.translate(0.0D, 0.0D, -1.0D);
                case 90 -> pose.translate(0.0D, 0.0D, -2.0D);
                case 180 -> pose.translate(-1.0D, 0.0D, -2.0D);
                default -> pose.translate(-1.0D, 0.0D, -1.0D);
            }
            pose.mulPose(Axis.XP.rotationDegrees(180.0F));
            pose.scale(0.074F * 16.0F, 0.074F * 16.0F, 0.074F * 16.0F);
            this.stoneLid.xRot = (float) (-Math.PI / 2.0D);
            this.stoneLid.yRot = (float) Math.toRadians(-lid);
            VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutout(texture("stone_coffin_" + variant)));
            this.stone.render(pose, vertices, light, overlay);
        }
        else
        {
            pose.translate(0.5D, 2.16D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(-angle));
            if (coffin.upright())
            {
                pose.translate(0.0D, -1.25D, angle % 90.0F == 0.0F ? 1.85D : 2.1D);
            }
            else
            {
                pose.translate(0.0D, 0.0D, -0.22D);
            }
            pose.mulPose(Axis.XP.rotationDegrees(coffin.upright() ? 265.0F : 180.0F));
            pose.scale(0.09F * 16.0F, 0.09F * 16.0F, 0.09F * 16.0F);
            this.woodenLid.zRot = (float) Math.toRadians(-lid);
            VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutout(texture("coffin_" + variant)));
            this.wooden.render(pose, vertices, light, overlay);
        }
        pose.popPose();
    }

    private static ResourceLocation texture(String name)
    {
        return ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "textures/entity/aw/" + name + ".png");
    }

    @Override
    public boolean shouldRenderOffScreen(CoffinBlockEntity coffin)
    {
        return true;
    }

    /** AW's ModelCoffin (144 x 80). */
    public static LayerDefinition woodenLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("sideR2", box(0, 35, 1, 9, 26), PartPose.offsetAndRotation(-5.0F, 15.0F, -7.89F, 0.0F, -0.11519173F, 0.0F));
        root.addOrReplaceChild("bottom", box(24, 47, 16, 1, 32), PartPose.offset(-8.0F, 22.9F, -8.0F));
        root.addOrReplaceChild("sideL2", box(0, 0, 1, 9, 26), PartPose.offsetAndRotation(4.0F, 15.0F, -7.89F, 0.0F, 0.11519173F, 0.0F));
        root.addOrReplaceChild("sideBottom", box(0, 10, 10, 9, 1), PartPose.offset(-5.0F, 15.0F, -8.0F));
        root.addOrReplaceChild("sideTop", box(0, 0, 10, 9, 1), PartPose.offset(-5.0F, 15.0F, 23.0F));
        root.addOrReplaceChild("sideR1", box(28, 45, 1, 9, 7), PartPose.offsetAndRotation(-7.96F, 15.0F, 17.75F, 0.0F, 0.43633232F, 0.0F));
        root.addOrReplaceChild("sideL1", box(28, 10, 1, 9, 7), PartPose.offsetAndRotation(7.05F, 15.0F, 17.3F, 0.0F, -0.43633232F, 0.0F));
        PartDefinition lid = root.addOrReplaceChild("lid", box(24, 0, 16, 0, 32), PartPose.offset(-8.0F, 14.1F, -8.0F));
        lid.addOrReplaceChild("lidsideR1", box(118, 45, 1, 1, 7), PartPose.offsetAndRotation(0.0F, -0.1F, 25.6F, 0.0F, 0.43633232F, 0.0F));
        lid.addOrReplaceChild("lidsideR2", box(90, 27, 1, 1, 26), PartPose.offsetAndRotation(3.0F, -0.1F, 0.0F, 0.0F, -0.11519173F, 0.0F));
        lid.addOrReplaceChild("lidsideL2", box(90, 0, 1, 1, 26), PartPose.offsetAndRotation(12.0F, -0.09F, 0.0F, 0.0F, 0.11519173F, 0.0F));
        lid.addOrReplaceChild("lidsideBottom", box(90, 2, 10, 1, 1), PartPose.offset(3.0F, -0.11F, 0.0F));
        lid.addOrReplaceChild("lidsideL1", box(118, 18, 1, 1, 7), PartPose.offsetAndRotation(15.1F, -0.1F, 25.2F, 0.0F, -0.43633232F, 0.0F));
        lid.addOrReplaceChild("lidsideTop", box(90, 0, 10, 1, 1), PartPose.offset(3.0F, -0.11F, 31.0F));
        return LayerDefinition.create(mesh, 144, 80);
    }

    /** AW's ModelStoneCoffin (128 x 128). */
    public static LayerDefinition stoneLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition base = root.addOrReplaceChild("baseface", CubeListBuilder.create().texOffs(-48, 0).addBox(-12.0F, -4.0F, -24.0F, 24, 4, 48),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        base.addOrReplaceChild("southface", CubeListBuilder.create().texOffs(0, 52).addBox(-12.0F, -12.0F, -24.0F, 24, 12, 4), PartPose.ZERO);
        base.addOrReplaceChild("northface", CubeListBuilder.create().texOffs(0, 52).addBox(-12.0F, -12.0F, -24.0F, 24, 12, 4),
                PartPose.rotation(0.0F, (float) -Math.PI, 0.0F));
        base.addOrReplaceChild("leftface", CubeListBuilder.create().texOffs(0, 68).addBox(-24.0F, -12.0F, -12.0F, 48, 12, 4),
                PartPose.rotation(0.0F, (float) (Math.PI / 2.0D), 0.0F));
        base.addOrReplaceChild("rightface", CubeListBuilder.create().texOffs(0, 68).addBox(-24.0F, -12.0F, -12.0F, 48, 12, 4),
                PartPose.rotation(0.0F, (float) (-Math.PI / 2.0D), 0.0F));
        PartDefinition lid = root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(48, 0).addBox(-10.0F, -37.0F, -4.0F, 20, 44, 4),
                PartPose.offset(0.0F, 12.0F, -15.0F));
        lid.addOrReplaceChild("lidInside", CubeListBuilder.create().texOffs(2, 1).mirror().addBox(-9.5F, -36.5F, -3.5F, 19, 43, 3), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static CubeListBuilder box(int u, int v, int width, int height, int depth)
    {
        return CubeListBuilder.create().texOffs(u, v).addBox(0.0F, 0.0F, 0.0F, width, height, depth);
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModBlockEntities.COFFIN.get(), CoffinRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event)
    {
        event.registerLayerDefinition(WOODEN, CoffinRenderer::woodenLayer);
        event.registerLayerDefinition(STONE, CoffinRenderer::stoneLayer);
    }
}
