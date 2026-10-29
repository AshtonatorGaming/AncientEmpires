package com.ancientempires.item;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcChain;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcFormation;
import com.ancientempires.entity.npc.NpcOrder;
import com.ancientempires.entity.npc.NpcRank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Signal Flag, the field tool of the chain of command
 * (docs/chain-of-command.md). Use it on one of your sergeants, captains, or
 * generals to take up their unit; then on your soldiers to put them under that
 * leader, or take them out again. With a unit taken up:
 * <ul>
 * <li>on a block: Hold there, facing the way you look; on the same spot
 * again: Defend it (and again: Hold); sneaking: Fall back to it;</li>
 * <li>on any other creature: Attack it;</li>
 * <li>in the air: Follow you (a unit under another leader: rejoin it), again
 * Free; sneaking: the next formation (line, column, loose).</li>
 * </ul>
 * Using it on the selected leader again puts the flag down.
 */
public class SignalFlagItem extends Item
{
    private static final String LEADER_KEY = "Leader";
    private static final String LEADER_NAME_KEY = "LeaderName";
    /** Blocks within which a second Hold on the spot means Defend. */
    private static final double SAME_SPOT = 2.5D;

    public SignalFlagItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack ignored, Player player, LivingEntity target, InteractionHand hand)
    {
        if (target == player)
        {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        // The held stack itself (in creative the one passed in is a copy).
        ItemStack flag = player.getItemInHand(hand);
        NpcEntity selected = selected(flag, player);
        boolean own = target instanceof NpcEntity npc && npc.isOwnedNpc() && player.getUUID().equals(npc.getOwner());
        if (!own)
        {
            if (selected == null)
            {
                say(player, "message.ancientempires.signal.select_leader");
                return InteractionResult.CONSUME;
            }
            selected.getChain().setOrder(NpcOrder.ATTACK, null, Float.NaN, target.getUUID());
            say(player, "message.ancientempires.signal.attack", selected.getDisplayName(), target.getDisplayName());
            return InteractionResult.CONSUME;
        }
        NpcEntity npc = (NpcEntity) target;
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
        select(flag, npc);
        NpcChain chain = npc.getChain();
        say(player, "message.ancientempires.signal.selected", npc.getDisplayName(), count(npc), orderName(chain),
                Component.translatable("formation.ancientempires." + chain.formation().id()));
        return InteractionResult.CONSUME;
    }

    /** Takes up {@code leader}'s unit on this flag (also from the command screen). */
    public static void select(ItemStack flag, NpcEntity leader)
    {
        CompoundTag tag = flag.getOrCreateTag();
        tag.putUUID(LEADER_KEY, leader.getUUID());
        tag.putString(LEADER_NAME_KEY, Component.Serializer.toJson(leader.getDisplayName()));
    }

    /** On a block: Hold there (again on the spot: Defend), or sneaking, Fall back to it. */
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        if (player == null)
        {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        NpcEntity selected = this.selectedOrSay(player, context.getItemInHand());
        if (selected == null)
        {
            return InteractionResult.CONSUME;
        }
        BlockPos spot = context.getClickedPos().relative(context.getClickedFace());
        float facing = player.getYRot();
        NpcChain chain = selected.getChain();
        if (player.isShiftKeyDown())
        {
            chain.setOrder(NpcOrder.FALL_BACK, spot, facing, null);
            say(player, "message.ancientempires.signal.fall_back", selected.getDisplayName());
            return InteractionResult.CONSUME;
        }
        NpcOrder now = chain.holdsOrder() ? chain.effectiveOrder() : null;
        boolean sameSpot = chain.holdsOrder() && chain.orderPos() != null && chain.orderPos().distSqr(spot) <= SAME_SPOT * SAME_SPOT;
        NpcOrder next = sameSpot && now == NpcOrder.HOLD ? NpcOrder.DEFEND : NpcOrder.HOLD;
        chain.setOrder(next, sameSpot ? chain.orderPos() : spot, facing, null);
        say(player, next == NpcOrder.DEFEND ? "message.ancientempires.signal.defend" : "message.ancientempires.signal.hold", selected.getDisplayName());
        return InteractionResult.CONSUME;
    }

    /** In the air: Follow (or rejoin), then Free; sneaking, the next formation. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack flag = player.getItemInHand(hand);
        if (level.isClientSide)
        {
            return InteractionResultHolder.success(flag);
        }
        NpcEntity selected = this.selectedOrSay(player, flag);
        if (selected == null)
        {
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
            // A unit under another leader rejoins it, taking its order again.
            chain.setOrder(NpcOrder.FOLLOW, null, Float.NaN, null);
            say(player, "message.ancientempires.signal.rejoin", selected.getDisplayName());
        }
        else
        {
            NpcOrder next = chain.order() == NpcOrder.FOLLOW ? NpcOrder.FREE : NpcOrder.FOLLOW;
            chain.setOrder(next, null, Float.NaN, null);
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
        tooltip.add(Component.translatable("item.ancientempires.signal_flag.orders").withStyle(ChatFormatting.GRAY));
    }

    /** The unit taken up, or null after telling the player why there is none. */
    @Nullable
    private NpcEntity selectedOrSay(Player player, ItemStack flag)
    {
        if (!flag.hasTag() || !flag.getTag().hasUUID(LEADER_KEY))
        {
            say(player, "message.ancientempires.signal.select_leader");
            return null;
        }
        NpcEntity selected = selected(flag, player);
        if (selected == null)
        {
            say(player, "message.ancientempires.signal.out_of_reach");
        }
        return selected;
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

    private static Component orderName(NpcChain chain)
    {
        return Component.translatable("order.ancientempires." + chain.effectiveOrder().id());
    }

    private static Component count(NpcEntity leader)
    {
        NpcChain chain = leader.getChain();
        int officers = NpcChain.officerCap(leader.getRank());
        return officers == 0 ? Component.translatable("message.ancientempires.signal.count", chain.soldierCount(), NpcChain.soldierCap(leader.getRank()))
                : Component.translatable("message.ancientempires.signal.count_officers", chain.soldierCount(),
                        NpcChain.soldierCap(leader.getRank()), chain.officerCount(),
                        officers == Integer.MAX_VALUE ? "-" : Integer.toString(officers));
    }

    private static void say(Player player, String key, Object... args)
    {
        player.displayClientMessage(Component.translatable(key, args), true);
    }
}
