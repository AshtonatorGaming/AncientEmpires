package com.ancientempires.client;

import com.ancientempires.entity.npc.FormationLayout;
import com.ancientempires.entity.npc.NpcChain;
import com.ancientempires.entity.npc.NpcFormation;
import com.ancientempires.entity.npc.NpcGlow;
import com.ancientempires.entity.npc.NpcRank;
import com.ancientempires.network.ArmyPacket;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Which of the player's numbered units the battle orders go to (1 to 9; none
 * picked means all), remembered between uses, and what the client knows of
 * them from the last army list: the units for the wheel's bar, their shapes
 * for placement ghosts, and the glow that shows who will move.
 */
@OnlyIn(Dist.CLIENT)
public final class ArmyPicks
{
    /** The units picked last time; empty for all. */
    private static final Set<Integer> SELECTED = new TreeSet<>();
    /** Whether picked units glow now (the wheel is open, or placing). */
    private static boolean glowing;
    /** Soldiers glowing instead of the picked units (the War Table's hover or Locate), and until when (-1: until cleared). */
    @Nullable
    private static Set<UUID> only;
    private static long onlyUntil = -1L;

    private ArmyPicks()
    {
    }

    public static Set<Integer> selected()
    {
        return SELECTED;
    }

    public static boolean picked(int number)
    {
        return SELECTED.isEmpty() || SELECTED.contains(number);
    }

    /** Picks or drops one unit; 0 picks all. */
    public static void toggle(int number)
    {
        if (number == 0)
        {
            SELECTED.clear();
        }
        else if (!SELECTED.remove(number))
        {
            SELECTED.add(number);
        }
        refreshGlow();
    }

    /** The player's numbered units with soldiers, by number: each one's leader. */
    public static TreeMap<Integer, ArmyPacket.Entry> units()
    {
        TreeMap<Integer, ArmyPacket.Entry> units = new TreeMap<>();
        for (ArmyPacket.Entry entry : ClientArmy.latest())
        {
            if (entry.unitNumber() > 0 && entry.members() > 0)
            {
                units.putIfAbsent(entry.unitNumber(), entry);
            }
        }
        return units;
    }

    /**
     * The picked units' leaders, by number, each commander followed by its
     * command (its cohorts, and theirs), as the server expands a group order.
     */
    public static List<ArmyPacket.Entry> pickedUnits()
    {
        Map<UUID, List<ArmyPacket.Entry>> children = children();
        Set<ArmyPacket.Entry> picked = new LinkedHashSet<>();
        for (Map.Entry<Integer, ArmyPacket.Entry> unit : units().entrySet())
        {
            if (picked(unit.getKey()))
            {
                collect(unit.getValue(), children, picked, 0);
            }
        }
        return new ArrayList<>(picked);
    }

    private static void collect(ArmyPacket.Entry leader, Map<UUID, List<ArmyPacket.Entry>> children, Set<ArmyPacket.Entry> found, int depth)
    {
        if (!found.add(leader) || depth > 4)
        {
            return;
        }
        for (ArmyPacket.Entry member : children.getOrDefault(leader.id(), List.of()))
        {
            if (member.rank() != NpcRank.SOLDIER.ordinal())
            {
                collect(member, children, found, depth + 1);
            }
        }
    }

    /** Whether this leader commands cohorts (officers under it): it stands behind them in a group order. */
    public static boolean commands(ArmyPacket.Entry leader)
    {
        for (ArmyPacket.Entry member : children().getOrDefault(leader.id(), List.of()))
        {
            if (member.rank() != NpcRank.SOLDIER.ordinal())
            {
                return true;
            }
        }
        return false;
    }

    /** Everyone directly under each leader in the last army list. */
    private static Map<UUID, List<ArmyPacket.Entry>> children()
    {
        Map<UUID, List<ArmyPacket.Entry>> children = new HashMap<>();
        for (ArmyPacket.Entry entry : ClientArmy.latest())
        {
            if (entry.leader() != null)
            {
                children.computeIfAbsent(entry.leader(), id -> new ArrayList<>()).add(entry);
            }
        }
        return children;
    }

    /** A picked unit as placement lays it out: the server's shape, from the client's list. */
    public static FormationLayout.Unit shape(ArmyPacket.Entry leader)
    {
        int melee = 0;
        int archers = 0;
        int riders = 0;
        for (ArmyPacket.Entry member : children().getOrDefault(leader.id(), List.of()))
        {
            if (member.rank() != NpcRank.SOLDIER.ordinal())
            {
                // Officers stand with their own cohorts.
                continue;
            }
            if (member.mounted())
            {
                riders++;
            }
            else if (member.archer())
            {
                archers++;
            }
            else
            {
                melee++;
            }
        }
        NpcFormation formation = NpcFormation.values()[Math.min(leader.formation(), NpcFormation.values().length - 1)];
        return new FormationLayout.Unit(formation, melee, archers, riders, 0, NpcChain.settings(leader.width(), leader.spread()));
    }

    /** Only these soldiers glow (the War Table's hover), until cleared with none. */
    public static void glowOnly(Set<UUID> soldiers)
    {
        only = soldiers.isEmpty() ? null : Set.copyOf(soldiers);
        onlyUntil = -1L;
        refreshGlow();
    }

    /** These soldiers glow for {@code ticks} (the War Table's Locate). */
    public static void glowFor(Set<UUID> soldiers, int ticks)
    {
        Minecraft minecraft = Minecraft.getInstance();
        only = Set.copyOf(soldiers);
        onlyUntil = minecraft.level == null ? -1L : minecraft.level.getGameTime() + ticks;
        refreshGlow();
    }

    /** Client tick: a Locate glow runs out, and the glow is matched to who has come in sight, once a second. */
    static void tick()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
        {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (only != null && onlyUntil >= 0L && now >= onlyUntil)
        {
            only = null;
            onlyUntil = -1L;
            refreshGlow();
        }
        else if ((only != null || glowing) && now % 20L == 0L)
        {
            refreshGlow();
        }
    }

    /** Starts or stops the picked units glowing. */
    public static void setGlowing(boolean on)
    {
        glowing = on;
        refreshGlow();
    }

    /** Works the glow out again: everyone in or under a picked unit, found among the entities the client has. */
    public static void refreshGlow()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (!glowing && only == null || minecraft.level == null)
        {
            NpcGlow.clear();
            return;
        }
        Map<UUID, List<ArmyPacket.Entry>> children = children();
        Set<UUID> wanted = new HashSet<>();
        if (only != null)
        {
            wanted.addAll(only);
        }
        List<UUID> open = new ArrayList<>();
        if (only == null)
        {
            for (ArmyPacket.Entry leader : pickedUnits())
            {
                open.add(leader.id());
            }
        }
        while (!open.isEmpty())
        {
            UUID id = open.remove(open.size() - 1);
            if (wanted.add(id))
            {
                for (ArmyPacket.Entry child : children.getOrDefault(id, List.of()))
                {
                    open.add(child.id());
                }
            }
        }
        IntSet ids = new IntOpenHashSet();
        for (Entity entity : minecraft.level.entitiesForRendering())
        {
            if (wanted.contains(entity.getUUID()))
            {
                ids.add(entity.getId());
            }
        }
        NpcGlow.set(ids);
    }
}
