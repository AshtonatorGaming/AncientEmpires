package com.ancientempires.entity.npc;

import com.ancientempires.block.TreeFarmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/**
 * A woodcutter: a Worker-path NPC with an axe, at its owner's tree farm. It
 * stands at a tree and chops it log by log from the top down, with a player's
 * chopping time, pocketing the wood; then it walks over the saplings, apples,
 * and sticks the decaying leaves drop and picks them up, replants saplings on
 * the farm's grid, and carries the rest back.
 */
public final class NpcWoodcutterGoal extends NpcWorkGoal<TreeFarmBlockEntity>
{
    private static final int FELL = 0;
    private static final int COLLECT = 1;
    private static final int PLANT = 2;
    private static final double PICKUP_RANGE = 1.5D;

    public NpcWoodcutterGoal(NpcEntity npc)
    {
        super(npc, TreeFarmBlockEntity.class);
    }

    @Override
    protected boolean isTool(ItemStack stack)
    {
        return stack.getItem() instanceof AxeItem;
    }

    @Override
    protected boolean isSupply(ItemStack stack)
    {
        return isSapling(stack) || isBoneMeal(stack);
    }

    /** A sapling that plants as a block. */
    public static boolean isSapling(ItemStack stack)
    {
        return stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem;
    }

    /** Fell, then gather what fell, then replant (fetching saplings if needed), then carry the wood home. */
    @Override
    protected boolean pickJob()
    {
        TreeFarmBlockEntity farm = this.site;
        if (farm == null)
        {
            return false;
        }
        if (this.tripToUnload(false))
        {
            return true;
        }
        int id = this.npc.getId();
        BlockPos at = this.npc.blockPosition();
        BlockPos tile = farm.claim(TreeFarmBlockEntity.Task.FELL, id, at);
        if (tile != null)
        {
            return this.assign(FELL, tile);
        }
        tile = farm.claim(TreeFarmBlockEntity.Task.COLLECT, id, at);
        if (tile != null)
        {
            return this.assign(COLLECT, tile);
        }
        if (farm.hasWork(TreeFarmBlockEntity.Task.PLANT))
        {
            if (this.slotOf(NpcWoodcutterGoal::isSapling) >= 0)
            {
                tile = farm.claim(TreeFarmBlockEntity.Task.PLANT, id, at);
                if (tile != null)
                {
                    return this.assign(PLANT, tile);
                }
            }
            else if (this.tripToFetch(NpcWoodcutterGoal::isSapling))
            {
                return true;
            }
        }
        return this.tripToUnload(true);
    }

    @Override
    protected boolean digs(int kind)
    {
        return kind == FELL;
    }

    @Override
    protected BlockPos digPos(int kind, BlockPos target)
    {
        BlockPos top = TreeFarmBlockEntity.topLog(this.npc.level(), target);
        return top == null ? target : top;
    }

    @Override
    protected int actionTicks(int kind, BlockPos target)
    {
        return kind == FELL ? this.digTicks(this.digPos(kind, target)) : super.actionTicks(kind, target);
    }

    @Override
    protected boolean act(ServerLevel level, int kind, BlockPos target)
    {
        return switch (kind)
        {
            case FELL -> this.chop(level, target);
            case COLLECT ->
            {
                this.collectAt(level, target, PICKUP_RANGE);
                yield false;
            }
            case PLANT ->
            {
                this.plant(level, target);
                yield false;
            }
            default -> false;
        };
    }

    /** Chops the top log of the tree on {@code base}. Returns true while logs remain, to keep going. */
    private boolean chop(ServerLevel level, BlockPos base)
    {
        BlockPos log = TreeFarmBlockEntity.topLog(level, base);
        if (log == null)
        {
            return false;
        }
        BlockState state = level.getBlockState(log);
        List<ItemStack> drops = Block.getDrops(state, level, log, level.getBlockEntity(log), this.npc, this.npc.getMainHandItem());
        level.destroyBlock(log, false, this.npc);
        this.wearTool();
        for (ItemStack drop : drops)
        {
            ItemStack rest = this.pocket(drop);
            if (!rest.isEmpty())
            {
                Block.popResource(level, base, rest);
            }
        }
        return !log.equals(base) && this.isTool(this.npc.getMainHandItem());
    }

    /** Sets a carried sapling on a grid tile. */
    private void plant(ServerLevel level, BlockPos pos)
    {
        int slot = this.slotOf(NpcWoodcutterGoal::isSapling);
        if (slot < 0 || !level.getBlockState(pos).isAir())
        {
            return;
        }
        ItemStack saplings = this.npc.getInventory().getItem(slot);
        BlockState sapling = ((BlockItem) saplings.getItem()).getBlock().defaultBlockState();
        if (!sapling.canSurvive(level, pos))
        {
            return;
        }
        level.setBlock(pos, sapling, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.npc, sapling));
        level.playSound(null, pos, sapling.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        saplings.shrink(1);
        this.npc.getInventory().setItem(slot, saplings);
        this.boneMeal(level, pos);
    }
}
