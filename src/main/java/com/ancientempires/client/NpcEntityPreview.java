package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.inventory.NpcLayout;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Inventory paperdoll. The draw uses the same lights as the player inventory
 * screen, and the scale comes from the hitbox so a short body and a giant both
 * land inside the frame with the feet near the bottom. Clipped to its box.
 */
@OnlyIn(Dist.CLIENT)
public final class NpcEntityPreview
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

    /**
     * Draws the NPC in the box, facing the mouse. A screen drawn under a popup gets
     * {@link Integer#MAX_VALUE} for the mouse; the NPC then looks straight ahead.
     */
    public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY)
    {
        this.render(graphics, x, y, width, height, mouseX, mouseY, 1.0F);
    }

    /**
     * As above, the figure drawn at {@code fill} of the size that fits (a portrait with room around it); the box still
     * clips, so a smaller figure is never cut short of the box's edge.
     */
    @SuppressWarnings("deprecation") // runAsFancy is what the vanilla inventory paperdoll still calls
    public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float fill)
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
        float scale = Math.min(usableHeight / visualHeight, usableWidth / visualWidth) * fill;
        // A smaller figure still stands on the box's floor, with the room above it.
        float feetY = y + height - NpcLayout.PREVIEW_FOOT_MARGIN - (fill < 1.0F ? 6.0F : 0.0F);
        float centerX = x + width / 2.0F;
        float centerY = feetY - scale * visualHeight / 2.0F;

        boolean mouse = mouseX != Integer.MAX_VALUE && mouseY != Integer.MAX_VALUE;
        float lookX = mouse ? (float) Math.atan((centerX - mouseX) / 40.0D) : 0.0F;
        float lookY = mouse ? (float) Math.atan((centerY - mouseY) / 40.0D) : 0.0F;
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

        PoseStack pose = graphics.pose();
        graphics.enableScissor(x, y, x + width, y + height);
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
                    pose, graphics.bufferSource(), FULL_BRIGHT));
            graphics.flush();
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
            graphics.disableScissor();
            Lighting.setupFor3DItems();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
