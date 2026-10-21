package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs
{
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AncientEmpires.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ancientempires"))
                    .icon(() -> new ItemStack(ModItems.OWNED_EGGS.get(0).get()))
                    .displayItems((parameters, output) ->
                    {
                        for (RegistryObject<Item> egg : ModItems.SPAWN_EGGS)
                        {
                            output.accept(egg.get());
                        }
                    })
                    .build());

    private ModCreativeTabs()
    {
    }
}
