package com.ancientempires.structure;

import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.server.level.ServerPlayer;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.network.OutlinePacket;
import com.ancientempires.AncientEmpires;
import com.ancientempires.block.FlagBlockEntity;
import com.ancientempires.block.NpcSpawnerBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcPath;
import com.ancientempires.entity.npc.NpcRoster;
import com.ancientempires.entity.npc.NpcStandings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * AW's conquest, on {@link StructureRecords}. A faction structure is held
 * until a player claims its protection flag; until then its blocks mine
 * {@code protectionMultiplier} times slower and its containers stay shut while
 * the faction can see you (stolen otherwise). A claim needs the hostiles still
 * inside to score under {@code claimThreshold}: each soldier-path NPC of the
 * structure's faction or hostile to the player counts (ordinary, elite,
 * leader), and each hostile spawner still holding people. A failed claim makes
 * what stands in the way glow; a won one costs standing with the faction.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class StructureClaims
{
    private static final double SIGHT_RANGE = 24.0D;
    private static final int NAMED_SPAWNERS = 3;

    private StructureClaims()
    {
    }

    /** The unclaimed faction structure a block is in (its containers locked), or null. */
    public static StructureRecords.Record held(ServerLevel level, BlockPos pos)
    {
        StructureRecords.Record record = StructureRecords.get(level).at(pos);
        return record != null && !record.claimed() && !record.faction.isEmpty() ? record : null;
    }

    public static void tryClaim(ServerLevel level, BlockPos flagPos, FlagBlockEntity flag, Player player)
    {
        StructureRecords records = StructureRecords.get(level);
        StructureRecords.Record found = records.byId(flag.record());
        if (found == null && flag.record() == 0)
        {
            // World generation records a structure after placing its flag, so the flag finds it by position once.
            found = records.at(flagPos);
            if (found != null)
            {
                flag.setProtection(true, found.id);
                records.tieFlag(found, flagPos);
            }
        }
        StructureRecords.Record record = found;
        if (record == null)
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.flag.guards_nothing"), true);
            return;
        }
        if (record.claimed())
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.flag.claimed_by", record.claimedName), true);
            return;
        }
        AABB box = AABB.of(record.bounds);
        List<NpcEntity> holders = level.getEntitiesOfClass(NpcEntity.class, box, npc -> holds(npc, record, player));
        int score = 0;
        for (NpcEntity npc : holders)
        {
            score += points(npc);
        }
        List<BlockPos> spawners = new ArrayList<>();
        for (int cx = record.bounds.minX() >> 4; cx <= record.bounds.maxX() >> 4; cx++)
        {
            for (int cz = record.bounds.minZ() >> 4; cz <= record.bounds.maxZ() >> 4; cz++)
            {
                for (BlockEntity blockEntity : level.getChunk(cx, cz).getBlockEntities().values())
                {
                    if (blockEntity instanceof NpcSpawnerBlockEntity spawner && spawner.blocksClaim() && record.bounds.isInside(spawner.getBlockPos()))
                    {
                        spawners.add(spawner.getBlockPos());
                    }
                }
            }
        }
        score += spawners.size() * AncientEmpiresConfig.claimSpawnerPoints;
        if (score >= AncientEmpiresConfig.claimThreshold)
        {
            // Mark what stands in the way, as AW did.
            for (NpcEntity npc : holders)
            {
                npc.addEffect(new MobEffectInstance(MobEffects.GLOWING, AncientEmpiresConfig.claimWitnessTicks, 0, false, false));
            }
            if (player instanceof ServerPlayer server)
            {
                ModNetwork.outline(server, spawners, OutlinePacket.Kind.SPAWNER);
            }
            player.sendSystemMessage(Component.translatable("message.ancientempires.flag.held", holders.size(), spawners.size()));
            for (BlockPos spawner : spawners.subList(0, Math.min(NAMED_SPAWNERS, spawners.size())))
            {
                player.sendSystemMessage(Component.translatable("message.ancientempires.flag.spawner_at", spawner.getX(), spawner.getY(), spawner.getZ()));
            }
            return;
        }
        records.claim(record, player.getUUID(), player.getGameProfile().getName());
        flag.claim(player.getUUID(), player.getGameProfile().getName());
        if (AncientEmpiresConfig.claimStandingCost > 0)
        {
            NpcStandings.get(level.getServer()).adjust(player.getUUID(), record.faction, -AncientEmpiresConfig.claimStandingCost);
        }
        player.sendSystemMessage(Component.translatable("message.ancientempires.flag.conquered", record.faction));
    }

    /** One of the structure's defenders: a soldier of its faction, or hostile to this player, alive and not surrendered. */
    private static boolean holds(NpcEntity npc, StructureRecords.Record record, Player player)
    {
        if (!npc.isAlive() || npc.isOwnedNpc() || npc.getNpcPath() != NpcPath.SOLDIER || npc.getMorale().isSurrendered())
        {
            return false;
        }
        return npc.getFaction().equals(record.faction)
                || NpcStandings.get(player.getServer()).hostile(player.getUUID(), npc.getFaction());
    }

    private static int points(NpcEntity npc)
    {
        NpcRoster.Entry entry = npc.rosterEntry();
        String type = entry == null ? "" : entry.subtype();
        if (type.startsWith("leader"))
        {
            return AncientEmpiresConfig.claimLeaderPoints;
        }
        return type.endsWith(".elite") ? AncientEmpiresConfig.claimElitePoints : AncientEmpiresConfig.claimNormalPoints;
    }

    /** AW's block protection: an unclaimed faction structure's blocks are slow to mine. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        Player player = event.getEntity();
        if (AncientEmpiresConfig.protectionMultiplier <= 1.0D || player.isCreative() || !(player.level() instanceof ServerLevel level)
                || event.getPosition().isEmpty())
        {
            return;
        }
        if (held(level, event.getPosition().get()) != null)
        {
            event.setNewSpeed((float) (event.getNewSpeed() / AncientEmpiresConfig.protectionMultiplier));
        }
    }

    /**
     * AW's loot protection: an unclaimed faction structure's containers stay
     * shut while any of the faction can see you; unseen, you may steal.
     * Before {@link AwLoot}, so a locked chest's traps wait too.
     */
    /**
     * A structure's protection flag. World generation records a structure
     * without one (it is placed a chunk at a time), so the first time it is
     * asked for, the flag is looked for in the structure's loaded chunks and
     * tied to the record.
     */
    @Nullable
    private static BlockPos flagOf(ServerLevel level, StructureRecords.Record record)
    {
        if (record.flag != null)
        {
            return record.flag;
        }
        BoundingBox box = record.bounds;
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
        {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
            {
                if (!level.hasChunk(cx, cz))
                {
                    continue;
                }
                for (BlockEntity blockEntity : level.getChunk(cx, cz).getBlockEntities().values())
                {
                    if (blockEntity instanceof FlagBlockEntity flag && flag.isProtection() && box.isInside(blockEntity.getBlockPos()))
                    {
                        StructureRecords.get(level).tieFlag(record, blockEntity.getBlockPos());
                        return record.flag;
                    }
                }
            }
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event)
    {
        Player player = event.getEntity();
        if (!AncientEmpiresConfig.lootLock || player.isCreative() || player.isSpectator() || !(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(event.getPos());
        if (!(blockEntity instanceof Container))
        {
            return;
        }
        StructureRecords.Record record = held(level, event.getPos());
        if (record == null)
        {
            return;
        }
        boolean seen = !level.getEntitiesOfClass(NpcEntity.class, player.getBoundingBox().inflate(SIGHT_RANGE),
                npc -> npc.isAlive() && npc.getFaction().equals(record.faction) && !npc.getMorale().isSurrendered() && npc.hasLineOfSight(player)).isEmpty();
        // Point out the banner to take, through the walls.
        BlockPos flag = flagOf(level, record);
        if (flag != null && player instanceof ServerPlayer server)
        {
            ModNetwork.outline(server, List.of(flag), OutlinePacket.Kind.FLAG);
        }
        if (AncientEmpiresConfig.allowStealing && !seen)
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.flag.stealing"), true);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        player.displayClientMessage(flag == null ? Component.translatable("message.ancientempires.flag.locked")
                : Component.translatable("message.ancientempires.flag.locked_at", flag.getX(), flag.getY(), flag.getZ()), true);
    }
}
