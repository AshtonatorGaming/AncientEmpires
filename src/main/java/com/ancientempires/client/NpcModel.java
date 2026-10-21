package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Player model. Better Mob Combat's stack is applied inside the vanilla setup
 * when that mod is loaded. Otherwise the fallback stack is copied on here.
 * Without either, a swing in progress holds the main arm up.
 */
public class NpcModel extends PlayerModel<NpcEntity>
{
    public NpcModel(ModelPart root)
    {
        super(root, false);
    }

    @Override
    public void setupAnim(NpcEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        boolean drawingBow = entity.isDrawingBow();
        if (drawingBow)
        {
            this.armBow(entity);
        }
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (drawingBow)
        {
            // Mob animator and the vanilla off-hand pose both run inside super.
            // Put the draw back on after them, and do not let a combat pose replace it.
            this.applyBowArms();
            this.copyWear();
            NpcCombatAnimations.yieldBowDraw(entity, this, limbSwingAmount);
            return;
        }
        boolean posed = NpcCombatAnimations.apply(entity, this, ageInTicks - entity.tickCount);
        if (!posed && entity.isAttackWindowOpen() && !NpcCombatAnimations.attackClipOwnsArms(entity))
        {
            ModelPart arm = entity.getMainArm() == HumanoidArm.LEFT ? this.leftArm : this.rightArm;
            arm.xRot = -1.2F;
            arm.yRot = 0.0F;
            arm.zRot = entity.getMainArm() == HumanoidArm.LEFT ? -0.1F : 0.1F;
        }
        if (posed || entity.isAttackWindowOpen())
        {
            this.copyWear();
        }
        NpcCombatAnimations.yieldToEmf(entity, this, limbSwingAmount);
    }

    /**
     * Right-handed draw raises both arms. A left-handed main arm swaps the pull hand.
     * The numbers match {@link HumanoidModel}'s bow pose.
     */
    private void armBow(NpcEntity entity)
    {
        boolean left = entity.getMainArm() == HumanoidArm.LEFT;
        this.rightArmPose = left ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.BOW_AND_ARROW;
        this.leftArmPose = left ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.EMPTY;
    }

    private void applyBowArms()
    {
        float xRot = (-(float) Math.PI / 2.0F) + this.head.xRot;
        if (this.leftArmPose == HumanoidModel.ArmPose.BOW_AND_ARROW)
        {
            this.rightArm.yRot = -0.1F + this.head.yRot - 0.4F;
            this.leftArm.yRot = 0.1F + this.head.yRot;
        }
        else
        {
            this.rightArm.yRot = -0.1F + this.head.yRot;
            this.leftArm.yRot = 0.1F + this.head.yRot + 0.4F;
        }
        this.rightArm.xRot = xRot;
        this.leftArm.xRot = xRot;
        this.rightArm.zRot = 0.0F;
        this.leftArm.zRot = 0.0F;
    }

    private void copyWear()
    {
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
    }
}
