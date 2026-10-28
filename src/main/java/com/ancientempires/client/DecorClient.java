package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.decor.AwDecor;
import com.ancientempires.block.decor.AwDecorBlockEntity;
import com.ancientempires.registry.ModBlocks;
import com.ancientempires.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;

/**
 * The client side of AW's decor items and colours: each wood or stone variant
 * shows its own icon ({@code ancientempires:variant}, the variant's place in
 * the block's list, read from the stack's {@code BlockStateTag}), and altar
 * candles and cloths take their colour (AW's dye or custom colour) from their
 * block entity, or the stack's.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DecorClient
{
    public static final List<String> COLORED = List.of("altar_candle", "altar_long_cloth", "altar_short_cloth");
    private static final int WHITE = 0xFFFFFF;

    private DecorClient()
    {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> ModItems.DECOR.forEach((name, item) ->
        {
            List<String> variants = AwDecor.specs().get(name).properties().get("variant");
            if (variants != null)
            {
                ItemProperties.register(item.get(), ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "variant"),
                        (stack, level, entity, seed) -> Math.max(0, variants.indexOf(stateTag(stack).getString("variant"))));
            }
        }));
    }

    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event)
    {
        for (String name : COLORED)
        {
            event.register((state, level, pos, tint) -> level != null && pos != null && level.getBlockEntity(pos) instanceof AwDecorBlockEntity decor
                    && decor.color() >= 0 ? decor.color() : WHITE, ModBlocks.DECOR.get(name).get());
        }
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event)
    {
        for (String name : COLORED)
        {
            event.register((stack, tint) ->
            {
                CompoundTag data = stack.getTagElement("BlockEntityTag");
                return data != null && data.contains("Color") && data.getInt("Color") >= 0 ? data.getInt("Color") : WHITE;
            }, ModItems.DECOR.get(name).get());
        }
    }

    private static CompoundTag stateTag(ItemStack stack)
    {
        CompoundTag tag = stack.getTagElement("BlockStateTag");
        return tag == null ? new CompoundTag() : tag;
    }
}
