package com.ancientempires.entity.npc;

import com.ancientempires.block.QuarryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A miner: a Worker-path NPC with a pickaxe, at its owner's quarry. It walks
 * down the quarry's stair and digs the current layer one block at a time, with
 * a player's mining time and break cracks, standing on or beside the block;
 * the stone and ore go in its backpack, and it climbs back up to unload. A
 * block its pickaxe cannot harvest is left standing.
 */
public final class NpcMinerGoal extends NpcWorkGoal<QuarryBlockEntity>
{
    private static final int DIG = 0;

    public NpcMinerGoal(NpcEntity npc)
    {
        super(npc, QuarryBlockEntity.class);
    }

    @Override
    protected boolean isTool(ItemStack stack)
    {
        return stack.getItem() instanceof PickaxeItem;
    }

    @Override
    protected boolean pickJob()
    {
        QuarryBlockEntity quarry = this.site;
        if (quarry == null)
        {
            return false;
        }
        if (this.tripToUnload(false))
        {
            return true;
        }
        BlockPos block = quarry.finished() ? null : quarry.claim(this.npc.getId(), this.npc.blockPosition());
        if (block != null)
        {
            return this.assign(DIG, block);
        }
        return this.tripToUnload(true);
    }

    @Override
    protected boolean digs(int kind)
    {
        return true;
    }

    /** Stand on the block, or as near as the path gets. */
    @Override
    protected BlockPos walkTarget(int kind, BlockPos target)
    {
        return target.above();
    }

    @Override
    protected double reachSqr(int kind)
    {
        return 3.0D * 3.0D;
    }

    @Override
    protected int actionTicks(int kind, BlockPos target)
    {
        return this.digTicks(target);
    }

    @Override
    protected boolean act(ServerLevel level, int kind, BlockPos pos)
    {
        QuarryBlockEntity quarry = this.site;
        if (quarry == null || !QuarryBlockEntity.mineable(level, pos))
        {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        ItemStack tool = this.npc.getMainHandItem();
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state))
        {
            quarry.skip(pos);
            return false;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), this.npc, tool);
        level.destroyBlock(pos, false, this.npc);
        tool.hurtAndBreak(1, this.npc, npc -> npc.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        quarry.markDug();
        for (ItemStack drop : drops)
        {
            ItemStack rest = this.pocket(drop);
            if (!rest.isEmpty())
            {
                Block.popResource(level, pos, rest);
            }
        }
        return false;
    }
}
