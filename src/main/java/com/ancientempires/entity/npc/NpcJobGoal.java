package com.ancientempires.entity.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    /**
     * Why this NPC is or is not working, one condition per line, for
     * {@code /ancientempires work}. Mirrors {@link NpcWorkGoal#mayWork}.
     */
    public static List<String> diagnose(NpcEntity npc)
    {
        NpcJobGoal probe = new NpcJobGoal(npc);
        NpcWorkGoal<?> trade = probe.tradeFor(npc.getMainHandItem());
        List<String> lines = new ArrayList<>();
        lines.add(check(npc.getNpcPath() == NpcPath.WORKER, "Worker path (now " + npc.getNpcPath().id() + ")"));
        lines.add(check(trade != null, "Trade tool in main hand (" + npc.getMainHandItem().getHoverName().getString()
                + (trade == null ? ": not a trade tool" : ": " + trade.getClass().getSimpleName().replace("Npc", "").replace("Goal", "")) + ")"));
        lines.add(check(npc.isOwnedNpc(), "Owned by a player"));
        lines.add(check(!npc.isFollowing(), "Not following"));
        lines.add(check(npc.getTarget() == null, "Not fighting"));
        lines.add(check(!npc.isPassenger(), "Not riding"));
        lines.add(check(!npc.isHungry(), "Not hungry"));
        lines.add(check(npc.level().isDay(), "Daytime"));
        lines.add(check(!npc.level().isRainingAt(npc.blockPosition().above()), "No rain on it"));
        BlockPos site = npc.getWorksite();
        lines.add(check(site != null, site == null
                ? "Worksite found (none yet; it searches its owner's worksites of its trade within work.searchRange of its home)"
                : "Worksite at " + site.toShortString()));
        return lines;
    }

    private static String check(boolean ok, String text)
    {
        return (ok ? "[ok] " : "[NO] ") + text;
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
