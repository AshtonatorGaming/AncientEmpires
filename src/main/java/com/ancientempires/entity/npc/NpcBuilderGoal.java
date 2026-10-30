package com.ancientempires.entity.npc;

import com.ancientempires.block.StructureBuilderBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;

/**
 * A builder: a Worker-path NPC holding a shovel, at its owner's structure
 * builder (AW's builder worked by its workers). It stands by the builder
 * block and, whenever the store holds the next layer's materials, lays that
 * layer, {@code structures.builderLayerTicks} a layer.
 */
public final class NpcBuilderGoal extends NpcWorkGoal<StructureBuilderBlockEntity>
{
    private static final int BUILD = 0;

    public NpcBuilderGoal(NpcEntity npc)
    {
        super(npc, StructureBuilderBlockEntity.class);
    }

    @Override
    protected boolean isTool(ItemStack stack)
    {
        return stack.getItem() instanceof ShovelItem;
    }

    @Override
    protected boolean pickJob()
    {
        StructureBuilderBlockEntity builder = this.site;
        return builder != null && builder.canBuild() && this.assign(BUILD, builder.getBlockPos());
    }

    @Override
    protected int actionTicks(int kind, BlockPos target)
    {
        return AncientEmpiresConfig.builderLayerTicks;
    }

    @Override
    protected void onActionTick(int kind, BlockPos target, int tick)
    {
        if (tick % 20 == 0 && this.npc.level() instanceof ServerLevel level)
        {
            level.playSound(null, target, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.7F, 1.0F);
            level.sendParticles(ParticleTypes.CLOUD, target.getX() + 0.5D, target.getY() + 1.1D, target.getZ() + 0.5D, 2, 0.3D, 0.1D, 0.3D, 0.01D);
        }
    }

    @Override
    protected boolean act(ServerLevel level, int kind, BlockPos target)
    {
        StructureBuilderBlockEntity builder = this.site;
        if (builder != null && builder.buildLayer())
        {
            level.playSound(null, target, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
            this.npc.addXp(AncientEmpiresConfig.levelXpPerWork);
        }
        return false;
    }
}
