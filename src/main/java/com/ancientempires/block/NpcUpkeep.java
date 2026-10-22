package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Town Halls in a dimension, so an NPC can find its owner's nearest one
 * without scanning: to fetch food when its backpack is empty, and to have its
 * death recorded. Nothing here ticks; NPCs feed themselves
 * ({@code NpcUpkeepGoal}).
 */
public final class NpcUpkeep extends SavedData
{
    private static final String NAME = "ancientempires_town_halls";

    private final Set<BlockPos> halls = new HashSet<>();

    private static NpcUpkeep get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(NpcUpkeep::load, NpcUpkeep::new, NAME);
    }

    static void register(ServerLevel level, BlockPos pos)
    {
        NpcUpkeep data = get(level);
        if (data.halls.add(pos.immutable()))
        {
            data.setDirty();
        }
    }

    static void unregister(ServerLevel level, BlockPos pos)
    {
        NpcUpkeep data = get(level);
        if (data.halls.remove(pos))
        {
            data.setDirty();
        }
    }

    /** The owner's nearest town hall within {@code upkeep.range} of a spot, in a loaded chunk, or null. */
    @Nullable
    public static TownHallBlockEntity hallFor(ServerLevel level, BlockPos at, UUID owner)
    {
        double range = AncientEmpiresConfig.upkeepRange;
        TownHallBlockEntity best = null;
        double bestDistance = range * range;
        for (BlockPos pos : get(level).halls)
        {
            double distance = pos.distSqr(at);
            if (distance > bestDistance || !level.hasChunkAt(pos))
            {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof TownHallBlockEntity hall && owner.equals(hall.owner()))
            {
                bestDistance = distance;
                best = hall;
            }
        }
        return best;
    }

    /** Whether a death here would be recorded: the NPC is owned and its owner has a hall in range. */
    public static boolean recordsDeathOf(ServerLevel level, NpcEntity npc)
    {
        UUID owner = npc.getOwner();
        return owner != null && hallFor(level, npc.blockPosition(), owner) != null;
    }

    /** Records a fallen owned NPC at its owner's nearest hall, if one is in range, at the moment it dies. */
    public static void recordFallen(ServerLevel level, NpcEntity npc, ItemStack scroll, Component message)
    {
        UUID owner = npc.getOwner();
        if (owner == null)
        {
            return;
        }
        TownHallBlockEntity hall = hallFor(level, npc.blockPosition(), owner);
        if (hall != null)
        {
            hall.recordFallen(scroll, message, npc.blockPosition(), level.getGameTime());
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        long[] positions = new long[this.halls.size()];
        int index = 0;
        for (BlockPos pos : this.halls)
        {
            positions[index++] = pos.asLong();
        }
        tag.put("Halls", new LongArrayTag(positions));
        return tag;
    }

    private static NpcUpkeep load(CompoundTag tag)
    {
        NpcUpkeep data = new NpcUpkeep();
        for (long packed : tag.getLongArray("Halls"))
        {
            data.halls.add(BlockPos.of(packed));
        }
        return data;
    }
}
