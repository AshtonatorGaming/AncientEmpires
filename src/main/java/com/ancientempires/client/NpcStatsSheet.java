package com.ancientempires.client;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcMorale;
import com.ancientempires.entity.npc.NpcRank;
import com.ancientempires.inventory.NpcMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * The NPC's stats panel, drawn in one pass each frame from synced values: rank
 * and level with an XP bar, then for soldiers morale (a bar, its state, and up
 * to three reasons) and guard, then upkeep. Everything a player needs to see
 * why an NPC did what it did, in one place. A popup over the inventory, whose
 * menu keeps syncing the stats beneath it.
 */
@OnlyIn(Dist.CLIENT)
final class NpcStatsSheet extends NpcPopupScreen
{
    static final int WIDTH = 188;
    static final int HEIGHT = 132;
    private static final int INK = 0xFFE6D2A2;
    private static final int DIM = 0xFFA08860;
    private static final int GOLD = 0xFFC6A56A;
    private static final int TRACK = 0xFF2C241C;
    private static final int XP = 0xFF7FBF4F;
    private static final int GUARD = 0xFF8FA6C6;
    private static final int LINE = 11;
    private static final int MAX_REASONS = 3;

    private final NpcMenu menu;
    private final NpcEntity npc;

    NpcStatsSheet(NpcMenu menu, NpcEntity npc)
    {
        super(npc.getDisplayName(), WIDTH, HEIGHT);
        this.menu = menu;
        this.npc = npc;
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        Font font = this.font;
        int left = this.left + 6;
        int inner = WIDTH - 12;
        int line = this.top + 6;
        Component title = this.npc.getRank() != NpcRank.SOLDIER
                ? Component.translatable("gui.ancientempires.stats.title_rank", this.npc.getDisplayName(), this.npc.getRankName())
                : this.npc.getDisplayName();
        FitText.draw(graphics, font, title.getString(), left, line, inner, 10, GOLD, true, true);
        line += LINE + 2;
        if (this.npc.levels())
        {
            double need = this.npc.getXpToNext();
            String band = Component.translatable(NpcEntity.bandKey(this.npc.getNpcPath(), this.npc.getLevelBand())).getString();
            String level = need > 0.0D
                    ? Component.translatable("gui.ancientempires.stats.level", band, this.npc.getLevel(), (int) this.npc.getXp(), (int) Math.ceil(need)).getString()
                    : Component.translatable("gui.ancientempires.stats.level_max", band, this.npc.getLevel()).getString();
            FitText.draw(graphics, font, level, left, line, inner, 10, INK, false, true);
            line += LINE;
            bar(graphics, left, line, inner, need > 0.0D ? this.npc.getXp() / need : 1.0D, XP);
            line += 6;
            if (!this.npc.getNpcPath().combat())
            {
                int faster = (int) Math.round((this.npc.getWorkSpeed() - 1.0D) * 100.0D);
                FitText.draw(graphics, font, Component.translatable("gui.ancientempires.stats.work", faster).getString(),
                        left, line, inner, 10, DIM, false, true);
                line += LINE;
            }
        }
        int morale = this.menu.stat(NpcMenu.STAT_MORALE);
        if (morale >= 0)
        {
            NpcMorale.State state = NpcMorale.State.values()[Math.min(this.menu.stat(NpcMenu.STAT_MORALE_STATE), NpcMorale.State.values().length - 1)];
            String label = Component.translatable("gui.ancientempires.stats.morale",
                    Component.translatable("gui.ancientempires.morale." + state.name().toLowerCase(Locale.ROOT)), morale / 10).getString();
            FitText.draw(graphics, font, label, left, line, inner, 10, INK, false, true);
            line += LINE;
            bar(graphics, left, line, inner, morale / 1000.0D, moraleColor(state));
            mark(graphics, left, line, inner, AncientEmpiresConfig.moraleWaverBelow / 100.0D);
            mark(graphics, left, line, inner, AncientEmpiresConfig.moraleRoutBelow / 100.0D);
            line += 6;
            int reasons = this.menu.stat(NpcMenu.STAT_MORALE_REASONS);
            int shown = 0;
            for (int reason = 0; reason < NpcMorale.REASON_COUNT && shown < MAX_REASONS; reason++)
            {
                if ((reasons & 1 << reason) == 0)
                {
                    continue;
                }
                boolean good = reason == NpcMorale.REASON_OUTNUMBERING || reason == NpcMorale.REASON_HEARTENED
                        || reason == NpcMorale.REASON_INSPIRED || reason == NpcMorale.REASON_OWNER || reason == NpcMorale.REASON_BANNER
                        || reason == NpcMorale.REASON_WATCH || reason == NpcMorale.REASON_FORMED || reason == NpcMorale.REASON_HOME;
                FitText.draw(graphics, font, (good ? "+ " : "- ") + Component.translatable("gui.ancientempires.morale.reason." + reason).getString(),
                        left + 4, line, inner - 4, 10, good ? 0xFF9FD07F : 0xFFE08A70, false, true);
                line += LINE - 1;
                shown++;
            }
            if (shown == 0)
            {
                FitText.draw(graphics, font, Component.translatable("gui.ancientempires.morale.reason.none").getString(),
                        left + 4, line, inner - 4, 10, DIM, false, true);
                line += LINE - 1;
            }
            line += 2;
        }
        int guard = this.menu.stat(NpcMenu.STAT_GUARD);
        int guardMax = this.menu.stat(NpcMenu.STAT_GUARD_MAX);
        if (guard >= 0 && guardMax > 0)
        {
            FitText.draw(graphics, font, Component.translatable("gui.ancientempires.stats.guard", guard / 10, guardMax / 10).getString(),
                    left, line, inner, 10, INK, false, true);
            line += LINE;
            bar(graphics, left, line, inner, guard / (double) guardMax, GUARD);
            line += 6;
        }
        if (this.npc.isOwnedNpc())
        {
            boolean hungry = this.menu.stat(NpcMenu.STAT_HUNGRY) != 0;
            FitText.draw(graphics, font, Component.translatable(hungry ? "gui.ancientempires.stats.hungry" : "gui.ancientempires.stats.fed").getString(),
                    left, line, inner, 10, hungry ? 0xFFE08A70 : DIM, false, true);
        }
    }

    private static void bar(GuiGraphics graphics, int x, int y, int width, double fraction, int color)
    {
        graphics.fill(x, y, x + width, y + 4, TRACK);
        int filled = (int) Math.round(width * Math.max(0.0D, Math.min(1.0D, fraction)));
        if (filled > 0)
        {
            graphics.fill(x, y, x + filled, y + 4, color);
        }
    }

    /** A tick on a bar at a threshold (waver, rout). */
    private static void mark(GuiGraphics graphics, int x, int y, int width, double fraction)
    {
        int at = x + (int) Math.round(width * Math.max(0.0D, Math.min(1.0D, fraction)));
        graphics.fill(at, y - 1, at + 1, y + 5, 0xFF120E0C);
    }

    private static int moraleColor(NpcMorale.State state)
    {
        return switch (state)
        {
            case STEADY -> 0xFF7FBF4F;
            case WAVERING -> 0xFFE0B040;
            case ROUTING -> 0xFFE07030;
            case SHATTERED, SURRENDERED -> 0xFFB03A30;
        };
    }
}
