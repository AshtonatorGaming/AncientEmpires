package com.ancientempires.block;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Immigration for Settlers' Posts. Nothing ticks per post: once per in-game day
 * per dimension, at the day change, each open post in a loaded chunk may draw a
 * small group of settlers. A group is capped by free beds within
 * {@code immigration.bedRadius} (vanilla's home points, the index villagers use,
 * so no block scan) less the owner's NPCs already there, and by stored food
 * bundles. Newcomers appear {@code spawnDistance} blocks out and walk in.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class NpcImmigration extends SavedData
{
    private static final String NAME = "ancientempires_settlers_posts";
    private static final String NEWCOMER_ROW = "owned.commoner";
    private static final int SPOT_TRIES = 12;

    private final Set<BlockPos> posts = new HashSet<>();
    private long lastDay = -1L;

    private static NpcImmigration get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(NpcImmigration::load, NpcImmigration::new, NAME);
    }

    static void register(ServerLevel level, BlockPos pos)
    {
        NpcImmigration data = get(level);
        if (data.posts.add(pos.immutable()))
        {
            data.setDirty();
        }
    }

    static void unregister(ServerLevel level, BlockPos pos)
    {
        NpcImmigration data = get(level);
        if (data.posts.remove(pos))
        {
            data.setDirty();
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || !AncientEmpiresConfig.immigrationEnabled)
        {
            return;
        }
        long day = level.getDayTime() / 24000L;
        NpcImmigration data = get(level);
        if (day == data.lastDay)
        {
            return;
        }
        boolean first = data.lastDay < 0L;
        data.lastDay = day;
        data.setDirty();
        if (first || data.posts.isEmpty())
        {
            return;
        }
        for (BlockPos pos : new ArrayList<>(data.posts))
        {
            if (!level.hasChunkAt(pos))
            {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof SettlersPostBlockEntity post)
            {
                arrive(level, pos, post);
            }
            else
            {
                data.posts.remove(pos);
            }
        }
    }

    private static void arrive(ServerLevel level, BlockPos pos, SettlersPostBlockEntity post)
    {
        UUID owner = post.owner();
        RandomSource random = level.getRandom();
        if (!post.isOpen() || owner == null || random.nextDouble() >= AncientEmpiresConfig.immigrationChance)
        {
            return;
        }
        int perSettler = Math.max(0, AncientEmpiresConfig.immigrationFoodPerSettler);
        int fed = perSettler == 0 ? Integer.MAX_VALUE : post.food() / perSettler;
        int group = Math.min(1 + random.nextInt(Math.max(1, AncientEmpiresConfig.immigrationMaxGroup)), Math.min(freeBeds(level, pos, owner), fed));
        int arrived = 0;
        for (int index = 0; index < group; index++)
        {
            BlockPos spot = arrivalSpot(level, pos, random);
            if (spot != null && spawnSettler(level, spot, pos, owner))
            {
                arrived++;
            }
        }
        if (arrived <= 0)
        {
            return;
        }
        post.useFood(arrived * perSettler);
        Player player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null)
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.post.arrived", arrived,
                    pos.getX(), pos.getY(), pos.getZ()), false);
        }
    }

    /** Beds near the post, less the owner's NPCs already living there. */
    private static int freeBeds(ServerLevel level, BlockPos pos, UUID owner)
    {
        int radius = AncientEmpiresConfig.immigrationBedRadius;
        long beds = level.getPoiManager().getCountInRange(type -> type.is(PoiTypes.HOME), pos, radius, PoiManager.Occupancy.ANY);
        int residents = level.getEntitiesOfClass(NpcEntity.class, new AABB(pos).inflate(radius),
                npc -> owner.equals(npc.getOwner())).size();
        return (int) Mth.clamp(beds - residents, 0L, Integer.MAX_VALUE);
    }

    /** Open, dry ground at about {@code spawnDistance} from the post, in a loaded chunk. */
    @Nullable
    private static BlockPos arrivalSpot(ServerLevel level, BlockPos post, RandomSource random)
    {
        double distance = AncientEmpiresConfig.immigrationSpawnDistance;
        for (int attempt = 0; attempt < SPOT_TRIES; attempt++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double reach = distance * (0.8D + random.nextDouble() * 0.4D);
            int x = post.getX() + Mth.floor(Math.cos(angle) * reach);
            int z = post.getZ() + Mth.floor(Math.sin(angle) * reach);
            if (!level.hasChunk(x >> 4, z >> 4))
            {
                continue;
            }
            BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (level.getFluidState(spot).isEmpty() && level.getFluidState(spot.below()).isEmpty()
                    && level.getBlockState(spot.below()).isSolid() && level.noCollision(ModEntities.NPC.get().getAABB(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D)))
            {
                return spot;
            }
        }
        return null;
    }

    private static boolean spawnSettler(ServerLevel level, BlockPos spot, BlockPos post, UUID owner)
    {
        NpcEntity.beginSpawnRoster(NEWCOMER_ROW);
        NpcEntity.beginSpawnClaim(owner);
        NpcEntity npc;
        try
        {
            npc = ModEntities.NPC.get().spawn(level, spot, MobSpawnType.EVENT);
        }
        finally
        {
            NpcEntity.endSpawnRoster();
            NpcEntity.endSpawnClaim();
        }
        if (npc == null)
        {
            return false;
        }
        npc.assignHome(post);
        npc.getNavigation().moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, 1.0D);
        return true;
    }

    /** What the sneak-click shows: open or closed, beds, food, and what would stop a wave. */
    static Component status(ServerLevel level, BlockPos pos, SettlersPostBlockEntity post)
    {
        UUID owner = post.owner();
        int free = owner == null ? 0 : freeBeds(level, pos, owner);
        int perSettler = Math.max(0, AncientEmpiresConfig.immigrationFoodPerSettler);
        String reason;
        if (!AncientEmpiresConfig.immigrationEnabled)
        {
            reason = "message.ancientempires.post.reason_disabled";
        }
        else if (!post.isOpen())
        {
            reason = "message.ancientempires.post.reason_closed";
        }
        else if (free <= 0)
        {
            reason = "message.ancientempires.post.reason_beds";
        }
        else if (perSettler > 0 && post.food() < perSettler)
        {
            reason = "message.ancientempires.post.reason_food";
        }
        else
        {
            reason = "message.ancientempires.post.reason_ready";
        }
        return Component.translatable("message.ancientempires.post.status",
                Component.translatable(post.isOpen() ? "message.ancientempires.post.open" : "message.ancientempires.post.shut"),
                free, post.food(), Component.translatable(reason));
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        long[] positions = new long[this.posts.size()];
        int index = 0;
        for (BlockPos pos : this.posts)
        {
            positions[index++] = pos.asLong();
        }
        tag.put("Posts", new LongArrayTag(positions));
        tag.putLong("LastDay", this.lastDay);
        return tag;
    }

    private static NpcImmigration load(CompoundTag tag)
    {
        NpcImmigration data = new NpcImmigration();
        for (long packed : tag.getLongArray("Posts"))
        {
            data.posts.add(BlockPos.of(packed));
        }
        data.lastDay = tag.contains("LastDay") ? tag.getLong("LastDay") : -1L;
        return data;
    }
}
