package com.ancientempires.entity.npc;

import com.ancientempires.block.FruitFarmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/**
 * A fruit picker: a Worker-path NPC with a hoe at its owner's fruit farm (the
 * hoe's second trade: a farmer with no crop farm, or whose Work Order lists a
 * fruit farm, picks fruit). It picks ripe fruit into its backpack as AW's fruit
 * farm did: a cocoa pod is broken and a bean set back in its place, a berry
 * bush and glow berries are picked and left growing, a melon or pumpkin is cut
 * from its stem. It sets carried cocoa beans on bare jungle logs, and carries
 * the harvest back.
 */
public final class NpcFruitPickerGoal extends NpcWorkGoal<FruitFarmBlockEntity>
{
    private static final int PICK = 0;
    private static final int PLANT = 1;
    /** Fruit hangs high: picked from this far, squared (a tall reach). */
    private static final double PICK_REACH_SQR = 4.5D * 4.5D;

    public NpcFruitPickerGoal(NpcEntity npc)
    {
        super(npc, FruitFarmBlockEntity.class);
    }

    @Override
    protected boolean isTool(ItemStack stack)
    {
        return stack.getItem() instanceof HoeItem;
    }

    @Override
    protected boolean isSupply(ItemStack stack)
    {
        return stack.is(Items.COCOA_BEANS);
    }

    @Override
    protected double reachSqr(int kind)
    {
        return PICK_REACH_SQR;
    }

    /** Pick first, then set beans (fetching some if the farm has them), then carry the harvest home. */
    @Override
    protected boolean pickJob()
    {
        FruitFarmBlockEntity farm = this.site;
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
        BlockPos tile = farm.claim(FruitFarmBlockEntity.Task.PICK, id, at);
        if (tile != null)
        {
            return this.assign(PICK, tile);
        }
        if (farm.hasWork(FruitFarmBlockEntity.Task.PLANT))
        {
            if (this.slotOf(stack -> stack.is(Items.COCOA_BEANS)) >= 0)
            {
                tile = farm.claim(FruitFarmBlockEntity.Task.PLANT, id, at);
                if (tile != null)
                {
                    return this.assign(PLANT, tile);
                }
            }
            else if (this.tripToFetch(stack -> stack.is(Items.COCOA_BEANS)))
            {
                return true;
            }
        }
        return this.tripToUnload(true);
    }

    @Override
    protected boolean act(ServerLevel level, int kind, BlockPos pos)
    {
        if (kind == PICK)
        {
            this.pick(level, pos);
        }
        else if (kind == PLANT)
        {
            this.plant(level, pos);
        }
        return false;
    }

    private void pick(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (!FruitFarmBlockEntity.isRipe(state))
        {
            return;
        }
        if (state.getBlock() instanceof SweetBerryBushBlock)
        {
            // As a player picks: two or three berries, the bush back to its first berries' stage.
            this.give(level, pos, new ItemStack(Items.SWEET_BERRIES, 2 + level.getRandom().nextInt(2)));
            level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(this.npc, state));
            return;
        }
        if (state.getBlock() instanceof CaveVines)
        {
            this.give(level, pos, new ItemStack(Items.GLOW_BERRIES));
            level.setBlock(pos, state.setValue(CaveVines.BERRIES, false), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(this.npc, state));
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), this.npc, this.npc.getMainHandItem());
        level.destroyBlock(pos, false, this.npc);
        boolean replant = state.getBlock() instanceof CocoaBlock;
        for (ItemStack drop : drops)
        {
            if (replant && drop.is(Items.COCOA_BEANS))
            {
                // AW's cocoa: one bean of the pod goes straight back on the log.
                level.setBlock(pos, state.setValue(CocoaBlock.AGE, 0), Block.UPDATE_ALL);
                drop.shrink(1);
                replant = false;
            }
            this.give(level, pos, drop);
        }
    }

    /** Pockets what it picked; what does not fit falls. */
    private void give(ServerLevel level, BlockPos pos, ItemStack stack)
    {
        ItemStack rest = this.pocket(stack);
        if (!rest.isEmpty())
        {
            Block.popResource(level, pos, rest);
        }
    }

    /** Sets a carried cocoa bean on a jungle log's bare side. */
    private void plant(ServerLevel level, BlockPos pos)
    {
        int slot = this.slotOf(stack -> stack.is(Items.COCOA_BEANS));
        Direction facing = FruitFarmBlockEntity.cocoaFacing(level, pos);
        if (slot < 0 || facing == null || !level.getBlockState(pos).isAir())
        {
            return;
        }
        BlockState pod = Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, facing);
        level.setBlock(pos, pod, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.npc, pod));
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        ItemStack beans = this.npc.getInventory().getItem(slot);
        beans.shrink(1);
        this.npc.getInventory().setItem(slot, beans);
    }
}
