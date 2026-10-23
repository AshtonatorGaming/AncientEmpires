package com.ancientempires.entity.npc;

import com.ancientempires.block.QuarryBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A miner: a Worker-path NPC with a pickaxe, at its owner's quarry. It walks
 * down the quarry's stair and digs the current layer one block at a time, with
 * a player's mining time and break cracks, standing on or beside the block;
 * the stone and ore go in its backpack, and it climbs back up to unload. A
 * block its pickaxe cannot harvest is left standing. It keeps some cheap stone
 * ({@code ancientempires:stair_stone}) on hand, and with it first relays stair
 * steps that are not plain stone, so the way out stays solid.
 */
public final class NpcMinerGoal extends NpcWorkGoal<QuarryBlockEntity>
{
    /** Cheap stone a miner lays in the stair: cobblestone, sandstone, and the like, never smooth stone. */
    private static final TagKey<Item> STAIR_STONE = ItemTags.create(ResourceLocation.fromNamespaceAndPath("ancientempires", "stair_stone"));
    private static final int DIG = 0;
    private static final int STAIR = 1;

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
    protected boolean isSupply(ItemStack stack)
    {
        return AncientEmpiresConfig.quarryStairStone && stack.is(STAIR_STONE) && stack.getItem() instanceof BlockItem;
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
        if (AncientEmpiresConfig.quarryStairStone && !quarry.finished())
        {
            if (this.supplySlot() >= 0)
            {
                BlockPos step = quarry.claimStair(this.npc.getId(), this.npc.blockPosition());
                if (step != null)
                {
                    return this.assign(STAIR, step);
                }
            }
            else if (quarry.stairNeedsStone() && this.tripToFetch())
            {
                return true;
            }
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
        if (quarry == null)
        {
            return false;
        }
        if (kind == STAIR)
        {
            this.relay(level, quarry, pos);
            return false;
        }
        if (!QuarryBlockEntity.mineable(level, pos))
        {
            return false;
        }
        this.dig(level, quarry, pos);
        return false;
    }

    /** Breaks a block into the backpack. False when the pickaxe cannot harvest it (it is skipped). */
    private boolean dig(ServerLevel level, QuarryBlockEntity quarry, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        ItemStack tool = this.npc.getMainHandItem();
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state))
        {
            quarry.skip(pos);
            return false;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), this.npc, tool);
        level.destroyBlock(pos, false, this.npc);
        this.wearTool();
        quarry.markDug();
        for (ItemStack drop : drops)
        {
            ItemStack rest = this.pocket(drop);
            if (!rest.isEmpty())
            {
                Block.popResource(level, pos, rest);
            }
        }
        return true;
    }

    /** Digs out a stair step that is not plain stone (keeping what it was) and lays stone in its place. */
    private void relay(ServerLevel level, QuarryBlockEntity quarry, BlockPos pos)
    {
        int slot = this.supplySlot();
        BlockState state = level.getBlockState(pos);
        if (slot < 0 || state.is(QuarryBlockEntity.STAIR_STONE))
        {
            return;
        }
        if (!state.canBeReplaced() && !this.dig(level, quarry, pos))
        {
            return;
        }
        ItemStack stone = this.npc.getInventory().getItem(slot);
        if (!(stone.getItem() instanceof BlockItem item))
        {
            return;
        }
        BlockState laid = item.getBlock().defaultBlockState();
        level.setBlockAndUpdate(pos, laid);
        SoundType sound = laid.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        stone.shrink(1);
        this.npc.getInventory().setItem(slot, stone);
    }
}
