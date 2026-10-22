package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.combat.BetterCombatBridge;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Weapon trails from Better Combat Particle ({@code bc_particle}) on NPC
 * swings. That mod draws mob trails from Better Mob Combat's client hit event,
 * which does not fire on our own attack path. The animation driver calls this
 * at the hit frame instead, on each client, with the same placement that mod
 * computes for mobs. Nothing goes over the network. Reflection only; absent or
 * changed, this stays off.
 */
final class NpcTrailParticles
{
    private static final String MOD_ID = "bc_particle";

    private enum Status
    {
        UNCHECKED,
        READY,
        ABSENT,
        FAILED
    }

    private static Status status = Status.UNCHECKED;
    private static Object settingsLoader;
    private static Method getSettings;
    private static Method resolveColors;
    private static Method colorHex;
    private static Method colorHexSec;
    private static Method light;
    private static Method xAddition;
    private static Method yAddition;
    private static Method zAddition;
    private static Method spawn;
    private static Field modConfig;
    private static Field showMobParticles;

    private NpcTrailParticles()
    {
    }

    /**
     * Spawns the trail for one swing. {@code offHand} is the off-hand beat of
     * a fist combo or a dual-wield pair; it picks the item and mirrors the trail.
     */
    static void spawn(NpcEntity npc, String animationId, boolean offHand)
    {
        if (!ready() || !(npc.level() instanceof ClientLevel level))
        {
            return;
        }
        try
        {
            if (!showMobParticles())
            {
                return;
            }
            Object found = getSettings.invoke(settingsLoader, animationId);
            if (!(found instanceof List<?> settings) || settings.isEmpty())
            {
                return;
            }
            ItemStack stack = offHand ? npc.getOffhandItem() : npc.getMainHandItem();
            Object colors = resolveColors.invoke(null, stack);
            boolean glow = (Boolean) light.invoke(colors);
            String primary = (String) colorHex.invoke(colors);
            String secondary = (String) colorHexSec.invoke(colors);
            float range = (float) weaponRange(stack);

            // The same placement that mod uses for a mob: a point beside the eyes,
            // shifted per setting, and a stab point the weapon's reach ahead of it.
            boolean leftHanded = npc.getMainArm() == HumanoidArm.LEFT;
            boolean flip = leftHanded != offHand;
            float side = flip ? -1.0F : 1.0F;
            float yaw = npc.getYRot();
            float pitch = npc.getXRot();
            Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F).normalize();
            Vec3 forward = Vec3.directionFromRotation(pitch, yaw).normalize();
            for (Object setting : settings)
            {
                float x = (Float) xAddition.invoke(setting);
                float y = (Float) yAddition.invoke(setting);
                float z = (Float) zAddition.invoke(setting);
                Vec3 base = new Vec3(npc.getX(), npc.getEyeY() - 0.25D + y, npc.getZ());
                Vec3 at = base.add(forward.scale(z)).add(right.scale(x * side));
                Vec3 stab = at.add(forward.scale(range - 1.5D));
                spawn.invoke(null, level, at.x, at.y, at.z, stab.x, stab.y, stab.z,
                        pitch, yaw, range, flip, List.of(setting), glow, primary, secondary);
            }
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            fail(exception);
        }
    }

    private static double weaponRange(ItemStack stack)
    {
        var weapon = stack.isEmpty() ? BetterCombatBridge.lookupFist() : BetterCombatBridge.lookup(stack);
        if (weapon.isPresent() && weapon.get().range() > 0.0D)
        {
            return weapon.get().range();
        }
        return stack.isEmpty() ? AncientEmpiresConfig.unarmedRange : AncientEmpiresConfig.vanillaRange;
    }

    /** That mod's own switch for trails on mobs. Missing means on. */
    private static boolean showMobParticles() throws IllegalAccessException
    {
        if (showMobParticles == null)
        {
            return true;
        }
        Object config = modConfig.get(null);
        return config == null || showMobParticles.getBoolean(config);
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
        if (!ModList.get().isLoaded(MOD_ID))
        {
            status = Status.ABSENT;
            return false;
        }
        try
        {
            bind();
            status = Status.READY;
            AncientEmpires.LOGGER.info("Better Combat Particle trails are enabled for NPC swings.");
            return true;
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException exception)
        {
            fail(exception);
            return false;
        }
    }

    private static void bind() throws ReflectiveOperationException
    {
        Class<?> loader = Class.forName("malfu.bc_particle.config.ParticleSettingsLoader");
        settingsLoader = loader.getField("INSTANCE").get(null);
        getSettings = loader.getMethod("getSettings", String.class);
        resolveColors = Class.forName("malfu.bc_particle.config.ColorChangerSettings").getMethod("resolveWeaponEffects", ItemStack.class);
        Class<?> colors = Class.forName("malfu.bc_particle.config.ColorLightSettings");
        colorHex = colors.getMethod("colorHex");
        colorHexSec = colors.getMethod("colorHexSec");
        light = colors.getMethod("light");
        Class<?> settings = Class.forName("malfu.bc_particle.config.ParticleSettings");
        xAddition = settings.getMethod("xAddition");
        yAddition = settings.getMethod("yAddition");
        zAddition = settings.getMethod("zAddition");
        spawn = Class.forName("malfu.bc_particle.network.ClientNetworkHandler").getMethod("spawnParticleFromNetwork",
                ClientLevel.class, double.class, double.class, double.class, double.class, double.class, double.class,
                float.class, float.class, float.class, boolean.class, List.class, boolean.class, String.class, String.class);
        modConfig = Class.forName("malfu.bc_particle.BetterCombatParticleMods").getField("config");
        try
        {
            showMobParticles = modConfig.getType().getField("showBMCParticle");
        }
        catch (NoSuchFieldException exception)
        {
            showMobParticles = null;
        }
    }

    private static void fail(Throwable exception)
    {
        if (status != Status.FAILED)
        {
            status = Status.FAILED;
            AncientEmpires.LOGGER.warn("Better Combat Particle is loaded, but NPC weapon trails could not start.", exception);
        }
    }
}
