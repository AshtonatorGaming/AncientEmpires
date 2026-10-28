package com.ancientempires.client;

import com.ancientempires.network.ModNetwork;
import com.ancientempires.network.ScannerSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** Client screens opened from items (kept apart so servers never load them). */
public final class ClientScreens
{
    private ClientScreens()
    {
    }

    public static void openScanner(InteractionHand hand)
    {
        Minecraft.getInstance().setScreen(new ScannerScreen(hand));
    }

    /** Names a scan and saves it: the name is the template's file name (lower case, letters, digits, underscores). */
    private static final class ScannerScreen extends Screen
    {
        private final InteractionHand hand;
        private EditBox name;

        ScannerScreen(InteractionHand hand)
        {
            super(Component.translatable("screen.ancientempires.scanner"));
            this.hand = hand;
        }

        @Override
        protected void init()
        {
            int x = this.width / 2 - 100;
            int y = this.height / 2 - 20;
            this.name = new EditBox(this.font, x, y, 200, 20, Component.translatable("screen.ancientempires.scanner.name"));
            this.name.setMaxLength(64);
            this.addRenderableWidget(this.name);
            this.setInitialFocus(this.name);
            this.addRenderableWidget(Button.builder(Component.translatable("screen.ancientempires.scanner.save"), button -> this.save())
                    .bounds(x, y + 28, 98, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                    .bounds(x + 102, y + 28, 98, 20).build());
        }

        private void save()
        {
            if (!this.name.getValue().isBlank())
            {
                ModNetwork.sendToServer(new ScannerSavePacket(this.hand, this.name.getValue()));
                this.onClose();
            }
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers)
        {
            if (key == 257 || key == 335)
            {
                this.save();
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            this.renderBackground(graphics);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen()
        {
            return false;
        }
    }
}
