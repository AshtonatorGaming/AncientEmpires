package com.ancientempires.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwsTemplateTest
{
    private static final List<String> SAMPLE = List.of(
            "# minified",
            "header:",
            "version=2.10",
            "name=Sample",
            "size=4,3,3",
            "offset=1,0,2",
            ":endheader",
            "validation:",
            "type=ground",
            "preserveBlocks=false",
            ":endvalidation",
            "layer: 0",
            "3x1|4",
            ":endlayer",
            "layer: 1-2",
            "0,2|2,0",
            "2x0|4",
            ":endlayer",
            "rule:",
            "plugin=vanillaBlocks",
            "number=1",
            "data:",
            "JSON:{blockState:{blockName:\"minecraft:dirt\",properties:{variant:\"dirt\"}}}",
            ":enddata",
            ":endrule",
            "",
            "rule:",
            "plugin=vanillaBlocks",
            "number=2",
            "data:",
            "JSON:{blockState:{blockName:\"minecraft:planks\",properties:{variant:\"spruce\"}}}",
            ":enddata",
            ":endrule",
            "entity:",
            "plugin=entity",
            "number=0",
            "data:",
            "JSON:{mobID:\"minecraft:pig\",position:0L}",
            ":enddata",
            ":endentity");

    @Test
    void readsHeaderAndValidation()
    {
        AwsTemplate template = AwsTemplate.parse("sample.aws", SAMPLE);
        assertEquals("Sample", template.name());
        assertEquals(4, template.sizeX());
        assertEquals(3, template.sizeY());
        assertEquals(3, template.sizeZ());
        assertEquals(1, template.offsetX());
        assertEquals(2, template.offsetZ());
        assertEquals("ground", template.validation().get("type"));
    }

    @Test
    void expandsRowRepeatsAndRuns()
    {
        AwsTemplate template = AwsTemplate.parse("sample.aws", SAMPLE);
        for (int z = 0; z < 3; z++)
        {
            for (int x = 0; x < 4; x++)
            {
                assertEquals(1, template.ruleAt(x, 0, z), "floor " + x + "," + z);
            }
        }
        // layer 1-2: first row 0,2,2,0, then two empty rows, on both y.
        for (int y = 1; y <= 2; y++)
        {
            assertEquals(0, template.ruleAt(0, y, 0));
            assertEquals(2, template.ruleAt(1, y, 0));
            assertEquals(2, template.ruleAt(2, y, 0));
            assertEquals(0, template.ruleAt(3, y, 0));
            assertEquals(0, template.ruleAt(1, y, 1));
            assertEquals(0, template.ruleAt(1, y, 2));
        }
    }

    @Test
    void keepsRulesAndEntitiesRaw()
    {
        AwsTemplate template = AwsTemplate.parse("sample.aws", SAMPLE);
        assertEquals(2, template.rules().size());
        assertEquals("vanillaBlocks", template.rules().get(2).plugin());
        assertTrue(template.rules().get(2).data().startsWith("{blockState:"));
        assertEquals(1, template.entities().size());
        assertEquals("entity", template.entities().get(0).plugin());
    }

    @Test
    void decodesOneTwelvePackedPositions()
    {
        // Minecraft 1.12 BlockPos.toLong: x << 38 | y << 26 | z, each masked to its width.
        int[][] cases = {{0, 0, 0}, {5, 64, 7}, {-3, 12, -9}, {1000, 255, -1000}};
        for (int[] pos : cases)
        {
            long packed = ((long) pos[0] & 0x3FFFFFFL) << 38 | ((long) pos[1] & 0xFFFL) << 26 | ((long) pos[2] & 0x3FFFFFFL);
            assertArrayEquals(pos, AwsTemplate.legacyPos(packed));
        }
    }
}
