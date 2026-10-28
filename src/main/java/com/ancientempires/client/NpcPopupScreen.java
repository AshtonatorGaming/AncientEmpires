package com.ancientempires.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;

/**
 * A parchment panel over the NPC inventory: path chooser, stats sheet, skin
 * selector. It is a Forge gui layer, so the inventory stays open beneath it
 * (its menu, its stats sync) and is drawn under it. Escape or a click outside
 * the panel closes it back to the inventory.
 */
@OnlyIn(Dist.CLIENT)
abstract class NpcPopupScreen extends Screen
{
    private static final int VEIL = 0x50000000;

    protected final int panelWidth;
    protected final int panelHeight;
    protected int left;
    protected int top;

    protected NpcPopupScreen(Component title, int panelWidth, int panelHeight)
    {
        super(title);
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
    }

    /** Opens a popup over the current screen (the NPC inventory). */
    static void open(NpcPopupScreen popup)
    {
        ForgeHooksClient.pushGuiLayer(Minecraft.getInstance(), popup);
    }

    @Override
    protected void init()
    {
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        graphics.fill(0, 0, this.width, this.height, VEIL);
        NpcGui.bevel(graphics, this.left, this.top, this.panelWidth, this.panelHeight, NpcGui.PARCHMENT);
        this.renderPanel(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** The panel's own drawing, under its widgets. */
    protected abstract void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (super.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
        if (mouseX < this.left || mouseY < this.top || mouseX >= this.left + this.panelWidth || mouseY >= this.top + this.panelHeight)
        {
            this.onClose();
        }
        // Nothing under the panel takes the click.
        return true;
    }

    /** Back to the inventory, not out of it. */
    @Override
    public void onClose()
    {
        ForgeHooksClient.popGuiLayer(this.minecraft);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
