package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Command (DESIGN Tier 1): a commander inspires the allied soldiers around it,
 * who hit harder and stand better armoured while near it, with any melee
 * weapon. Every rank above soldier commands ({@link NpcRank}), with the strength and
 * reach of {@code command.strengthByRank} and {@code radiusByRank}; a faction with
 * {@code command: none} has no commanders. When a commander falls, the
 * highest-ranked allied soldier near it takes command, at
 * {@code successionScale} of the strength, and turns on the commander's killer
 * (its last order, until orders exist). Inspired is also what morale will read.
 * <p>
 * Only commanders scan, every {@code command.auraTicks}; an inspired NPC just
 * counts down, and its bonus lapses a little after it leaves the aura.
 */
public final class NpcCommand
{
    private static final UUID DAMAGE_ID = UUID.fromString("3b5e3c1e-6a39-4d4f-9a0e-5a1d2b7c9e01");
    private static final UUID ARMOR_ID = UUID.fromString("3b5e3c1e-6a39-4d4f-9a0e-5a1d2b7c9e02");

    private NpcCommand()
    {
    }

    /** How strongly this NPC commands, 0 for not at all. */
    static float strength(NpcEntity npc)
    {
        if (!AncientEmpiresConfig.commandEnabled || !npc.getNpcPath().combat() || npc.isLevied())
        {
            return 0.0F;
        }
        if (npc.getInheritedCommand() > 0.0F)
        {
            return npc.getInheritedCommand();
        }
        if (NpcFactions.commandStyle(npc.getFaction()) == NpcFactions.CommandStyle.NONE)
        {
            return 0.0F;
        }
        return (float) byRank(AncientEmpiresConfig.commandStrengthByRank, npc.getRank());
    }

    /** How far the aura reaches: the rank's reach, or a captain's for a soldier holding inherited command. */
    static double radius(NpcEntity npc)
    {
        double radius = byRank(AncientEmpiresConfig.commandRadiusByRank, npc.getRank());
        return radius > 0.0D ? radius : byRank(AncientEmpiresConfig.commandRadiusByRank, NpcRank.CAPTAIN);
    }

    static double byRank(List<? extends Number> values, NpcRank rank)
    {
        return values.isEmpty() ? 0.0D : values.get(Math.min(rank.ordinal(), values.size() - 1)).doubleValue();
    }

    /** A commander's tick: inspire the allied soldiers in reach. */
    static void tick(NpcEntity commander)
    {
        float strength = strength(commander);
        if (strength <= 0.0F || (commander.tickCount + commander.getId()) % AncientEmpiresConfig.commandAuraTicks != 0)
        {
            return;
        }
        commander.level().getProfiler().push("ancientempires_command");
        double radius = radius(commander);
        int until = AncientEmpiresConfig.commandAuraTicks * 2 + 20;
        for (NpcEntity ally : commander.level().getEntitiesOfClass(NpcEntity.class, commander.getBoundingBox().inflate(radius),
                other -> other != commander && other.isAlive() && other.getNpcPath().combat() && commander.isAlly(other)))
        {
            ally.inspire(strength, until);
        }
        commander.inspire(strength, until);
        commander.level().getProfiler().pop();
    }

    /** Puts the bonuses on, scaled; called when an NPC is inspired or its strength changes. */
    static void applyBonuses(NpcEntity npc, float strength)
    {
        set(npc.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID, "Inspired damage", AncientEmpiresConfig.commandDamageBonus * strength,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        set(npc.getAttribute(Attributes.ARMOR), ARMOR_ID, "Inspired armor", AncientEmpiresConfig.commandArmorBonus * strength,
                AttributeModifier.Operation.ADDITION);
    }

    static void removeBonuses(NpcEntity npc)
    {
        AttributeInstance damage = npc.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null)
        {
            damage.removeModifier(DAMAGE_ID);
        }
        AttributeInstance armor = npc.getAttribute(Attributes.ARMOR);
        if (armor != null)
        {
            armor.removeModifier(ARMOR_ID);
        }
    }

    private static void set(@Nullable AttributeInstance attribute, UUID id, String name, double amount, AttributeModifier.Operation operation)
    {
        if (attribute == null)
        {
            return;
        }
        attribute.removeModifier(id);
        if (amount != 0.0D)
        {
            attribute.addTransientModifier(new AttributeModifier(id, name, amount, operation));
        }
    }

    /**
     * A commander fell: the highest-ranked allied soldier near it takes command,
     * weakened, and turns on the killer.
     */
    static void succeed(NpcEntity fallen, @Nullable LivingEntity killer)
    {
        float strength = strength(fallen);
        if (strength <= 0.0F)
        {
            return;
        }
        double range = AncientEmpiresConfig.commandSuccessionRange;
        NpcEntity heir = null;
        for (NpcEntity ally : fallen.level().getEntitiesOfClass(NpcEntity.class, fallen.getBoundingBox().inflate(range),
                other -> other != fallen && other.isAlive() && other.getNpcPath().combat() && !other.isLevied() && fallen.isAlly(other)))
        {
            if (strength(ally) > 0.0F)
            {
                // Another commander is already here: command does not pass.
                return;
            }
            if (heir == null || rank(ally) > rank(heir))
            {
                heir = ally;
            }
        }
        if (heir == null)
        {
            return;
        }
        float inherited = inherited(strength, AncientEmpiresConfig.commandSuccessionScale, AncientEmpiresConfig.commandMinScale);
        heir.setInheritedCommand(inherited);
        if (killer != null && killer.isAlive() && heir.isScanTarget(killer))
        {
            heir.setTarget(killer);
        }
    }

    /** A successor's strength: the fallen's, weakened by {@code scale}, never below {@code floor}. */
    public static float inherited(float strength, double scale, double floor)
    {
        return (float) Math.max(floor, strength * scale);
    }

    /** Who inherits: the highest rank, then the highest level, then the healthiest. */
    private static double rank(NpcEntity npc)
    {
        return npc.getRank().ordinal() * 10000.0D + npc.getLevel() * 100.0D + npc.getHealth();
    }
}
