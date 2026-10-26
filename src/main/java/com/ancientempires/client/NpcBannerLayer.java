package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A standard bearer's banner, worn on the back like a sashimono: vanilla's
 * banner model (pole, bar, and the flag with its dyes and patterns) on a short
 * pole rising above the head, following the body as it turns and bends. The
 * flag stirs a little, as a placed banner does. The bearer's hands stay free.
 */
@OnlyIn(Dist.CLIENT)
final class NpcBannerLayer extends RenderLayer<NpcEntity, NpcModel>
{
    /** Half a placed banner's size: the pole rises about a block above the shoulders. */
    private static final float SCALE = 0.5F;

    private final ModelPart flag;
    private final ModelPart pole;
    private final ModelPart bar;

    NpcBannerLayer(RenderLayerParent<NpcEntity, NpcModel> parent, ModelPart banner)
    {
        super(parent);
        this.flag = banner.getChild("flag");
        this.pole = banner.getChild("pole");
        this.bar = banner.getChild("bar");
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, NpcEntity npc, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch)
    {
        ItemStack banner = npc.getCarriedBanner();
        if (banner.isEmpty() || !(banner.getItem() instanceof BannerItem item) || npc.isInvisible())
        {
            return;
        }
        pose.pushPose();
        this.getParentModel().body.translateAndRotate(pose);
        // Behind the back, the flag facing the way the bearer looks back from.
        pose.translate(0.0F, 0.2F, 0.22F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(SCALE, SCALE, SCALE);
        VertexConsumer base = ModelBakery.BANNER_BASE.buffer(buffer, RenderType::entitySolid);
        this.pole.visible = true;
        this.pole.render(pose, base, light, OverlayTexture.NO_OVERLAY);
        this.bar.render(pose, base, light, OverlayTexture.NO_OVERLAY);
        float sway = (Math.floorMod(npc.getId() * 7L + npc.tickCount, 100L) + partialTick) / 100.0F;
        this.flag.xRot = (-0.0125F + 0.01F * Mth.cos(Mth.TWO_PI * sway)) * Mth.PI;
        this.flag.y = -32.0F;
        BannerRenderer.renderPatterns(pose, buffer, light, OverlayTexture.NO_OVERLAY, this.flag, ModelBakery.BANNER_BASE, true,
                BannerBlockEntity.createPatterns(item.getColor(), BannerBlockEntity.getItemPatterns(banner)));
        pose.popPose();
    }
}
