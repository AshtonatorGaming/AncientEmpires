package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Ancient Warfare's NpcAIRideHorse. A faction cavalry or mounted-archer row
 * spawns on its mount ({@link NpcRoster.Entry#mount()}: a horse, or a Norska
 * polar bear, a gnome chicken, ...). A mount that dies is not replaced; the
 * rider fights on foot. A rider thrown from a living mount walks back to it
 * when it is not fighting. Owned NPCs mount and dismount on command.
 * <p>
 * Vanilla turns a mount's own movement off while a mob rides it, but leaves
 * nothing driving it. While riding, {@link NpcEntity#getNavigation()} hands the
 * NPC's goals the mount's navigation, so the mount paths with its own size.
 */
public final class NpcMount
{
    private static final String UUID_KEY = "AeMountUuid";
    private static final String LOST_KEY = "AeMountLost";
    /** On a mount this mod spawned, so a repack can take it along instead of leaving it. */
    private static final String SPAWNED_TAG = "AeSpawnedMount";
    private static final UUID SPEED_ID = UUID.fromString("5f0b7c8e-3a41-4c55-9d1e-6a2f6f0c4b21");
    private static final double BOARD_RANGE_SQR = 2.5D * 2.5D;

    private final NpcEntity npc;
    @Nullable
    private UUID mountUuid;
    /** The roster mount died or was given up; it is not spawned again. */
    private boolean lost;

    NpcMount(NpcEntity npc)
    {
        this.npc = npc;
    }

    /** Server tick. */
    void tick()
    {
        if (this.npc.getVehicle() instanceof Mob mount)
        {
            this.mountUuid = mount.getUUID();
            steer(mount);
            return;
        }
        if ((this.npc.tickCount + this.npc.getId()) % 10 != 0 || !(this.npc.level() instanceof ServerLevel level))
        {
            return;
        }
        if (this.mountUuid != null)
        {
            this.returnToMount(level);
            return;
        }
        this.spawnRosterMount(level);
    }

    /**
     * Keeps the mount's own goals from moving or turning it (vanilla re-enables
     * them every few ticks for mounts that only answer to players, such as pigs),
     * and gives it the riding speed.
     */
    private static void steer(Mob mount)
    {
        mount.goalSelector.setControlFlag(Goal.Flag.MOVE, false);
        mount.goalSelector.setControlFlag(Goal.Flag.JUMP, false);
        mount.goalSelector.setControlFlag(Goal.Flag.LOOK, false);
        AttributeInstance speed = mount.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_ID) == null && AncientEmpiresConfig.cavalryMountSpeed != 1.0D)
        {
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "NPC rider", AncientEmpiresConfig.cavalryMountSpeed - 1.0D,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    /** Called as the NPC leaves a vehicle. */
    void onDismount(Entity vehicle)
    {
        if (vehicle instanceof Mob mount)
        {
            AttributeInstance speed = mount.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null)
            {
                speed.removeModifier(SPEED_ID);
            }
        }
    }

    /** Whether the NPC is the one steering this mount. */
    boolean steers(Mob mount)
    {
        return mount.getFirstPassenger() == this.npc;
    }

    private void returnToMount(ServerLevel level)
    {
        Entity entity = level.getEntity(this.mountUuid);
        double range = AncientEmpiresConfig.cavalryRemountRange;
        if (!(entity instanceof Mob mount) || !mount.isAlive() || mount.isVehicle()
                || this.npc.distanceToSqr(mount) > range * range)
        {
            // Dead, gone, taken, or too far: the rider carries on afoot.
            this.mountUuid = null;
            this.lost = true;
            return;
        }
        if (this.npc.getTarget() != null)
        {
            return;
        }
        if (this.npc.distanceToSqr(mount) <= BOARD_RANGE_SQR)
        {
            this.npc.startRiding(mount, true);
            return;
        }
        this.npc.getNavigation().moveTo(mount, 1.0D);
    }

    private void spawnRosterMount(ServerLevel level)
    {
        if (this.lost || !this.npc.isFactionNpc() || this.npc.tickCount < 5)
        {
            return;
        }
        NpcRoster.Entry entry = this.npc.rosterEntry();
        if (entry == null || entry.mount().isEmpty())
        {
            return;
        }
        // Whatever happens next, this NPC tries once.
        this.lost = true;
        ResourceLocation id = ResourceLocation.tryParse(entry.mount());
        EntityType<?> type = id == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
        Entity created = type == null ? null : type.create(level);
        if (!(created instanceof Mob mount))
        {
            if (created != null)
            {
                created.discard();
            }
            AncientEmpires.LOGGER.warn("Roster row {} names mount {}, which is not a mob", entry.id(), entry.mount());
            return;
        }
        mount.moveTo(this.npc.getX(), this.npc.getY(), this.npc.getZ(), this.npc.getYRot(), 0.0F);
        mount.finalizeSpawn(level, level.getCurrentDifficultyAt(this.npc.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
        // A spider can roll a skeleton jockey; the seat is the NPC's.
        for (Entity passenger : List.copyOf(mount.getPassengers()))
        {
            passenger.stopRiding();
            passenger.discard();
        }
        if (mount instanceof AbstractHorse horse)
        {
            horse.setTamed(true);
            if (horse.isSaddleable())
            {
                horse.equipSaddle(null);
            }
        }
        mount.setPersistenceRequired();
        mount.getPersistentData().putBoolean(SPAWNED_TAG, true);
        if (!level.addFreshEntity(mount))
        {
            return;
        }
        if (this.npc.startRiding(mount, true))
        {
            this.mountUuid = mount.getUUID();
            this.lost = false;
        }
    }

    /**
     * Before the NPC is packed into its scroll: a mount this mod spawned goes
     * with it (it is spawned again on placing), anything else is left standing.
     */
    void beforeRepack()
    {
        Entity vehicle = this.npc.getVehicle();
        this.npc.stopRiding();
        this.mountUuid = null;
        if (vehicle != null && vehicle.getPersistentData().getBoolean(SPAWNED_TAG))
        {
            vehicle.discard();
        }
    }

    /**
     * The inventory command. Off a mount: climb onto the nearest free one in
     * {@code cavalry.mountRange}, which for a player must be a saddled horse they
     * tamed; an admin may seat the NPC on any mob. On a mount: get off.
     */
    void command(Player player)
    {
        if (this.npc.isPassenger())
        {
            this.npc.stopRiding();
            this.mountUuid = null;
            player.displayClientMessage(Component.translatable("message.ancientempires.npc.dismounted"), true);
            return;
        }
        boolean admin = NpcEntity.mayToggleCreative(player);
        double range = AncientEmpiresConfig.cavalryMountRange;
        AABB area = this.npc.getBoundingBox().inflate(range);
        Mob best = null;
        double bestDistance = range * range;
        for (Mob mob : this.npc.level().getEntitiesOfClass(Mob.class, area, mob -> this.canBoard(mob, player, admin)))
        {
            double distance = this.npc.distanceToSqr(mob);
            if (distance <= bestDistance)
            {
                bestDistance = distance;
                best = mob;
            }
        }
        if (best == null || !this.npc.startRiding(best, true))
        {
            player.displayClientMessage(Component.translatable(admin
                    ? "message.ancientempires.npc.no_mount_admin"
                    : "message.ancientempires.npc.no_mount"), true);
            return;
        }
        this.mountUuid = best.getUUID();
        player.displayClientMessage(Component.translatable("message.ancientempires.npc.mounted", best.getDisplayName()), true);
    }

    private boolean canBoard(Mob mob, Player player, boolean admin)
    {
        if (mob instanceof NpcEntity || !mob.isAlive() || mob.isVehicle() || mob == this.npc.getVehicle())
        {
            return false;
        }
        if (admin)
        {
            return true;
        }
        return mob instanceof AbstractHorse horse && horse.isTamed() && horse.isSaddled()
                && player.getUUID().equals(horse.getOwnerUUID());
    }

    void save(CompoundTag tag)
    {
        if (this.mountUuid != null)
        {
            tag.putUUID(UUID_KEY, this.mountUuid);
        }
        if (this.lost)
        {
            tag.putBoolean(LOST_KEY, true);
        }
    }

    /** For the scroll: whether the roster mount is gone. The mount itself is not packed. */
    void savePacked(CompoundTag tag)
    {
        tag.putBoolean(LOST_KEY, this.lost);
    }

    void load(CompoundTag tag)
    {
        this.mountUuid = tag.hasUUID(UUID_KEY) ? tag.getUUID(UUID_KEY) : null;
        this.lost = tag.getBoolean(LOST_KEY);
    }
}
