package com.ancientempires.structure;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AeTemplateTest
{
    private static CompoundTag state(String name)
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    @Test
    void roundTripsEveryCell()
    {
        // Uneven sizes so edge tiles are partial; enough states for more than one bit width.
        int sizeX = 37;
        int sizeY = 9;
        int sizeZ = 21;
        AeTemplate.Builder builder = new AeTemplate.Builder(sizeX, sizeY, sizeZ, 3465);
        int[] indices = new int[40];
        for (int i = 0; i < indices.length; i++)
        {
            indices[i] = builder.intern(state("minecraft:block_" + i));
        }
        Random random = new Random(42L);
        int[][][] expected = new int[sizeX][sizeY][sizeZ];
        for (int x = 0; x < sizeX; x++)
        {
            for (int y = 0; y < sizeY; y++)
            {
                for (int z = 0; z < sizeZ; z++)
                {
                    // Leave the far corner tile all void, to test empty tiles.
                    int state = x >= 32 && z >= 16 ? 0 : (random.nextInt(4) == 0 ? 0 : indices[random.nextInt(indices.length)]);
                    expected[x][y][z] = state;
                    builder.set(x, y, z, state);
                }
            }
        }
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        builder.setBlockEntity(3, 4, 5, chest);
        builder.addEntity(1.5D, 2.0D, 3.25D, "entity", state("minecraft:pig"));

        AeTemplate loaded = AeTemplate.load(builder.build().save());
        assertEquals(sizeX, loaded.sizeX());
        assertEquals(41, loaded.palette().size());
        assertEquals(AeTemplate.VOID, loaded.palette().get(0).getString("Name"));
        for (int x = 0; x < sizeX; x++)
        {
            for (int y = 0; y < sizeY; y++)
            {
                for (int z = 0; z < sizeZ; z++)
                {
                    assertEquals(expected[x][y][z], loaded.stateAt(x, y, z), x + "," + y + "," + z);
                }
            }
        }
        assertEquals("minecraft:chest", loaded.blockEntityAt(3, 4, 5).getString("id"));
        assertEquals(1, loaded.entities().size());

        int[] seen = new int[1];
        loaded.forEachInTile(2, 1, (x, y, z, state) ->
        {
            assertEquals(expected[x][y][z], state);
            seen[0]++;
        });
        assertEquals(0, seen[0], "the far corner tile is all void");
    }
}
