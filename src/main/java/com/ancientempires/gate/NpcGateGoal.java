package com.ancientempires.gate;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * With {@code structures.npcUse}: an NPC opens the gate on its path just ahead,
 * if it may ({@link GateEntity#mayNpcUse}), and closes the gates it opened
 * once it is past them and nobody stands in the opening, as the door goal does
 * for doors. No flags, so it runs beside whatever moves the NPC.
 */
public class NpcGateGoal extends Goal
{
    private static final double REACH = 3.0D;
    private static final double PAST = 4.0D;
    private static final int LOOK_AHEAD = 3;

    private final NpcEntity npc;
    private final List<GateEntity> opened = new ArrayList<>();

    public NpcGateGoal(NpcEntity npc)
    {
        this.npc = npc;
    }

    @Override
    public boolean canUse()
    {
        return AncientEmpiresConfig.gateNpcUse && this.npc.isOwnedNpc() && this.npc.tickCount % 5 == 0 && this.gateAhead() != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return !this.opened.isEmpty();
    }

    @Override
    public void start()
    {
        this.openAhead();
    }

    @Override
    public void stop()
    {
        this.opened.clear();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return false;
    }

    @Override
    public void tick()
    {
        if (this.npc.tickCount % 5 == 0)
        {
            this.openAhead();
        }
        this.opened.removeIf(gate ->
        {
            if (gate.isRemoved())
            {
                return true;
            }
            if (this.npc.distanceToSqr(gate.position()) < PAST * PAST)
            {
                return false;
            }
            BlockPos min = gate.min();
            BlockPos max = gate.max();
            AABB opening = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
            if (!this.npc.level().getEntitiesOfClass(LivingEntity.class, opening, LivingEntity::isAlive).isEmpty())
            {
                return false;
            }
            gate.close();
            return true;
        });
    }

    private void openAhead()
    {
        GateEntity gate = this.gateAhead();
        if (gate != null && gate.mayNpcUse(this.npc))
        {
            gate.open();
            if (!this.opened.contains(gate))
            {
                this.opened.add(gate);
            }
        }
    }

    /** The closed gate within reach on the next few steps of the NPC's path, or null. */
    private GateEntity gateAhead()
    {
        Path path = this.npc.getNavigation().getPath();
        if (path == null || path.isDone())
        {
            return null;
        }
        int end = Math.min(path.getNodeCount(), path.getNextNodeIndex() + LOOK_AHEAD);
        for (int index = path.getNextNodeIndex(); index < end; index++)
        {
            Node node = path.getNode(index);
            BlockPos pos = node.asBlockPos();
            if (this.npc.distanceToSqr(pos.getCenter()) > REACH * REACH)
            {
                break;
            }
            for (BlockPos cell : new BlockPos[] {pos, pos.above()})
            {
                if (this.npc.level().getBlockState(cell).is(ModBlocks.GATE_PROXY.get()))
                {
                    return GateProxyBlock.gateAt(this.npc.level(), cell);
                }
            }
        }
        return null;
    }
}
