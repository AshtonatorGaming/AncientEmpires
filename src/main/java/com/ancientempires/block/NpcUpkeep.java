package com.ancientempires.block;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Town Hall upkeep. Nothing ticks per hall: once per in-game day per
 * dimension, each hall in a loaded chunk feeds its owner's NPCs within
 * {@code upkeep.range}; those it cannot feed go hungry, and hungry workers do
 * not work. NPCs no hall reaches are left alone, unless
 * {@code upkeep.requireHall} is on. Also finds the hall that records an owned
 * NPC's death.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class NpcUpkeep extends SavedData
{
    private static final String NAME = "ancientempires_town_halls";

    private final Set<BlockPos> halls = new HashSet<>();
    private long lastDay = -1L;

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

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || !AncientEmpiresConfig.upkeepEnabled)
        {
            return;
        }
        long day = level.getDayTime() / 24000L;
        NpcUpkeep data = get(level);
        if (day == data.lastDay)
        {
            return;
        }
        boolean first = data.lastDay < 0L;
        data.lastDay = day;
        data.setDirty();
        if (!first)
        {
            data.feedAll(level);
        }
    }

    private void feedAll(ServerLevel level)
    {
        Set<NpcEntity> fed = new HashSet<>();
        Set<NpcEntity> seen = new HashSet<>();
        double range = AncientEmpiresConfig.upkeepRange;
        for (BlockPos pos : new ArrayList<>(this.halls))
        {
            if (!level.hasChunkAt(pos))
            {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof TownHallBlockEntity hall))
            {
                this.halls.remove(pos);
                this.setDirty();
                continue;
            }
            UUID owner = hall.owner();
            if (owner == null)
            {
                continue;
            }
            for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, new AABB(pos).inflate(range),
                    npc -> owner.equals(npc.getOwner())))
            {
                seen.add(npc);
                if (!fed.contains(npc) && hall.feedOne())
                {
                    fed.add(npc);
                }
            }
        }
        for (NpcEntity npc : seen)
        {
            npc.setHungry(!fed.contains(npc));
        }
        if (AncientEmpiresConfig.upkeepRequireHall)
        {
            for (Entity entity : level.getAllEntities())
            {
                if (entity instanceof NpcEntity npc && npc.isOwnedNpc() && !seen.contains(npc))
                {
                    npc.setHungry(true);
                }
            }
        }
    }

    /** The owner's nearest town hall within {@code upkeep.range}, or null. */
    @Nullable
    private static TownHallBlockEntity hallFor(ServerLevel level, BlockPos at, UUID owner)
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

    /** Records a fallen owned NPC at its owner's nearest hall, if one is in range. */
    public static void recordFallen(ServerLevel level, NpcEntity npc, ItemStack packed)
    {
        UUID owner = npc.getOwner();
        if (owner == null)
        {
            return;
        }
        TownHallBlockEntity hall = hallFor(level, npc.blockPosition(), owner);
        if (hall != null)
        {
            hall.recordFallen(packed);
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
        tag.putLong("LastDay", this.lastDay);
        return tag;
    }

    private static NpcUpkeep load(CompoundTag tag)
    {
        NpcUpkeep data = new NpcUpkeep();
        for (long packed : tag.getLongArray("Halls"))
        {
            data.halls.add(BlockPos.of(packed));
        }
        data.lastDay = tag.contains("LastDay") ? tag.getLong("LastDay") : -1L;
        return data;
    }

    /** The hall's status line pieces: NPCs in range, how many are hungry, food left, fallen recorded. */
    static List<Integer> status(ServerLevel level, BlockPos pos, TownHallBlockEntity hall)
    {
        UUID owner = hall.owner();
        int total = 0;
        int hungry = 0;
        if (owner != null)
        {
            for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, new AABB(pos).inflate(AncientEmpiresConfig.upkeepRange),
                    npc -> owner.equals(npc.getOwner())))
            {
                total++;
                if (npc.isHungry())
                {
                    hungry++;
                }
            }
        }
        return List.of(total, hungry, hall.fallen().size());
    }
}
