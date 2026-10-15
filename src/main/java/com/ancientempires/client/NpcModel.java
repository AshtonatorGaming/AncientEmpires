package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Player model with Better Combat's stack applied after the vanilla pose.
 * Without that stack, a swing in progress holds the main arm up.
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
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        boolean posed = NpcCombatAnimations.apply(entity, this, ageInTicks - entity.tickCount);
        if (!posed && entity.isAttackWindowOpen())
        {
            ModelPart arm = entity.getMainArm() == HumanoidArm.LEFT ? this.leftArm : this.rightArm;
            arm.xRot = -1.2F;
            arm.yRot = 0.0F;
            arm.zRot = entity.getMainArm() == HumanoidArm.LEFT ? -0.1F : 0.1F;
        }
        if (posed || entity.isAttackWindowOpen())
        {
            this.hat.copyFrom(this.head);
            this.jacket.copyFrom(this.body);
            this.leftSleeve.copyFrom(this.leftArm);
            this.rightSleeve.copyFrom(this.rightArm);
            this.leftPants.copyFrom(this.leftLeg);
            this.rightPants.copyFrom(this.rightLeg);
        }
        NpcCombatAnimations.yieldToEmf(entity, this, limbSwingAmount);
    }
}
