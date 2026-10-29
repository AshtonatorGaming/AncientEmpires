package com.ancientempires.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
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
}
