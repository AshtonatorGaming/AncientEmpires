package com.ancientempires.entity.npc;

import com.ancientempires.block.NpcUpkeep;
import com.ancientempires.block.TownHallBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.item.NpcSpawnEggItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A priest: a Worker-path NPC holding a book. When its owner's Town Hall in
 * reach has fallen NPCs recorded, it walks there and prays over them for
 * {@code priest.reviveTicks}, and the one fallen longest rises beside the hall
 * with what {@code upkeep.keepOnDeath} kept, paying {@code priest.cost} from
 * the hall's store when {@code priest.costEnabled}. Fights, orders, and meals
 * come first.
 */
public final class NpcPriestGoal extends Goal
{
    private static final int CHECK_INTERVAL = 100;
    private static final double REACH_SQR = 3.0D * 3.0D;
    private static final int GIVE_UP_TICKS = 600;

    private final NpcEntity npc;
    @Nullable
    private TownHallBlockEntity hall;
    private int nextCheck;
    private int ticks;
    private int prayer;
    private int repath;

    public NpcPriestGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean isPriest()
    {
        return this.npc.getNpcPath() == NpcPath.WORKER && NpcPath.isScripture(this.npc.getMainHandItem())
                && this.npc.isOwnedNpc() && this.npc.getTarget() == null && !this.npc.isFollowing() && !this.npc.isHungry();
    }

    @Override
    public boolean canUse()
    {
        if (this.npc.tickCount < this.nextCheck)
        {
            return false;
        }
        this.nextCheck = this.npc.tickCount + CHECK_INTERVAL + (this.npc.getId() & 31);
        if (!this.isPriest() || !(this.npc.level() instanceof ServerLevel level))
        {
            return false;
        }
        TownHallBlockEntity found = NpcUpkeep.hallFor(level, this.npc.blockPosition(), this.npc.getOwner());
        if (found == null || found.fallen().isEmpty() || !canPay(found))
        {
            return false;
        }
        this.hall = found;
        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.hall != null && !this.hall.isRemoved() && this.isPriest() && this.ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start()
    {
        this.ticks = 0;
        this.prayer = 0;
        this.repath = 0;
    }

    @Override
    public void stop()
    {
        this.hall = null;
        this.npc.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        TownHallBlockEntity site = this.hall;
        if (site == null)
        {
            return;
        }
        this.ticks++;
        BlockPos pos = site.getBlockPos();
        this.npc.getLookControl().setLookAt(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (this.npc.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > REACH_SQR)
        {
            this.prayer = 0;
            if (--this.repath <= 0)
            {
                this.repath = 20;
                this.npc.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.9D);
            }
            return;
        }
        this.npc.getNavigation().stop();
        ServerLevel level = (ServerLevel) this.npc.level();
        if (this.prayer == 0)
        {
            level.playSound(null, pos, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.NEUTRAL, 0.6F, 1.4F);
        }
        if (this.prayer % 10 == 0)
        {
            this.npc.swing(InteractionHand.MAIN_HAND);
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D, 12, 0.6D, 0.4D, 0.6D, 0.4D);
        }
        if (++this.prayer < AncientEmpiresConfig.priestReviveTicks)
        {
            return;
        }
        this.raise(level, site);
        this.hall = null;
    }

    private void raise(ServerLevel level, TownHallBlockEntity site)
    {
        if (!canPay(site))
        {
            return;
        }
        TownHallBlockEntity.Fallen fallen = site.takeOldestFallen();
        if (fallen == null)
        {
            return;
        }
        BlockPos spot = site.getBlockPos().relative(this.npc.getDirection().getOpposite());
        NpcEntity raised = NpcSpawnEggItem.spawnFrom(level, fallen.scroll(), spot, site.owner());
        if (raised == null)
        {
            return;
        }
        Item cost = costItem();
        if (AncientEmpiresConfig.priestCostEnabled && cost != null)
        {
            site.take(cost, AncientEmpiresConfig.priestCostCount);
        }
        level.playSound(null, spot, SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.6F, 1.2F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, raised.getX(), raised.getY() + 1.0D, raised.getZ(), 30, 0.4D, 0.8D, 0.4D, 0.3D);
        Player owner = site.owner() == null ? null : level.getServer().getPlayerList().getPlayer(site.owner());
        if (owner != null)
        {
            owner.displayClientMessage(Component.translatable("message.ancientempires.priest.raised", raised.getDisplayName()), false);
        }
    }

    private static boolean canPay(TownHallBlockEntity site)
    {
        if (!AncientEmpiresConfig.priestCostEnabled)
        {
            return true;
        }
        Item cost = costItem();
        return cost == null || site.has(cost, AncientEmpiresConfig.priestCostCount);
    }

    @Nullable
    private static Item costItem()
    {
        ResourceLocation id = ResourceLocation.tryParse(AncientEmpiresConfig.priestCostItem);
        return id == null || !ForgeRegistries.ITEMS.containsKey(id) ? null : ForgeRegistries.ITEMS.getValue(id);
    }
}
