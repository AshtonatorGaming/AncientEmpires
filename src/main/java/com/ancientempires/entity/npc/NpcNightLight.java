package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.ancientempires.combat.BetterCombatBridge;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Night watch lights, as ToroQuest's guards carried. A soldier awake at night
 * (or somewhere dark) with a free off hand holds up a torch or lantern
 * ({@code ancientempires:night_lights}): an owned one takes it from its
 * backpack and puts it back at dawn; a faction guard is issued one for the
 * night ({@code nightLight.factionGuards}; not beasts, giants, demons, and other factions
 * whose {@code nightLights} is false), which never drops or repacks. No light with a
 * weapon that takes both hands (bows, crossbows, Better Combat two-handers).
 * Dynamic light mods light any living thing holding a light item, so the
 * NPC lights its way with no extra code. A shield keeps the off hand.
 */
public final class NpcNightLight
{
    public static final TagKey<Item> NIGHT_LIGHTS = ItemTags.create(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "night_lights"));
    /** Ticks between checks, staggered by entity id. */
    public static final int CHECK_TICKS = 20;
    private static final UUID WATCH_DAMAGE_ID = UUID.fromString("7a1f3c2d-5e6b-4c8a-9d0e-1f2a3b4c5d6e");
    /** Marks a light issued to a faction guard for the night. */
    private static final String ISSUED_KEY = "AeNightLight";

    private NpcNightLight()
    {
    }

    public static boolean isLight(ItemStack stack)
    {
        return !stack.isEmpty() && stack.is(NIGHT_LIGHTS);
    }

    static void update(NpcEntity npc)
    {
        ItemStack offHand = npc.getOffhandItem();
        boolean watch = onWatch(npc);
        setWatch(npc, watch);
        if (watch && !needsBothHands(npc))
        {
            if (!offHand.isEmpty())
            {
                return;
            }
            int slot = backpackLight(npc);
            if (slot >= 0)
            {
                NpcInventory inventory = npc.getInventory();
                npc.setItemSlot(EquipmentSlot.OFFHAND, inventory.getItem(slot));
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            else if (!npc.isOwnedNpc() && AncientEmpiresConfig.nightLightFactionGuards && NpcFactions.carriesLights(npc.getFaction()))
            {
                ItemStack issued = new ItemStack(npc.getRandom().nextBoolean() ? Items.LANTERN : Items.TORCH);
                issued.getOrCreateTag().putBoolean(ISSUED_KEY, true);
                npc.setItemSlot(EquipmentSlot.OFFHAND, issued);
            }
            return;
        }
        if (!isLight(offHand))
        {
            return;
        }
        if (isIssued(offHand))
        {
            npc.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            return;
        }
        // Back in the backpack by day; with no room it stays in hand.
        NpcInventory inventory = npc.getInventory();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            if (inventory.getItem(slot).isEmpty())
            {
                inventory.setItem(slot, offHand);
                npc.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                return;
            }
        }
    }

    /** Takes back an issued light before the NPC is packed or dies, so none leaks out. */
    static void stripIssued(NpcEntity npc)
    {
        if (isIssued(npc.getOffhandItem()))
        {
            npc.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    private static boolean isIssued(ItemStack stack)
    {
        return !stack.isEmpty() && stack.getTag() != null && stack.getTag().getBoolean(ISSUED_KEY);
    }

    /** A soldier on watch: awake, and it is night or dark where it stands. */
    /** A soldier awake at night or in the dark: on the watch, with a light if a hand is free. */
    static boolean onWatch(NpcEntity npc)
    {
        if (!AncientEmpiresConfig.nightLightEnabled || !npc.getNpcPath().combat() || npc.isSleeping())
        {
            return false;
        }
        Level level = npc.level();
        return level.isNight() || level.getMaxLocalRawBrightness(npc.blockPosition()) < AncientEmpiresConfig.nightLightDarkness;
    }

    /** Night Watch: a transient damage bonus while on the watch (morale reads {@link NpcEntity#isOnWatch}). */
    private static void setWatch(NpcEntity npc, boolean watch)
    {
        npc.setOnWatch(watch);
        AttributeInstance damage = npc.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null)
        {
            return;
        }
        boolean has = damage.getModifier(WATCH_DAMAGE_ID) != null;
        if (watch && !has && AncientEmpiresConfig.nightWatchDamage > 0.0D)
        {
            damage.addTransientModifier(new AttributeModifier(WATCH_DAMAGE_ID, "Night watch", AncientEmpiresConfig.nightWatchDamage,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        else if (!watch && has)
        {
            damage.removeModifier(WATCH_DAMAGE_ID);
        }
    }

    /**
     * A weapon that takes both hands leaves none for a light: a bow or crossbow, or
     * anything Better Combat marks two-handed (pikes, greatswords, halberds).
     */
    private static boolean needsBothHands(NpcEntity npc)
    {
        ItemStack main = npc.getMainHandItem();
        if (main.getItem() instanceof ProjectileWeaponItem)
        {
            return true;
        }
        return BetterCombatBridge.lookup(main).map(BetterCombatBridge.WeaponSnapshot::twoHanded).orElse(false);
    }

    private static int backpackLight(NpcEntity npc)
    {
        NpcInventory inventory = npc.getInventory();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            if (isLight(inventory.getItem(slot)))
            {
                return slot;
            }
        }
        return -1;
    }
}
