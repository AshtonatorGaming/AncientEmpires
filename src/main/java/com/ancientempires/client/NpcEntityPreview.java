package com.ancientempires.client;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.theme.WidgetTheme;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.inventory.NpcLayout;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Inventory paperdoll. The draw uses the same lights as the player inventory
 * screen, and the scale comes from the hitbox so a short body and a giant both
 * land inside the frame with the feet near the bottom.
 */
@OnlyIn(Dist.CLIENT)
public final class NpcEntityPreview implements IDrawable
{
    /** Steve is 32px tall. The standing hitbox is 1.8 blocks, which is 28.8px. */
    private static final float VISUAL_HEIGHT_RATIO = 32.0F / 28.8F;
    /** Body plus arms is 16px. The standing hitbox is 0.6 blocks, which is 9.6px. */
    private static final float VISUAL_WIDTH_RATIO = 16.0F / 9.6F;
    private static final int FULL_BRIGHT = 15728880;

    private final NpcEntity entity;

    public NpcEntityPreview(NpcEntity entity)
    {
        this.entity = entity;
    }

    @Override
    @SuppressWarnings("deprecation") // runAsFancy is what the vanilla inventory paperdoll still calls
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme)
    {
        float bbHeight = Math.max(0.25F, this.entity.getBbHeight());
        float bbWidth = Math.max(0.25F, this.entity.getBbWidth());
        float visualHeight = bbHeight * VISUAL_HEIGHT_RATIO;
        float visualWidth = bbWidth * VISUAL_WIDTH_RATIO;
        float usableHeight = height - NpcLayout.PREVIEW_HEAD_MARGIN - NpcLayout.PREVIEW_FOOT_MARGIN;
        float usableWidth = width - NpcLayout.PREVIEW_SIDE_MARGIN * 2.0F;
        if (usableHeight <= 1.0F || usableWidth <= 1.0F)
        {
            return;
        }
        float scale = Math.min(usableHeight / visualHeight, usableWidth / visualWidth);
        float feetY = y + height - NpcLayout.PREVIEW_FOOT_MARGIN;
        float centerX = x + width / 2.0F;
        float centerY = feetY - scale * visualHeight / 2.0F;

        float lookX = (float) Math.atan((centerX - context.getMouseX()) / 40.0D);
        float lookY = (float) Math.atan((centerY - context.getMouseY()) / 40.0D);
        Quaternionf poseRotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(lookY * 20.0F * ((float) Math.PI / 180.0F));
        poseRotation.mul(camera);

        float yRot = this.entity.getYRot();
        float yRotO = this.entity.yRotO;
        float xRot = this.entity.getXRot();
        float xRotO = this.entity.xRotO;
        float body = this.entity.yBodyRot;
        float bodyO = this.entity.yBodyRotO;
        float head = this.entity.yHeadRot;
        float headO = this.entity.yHeadRotO;

        PoseStack pose = context.getGraphics().pose();
        pose.pushPose();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean shadow = true;
        this.entity.setNameplateSuppressed(true);
        try
        {
            pose.translate(centerX, centerY, 50.0F);
            pose.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
            pose.translate(0.0F, visualHeight / 2.0F, 0.0F);
            pose.mulPose(poseRotation);
            // After the paperdoll pose, not before. A tint left by a textured
            // frame would otherwise multiply the whole model.
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            Lighting.setupForEntityInInventory();

            this.entity.yBodyRot = 180.0F + lookX * 20.0F;
            this.entity.setYRot(180.0F + lookX * 40.0F);
            this.entity.setXRot(-lookY * 20.0F);
            this.entity.yRotO = this.entity.getYRot();
            this.entity.xRotO = this.entity.getXRot();
            this.entity.yHeadRot = this.entity.getYRot();
            this.entity.yHeadRotO = this.entity.getYRot();
            this.entity.yBodyRotO = this.entity.yBodyRot;

            camera.conjugate();
            dispatcher.overrideCameraOrientation(camera);
            dispatcher.setRenderShadow(false);
            shadow = false;
            float partialTick = Minecraft.getInstance().getPartialTick();
            RenderSystem.runAsFancy(() -> dispatcher.render(this.entity, 0.0D, 0.0D, 0.0D, 0.0F, partialTick,
                    pose, context.getGraphics().bufferSource(), FULL_BRIGHT));
            context.getGraphics().flush();
        }
        finally
        {
            this.entity.setNameplateSuppressed(false);
            this.entity.setYRot(yRot);
            this.entity.yRotO = yRotO;
            this.entity.setXRot(xRot);
            this.entity.xRotO = xRotO;
            this.entity.yBodyRot = body;
            this.entity.yBodyRotO = bodyO;
            this.entity.yHeadRot = head;
            this.entity.yHeadRotO = headO;
            dispatcher.overrideCameraOrientation(null);
            if (!shadow)
            {
                dispatcher.setRenderShadow(true);
            }
            pose.popPose();
            Lighting.setupFor3DItems();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
