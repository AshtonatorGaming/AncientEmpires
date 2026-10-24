package com.ancientempires.combat;

import com.ancientempires.AncientEmpires;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Spartan Weaponry's throwing weapons (javelins, tomahawks, throwing knives,
 * boomerangs) for NPCs, by reflection, so nothing breaks without Spartan.
 * Spartan's own throw only works for players, so the projectile is built here
 * the way AncientTactics did: the item's entity class ({@code JavelinItem} to
 * {@code JavelinEntity}) with its (Level, LivingEntity) constructor. Ammo is
 * Spartan's own {@code AmmoUsed} count on the stack, so a player sees it too.
 * Lookups are cached per item class.
 */
public final class SpartanThrowing
{
    private static final String AMMO_USED = "AmmoUsed";
    private static final String ITEM_PACKAGE = "com.oblivioussp.spartanweaponry.item.";
    private static final String ENTITY_PACKAGE = "com.oblivioussp.spartanweaponry.entity.projectile.";

    private static final ClassValue<Optional<Constructor<?>>> CONSTRUCTORS = new ClassValue<>()
    {
        @Override
        protected Optional<Constructor<?>> computeValue(Class<?> type)
        {
            for (Class<?> at = type; at != null && at != Item.class; at = at.getSuperclass())
            {
                String name = at.getName();
                if (!name.startsWith(ITEM_PACKAGE) || !name.endsWith("Item"))
                {
                    continue;
                }
                String entity = ENTITY_PACKAGE + name.substring(ITEM_PACKAGE.length(), name.length() - 4) + "Entity";
                try
                {
                    return Optional.of(Class.forName(entity, false, type.getClassLoader()).getConstructor(Level.class, LivingEntity.class));
                }
                catch (ReflectiveOperationException | LinkageError ignored)
                {
                    // Not this one; try the parent class.
                }
            }
            return Optional.empty();
        }
    };

    private static final ClassValue<Optional<Method>> MAX_AMMO = method("getMaxAmmo", ItemStack.class);
    private static final ClassValue<Optional<Method>> MAX_CHARGE = method("getMaxChargeTicks", ItemStack.class);
    private static final ClassValue<Optional<Method>> DIRECT_DAMAGE = method("getDirectAttackDamage");
    private static final ClassValue<Optional<Method>> SET_WEAPON = method("setWeapon", ItemStack.class);

    private SpartanThrowing()
    {
    }

    private static ClassValue<Optional<Method>> method(String name, Class<?>... parameters)
    {
        return new ClassValue<>()
        {
            @Override
            protected Optional<Method> computeValue(Class<?> type)
            {
                try
                {
                    return Optional.of(type.getMethod(name, parameters));
                }
                catch (ReflectiveOperationException | LinkageError exception)
                {
                    return Optional.empty();
                }
            }
        };
    }

    /** A Spartan throwing weapon that this bridge can throw. */
    public static boolean isThrowing(ItemStack stack)
    {
        return !stack.isEmpty() && WeaponClassifier.isThrowing(stack) && CONSTRUCTORS.get(stack.getItem().getClass()).isPresent();
    }

    /** Throws left before the stack is spent. */
    public static int ammoLeft(ItemStack stack)
    {
        return Math.max(0, invokeInt(MAX_AMMO, stack.getItem(), 1, stack) - stack.getOrCreateTag().getInt(AMMO_USED));
    }

    public static int chargeTicks(ItemStack stack)
    {
        return Math.max(1, invokeInt(MAX_CHARGE, stack.getItem(), 20, stack));
    }

    /** One spent throw back (restocking while out of a fight). */
    public static void restockOne(ItemStack stack)
    {
        int used = stack.getOrCreateTag().getInt(AMMO_USED);
        if (used > 0)
        {
            stack.getOrCreateTag().putInt(AMMO_USED, used - 1);
        }
    }

    /**
     * The projectile for one throw, not yet aimed or added; spends one ammo.
     * Null if the weapon cannot be thrown or is out of ammo.
     */
    @Nullable
    public static AbstractArrow createThrow(Level level, LivingEntity thrower, ItemStack stack)
    {
        if (ammoLeft(stack) <= 0)
        {
            return null;
        }
        Optional<Constructor<?>> constructor = CONSTRUCTORS.get(stack.getItem().getClass());
        if (constructor.isEmpty())
        {
            return null;
        }
        try
        {
            Object created = constructor.get().newInstance(level, thrower);
            if (!(created instanceof AbstractArrow projectile))
            {
                return null;
            }
            Optional<Method> setWeapon = SET_WEAPON.get(created.getClass());
            if (setWeapon.isPresent())
            {
                setWeapon.get().invoke(created, stack.copyWithCount(1));
            }
            float damage = DIRECT_DAMAGE.get(stack.getItem().getClass())
                    .map(method -> invokeFloat(method, stack.getItem()))
                    .orElse(4.0F);
            // AncientTactics' figure, which matched a player's full throw.
            projectile.setBaseDamage((damage + 1.0D) * 2.0D);
            // The NPC keeps its stack and counts ammo instead, so the thrown one is not a free pickup.
            projectile.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            stack.getOrCreateTag().putInt(AMMO_USED, stack.getOrCreateTag().getInt(AMMO_USED) + 1);
            return projectile;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError exception)
        {
            AncientEmpires.LOGGER.debug("Could not throw {}", stack, exception);
            return null;
        }
    }

    private static int invokeInt(ClassValue<Optional<Method>> lookup, Object target, int fallback, Object... arguments)
    {
        Optional<Method> method = lookup.get(target.getClass());
        if (method.isEmpty())
        {
            return fallback;
        }
        try
        {
            return ((Number) method.get().invoke(target, arguments)).intValue();
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            return fallback;
        }
    }

    private static float invokeFloat(Method method, Object target)
    {
        try
        {
            return ((Number) method.invoke(target)).floatValue();
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            return 4.0F;
        }
    }
}
