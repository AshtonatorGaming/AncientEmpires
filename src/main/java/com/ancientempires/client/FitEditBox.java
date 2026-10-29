package com.ancientempires.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A text field that shows its whole value while not being typed in. Vanilla's
 * field shows only what fits from its scroll point, so a long name read as its
 * tail end, cut at the frame; here an unfocused field draws the value through
 * {@link FitText} (smaller, then an ellipsis) and only editing scrolls.
 */
@OnlyIn(Dist.CLIENT)
public class FitEditBox extends EditBox
{
    private final Font font;
    private int color = 0xFFE0E0E0;
    private boolean bordered = true;
    /** Vanilla's text shadow; off for ink on parchment, where it smears. */
    private boolean shadow = true;
    @Nullable
    private Component hint;

    public FitEditBox(Font font, int x, int y, int width, int height, Component message)
    {
        super(font, x, y, width, height, message);
        this.font = font;
    }

    @Override
    public void setTextColor(int color)
    {
        super.setTextColor(color);
        this.color = color;
    }

    /** Draws the text without vanilla's shadow, typing included. */
    public FitEditBox noShadow()
    {
        this.shadow = false;
        return this;
    }

    @Override
    public void setHint(Component hint)
    {
        super.setHint(hint);
        this.hint = hint;
    }

    @Override
    public void setBordered(boolean bordered)
    {
        super.setBordered(bordered);
        this.bordered = bordered;
    }

    @Override
    public void setFocused(boolean focused)
    {
        super.setFocused(focused);
        if (!focused)
        {
            // Editing starts from the front again, not from wherever the last edit scrolled to.
            this.moveCursorToStart();
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        if (!this.shadow)
        {
            this.renderPlain(graphics);
            return;
        }
        if (!this.isVisible() || this.isFocused() || this.getValue().isEmpty())
        {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            return;
        }
        if (this.bordered)
        {
            // Vanilla's frame, without its text.
            int outline = this.isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0;
            graphics.fill(this.getX() - 1, this.getY() - 1, this.getX() + this.width + 1, this.getY() + this.height + 1, outline);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF000000);
            FitText.draw(graphics, this.font, this.getValue(), this.getX() + 4, this.getY(), this.width - 8, this.height, this.color, false, true);
        }
        else
        {
            // Unbordered, vanilla draws the text at the box's top.
            FitText.draw(graphics, this.font, this.getValue(), this.getX(), this.getY(), this.width, 8, this.color, false, true);
        }
    }

    /**
     * The field drawn by hand, without shadows (unbordered only): the hint when
     * empty, the whole value fitted when not typed in, and while typing the
     * stretch around the cursor with a blinking caret.
     */
    private void renderPlain(GuiGraphics graphics)
    {
        if (!this.isVisible())
        {
            return;
        }
        String value = this.getValue();
        int x = this.getX();
        int y = this.getY();
        if (!this.isFocused())
        {
            if (value.isEmpty())
            {
                if (this.hint != null)
                {
                    FitText.draw(graphics, this.font, this.hint.getString(), x, y, this.width, 8, (this.color & 0xFFFFFF) | 0x80000000, false, false);
                }
                return;
            }
            FitText.draw(graphics, this.font, value, x, y, this.width, 8, this.color, false, false);
            return;
        }
        int cursor = Math.min(this.getCursorPosition(), value.length());
        int start = 0;
        while (start < cursor && this.font.width(value.substring(start, cursor)) > this.width - 2)
        {
            start++;
        }
        String visible = this.font.plainSubstrByWidth(value.substring(start), this.width);
        graphics.drawString(this.font, visible, x, y, this.color, false);
        if (Util.getMillis() / 300L % 2L == 0L)
        {
            int caret = x + this.font.width(value.substring(start, cursor));
            graphics.fill(caret, y - 1, caret + 1, y + 9, this.color);
        }
    }
}
