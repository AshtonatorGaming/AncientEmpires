package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.fml.ModList;

/**
 * Player model. Better Mob Combat's stack is applied inside the vanilla setup
 * when that mod is loaded. Otherwise the fallback stack is copied on here.
 * Without either, or when a weapon's clip is missing, the arm is drawn back
 * through the wind-up and the vanilla swing plays at the hit.
 */
public class NpcModel extends PlayerModel<NpcEntity>
{
    /** Not Enough Animations: players eat with the food at their mouth in third person. */
    private static final boolean NEA = ModList.get().isLoaded("notenoughanimations");
    /** Its EMF add-on: burning and freezing reactions. */
    private static final boolean NEA_EMF = ModList.get().isLoaded("emf_compat_not_enough_animations");

    public NpcModel(ModelPart root)
    {
        super(root, false);
    }

    @Override
    public void setupAnim(NpcEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        // One model draws every NPC, so the arm poses are set fresh for each.
        this.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        this.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        boolean drawingBow = entity.isDrawingBow();
        if (drawingBow)
        {
            this.armBow(entity);
        }
        else if (entity.isUsingItem() && entity.getUseItem().getUseAnimation() == UseAnim.BLOCK)
        {
            // The vanilla raised-shield pose on whichever arm holds the shield.
            boolean leftArm = (entity.getUsedItemHand() == InteractionHand.MAIN_HAND) == (entity.getMainArm() == HumanoidArm.LEFT);
            if (leftArm)
            {
                this.leftArmPose = HumanoidModel.ArmPose.BLOCK;
            }
            else
            {
                this.rightArmPose = HumanoidModel.ArmPose.BLOCK;
            }
        }
        if (this.attackTime > 0.0F && NpcCombatAnimations.attackClipPlaying(entity))
        {
            // A Better Combat clip owns the arms. The vanilla swing would shove them on
            // every axis the clip leaves free, which Better Combat cancels for players too.
            this.attackTime = 0.0F;
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
        if (!posed && !entity.isAttackWindowOpen())
        {
            posed = this.reactionPose(entity, ageInTicks);
        }
        if (!posed && entity.isAttackWindowOpen() && this.attackTime <= 0.0F && !NpcCombatAnimations.attackClipOwnsArms(entity))
        {
            // Vanilla fallback wind-up: the arm drawn back until the swing at the hit.
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
     * Flavor poses borrowed from animation mods, shown only when such a mod is
     * installed (soft dependencies; without them NPCs look as vanilla mobs do):
     * eating and drinking with Not Enough Animations (food raised to the mouth),
     * and with its EMF add-on the burning flail and the freezing shiver.
     */
    private boolean reactionPose(NpcEntity entity, float ageInTicks)
    {
        if (NEA && entity.isUsingItem())
        {
            UseAnim anim = entity.getUseItem().getUseAnimation();
            if (anim == UseAnim.EAT || anim == UseAnim.DRINK)
            {
                boolean leftArm = (entity.getUsedItemHand() == InteractionHand.MAIN_HAND) == (entity.getMainArm() == HumanoidArm.LEFT);
                ModelPart arm = leftArm ? this.leftArm : this.rightArm;
                // Hand at the mouth, with a small chewing bob.
                arm.xRot = -1.45F + Mth.sin(ageInTicks * 1.6F) * 0.08F;
                arm.yRot = leftArm ? 0.45F : -0.45F;
                arm.zRot = 0.0F;
                this.head.xRot = Math.max(this.head.xRot, 0.1F);
                return true;
            }
        }
        if (!NEA_EMF)
        {
            return false;
        }
        if (entity.isOnFire() && !entity.isInWaterOrRain())
        {
            // Flailing, arms up and beating, out of step.
            this.rightArm.xRot = -2.6F + Mth.sin(ageInTicks * 0.9F) * 0.6F;
            this.leftArm.xRot = -2.6F + Mth.cos(ageInTicks * 0.9F) * 0.6F;
            this.rightArm.zRot = 0.25F + Mth.cos(ageInTicks * 0.7F) * 0.2F;
            this.leftArm.zRot = -0.25F - Mth.sin(ageInTicks * 0.7F) * 0.2F;
            this.rightArm.yRot = 0.0F;
            this.leftArm.yRot = 0.0F;
            this.head.zRot = Mth.sin(ageInTicks * 0.5F) * 0.15F;
            return true;
        }
        if (entity.getTicksFrozen() > 0)
        {
            // Arms hugged to the chest, shivering.
            float shiver = Mth.sin(ageInTicks * 3.0F) * 0.04F;
            this.rightArm.xRot = -0.9F + shiver;
            this.leftArm.xRot = -0.9F - shiver;
            this.rightArm.yRot = -0.6F;
            this.leftArm.yRot = 0.6F;
            this.rightArm.zRot = 0.0F;
            this.leftArm.zRot = 0.0F;
            return true;
        }
        return false;
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
