package com.ancientempires.item;

import com.ancientempires.block.WorksiteBlockEntity;
import com.ancientempires.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ancient Warfare's worksite size upgrades. Bounds upgrades fit every worksite
 * with an area; quarry upgrades fit only the quarry, whose limits are larger.
 * Right-click the worksite with one to fit it. The large one replaces the
 * medium one (which comes back); a worksite that is broken drops its upgrade.
 */
public final class WorksiteUpgradeItem extends Item
{
    private final WorksiteBlockEntity.UpgradeKind kind;
    private final int tier;

    public WorksiteUpgradeItem(WorksiteBlockEntity.UpgradeKind kind, int tier, Properties properties)
    {
        super(properties);
        this.kind = kind;
        this.tier = tier;
    }

    /** Fits this upgrade to a worksite the player may manage. Server side. */
    public InteractionResult apply(Player player, WorksiteBlockEntity site, ItemStack stack)
    {
        if (site.upgradeKind() != this.kind)
        {
            player.displayClientMessage(Component.translatable(site.upgradeKind() == WorksiteBlockEntity.UpgradeKind.NONE
                    ? "message.ancientempires.upgrade.no_area" : "message.ancientempires.upgrade.wrong_kind"), true);
            return InteractionResult.CONSUME;
        }
        if (site.sizeTier() >= this.tier)
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.upgrade.already"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack replaced = stackFor(this.kind, site.sizeTier());
        site.setSizeTier(this.tier);
        if (!player.getAbilities().instabuild)
        {
            stack.shrink(1);
            if (!replaced.isEmpty() && !player.getInventory().add(replaced))
            {
                player.drop(replaced, false);
            }
        }
        player.level().playSound(null, site.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.1F);
        player.displayClientMessage(Component.translatable("message.ancientempires.upgrade.applied", site.maxSize(), site.maxSize()), true);
        return InteractionResult.CONSUME;
    }

    /** The upgrade item for a kind and tier, or empty for tier 0 or no such item. */
    public static ItemStack stackFor(WorksiteBlockEntity.UpgradeKind kind, int tier)
    {
        Item item = switch (kind)
        {
            case BOUNDS -> tier == 1 ? ModItems.UPGRADE_SIZE_MEDIUM.get() : tier == 2 ? ModItems.UPGRADE_SIZE_LARGE.get() : null;
            case QUARRY -> tier == 1 ? ModItems.UPGRADE_QUARRY_MEDIUM.get() : tier == 2 ? ModItems.UPGRADE_QUARRY_LARGE.get() : null;
            case NONE -> null;
        };
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable(this.kind == WorksiteBlockEntity.UpgradeKind.QUARRY
                ? "item.ancientempires.worksite_upgrade.quarry_tooltip" : "item.ancientempires.worksite_upgrade.bounds_tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
