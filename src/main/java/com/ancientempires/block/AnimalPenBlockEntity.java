package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.IForgeShearable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An animal pen. Ranchers ask it for animals to cull (adults past
 * {@code work.ranchHerdSize} of a kind), to shear, and to feed for breeding
 * (kinds below the herd size with two adults ready), and for drops (eggs,
 * feathers, wool) lying in the pen. Named, leashed, and tamed animals are left
 * alone. The pen's square runs from two blocks below the block to three above.
 */
public final class AnimalPenBlockEntity extends WorksiteBlockEntity
{
    public enum Task
    {
        CULL, SHEAR, BREED, COLLECT
    }

    private static final ItemStack SHEARS = new ItemStack(Items.SHEARS);

    private final List<Animal> cull = new ArrayList<>();
    private final List<Animal> shear = new ArrayList<>();
    private final List<Animal> breed = new ArrayList<>();
    private final List<BlockPos> drops = new ArrayList<>();

    public AnimalPenBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.ANIMAL_PEN.get(), pos, state);
    }

    @Override
    protected int defaultRadius()
    {
        return AncientEmpiresConfig.ranchDefaultRadius;
    }

    @Override
    protected int maxRadius()
    {
        return AncientEmpiresConfig.ranchMaxRadius;
    }

    /** The pen's volume. */
    public AABB area()
    {
        int r = this.radius();
        return new AABB(this.worldPosition).inflate(r, 0, r).expandTowards(0, 3, 0).expandTowards(0, -2, 0);
    }

    @Nullable
    public Animal claimAnimal(Task task, int workerId, BlockPos from)
    {
        this.refresh();
        List<Animal> list = switch (task)
        {
            case CULL -> this.cull;
            case SHEAR -> this.shear;
            case BREED -> this.breed;
            case COLLECT -> List.of();
        };
        return this.claimNearestEntity(list, workerId, from);
    }

    @Nullable
    public BlockPos claimDrop(int workerId, BlockPos from)
    {
        this.refresh();
        return this.claimNearest(this.drops, workerId, from);
    }

    public boolean hasWork(Task task)
    {
        this.refresh();
        return switch (task)
        {
            case CULL -> !this.cull.isEmpty();
            case SHEAR -> !this.shear.isEmpty();
            case BREED -> !this.breed.isEmpty();
            case COLLECT -> !this.drops.isEmpty();
        };
    }

    @Override
    protected void forget(BlockPos tile)
    {
        this.drops.remove(tile);
    }

    @Override
    public void releaseEntity(UUID creature)
    {
        super.releaseEntity(creature);
        this.cull.removeIf(animal -> animal.getUUID().equals(creature));
        this.shear.removeIf(animal -> animal.getUUID().equals(creature));
        this.breed.removeIf(animal -> animal.getUUID().equals(creature));
    }

    @Override
    protected void scan(Level level)
    {
        this.cull.clear();
        this.shear.clear();
        this.breed.clear();
        this.drops.clear();
        Map<EntityType<?>, List<Animal>> adults = new HashMap<>();
        for (Animal animal : level.getEntitiesOfClass(Animal.class, this.area(), AnimalPenBlockEntity::livestock))
        {
            if (animal.isBaby())
            {
                continue;
            }
            adults.computeIfAbsent(animal.getType(), type -> new ArrayList<>()).add(animal);
            if (animal instanceof IForgeShearable shearable && shearable.isShearable(SHEARS, level, animal.blockPosition()))
            {
                this.shear.add(animal);
            }
        }
        int herd = Math.max(2, AncientEmpiresConfig.ranchHerdSize);
        for (List<Animal> kind : adults.values())
        {
            if (kind.size() > herd)
            {
                this.cull.addAll(kind.subList(herd, kind.size()));
                continue;
            }
            List<Animal> ready = kind.stream().filter(animal -> animal.getAge() == 0 && !animal.isInLove()).toList();
            if (kind.size() < herd && ready.size() >= 2)
            {
                this.breed.addAll(ready);
            }
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.area(), item -> item.isAlive() && !item.hasPickUpDelay()))
        {
            BlockPos at = item.blockPosition();
            if (!this.drops.contains(at))
            {
                this.drops.add(at);
            }
        }
    }

    /** Stock the pen may work: not named, leashed, or anyone's pet. */
    public static boolean livestock(Animal animal)
    {
        if (!animal.isAlive() || animal.hasCustomName() || animal.isLeashed())
        {
            return false;
        }
        return !(animal instanceof TamableAnimal tamable && tamable.isTame());
    }
}
