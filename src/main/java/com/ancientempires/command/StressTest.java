package com.ancientempires.command;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcFactions;
import com.ancientempires.entity.npc.NpcRoster;
import com.ancientempires.item.NpcSpawnEggItem;
import com.ancientempires.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /ancientempires stress}: two armies of faction NPCs facing each other,
 * to measure the mod under load (with Spark, whose profile shows this mod's
 * named sections). Soldiers, archers, and leaders of each faction, in rows,
 * 24 blocks apart. Each is tagged so {@code stress clear} removes them all.
 */
final class StressTest
{
    static final String TAG = "AeStress";
    static final int MAX_PER_SIDE = 500;
    private static final double SIDE_GAP = 12.0D;
    private static final int ROW_WIDTH = 10;
    private static final Set<String> RANKS = Set.of("soldier", "soldier.elite", "archer", "archer.elite", "leader", "leader.elite");
    /** How many each faction put in the field in the last stress spawns, for the report. */
    private static final Map<String, Integer> FIELDED = new LinkedHashMap<>();

    private StressTest()
    {
    }

    /** Both armies, centred on {@code centre}, lined up along {@code facing}. Returns the number spawned. */
    static int spawn(ServerLevel level, Vec3 centre, Vec3 facing, int perSide, String factionA, String factionB)
    {
        Vec3 across = new Vec3(-facing.z, 0.0D, facing.x);
        int a = army(level, centre.subtract(facing.scale(SIDE_GAP)), facing, across, perSide, factionA);
        int b = army(level, centre.add(facing.scale(SIDE_GAP)), facing.scale(-1.0D), across, perSide, factionB);
        FIELDED.merge(factionA, a, Integer::sum);
        FIELDED.merge(factionB, b, Integer::sum);
        return a + b;
    }

    private static int army(ServerLevel level, Vec3 front, Vec3 facing, Vec3 across, int count, String faction)
    {
        List<Item> scrolls = new ArrayList<>();
        for (NpcRoster.Entry entry : NpcRoster.entries())
        {
            Item egg = entry.owned() || !entry.faction().equals(faction) || !RANKS.contains(entry.subtype()) ? null : ModItems.eggFor(entry.id());
            if (egg != null)
            {
                scrolls.add(egg);
            }
        }
        if (scrolls.isEmpty())
        {
            return 0;
        }
        int spawned = 0;
        for (int index = 0; index < count; index++)
        {
            int row = index / ROW_WIDTH;
            int column = index % ROW_WIDTH - ROW_WIDTH / 2;
            Vec3 at = front.subtract(facing.scale(row * 1.5D)).add(across.scale(column * 1.5D));
            int x = (int) Math.floor(at.x);
            int z = (int) Math.floor(at.z);
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            NpcEntity npc = NpcSpawnEggItem.spawnFrom(level, new ItemStack(scrolls.get(index % scrolls.size())), pos, null);
            if (npc != null)
            {
                npc.getPersistentData().putBoolean(TAG, true);
                spawned++;
            }
        }
        return spawned;
    }

    static boolean fight(String factionA, String factionB)
    {
        return NpcFactions.hostile(factionA, factionB) || NpcFactions.hostile(factionB, factionA);
    }

    /** Removes every stress-test NPC in loaded chunks. */
    static int clear(ServerLevel level)
    {
        List<Entity> tagged = new ArrayList<>();
        for (Entity entity : level.getAllEntities())
        {
            if (entity instanceof NpcEntity npc && npc.getPersistentData().getBoolean(TAG))
            {
                tagged.add(entity);
            }
        }
        tagged.forEach(Entity::discard);
        FIELDED.clear();
        return tagged.size();
    }

    /**
     * Stress-test NPCs alive and the server's average tick time, then a line
     * per side: alive of fielded, how many have broken (routing, shattered,
     * surrendered), average level and health left, for balance.
     */
    static List<Component> report(ServerLevel level)
    {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, double[]> sums = new LinkedHashMap<>();
        int alive = 0;
        for (Entity entity : level.getAllEntities())
        {
            if (entity instanceof NpcEntity npc && npc.isAlive() && npc.getPersistentData().getBoolean(TAG))
            {
                alive++;
                int[] count = counts.computeIfAbsent(npc.getFaction(), key -> new int[2]);
                count[0]++;
                count[1] += npc.getMorale().isBroken() ? 1 : 0;
                double[] sum = sums.computeIfAbsent(npc.getFaction(), key -> new double[2]);
                sum[0] += npc.getLevel();
                sum[1] += npc.getHealth() / npc.getMaxHealth();
            }
        }
        double mspt = level.getServer().getAverageTickTime();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("commands.ancientempires.stress.report", alive, String.format("%.2f", mspt)));
        Set<String> sides = new LinkedHashSet<>(FIELDED.keySet());
        sides.addAll(counts.keySet());
        for (String side : sides)
        {
            int[] count = counts.getOrDefault(side, new int[2]);
            double[] sum = sums.getOrDefault(side, new double[2]);
            int n = Math.max(1, count[0]);
            lines.add(Component.translatable("commands.ancientempires.stress.side", side, count[0], FIELDED.getOrDefault(side, count[0]),
                    count[1], String.format("%.1f", sum[0] / n), Math.round(sum[1] / n * 100.0D)));
        }
        return lines;
    }
}
