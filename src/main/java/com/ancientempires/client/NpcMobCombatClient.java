package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import me.Thelnfamous1.bettermobcombat.api.MobAttackAnimation;
import me.Thelnfamous1.mobplayeranimator.api.PlayerAnimatorHelper;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Loaded only when Better Mob Combat and Mob Player Animator are both present.
 * Their stack is the only pose written onto the model. This reads it for the
 * held item and reports which channel is active so EMF can yield.
 */
final class NpcMobCombatClient
{
    private NpcMobCombatClient()
    {
    }

    static boolean attackActive(NpcEntity npc)
    {
        return npc instanceof MobAttackAnimation animation && animation.bettermobcombat$hasActiveAttackAnimation();
    }

    static boolean holdActive(NpcEntity npc)
    {
        if (!(npc instanceof MobAttackAnimation animation))
        {
            return false;
        }
        return animation.bettermobcombat$hasActiveMainHandBodyPose()
                || animation.bettermobcombat$hasActiveMainHandItemPose()
                || animation.bettermobcombat$hasActiveOffHandBodyPose()
                || animation.bettermobcombat$hasActiveOffHandItemPose();
    }

    static void bendHeldArm(NpcEntity npc, HumanoidArm arm, PoseStack pose)
    {
        AnimationApplier applier = PlayerAnimatorHelper.getAnimation(npc);
        NpcBetterCombatDriver.bend(applier, arm, pose);
    }

    static void placeHeldItem(NpcEntity npc, HumanoidArm arm, PoseStack pose)
    {
        AnimationApplier applier = PlayerAnimatorHelper.getAnimation(npc);
        NpcBetterCombatDriver.place(applier, arm, pose);
    }
}
