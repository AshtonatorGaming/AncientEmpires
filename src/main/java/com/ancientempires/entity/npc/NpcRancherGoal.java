package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.AnimalPenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraftforge.common.IForgeShearable;

import java.util.List;

/**
 * A rancher: a Worker-path NPC with shears, at its owner's animal pen. It
 * walks up to each animal: culls the extras past the herd size with one clean
 * stroke (no beating them down), shears what has wool, feeds pairs for
 * breeding from the feed it carries (the {@code ancientempires:ranch_feed} tag,
 * fetched from the pen's store), and picks up eggs, feathers, and wool, then
 * carries it all back.
 */
public final class NpcRancherGoal extends NpcWorkGoal<AnimalPenBlockEntity>
{
    public static final TagKey<Item> FEED = ItemTags.create(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "ranch_feed"));

    private static final int CULL = 0;
    private static final int SHEAR = 1;
    private static final int BREED = 2;
    private static final int COLLECT = 3;
    private static final double PICKUP_RANGE = 1.5D;

    public NpcRancherGoal(NpcEntity npc)
    {
        super(npc, AnimalPenBlockEntity.class);
    }

    @Override
    protected boolean isTool(ItemStack stack)
    {
        return stack.getItem() instanceof ShearsItem;
    }

    @Override
    protected boolean isSupply(ItemStack stack)
    {
        return stack.is(FEED);
    }

    /** Cull, shear, feed for breeding (fetching feed if needed), pick up, then carry it home. */
    @Override
    protected boolean pickJob()
    {
        AnimalPenBlockEntity pen = this.site;
        if (pen == null)
        {
            return false;
        }
        if (this.tripToUnload(false))
        {
            return true;
        }
        int id = this.npc.getId();
        BlockPos at = this.npc.blockPosition();
        Animal animal = pen.claimAnimal(AnimalPenBlockEntity.Task.CULL, id, at);
        if (animal != null)
        {
            return this.assignEntity(CULL, animal);
        }
        animal = pen.claimAnimal(AnimalPenBlockEntity.Task.SHEAR, id, at);
        if (animal != null)
        {
            return this.assignEntity(SHEAR, animal);
        }
        if (pen.hasWork(AnimalPenBlockEntity.Task.BREED))
        {
            if (this.supplySlot() >= 0)
            {
                animal = pen.claimAnimal(AnimalPenBlockEntity.Task.BREED, id, at);
                if (animal != null && this.feedFor(animal) >= 0)
                {
                    return this.assignEntity(BREED, animal);
                }
                if (animal != null)
                {
                    pen.releaseEntity(animal.getUUID());
                }
            }
            else if (this.tripToFetch())
            {
                return true;
            }
        }
        BlockPos drop = pen.claimDrop(id, at);
        if (drop != null)
        {
            return this.assign(COLLECT, drop);
        }
        return this.tripToUnload(true);
    }

    @Override
    protected boolean act(ServerLevel level, int kind, BlockPos target)
    {
        Entity creature = this.entityTarget();
        switch (kind)
        {
            case CULL ->
            {
                if (creature instanceof Animal animal && animal.isAlive())
                {
                    // One clean stroke, as a butcher would; the drops are picked up after.
                    animal.hurt(this.npc.damageSources().mobAttack(this.npc), Float.MAX_VALUE);
                    level.sendParticles(ParticleTypes.POOF, animal.getX(), animal.getY() + 0.5D, animal.getZ(), 6, 0.3D, 0.3D, 0.3D, 0.02D);
                }
            }
            case SHEAR ->
            {
                if (creature instanceof IForgeShearable shearable && creature instanceof Animal animal
                        && shearable.isShearable(this.npc.getMainHandItem(), level, animal.blockPosition()))
                {
                    List<ItemStack> wool = shearable.onSheared(null, this.npc.getMainHandItem(), level, animal.blockPosition(), 0);
                    level.playSound(null, animal, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    this.wearTool();
                    for (ItemStack stack : wool)
                    {
                        ItemStack rest = this.pocket(stack);
                        if (!rest.isEmpty())
                        {
                            animal.spawnAtLocation(rest);
                        }
                    }
                }
            }
            case BREED ->
            {
                if (creature instanceof Animal animal && animal.isAlive() && animal.getAge() == 0 && !animal.isInLove())
                {
                    int slot = this.feedFor(animal);
                    if (slot >= 0)
                    {
                        ItemStack feed = this.npc.getInventory().getItem(slot);
                        feed.shrink(1);
                        this.npc.getInventory().setItem(slot, feed);
                        animal.setInLove(null);
                    }
                }
            }
            case COLLECT -> this.collectAt(level, target, PICKUP_RANGE);
            default ->
            {
            }
        }
        return false;
    }

    /** The backpack slot of feed this animal eats, or -1. */
    private int feedFor(Animal animal)
    {
        NpcInventory inventory = this.npc.getInventory();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && animal.isFood(stack))
            {
                return slot;
            }
        }
        return -1;
    }
}
