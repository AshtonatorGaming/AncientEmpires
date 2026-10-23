package com.ancientempires.entity.npc;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Every hands-on trade as one goal. The goal selector asks each idle goal
 * whether it can run about ten times a second; registering the trades
 * separately put seven of those checks on every NPC. This asks once: the tool
 * in the main hand picks the trade, and only that trade is consulted.
 */
public final class NpcJobGoal extends Goal
{
    private final NpcEntity npc;
    private final List<NpcWorkGoal<?>> trades;
    @Nullable
    private NpcWorkGoal<?> active;

    public NpcJobGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.trades = List.of(new NpcFarmerGoal(npc), new NpcWoodcutterGoal(npc), new NpcMinerGoal(npc), new NpcRancherGoal(npc),
                new NpcHunterGoal(npc), new NpcFisherGoal(npc), new NpcCookGoal(npc));
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        if (this.npc.getNpcPath() != NpcPath.WORKER)
        {
            return false;
        }
        NpcWorkGoal<?> trade = this.tradeFor(this.npc.getMainHandItem());
        if (trade == null || !trade.canUse())
        {
            return false;
        }
        this.active = trade;
        return true;
    }

    @Nullable
    private NpcWorkGoal<?> tradeFor(ItemStack tool)
    {
        for (NpcWorkGoal<?> trade : this.trades)
        {
            if (trade.isTrade(tool))
            {
                return trade;
            }
        }
        return null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.active != null && this.active.canContinueToUse();
    }

    @Override
    public void start()
    {
        if (this.active != null)
        {
            this.active.start();
        }
    }

    @Override
    public void stop()
    {
        if (this.active != null)
        {
            this.active.stop();
        }
        this.active = null;
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        if (this.active != null)
        {
            this.active.tick();
        }
    }
}
