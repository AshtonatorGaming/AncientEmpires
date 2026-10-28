package com.ancientempires.structure;

import com.ancientempires.registry.ModLoot;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A loot entry for an item that may not exist: {@code "type": "ancientempires:item"},
 * {@code "name"} (any id, resolved when rolled through {@link AeRemap}'s item
 * tables and namespace renames), and an optional {@code "fallback"}. A vanilla
 * {@code minecraft:item} entry naming an unknown item makes the whole table fail
 * to load, which would drop every AW chest that once held a modded item; this
 * one quietly gives the fallback, or nothing.
 */
public class AeLootItem extends LootPoolSingletonContainer
{
    private final String name;
    @Nullable
    private final String fallback;

    AeLootItem(String name, @Nullable String fallback, int weight, int quality, LootItemCondition[] conditions, LootItemFunction[] functions)
    {
        super(weight, quality, conditions, functions);
        this.name = name;
        this.fallback = fallback;
    }

    @Override
    public LootPoolEntryType getType()
    {
        return ModLoot.ITEM.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> output, LootContext context)
    {
        Item item = resolve(AeRemap.itemId(this.name));
        if (item == null && this.fallback != null)
        {
            item = resolve(this.fallback);
        }
        if (item != null)
        {
            output.accept(new ItemStack(item));
        }
    }

    @Nullable
    private static Item resolve(String id)
    {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && ForgeRegistries.ITEMS.containsKey(location) ? ForgeRegistries.ITEMS.getValue(location) : null;
    }

    public static class Serializer extends LootPoolSingletonContainer.Serializer<AeLootItem>
    {
        @Override
        public void serializeCustom(JsonObject json, AeLootItem entry, JsonSerializationContext context)
        {
            super.serializeCustom(json, entry, context);
            json.addProperty("name", entry.name);
            if (entry.fallback != null)
            {
                json.addProperty("fallback", entry.fallback);
            }
        }

        @Override
        protected AeLootItem deserialize(JsonObject json, JsonDeserializationContext context, int weight, int quality,
                LootItemCondition[] conditions, LootItemFunction[] functions)
        {
            String fallback = json.has("fallback") ? GsonHelper.getAsString(json, "fallback") : null;
            return new AeLootItem(GsonHelper.getAsString(json, "name"), fallback, weight, quality, conditions, functions);
        }
    }
}
