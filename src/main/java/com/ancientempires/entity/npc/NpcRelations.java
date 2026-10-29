package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Whom an NPC sides with and goes after (AW Tweaked's relations, the
 * {@code relations} config): allies (same faction, or same owner), what its
 * automatic scan may pick, hostility to players (standing below zero, or a
 * grudge for a faction-mate's death nearby), and the standing a kill costs.
 * Grudges are saved as {@code AeRevenge} (not packed).
 */
public final class NpcRelations
{
    private static final String REVENGE_KEY = "AeRevenge";

    private final NpcEntity npc;
    /** Players this NPC holds a faction-mate's death against, with the game time it ends. */
    private final Map<UUID, Long> revenge = new HashMap<>();

    NpcRelations(NpcEntity npc)
    {
        this.npc = npc;
    }

    /**
     * Same side: the same faction for faction NPCs, the same owner for owned ones.
     * Allies answer each other's alerts.
     */
    public boolean isAlly(NpcEntity other)
    {
        if (other == this.npc)
        {
            return false;
        }
        if (this.npc.isFactionNpc())
        {
            return this.npc.getFaction().equals(other.getFaction());
        }
        return sameOwner(this.npc, other);
    }

    /**
     * What the automatic scan may pick: any monster, or an NPC that
     * {@link #acceptsTarget} allows. Vanilla's combat checks run after this.
     */
    public boolean scanTarget(LivingEntity living)
    {
        if (living.getFirstPassenger() instanceof NpcEntity)
        {
            // An NPC's mount (a gremlin's spider, an undead's skeleton horse); the rider is the target.
            return false;
        }
        if (living instanceof Player player)
        {
            return this.hostileTo(player);
        }
        if (living instanceof NpcEntity target)
        {
            return this.acceptsTarget(target);
        }
        if (this.npc.isFactionNpc() && AncientEmpiresConfig.relationsEnabled)
        {
            return NpcFactions.hunts(this.npc.getFaction(), living);
        }
        return living instanceof Enemy;
    }

    /**
     * Automatic scans only. A same-owner pair is never a target. Factions attack
     * the factions on their hostile list, and owned NPCs of players they are
     * hostile to; an owned NPC attacks faction NPCs hostile to its owner.
     * A hit still uses the hurt-by goal, which only rejects a same-owner attacker.
     */
    boolean acceptsTarget(NpcEntity target)
    {
        if (target == this.npc || !target.isAlive() || sameOwner(this.npc, target) || target.getMorale().isSurrendered())
        {
            return false;
        }
        if (!this.chases(target))
        {
            return false;
        }
        if (this.npc.isFactionNpc())
        {
            if (target.isFactionNpc())
            {
                return NpcFactions.hostile(this.npc.getFaction(), target.getFaction());
            }
            return target.isOwnedNpc() && this.hostileToPlayer(target.getOwner());
        }
        if (!this.npc.isOwnedNpc() || !target.isFactionNpc())
        {
            return false;
        }
        return !AncientEmpiresConfig.relationsEnabled || target.getRelations().hostileToPlayer(this.npc.getOwner());
    }

    /**
     * Whether this NPC goes after a target: anyone fighting, and a routing NPC
     * only within {@code morale.chaseRange} on foot, so a won fight does not
     * scatter the winners across the field; riders run routers down.
     */
    public boolean chases(LivingEntity target)
    {
        if (!(target instanceof NpcEntity npc) || !npc.getMorale().isBroken() || this.npc.isPassenger() || AncientEmpiresConfig.moraleChaseRange <= 0.0D)
        {
            return true;
        }
        return this.npc.distanceToSqr(target) <= AncientEmpiresConfig.moraleChaseRange * AncientEmpiresConfig.moraleChaseRange;
    }

    /** A faction NPC's view of a player in the world. Creative and spectator players are never enemies. */
    public boolean hostileTo(Player player)
    {
        return !player.isCreative() && !player.isSpectator() && this.hostileToPlayer(player.getUUID());
    }

    /**
     * Whether this faction NPC treats the player (and the player's NPCs) as an
     * enemy: standing below zero, or a recent kill of one of its own nearby
     * (revenge, as in Ancient Warfare). Owned NPCs never do.
     */
    public boolean hostileToPlayer(@Nullable UUID player)
    {
        if (player == null || !this.npc.isFactionNpc() || !AncientEmpiresConfig.relationsEnabled || this.npc.level().isClientSide)
        {
            return false;
        }
        Long until = this.revenge.get(player);
        if (until != null)
        {
            if (until > this.npc.level().getGameTime())
            {
                return true;
            }
            this.revenge.remove(player);
        }
        MinecraftServer server = this.npc.level().getServer();
        return server != null && NpcStandings.get(server).hostile(player, this.npc.getFaction());
    }

    /** This NPC holds the kill of one of its own against the player for {@code relations.revengeTicks}. */
    public void markRevenge(UUID player)
    {
        this.revenge.put(player, this.npc.level().getGameTime() + AncientEmpiresConfig.relationsRevengeTicks);
    }

    /**
     * Ancient Warfare's standing change and revenge: killing a faction NPC, or
     * having one's owned NPCs kill it, costs the player that faction's
     * {@code kill} standing, and the dead NPC's faction-mates within
     * {@code relations.revengeRange} turn on the player for a while.
     */
    void recordKill(DamageSource source)
    {
        if (!this.npc.isFactionNpc() || !AncientEmpiresConfig.relationsEnabled || !(this.npc.level() instanceof ServerLevel level))
        {
            return;
        }
        Entity killer = source.getEntity();
        UUID player = null;
        if (killer instanceof Player killerPlayer)
        {
            if (!killerPlayer.isCreative() || AncientEmpiresConfig.relationsCountCreative)
            {
                player = killerPlayer.getUUID();
            }
        }
        else if (killer instanceof NpcEntity npc && npc.isOwnedNpc())
        {
            player = npc.getOwner();
        }
        if (player == null)
        {
            return;
        }
        String faction = this.npc.getFaction();
        NpcStandings standings = NpcStandings.get(level.getServer());
        boolean wasHostile = standings.hostile(player, faction);
        int now = standings.adjust(player, faction, NpcFactions.killChange(faction));
        Player online = level.getServer().getPlayerList().getPlayer(player);
        if (online != null && !wasHostile && now < 0)
        {
            online.displayClientMessage(Component.translatable("message.ancientempires.standing.hostile", NpcFactions.displayName(faction)), false);
        }
        double range = AncientEmpiresConfig.relationsRevengeRange;
        UUID avenged = player;
        for (NpcEntity mate : level.getEntitiesOfClass(NpcEntity.class, this.npc.getBoundingBox().inflate(range),
                other -> other != this.npc && other.isAlive() && faction.equals(other.getFaction())))
        {
            mate.getRelations().markRevenge(avenged);
        }
    }


    static boolean sameOwner(NpcEntity left, NpcEntity right)
    {
        UUID owner = left.getOwner();
        return owner != null && owner.equals(right.getOwner());
    }

    void save(CompoundTag tag)
    {
        if (!this.revenge.isEmpty())
        {
            ListTag grudges = new ListTag();
            for (Map.Entry<UUID, Long> entry : this.revenge.entrySet())
            {
                CompoundTag grudge = new CompoundTag();
                grudge.putUUID("Player", entry.getKey());
                grudge.putLong("Until", entry.getValue());
                grudges.add(grudge);
            }
            tag.put(REVENGE_KEY, grudges);
        }
    }

    void load(CompoundTag tag)
    {
        this.revenge.clear();
        for (Tag element : tag.getList(REVENGE_KEY, Tag.TAG_COMPOUND))
        {
            CompoundTag grudge = (CompoundTag) element;
            if (grudge.hasUUID("Player"))
            {
                this.revenge.put(grudge.getUUID("Player"), grudge.getLong("Until"));
            }
        }
    }
}
