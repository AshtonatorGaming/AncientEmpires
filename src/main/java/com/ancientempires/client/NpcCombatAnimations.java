package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.combat.BetterCombatBridge;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Better Combat's attack and idle clips, played through Player Animation Library.
 * The library only copies its stack onto players, so the NPC renderer applies
 * the same layers to its own player model. Missing either mod leaves the
 * vanilla arm pose in place.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NpcCombatAnimations
{
    private static final String PLAYER_ANIMATOR = "playeranimator";

    private enum Status
    {
        UNCHECKED,
        READY,
        ABSENT,
        FAILED
    }

    private static Status status = Status.UNCHECKED;
    private static final Map<NpcEntity, Session> SESSIONS = new WeakHashMap<>();

    private static Constructor<?> stackConstructor;
    private static Constructor<?> layerConstructor;
    private static Constructor<?> mirrorConstructor;
    private static Constructor<?> speedConstructor;
    private static Constructor<?> gearConstructor;
    private static Constructor<?> playerConstructor;
    private static Constructor<?> applierConstructor;

    private static Method addAnimLayer;
    private static Method stackTick;
    private static Method addModifier;
    private static Method replaceAnimation;
    private static Method setEnabled;
    private static Method speedSet;
    private static Method standardFadeIn;
    private static Method mutableCopy;
    private static Method buildAnimation;
    private static Method fullyEnablePart;
    private static Method setAxisEnabled;
    private static Method configurePart;
    private static Method setTickDelta;
    private static Method updatePart;
    private static Method isActive;
    private static Method getTransform;
    private static Method componentX;
    private static Method componentY;
    private static Method componentZ;

    private static Field animations;
    private static Field endTick;
    private static Field beginTick;
    private static Object easeInOut;
    private static Object transformPosition;
    private static Object transformRotation;
    private static Object transformBend;
    private static Object zero;

    private NpcCombatAnimations()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused())
        {
            return;
        }
        for (Entity entity : minecraft.level.entitiesForRendering())
        {
            if (entity instanceof NpcEntity npc)
            {
                tick(npc);
            }
        }
    }

    /**
     * Copies the active stack onto the model. Returns false when there is
     * nothing to play, including when the animation mods are absent.
     */
    public static boolean apply(NpcEntity npc, PlayerModel<NpcEntity> model, float partialTick)
    {
        Session session = session(npc);
        if (session == null)
        {
            return false;
        }
        try
        {
            session.sync(npc);
            return session.apply(model, partialTick);
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            fail(exception);
            return false;
        }
    }

    public static void bendHeldArm(NpcEntity npc, HumanoidArm arm, PoseStack pose)
    {
        float bendX = read(npc, arm == HumanoidArm.LEFT ? "leftArm" : "rightArm", transformBend, 0);
        float bendY = read(npc, arm == HumanoidArm.LEFT ? "leftArm" : "rightArm", transformBend, 1);
        if (bendX == 0.0F && bendY == 0.0F)
        {
            return;
        }
        pose.translate(0.0F, 0.25F, 0.0F);
        float axis = -bendX;
        pose.mulPose(new Quaternionf().rotateAxis(bendY, new Vector3f(Mth.cos(axis), 0.0F, Mth.sin(axis))));
        pose.translate(0.0F, -0.25F, 0.0F);
    }

    public static void placeHeldItem(NpcEntity npc, HumanoidArm arm, PoseStack pose)
    {
        String part = arm == HumanoidArm.LEFT ? "leftItem" : "rightItem";
        float x = read(npc, part, transformPosition, 0) / 16.0F;
        float y = read(npc, part, transformPosition, 1) / 16.0F;
        float z = read(npc, part, transformPosition, 2) / 16.0F;
        float rx = read(npc, part, transformRotation, 0);
        float ry = read(npc, part, transformRotation, 1);
        float rz = read(npc, part, transformRotation, 2);
        if (x == 0.0F && y == 0.0F && z == 0.0F && rx == 0.0F && ry == 0.0F && rz == 0.0F)
        {
            return;
        }
        pose.translate(x, y, z);
        pose.mulPose(Axis.ZP.rotation(rz));
        pose.mulPose(Axis.YP.rotation(ry));
        pose.mulPose(Axis.XP.rotation(rx));
    }

    private static void tick(NpcEntity npc)
    {
        Session session = session(npc);
        if (session == null)
        {
            return;
        }
        try
        {
            session.sync(npc);
            stackTick.invoke(session.stack);
        }
        catch (ReflectiveOperationException exception)
        {
            fail(exception);
        }
    }

    private static Session session(NpcEntity npc)
    {
        if (!prepare())
        {
            return null;
        }
        try
        {
            return SESSIONS.computeIfAbsent(npc, key -> new Session());
        }
        catch (RuntimeException exception)
        {
            fail(exception);
            return null;
        }
    }

    private static float read(NpcEntity npc, String part, Object type, int axis)
    {
        Session session = SESSIONS.get(npc);
        if (session == null || type == null || !session.active())
        {
            return 0.0F;
        }
        try
        {
            Object value = getTransform.invoke(session.applier, part, type, zero);
            Object component = switch (axis)
            {
                case 0 -> componentX.invoke(value);
                case 1 -> componentY.invoke(value);
                default -> componentZ.invoke(value);
            };
            return component instanceof Number number ? number.floatValue() : 0.0F;
        }
        catch (ReflectiveOperationException exception)
        {
            return 0.0F;
        }
    }

    private static boolean prepare()
    {
        if (status == Status.READY)
        {
            return true;
        }
        if (status != Status.UNCHECKED)
        {
            return false;
        }
        if (!ModList.get().isLoaded(BetterCombatBridge.MOD_ID) || !ModList.get().isLoaded(PLAYER_ANIMATOR))
        {
            status = Status.ABSENT;
            return false;
        }
        try
        {
            bind();
            status = Status.READY;
            AncientEmpires.LOGGER.info("Better Combat attack and idle animations are available for NPCs.");
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
            SESSIONS.clear();
            AncientEmpires.LOGGER.warn("Better Combat animations could not be attached to NPCs. They will use the vanilla arm swing.", exception);
        }
    }

    private static void bind() throws ReflectiveOperationException
    {
        Class<?> animationType = Class.forName("dev.kosmx.playerAnim.api.layered.IAnimation");
        Class<?> stackType = Class.forName("dev.kosmx.playerAnim.api.layered.AnimationStack");
        Class<?> layerType = Class.forName("dev.kosmx.playerAnim.api.layered.ModifierLayer");
        Class<?> fadeType = Class.forName("dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier");
        Class<?> modifierType = Class.forName("dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier");
        Class<?> mirrorType = Class.forName("dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier");
        Class<?> playerType = Class.forName("dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer");
        Class<?> keyframeType = Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation");
        Class<?> builderType = Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation$AnimationBuilder");
        Class<?> stateType = Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation$StateCollection");
        Class<?> easeType = Class.forName("dev.kosmx.playerAnim.core.util.Ease");
        Class<?> vectorType = Class.forName("dev.kosmx.playerAnim.core.util.Vec3f");
        Class<?> transformType = Class.forName("dev.kosmx.playerAnim.api.TransformType");
        Class<?> applierType = Class.forName("dev.kosmx.playerAnim.impl.animation.AnimationApplier");
        Class<?> speedType = Class.forName("net.bettercombat.client.animation.modifier.TransmissionSpeedModifier");
        Class<?> gearType = Class.forName("net.bettercombat.client.animation.modifier.TransmissionSpeedModifier$Gear");
        Class<?> registryType = Class.forName("net.bettercombat.client.animation.AnimationRegistry");
        Class<?> helperType = Class.forName("net.bettercombat.client.animation.StateCollectionHelper");

        stackConstructor = stackType.getConstructor();
        layerConstructor = layerType.getConstructor();
        mirrorConstructor = mirrorType.getConstructor();
        speedConstructor = speedType.getConstructor();
        gearConstructor = gearType.getConstructor(float.class, float.class);
        applierConstructor = applierType.getConstructor(animationType);
        playerConstructor = playerConstructor(playerType, keyframeType);

        addAnimLayer = stackType.getMethod("addAnimLayer", int.class, animationType);
        stackTick = stackType.getMethod("tick");
        addModifier = layerType.getMethod("addModifier", modifierType, int.class);
        replaceAnimation = layerType.getMethod("replaceAnimationWithFade", fadeType, animationType);
        setEnabled = mirrorType.getMethod("setEnabled", boolean.class);
        speedSet = speedType.getMethod("set", float.class, List.class);
        standardFadeIn = fadeType.getMethod("standardFadeIn", int.class, easeType);
        mutableCopy = keyframeType.getMethod("mutableCopy");
        buildAnimation = builderType.getMethod("build");
        fullyEnablePart = stateType.getMethod("fullyEnablePart", boolean.class);
        setAxisEnabled = declaredField(stateType, "pitch").getType().getMethod("setEnabled", boolean.class);
        configurePart = helperType.getMethod("configure", stateType, boolean.class, boolean.class);
        setTickDelta = applierType.getMethod("setTickDelta", float.class);
        updatePart = applierType.getMethod("updatePart", String.class, ModelPart.class);
        isActive = applierType.getMethod("isActive");
        getTransform = applierType.getMethod("get3DTransform", String.class, transformType, vectorType);
        componentX = vectorType.getMethod("getX");
        componentY = vectorType.getMethod("getY");
        componentZ = vectorType.getMethod("getZ");

        animations = declaredField(registryType, "animations");
        endTick = declaredField(keyframeType, "endTick");
        beginTick = declaredField(builderType, "beginTick");
        easeInOut = easeType.getField("INOUTSINE").get(null);
        transformPosition = transformType.getField("POSITION").get(null);
        transformRotation = transformType.getField("ROTATION").get(null);
        transformBend = transformType.getField("BEND").get(null);
        zero = vectorType.getField("ZERO").get(null);
    }

    private static Constructor<?> playerConstructor(Class<?> playerType, Class<?> keyframeType) throws NoSuchMethodException
    {
        for (Constructor<?> constructor : playerType.getConstructors())
        {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 2 && parameters[1] == int.class && parameters[0].isAssignableFrom(keyframeType))
            {
                return constructor;
            }
        }
        throw new NoSuchMethodException("KeyframeAnimationPlayer(KeyframeAnimation, int)");
    }

    private static Field declaredField(Class<?> type, String name) throws NoSuchFieldException
    {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException
    {
        Class<?> type = target.getClass();
        while (type != null)
        {
            try
            {
                Field found = type.getDeclaredField(name);
                found.setAccessible(true);
                return found.get(target);
            }
            catch (NoSuchFieldException exception)
            {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    @SuppressWarnings("unchecked")
    private static Object animation(String id)
    {
        try
        {
            Object raw = animations.get(null);
            if (raw instanceof Map<?, ?> map)
            {
                return map.get(id);
            }
        }
        catch (IllegalAccessException exception)
        {
            fail(exception);
        }
        return null;
    }

    private static Object fade(int ticks) throws ReflectiveOperationException
    {
        return standardFadeIn.invoke(null, Math.max(0, ticks), easeInOut);
    }

    private static final class Session
    {
        private final Object stack;
        private final Object applier;
        private final Object attackLayer;
        private final Object attackSpeed;
        private final Object attackMirror;
        private final PoseSlot itemPose;
        private final PoseSlot bodyPose;
        private int playedSerial = -1;
        private int misses;

        private Session()
        {
            try
            {
                this.stack = stackConstructor.newInstance();
                this.itemPose = new PoseSlot(false);
                this.bodyPose = new PoseSlot(true);
                this.attackLayer = layerConstructor.newInstance();
                this.attackSpeed = speedConstructor.newInstance();
                this.attackMirror = mirrorConstructor.newInstance();
                setEnabled.invoke(this.attackMirror, false);
                addModifier.invoke(this.attackLayer, this.attackSpeed, 0);
                addModifier.invoke(this.attackLayer, this.attackMirror, 0);
                addAnimLayer.invoke(this.stack, 3, this.itemPose.layer);
                addAnimLayer.invoke(this.stack, 4, this.bodyPose.layer);
                addAnimLayer.invoke(this.stack, 2000, this.attackLayer);
                this.applier = applierConstructor.newInstance(this.stack);
            }
            catch (ReflectiveOperationException exception)
            {
                throw new IllegalStateException(exception);
            }
        }

        private void sync(NpcEntity npc) throws ReflectiveOperationException
        {
            this.updateAttack(npc);
            this.updatePose(npc);
        }

        private void updateAttack(NpcEntity npc) throws ReflectiveOperationException
        {
            int serial = npc.getAttackPresentationSerial();
            if (serial == this.playedSerial)
            {
                return;
            }
            String id = npc.getAttackPresentationId();
            if (!AncientEmpiresConfig.betterCombatEnabled || id.isEmpty())
            {
                replaceAnimation.invoke(this.attackLayer, fade(4), null);
                this.playedSerial = serial;
                this.misses = 0;
                return;
            }
            if (!this.play(npc, id))
            {
                this.misses++;
                if (this.misses > 40)
                {
                    this.playedSerial = serial;
                    this.misses = 0;
                }
                return;
            }
            this.playedSerial = serial;
            this.misses = 0;
        }

        private boolean play(NpcEntity npc, String id) throws ReflectiveOperationException
        {
            Object clip = animation(id);
            if (clip == null)
            {
                return false;
            }
            Object copy = mutableCopy.invoke(clip);
            fullyEnablePart.invoke(field(copy, "torso"), true);
            setAxisEnabled.invoke(field(field(copy, "head"), "pitch"), false);
            if (npc.isPassenger() || npc.isSwimming())
            {
                configurePart.invoke(null, field(copy, "rightLeg"), false, false);
                configurePart.invoke(null, field(copy, "leftLeg"), false, false);
            }
            int lengthTicks = Math.max(1, ((Number) endTick.get(clip)).intValue());
            int fadeTicks = Math.max(0, ((Number) beginTick.get(copy)).intValue());
            Object built = buildAnimation.invoke(copy);

            float length = Math.max(1.0F, npc.getAttackPresentationLength());
            float upswing = Mth.clamp(npc.getAttackPresentationUpswing(), 0.0F, 0.99F);
            float rate = lengthTicks / length;
            float multiplier = BetterCombatBridge.upswingMultiplier(0.5F);
            if (multiplier < 0.05F)
            {
                multiplier = 0.5F;
            }
            float blend = Math.max(multiplier - 0.5F, 0.0F) / 0.5F;
            float downwind = (float) (rate * Mth.lerp(blend, 1.0F - upswing, upswing / (1.0F - upswing)));
            Object slow = gearConstructor.newInstance(length * upswing, downwind);
            Object resume = gearConstructor.newInstance(length, rate);
            speedSet.invoke(this.attackSpeed, rate / multiplier, List.of(slow, resume));
            setEnabled.invoke(this.attackMirror, npc.getMainArm() == HumanoidArm.LEFT);

            Object player = playerConstructor.newInstance(built, 0);
            replaceAnimation.invoke(this.attackLayer, fade(fadeTicks), player);
            return true;
        }

        private void updatePose(NpcEntity npc) throws ReflectiveOperationException
        {
            ItemStack held = npc.getMainHandItem();
            boolean busy = npc.swinging || npc.isSwimming() || npc.isUsingItem() || CrossbowItem.isCharged(held);
            boolean moving = !npc.isDeadOrDying() && npc.getDeltaMovement().horizontalDistanceSqr() > 0.0009D;
            boolean left = npc.getMainArm() == HumanoidArm.LEFT;
            String pose = null;
            boolean twoHanded = false;
            if (AncientEmpiresConfig.betterCombatEnabled && !held.isEmpty())
            {
                var weapon = BetterCombatBridge.lookup(held);
                if (weapon.isPresent())
                {
                    pose = weapon.get().pose();
                    twoHanded = weapon.get().twoHanded();
                }
            }
            boolean legsLocked = npc.isPassenger() || npc.isSwimming();
            String body = busy || (!twoHanded && (moving || npc.isCrouching())) ? null : pose;
            this.itemPose.set(busy ? null : pose, left, legsLocked);
            this.bodyPose.set(body, left, legsLocked);
        }

        private boolean apply(PlayerModel<NpcEntity> model, float partialTick) throws ReflectiveOperationException
        {
            float partial = Mth.clamp(partialTick, 0.0F, 1.0F);
            setTickDelta.invoke(this.applier, partial);
            if (!this.active())
            {
                return false;
            }
            updatePart.invoke(this.applier, "head", model.head);
            updatePart.invoke(this.applier, "leftArm", model.leftArm);
            updatePart.invoke(this.applier, "rightArm", model.rightArm);
            updatePart.invoke(this.applier, "leftLeg", model.leftLeg);
            updatePart.invoke(this.applier, "rightLeg", model.rightLeg);
            updatePart.invoke(this.applier, "torso", model.body);
            return true;
        }

        private boolean active()
        {
            try
            {
                return Boolean.TRUE.equals(isActive.invoke(this.applier));
            }
            catch (ReflectiveOperationException exception)
            {
                return false;
            }
        }
    }

    private static final class PoseSlot
    {
        private final Object layer;
        private final Object mirror;
        private final boolean bodyChannel;
        private String appliedId;
        private boolean appliedLeft;
        private boolean appliedLegs;
        private boolean applied;

        private PoseSlot(boolean bodyChannel) throws ReflectiveOperationException
        {
            this.bodyChannel = bodyChannel;
            this.layer = layerConstructor.newInstance();
            this.mirror = mirrorConstructor.newInstance();
            addModifier.invoke(this.layer, this.mirror, 0);
        }

        private void set(String id, boolean left, boolean legsLocked) throws ReflectiveOperationException
        {
            if (this.applied && java.util.Objects.equals(this.appliedId, id) && this.appliedLeft == left && this.appliedLegs == legsLocked)
            {
                return;
            }
            this.applied = true;
            this.appliedId = id;
            this.appliedLeft = left;
            this.appliedLegs = legsLocked;
            if (id == null)
            {
                replaceAnimation.invoke(this.layer, fade(5), null);
                return;
            }
            Object clip = animation(id);
            if (clip == null)
            {
                this.applied = false;
                return;
            }
            Object copy = mutableCopy.invoke(clip);
            if (this.bodyChannel)
            {
                configurePart.invoke(null, field(copy, "rightItem"), false, false);
                configurePart.invoke(null, field(copy, "leftItem"), false, false);
            }
            else
            {
                configurePart.invoke(null, field(copy, "head"), false, false);
                configurePart.invoke(null, field(copy, "torso"), false, false);
                configurePart.invoke(null, field(copy, "body"), false, false);
                configurePart.invoke(null, field(copy, "rightArm"), false, false);
                configurePart.invoke(null, field(copy, "leftArm"), false, false);
                configurePart.invoke(null, field(copy, "rightLeg"), false, false);
                configurePart.invoke(null, field(copy, "leftLeg"), false, false);
            }
            if (legsLocked)
            {
                configurePart.invoke(null, field(copy, "rightLeg"), false, false);
                configurePart.invoke(null, field(copy, "leftLeg"), false, false);
            }
            setEnabled.invoke(this.mirror, left);
            Object player = playerConstructor.newInstance(buildAnimation.invoke(copy), 0);
            replaceAnimation.invoke(this.layer, fade(5), player);
        }
    }
}
