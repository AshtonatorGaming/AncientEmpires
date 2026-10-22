package com.ancientempires.entity.npc;

import com.ancientempires.combat.WeaponClassifier;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Bow-to-melee fallback for a cornered archer. The bow trades places with the
 * best melee weapon in the backpack, so the bow goal stops and the melee goal
 * takes over without either knowing about the other. The bow comes back to the
 * main hand when the target dies, is lost, or stays away for a while.
 * <p>
 * The backpack is scanned only after it changes. While the bow is primary the
 * per-tick cost is one cooldown decrement.
 * <p>
 * The backpack slot that holds the bow is saved, so a world saved mid-fight
 * loads with the bow back in the main hand.
 */
public final class NpcSidearm
{
    private static final String BOW_SLOT_KEY = "AeSidearmBowSlot";

    private final NpcEntity npc;

    /** Backpack index (0-8) holding the bow while a sidearm is drawn. -1 while the bow is primary. */
    private int bowSlot = -1;
    private int cooldown;
    private int farTicks;

    private int scannedVersion = Integer.MIN_VALUE;
    private int bestSlot = -1;

    NpcSidearm(NpcEntity npc)
    {
        this.npc = npc;
    }

    public boolean isDrawn()
    {
        return this.bowSlot >= 0;
    }

    /**
     * Backpack slot of the sidearm to draw against this target, or -1. Called by
     * the bow goal each tick it holds the fight, so the cheap tests come first.
     */
    public int corneredSlot(LivingEntity target)
    {
        if (!AncientEmpiresConfig.sidearmEnabled || this.bowSlot >= 0 || this.cooldown > 0
                || this.npc.isHoldingPosition())
        {
            return -1;
        }
        double range = AncientEmpiresConfig.sidearmCorneredRange;
        if (this.npc.distanceToSqr(target) > range * range)
        {
            return -1;
        }
        return this.bestSlot();
    }

    /**
     * Swaps the bow in the main hand with the given backpack slot. The caller
     * stops the bow draw first.
     */
    public void draw(int slot)
    {
        if (this.bowSlot >= 0 || !this.npc.isHoldingBow())
        {
            return;
        }
        this.npc.getInventory().swapMainHandWithBackpack(slot);
        this.bowSlot = slot;
        this.cooldown = AncientEmpiresConfig.sidearmSwapCooldown;
        this.farTicks = 0;
    }

    /**
     * Server tick from the entity. Brings the bow back when the fight is over
     * or the target has stayed out past the release range.
     */
    void tick()
    {
        if (this.cooldown > 0)
        {
            this.cooldown--;
        }
        if (this.bowSlot < 0)
        {
            return;
        }
        if (!this.bowStillStored())
        {
            // The owner rearranged the gear by hand. Keep what they chose.
            this.bowSlot = -1;
            return;
        }
        LivingEntity target = this.npc.getTarget();
        if (target == null || !target.isAlive())
        {
            this.restore();
            return;
        }
        double release = AncientEmpiresConfig.sidearmReleaseRange;
        if (this.npc.distanceToSqr(target) > release * release)
        {
            this.farTicks++;
        }
        else
        {
            this.farTicks = 0;
        }
        if (this.farTicks >= AncientEmpiresConfig.sidearmReleaseTicks && this.cooldown <= 0)
        {
            this.restore();
        }
    }

    /**
     * Puts the bow back in the main hand now, if a sidearm is drawn. The
     * sidearm returns to the slot it came from. Repack calls this first.
     */
    void restore()
    {
        if (this.bowSlot < 0)
        {
            return;
        }
        if (this.bowStillStored())
        {
            this.npc.getInventory().swapMainHandWithBackpack(this.bowSlot);
            this.cooldown = AncientEmpiresConfig.sidearmSwapCooldown;
        }
        this.bowSlot = -1;
        this.farTicks = 0;
    }

    void save(CompoundTag tag)
    {
        if (this.bowSlot >= 0)
        {
            tag.putByte(BOW_SLOT_KEY, (byte) this.bowSlot);
        }
    }

    /**
     * Runs after the hands and backpack have loaded. A saved swap is undone
     * here, so the NPC always loads with its bow as the primary weapon.
     */
    void load(CompoundTag tag)
    {
        this.bowSlot = -1;
        this.farTicks = 0;
        this.cooldown = 0;
        if (!tag.contains(BOW_SLOT_KEY, Tag.TAG_BYTE))
        {
            return;
        }
        int slot = tag.getByte(BOW_SLOT_KEY);
        if (slot >= 0 && slot < NpcInventory.BACKPACK_SLOTS)
        {
            this.bowSlot = slot;
            this.restore();
            this.cooldown = 0;
        }
    }

    private boolean bowStillStored()
    {
        ItemStack stored = this.npc.getInventory().getItem(NpcInventory.BACKPACK_START + this.bowSlot);
        return WeaponClassifier.isBow(stored) && !this.npc.isHoldingBow();
    }

    /**
     * Highest main-hand attack damage in the backpack. Ties keep the first slot.
     * Rescanned only when the backpack has changed since the last call.
     */
    private int bestSlot()
    {
        NpcInventory inventory = this.npc.getInventory();
        int version = inventory.getBackpackVersion();
        if (version == this.scannedVersion)
        {
            return this.bestSlot;
        }
        this.scannedVersion = version;
        this.bestSlot = -1;
        double best = 0.0D;
        for (int slot = 0; slot < NpcInventory.BACKPACK_SLOTS; slot++)
        {
            double damage = WeaponClassifier.meleeDamage(inventory.getItem(NpcInventory.BACKPACK_START + slot));
            if (damage > best)
            {
                best = damage;
                this.bestSlot = slot;
            }
        }
        return this.bestSlot;
    }
}
