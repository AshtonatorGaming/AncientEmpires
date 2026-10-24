package com.ancientempires.combat;

import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

/**
 * One place that decides what kind of weapon a stack is. The item-type part
 * is cached per item class, so Spartan Weaponry's throwing class is looked up
 * once and never linked. Attack damage is read from the stack because a stack
 * can carry its own attribute modifiers. Callers that scan the backpack only
 * do so when it changes.
 */
public final class WeaponClassifier
{
    private static final String SPARTAN_THROWING = "com.oblivioussp.spartanweaponry.item.ThrowingWeaponItem";

    public enum Kind
    {
        /** A {@link BowItem} that is not a crossbow. The archer goal's weapon. */
        BOW,
        /** Crossbows, tridents, Spartan throwing weapons. Ranged, but not a bow here. */
        OTHER_RANGED,
        SHIELD,
        /** Anything else. Only a melee weapon if it also has attack damage. */
        PLAIN
    }

    @Nullable
    private static final Class<?> THROWING = findThrowing();

    private static final ClassValue<Kind> KINDS = new ClassValue<>()
    {
        @Override
        protected Kind computeValue(Class<?> type)
        {
            return classify(type);
        }
    };

    private WeaponClassifier()
    {
    }

    public static Kind kind(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return Kind.PLAIN;
        }
        return KINDS.get(stack.getItem().getClass());
    }

    /** A Spartan Weaponry throwing weapon (javelin, tomahawk, throwing knife, boomerang). */
    public static boolean isThrowing(ItemStack stack)
    {
        return THROWING != null && !stack.isEmpty() && THROWING.isInstance(stack.getItem());
    }

    public static boolean isBow(ItemStack stack)
    {
        return kind(stack) == Kind.BOW;
    }

    /**
     * A shield by Forge's tool action, which modded shields (Spartan Shields and
     * others) declare even when they do not extend the vanilla shield. Vanilla
     * blocking checks the same action, so this matches what can actually block.
     */
    public static boolean isShield(ItemStack stack)
    {
        return !stack.isEmpty() && (kind(stack) == Kind.SHIELD || stack.canPerformAction(ToolActions.SHIELD_BLOCK));
    }

    /**
     * A sidearm: main-hand attack damage, and not a bow, crossbow, throwing
     * weapon, trident, or shield. Zero means not a melee weapon.
     */
    public static double meleeDamage(ItemStack stack)
    {
        if (stack.isEmpty() || kind(stack) != Kind.PLAIN || isShield(stack))
        {
            return 0.0D;
        }
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        double damage = 0.0D;
        boolean found = false;
        for (AttributeModifier modifier : modifiers.get(Attributes.ATTACK_DAMAGE))
        {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION)
            {
                damage += modifier.getAmount();
                found = true;
            }
        }
        // A weapon whose only damage modifier is a multiplier still counts, just lowest.
        if (!found)
        {
            return modifiers.containsKey(Attributes.ATTACK_DAMAGE) ? Double.MIN_VALUE : 0.0D;
        }
        return Math.max(damage, Double.MIN_VALUE);
    }

    private static Kind classify(Class<?> type)
    {
        if (CrossbowItem.class.isAssignableFrom(type) || TridentItem.class.isAssignableFrom(type))
        {
            return Kind.OTHER_RANGED;
        }
        if (THROWING != null && THROWING.isAssignableFrom(type))
        {
            return Kind.OTHER_RANGED;
        }
        if (BowItem.class.isAssignableFrom(type))
        {
            return Kind.BOW;
        }
        if (ShieldItem.class.isAssignableFrom(type))
        {
            return Kind.SHIELD;
        }
        return Kind.PLAIN;
    }

    @Nullable
    private static Class<?> findThrowing()
    {
        try
        {
            return Class.forName(SPARTAN_THROWING, false, WeaponClassifier.class.getClassLoader());
        }
        catch (ClassNotFoundException | LinkageError exception)
        {
            return null;
        }
    }
}
