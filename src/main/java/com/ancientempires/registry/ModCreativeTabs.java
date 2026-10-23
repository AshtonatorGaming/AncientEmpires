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
                        output.accept(ModItems.FOOD_BUNDLE.get());
                        output.accept(ModItems.BANDAGE.get());
                        output.accept(ModItems.SETTLERS_POST.get());
                        output.accept(ModItems.TOWN_HALL.get());
                        output.accept(ModItems.CROP_FARM.get());
                        output.accept(ModItems.TREE_FARM.get());
                        output.accept(ModItems.QUARRY.get());
                        output.accept(ModItems.ANIMAL_PEN.get());
                        output.accept(ModItems.HUNTING_LODGE.get());
                        output.accept(ModItems.FISHING_DOCK.get());
                        output.accept(ModItems.KITCHEN.get());
                        output.accept(ModItems.UPGRADE_SIZE_MEDIUM.get());
                        output.accept(ModItems.UPGRADE_SIZE_LARGE.get());
                        output.accept(ModItems.UPGRADE_QUARRY_MEDIUM.get());
                        output.accept(ModItems.UPGRADE_QUARRY_LARGE.get());
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
