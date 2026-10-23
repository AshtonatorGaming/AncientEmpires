package com.ancientempires.entity.npc;

import com.ancientempires.combat.WeaponClassifier;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A shield bash for NPCs, mirroring Spartan Shields' player bash (read from
 * its ShieldBashPacket handler): knock the target back with strength 1 plus
 * Knockback, deal 1 damage plus any stored Payback damage, set it alight with
 * Firebrand, and wear the shield by 5. Spartan only lets players bash with a
 * key; NPCs counter-bash after a blocked hit instead.
 * <p>
 * Which shields can bash is {@code shield.bashMode}: {@code SPARTAN} uses
 * Spartan's {@code shields_with_bash} tag, which is empty without that mod, so
 * nothing bashes; {@code ALL} lets any shield bash; {@code OFF} turns it off.
 * Spartan's enchantments and sounds are looked up by id, so none of its
 * classes are needed.
 */
public final class NpcShieldBash
{
    public enum Mode
    {
        SPARTAN,
        ALL,
        OFF
    }

    private static final TagKey<Item> SHIELDS_WITH_BASH = ItemTags.create(ResourceLocation.fromNamespaceAndPath("spartanshields", "shields_with_bash"));
    private static final TagKey<Item> TOWER_SHIELDS = ItemTags.create(ResourceLocation.fromNamespaceAndPath("spartanshields", "tower_shields"));
    private static final ResourceLocation PAYBACK = ResourceLocation.fromNamespaceAndPath("spartanshields", "payback");
    private static final ResourceLocation FIREBRAND = ResourceLocation.fromNamespaceAndPath("spartanshields", "firebrand");
    private static final ResourceLocation HIT_SOUND = ResourceLocation.fromNamespaceAndPath("spartanshields", "shield_bash_hit");
    private static final String PAYBACK_DAMAGE = "PaybackDamage";

    private NpcShieldBash()
    {
    }

    /** The NPC is blocking with a shield that may bash, and the bash is off cooldown. */
    static boolean canBash(NpcEntity npc)
    {
        if (!npc.isRaisingShield() || !npc.isBashReady())
        {
            return false;
        }
        ItemStack shield = npc.getUseItem();
        return switch (AncientEmpiresConfig.shieldBashMode)
        {
            case SPARTAN -> shield.is(SHIELDS_WITH_BASH);
            case ALL -> WeaponClassifier.isShield(shield);
            case OFF -> false;
        };
    }

    /**
     * The tell before a bash: the NPC braces behind its raised shield for
     * {@code shield.bashWindupTicks} with a heavy creak, so a player hears it coming.
     */
    static void brace(NpcEntity npc, LivingEntity target)
    {
        // Only a player needs to read it; NPC-on-NPC bashes in a big battle stay quiet.
        if (target instanceof Player)
        {
            npc.playSound(SoundEvents.ARMOR_EQUIP_IRON, 0.8F, 0.6F);
        }
    }

    /**
     * After the brace: the shield, still raised, is shoved forward with a short
     * lunge, and only then comes down for the recovery, like a player's bash.
     * Bashes the target if it is still in front and within reach; starts the
     * cooldown either way.
     */
    static void bash(NpcEntity npc, LivingEntity target)
    {
        if (!canBash(npc))
        {
            return;
        }
        ItemStack shield = npc.getUseItem();
        boolean tower = shield.is(TOWER_SHIELDS);
        npc.swing(InteractionHand.OFF_HAND);
        float lungeYaw = npc.getYRot() * Mth.DEG_TO_RAD;
        npc.push(-Mth.sin(lungeYaw) * 0.3D, 0.0D, Mth.cos(lungeYaw) * 0.3D);
        double reach = AncientEmpiresConfig.shieldBashRange + target.getBbWidth() * 0.5D;
        boolean lands = target.isAlive() && npc.distanceToSqr(target) <= reach * reach && npc.hasLineOfSight(target);
        // The shove lands with the shield up; it comes down after, for the recovery.
        npc.startBashCooldown(tower ? AncientEmpiresConfig.towerBashCooldownTicks : AncientEmpiresConfig.shieldBashCooldownTicks);
        if (!lands)
        {
            npc.lowerShield();
            return;
        }
        npc.lowerShield();
        float yaw = npc.getYRot() * Mth.DEG_TO_RAD;
        int knockback = shield.getEnchantmentLevel(Enchantments.KNOCKBACK);
        target.knockback(1.0D + knockback, Mth.sin(yaw), -Mth.cos(yaw));
        float damage = 1.0F + takePayback(shield);
        target.hurt(npc.damageSources().mobAttack(npc), damage);
        Enchantment firebrand = ForgeRegistries.ENCHANTMENTS.getValue(FIREBRAND);
        if (firebrand != null && shield.getEnchantmentLevel(firebrand) > 0)
        {
            target.setSecondsOnFire(5);
        }
        if (!npc.isCreative())
        {
            shield.hurtAndBreak(5, npc, entity -> entity.broadcastBreakEvent(InteractionHand.OFF_HAND));
        }
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(HIT_SOUND);
        npc.playSound(sound != null ? sound : SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 1.0F);
    }

    /** Spartan's Payback stores blocked damage on the shield; a bash spends it. */
    private static float takePayback(ItemStack shield)
    {
        Enchantment payback = ForgeRegistries.ENCHANTMENTS.getValue(PAYBACK);
        CompoundTag tag = shield.getTag();
        if (payback == null || tag == null || shield.getEnchantmentLevel(payback) <= 0)
        {
            return 0.0F;
        }
        float stored = tag.getFloat(PAYBACK_DAMAGE);
        tag.putFloat(PAYBACK_DAMAGE, 0.0F);
        return stored;
    }
}
