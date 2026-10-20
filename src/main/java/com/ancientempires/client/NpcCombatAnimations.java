package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.combat.BetterCombatBridge;
import com.ancientempires.combat.MobCombatBridge;
import com.ancientempires.entity.npc.NpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Better Mob Combat and Mob Player Animator own the pose when they are loaded.
 * The hand-built Better Combat stack is only the fallback. EMF is asked to
 * yield its arms while one of those poses is active, and it does not receive
 * a second pose of our own.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NpcCombatAnimations
{
    private static final String PLAYER_ANIMATOR = "playeranimator";
    private static final String MOB_ANIMATOR = "mobplayeranimator";
    private static final String EMF = "entity_model_features";
    private static final String EMF_COMPAT = "emf_compat_core";

    private enum Status
    {
        UNCHECKED,
        READY,
        ABSENT,
        FAILED
    }

    private static Status poses = Status.UNCHECKED;
    private static Status mobCombat = Status.UNCHECKED;
    private static Status emf = Status.UNCHECKED;

    private NpcCombatAnimations()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || mobAnimatorOwnsModel() || !posesReady())
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused())
        {
            return;
        }
        try
        {
            for (Entity entity : minecraft.level.entitiesForRendering())
            {
                if (entity instanceof NpcEntity npc)
                {
                    NpcBetterCombatDriver.tick(npc);
                }
            }
        }
        catch (Throwable exception)
        {
            failPoses(exception);
        }
    }

    /**
     * Copies the fallback Better Combat stack onto the model. Returns false when
     * Better Mob Combat is already posing the model, or when there is nothing to play.
     */
    public static boolean apply(NpcEntity npc, PlayerModel<NpcEntity> model, float partialTick)
    {
        if (mobAnimatorOwnsModel() || !posesReady())
        {
            return false;
        }
        try
        {
            return NpcBetterCombatDriver.apply(npc, model, partialTick);
        }
        catch (Throwable exception)
        {
            failPoses(exception);
            return false;
        }
    }

    public static void bendHeldArm(NpcEntity npc, HumanoidArm arm, PoseStack pose)
    {
        if (mobAnimatorOwnsModel())
        {
            if (mobCombat != Status.READY)
            {
                return;
            }
            try
            {
                NpcMobCombatClient.bendHeldArm(npc, arm, pose);
            }
            catch (Throwable exception)
            {
                failMobCombat(exception);
            }
            return;
        }
        if (!posesReady())
        {
            return;
        }
        try
        {
            NpcBetterCombatDriver.bendHeldArm(npc, arm, pose);
        }
        catch (Throwable exception)
        {
            failPoses(exception);
        }
    }

    public static void placeHeldItem(NpcEntity npc, HumanoidArm arm, PoseStack pose)
    {
        if (mobAnimatorOwnsModel())
        {
            if (mobCombat != Status.READY)
            {
                return;
            }
            try
            {
                NpcMobCombatClient.placeHeldItem(npc, arm, pose);
            }
            catch (Throwable exception)
            {
                failMobCombat(exception);
            }
            return;
        }
        if (!posesReady())
        {
            return;
        }
        try
        {
            NpcBetterCombatDriver.placeHeldItem(npc, arm, pose);
        }
        catch (Throwable exception)
        {
            failPoses(exception);
        }
    }

    /**
     * EMF is about to place the held item on this arm, so the caller must not
     * draw a second copy.
     */
    public static boolean emfPlacesArm(HumanoidArm arm)
    {
        if (!emfReady())
        {
            return false;
        }
        try
        {
            return NpcEmfCompat.placesArm(arm);
        }
        catch (Throwable exception)
        {
            failEmf(exception);
            return false;
        }
    }

    public static void yieldToEmf(NpcEntity npc, PlayerModel<NpcEntity> model, float limbSwingAmount)
    {
        if (!emfReady())
        {
            return;
        }
        try
        {
            boolean attack;
            boolean hold;
            if (mobCombat == Status.READY)
            {
                try
                {
                    attack = NpcMobCombatClient.attackActive(npc);
                    // One arm pose at a time. The attack clip replaces the hold, then the hold comes back.
                    hold = !attack && NpcMobCombatClient.holdActive(npc);
                }
                catch (Throwable exception)
                {
                    failMobCombat(exception);
                    return;
                }
            }
            else
            {
                attack = poses == Status.READY && NpcBetterCombatDriver.attackActive(npc);
                hold = poses == Status.READY && NpcBetterCombatDriver.holdActive(npc);
                if (!attack && npc.isAttackWindowOpen())
                {
                    attack = true;
                }
            }
            NpcEmfCompat.afterPose(npc, model, attack, hold, limbSwingAmount);
        }
        catch (Throwable exception)
        {
            failEmf(exception);
        }
    }

    /**
     * The mob animator is playing the attack clip that was started for this swing.
     * The model's static raised arm would replace that clip on the same frame.
     */
    public static boolean attackClipOwnsArms(NpcEntity npc)
    {
        return mobAnimatorOwnsModel() && !npc.getAttackPresentationId().isEmpty();
    }

    /**
     * True once Better Mob Combat's own stack is on the mob. A failed read still
     * leaves that stack in place, so the fallback stack must stay idle.
     */
    private static boolean mobAnimatorOwnsModel()
    {
        mobCombatReady();
        return mobCombat == Status.READY || mobCombat == Status.FAILED;
    }

    private static boolean mobCombatReady()
    {
        if (mobCombat == Status.READY)
        {
            return true;
        }
        if (mobCombat != Status.UNCHECKED)
        {
            return false;
        }
        if (!ModList.get().isLoaded(MobCombatBridge.MOD_ID)
                || !ModList.get().isLoaded(MOB_ANIMATOR)
                || !ModList.get().isLoaded(BetterCombatBridge.MOD_ID)
                || !ModList.get().isLoaded(PLAYER_ANIMATOR))
        {
            mobCombat = Status.ABSENT;
            return false;
        }
        try
        {
            Class.forName("me.Thelnfamous1.bettermobcombat.api.MobAttackAnimation");
            Class.forName("me.Thelnfamous1.mobplayeranimator.api.PlayerAnimatorHelper");
            mobCombat = Status.READY;
            AncientEmpires.LOGGER.info("Better Mob Combat and Mob Player Animator are posing NPCs. The fallback stack stays idle.");
            return true;
        }
        catch (Throwable exception)
        {
            failMobCombat(exception);
            return false;
        }
    }

    private static boolean posesReady()
    {
        if (poses == Status.READY)
        {
            return true;
        }
        if (poses != Status.UNCHECKED)
        {
            return false;
        }
        if (!ModList.get().isLoaded(BetterCombatBridge.MOD_ID) || !ModList.get().isLoaded(PLAYER_ANIMATOR))
        {
            poses = Status.ABSENT;
            return false;
        }
        try
        {
            NpcBetterCombatDriver.init();
            poses = Status.READY;
            AncientEmpires.LOGGER.info("Better Combat attack and idle animations are available for NPCs.");
            return true;
        }
        catch (Throwable exception)
        {
            failPoses(exception);
            return false;
        }
    }

    private static boolean emfReady()
    {
        if (emf == Status.READY)
        {
            return true;
        }
        if (emf != Status.UNCHECKED)
        {
            return false;
        }
        if (!ModList.get().isLoaded(EMF) || !ModList.get().isLoaded(EMF_COMPAT))
        {
            emf = Status.ABSENT;
            return false;
        }
        try
        {
            NpcEmfCompat.init();
            emf = Status.READY;
            AncientEmpires.LOGGER.info("EMF will keep NPC idle movement and yield arms to Better Combat poses.");
            return true;
        }
        catch (Throwable exception)
        {
            failEmf(exception);
            return false;
        }
    }

    private static void failMobCombat(Throwable exception)
    {
        if (mobCombat != Status.FAILED)
        {
            mobCombat = Status.FAILED;
            AncientEmpires.LOGGER.warn("Better Mob Combat poses could not be read. NPCs will keep Mob Player Animator's stack and skip the fallback stack.", exception);
        }
    }

    private static void failPoses(Throwable exception)
    {
        if (poses != Status.FAILED)
        {
            poses = Status.FAILED;
            AncientEmpires.LOGGER.warn("Better Combat animations could not be attached to NPCs. They will use the vanilla arm swing.", exception);
        }
    }

    private static void failEmf(Throwable exception)
    {
        if (emf != Status.FAILED)
        {
            emf = Status.FAILED;
            AncientEmpires.LOGGER.warn("EMF compatibility for NPC poses could not start. Better Combat still poses the vanilla model.", exception);
        }
    }
}
