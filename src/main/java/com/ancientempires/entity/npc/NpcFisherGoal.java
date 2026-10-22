package com.ancientempires.entity.npc;

import com.ancientempires.block.FishingDockBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A fisher: a Worker-path NPC with a fishing rod, at its owner's fishing dock.
 * It walks to the bank by a stretch of open water, casts (vanilla's bobber
 * only works for players, so the water stirs where the line went in), waits as
 * long as vanilla fishing does, and reels in a catch from vanilla's fishing
 * loot table (fish, junk, the odd treasure, and anything mods add to it), then
 * carries the catch back.
 */
public final class NpcFisherGoal extends NpcWorkGoal<FishingDockBlockEntity>
{
    private static final int FISH = 0;
    private static final double CAST_REACH_SQR = 6.0D * 6.0D;
    /** Marks water with no bank in reach. */
    private static final BlockPos NO_SHORE = new BlockPos(0, Integer.MIN_VALUE, 0);

    /** Shore spots worked out for water tiles, so the search runs once per tile. */
    private final Map<BlockPos, BlockPos> shores = new HashMap<>();

    public NpcFisherGoal(NpcEntity npc)
    {
        super(npc, FishingDockBlockEntity.class);
    }

    @Override
    protected boolean isTool(ItemStack stack)
    {
        return stack.getItem() instanceof FishingRodItem;
    }

    @Override
    protected boolean pickJob()
    {
        FishingDockBlockEntity dock = this.site;
        if (dock == null)
        {
            return false;
        }
        if (this.tripToUnload(false))
        {
            return true;
        }
        BlockPos water = dock.claimWater(this.npc.getId(), this.npc.blockPosition());
        if (water != null && this.shore(water) != null)
        {
            return this.assign(FISH, water);
        }
        if (water != null)
        {
            dock.release(water);
        }
        return this.tripToUnload(true);
    }

    @Nullable
    private BlockPos shore(BlockPos water)
    {
        if (this.shores.size() > 256)
        {
            this.shores.clear();
        }
        BlockPos cached = this.shores.get(water);
        if (cached == null)
        {
            BlockPos found = FishingDockBlockEntity.shoreFor(this.npc.level(), water);
            cached = found == null ? NO_SHORE : found;
            this.shores.put(water, cached);
        }
        return cached == NO_SHORE ? null : cached;
    }

    @Override
    protected BlockPos walkTarget(int kind, BlockPos target)
    {
        BlockPos shore = this.shore(target);
        return shore == null ? target : shore;
    }

    /** On dry land, within a cast of the water. */
    @Override
    protected boolean inReach(int kind, BlockPos target, @Nullable Entity creature)
    {
        if (kind != FISH)
        {
            return super.inReach(kind, target, creature);
        }
        return !this.npc.isInWater() && this.npc.onGround()
                && this.npc.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) <= CAST_REACH_SQR;
    }

    @Override
    protected boolean swings(int kind)
    {
        return kind != FISH;
    }

    /** Vanilla's bite wait: 5 to 30 seconds, less with Lure on the rod. */
    @Override
    protected int actionTicks(int kind, BlockPos target)
    {
        if (kind != FISH)
        {
            return super.actionTicks(kind, target);
        }
        int lure = net.minecraft.world.item.enchantment.EnchantmentHelper.getFishingSpeedBonus(this.npc.getMainHandItem());
        int wait = 100 + this.npc.getRandom().nextInt(500);
        return Math.max(20, wait - lure * 100);
    }

    @Override
    protected void onActionStart(int kind)
    {
        if (kind == FISH)
        {
            this.npc.swing(InteractionHand.MAIN_HAND);
            this.npc.level().playSound(null, this.npc.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F,
                    0.4F / (this.npc.getRandom().nextFloat() * 0.4F + 0.8F));
        }
    }

    /** Where the line went in, the water stirs now and then. */
    @Override
    protected void onActionTick(int kind, BlockPos target, int tick)
    {
        if (kind == FISH && tick % 20 == 0 && this.npc.level() instanceof ServerLevel level)
        {
            level.sendParticles(ParticleTypes.FISHING, target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D, 2, 0.1D, 0.0D, 0.1D, 0.0D);
        }
    }

    @Override
    protected boolean act(ServerLevel level, int kind, BlockPos target)
    {
        if (kind != FISH || level.getFluidState(target).isEmpty())
        {
            return false;
        }
        Vec3 bob = new Vec3(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D);
        level.sendParticles(ParticleTypes.SPLASH, bob.x, bob.y, bob.z, 12, 0.2D, 0.0D, 0.2D, 0.1D);
        level.playSound(null, target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);
        this.npc.swing(InteractionHand.MAIN_HAND);
        ItemStack rod = this.npc.getMainHandItem();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, bob)
                .withParameter(LootContextParams.TOOL, rod)
                .withLuck(net.minecraft.world.item.enchantment.EnchantmentHelper.getFishingLuckBonus(rod))
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
        List<ItemStack> catches = table.getRandomItems(params);
        for (ItemStack fish : catches)
        {
            ItemStack rest = this.pocket(fish);
            if (!rest.isEmpty())
            {
                this.npc.spawnAtLocation(rest);
            }
        }
        rod.hurtAndBreak(1, this.npc, npc -> npc.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        // Cast again into the same water while there is room in the backpack.
        return !this.backpackFull() && this.isTool(this.npc.getMainHandItem()) && AncientEmpiresConfig.fishRecast;
    }
}
