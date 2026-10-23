package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.item.BandageItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A medic: a soldier with bandages in its off hand. It looks for the most
 * hurt ally within {@code medic.range} (same faction or owner, its owner, or
 * itself) under {@code medic.healBelow} of its health, goes to them even
 * mid-fight, and binds the wound with a bandage, then waits out
 * {@code medic.cooldownTicks}. The off hand restocks from bandages in the
 * backpack. Out of bandages, it is a soldier again.
 */
public final class NpcMedicGoal extends Goal
{
    private static final int SCAN_INTERVAL = 10;
    private static final double REACH_SQR = 2.5D * 2.5D;
    private static final int GIVE_UP_TICKS = 200;

    private final NpcEntity npc;
    @Nullable
    private LivingEntity patient;
    private int nextScan;
    private int readyAt;
    private int ticks;
    private int bindTicks;
    private int repath;

    public NpcMedicGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static boolean isMedic(NpcEntity npc)
    {
        return npc.getNpcPath().combat() && npc.getOffhandItem().getItem() instanceof BandageItem;
    }

    @Override
    public boolean canUse()
    {
        if (this.npc.tickCount < this.nextScan || this.npc.tickCount < this.readyAt || !isMedic(this.npc))
        {
            return false;
        }
        this.nextScan = this.npc.tickCount + SCAN_INTERVAL;
        this.patient = this.findPatient();
        return this.patient != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        LivingEntity patient = this.patient;
        return patient != null && patient.isAlive() && isMedic(this.npc) && this.ticks < GIVE_UP_TICKS
                && patient.getHealth() < patient.getMaxHealth();
    }

    @Override
    public void start()
    {
        this.ticks = 0;
        this.bindTicks = 0;
        this.repath = 0;
    }

    @Override
    public void stop()
    {
        this.patient = null;
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
        LivingEntity patient = this.patient;
        if (patient == null)
        {
            return;
        }
        this.ticks++;
        this.npc.getLookControl().setLookAt(patient, 30.0F, 30.0F);
        if (patient != this.npc && this.npc.distanceToSqr(patient) > REACH_SQR)
        {
            this.bindTicks = 0;
            if (--this.repath <= 0)
            {
                this.repath = 10;
                this.npc.getNavigation().moveTo(patient, AncientEmpiresConfig.approachSpeed);
            }
            return;
        }
        this.npc.getNavigation().stop();
        if (this.bindTicks == 0)
        {
            this.npc.swing(InteractionHand.OFF_HAND);
        }
        if (++this.bindTicks < AncientEmpiresConfig.medicBindTicks)
        {
            return;
        }
        this.bind(patient);
        this.patient = null;
    }

    private void bind(LivingEntity patient)
    {
        ItemStack bandages = this.npc.getOffhandItem();
        if (!(bandages.getItem() instanceof BandageItem))
        {
            return;
        }
        BandageItem.bind(patient);
        if (this.npc.level() instanceof ServerLevel level)
        {
            level.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight() * 0.8D, patient.getZ(),
                    3, 0.3D, 0.2D, 0.3D, 0.0D);
        }
        bandages.shrink(1);
        this.npc.setItemSlot(EquipmentSlot.OFFHAND, bandages);
        if (bandages.isEmpty())
        {
            this.restock();
        }
        this.readyAt = this.npc.tickCount + AncientEmpiresConfig.medicCooldownTicks;
    }

    /** Moves bandages from the backpack to the empty off hand. */
    private void restock()
    {
        NpcInventory inventory = this.npc.getInventory();
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof BandageItem)
            {
                inventory.setItem(NpcInventory.OFFHAND_SLOT, stack);
                inventory.setItem(slot, ItemStack.EMPTY);
                return;
            }
        }
    }

    /** The most hurt ally in range below the healing threshold, or null. */
    @Nullable
    private LivingEntity findPatient()
    {
        double range = AncientEmpiresConfig.medicRange;
        LivingEntity best = null;
        double worst = AncientEmpiresConfig.medicHealBelow;
        for (LivingEntity other : this.npc.level().getEntitiesOfClass(LivingEntity.class, this.npc.getBoundingBox().inflate(range),
                other -> other.isAlive() && this.isPatient(other)))
        {
            double ratio = other.getHealth() / other.getMaxHealth();
            if (ratio < worst)
            {
                worst = ratio;
                best = other;
            }
        }
        return best;
    }

    private boolean isPatient(LivingEntity other)
    {
        if (other == this.npc)
        {
            return true;
        }
        if (other instanceof NpcEntity npc)
        {
            return this.npc.isAlly(npc);
        }
        return other instanceof Player player && !player.isSpectator() && player.getUUID().equals(this.npc.getOwner());
    }
}
