package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Moonlight's held-item hooks, which Amendments (3D held lanterns, torches) and
 * Supplementaries use. Its arm poses already reach NPCs through the shared
 * humanoid model, but its item renderer is only hooked into the player's
 * held-item layer, so NPCs held the flat item. This calls that renderer from
 * ours. Soft: reflection only, and any failure turns it off for the session.
 */
@OnlyIn(Dist.CLIENT)
final class NpcMoonlightCompat
{
    private static final boolean LOADED = ModList.get().isLoaded("moonlight");
    private static boolean failed;
    private static Method getRenderer;
    private static Method renderItem;
    private static Method getAnimation;
    /** Per item: Moonlight's renderer (or NO_RENDERER) and whether it poses the arm. Asked every frame. */
    private static final Map<Item, Object> RENDERERS = new IdentityHashMap<>();
    private static final Map<Item, Boolean> POSES = new IdentityHashMap<>();
    private static final Object NO_RENDERER = new Object();

    private NpcMoonlightCompat()
    {
    }

    /** Moonlight poses the arm holding this item (Amendments' lantern and torch holds). */
    static boolean posesArm(ItemStack stack)
    {
        if (!ready() || stack.isEmpty())
        {
            return false;
        }
        try
        {
            Boolean poses = POSES.get(stack.getItem());
            if (poses == null)
            {
                poses = getAnimation.invoke(null, stack.getItem()) != null;
                POSES.put(stack.getItem(), poses);
            }
            return poses;
        }
        catch (Throwable exception)
        {
            fail(exception);
            return false;
        }
    }

    /** Draws the held item with Moonlight's special renderer if it has one. False if not drawn. */
    static boolean render(EntityModel<?> model, LivingEntity entity, ItemStack stack, HumanoidArm arm, PoseStack pose, MultiBufferSource buffer, int light)
    {
        if (!ready() || stack.isEmpty())
        {
            return false;
        }
        try
        {
            Object renderer = RENDERERS.get(stack.getItem());
            if (renderer == null)
            {
                renderer = getRenderer.invoke(null, stack.getItem());
                RENDERERS.put(stack.getItem(), renderer == null ? NO_RENDERER : renderer);
            }
            if (renderer == null || renderer == NO_RENDERER)
            {
                return false;
            }
            renderItem.invoke(renderer, model, entity, stack, arm, pose, buffer, light);
            return true;
        }
        catch (Throwable exception)
        {
            fail(exception);
            return false;
        }
    }

    private static boolean ready()
    {
        if (!LOADED || failed)
        {
            return false;
        }
        if (getRenderer != null)
        {
            return true;
        }
        try
        {
            Class<?> special = Class.forName("net.mehvahdjukaar.moonlight.api.item.IThirdPersonSpecialItemRenderer");
            Class<?> animation = Class.forName("net.mehvahdjukaar.moonlight.api.item.IThirdPersonAnimationProvider");
            getAnimation = animation.getMethod("get", Item.class);
            renderItem = special.getMethod("renderThirdPersonItem", EntityModel.class, LivingEntity.class, ItemStack.class, HumanoidArm.class,
                    PoseStack.class, MultiBufferSource.class, int.class);
            getRenderer = special.getMethod("get", Item.class);
            return true;
        }
        catch (Throwable exception)
        {
            fail(exception);
            return false;
        }
    }

    private static void fail(Throwable exception)
    {
        failed = true;
        AncientEmpires.LOGGER.warn("Moonlight held-item compat is off for this session", exception);
    }
}
