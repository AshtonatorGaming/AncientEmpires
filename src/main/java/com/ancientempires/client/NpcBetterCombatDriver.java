package com.ancientempires.client;

import com.ancientempires.combat.BetterCombatBridge;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.animation.AnimationRegistry;
import net.bettercombat.client.animation.AttackAnimationSubStack;
import net.bettercombat.client.animation.CustomAnimationPlayer;
import net.bettercombat.client.animation.PoseSubStack;
import net.bettercombat.client.animation.StateCollectionHelper;
import net.bettercombat.client.animation.modifier.HarshAdjustmentModifier;
import net.bettercombat.client.animation.modifier.TransmissionSpeedModifier;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Better Combat's own attack stack and weapon poses, applied to the NPC model.
 * Player Animation Library only copies its stack onto players, so this ticks
 * the same layers and writes them onto the NPC's player model.
 */
final class NpcBetterCombatDriver
{
    private static final Map<NpcEntity, Session> SESSIONS = new WeakHashMap<>();

    private NpcBetterCombatDriver()
    {
    }

    static void init()
    {
        if (AnimationRegistry.animations == null)
        {
            throw new IllegalStateException("Better Combat has no animation registry");
        }
    }

    static void tick(NpcEntity npc)
    {
        Session session = session(npc);
        session.sync(npc);
        session.stack.tick();
    }

    static boolean apply(NpcEntity npc, PlayerModel<NpcEntity> model, float partialTick)
    {
        Session session = session(npc);
        session.sync(npc);
        return session.apply(model, partialTick);
    }

    static boolean attackActive(NpcEntity npc)
    {
        Session session = SESSIONS.get(npc);
        if (session == null)
        {
            return false;
        }
        Object animation = session.attack.base.getAnimation();
        return animation instanceof IAnimation playing && playing.isActive();
    }

    static boolean holdActive(NpcEntity npc)
    {
        Session session = SESSIONS.get(npc);
        // The body channel is the arm stance. The item channel only shifts the weapon.
        return session != null && session.bodyPose.base.isActive();
    }

    static void bendHeldArm(NpcEntity npc, HumanoidArm arm, PoseStack pose)
    {
        Session session = SESSIONS.get(npc);
        if (session == null || !session.applier.isActive())
        {
            return;
        }
        String part = arm == HumanoidArm.LEFT ? "leftArm" : "rightArm";
        Vec3f bend = session.applier.get3DTransform(part, TransformType.BEND, new Vec3f(0.0F, 0.0F, 0.0F));
        float bendX = bend.getX().floatValue();
        float bendY = bend.getY().floatValue();
        if (bendX == 0.0F && bendY == 0.0F)
        {
            return;
        }
        pose.translate(0.0F, 0.25F, 0.0F);
        float axis = -bendX;
        pose.mulPose(new Quaternionf().rotateAxis(bendY, new Vector3f(Mth.cos(axis), 0.0F, Mth.sin(axis))));
        pose.translate(0.0F, -0.25F, 0.0F);
    }

    static void placeHeldItem(NpcEntity npc, HumanoidArm arm, PoseStack pose)
    {
        Session session = SESSIONS.get(npc);
        if (session == null || !session.applier.isActive())
        {
            return;
        }
        String part = arm == HumanoidArm.LEFT ? "leftItem" : "rightItem";
        Vec3f rotation = session.applier.get3DTransform(part, TransformType.ROTATION, new Vec3f(0.0F, 0.0F, 0.0F));
        Vec3f position = session.applier.get3DTransform(part, TransformType.POSITION, new Vec3f(0.0F, 0.0F, 0.0F)).scale(1.0F / 16.0F);
        float x = position.getX().floatValue();
        float y = position.getY().floatValue();
        float z = position.getZ().floatValue();
        float rx = rotation.getX().floatValue();
        float ry = rotation.getY().floatValue();
        float rz = rotation.getZ().floatValue();
        if (x == 0.0F && y == 0.0F && z == 0.0F && rx == 0.0F && ry == 0.0F && rz == 0.0F)
        {
            return;
        }
        pose.translate(x, y, z);
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(rz));
        pose.mulPose(com.mojang.math.Axis.YP.rotation(ry));
        pose.mulPose(com.mojang.math.Axis.XP.rotation(rx));
    }

    private static Session session(NpcEntity npc)
    {
        return SESSIONS.computeIfAbsent(npc, key -> new Session());
    }

    private static final class Session
    {
        private final AnimationStack stack = new AnimationStack();
        private final PoseSubStack itemPose;
        private final PoseSubStack bodyPose;
        private final AttackAnimationSubStack attack;
        private final AnimationApplier applier;
        private NpcEntity current;
        private int playedSerial = -1;
        private int misses;
        private int poseMisses;

        private Session()
        {
            this.itemPose = new PoseSubStack(null, false, true);
            this.bodyPose = new PoseSubStack(new HarshAdjustmentModifier(this::sneakOffset), true, true);
            this.bodyPose.configure = this::lockLegs;
            this.attack = new AttackAnimationSubStack(new AdjustmentModifier(this::pitchAdjustment));
            this.stack.addAnimLayer(3, this.itemPose.base);
            this.stack.addAnimLayer(4, this.bodyPose.base);
            this.stack.addAnimLayer(2000, this.attack.base);
            this.applier = new AnimationApplier(this.stack);
        }

        private void sync(NpcEntity npc)
        {
            this.current = npc;
            this.updateAttack(npc);
            this.updatePose(npc);
        }

        private void updateAttack(NpcEntity npc)
        {
            int serial = npc.getAttackPresentationSerial();
            if (serial == this.playedSerial)
            {
                return;
            }
            String id = npc.getAttackPresentationId();
            if (!AncientEmpiresConfig.betterCombatEnabled || id.isEmpty())
            {
                this.attack.base.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE), null);
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

        private boolean play(NpcEntity npc, String id)
        {
            if (AnimationRegistry.animations == null)
            {
                return false;
            }
            KeyframeAnimation animation = AnimationRegistry.animations.get(id);
            if (animation == null)
            {
                return false;
            }
            KeyframeAnimation.AnimationBuilder copy = animation.mutableCopy();
            this.lockLegs(copy);
            copy.torso.fullyEnablePart(true);
            copy.head.pitch.setEnabled(false);
            float length = Math.max(1.0F, npc.getAttackPresentationLength());
            float upswing = Mth.clamp(npc.getAttackPresentationUpswing(), 0.0F, 0.99F);
            float speed = animation.endTick / length;
            float multiplier = BetterCombatBridge.upswingMultiplier(0.5F);
            if (multiplier < 0.05F)
            {
                multiplier = 0.5F;
            }
            float blend = Math.max(multiplier - 0.5F, 0.0F) / 0.5F;
            float downwind = (float) (speed * Mth.lerp(blend, 1.0F - upswing, upswing / (1.0F - upswing)));
            this.attack.speed.set(speed / multiplier, List.of(
                    new TransmissionSpeedModifier.Gear(length * upswing, downwind),
                    new TransmissionSpeedModifier.Gear(length, speed)
            ));
            this.attack.mirror.setEnabled(npc.getMainArm() == HumanoidArm.LEFT);
            KeyframeAnimationPlayer player = new CustomAnimationPlayer(copy.build(), 0);
            int fadeTicks = Math.max(0, copy.beginTick);
            this.attack.base.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeTicks, Ease.INOUTSINE), player);
            return true;
        }

        private void updatePose(NpcEntity npc)
        {
            ItemStack held = npc.getMainHandItem();
            boolean busy = npc.swinging || npc.isSwimming() || npc.isUsingItem() || CrossbowItem.isCharged(held);
            boolean moving = !npc.isDeadOrDying() && npc.getDeltaMovement().horizontalDistanceSqr() > 0.0009D;
            boolean left = npc.getMainArm() == HumanoidArm.LEFT;
            KeyframeAnimation pose = null;
            boolean twoHanded = false;
            if (AncientEmpiresConfig.betterCombatEnabled && !held.isEmpty() && !busy)
            {
                WeaponAttributes weapon = null;
                try
                {
                    weapon = WeaponRegistry.getAttributes(held);
                }
                catch (RuntimeException ignored)
                {
                    weapon = null;
                }
                if (weapon != null && weapon.pose() != null && AnimationRegistry.animations != null)
                {
                    pose = AnimationRegistry.animations.get(weapon.pose());
                    if (pose == null)
                    {
                        this.poseMisses++;
                        if (this.poseMisses > 40)
                        {
                            this.itemPose.setPose(null, left);
                            this.bodyPose.setPose(null, left);
                        }
                        return;
                    }
                    twoHanded = weapon.isTwoHanded();
                }
            }
            this.poseMisses = 0;
            KeyframeAnimation body = pose;
            if (!twoHanded && (moving || npc.isCrouching()))
            {
                body = null;
            }
            this.itemPose.setPose(pose, left);
            this.bodyPose.setPose(body, left);
        }

        private boolean apply(PlayerModel<NpcEntity> model, float partialTick)
        {
            this.applier.setTickDelta(Mth.clamp(partialTick, 0.0F, 1.0F));
            if (!this.applier.isActive())
            {
                return false;
            }
            this.applier.updatePart("head", model.head);
            this.applier.updatePart("leftArm", model.leftArm);
            this.applier.updatePart("rightArm", model.rightArm);
            this.applier.updatePart("leftLeg", model.leftLeg);
            this.applier.updatePart("rightLeg", model.rightLeg);
            this.applier.updatePart("torso", model.body);
            return true;
        }

        private void lockLegs(KeyframeAnimation.AnimationBuilder builder)
        {
            if (this.current != null && (this.current.isPassenger() || this.current.isSwimming()))
            {
                StateCollectionHelper.configure(builder.rightLeg, false, false);
                StateCollectionHelper.configure(builder.leftLeg, false, false);
            }
        }

        private Optional<AdjustmentModifier.PartModifier> sneakOffset(String part)
        {
            if (this.current == null || this.itemPose.lastAnimationUsesBodyChannel || !this.current.isCrouching())
            {
                return Optional.empty();
            }
            if (!"rightArm".equals(part) && !"leftArm".equals(part))
            {
                return Optional.empty();
            }
            return Optional.of(new AdjustmentModifier.PartModifier(new Vec3f(0.0F, 0.0F, 0.0F), new Vec3f(0.0F, 3.0F, 0.0F)));
        }

        private Optional<AdjustmentModifier.PartModifier> pitchAdjustment(String part)
        {
            if (this.current == null)
            {
                return Optional.empty();
            }
            float pitch = (float) Math.toRadians(this.current.getXRot());
            float rotation = switch (part)
            {
                case "body" -> -pitch * 0.75F;
                case "rightArm", "leftArm" -> pitch * 0.25F;
                case "rightLeg", "leftLeg" -> -pitch * 0.75F;
                default -> Float.NaN;
            };
            if (Float.isNaN(rotation))
            {
                return Optional.empty();
            }
            return Optional.of(new AdjustmentModifier.PartModifier(new Vec3f(rotation, 0.0F, 0.0F), new Vec3f(0.0F, 0.0F, 0.0F)));
        }
    }
}
