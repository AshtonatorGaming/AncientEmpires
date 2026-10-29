package com.ancientempires.registry;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.decor.AwDecor;
import com.ancientempires.block.decor.CoffinBlock;
import com.ancientempires.entity.npc.NpcFactions;
import com.ancientempires.item.FlagItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The mod's creative tab: food, coins, blocks, tools, gate spawners, a
 * Protection Flag and a Flag per faction, then every NPC scroll. The scrolls
 * also go in vanilla's spawn egg tab ({@code AncientEmpires.addCreative}).
 */
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
                        output.accept(ModItems.COIN_COPPER.get());
                        output.accept(ModItems.COIN_SILVER.get());
                        output.accept(ModItems.COIN_GOLD.get());
                        output.accept(ModItems.COIN_ANCIENT.get());
                        output.accept(ModItems.COIN_PURSE.get());
                        output.accept(ModItems.ROUTING_ORDER.get());
                        output.accept(ModItems.COIN_STACK_COPPER.get());
                        output.accept(ModItems.COIN_STACK_SILVER.get());
                        output.accept(ModItems.COIN_STACK_GOLD.get());
                        output.accept(ModItems.COIN_STACK_ANCIENT.get());
                        output.accept(ModItems.BANDAGE.get());
                        output.accept(ModItems.BANDAGE_ROLL.get());
                        output.accept(ModItems.WAR_HORN.get());
                        output.accept(ModItems.SERGEANT_INSIGNIA.get());
                        output.accept(ModItems.CAPTAIN_INSIGNIA.get());
                        output.accept(ModItems.GENERAL_INSIGNIA.get());
                        output.accept(ModItems.SIGNAL_FLAG.get());
                        output.accept(ModItems.SETTLERS_POST.get());
                        output.accept(ModItems.TOWN_HALL.get());
                        output.accept(ModItems.CROP_FARM.get());
                        output.accept(ModItems.TREE_FARM.get());
                        output.accept(ModItems.QUARRY.get());
                        output.accept(ModItems.ANIMAL_PEN.get());
                        output.accept(ModItems.HUNTING_LODGE.get());
                        output.accept(ModItems.FISHING_DOCK.get());
                        output.accept(ModItems.KITCHEN.get());
                        output.accept(ModItems.NPC_SPAWNER.get());
                        output.accept(ModItems.CAPTURE_WAND.get());
                        output.accept(ModItems.STRUCTURE_SCANNER.get());
                        for (RegistryObject<Item> gate : ModItems.GATE_SPAWNERS)
                        {
                            output.accept(gate.get());
                        }
                        List<String> factions = new ArrayList<>(NpcFactions.ids());
                        factions.sort(String::compareTo);
                        for (boolean protection : new boolean[] {true, false})
                        {
                            for (String faction : factions)
                            {
                                output.accept(FlagItem.of(ModItems.FLAG.get(), faction, protection));
                            }
                        }
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

    /** AW's decor blocks, each wood or stone variant as its own stack. */
    public static final RegistryObject<CreativeModeTab> DECOR = TABS.register("decor",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ancientempires.decor"))
                    .icon(() -> new ItemStack(ModItems.DECOR.get("chair").get()))
                    .withTabsBefore(MAIN.getKey())
                    .displayItems((parameters, output) ->
                    {
                        for (String variant : CoffinBlock.VARIANT.getPossibleValues())
                        {
                            ItemStack stack = new ItemStack(variant.equals("stone") || variant.equals("sandstone") || variant.equals("prismarine")
                                    || variant.equals("demonic") ? ModItems.STONE_COFFIN.get() : ModItems.WOODEN_COFFIN.get());
                            CompoundTag state = new CompoundTag();
                            state.putString("variant", variant);
                            stack.getOrCreateTag().put("BlockStateTag", state);
                            output.accept(stack);
                        }
                        output.accept(ModItems.STATUE.get());
                        output.accept(ModItems.SOUND_BLOCK.get());
                        for (Map.Entry<String, RegistryObject<Item>> entry : ModItems.DECOR.entrySet())
                        {
                            List<String> variants = AwDecor.specs().get(entry.getKey()).properties().get("variant");
                            if (entry.getKey().equals("altar_candle") || entry.getKey().endsWith("_cloth"))
                            {
                                // AW's altar pieces came in every dye colour.
                                for (DyeColor dye : DyeColor.values())
                                {
                                    float[] rgb = dye.getTextureDiffuseColors();
                                    ItemStack stack = new ItemStack(entry.getValue().get());
                                    CompoundTag data = new CompoundTag();
                                    data.putInt("Color", (int) (rgb[0] * 255.0F) << 16 | (int) (rgb[1] * 255.0F) << 8 | (int) (rgb[2] * 255.0F));
                                    stack.getOrCreateTag().put("BlockEntityTag", data);
                                    output.accept(stack);
                                }
                                continue;
                            }
                            if (variants == null)
                            {
                                output.accept(entry.getValue().get());
                                continue;
                            }
                            for (String variant : variants)
                            {
                                ItemStack stack = new ItemStack(entry.getValue().get());
                                CompoundTag state = new CompoundTag();
                                state.putString("variant", variant);
                                stack.getOrCreateTag().put("BlockStateTag", state);
                                output.accept(stack);
                            }
                        }
                    })
                    .build());

    private ModCreativeTabs()
    {
    }
}
