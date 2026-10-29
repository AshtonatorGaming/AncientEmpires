package com.ancientempires.entity.npc;

import com.ancientempires.block.NpcUpkeep;
import com.ancientempires.block.TownHallBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.EmptyMapItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.List;

/**
 * The Governor (DESIGN Tier 1): a Worker-path NPC holding a map, surveying
 * the domain. By day it goes to its owner's nearest Town Hall and stays there;
 * every {@code governor.pulseTicks} the owner's workers within
 * {@code auraRadius} work {@code workBonus} faster, and each one it oversees
 * earns the hall a tax ({@code taxItem}, one per worker per
 * {@code taxTicksPerWorker}) put in the hall's store. One query per pulse, only
 * while governing.
 */
final class NpcGovernorGoal extends Goal
{
    private static final int CHECK_INTERVAL = 40;
    private static final double AT_HALL = 6.0D;

    private final NpcEntity npc;
    private TownHallBlockEntity hall;
    private int nextCheck;
    private int repath;
    /** Worker-ticks of oversight not yet paid as tax. */
    private long taxOwed;

    NpcGovernorGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    static boolean holdsMap(ItemStack stack)
    {
        return stack.getItem() instanceof MapItem || stack.getItem() instanceof EmptyMapItem;
    }

    private boolean governs()
    {
        return AncientEmpiresConfig.governorEnabled && this.npc.getNpcPath() == NpcPath.WORKER && holdsMap(this.npc.getMainHandItem())
                && this.npc.isOwnedNpc() && this.npc.getTarget() == null && !this.npc.isFollowing() && !this.npc.getNeeds().hungry()
                && this.npc.level().isDay();
    }

    @Override
    public boolean canUse()
    {
        if (this.npc.tickCount < this.nextCheck)
        {
            return false;
        }
        this.nextCheck = this.npc.tickCount + CHECK_INTERVAL + (this.npc.getId() & 15);
        if (!this.governs() || !(this.npc.level() instanceof ServerLevel level))
        {
            return false;
        }
        this.hall = NpcUpkeep.hallFor(level, this.npc.blockPosition(), this.npc.getOwner());
        return this.hall != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.hall != null && !this.hall.isRemoved() && this.governs();
    }

    @Override
    public void stop()
    {
        this.hall = null;
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        BlockPos at = this.hall.getBlockPos();
        double distance = this.npc.distanceToSqr(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        if (distance > AT_HALL * AT_HALL)
        {
            if (--this.repath <= 0)
            {
                this.repath = 20;
                this.npc.getNavigation().moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, AncientEmpiresConfig.workWalkSpeed);
            }
            return;
        }
        this.npc.getNavigation().stop();
        int pulse = AncientEmpiresConfig.governorPulseTicks;
        if ((this.npc.tickCount + this.npc.getId()) % pulse != 0)
        {
            return;
        }
        this.npc.level().getProfiler().push("ancientempires_governor");
        double radius = AncientEmpiresConfig.governorAuraRadius;
        List<NpcEntity> workers = this.npc.level().getEntitiesOfClass(NpcEntity.class, this.npc.getBoundingBox().inflate(radius, 8.0D, radius),
                other -> other != this.npc && other.isAlive() && other.getNpcPath() == NpcPath.WORKER && NpcRelations.sameOwner(this.npc, other));
        for (NpcEntity worker : workers)
        {
            // Lasts two pulses, so it holds while the governor stands at the hall.
            worker.govern(pulse * 2 + 10);
        }
        this.collectTax(workers.size(), pulse);
        this.npc.level().getProfiler().pop();
    }

    /** One tax item per worker per {@code taxTicksPerWorker} of oversight, into the hall's store. */
    private void collectTax(int workers, int pulse)
    {
        Item tax = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(AncientEmpiresConfig.governorTaxItem));
        int per = AncientEmpiresConfig.governorTaxTicksPerWorker;
        if (tax == null || per <= 0 || workers <= 0)
        {
            return;
        }
        this.taxOwed += (long) workers * pulse;
        int due = (int) (this.taxOwed / per);
        if (due <= 0)
        {
            return;
        }
        this.taxOwed -= (long) due * per;
        ItemStack left = this.hall.deposit(new ItemStack(tax, due));
        if (left.getCount() < due)
        {
            // Entered in the ledger: a stroke of the pen.
            this.npc.swing(InteractionHand.MAIN_HAND);
            this.npc.addXp(AncientEmpiresConfig.levelXpPerWork * (due - left.getCount()));
        }
    }
}
