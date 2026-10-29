package com.ancientempires.entity.npc;

import com.ancientempires.block.CraftingStationBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A craftsman: a Worker-path NPC holding a crafting table, at its owner's
 * crafting station (AW's auto crafting, by hand). It stands there and crafts
 * the station's recipe from its store, {@code work.craftTicks} a craft. It
 * carries nothing; the station's store is its bench.
 */
public final class NpcCraftsmanGoal extends NpcWorkGoal<CraftingStationBlockEntity>
{
    private static final int CRAFT = 0;

    public NpcCraftsmanGoal(NpcEntity npc)
    {
        super(npc, CraftingStationBlockEntity.class);
    }

    @Override
    protected boolean isTool(ItemStack stack)
    {
        return stack.is(Items.CRAFTING_TABLE);
    }

    @Override
    protected boolean pickJob()
    {
        CraftingStationBlockEntity station = this.site;
        return station != null && station.canCraft() && this.assign(CRAFT, station.getBlockPos());
    }

    @Override
    protected int actionTicks(int kind, BlockPos target)
    {
        return AncientEmpiresConfig.craftTicks;
    }

    @Override
    protected boolean act(ServerLevel level, int kind, BlockPos target)
    {
        CraftingStationBlockEntity station = this.site;
        if (station != null && station.craftOne())
        {
            level.playSound(null, target, SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.BLOCKS, 0.8F, 1.0F);
            this.npc.addXp(AncientEmpiresConfig.levelXpPerWork);
        }
        return false;
    }
}
