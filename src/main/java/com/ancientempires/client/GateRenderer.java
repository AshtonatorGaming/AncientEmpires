package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.gate.GateEntity;
import com.ancientempires.gate.GateType;
import com.ancientempires.registry.ModEntities;
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
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws AW's gates with AW's own models (ModelGateBasic and ModelGateBridge,
 * rebuilt box for box on AW's 256 by 256 sheets) and the same per-cell logic
 * as AW's four gate renderers: the frame (top, corners, sides) stays, while
 * the wall (solid for wood, bars for iron) rises, slides, or parts with the
 * gate's edge; a drawbridge turns about its foot. AW drew in a flipped space
 * (scale -1, -1, 1); that is kept so its numbers carry over unchanged.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class GateRenderer extends EntityRenderer<GateEntity>
{
    public static final ModelLayerLocation BASIC = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "gate_basic"), "main");
    public static final ModelLayerLocation BRIDGE = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "gate_bridge"), "main");

    private final ModelPart wall;
    private final ModelPart bars;
    private final ModelPart top;
    private final ModelPart corner;
    private final ModelPart side;
    private final ModelPart bridgeBlock;
    private final ModelPart bridgeWall;
    private final ModelPart[] bridgeTop;
    private final ModelPart[] bridgeCorner;
    private final ModelPart[] bridgeCorner2;
    private final ModelPart[] bridgeSide1;
    private final ModelPart[] bridgeSide2;

    public GateRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        ModelPart basic = context.bakeLayer(BASIC);
        this.wall = basic.getChild("part1");
        this.bars = basic.getChild("barsTop");
        this.top = basic.getChild("top1");
        this.corner = basic.getChild("cornerTop1");
        this.side = basic.getChild("side1");
        ModelPart bridge = context.bakeLayer(BRIDGE);
        this.bridgeBlock = bridge.getChild("part1");
        this.bridgeWall = bridge.getChild("bridgePiece");
        ModelPart side1 = bridge.getChild("bridgeSide1");
        ModelPart side2 = bridge.getChild("bridgeSide2");
        ModelPart side3 = bridge.getChild("bridgeSide3");
        ModelPart side4 = bridge.getChild("bridgeSide4");
        this.bridgeTop = new ModelPart[] {bridge.getChild("bridgeTop1"), bridge.getChild("bridgeTop2")};
        this.bridgeCorner = new ModelPart[] {bridge.getChild("bridgeCorner1"), bridge.getChild("bridgeCorner2"), side1, side2};
        this.bridgeCorner2 = new ModelPart[] {bridge.getChild("bridgeCorner3"), bridge.getChild("bridgeCorner4"), side3, side4};
        this.bridgeSide1 = new ModelPart[] {side1, side2};
        this.bridgeSide2 = new ModelPart[] {side3, side4};
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.GATE.get(), GateRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event)
    {
        event.registerLayerDefinition(BASIC, GateRenderer::basicLayer);
        event.registerLayerDefinition(BRIDGE, GateRenderer::bridgeLayer);
    }

    /** AW's ModelGateBasic. */
    private static LayerDefinition basicLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("part1", CubeListBuilder.create().texOffs(0, 0).addBox(-1, -16, -8, 2, 16, 16), PartPose.ZERO);
        PartDefinition top = root.addOrReplaceChild("top1", CubeListBuilder.create().texOffs(41, 26).addBox(4, 0, -8, 2, 6, 16), PartPose.offset(0, -16, 0));
        top.addOrReplaceChild("top4", CubeListBuilder.create().texOffs(39, 0).addBox(1, 0, -8, 3, 8, 16), PartPose.ZERO);
        top.addOrReplaceChild("top3", CubeListBuilder.create().texOffs(39, 0).addBox(0, 0, -8, 3, 8, 16), PartPose.offset(-4, 0, 0));
        top.addOrReplaceChild("top2", CubeListBuilder.create().texOffs(41, 26).addBox(0, 0, -8, 2, 6, 16), PartPose.offset(-6, 0, 0));
        PartDefinition bars = root.addOrReplaceChild("barsTop", CubeListBuilder.create().texOffs(0, 75).addBox(-1, -13, -8, 2, 2, 16), PartPose.ZERO);
        bars.addOrReplaceChild("barsLeft1", CubeListBuilder.create().texOffs(0, 52).addBox(-1, -16, -5, 2, 3, 2), PartPose.ZERO);
        bars.addOrReplaceChild("barsLeft3", CubeListBuilder.create().texOffs(0, 58).addBox(-1, -11, -5, 2, 6, 2), PartPose.ZERO);
        bars.addOrReplaceChild("barsLeft5", CubeListBuilder.create().texOffs(0, 67).addBox(-1, -3, -5, 2, 3, 2), PartPose.ZERO);
        bars.addOrReplaceChild("barsRight1", CubeListBuilder.create().texOffs(27, 52).addBox(-1, -16, 3, 2, 3, 2), PartPose.ZERO);
        bars.addOrReplaceChild("barsRight3", CubeListBuilder.create().texOffs(27, 58).addBox(-1, -11, 3, 2, 6, 2), PartPose.ZERO);
        bars.addOrReplaceChild("barsRight5", CubeListBuilder.create().texOffs(27, 67).addBox(-1, -3, 3, 2, 3, 2), PartPose.ZERO);
        bars.addOrReplaceChild("barsBottom", CubeListBuilder.create().texOffs(0, 33).addBox(-1, -5, -8, 2, 2, 16), PartPose.ZERO);
        PartDefinition corner = root.addOrReplaceChild("cornerTop1", CubeListBuilder.create().texOffs(39, 49).addBox(1, 0, 0, 3, 8, 8), PartPose.offset(0, -16, 0));
        corner.addOrReplaceChild("cornerTop2", CubeListBuilder.create().texOffs(39, 66).addBox(0, 0, 0, 2, 6, 10), PartPose.offset(4, 0, -2));
        corner.addOrReplaceChild("cornerSide2", CubeListBuilder.create().texOffs(62, 83).addBox(0, 0, 0, 2, 16, 6), PartPose.offset(4, 0, -8));
        corner.addOrReplaceChild("cornerSide1", CubeListBuilder.create().texOffs(39, 83).addBox(0, 0, 0, 3, 16, 8), PartPose.offset(1, 0, -8));
        corner.addOrReplaceChild("cornerTop3", CubeListBuilder.create().texOffs(62, 49).addBox(0, 0, 0, 3, 8, 8), PartPose.offset(-4, 0, 0));
        corner.addOrReplaceChild("cornerTop4", CubeListBuilder.create().texOffs(64, 66).addBox(0, 0, 0, 2, 6, 10), PartPose.offset(-6, 0, -2));
        corner.addOrReplaceChild("cornerSide3", CubeListBuilder.create().texOffs(39, 108).addBox(0, 0, 0, 3, 16, 8), PartPose.offset(-4, 0, -8));
        corner.addOrReplaceChild("cornerSide4", CubeListBuilder.create().texOffs(62, 108).addBox(0, 0, 0, 2, 16, 6), PartPose.offset(-6, 0, -8));
        PartDefinition side = root.addOrReplaceChild("side1", CubeListBuilder.create().texOffs(39, 83).addBox(1, 0, -8, 3, 16, 8), PartPose.offset(0, -16, 0));
        side.addOrReplaceChild("side2", CubeListBuilder.create().texOffs(62, 83).addBox(0, 0, -8, 2, 16, 6), PartPose.offset(4, 0, 0));
        side.addOrReplaceChild("side3", CubeListBuilder.create().texOffs(39, 108).addBox(0, 0, -8, 3, 16, 8), PartPose.offset(-4, 0, 0));
        side.addOrReplaceChild("side4", CubeListBuilder.create().texOffs(62, 108).addBox(0, 0, -8, 2, 16, 6), PartPose.offset(-6, 0, 0));
        return LayerDefinition.create(mesh, 256, 256);
    }

    /** AW's ModelGateBridge. */
    private static LayerDefinition bridgeLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("part1", CubeListBuilder.create().texOffs(0, 0).addBox(-8, -8, -8, 16, 16, 16), PartPose.ZERO);
        root.addOrReplaceChild("bridgePiece", CubeListBuilder.create().texOffs(0, 33).addBox(-8, -8, -8, 16, 16, 8), PartPose.ZERO);
        root.addOrReplaceChild("bridgeSide1", CubeListBuilder.create().texOffs(0, 58).addBox(-2, -8, 0, 10, 16, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeSide4", CubeListBuilder.create().texOffs(27, 58).addBox(-8, -8, 3, 6, 16, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeCorner1", CubeListBuilder.create().texOffs(49, 34).addBox(-8, -8, 0, 6, 9, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeCorner2", CubeListBuilder.create().texOffs(49, 47).addBox(-8, -8, 3, 10, 6, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeTop1", CubeListBuilder.create().texOffs(49, 58).addBox(-8, -8, 0, 16, 9, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeTop2", CubeListBuilder.create().texOffs(49, 72).addBox(-8, -8, 3, 16, 6, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeSide3", CubeListBuilder.create().texOffs(0, 58).addBox(-8, -8, 0, 10, 16, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeSide2", CubeListBuilder.create().texOffs(27, 58).addBox(2, -8, 3, 6, 16, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeCorner4", CubeListBuilder.create().texOffs(49, 47).addBox(-2, -8, 3, 10, 6, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeCorner3", CubeListBuilder.create().texOffs(49, 34).addBox(2, -8, 0, 6, 9, 3), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public void render(GateEntity gate, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light)
    {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(gate.gateType().texture()));
        pose.pushPose();
        pose.scale(-1.0F, -1.0F, 1.0F);
        if (gate.gateType().motion == GateType.Motion.BRIDGE)
        {
            this.renderBridge(gate, partialTick, pose, buffer, light);
        }
        else
        {
            this.renderWall(gate, partialTick, pose, buffer, light);
        }
        pose.popPose();
        super.render(gate, yaw, partialTick, pose, buffers, light);
    }

    private static void draw(ModelPart part, float rotation, PoseStack pose, VertexConsumer buffer, int light)
    {
        part.yRot = (float) Math.toRadians(rotation);
        part.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
    }

    /** RenderGateBasic, with RenderGateSingle's and RenderGateDouble's wall placement. */
    private void renderWall(GateEntity gate, float partialTick, PoseStack pose, VertexConsumer buffer, int light)
    {
        boolean wideX = gate.wideOnX();
        float width = gate.width();
        float height = gate.height();
        float edge = gate.edge(partialTick);
        float axis = wideX ? 90.0F : 0.0F;
        float tx = wideX ? 1.0F : 0.0F;
        float tz = wideX ? 0.0F : 1.0F;
        float xOffset = wideX ? width * 0.5F - 0.5F : 0.0F;
        float zOffset = wideX ? 0.0F : -width * 0.5F + 0.5F;
        pose.translate(-xOffset, 0.0F, zOffset);
        for (int y = 0; y < height; y++)
        {
            pose.pushPose();
            for (int x = 0; x < width; x++)
            {
                boolean topRow = y == height - 1;
                if (topRow && x > 0 && x < width - 1)
                {
                    draw(this.top, axis, pose, buffer, light);
                }
                else if (topRow && x == 0)
                {
                    draw(this.corner, axis, pose, buffer, light);
                }
                else if (topRow && x == width - 1)
                {
                    draw(this.corner, axis + 180.0F, pose, buffer, light);
                }
                else if (x == 0)
                {
                    draw(this.side, axis, pose, buffer, light);
                }
                else if (x == width - 1)
                {
                    draw(this.side, axis + 180.0F, pose, buffer, light);
                }
                this.wallPiece(gate, x, width, y, height, wideX, axis, edge, pose, buffer, light);
                pose.translate(tx, 0.0F, tz);
            }
            pose.popPose();
            pose.translate(0.0F, -1.0F, 0.0F);
        }
    }

    private void wallPiece(GateEntity gate, int x, float width, int y, float height, boolean wideX, float axis, float edge,
            PoseStack pose, VertexConsumer buffer, int light)
    {
        float moveX = 0.0F;
        float moveY = 0.0F;
        float moveZ = 0.0F;
        boolean show;
        switch (gate.gateType().motion)
        {
            case VERTICAL ->
            {
                show = y + edge <= height - 0.475F;
                moveY = -edge;
            }
            case SLIDE ->
            {
                // AW slid a single gate away from pos1, the opposite way when pos1 is the larger end.
                boolean reverse = gate.pos1().getX() > gate.pos2().getX() || gate.pos1().getZ() < gate.pos2().getZ();
                show = reverse ? x - edge > -0.5F : edge + x < gate.edgeMax() - 0.5F;
                float move = reverse ? -edge : edge;
                moveX = wideX ? move : 0.0F;
                moveZ = wideX ? 0.0F : move;
            }
            default ->
            {
                float move = x < width * 0.5F ? -edge : edge;
                show = x < width * 0.5F ? x + move > -0.5F : x + move <= width - 0.475F;
                moveX = wideX ? move : 0.0F;
                moveZ = wideX ? 0.0F : move;
            }
        }
        if (!show)
        {
            return;
        }
        pose.pushPose();
        pose.translate(moveX, moveY, moveZ);
        draw(gate.gateType().bars() ? this.bars : this.wall, axis, pose, buffer, light);
        pose.popPose();
    }

    /** RenderGateRotatingBridge. */
    private void renderBridge(GateEntity gate, float partialTick, PoseStack pose, VertexConsumer buffer, int light)
    {
        boolean wideX = gate.wideOnX();
        float edge = gate.edge(partialTick);
        float rx = wideX ? edge : 0.0F;
        float rz = wideX ? 0.0F : edge;
        boolean invert = gate.facing() == Direction.SOUTH || gate.facing() == Direction.EAST;
        if (invert)
        {
            rx = -rx;
            rz = -rz;
        }
        pose.translate(0.0F, -0.5F, 0.0F);
        pose.mulPose(Axis.XP.rotationDegrees(rx));
        pose.mulPose(Axis.ZP.rotationDegrees(rz));
        float width = gate.width();
        float height = gate.height();
        float xOffset = wideX ? width * 0.5F - 0.5F : 0.0F;
        float zOffset = wideX ? 0.0F : -width * 0.5F + 0.5F;
        float tx = wideX ? 1.0F : 0.0F;
        float tz = wideX ? 0.0F : 1.0F;
        float axis = wideX ? 180.0F : 90.0F;
        if (invert)
        {
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
        pose.translate(-xOffset, 0.0F, zOffset);
        for (int y = 0; y < height; y++)
        {
            pose.pushPose();
            for (int x = 0; x < width; x++)
            {
                if (y == 0)
                {
                    draw(this.bridgeBlock, axis, pose, buffer, light);
                }
                boolean topRow = y == height - 1;
                ModelPart[] parts = topRow && x > 0 && x < width - 1 ? this.bridgeTop
                        : topRow && x == 0 ? this.bridgeCorner
                        : topRow && x == width - 1 ? this.bridgeCorner2
                        : x == 0 && y > 0 ? this.bridgeSide1
                        : x == width - 1 && y > 0 ? this.bridgeSide2 : null;
                if (parts != null)
                {
                    for (ModelPart part : parts)
                    {
                        draw(part, axis, pose, buffer, light);
                    }
                }
                if (y > 0)
                {
                    draw(this.bridgeWall, axis, pose, buffer, light);
                }
                pose.translate(tx, 0.0F, tz);
            }
            pose.popPose();
            pose.translate(0.0F, -1.0F, 0.0F);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(GateEntity gate)
    {
        return gate.gateType().texture();
    }
}
