package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * One held item. When EMF has an arm attachment, the vanilla method is the
 * draw EMF repositions. Otherwise this layer is the only draw, including the
 * item bend Player Animation Library would apply to a player.
 */
public class NpcHeldItemLayer extends ItemInHandLayer<NpcEntity, NpcModel>
{
    private final ItemInHandRenderer items;

    public NpcHeldItemLayer(RenderLayerParent<NpcEntity, NpcModel> parent, ItemInHandRenderer items)
    {
        super(parent, items);
        this.items = items;
    }

    @Override
    protected void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose, MultiBufferSource buffer, int light)
    {
        if (stack.isEmpty() || !(entity instanceof NpcEntity npc))
        {
            return;
        }
        if (NpcCombatAnimations.emfPlacesArm(arm))
        {
            super.renderArmWithItem(entity, stack, context, arm, pose, buffer, light);
            return;
        }
        pose.pushPose();
        ((ArmedModel) this.getParentModel()).translateToHand(arm, pose);
        NpcCombatAnimations.bendHeldArm(npc, arm, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        boolean left = arm == HumanoidArm.LEFT;
        pose.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        NpcCombatAnimations.placeHeldItem(npc, arm, pose);
        this.items.renderItem(npc, stack, context, left, pose, buffer, light);
        pose.popPose();
    }
}
