package com.ancientempires.combat;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Parries, the same for players and NPCs. A parry is a short timed guard with
 * the weapon ({@code parry.windowTicks}). A melee hit from the front inside it
 * does nothing to the parrier: the attacker is staggered instead (slowed, and no
 * swing for {@code staggerTicks}), with a ring of steel. A parry that catches
 * nothing leaves the parrier open: no swing for {@code recoveryTicks} (a
 * player's attack cooldown resets). That is what a feint baits.
 * <p>
 * Players parry with the Parry key; NPCs decide in {@link NpcParryPlanner}.
 * Server side only; the state is kept here, not saved.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class Parry
{
    /** Game times: the window's end, and the earliest next parry. */
    private static final class State
    {
        long windowEnd;
        long cooldownEnd;
        boolean landed;
        boolean judged;
    }

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

    private Parry()
    {
    }

    /** Opens a parry. False while the last one is cooling down, or parries are off. */
    public static boolean start(LivingEntity parrier)
    {
        if (!AncientEmpiresConfig.parryEnabled || parrier.level().isClientSide)
        {
            return false;
        }
        long now = parrier.level().getGameTime();
        State state = STATES.computeIfAbsent(parrier, entity -> new State());
        if (now < state.cooldownEnd)
        {
            return false;
        }
        state.windowEnd = now + AncientEmpiresConfig.parryWindowTicks;
        state.cooldownEnd = now + AncientEmpiresConfig.parryCooldownTicks;
        state.landed = false;
        state.judged = false;
        if (parrier instanceof NpcEntity npc)
        {
            npc.setParrying(true);
        }
        parrier.level().playSound(null, parrier.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.6F, 1.4F);
        return true;
    }

    public static boolean isParrying(LivingEntity entity)
    {
        State state = STATES.get(entity);
        return state != null && !state.landed && entity.level().getGameTime() <= state.windowEnd;
    }

    /** Whether the parry can be tried now (off cooldown). */
    public static boolean ready(LivingEntity entity)
    {
        State state = STATES.get(entity);
        return state == null || entity.level().getGameTime() >= state.cooldownEnd;
    }

    /** A melee hit on a parrying fighter, from the front, is parried. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event)
    {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !isParrying(target))
        {
            return;
        }
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
                || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.BYPASSES_SHIELD))
        {
            return;
        }
        Vec3 toAttacker = attacker.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        Vec3 facing = target.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D).normalize();
        if (toAttacker.dot(facing) < 0.3D)
        {
            // From the side or behind: a parry only meets what it faces.
            return;
        }
        event.setCanceled(true);
        STATES.get(target).landed = true;
        if (target instanceof NpcEntity npc)
        {
            npc.setParrying(false);
            npc.onParried(attacker);
        }
        stagger(attacker);
        target.level().playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.9F);
        if (target.level() instanceof ServerLevel level)
        {
            Vec3 at = target.position().add(toAttacker.scale(0.6D)).add(0.0D, target.getBbHeight() * 0.65D, 0.0D);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.15D, 0.15D, 0.15D, 0.2D);
        }
    }

    /** Thrown off balance by a parry: slowed, and no swing for a moment. The same for players and NPCs. */
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
            npc.stagger(ticks);
        }
        else if (attacker instanceof Player player)
        {
            player.resetAttackStrengthTicker();
        }
    }

    /** A parry that caught nothing leaves the parrier open, once its window is over. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || STATES.isEmpty())
        {
            return;
        }
        Iterator<Map.Entry<LivingEntity, State>> entries = STATES.entrySet().iterator();
        while (entries.hasNext())
        {
            Map.Entry<LivingEntity, State> entry = entries.next();
            LivingEntity parrier = entry.getKey();
            State state = entry.getValue();
            if (!parrier.isAlive())
            {
                entries.remove();
                continue;
            }
            long now = parrier.level().getGameTime();
            if (state.judged)
            {
                if (now >= state.cooldownEnd)
                {
                    entries.remove();
                }
                continue;
            }
            if (now <= state.windowEnd)
            {
                continue;
            }
            state.judged = true;
            if (parrier instanceof NpcEntity npc)
            {
                npc.setParrying(false);
                if (!state.landed)
                {
                    npc.parryWhiffed(AncientEmpiresConfig.parryRecoveryTicks);
                }
            }
            else if (parrier instanceof Player player && !state.landed)
            {
                player.resetAttackStrengthTicker();
            }
        }
    }
}
