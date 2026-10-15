package com.ancientempires.combat;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.ModList;

import java.util.function.BiConsumer;

/**
 * Better Mob Combat is optional. When it is loaded and the held weapon has a
 * Better Combat attack, it owns the upswing. This mod still applies the hit.
 */
public final class MobCombatBridge
{
    public static final String MOD_ID = "bettermobcombat";

    private enum Status
    {
        UNCHECKED,
        READY,
        ABSENT,
        FAILED
    }

    private static Status status = Status.UNCHECKED;

    private MobCombatBridge()
    {
    }

    public static boolean owns(NpcEntity npc)
    {
        return AncientEmpiresConfig.betterCombatEnabled && ready() && use(npc);
    }

    public static boolean attackReady(NpcEntity npc)
    {
        return ready() && MobCombatHooks.attackReady(npc);
    }

    public static boolean tryStart(NpcEntity npc, BiConsumer<Mob, Entity> hit)
    {
        if (!ready())
        {
            return false;
        }
        try
        {
            return MobCombatHooks.tryStart(npc, hit);
        }
        catch (Throwable exception)
        {
            fail(exception);
            return false;
        }
    }

    public static boolean upswinging(NpcEntity npc)
    {
        if (status != Status.READY)
        {
            return false;
        }
        try
        {
            return MobCombatHooks.upswinging(npc);
        }
        catch (Throwable exception)
        {
            fail(exception);
            return false;
        }
    }

    public static int attackCooldown(NpcEntity npc)
    {
        if (status != Status.READY)
        {
            return 0;
        }
        try
        {
            return MobCombatHooks.attackCooldown(npc);
        }
        catch (Throwable exception)
        {
            fail(exception);
            return 0;
        }
    }

    public static void cancel(NpcEntity npc)
    {
        if (status != Status.READY)
        {
            return;
        }
        try
        {
            MobCombatHooks.cancel(npc);
        }
        catch (Throwable exception)
        {
            fail(exception);
        }
    }

    private static boolean use(NpcEntity npc)
    {
        try
        {
            return MobCombatHooks.canUse(npc);
        }
        catch (Throwable exception)
        {
            fail(exception);
            return false;
        }
    }

    private static boolean ready()
    {
        if (status == Status.READY)
        {
            return true;
        }
        if (status != Status.UNCHECKED)
        {
            return false;
        }
        if (!ModList.get().isLoaded(MOD_ID) || !ModList.get().isLoaded(BetterCombatBridge.MOD_ID))
        {
            status = Status.ABSENT;
            return false;
        }
        try
        {
            Class.forName("me.Thelnfamous1.bettermobcombat.api.MobAttackWindup");
            status = Status.READY;
            AncientEmpires.LOGGER.info("Better Mob Combat owns NPC attack timing. This mod still applies the hit.");
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
        if (status != Status.FAILED)
        {
            status = Status.FAILED;
            AncientEmpires.LOGGER.warn("Better Mob Combat could not drive NPC attacks. The internal wind-up will be used.", exception);
        }
    }
}
