package com.ancientempires.item;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The War Horn, blown like a goat horn. Its owner's NPCs within
 * {@code horn.range} answer the call it is set to: Rally (soldiers, asleep or
 * off duty, come and follow), Call to Arms (rally, and settlers and workers
 * are levied to follow and fight), or Stand Down (levies end, nobody
 * follows, and everyone goes home or back to work). Sneak-use switches the
 * call. Each call has its own sound, so a town hears which one it was.
 */
public final class WarHornItem extends Item
{
    private static final String CALL_KEY = "AeHornCall";
    private static final int USE_TICKS = 40;

    /** What the horn tells the NPCs who hear it. */
    public enum Call
    {
        RALLY("rally"),
        CALL_TO_ARMS("call_to_arms"),
        STAND_DOWN("stand_down");

        private final String id;

        Call(String id)
        {
            this.id = id;
        }

        public Component displayName()
        {
            return Component.translatable("item.ancientempires.war_horn.call." + this.id);
        }

        Call next()
        {
            Call[] calls = values();
            return calls[(this.ordinal() + 1) % calls.length];
        }
    }

    public WarHornItem(Properties properties)
    {
        super(properties);
    }

    public static Call call(ItemStack stack)
    {
        int index = stack.getOrCreateTag().getInt(CALL_KEY);
        Call[] calls = Call.values();
        return index >= 0 && index < calls.length ? calls[index] : Call.RALLY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown())
        {
            Call next = call(stack).next();
            stack.getOrCreateTag().putInt(CALL_KEY, next.ordinal());
            player.displayClientMessage(Component.translatable("message.ancientempires.war_horn.set", next.displayName()), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        Call call = call(stack);
        player.startUsingItem(hand);
        player.getCooldowns().addCooldown(this, AncientEmpiresConfig.hornCooldownTicks);
        if (!level.isClientSide)
        {
            level.playSound(null, player, sound(call), SoundSource.PLAYERS, 16.0F, 1.0F);
            int answered = 0;
            double range = AncientEmpiresConfig.hornRange;
            for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, player.getBoundingBox().inflate(range),
                    npc -> npc.isAlive() && player.getUUID().equals(npc.getOwner()) && npc.distanceToSqr(player) <= range * range))
            {
                npc.answerHorn(call);
                answered++;
            }
            player.displayClientMessage(Component.translatable("message.ancientempires.war_horn.blown", call.displayName(), answered), true);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** Vanilla's horn calls: the raid horn for a call to arms, goat horns for the others. */
    private static SoundEvent sound(Call call)
    {
        Holder<SoundEvent> sound = switch (call)
        {
            case RALLY -> SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5);
            case CALL_TO_ARMS -> SoundEvents.RAID_HORN;
            case STAND_DOWN -> SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(7);
        };
        return sound.value();
    }

    @Override
    public int getUseDuration(ItemStack stack)
    {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack)
    {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.ancientempires.war_horn.current", call(stack).displayName()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.ancientempires.war_horn.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
