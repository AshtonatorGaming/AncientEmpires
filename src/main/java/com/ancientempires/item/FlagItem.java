package com.ancientempires.item;

import com.ancientempires.client.FlagItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

import com.ancientempires.entity.npc.NpcFactions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * AW's faction flags, one creative-tab entry per faction and kind: the
 * faction and whether it is a protection flag ride in the stack's
 * {@code BlockEntityTag}, which placing hands to the flag.
 */
public class FlagItem extends BlockItem
{
    public FlagItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    /** The item is drawn as the flag itself ({@code client.FlagItemRenderer}). */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer)
    {
        consumer.accept(new IClientItemExtensions()
        {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer()
            {
                return FlagItemRenderer.instance();
            }
        });
    }

    /** A flag of {@code faction} (empty for a plain cloth), a protection flag or a decorative one. */
    public static ItemStack of(FlagItem item, String faction, boolean protection)
    {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString("Faction", faction);
        tag.putBoolean("Protection", protection);
        stack.getOrCreateTag().put("BlockEntityTag", tag);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack)
    {
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        String faction = tag == null ? "" : tag.getString("Faction");
        boolean protection = tag != null && tag.getBoolean("Protection");
        Component name = faction.isEmpty() ? Component.translatable("item.ancientempires.flag.plain") : NpcFactions.displayName(faction);
        return Component.translatable(protection ? "item.ancientempires.flag.protection" : "item.ancientempires.flag.named", name);
    }
}
