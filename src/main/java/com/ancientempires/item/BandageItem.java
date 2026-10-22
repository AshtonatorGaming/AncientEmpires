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
 * A bandage. Carried in the off hand it makes a soldier a medic, who binds
 * wounded allies with it; a player can hold it to bind their own wounds.
 * Either way it heals {@code medic.bandageHeal}.
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
                stack.shrink(1);
            }
        }
        return stack;
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
