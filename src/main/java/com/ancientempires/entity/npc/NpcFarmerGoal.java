package com.ancientempires.entity.npc;

import com.ancientempires.block.CropFarmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.PlantType;

import java.util.List;

/**
 * A farmer: a Worker-path NPC with a hoe, at its owner's crop farm. It
 * harvests ripe crops into its backpack, plants seeds it carries, and tills
 * bare soil, swinging its hoe at each; it fetches seeds from the farm when it
 * has none, and carries the harvest back.
 */
public final class NpcFarmerGoal extends NpcWorkGoal<CropFarmBlockEntity>
{
    private static final int HARVEST = 0;
    private static final int PLANT = 1;
    private static final int TILL = 2;

    public NpcFarmerGoal(NpcEntity npc)
    {
        super(npc, CropFarmBlockEntity.class);
    }

    @Override
    protected boolean isTool(ItemStack stack)
    {
        return stack.getItem() instanceof HoeItem;
    }

    @Override
    protected boolean isSupply(ItemStack stack)
    {
        return isSeed(stack);
    }

    /** Harvest first, then plant (fetching seeds if needed), then till, then carry the harvest home. */
    @Override
    protected boolean pickJob()
    {
        CropFarmBlockEntity farm = this.site;
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
        BlockPos tile = farm.claim(CropFarmBlockEntity.Task.HARVEST, id, at);
        if (tile != null)
        {
            return this.assign(HARVEST, tile);
        }
        if (farm.hasWork(CropFarmBlockEntity.Task.PLANT))
        {
            if (this.supplySlot() >= 0)
            {
                tile = farm.claim(CropFarmBlockEntity.Task.PLANT, id, at);
                if (tile != null)
                {
                    return this.assign(PLANT, tile);
                }
            }
            else if (this.tripToFetch())
            {
                return true;
            }
        }
        tile = farm.claim(CropFarmBlockEntity.Task.TILL, id, at);
        if (tile != null)
        {
            return this.assign(TILL, tile);
        }
        return this.tripToUnload(true);
    }

    @Override
    protected boolean act(ServerLevel level, int kind, BlockPos pos)
    {
        switch (kind)
        {
            case HARVEST -> this.harvest(level, pos);
            case PLANT -> this.plant(level, pos);
            case TILL -> this.till(level, pos);
            default ->
            {
            }
        }
        return false;
    }

    /** Breaks a ripe crop (or a pumpkin or melon) and pockets the drops; what does not fit falls. */
    private void harvest(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (!CropFarmBlockEntity.isRipe(state) && !state.is(Blocks.PUMPKIN) && !state.is(Blocks.MELON))
        {
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), this.npc, this.npc.getMainHandItem());
        level.destroyBlock(pos, false, this.npc);
        for (ItemStack drop : drops)
        {
            ItemStack rest = this.pocket(drop);
            if (!rest.isEmpty())
            {
                Block.popResource(level, pos, rest);
            }
        }
    }

    /** Sets a carried seed on bare farmland. */
    private void plant(ServerLevel level, BlockPos pos)
    {
        int slot = this.supplySlot();
        if (slot < 0 || !level.getBlockState(pos).isAir())
        {
            return;
        }
        ItemStack seeds = this.npc.getInventory().getItem(slot);
        BlockState crop = ((BlockItem) seeds.getItem()).getBlock().defaultBlockState();
        if (!crop.canSurvive(level, pos))
        {
            return;
        }
        level.setBlock(pos, crop, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.npc, crop));
        level.playSound(null, pos, crop.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        seeds.shrink(1);
        this.npc.getInventory().setItem(slot, seeds);
    }

    /** Turns grass or dirt into farmland, as a hoe would. */
    private void till(ServerLevel level, BlockPos pos)
    {
        if (!level.getBlockState(pos.above()).isAir() || !CropFarmBlockEntity.isTillable(level.getBlockState(pos)))
        {
            return;
        }
        BlockState farmland = Blocks.FARMLAND.defaultBlockState();
        level.setBlock(pos, farmland, Block.UPDATE_ALL_IMMEDIATE);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(this.npc, farmland));
        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.npc.getMainHandItem().hurtAndBreak(1, this.npc, npc -> npc.broadcastBreakEvent(InteractionHand.MAIN_HAND));
    }

    /** Anything that plants as a crop: wheat, carrot, potato, beetroot, pumpkin and melon seeds, modded crops. */
    static boolean isSeed(ItemStack stack)
    {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof IPlantable plant))
        {
            return false;
        }
        try
        {
            // Vanilla answers from the block alone; a modded plant that reads the world is not a seed here.
            return plant.getPlantType(null, BlockPos.ZERO) == PlantType.CROP;
        }
        catch (RuntimeException exception)
        {
            return false;
        }
    }
}
