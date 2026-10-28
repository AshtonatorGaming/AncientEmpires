package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The parchment look shared by the NPC inventory and its popups: colors, the
 * gold-edged bevel every frame and slot uses, and the command icons.
 */
@OnlyIn(Dist.CLIENT)
final class NpcGui
{
    static final int PARCHMENT = 0xFF4A3B2A;
    static final int INK = 0xFF120E0C;
    static final int BUTTON = 0xFF3A2E24;
    static final int BUTTON_ON = 0xFF6A5430;
    static final int BUTTON_HOVER = 0xFF8A6A3C;
    static final int BUTTON_OFF = 0xFF2A221C;
    static final int NAME_INK = 0xFFE6D2A2;
    static final int NAME_HINT = 0xFF8A7050;
    static final int SLOT_FILL = 0xFF2C241C;
    static final int SLOT_HOVER = 0xFF3A2E24;
    static final int GOLD = 0xFFC6A56A;

    /**
     * One 16 px cell per icon, in {@link Icon} order. The glyphs are ModularUI's
     * (LGPL-3.0, brachy84/ModularUI-Modern), kept when the screen left that library.
     */
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "textures/gui/npc_icons.png");
    private static final int ICON_SHEET_W = 144;
    private static final int ICON_SHEET_H = 16;

    /** A glyph in the icon sheet: its cell and the part of it that is drawn. */
    enum Icon
    {
        FOLLOW(8, 16),
        SET_HOME(16, 16),
        CLOSE(10, 10),
        REPACK(16, 16),
        HOLD(16, 16),
        CREATIVE(16, 16),
        LEVY(16, 16),
        MOUNT(16, 8),
        SKIN(16, 16);

        private final int width;
        private final int height;

        Icon(int width, int height)
        {
            this.width = width;
            this.height = height;
        }
    }

    private NpcGui()
    {
    }

    /**
     * Fill and a 1 px gold stroke on the same rectangle. The cell below or to the
     * right owns a shared edge, so the join stays a single line.
     */
    static void bevel(GuiGraphics graphics, int x, int y, int width, int height, int fill, boolean neighborBottom, boolean neighborRight)
    {
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, GOLD);
        graphics.fill(x, y, x + 1, y + height, GOLD);
        if (!neighborBottom)
        {
            graphics.fill(x, y + height - 1, x + width, y + height, GOLD);
        }
        if (!neighborRight)
        {
            graphics.fill(x + width - 1, y, x + width, y + height, GOLD);
        }
    }

    static void bevel(GuiGraphics graphics, int x, int y, int width, int height, int fill)
    {
        bevel(graphics, x, y, width, height, fill, false, false);
    }

    /** An icon stretched to the box, as the old widgets drew them. */
    static void icon(GuiGraphics graphics, Icon icon, int x, int y, int width, int height)
    {
        RenderSystem.enableBlend();
        graphics.blit(ICONS, x, y, width, height, icon.ordinal() * 16.0F, 0.0F, icon.width, icon.height, ICON_SHEET_W, ICON_SHEET_H);
        RenderSystem.disableBlend();
    }

    /** An icon centered in a box. */
    static void iconCentered(GuiGraphics graphics, Icon icon, int x, int y, int boxWidth, int boxHeight, int width, int height)
    {
        icon(graphics, icon, x + (boxWidth - width) / 2, y + (boxHeight - height) / 2, width, height);
    }
}
