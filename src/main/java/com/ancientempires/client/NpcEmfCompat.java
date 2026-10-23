package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3f;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * While a Better Combat attack or hold pose is already on the model, hand that
 * one pose to EMF Compat so it does not overwrite the arms. Attack and hold
 * are not published together. EMF keeps the body when neither is active.
 */
final class NpcEmfCompat
{
    static final String ATTACK = "better_combat";
    static final String POSE = "better_combat_pose";

    private static Method emfState;
    private static Method rightArmOverride;
    private static Method leftArmOverride;
    private static Field attachmentPose;
    private static boolean placeProbeFailed;
    private static Boolean betterCombatCompat;

    private NpcEmfCompat()
    {
    }

    static void init()
    {
        PoseManager.setSourcePriority(POSE, -10);
    }

    /** The mod list does not change after load, so this is read once instead of per NPC per frame. */
    private static boolean betterCombatCompat()
    {
        Boolean loaded = betterCombatCompat;
        if (loaded == null)
        {
            loaded = ModList.get().isLoaded("emf_compat_better_combat");
            betterCombatCompat = loaded;
        }
        return loaded;
    }

    static void afterPose(NpcEntity npc, PlayerModel<?> model, boolean attackActive, boolean holdActive, float limbSwingAmount)
    {
        UUID uuid = npc.getUUID();
        boolean compat = betterCombatCompat();
        if (compat && !NpcEmfBetterCombat.enabled())
        {
            PoseManager.clearPoses(uuid, ATTACK);
            PoseManager.clearPoses(uuid, POSE);
            return;
        }
        boolean bodyFollow = !compat || NpcEmfBetterCombat.bodyFollow();
        boolean attackLegs = !compat || NpcEmfBetterCombat.attackLegs();
        if (holdActive)
        {
            PoseManager.savePoses(uuid, POSE, arm(model.leftArm), arm(model.rightArm), null, body(model, bodyFollow));
        }
        else
        {
            PoseManager.clearPoses(uuid, POSE);
        }
        if (attackActive)
        {
            if (compat)
            {
                NpcEmfBetterCombat.mark(uuid);
            }
            // EMF Compat keeps these snapshots by reference, so they are new each frame.
            Map<String, PoseSnapshot> legs = null;
            if (attackLegs && limbSwingAmount < 0.15F)
            {
                legs = Map.of(
                        "left_leg", new PoseSnapshot(model.leftLeg, true),
                        "right_leg", new PoseSnapshot(model.rightLeg, true));
            }
            PoseManager.savePoses(uuid, ATTACK, arm(model.leftArm), arm(model.rightArm), legs, body(model, bodyFollow));
            return;
        }
        if (compat)
        {
            NpcEmfBetterCombat.tick(uuid);
            if (!NpcEmfBetterCombat.holding(uuid))
            {
                PoseManager.clearPoses(uuid, ATTACK);
            }
            return;
        }
        PoseManager.clearPoses(uuid, ATTACK);
    }

    /**
     * True when EMF has an arm attachment ready to place the one held item.
     * The state type extends an Entity Texture Features class that is not in
     * libs, so this probe stays on reflection.
     */
    static boolean placesArm(HumanoidArm arm)
    {
        if (placeProbeFailed)
        {
            return false;
        }
        try
        {
            if (emfState == null)
            {
                Class<?> context = Class.forName("traben.entity_model_features.models.animation.EMFAnimationEntityContext");
                emfState = context.getMethod("getEmfState");
            }
            Object state = emfState.invoke(null);
            if (state == null)
            {
                return false;
            }
            boolean right = arm == HumanoidArm.RIGHT;
            Method override = right ? rightArmOverride : leftArmOverride;
            if (override == null)
            {
                override = state.getClass().getMethod(right ? "rightArmOverride" : "leftArmOverride");
                if (right)
                {
                    rightArmOverride = override;
                }
                else
                {
                    leftArmOverride = override;
                }
            }
            Object attachments = override.invoke(state);
            if (attachments == null)
            {
                return false;
            }
            if (attachmentPose == null)
            {
                attachmentPose = attachments.getClass().getField("pose");
            }
            return attachmentPose.get(attachments) != null;
        }
        catch (ReflectiveOperationException | LinkageError exception)
        {
            placeProbeFailed = true;
            return false;
        }
        catch (RuntimeException exception)
        {
            return false;
        }
    }

    private static PoseSnapshot arm(ModelPart part)
    {
        return new PoseSnapshot(part);
    }

    private static Vector3f body(PlayerModel<?> model, boolean bodyFollow)
    {
        if (!bodyFollow)
        {
            return null;
        }
        return new Vector3f(model.body.xRot, model.body.yRot, model.body.zRot);
    }
}
