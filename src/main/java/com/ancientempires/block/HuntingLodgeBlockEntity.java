package com.ancientempires.block;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A hunting lodge: a large hunting ground around it. Hunters ask it for game
 * (wild animals, never livestock in an Animal Pen, named, leashed, or tamed
 * ones, nor anything in {@code ancientempires:hunter_ignores}; creatures in
 * {@code hunter_game} count too), always leaving {@code hunting.keepPerKind}
 * of each kind so the ground recovers. Vanilla barely respawns animals, so
 * while a hunter works it, game wanders in: every {@code spawnInterval} ticks,
 * below {@code gameCap}, one creature from the biome's own creature spawn list
 * (modded animals included) appears at the edge of the ground.
 */
public final class HuntingLodgeBlockEntity extends WorksiteBlockEntity
{
    public static final TagKey<EntityType<?>> IGNORES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "hunter_ignores"));
    public static final TagKey<EntityType<?>> GAME = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "hunter_game"));

    private static final int VERTICAL = 16;
    private static final int SPAWN_TRIES = 10;

    private final List<LivingEntity> game = new ArrayList<>();
    private long lastWanderIn;

    public HuntingLodgeBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.HUNTING_LODGE.get(), pos, state);
    }

    @Override
    protected int defaultRadius()
    {
        return AncientEmpiresConfig.huntDefaultRadius;
    }

    @Override
    protected int maxRadius()
    {
        return AncientEmpiresConfig.huntMaxRadius;
    }

    @Nullable
    public LivingEntity claimGame(int hunterId, BlockPos from)
    {
        this.refresh();
        return this.claimNearestEntity(this.game, hunterId, from);
    }

    @Override
    protected void forget(BlockPos tile)
    {
    }

    @Override
    public void releaseEntity(UUID creature)
    {
        super.releaseEntity(creature);
        this.game.removeIf(animal -> animal.getUUID().equals(creature));
    }

    private AABB ground()
    {
        int r = this.radius();
        return new AABB(this.worldPosition).inflate(r, VERTICAL, r);
    }

    @Override
    protected void scan(Level level)
    {
        this.game.clear();
        List<AnimalPenBlockEntity> pens = this.pensNearby(level);
        Map<EntityType<?>, List<LivingEntity>> byKind = new HashMap<>();
        for (LivingEntity creature : level.getEntitiesOfClass(LivingEntity.class, this.ground(), creature -> isGame(creature, pens)))
        {
            byKind.computeIfAbsent(creature.getType(), type -> new ArrayList<>()).add(creature);
        }
        int keep = Math.max(0, AncientEmpiresConfig.huntKeepPerKind);
        int total = 0;
        for (List<LivingEntity> kind : byKind.values())
        {
            total += kind.size();
            if (kind.size() > keep)
            {
                this.game.addAll(kind.subList(keep, kind.size()));
            }
        }
        if (level instanceof ServerLevel server && total < AncientEmpiresConfig.huntGameCap
                && level.getGameTime() - this.lastWanderIn >= AncientEmpiresConfig.huntSpawnInterval)
        {
            this.lastWanderIn = level.getGameTime();
            this.wanderIn(server);
        }
    }

    /** Wild, unclaimed game: an animal (or a {@code hunter_game} creature) that no pen keeps and nobody owns. */
    private static boolean isGame(LivingEntity creature, List<AnimalPenBlockEntity> pens)
    {
        if (!creature.isAlive() || creature.isBaby() || creature.getType().is(IGNORES))
        {
            return false;
        }
        boolean huntable = creature.getType().is(GAME) || (creature instanceof Animal animal && AnimalPenBlockEntity.livestock(animal));
        if (!huntable || creature.hasCustomName() || (creature instanceof Mob mob && mob.isLeashed()))
        {
            return false;
        }
        for (AnimalPenBlockEntity pen : pens)
        {
            if (pen.area().contains(creature.position()))
            {
                return false;
            }
        }
        return true;
    }

    /** Animal pens near the ground, so their stock is not hunted. Block entities only, in loaded chunks. */
    private List<AnimalPenBlockEntity> pensNearby(Level level)
    {
        List<AnimalPenBlockEntity> pens = new ArrayList<>();
        int reach = this.radius() + AncientEmpiresConfig.ranchMaxRadius;
        for (int cx = (this.worldPosition.getX() - reach) >> 4; cx <= (this.worldPosition.getX() + reach) >> 4; cx++)
        {
            for (int cz = (this.worldPosition.getZ() - reach) >> 4; cz <= (this.worldPosition.getZ() + reach) >> 4; cz++)
            {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null)
                {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values())
                {
                    if (entity instanceof AnimalPenBlockEntity pen)
                    {
                        pens.add(pen);
                    }
                }
            }
        }
        return pens;
    }

    /**
     * One creature from the biome's creature spawn list at the edge of the
     * ground, where vanilla's own spawn rules allow it (grass for most animals).
     */
    private void wanderIn(ServerLevel level)
    {
        RandomSource random = level.getRandom();
        int r = this.radius();
        for (int attempt = 0; attempt < SPAWN_TRIES; attempt++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double reach = r * (0.75D + random.nextDouble() * 0.25D);
            int x = this.worldPosition.getX() + Mth.floor(Math.cos(angle) * reach);
            int z = this.worldPosition.getZ() + Mth.floor(Math.sin(angle) * reach);
            if (!level.hasChunk(x >> 4, z >> 4))
            {
                continue;
            }
            BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            WeightedRandomList<MobSpawnSettings.SpawnerData> creatures = level.getBiome(spot).value().getMobSettings().getMobs(MobCategory.CREATURE);
            Optional<MobSpawnSettings.SpawnerData> pick = creatures.getRandom(random);
            if (pick.isEmpty())
            {
                continue;
            }
            EntityType<?> type = pick.get().type;
            if (type.is(IGNORES) || !SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, spot, random)
                    || !level.noCollision(type.getAABB(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D)))
            {
                continue;
            }
            if (type.spawn(level, spot, MobSpawnType.NATURAL) != null)
            {
                return;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putLong("LastWanderIn", this.lastWanderIn);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.lastWanderIn = tag.getLong("LastWanderIn");
    }
}
