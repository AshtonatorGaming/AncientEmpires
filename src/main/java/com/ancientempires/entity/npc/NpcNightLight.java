package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Night watch lights, as ToroQuest's guards carried. A soldier awake at night
 * (or somewhere dark) with a free off hand holds up a torch or lantern
 * ({@code ancientempires:night_lights}): an owned one takes it from its
 * backpack and puts it back at dawn; a faction guard is issued one for the
 * night ({@code nightLight.factionGuards}), which never drops or repacks.
 * Dynamic light mods light any living thing holding a light item, so the
 * NPC lights its way with no extra code. A shield keeps the off hand.
 */
public final class NpcNightLight
{
    public static final TagKey<Item> NIGHT_LIGHTS = ItemTags.create(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "night_lights"));
    /** Ticks between checks, staggered by entity id. */
    public static final int CHECK_TICKS = 20;
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
        if (wantsLight(npc))
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
            else if (!npc.isOwnedNpc() && AncientEmpiresConfig.nightLightFactionGuards)
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
    private static boolean wantsLight(NpcEntity npc)
    {
        if (!AncientEmpiresConfig.nightLightEnabled || !npc.getNpcPath().combat() || npc.isSleeping())
        {
            return false;
        }
        Level level = npc.level();
        return level.isNight() || level.getMaxLocalRawBrightness(npc.blockPosition()) < AncientEmpiresConfig.nightLightDarkness;
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
