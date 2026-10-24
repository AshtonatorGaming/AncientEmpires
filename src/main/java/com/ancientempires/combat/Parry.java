package com.ancientempires.combat;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The perfect block, the same for players and NPCs: a shield raised just before
 * a melee hit lands ({@code parry.windowTicks} of the raise) turns it aside
 * whole. It costs no guard, and the attacker is staggered (slowed, and no swing
 * for {@code staggerTicks}), with a ring of steel. A shield held up longer is an
 * ordinary block (vanilla's, which wears the guard). It also covers vanilla's
 * five ticks before a raised shield blocks, so a shield that looks raised always
 * blocks. A raise only counts as a perfect block if the last one was at least
 * {@code cooldownTicks} before, so tapping the shield does not farm them.
 * <p>
 * Players simply raise their shield; NPCs time it in their shield goal.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class Parry
{
    /** Game times of the current raise and the one before it. */
    private static final class Raises
    {
        long current = Long.MIN_VALUE / 2;
        long previous = Long.MIN_VALUE / 2;
    }

    private static final Map<LivingEntity, Raises> RAISES = new WeakHashMap<>();

    private Parry()
    {
    }

    /** Remembers when each shield goes up. */
    @SubscribeEvent
    public static void onRaise(LivingEntityUseItemEvent.Start event)
    {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !WeaponClassifier.isShield(event.getItem()))
        {
            return;
        }
        Raises raises = RAISES.computeIfAbsent(entity, key -> new Raises());
        raises.previous = raises.current;
        raises.current = entity.level().getGameTime();
    }

    /** Whether a hit landing now would be a perfect block. */
    public static boolean inPerfectWindow(LivingEntity defender)
    {
        if (!AncientEmpiresConfig.parryEnabled || !defender.isUsingItem() || !WeaponClassifier.isShield(defender.getUseItem()))
        {
            return false;
        }
        if (defender instanceof NpcEntity npc && !npc.getGuard().isPlannedRaise())
        {
            // An NPC raising its shield for any other reason (its guard stance, after its own swing)
            // just blocks; only a timed attempt can be perfect, at its skill's chance.
            return false;
        }
        Raises raises = RAISES.get(defender);
        if (raises == null)
        {
            return false;
        }
        return perfectWindow(defender.level().getGameTime(), raises.current, raises.previous,
                AncientEmpiresConfig.parryWindowTicks, AncientEmpiresConfig.parryCooldownTicks);
    }

    /**
     * Players who hit a shield this tick. Vanilla follows a hit that does no damage with
     * the "no damage" thump, which sounds like an impact; on a shield only the shield's
     * own sound should play.
     */
    private static final Map<Player, Long> SHIELD_HITS = new WeakHashMap<>();

    @SubscribeEvent
    public static void onSound(PlayLevelSoundEvent.AtPosition event)
    {
        if (SHIELD_HITS.isEmpty() || event.getLevel().isClientSide || event.getSound() == null
                || event.getSound().value() != SoundEvents.PLAYER_ATTACK_NODAMAGE)
        {
            return;
        }
        long now = event.getLevel().getGameTime();
        Vec3 at = event.getPosition();
        for (Map.Entry<Player, Long> hit : SHIELD_HITS.entrySet())
        {
            if (hit.getValue() == now && hit.getKey().position().distanceToSqr(at) < 0.01D)
            {
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * The rule itself: a hit now is a perfect block if the shield went up at most
     * {@code window} ticks ago, and that raise came at least {@code cooldown} after the
     * one before (so tapping the shield does not farm them).
     */
    public static boolean perfectWindow(long now, long raised, long raisedBefore, int window, int cooldown)
    {
        return now - raised <= window && raised - raisedBefore >= cooldown;
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event)
    {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !target.isUsingItem() || !WeaponClassifier.isShield(target.getUseItem()))
        {
            return;
        }
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
                || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.BYPASSES_SHIELD))
        {
            return;
        }
        // The same front check vanilla uses for a shield, by where the head faces.
        Vec3 fromAttacker = target.position().subtract(attacker.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        Vec3 facing = Vec3.directionFromRotation(0.0F, target.getYHeadRot());
        if (fromAttacker.dot(facing) >= 0.0D)
        {
            return;
        }
        if (attacker instanceof Player player)
        {
            // Caught on the shield (perfect, instant, or vanilla's block): only the shield sounds.
            SHIELD_HITS.put(player, target.level().getGameTime());
        }
        if (inPerfectWindow(target) || target instanceof NpcEntity npc && attacker instanceof Player && npc.getGuard().rollsPerfectAgainstPlayer())
        {
            perfectBlock(event, target, attacker, fromAttacker);
            return;
        }
        if (AncientEmpiresConfig.shieldInstantBlock && !target.isBlocking())
        {
            // Vanilla's five ticks before a raised shield blocks: block anyway, so a shield that
            // looks raised is raised. An ordinary block, wearing the shield (and an NPC's guard).
            // With Responsive Shields or the like the shield already blocks, so this never runs.
            event.setCanceled(true);
            target.level().broadcastEntityEvent(target, (byte) 29);
            if (target instanceof NpcEntity npc)
            {
                npc.takeOnShield(event.getAmount());
            }
            else
            {
                target.getUseItem().hurtAndBreak(1 + (int) event.getAmount(), target,
                        entity -> entity.broadcastBreakEvent(entity.getUsedItemHand()));
            }
        }
    }

    private static void perfectBlock(LivingAttackEvent event, LivingEntity target, LivingEntity attacker, Vec3 fromAttacker)
    {
        event.setCanceled(true);
        if (target instanceof NpcEntity npc)
        {
            npc.getGuard().onPerfectBlock();
            npc.addXp(AncientEmpiresConfig.levelXpPerPerfectBlock);
            // The shield snaps forward, turning the blow aside.
            npc.swing(npc.getUsedItemHand());
        }
        // The shield bounces the attacker back a step, so the stagger reads at a glance.
        attacker.knockback(0.6D, target.getX() - attacker.getX(), target.getZ() - attacker.getZ());
        attacker.hurtMarked = true;
        stagger(attacker);
        // Unmistakable: a ringing clang over the shield's thud, and a word to any player involved.
        target.level().playSound(null, target.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
        target.level().playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 1.6F);
        if (target instanceof Player player)
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.perfect_block").withStyle(ChatFormatting.GOLD), true);
        }
        if (attacker instanceof Player player)
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.staggered").withStyle(ChatFormatting.RED), true);
        }
        if (target.level() instanceof ServerLevel level)
        {
            Vec3 at = target.position().subtract(fromAttacker.scale(0.6D)).add(0.0D, target.getBbHeight() * 0.65D, 0.0D);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.15D, 0.15D, 0.15D, 0.2D);
        }
    }


    /** Thrown off balance by a perfect block: slowed, and no swing for a moment. The same for players and NPCs. */
    public static void stagger(LivingEntity attacker)
    {
        int ticks = AncientEmpiresConfig.parryStaggerTicks;
        if (ticks <= 0)
        {
            return;
        }
        attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 2, false, false, false));
        if (attacker instanceof NpcEntity npc)
        {
            npc.getGuard().stagger(ticks);
        }
        else if (attacker instanceof Player player)
        {
            player.resetAttackStrengthTicker();
        }
    }
}
