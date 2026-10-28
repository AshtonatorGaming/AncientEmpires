package com.ancientempires.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A flat parchment button: a fill that lightens on hover, a face drawn over it,
 * and a tooltip read each frame. Unlike vanilla buttons it hears every mouse
 * button and passes which one, so the role label can tell left from right.
 */
@OnlyIn(Dist.CLIENT)
final class NpcButton extends AbstractWidget
{
    @FunctionalInterface
    interface Face
    {
        void draw(GuiGraphics graphics, int x, int y, int width, int height);
    }

    @FunctionalInterface
    interface Press
    {
        /** True when the click was taken. */
        boolean press(int button);
    }

    private final Face face;
    private final Press press;
    private IntSupplier fill = () -> NpcGui.BUTTON;
    /** A dead button keeps its place and look but takes no hover color or clicks. */
    private BooleanSupplier live = () -> true;
    private Supplier<List<Component>> tooltip;
    /** Tooltip wrap width, 0 for one line per component. */
    private int tooltipWidth;

    NpcButton(int x, int y, int width, int height, Component narration, Face face, Press press)
    {
        super(x, y, width, height, narration);
        this.face = face;
        this.press = press;
    }

    NpcButton fill(IntSupplier fill)
    {
        this.fill = fill;
        return this;
    }

    NpcButton live(BooleanSupplier live)
    {
        this.live = live;
        return this;
    }

    NpcButton tooltip(Supplier<List<Component>> tooltip, int wrapWidth)
    {
        this.tooltip = tooltip;
        this.tooltipWidth = wrapWidth;
        return this;
    }

    NpcButton tooltip(Component line)
    {
        List<Component> lines = List.of(line);
        return this.tooltip(() -> lines, 0);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        boolean live = this.live.getAsBoolean();
        int color = live && this.isHovered() ? NpcGui.BUTTON_HOVER : this.fill.getAsInt();
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, color);
        this.face.draw(graphics, this.getX(), this.getY(), this.width, this.height);
        if (this.isHovered() && this.tooltip != null)
        {
            this.showTooltip();
        }
    }

    private void showTooltip()
    {
        Screen screen = Minecraft.getInstance().screen;
        List<Component> lines = this.tooltip.get();
        if (screen == null || lines.isEmpty())
        {
            return;
        }
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> shown = new ArrayList<>();
        for (Component line : lines)
        {
            if (this.tooltipWidth > 0)
            {
                shown.addAll(font.split(line, this.tooltipWidth));
            }
            else
            {
                shown.add(line.getVisualOrderText());
            }
        }
        screen.setTooltipForNextRenderPass(shown);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (!this.active || !this.visible || !this.clicked(mouseX, mouseY) || !this.live.getAsBoolean())
        {
            return false;
        }
        if (!this.press.press(button))
        {
            return false;
        }
        this.playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output)
    {
        this.defaultButtonNarrationText(output);
    }
}
