package com.ancientempires.entity.npc;

import com.ancientempires.block.KitchenBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A cook: a Worker-path NPC with a bowl, at its owner's kitchen. It stands at
 * the kitchen and smokes raw food from its store (smoke, sizzle, and a smoker's
 * time per item), and packs cooked food into food bundles at a discount over
 * the crafting recipe. It carries nothing; the kitchen's store is its pantry.
 */
public final class NpcCookGoal extends NpcWorkGoal<KitchenBlockEntity>
{
    private static final int COOK = 0;
    private static final int BUNDLE = 1;
    private static final int BUNDLE_TICKS = 60;

    public NpcCookGoal(NpcEntity npc)
    {
        super(npc, KitchenBlockEntity.class);
    }

    @Override
    protected boolean isTool(ItemStack stack)
    {
        return stack.is(Items.BOWL);
    }

    @Override
    protected boolean pickJob()
    {
        KitchenBlockEntity kitchen = this.site;
        if (kitchen == null)
        {
            return false;
        }
        if (kitchen.canCook())
        {
            return this.assign(COOK, kitchen.getBlockPos());
        }
        if (kitchen.canBundle())
        {
            return this.assign(BUNDLE, kitchen.getBlockPos());
        }
        return false;
    }

    @Override
    protected int actionTicks(int kind, BlockPos target)
    {
        return kind == COOK ? AncientEmpiresConfig.cookTicks : BUNDLE_TICKS;
    }

    @Override
    protected void onActionTick(int kind, BlockPos target, int tick)
    {
        if (kind == COOK && tick % 15 == 0 && this.npc.level() instanceof ServerLevel level)
        {
            level.sendParticles(ParticleTypes.SMOKE, target.getX() + 0.5D, target.getY() + 1.1D, target.getZ() + 0.5D, 3, 0.2D, 0.05D, 0.2D, 0.01D);
            if (tick % 45 == 0)
            {
                level.playSound(null, target, SoundEvents.SMOKER_SMOKE, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
        }
    }

    @Override
    protected boolean act(ServerLevel level, int kind, BlockPos target)
    {
        KitchenBlockEntity kitchen = this.site;
        if (kitchen == null)
        {
            return false;
        }
        if (kind == COOK && kitchen.cookOne())
        {
            level.playSound(null, target, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        else if (kind == BUNDLE && kitchen.bundleOne())
        {
            level.playSound(null, target, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return false;
    }
}
