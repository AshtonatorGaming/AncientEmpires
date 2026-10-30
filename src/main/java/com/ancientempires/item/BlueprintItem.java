package com.ancientempires.item;

import com.ancientempires.block.StructureBuilderBlockEntity;
import com.ancientempires.registry.ModBlocks;
import com.ancientempires.registry.ModItems;
import com.ancientempires.structure.AeBuilds;
import com.ancientempires.structure.AeTemplates;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Blueprint (AW's Structure Builder item), drafted at a Drafting Station:
 * one survival template. Used on the ground it sets a Structure Builder there
 * (the template's build key), the structure's front toward the player, and
 * is used up.
 */
public class BlueprintItem extends Item
{
    private static final String KEY = "AeTemplate";

    public BlueprintItem(Properties properties)
    {
        super(properties.stacksTo(1));
    }

    public static ItemStack of(String template)
    {
        ItemStack stack = new ItemStack(ModItems.BLUEPRINT.get());
        stack.getOrCreateTag().putString(KEY, template);
        return stack;
    }

    public static String template(ItemStack stack)
    {
        return stack.hasTag() ? stack.getTag().getString(KEY) : "";
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos key = context.getClickedPos().relative(context.getClickedFace());
        if (player == null || !level.getBlockState(key).canBeReplaced())
        {
            return InteractionResult.PASS;
        }
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        ResourceLocation id = AeTemplates.byName(template(stack));
        if (id == null || !AeBuilds.isSurvival(id))
        {
            player.displayClientMessage(Component.translatable("item.ancientempires.blueprint.unknown"), true);
            return InteractionResult.FAIL;
        }
        level.setBlockAndUpdate(key, ModBlocks.STRUCTURE_BUILDER.get().defaultBlockState());
        if (level.getBlockEntity(key) instanceof StructureBuilderBlockEntity builder)
        {
            builder.setOwner(player.getUUID());
            builder.start(id, facing(player));
        }
        player.displayClientMessage(Component.translatable("item.ancientempires.blueprint.placed", AeTemplates.name(id)), true);
        if (!player.getAbilities().instabuild)
        {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /** The front (the template's +z side, where AW put the build key) toward the player, as {@code structure place} turns it. */
    private static Rotation facing(Player player)
    {
        return switch (player.getDirection())
        {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        String template = template(stack);
        tooltip.add(template.isEmpty() ? Component.translatable("item.ancientempires.blueprint.blank").withStyle(ChatFormatting.GRAY)
                : Component.literal(template).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.ancientempires.blueprint.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
