package com.ancientempires.item;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcRank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A rank's insignia: used on one of the player's soldiers, it promotes it one
 * rank, to this one, once it has the level ({@code command.promoteLevels}),
 * and is used up. The insignia is only the token; the rank and level do the
 * work (command aura, succession, later squads). Admins in creative may
 * promote any NPC to any rank, skipping the level.
 */
public final class InsigniaItem extends Item
{
    private final NpcRank rank;

    public InsigniaItem(NpcRank rank, Properties properties)
    {
        super(properties);
        this.rank = rank;
    }

    public NpcRank rank()
    {
        return this.rank;
    }

    /** Called from {@link NpcEntity#mobInteract} before the inventory opens. */
    public InteractionResult promote(NpcEntity npc, Player player, ItemStack stack)
    {
        if (npc.level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        boolean admin = NpcEntity.mayToggleCreative(player);
        String refusal = admin ? null : this.refusal(npc, player);
        if (refusal != null)
        {
            player.displayClientMessage(Component.translatable(refusal, npc.getDisplayName(),
                    this.rank.displayName(npc.getFaction()), this.requiredLevel(),
                    this.previous().displayName(npc.getFaction())).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        npc.setRank(this.rank);
        if (!player.getAbilities().instabuild)
        {
            stack.shrink(1);
        }
        if (npc.level() instanceof ServerLevel server)
        {
            server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, npc.getX(), npc.getY() + npc.getBbHeight() * 0.6D, npc.getZ(),
                    20, 0.4D, 0.6D, 0.4D, 0.2D);
        }
        npc.playSound(SoundEvents.ARMOR_EQUIP_GOLD, 1.0F, 1.0F);
        npc.playSound(SoundEvents.PLAYER_LEVELUP, 0.6F, 0.8F);
        player.displayClientMessage(Component.translatable("message.ancientempires.npc.promoted", npc.getDisplayName(),
                npc.getRankName()).withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.CONSUME;
    }

    /** Why a player may not promote this NPC with this insignia, as a message key, or null. */
    @Nullable
    private String refusal(NpcEntity npc, Player player)
    {
        if (!npc.isOwnedNpc() || !player.getUUID().equals(npc.getOwner()))
        {
            return "message.ancientempires.promote.not_yours";
        }
        if (!npc.getNpcPath().combat())
        {
            return "message.ancientempires.promote.not_soldier";
        }
        if (npc.getRank().ordinal() >= this.rank.ordinal())
        {
            return "message.ancientempires.promote.already";
        }
        if (npc.getRank() != this.previous())
        {
            return "message.ancientempires.promote.one_step";
        }
        if (npc.getLevel() < this.requiredLevel())
        {
            return "message.ancientempires.promote.level";
        }
        return null;
    }

    private NpcRank previous()
    {
        return NpcRank.byId(this.rank.ordinal() - 1);
    }

    private int requiredLevel()
    {
        List<? extends Integer> levels = AncientEmpiresConfig.commandPromoteLevels;
        return levels.isEmpty() ? 1 : levels.get(Math.min(this.rank.ordinal(), levels.size() - 1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag)
    {
        lines.add(Component.translatable("item.ancientempires.insignia.tooltip", this.rank.displayName(""),
                this.previous().displayName(""), this.requiredLevel()).withStyle(ChatFormatting.GRAY));
    }
}
