package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.structure.StructureRecords;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Faction garrisons under the chain of command (docs/chain-of-command.md,
 * phase 5). A garrison spawner's people come out as one unit: the best of
 * them (its captain or general, else the highest level, an acting sergeant)
 * leads, and the unit Defends its spawner from its posts ({@link NpcChain#posted}):
 * the structure's builder placed them, so each holds its own spot and fights
 * from there within the defend radius, no formation. They are still one unit
 * for succession, morale, and the sally. A garrison soldier inside its own
 * faction's unclaimed structure fights on home ground (a morale boost). When
 * the structure's commander (a captain or above) falls or is badly hurt, every
 * garrison unit there sallies out: off its posts and at whoever struck the
 * commander (Attack; once that one falls the unit holds there, in formation),
 * or Free to hunt the invaders if there is nobody to name.
 */
public final class NpcGarrison
{
    private NpcGarrison()
    {
    }

    /** The people a garrison spawner just sent out form one unit, defending the spawner. */
    public static void form(ServerLevel level, List<NpcEntity> people, BlockPos spawner, @Nullable Player toward)
    {
        if (!AncientEmpiresConfig.chainEnabled || !AncientEmpiresConfig.chainGarrisons || people.size() < 2)
        {
            return;
        }
        NpcEntity leader = null;
        for (NpcEntity npc : people)
        {
            if (npc.getNpcPath().combat() && (leader == null || NpcChain.better(npc, leader)))
            {
                leader = npc;
            }
        }
        if (leader == null)
        {
            return;
        }
        for (NpcEntity npc : people)
        {
            if (npc != leader && npc.getNpcPath().combat())
            {
                NpcChain.link(leader, npc);
            }
        }
        // Facing whoever set it off; with nobody, the way the leader looks.
        float facing = leader.getYRot();
        if (toward != null)
        {
            facing = (float) (Mth.atan2(toward.getZ() - spawner.getZ() - 0.5D, toward.getX() - spawner.getX() - 0.5D) * Mth.RAD_TO_DEG) - 90.0F;
        }
        leader.getChain().setOrder(NpcOrder.DEFEND, spawner, facing, null);
        leader.getChain().setPosted(true);
    }

    /**
     * Whether a faction NPC stands on home ground: a garrison soldier inside
     * an unclaimed structure of its own faction.
     */
    static boolean onHomeGround(NpcEntity npc)
    {
        if (!npc.isFactionNpc() || !npc.isGarrison() || !(npc.level() instanceof ServerLevel level))
        {
            return false;
        }
        StructureRecords.Record record = StructureRecords.get(level).at(npc.blockPosition());
        return record != null && !record.claimed() && record.faction.equals(npc.getFaction());
    }

    /**
     * The structure's commander has fallen or is badly hurt: every garrison
     * unit of its faction in the structure stops defending and goes for the
     * invaders. Once per structure, as each unit's Defend becomes Free.
     */
    static void sallyOut(NpcEntity commander, @Nullable Entity attacker)
    {
        if (!AncientEmpiresConfig.chainEnabled || !commander.isFactionNpc() || !commander.isGarrison()
                || !(commander.level() instanceof ServerLevel level))
        {
            return;
        }
        StructureRecords.Record record = StructureRecords.get(level).at(commander.blockPosition());
        AABB area = record != null ? AABB.of(record.bounds).inflate(4.0D)
                : commander.getBoundingBox().inflate(AncientEmpiresConfig.chainDefendRadius);
        String faction = commander.getFaction();
        for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, area,
                other -> other.isAlive() && other.isGarrison() && faction.equals(other.getFaction())))
        {
            NpcChain chain = npc.getChain();
            if (chain.holdsOrder() && chain.order() == NpcOrder.DEFEND)
            {
                if (attacker instanceof LivingEntity enemy && enemy.isAlive() && npc.getRelations().scanTarget(enemy))
                {
                    chain.setOrder(NpcOrder.ATTACK, null, Float.NaN, enemy.getUUID());
                }
                else
                {
                    chain.setOrder(NpcOrder.FREE, null, Float.NaN, null);
                }
            }
        }
    }

    /** Whether this NPC's state calls the sally: a garrison captain or above, dead or under {@code garrisonSallyHealth}. */
    static boolean callsSally(NpcEntity npc, boolean dying)
    {
        return npc.isGarrison() && npc.isFactionNpc() && npc.getRank().ordinal() >= NpcRank.CAPTAIN.ordinal()
                && (dying || npc.getHealth() < npc.getMaxHealth() * AncientEmpiresConfig.chainGarrisonSallyHealth);
    }
}
