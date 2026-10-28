package com.ancientempires.structure;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConverterRulesTest
{
    private static CompoundTag state(String name, String... properties)
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        CompoundTag props = new CompoundTag();
        for (int i = 0; i < properties.length; i += 2)
        {
            props.putString(properties[i], properties[i + 1]);
        }
        tag.put("Properties", props);
        return tag;
    }

    private static String type(AeTemplate.Builder builder, int x, int z)
    {
        return builder.paletteEntry(builder.stateAt(x, 0, z)).getCompound("Properties").getString("type");
    }

    @Test
    void pairsSideBySideChestsAsVanillaReadsThem()
    {
        AeTemplate.Builder builder = new AeTemplate.Builder(4, 1, 3, 3465);
        // Facing north, the clockwise side is east: x 0 is the left half, x 1 the right.
        int north = builder.intern(state("minecraft:chest", "facing", "north", "type", "single"));
        builder.set(0, 0, 0, north);
        builder.set(1, 0, 0, north);
        // A lone chest and a chest facing another way stay single.
        builder.set(3, 0, 0, north);
        builder.set(0, 0, 2, north);
        builder.set(1, 0, 2, builder.intern(state("minecraft:chest", "facing", "south", "type", "single")));
        AwsConverter.pairChests(builder, 4, 1, 3);
        assertEquals("left", type(builder, 0, 0));
        assertEquals("right", type(builder, 1, 0));
        assertEquals("single", type(builder, 3, 0));
        assertEquals("single", type(builder, 0, 2));
        assertEquals("single", type(builder, 1, 2));
    }

    @Test
    void layLeavesPersistentAndDoorsWhole()
    {
        CompoundTag leaves = state("minecraft:birch_leaves", "check_decay", "false", "decayable", "true");
        LegacyFixer.overlay(leaves, new CompoundTag());
        assertEquals("true", leaves.getCompound("Properties").getString("persistent"));
        assertEquals(2, leaves.getCompound("Properties").size());

        CompoundTag door = state("minecraft:oak_door", "facing", "east", "half", "lower", "hinge", "right", "open", "false", "powered", "false");
        CompoundTag saved = new CompoundTag();
        saved.putString("hinge", "left");
        saved.putString("facing", "east");
        LegacyFixer.overlay(door, saved);
        assertEquals("left", door.getCompound("Properties").getString("hinge"));

        // Anything else keeps the fixer's answer, as a lever's 1.12 facing would be wrong now.
        CompoundTag lever = state("minecraft:lever", "face", "ceiling", "facing", "east");
        CompoundTag old = new CompoundTag();
        old.putString("facing", "down_x");
        LegacyFixer.overlay(lever, old);
        assertEquals("east", lever.getCompound("Properties").getString("facing"));
    }
}
