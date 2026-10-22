package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcFactions;
import com.ancientempires.network.StandingsPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The player's standing with every faction, under each faction's banner
 * (Ancient Warfare's own banner art), best first: green at peace, red hostile.
 * Hovering a banner gives the exact standing. Scrolls when the window is short.
 */
@OnlyIn(Dist.CLIENT)
public final class StandingsScreen extends Screen
{
    private static final int CELL_W = 40;
    private static final int CELL_H = 62;
    private static final int FLAG_W = 20;
    private static final int FLAG_H = 40;
    private static final int TOP = 32;
    private static final int PANEL = 0xC0101010;
    private static final int GOLD = 0xFFC6A56A;

    private final List<StandingsPacket.Entry> entries;
    private int scroll;

    private StandingsScreen(List<StandingsPacket.Entry> entries)
    {
        super(Component.translatable("gui.ancientempires.standings.title"));
        List<StandingsPacket.Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt((StandingsPacket.Entry entry) -> -entry.standing()).thenComparing(StandingsPacket.Entry::faction));
        this.entries = sorted;
    }

    public static void open(List<StandingsPacket.Entry> entries)
    {
        Minecraft.getInstance().setScreen(new StandingsScreen(entries));
    }

    private int columns()
    {
        return Math.max(1, Math.min(11, (this.width - 20) / CELL_W));
    }

    private int rows()
    {
        return (this.entries.size() + this.columns() - 1) / this.columns();
    }

    private int visibleRows()
    {
        return Math.max(1, (this.height - TOP - 12) / CELL_H);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        int columns = this.columns();
        int gridW = columns * CELL_W;
        int left = (this.width - gridW) / 2;
        int shownRows = Math.min(this.visibleRows(), this.rows());
        graphics.fill(left - 6, TOP - 6, left + gridW + 6, TOP + shownRows * CELL_H + 4, PANEL);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, GOLD);
        StandingsPacket.Entry hovered = null;
        for (int index = 0; index < this.entries.size(); index++)
        {
            int row = index / columns - this.scroll;
            if (row < 0 || row >= shownRows)
            {
                continue;
            }
            StandingsPacket.Entry entry = this.entries.get(index);
            int x = left + (index % columns) * CELL_W;
            int y = TOP + row * CELL_H;
            int flagX = x + (CELL_W - FLAG_W) / 2;
            boolean hostile = entry.standing() < 0;
            if (hostile)
            {
                graphics.fill(flagX - 1, y - 1, flagX + FLAG_W + 1, y + FLAG_H + 1, 0xFF7A1C1C);
            }
            graphics.blit(banner(entry.faction()), flagX, y, FLAG_W, FLAG_H, 1.0F, 1.0F, FLAG_W, FLAG_H, 64, 64);
            Component name = NpcFactions.displayName(entry.faction());
            String text = this.font.plainSubstrByWidth(name.getString(), CELL_W - 2);
            graphics.drawCenteredString(this.font, text, x + CELL_W / 2, y + FLAG_H + 2, 0xFFE6D2A2);
            graphics.drawCenteredString(this.font, Integer.toString(entry.standing()), x + CELL_W / 2, y + FLAG_H + 11,
                    hostile ? 0xFFFF6060 : 0xFF70E070);
            if (mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H)
            {
                hovered = entry;
            }
        }
        if (this.rows() > shownRows)
        {
            graphics.drawCenteredString(this.font, Component.translatable("gui.ancientempires.standings.scroll"),
                    this.width / 2, TOP + shownRows * CELL_H + 6, 0xFF8A7050);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null)
        {
            boolean hostile = hovered.standing() < 0;
            graphics.renderTooltip(this.font, List.of(
                    NpcFactions.displayName(hovered.faction()).copy().withStyle(ChatFormatting.GOLD),
                    Component.translatable(hostile ? "gui.ancientempires.standings.hostile" : "gui.ancientempires.standings.peace",
                            hovered.standing()).withStyle(hostile ? ChatFormatting.RED : ChatFormatting.GREEN)),
                    java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        int max = Math.max(0, this.rows() - this.visibleRows());
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(delta), 0, max);
        return true;
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private static ResourceLocation banner(String faction)
    {
        return ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "textures/gui/banner/" + faction + ".png");
    }
}
