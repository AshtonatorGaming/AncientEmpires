package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.inventory.TradeOrderMenu;
import com.ancientempires.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The Trade Order's goods editor, parchment like the NPC screens: the
 * customer's price on the left, the trader's goods on the right, page buttons
 * under them, and the player's inventory to pick items from.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class TradeOrderScreen extends AbstractContainerScreen<TradeOrderMenu>
{
    private static final int RIM_LIGHT = 0xFF6A5640;
    private static final int RIM_DARK = 0xFF2A2018;
    private static final int SLOT_SHADE = 0xFF1A1410;
    private static final int SLOT_EDGE = 0xFF5A4834;
    private static final int DIM = 0xFFA09070;

    public TradeOrderScreen(TradeOrderMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.imageWidth = TradeOrderMenu.WIDTH;
        this.imageHeight = TradeOrderMenu.HEIGHT;
        this.inventoryLabelX = TradeOrderMenu.PLAYER_X;
        this.inventoryLabelY = TradeOrderMenu.PLAYER_Y - 11;
    }

    @SubscribeEvent
    public static void register(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.TRADE_ORDER.get(), TradeOrderScreen::new));
    }

    @Override
    protected void init()
    {
        super.init();
        int y = this.topPos + TradeOrderMenu.BUTTONS_Y;
        int left = this.leftPos + 8;
        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.press(TradeOrderMenu.PREVIOUS))
                .bounds(left, y, 16, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.press(TradeOrderMenu.NEXT))
                .bounds(left + 74, y, 16, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.ancientempires.trade_order.add"), button -> this.press(TradeOrderMenu.ADD))
                .bounds(left + 96, y, 34, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.ancientempires.trade_order.add.tooltip"))).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.ancientempires.trade_order.remove"), button -> this.press(TradeOrderMenu.REMOVE))
                .bounds(left + 132, y, 28, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.ancientempires.trade_order.remove.tooltip"))).build());
    }

    private void press(int id)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null)
        {
            minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, NpcGui.PARCHMENT);
        graphics.fill(left, top, left + this.imageWidth, top + 1, RIM_LIGHT);
        graphics.fill(left, top, left + 1, top + this.imageHeight, RIM_LIGHT);
        graphics.fill(left, top + this.imageHeight - 1, left + this.imageWidth, top + this.imageHeight, RIM_DARK);
        graphics.fill(left + this.imageWidth - 1, top, left + this.imageWidth, top + this.imageHeight, RIM_DARK);
        for (int index = 0; index < this.menu.slots.size(); index++)
        {
            Slot slot = this.menu.slots.get(index);
            int x = left + slot.x;
            int y = top + slot.y;
            // The goods' slots wear the gold trim of the NPC screen's equipment.
            graphics.fill(x - 1, y - 1, x + 17, y + 17, index < TradeOrderMenu.GHOSTS ? NpcGui.GOLD : SLOT_EDGE);
            graphics.fill(x - 1, y - 1, x + 16, y + 16, SLOT_SHADE);
            graphics.fill(x, y, x + 16, y + 16, NpcGui.SLOT_FILL);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        FitText.draw(graphics, this.font, this.title.getString(), 8, 6, this.imageWidth - 16, 9, NpcGui.GOLD, false, false);
        FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.trade_order.pays").getString(),
                TradeOrderMenu.IN_X - 2, 19, 58, 9, NpcGui.NAME_INK, true, false);
        FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.trade_order.gives").getString(),
                TradeOrderMenu.OUT_X - 2, 19, 58, 9, NpcGui.NAME_INK, true, false);
        graphics.drawString(this.font, "→", (TradeOrderMenu.IN_X + 54 + TradeOrderMenu.OUT_X) / 2 - 3, TradeOrderMenu.GRID_Y + 22, NpcGui.GOLD, false);
        String page = Component.translatable("gui.ancientempires.trade_order.page", this.menu.page() + 1, this.menu.pages()).getString();
        FitText.draw(graphics, this.font, page, 26, TradeOrderMenu.BUTTONS_Y, 54, 16, NpcGui.GOLD, true, false);
        FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.trade_order.help").getString(),
                8, TradeOrderMenu.BUTTONS_Y + 19, this.imageWidth - 16, 9, DIM, true, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, NpcGui.NAME_INK, false);
    }
}
