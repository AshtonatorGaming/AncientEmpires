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
import dev.kosmx.playerAnim.impl.Helper;
import dev.kosmx.playerAnim.impl.IMutableModel;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.impl.animation.IBendHelper;
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
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Fallback pose stack used when Better Mob Combat is absent. Player Animation
 * Library only copies its stack onto players, so this ticks the same Better
 * Combat layers and writes them onto the NPC's player model. When Better Mob
 * Combat is loaded, Mob Player Animator already does that and this stack stays idle.
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
        session.tickTrail(npc);
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
        return session != null && (session.bodyPose.base.isActive() || session.itemPose.base.isActive());
    }

    /**
     * The clip's {@code body} channel: the whole-body lean, twist, and shift
     * that Better Combat swings carry. Player Animation Lib applies it to the
     * render matrix in its player renderer hook, which never runs for NPCs.
     * Same math, with the pivot and offset scaled to the NPC's body size.
     */
    static void applyBodyTransform(NpcEntity npc, PoseStack pose, float partialTick)
    {
        Session session = SESSIONS.get(npc);
        if (session == null)
        {
            return;
        }
        AnimationApplier applier = session.applier;
        applier.setTickDelta(Mth.clamp(partialTick, 0.0F, 1.0F));
        if (!applier.isActive())
        {
            return;
        }
        float scale = npc.getBodyScale();
        Vec3f position = applier.get3DTransform("body", TransformType.POSITION, Vec3f.ZERO);
        Vec3f rotation = applier.get3DTransform("body", TransformType.ROTATION, Vec3f.ZERO);
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
        pose.translate(x * scale, (y + 0.7F) * scale, z * scale);
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(rz));
        pose.mulPose(com.mojang.math.Axis.YP.rotation(ry));
        pose.mulPose(com.mojang.math.Axis.XP.rotation(rx));
        pose.translate(0.0F, -0.7F * scale, 0.0F);
    }

    /**
     * Upper-body layers (held items) follow the waist bend the model draws with.
     * Player Animation Lib does this for a player's layers; ours draw themselves.
     */
    static void bendUpperBody(NpcEntity npc, PoseStack pose)
    {
        Session session = SESSIONS.get(npc);
        if (session == null || !session.applier.isActive() || !Helper.isBendEnabled())
        {
            return;
        }
        IBendHelper.rotateMatrixStack(pose, session.applier.getBend("body"));
    }

    static void bendHeldArm(NpcEntity npc, HumanoidArm arm, PoseStack pose)
    {
        Session session = SESSIONS.get(npc);
        if (session != null)
        {
            bend(session.applier, arm, pose);
        }
    }

    static void placeHeldItem(NpcEntity npc, HumanoidArm arm, PoseStack pose)
    {
        Session session = SESSIONS.get(npc);
        if (session != null)
        {
            place(session.applier, arm, pose);
        }
    }

    static void bend(AnimationApplier applier, HumanoidArm arm, PoseStack pose)
    {
        if (applier == null || !applier.isActive())
        {
            return;
        }
        String part = arm == HumanoidArm.LEFT ? "leftArm" : "rightArm";
        Vec3f bend = applier.get3DTransform(part, TransformType.BEND, new Vec3f(0.0F, 0.0F, 0.0F));
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

    static void place(AnimationApplier applier, HumanoidArm arm, PoseStack pose)
    {
        if (applier == null || !applier.isActive())
        {
            return;
        }
        String part = arm == HumanoidArm.LEFT ? "leftItem" : "rightItem";
        Vec3f rotation = applier.get3DTransform(part, TransformType.ROTATION, new Vec3f(0.0F, 0.0F, 0.0F));
        Vec3f position = applier.get3DTransform(part, TransformType.POSITION, new Vec3f(0.0F, 0.0F, 0.0F)).scale(1.0F / 16.0F);
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
        /** Ticks until the current swing's hit frame, when its trail spawns. -1 when none is pending. */
        private int trailDelay = -1;
        private String trailId;
        private boolean trailOffhand;

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
            int serial = npc.getAttack().presentationSerial();
            if (serial == this.playedSerial)
            {
                return;
            }
            String id = npc.getAttack().presentationId();
            if (!AncientEmpiresConfig.betterCombatEnabled || id.isEmpty())
            {
                // A cancelled swing never reaches its hit frame, so it leaves no trail.
                this.trailDelay = -1;
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
            float upswing = Mth.clamp(npc.getAttack().presentationUpswing(), 0.0F, 0.99F);
            float multiplier = BetterCombatBridge.upswingMultiplier(0.5F);
            if (multiplier < 0.05F)
            {
                multiplier = 0.5F;
            }
            // Better Combat's curve lands the hit at length * upswing * multiplier. The
            // server's real wind-up can be longer (npcWindupScale), so the curve runs over
            // the length that puts its hit on that tick: the whole clip slows evenly
            // instead of bending around a different hit frame.
            float cooldown = Math.max(1.0F, npc.getAttack().presentationLength());
            int windup = npc.getAttack().presentationWindup();
            float hitRate = upswing * Mth.clamp(multiplier, 0.2F, 1.0F);
            float length = windup > 0 && hitRate > 0.01F
                    ? Mth.clamp(windup / hitRate, cooldown * 0.5F, cooldown * 4.0F)
                    : cooldown;
            float speed = animation.endTick / length;
            float blend = Math.max(multiplier - 0.5F, 0.0F) / 0.5F;
            float downwind = (float) (speed * Mth.lerp(blend, 1.0F - upswing, upswing / (1.0F - upswing)));
            this.attack.speed.set(speed / multiplier, List.of(
                    new TransmissionSpeedModifier.Gear(length * upswing, downwind),
                    new TransmissionSpeedModifier.Gear(length, speed)
            ));
            // Better Combat mirrors an off-hand swing, then mirrors again when the body is left-handed.
            boolean leftHanded = npc.getMainArm() == HumanoidArm.LEFT;
            this.attack.mirror.setEnabled(leftHanded != npc.getAttack().offHand());
            KeyframeAnimationPlayer player = new CustomAnimationPlayer(copy.build(), 0);
            int fadeTicks = Math.max(0, copy.beginTick);
            this.attack.base.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeTicks, Ease.INOUTSINE), player);
            // The server lands the hit at the same fraction of the swing.
            this.trailId = id;
            this.trailOffhand = npc.getAttack().offHand();
            this.trailDelay = Math.round(length * hitRate);
            return true;
        }

        private void tickTrail(NpcEntity npc)
        {
            if (this.trailDelay < 0)
            {
                return;
            }
            if (this.trailDelay > 0)
            {
                this.trailDelay--;
                return;
            }
            this.trailDelay = -1;
            NpcTrailParticles.spawn(npc, this.trailId, this.trailOffhand);
        }

        /**
         * The weapon stance stays on during attacks, as Better Combat does for a
         * player: the attack layer sits above it and the stance shows again as the
         * clip fades, with no gap. It is dropped only for actions that pose the
         * arms themselves (using an item, swimming).
         */
        private void updatePose(NpcEntity npc)
        {
            boolean left = npc.getMainArm() == HumanoidArm.LEFT;
            if (npc.isUsingItem() || npc.isSwimming())
            {
                this.poseMisses = 0;
                this.itemPose.setPose(null, left);
                this.bodyPose.setPose(null, left);
                return;
            }
            ItemStack held = npc.getMainHandItem();
            KeyframeAnimation pose = null;
            if (AncientEmpiresConfig.betterCombatEnabled && !held.isEmpty())
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
                }
            }
            this.poseMisses = 0;
            // Walking and crouching keep the weapon stance. The body channel is what
            // holds two-handers, halberds, and greatswords; the item channel only shifts the weapon.
            this.itemPose.setPose(pose, left);
            this.bodyPose.setPose(pose, left);
        }

        private boolean apply(PlayerModel<NpcEntity> model, float partialTick)
        {
            this.applier.setTickDelta(Mth.clamp(partialTick, 0.0F, 1.0F));
            if (!this.applier.isActive())
            {
                return false;
            }
            // The waist bend folds the head, arms, jacket, sleeves, and armor over the
            // hips inside the model's own render, but only while this supplier holds an
            // active animation. Player Animation Lib fills it for players and clears it
            // for every other entity in setupAnim, which has already run by now.
            ((IMutableModel) (Object) model).getEmoteSupplier().set(this.applier);
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
