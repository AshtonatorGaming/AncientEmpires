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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    /** The picked units' leaders, by number. */
    public static List<ArmyPacket.Entry> pickedUnits()
    {
        List<ArmyPacket.Entry> picked = new ArrayList<>();
        for (Map.Entry<Integer, ArmyPacket.Entry> unit : units().entrySet())
        {
            if (picked(unit.getKey()))
            {
                picked.add(unit.getValue());
            }
        }
        return picked;
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
        int officers = 0;
        for (ArmyPacket.Entry member : children().getOrDefault(leader.id(), List.of()))
        {
            if (member.rank() != NpcRank.SOLDIER.ordinal())
            {
                officers++;
            }
            else if (member.mounted())
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
        return new FormationLayout.Unit(formation, melee, archers, riders, officers, NpcChain.settings(leader.width(), leader.spread()));
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
        if (!glowing || minecraft.level == null)
        {
            NpcGlow.clear();
            return;
        }
        Map<UUID, List<ArmyPacket.Entry>> children = children();
        Set<UUID> wanted = new HashSet<>();
        List<UUID> open = new ArrayList<>();
        for (ArmyPacket.Entry leader : pickedUnits())
        {
            open.add(leader.id());
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
