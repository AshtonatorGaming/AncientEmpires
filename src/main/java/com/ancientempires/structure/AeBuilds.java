package com.ancientempires.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Survival building (AW's drafting station and structure builder): which
 * templates a player may build (AW's {@code survival} flag), where the build key
 * sits in each, and what a layer of one costs in items.
 */
public final class AeBuilds
{
    private AeBuilds()
    {
    }

    /** One buildable template, as the drafting station lists it. */
    public record Entry(String name, String category, int sizeX, int sizeY, int sizeZ)
    {
    }

    /** Every template AW marked for survival building, by category then name. */
    public static List<Entry> survival()
    {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonObject> template : AeTemplates.all().entrySet())
        {
            JsonObject sidecar = template.getValue();
            JsonObject validation = sidecar.has("validation") ? sidecar.getAsJsonObject("validation") : null;
            if (validation == null || !validation.has("survival") || !validation.get("survival").getAsString().equals("true"))
            {
                continue;
            }
            JsonArray size = sidecar.getAsJsonArray("size");
            entries.add(new Entry(AeTemplates.name(template.getKey()), AeTemplates.category(template.getKey()),
                    size.get(0).getAsInt(), size.get(1).getAsInt(), size.get(2).getAsInt()));
        }
        entries.sort((a, b) -> a.category().equals(b.category()) ? a.name().compareTo(b.name()) : a.category().compareTo(b.category()));
        return entries;
    }

    public static boolean isSurvival(ResourceLocation id)
    {
        JsonObject sidecar = AeTemplates.sidecar(id);
        return sidecar != null && sidecar.has("validation") && sidecar.getAsJsonObject("validation").has("survival")
                && sidecar.getAsJsonObject("validation").get("survival").getAsString().equals("true");
    }

    /** The build key's cell in the template (AW's offset), from the sidecar. */
    public static int[] offset(ResourceLocation id)
    {
        int[] offset = new int[3];
        JsonObject sidecar = AeTemplates.sidecar(id);
        if (sidecar != null && sidecar.has("offset"))
        {
            for (int i = 0; i < 3; i++)
            {
                offset[i] = sidecar.getAsJsonArray("offset").get(i).getAsInt();
            }
        }
        return offset;
    }

    /**
     * The items layer {@code y} of {@code template} takes to build: one of each
     * block's item, a double slab two; the other half of a door or bed, liquids,
     * air, and blocks with no item are free.
     */
    public static Map<Item, Integer> layerCost(AeTemplate template, int y)
    {
        BlockState[] states = new BlockState[template.palette().size()];
        for (int i = 1; i < states.length; i++)
        {
            states[i] = AeRemap.block(template.palette().get(i)).state();
        }
        Map<Item, Integer> cost = new LinkedHashMap<>();
        for (int x = 0; x < template.sizeX(); x++)
        {
            for (int z = 0; z < template.sizeZ(); z++)
            {
                int index = template.stateAt(x, y, z);
                if (index <= 0)
                {
                    continue;
                }
                BlockState state = states[index];
                Item item = state.getBlock().asItem();
                if (state.isAir() || item == Items.AIR || state.getBlock() instanceof LiquidBlock
                        || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                        || state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD)
                {
                    continue;
                }
                int count = state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
                cost.merge(item, count, Integer::sum);
            }
        }
        return cost;
    }
}
