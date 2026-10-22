package com.ancientempires.combat;

import com.ancientempires.AncientEmpires;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads Better Combat weapon attributes through reflection so this mod still
 * compiles and runs when Better Combat is absent. Attribute data only:
 * range, the attack list, upswing, damage multiplier, angle, animation id,
 * swing sound, idle pose, and whether the weapon is two-handed. Better Combat
 * never applies the hit.
 */
public final class BetterCombatBridge
{
    public static final String MOD_ID = "bettercombat";
    private static final String FIST_ID = "bettercombat:fist";
    /** Better Combat's per-stack attribute override, a JSON string tag. */
    private static final String STACK_ATTRIBUTES_KEY = "weapon_attributes";

    private enum Status
    {
        UNCHECKED,
        READY,
        FAILED
    }

    private static Status status = Status.UNCHECKED;

    private static Method attributesForStack;
    @Nullable
    private static Method attributesForId;
    @Nullable
    private static Field containersField;

    private static Method attackRange;
    private static Method attacks;
    private static Method damageMultiplier;
    private static Method upswing;
    private static Method angle;
    private static Method animation;
    @Nullable
    private static Method pose;
    @Nullable
    private static Method twoHanded;
    @Nullable
    private static Method conditions;
    @Nullable
    private static Method hitbox;
    @Nullable
    private static Method swingSound;

    @Nullable
    private static Method soundId;
    @Nullable
    private static Method soundVolume;
    @Nullable
    private static Method soundPitch;
    @Nullable
    private static Method soundRandomness;

    /** Per-item snapshots, valid while {@link #cacheStamp} is Better Combat's current data. */
    private static final Map<Item, Optional<WeaponSnapshot>> ITEM_CACHE = new ConcurrentHashMap<>();
    @Nullable
    private static volatile Optional<WeaponSnapshot> fistCache;
    @Nullable
    private static Object cacheStamp;

    private static boolean serverConfigRead;
    private static float cachedUpswingMultiplier = 0.5F;
    private static int cachedAttackInterval = 2;

    private BetterCombatBridge()
    {
    }

    public static boolean isModLoaded()
    {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Attributes for the stack, or empty when Better Combat is missing, the item
     * has no profile, or the API could not be read.
     */
    public static Optional<WeaponSnapshot> lookup(ItemStack stack)
    {
        if (!prepare() || stack.isEmpty())
        {
            return Optional.empty();
        }
        // A stack can carry its own attributes as NBT. Those skip the per-item
        // cache; Better Combat already caches the parsed value on the stack.
        boolean cacheable = !hasStackAttributes(stack) && cacheValid();
        Item item = stack.getItem();
        if (cacheable)
        {
            Optional<WeaponSnapshot> cached = ITEM_CACHE.get(item);
            if (cached != null)
            {
                return cached;
            }
        }
        try
        {
            Object attributes = attributesForStack.invoke(null, stack);
            Optional<WeaponSnapshot> read = snapshot(attributes);
            if (cacheable)
            {
                ITEM_CACHE.put(item, read);
            }
            return read;
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            AncientEmpires.LOGGER.debug("Could not read Better Combat attributes for {}.", item, exception);
            return Optional.empty();
        }
    }

    /**
     * The bundled fist profile. Callers use this for an empty hand and do not
     * equip an item. Read once per Better Combat data load.
     */
    public static Optional<WeaponSnapshot> lookupFist()
    {
        if (!prepare())
        {
            return Optional.empty();
        }
        boolean cacheable = cacheValid();
        Optional<WeaponSnapshot> cached = fistCache;
        if (cacheable && cached != null)
        {
            return cached;
        }
        try
        {
            Object attributes = fistAttributes();
            Optional<WeaponSnapshot> read = snapshot(attributes);
            if (cacheable)
            {
                fistCache = read;
            }
            return read;
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            AncientEmpires.LOGGER.debug("Better Combat fist profile could not be read.", exception);
            return Optional.empty();
        }
    }

    private static boolean hasStackAttributes(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(STACK_ATTRIBUTES_KEY, Tag.TAG_STRING);
    }

    /**
     * True when cached snapshots match Better Combat's current data. That mod
     * replaces its {@code containers} map on every server data load, including
     * {@code /reload} and a new world, so a different map clears the caches.
     * Without that field nothing is cached and every lookup reads through.
     */
    private static synchronized boolean cacheValid()
    {
        if (containersField == null)
        {
            return false;
        }
        Object current;
        try
        {
            current = containersField.get(null);
        }
        catch (IllegalAccessException | RuntimeException exception)
        {
            return false;
        }
        if (current != cacheStamp)
        {
            ITEM_CACHE.clear();
            fistCache = null;
            cacheStamp = current;
        }
        return current != null;
    }

    /**
     * Better Combat's clamped upswing multiplier, or {@code fallback} when the
     * config cannot be read yet.
     */
    public static float upswingMultiplier(float fallback)
    {
        readServerConfig(fallback, cachedAttackInterval);
        return serverConfigRead ? cachedUpswingMultiplier : fallback;
    }

    public static int attackIntervalCap(int fallback)
    {
        readServerConfig(cachedUpswingMultiplier, fallback);
        return serverConfigRead ? cachedAttackInterval : fallback;
    }

    private static boolean prepare()
    {
        if (status == Status.READY)
        {
            return true;
        }
        if (status == Status.FAILED || !isModLoaded())
        {
            return false;
        }
        try
        {
            bind();
            status = Status.READY;
            AncientEmpires.LOGGER.info("Better Combat weapon attributes are available for NPC attacks.");
            return true;
        }
        catch (ReflectiveOperationException exception)
        {
            fail(exception);
            return false;
        }
    }

    private static void fail(Exception exception)
    {
        if (status != Status.FAILED)
        {
            status = Status.FAILED;
            AncientEmpires.LOGGER.warn("Better Combat is loaded, but its weapon attribute API could not be read. NPCs will use the internal attack profile.", exception);
        }
    }

    private static void bind() throws ReflectiveOperationException
    {
        Class<?> registry = Class.forName("net.bettercombat.logic.WeaponRegistry");
        attributesForStack = findStackLookup(registry);
        attributesForId = findIdLookup(registry);
        try
        {
            containersField = registry.getDeclaredField("containers");
            containersField.setAccessible(true);
        }
        catch (NoSuchFieldException exception)
        {
            containersField = null;
        }

        Class<?> attributesType = Class.forName("net.bettercombat.api.WeaponAttributes");
        attackRange = attributesType.getMethod("attackRange");
        attacks = attributesType.getMethod("attacks");

        Class<?> attackType = Class.forName("net.bettercombat.api.WeaponAttributes$Attack");
        damageMultiplier = attackType.getMethod("damageMultiplier");
        upswing = attackType.getMethod("upswing");
        angle = attackType.getMethod("angle");
        animation = attackType.getMethod("animation");
        pose = findOptional(attributesType, "pose");
        twoHanded = findOptional(attributesType, "isTwoHanded");
        conditions = findOptional(attackType, "conditions");
        hitbox = findOptional(attackType, "hitbox");
        swingSound = findOptional(attackType, "swingSound");
    }

    private static Method findStackLookup(Class<?> registry) throws NoSuchMethodException
    {
        for (Method method : registry.getMethods())
        {
            if ("getAttributes".equals(method.getName())
                    && method.getParameterCount() == 1
                    && ItemStack.class.isAssignableFrom(method.getParameterTypes()[0]))
            {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException("WeaponRegistry.getAttributes(ItemStack)");
    }

    @Nullable
    private static Method findIdLookup(Class<?> registry)
    {
        for (Method method : registry.getDeclaredMethods())
        {
            if (!"getAttributes".equals(method.getName()) || method.getParameterCount() != 1)
            {
                continue;
            }
            if (!ItemStack.class.isAssignableFrom(method.getParameterTypes()[0]))
            {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    @Nullable
    private static Method findOptional(Class<?> type, String name)
    {
        try
        {
            return type.getMethod(name);
        }
        catch (NoSuchMethodException exception)
        {
            return null;
        }
    }

    @Nullable
    private static Object fistAttributes() throws ReflectiveOperationException
    {
        if (attributesForId != null)
        {
            Object id = identifier(attributesForId.getParameterTypes()[0]);
            if (id != null)
            {
                Object attributes = attributesForId.invoke(null, id);
                if (attributes != null)
                {
                    return attributes;
                }
            }
        }
        if (containersField == null)
        {
            return null;
        }
        Object raw = containersField.get(null);
        if (!(raw instanceof Map<?, ?> containers))
        {
            return null;
        }
        // The map is keyed by ResourceLocation in current versions. The walk
        // below is only for a version that keys it some other way.
        Object direct = containers.get(ResourceLocation.tryParse(FIST_ID));
        if (direct != null)
        {
            return direct.getClass().getMethod("attributes").invoke(direct);
        }
        for (Map.Entry<?, ?> entry : containers.entrySet())
        {
            if (!FIST_ID.equals(String.valueOf(entry.getKey())) || entry.getValue() == null)
            {
                continue;
            }
            Method attributes = entry.getValue().getClass().getMethod("attributes");
            return attributes.invoke(entry.getValue());
        }
        return null;
    }

    @Nullable
    private static Object identifier(Class<?> parameterType) throws ReflectiveOperationException
    {
        ResourceLocation parsed = ResourceLocation.tryParse(FIST_ID);
        if (parsed != null && parameterType.isInstance(parsed))
        {
            return parsed;
        }
        try
        {
            return parameterType.getConstructor(String.class, String.class).newInstance("bettercombat", "fist");
        }
        catch (NoSuchMethodException exception)
        {
            return parameterType.getConstructor(String.class).newInstance(FIST_ID);
        }
    }

    private static Optional<WeaponSnapshot> snapshot(@Nullable Object attributes) throws ReflectiveOperationException
    {
        if (attributes == null)
        {
            return Optional.empty();
        }
        double range = ((Number) attackRange.invoke(attributes)).doubleValue();
        Object rawAttacks = attacks.invoke(attributes);
        if (!(rawAttacks instanceof Object[] attackArray) || attackArray.length == 0)
        {
            return Optional.empty();
        }

        List<AttackSnapshot> parsed = new ArrayList<>(attackArray.length);
        for (Object attack : attackArray)
        {
            if (attack != null)
            {
                parsed.add(readAttack(attack));
            }
        }
        if (parsed.isEmpty())
        {
            return Optional.empty();
        }
        return Optional.of(new WeaponSnapshot(range, List.copyOf(parsed), readPose(attributes), readTwoHanded(attributes)));
    }

    @Nullable
    private static String readPose(Object attributes) throws ReflectiveOperationException
    {
        if (pose == null)
        {
            return null;
        }
        Object value = pose.invoke(attributes);
        if (value instanceof String text && !text.isEmpty())
        {
            return text;
        }
        return null;
    }

    private static boolean readTwoHanded(Object attributes) throws ReflectiveOperationException
    {
        if (twoHanded == null)
        {
            return false;
        }
        Object value = twoHanded.invoke(attributes);
        return value instanceof Boolean flag && flag;
    }

    private static AttackSnapshot readAttack(Object attack) throws ReflectiveOperationException
    {
        double damage = ((Number) damageMultiplier.invoke(attack)).doubleValue();
        double windup = ((Number) upswing.invoke(attack)).doubleValue();
        double arc = ((Number) angle.invoke(attack)).doubleValue();
        Object animationId = animation.invoke(attack);
        List<String> conditionNames = readConditions(attack);
        String shape = readHitbox(attack);
        SwingSound sound = readSound(attack);
        return new AttackSnapshot(
                damage,
                windup,
                arc,
                animationId instanceof String text && !text.isEmpty() ? text : null,
                conditionNames,
                shape,
                sound.id,
                sound.volume,
                sound.pitch,
                sound.randomness
        );
    }

    private static List<String> readConditions(Object attack) throws ReflectiveOperationException
    {
        if (conditions == null)
        {
            return List.of();
        }
        Object raw = conditions.invoke(attack);
        if (!(raw instanceof Object[] values) || values.length == 0)
        {
            return List.of();
        }
        List<String> names = new ArrayList<>(values.length);
        for (Object value : values)
        {
            if (value instanceof Enum<?> condition)
            {
                names.add(condition.name());
            }
        }
        return List.copyOf(names);
    }

    @Nullable
    private static String readHitbox(Object attack) throws ReflectiveOperationException
    {
        if (hitbox == null)
        {
            return null;
        }
        Object value = hitbox.invoke(attack);
        if (value instanceof Enum<?> shape)
        {
            return shape.name();
        }
        return value == null ? null : value.toString();
    }

    private static SwingSound readSound(Object attack) throws ReflectiveOperationException
    {
        if (swingSound == null)
        {
            return SwingSound.NONE;
        }
        Object sound = swingSound.invoke(attack);
        if (sound == null)
        {
            return SwingSound.NONE;
        }
        bindSound(sound);
        if (soundId == null)
        {
            return SwingSound.NONE;
        }
        Object id = soundId.invoke(sound);
        if (!(id instanceof String text) || text.isEmpty())
        {
            return SwingSound.NONE;
        }
        float volume = soundVolume == null ? 1.0F : ((Number) soundVolume.invoke(sound)).floatValue();
        float pitch = soundPitch == null ? 1.0F : ((Number) soundPitch.invoke(sound)).floatValue();
        float randomness = soundRandomness == null ? 0.0F : ((Number) soundRandomness.invoke(sound)).floatValue();
        return new SwingSound(text, volume, pitch, randomness);
    }

    private static void bindSound(Object sound) throws ReflectiveOperationException
    {
        if (soundId != null)
        {
            return;
        }
        Class<?> type = sound.getClass();
        soundId = type.getMethod("id");
        soundVolume = findOptional(type, "volume");
        soundPitch = findOptional(type, "pitch");
        soundRandomness = findOptional(type, "randomness");
    }

    private static void readServerConfig(float fallbackMultiplier, int fallbackInterval)
    {
        if (serverConfigRead || !prepare())
        {
            return;
        }
        try
        {
            Class<?> mod = Class.forName("net.bettercombat.BetterCombat");
            Object config = mod.getField("config").get(null);
            if (config == null)
            {
                return;
            }
            cachedUpswingMultiplier = ((Number) config.getClass().getMethod("getUpswingMultiplier").invoke(config)).floatValue();
            cachedAttackInterval = config.getClass().getField("attack_interval_cap").getInt(config);
            serverConfigRead = true;
        }
        catch (ReflectiveOperationException exception)
        {
            cachedUpswingMultiplier = fallbackMultiplier;
            cachedAttackInterval = fallbackInterval;
            serverConfigRead = true;
            AncientEmpires.LOGGER.debug("Better Combat server config could not be read. Using Ancient Empires upswing settings.", exception);
        }
    }

    public record WeaponSnapshot(double range, List<AttackSnapshot> attacks, @Nullable String pose, boolean twoHanded)
    {
    }

    public record AttackSnapshot(
            double damageMultiplier,
            double upswing,
            double angle,
            @Nullable String animationId,
            List<String> conditions,
            @Nullable String hitbox,
            @Nullable String swingSoundId,
            float swingVolume,
            float swingPitch,
            float swingPitchRandomness
    )
    {
    }

    private record SwingSound(@Nullable String id, float volume, float pitch, float randomness)
    {
        private static final SwingSound NONE = new SwingSound(null, 1.0F, 1.0F, 0.0F);
    }
}
