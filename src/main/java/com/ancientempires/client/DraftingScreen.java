package com.ancientempires.client;

import com.ancientempires.network.DraftingPacket;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.structure.AeBuilds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Drafting Station: the structures AW marked for survival building, by
 * category, with a search; pick one and draft its Blueprint.
 */
@OnlyIn(Dist.CLIENT)
public final class DraftingScreen extends Screen
{
    private static final int WIDTH = 260;
    private static final int ROW = 12;
    private static final int PAD = 8;
    private static final int TOP = 40;

    private final BlockPos station;
    private final List<AeBuilds.Entry> entries;
    private final List<AeBuilds.Entry> shown = new ArrayList<>();
    private EditBox search;
    private int scroll;
    private int picked = -1;

    private DraftingScreen(BlockPos station, List<AeBuilds.Entry> entries)
    {
        super(Component.translatable("block.ancientempires.drafting_station"));
        this.station = station;
        this.entries = entries;
    }

    public static void show(BlockPos station, List<AeBuilds.Entry> entries)
    {
        Minecraft.getInstance().setScreen(new DraftingScreen(station, entries));
    }

    private int left()
    {
        return (this.width - WIDTH) / 2;
    }

    private int panelHeight()
    {
        return Math.min(this.height - 20, 240);
    }

    private int top()
    {
        return (this.height - this.panelHeight()) / 2;
    }

    private int rows()
    {
        return Math.max(1, (this.panelHeight() - TOP - 30) / ROW);
    }

    @Override
    protected void init()
    {
        this.search = new EditBox(this.font, this.left() + PAD, this.top() + 20, WIDTH - PAD * 2, 14, Component.translatable("gui.ancientempires.drafting.search"));
        this.search.setResponder(text -> this.filter());
        this.addRenderableWidget(this.search);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.ancientempires.drafting.draft"), button -> this.draft())
                .bounds(this.left() + WIDTH - PAD - 70, this.top() + this.panelHeight() - 22, 70, 16).build());
        this.filter();
    }

    private void filter()
    {
        String text = this.search == null ? "" : this.search.getValue().toLowerCase(Locale.ROOT);
        AeBuilds.Entry kept = this.picked >= 0 && this.picked < this.shown.size() ? this.shown.get(this.picked) : null;
        this.shown.clear();
        for (AeBuilds.Entry entry : this.entries)
        {
            if (text.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(text) || entry.category().toLowerCase(Locale.ROOT).contains(text))
            {
                this.shown.add(entry);
            }
        }
        this.picked = kept == null ? -1 : this.shown.indexOf(kept);
        this.scroll = 0;
    }

    private void draft()
    {
        if (this.picked >= 0 && this.picked < this.shown.size())
        {
            ModNetwork.sendToServer(new DraftingPacket.Request(this.station, this.shown.get(this.picked).name()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        int left = this.left();
        int top = this.top();
        NpcGui.bevel(graphics, left, top, WIDTH, this.panelHeight(), NpcGui.PARCHMENT);
        FitText.draw(graphics, this.font, this.title.getString(), left + PAD, top + 6, WIDTH - PAD * 2, 10, NpcGui.GOLD, true, true);
        int rows = this.rows();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.shown.size() - rows));
        for (int i = 0; i < rows && this.scroll + i < this.shown.size(); i++)
        {
            AeBuilds.Entry entry = this.shown.get(this.scroll + i);
            int y = top + TOP + i * ROW;
            boolean over = mouseX >= left + 4 && mouseX < left + WIDTH - 4 && mouseY >= y && mouseY < y + ROW;
            if (this.scroll + i == this.picked)
            {
                graphics.fill(left + 4, y, left + WIDTH - 4, y + ROW, NpcGui.BUTTON_ON);
            }
            else if (over)
            {
                graphics.fill(left + 4, y, left + WIDTH - 4, y + ROW, NpcGui.BUTTON);
            }
            FitText.draw(graphics, this.font, entry.name(), left + PAD, y + 2, 140, 9, NpcGui.NAME_INK, false, false);
            String size = entry.sizeX() + "x" + entry.sizeY() + "x" + entry.sizeZ();
            FitText.draw(graphics, this.font, size, left + PAD + 144, y + 2, 44, 9, NpcGui.GOLD, false, false);
            FitText.draw(graphics, this.font, entry.category().replace("survival", ""), left + PAD + 192, y + 2, WIDTH - PAD * 2 - 192, 9, NpcGui.NAME_HINT, false, false);
        }
        if (this.shown.isEmpty())
        {
            FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.drafting.none").getString(), left + PAD, top + TOP, WIDTH - PAD * 2, ROW,
                    NpcGui.NAME_HINT, true, false);
        }
        FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.drafting.help").getString(), left + PAD, top + this.panelHeight() - 18,
                WIDTH - PAD * 2 - 76, 9, NpcGui.NAME_HINT, false, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int left = this.left();
        int row = (int) Math.floor((mouseY - this.top() - TOP) / ROW);
        if (button == 0 && row >= 0 && row < this.rows() && mouseX >= left + 4 && mouseX < left + WIDTH - 4 && this.scroll + row < this.shown.size())
        {
            this.picked = this.scroll + row;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        this.scroll -= (int) Math.signum(delta) * 3;
        return true;
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
