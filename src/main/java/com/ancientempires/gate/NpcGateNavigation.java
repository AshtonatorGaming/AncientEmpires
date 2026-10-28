package com.ancientempires.gate;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.HashMap;
import java.util.Map;

/**
 * NPC ground navigation that, with {@code structures.npcUse}, plans through the
 * closed gates the NPC may open ({@link GateEntity#mayNpcUse}): their proxy
 * cells read as a wooden door, which vanilla walks through when the NPC opens
 * doors (so at night, with doors kept shut, gates stay shut too). The gate goal
 * opens them when the NPC gets there. Each gate is judged once per path.
 */
public class NpcGateNavigation extends GroundPathNavigation
{
    public NpcGateNavigation(Mob mob, Level level)
    {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes)
    {
        this.nodeEvaluator = new Evaluator((NpcEntity) this.mob);
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    private static final class Evaluator extends WalkNodeEvaluator
    {
        private final NpcEntity npc;
        private final Map<Long, Boolean> gates = new HashMap<>();

        private Evaluator(NpcEntity npc)
        {
            this.npc = npc;
        }

        @Override
        public void prepare(PathNavigationRegion region, Mob mob)
        {
            super.prepare(region, mob);
            this.gates.clear();
        }

        @Override
        public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z)
        {
            BlockPathTypes type = super.getBlockPathType(level, x, y, z);
            if (!AncientEmpiresConfig.gateNpcUse || !level.getBlockState(new BlockPos(x, y, z)).is(ModBlocks.GATE_PROXY.get()))
            {
                return type;
            }
            boolean usable = this.gates.computeIfAbsent(BlockPos.asLong(x, y, z), key ->
            {
                GateEntity gate = GateProxyBlock.gateAt(this.npc.level(), BlockPos.of(key));
                return gate != null && gate.mayNpcUse(this.npc);
            });
            return usable ? BlockPathTypes.DOOR_WOOD_CLOSED : type;
        }
    }
}
