package com.ancientempires.item;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcChain;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcFormation;
import com.ancientempires.entity.npc.NpcOrder;
import com.ancientempires.entity.npc.NpcRank;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Signal Flag, the field tool of the chain of command
 * (docs/chain-of-command.md). Use it on one of your sergeants, captains, or
 * generals to take up their unit; then use it on your soldiers to put them
 * under that leader, or take them out again (a captain takes sergeants, with
 * their squads, and soldiers; a general captains and sergeants too). Used in
 * the air it gives the selected unit its order (Follow you, or Free), sneaking
 * it changes the unit's formation (line, column, loose). Using it on the
 * selected leader again puts the flag down.
 */
public class SignalFlagItem extends Item
{
    private static final String LEADER_KEY = "Leader";
    private static final String LEADER_NAME_KEY = "LeaderName";

    public SignalFlagItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack ignored, Player player, LivingEntity target, InteractionHand hand)
    {
        if (!(target instanceof NpcEntity npc))
        {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        // The held stack itself (in creative the one passed in is a copy).
        ItemStack flag = player.getItemInHand(hand);
        if (!npc.isOwnedNpc() || !player.getUUID().equals(npc.getOwner()))
        {
            say(player, "message.ancientempires.signal.not_yours");
            return InteractionResult.CONSUME;
        }
        NpcEntity selected = selected(flag, player);
        if (selected == npc)
        {
            flag.removeTagKey(LEADER_KEY);
            flag.removeTagKey(LEADER_NAME_KEY);
            say(player, "message.ancientempires.signal.put_down", npc.getDisplayName());
            return InteractionResult.CONSUME;
        }
        if (selected != null && npc.getRank().ordinal() < selected.getRank().ordinal())
        {
            NpcChain.Assign result = NpcChain.toggle(selected, npc);
            switch (result)
            {
                case ADDED -> say(player, "message.ancientempires.signal.added", npc.getDisplayName(), selected.getDisplayName(), count(selected));
                case REMOVED -> say(player, "message.ancientempires.signal.removed", npc.getDisplayName(), selected.getDisplayName(), count(selected));
                default -> say(player, "message.ancientempires.signal." + result.name().toLowerCase(Locale.ROOT), npc.getDisplayName());
            }
            return InteractionResult.CONSUME;
        }
        if (npc.getRank() == NpcRank.SOLDIER)
        {
            say(player, "message.ancientempires.signal.select_leader");
            return InteractionResult.CONSUME;
        }
        if (!npc.getNpcPath().combat())
        {
            say(player, "message.ancientempires.signal.not_soldier", npc.getDisplayName());
            return InteractionResult.CONSUME;
        }
        CompoundTag tag = flag.getOrCreateTag();
        tag.putUUID(LEADER_KEY, npc.getUUID());
        tag.putString(LEADER_NAME_KEY, Component.Serializer.toJson(npc.getDisplayName()));
        NpcChain chain = npc.getChain();
        say(player, "message.ancientempires.signal.selected", npc.getDisplayName(), count(npc),
                Component.translatable("order.ancientempires." + chain.order().id()),
                Component.translatable("formation.ancientempires." + chain.formation().id()));
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack flag = player.getItemInHand(hand);
        if (level.isClientSide)
        {
            return InteractionResultHolder.success(flag);
        }
        if (!flag.hasTag() || !flag.getTag().hasUUID(LEADER_KEY))
        {
            say(player, "message.ancientempires.signal.select_leader");
            return InteractionResultHolder.consume(flag);
        }
        NpcEntity selected = selected(flag, player);
        if (selected == null)
        {
            say(player, "message.ancientempires.signal.out_of_reach");
            return InteractionResultHolder.consume(flag);
        }
        NpcChain chain = selected.getChain();
        if (player.isShiftKeyDown())
        {
            NpcFormation next = chain.formation().next();
            chain.setFormation(next);
            say(player, "message.ancientempires.signal.formation", selected.getDisplayName(), Component.translatable("formation.ancientempires." + next.id()));
        }
        else if (chain.leaderId() != null)
        {
            // Orders come from the top: a unit under another leader follows that leader.
            say(player, "message.ancientempires.signal.not_top", selected.getDisplayName());
        }
        else
        {
            NpcOrder next = chain.order() == NpcOrder.FOLLOW ? NpcOrder.FREE : NpcOrder.FOLLOW;
            chain.setOrder(next);
            say(player, "message.ancientempires.signal.order", selected.getDisplayName(), Component.translatable("order.ancientempires." + next.id()));
        }
        return InteractionResultHolder.consume(flag);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        if (stack.hasTag() && stack.getTag().contains(LEADER_NAME_KEY))
        {
            Component name = Component.Serializer.fromJson(stack.getTag().getString(LEADER_NAME_KEY));
            if (name != null)
            {
                tooltip.add(Component.translatable("item.ancientempires.signal_flag.selected", name).withStyle(ChatFormatting.GOLD));
            }
        }
        tooltip.add(Component.translatable("item.ancientempires.signal_flag.help").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    private static NpcEntity selected(ItemStack flag, Player player)
    {
        if (!flag.hasTag() || !flag.getTag().hasUUID(LEADER_KEY) || !(player.level() instanceof ServerLevel level))
        {
            return null;
        }
        UUID id = flag.getTag().getUUID(LEADER_KEY);
        return level.getEntity(id) instanceof NpcEntity npc && npc.isAlive() ? npc : null;
    }

    private static Component count(NpcEntity leader)
    {
        NpcChain chain = leader.getChain();
        int officers = NpcChain.officerCap(leader.getRank());
        return officers == 0 ? Component.translatable("message.ancientempires.signal.count", chain.soldierCount(), AncientEmpiresConfig.chainSquadSize)
                : Component.translatable("message.ancientempires.signal.count_officers", chain.soldierCount(),
                        AncientEmpiresConfig.chainSquadSize, chain.officerCount(),
                        officers == Integer.MAX_VALUE ? "-" : Integer.toString(officers));
    }

    private static void say(Player player, String key, Object... args)
    {
        player.displayClientMessage(Component.translatable(key, args), true);
    }
}
