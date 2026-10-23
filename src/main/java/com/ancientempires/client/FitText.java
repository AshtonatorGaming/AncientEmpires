package com.ancientempires.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One line of text drawn to fit a width. A line that is too wide is first
 * drawn smaller (down to {@link #MIN_SCALE}), so long names stay whole; only
 * past that is it cut, with an ellipsis so the cut shows.
 */
@OnlyIn(Dist.CLIENT)
public final class FitText
{
    /** Smallest scale still readable with the vanilla font. */
    private static final float MIN_SCALE = 0.6F;
    private static final String ELLIPSIS = "...";

    private FitText()
    {
    }

    /** Whether {@code text} fits {@code width} at full size. */
    public static boolean fits(Font font, String text, int width)
    {
        return font.width(text) <= width;
    }

    /**
     * Draws {@code text} in a box at ({@code x}, {@code y}) of {@code width}
     * by {@code height}, vertically centered, horizontally centered or from the
     * left edge.
     */
    public static void draw(GuiGraphics graphics, Font font, String text, int x, int y, int width, int height, int color,
            boolean center, boolean shadow)
    {
        int max = Math.max(1, width);
        int full = font.width(text);
        float scale = full <= max ? 1.0F : Math.max(MIN_SCALE, (float) max / full);
        String line = text;
        if (full * scale > max)
        {
            int room = (int) (max / scale) - font.width(ELLIPSIS);
            line = font.plainSubstrByWidth(text, Math.max(0, room)) + ELLIPSIS;
        }
        float lineW = font.width(line) * scale;
        float textX = center ? x + (width - lineW) / 2.0F : x;
        float textY = y + (height - 8.0F * scale) / 2.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, line, 0, 0, color, shadow);
        graphics.pose().popPose();
    }
}
