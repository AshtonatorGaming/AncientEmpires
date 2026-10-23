package com.ancientempires.item;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A bandage, or a bandage roll (32 uses in one unstackable item). Carried in
 * the off hand it makes a soldier a medic, who binds wounded allies with it;
 * a player can hold it to bind their own wounds. Either way each use heals
 * {@code medic.bandageHeal}.
 */
public final class BandageItem extends Item
{
    private static final int USE_TICKS = 24;

    public BandageItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        if (player.getHealth() >= player.getMaxHealth())
        {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user)
    {
        if (!level.isClientSide)
        {
            bind(user);
            if (!(user instanceof Player player && player.getAbilities().instabuild))
            {
                useUp(stack, user, user.getUsedItemHand());
            }
        }
        return stack;
    }

    /**
     * One use: a loose bandage is spent, a bandage roll loses one of its uses
     * (it cannot stack, as vanilla never stacks worn items, so it carries
     * 32 uses instead) and is gone when they run out.
     */
    public static void useUp(ItemStack stack, LivingEntity user, InteractionHand hand)
    {
        if (stack.isDamageableItem())
        {
            stack.hurtAndBreak(1, user, holder -> holder.broadcastBreakEvent(hand));
        }
        else
        {
            stack.shrink(1);
        }
    }

    /** Heals a wound, with the sound of cloth. */
    public static void bind(LivingEntity patient)
    {
        patient.heal((float) AncientEmpiresConfig.medicBandageHeal);
        patient.level().playSound(null, patient.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 1.0F, 1.2F);
    }

    @Override
    public int getUseDuration(ItemStack stack)
    {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack)
    {
        return UseAnim.NONE;
    }
}
