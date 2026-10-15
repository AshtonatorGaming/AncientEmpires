package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.combat.BetterCombatBridge;
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
 * Better Combat and Player Animation Library are the pose path. EMF is asked
 * to yield its arms only while one of those poses is active. Either animation
 * mod being absent leaves the vanilla arm pose in place.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NpcCombatAnimations
{
    private static final String PLAYER_ANIMATOR = "playeranimator";
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
    private static Status emf = Status.UNCHECKED;

    private NpcCombatAnimations()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !posesReady())
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
     * Copies the active Better Combat stack onto the model. Returns false when
     * there is nothing to play, including when the animation mods are absent.
     */
    public static boolean apply(NpcEntity npc, PlayerModel<NpcEntity> model, float partialTick)
    {
        if (!posesReady())
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
            boolean attack = poses == Status.READY && NpcBetterCombatDriver.attackActive(npc);
            boolean hold = poses == Status.READY && NpcBetterCombatDriver.holdActive(npc);
            if (!attack && npc.isAttackWindowOpen())
            {
                attack = true;
            }
            NpcEmfCompat.afterPose(npc, model, attack, hold, limbSwingAmount);
        }
        catch (Throwable exception)
        {
            failEmf(exception);
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
