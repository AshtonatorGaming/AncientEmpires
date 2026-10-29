package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcTrades;
import com.ancientempires.item.Coins;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.network.TradePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * A trader's goods (AW's faction trade screen): one row per trade, what the
 * player gives, an arrow, what it gets, and the uses left. A row the player
 * can pay for and the trader has in stock lights up; clicking it trades.
 */
@OnlyIn(Dist.CLIENT)
public final class TradeScreen extends Screen
{
    private static final int ROW = 22;
    private static final int CELL = 18;
    private static final int PAD = 8;
    private static final int TOP = 22;
    private static final int STOCK_W = 34;

    private final int entityId;
    private List<NpcTrades.View> trades;
    private int scroll;

    private TradeScreen(int entityId, Component title, List<NpcTrades.View> trades)
    {
        super(title);
        this.entityId = entityId;
        this.trades = trades;
    }

    /** Opens the screen, or refreshes it if it is open for this trader. */
    public static void show(int entityId, boolean open, List<NpcTrades.View> trades)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TradeScreen screen && screen.entityId == entityId)
        {
            screen.trades = trades;
            return;
        }
        if (open)
        {
            Entity trader = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
            Component title = trader != null ? trader.getDisplayName() : Component.translatable("gui.ancientempires.trade.title");
            minecraft.setScreen(new TradeScreen(entityId, title, trades));
        }
    }

    private int inputCells()
    {
        int most = 1;
        for (NpcTrades.View trade : this.trades)
        {
            most = Math.max(most, trade.input().size());
        }
        return most;
    }

    private int outputCells()
    {
        int most = 1;
        for (NpcTrades.View trade : this.trades)
        {
            most = Math.max(most, trade.output().size());
        }
        return most;
    }

    private int panelWidth()
    {
        return Math.min(this.width - 16, PAD * 2 + (this.inputCells() + this.outputCells()) * CELL + 24 + STOCK_W);
    }

    private int visibleRows()
    {
        return Math.max(1, Math.min(this.trades.size(), (this.height - 40 - TOP - PAD) / ROW));
    }

    private int left()
    {
        return (this.width - this.panelWidth()) / 2;
    }

    private int top()
    {
        return (this.height - (TOP + this.visibleRows() * ROW + PAD)) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        int left = this.left();
        int top = this.top();
        int width = this.panelWidth();
        int rows = this.visibleRows();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.trades.size() - rows));
        NpcGui.bevel(graphics, left, top, width, TOP + rows * ROW + PAD, NpcGui.PARCHMENT);
        FitText.draw(graphics, this.font, this.title.getString(), left + PAD, top + 6, width - PAD * 2, 10, NpcGui.GOLD, true, true);
        Player viewer = Minecraft.getInstance().player;
        if (viewer != null)
        {
            // The player's money (purses and loose coins), under the panel.
            Component money = Component.translatable("gui.ancientempires.trade.money", Coins.describe(Coins.wealth(viewer)));
            FitText.draw(graphics, this.font, money.getString(), left + PAD, top + TOP + rows * ROW + PAD + 4, width - PAD * 2, 10, NpcGui.NAME_INK, true, true);
        }
        if (this.trades.isEmpty())
        {
            FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.trade.none").getString(), left + PAD, top + TOP, width - PAD * 2, ROW,
                    NpcGui.NAME_HINT, true, false);
            return;
        }
        ItemStack hovered = ItemStack.EMPTY;
        int arrowX = left + PAD + this.inputCells() * CELL + 4;
        int outputX = arrowX + 20;
        for (int i = 0; i < rows; i++)
        {
            NpcTrades.View trade = this.trades.get(this.scroll + i);
            int y = top + TOP + i * ROW;
            boolean ready = trade.available() > 0 && this.canPay(trade);
            boolean over = mouseX >= left + 4 && mouseX < left + width - 4 && mouseY >= y && mouseY < y + ROW - 2;
            int fill = ready ? (over ? NpcGui.BUTTON_HOVER : NpcGui.BUTTON_ON) : NpcGui.BUTTON_OFF;
            graphics.fill(left + 4, y, left + width - 4, y + ROW - 2, fill);
            hovered = this.items(graphics, trade.input(), left + PAD, y + 2, mouseX, mouseY, hovered);
            graphics.drawString(this.font, "→", arrowX + 4, y + 6, ready ? NpcGui.GOLD : NpcGui.NAME_HINT, false);
            hovered = this.items(graphics, trade.output(), outputX, y + 2, mouseX, mouseY, hovered);
            String stock = trade.available() + "/" + trade.max();
            FitText.draw(graphics, this.font, stock, left + width - PAD - STOCK_W, y, STOCK_W, ROW - 2, trade.available() > 0 ? NpcGui.NAME_INK : NpcGui.NAME_HINT,
                    true, false);
        }
        if (!hovered.isEmpty())
        {
            graphics.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
    }

    private ItemStack items(GuiGraphics graphics, List<ItemStack> stacks, int x, int y, int mouseX, int mouseY, ItemStack hovered)
    {
        for (int i = 0; i < stacks.size(); i++)
        {
            ItemStack stack = stacks.get(i);
            int cellX = x + i * CELL;
            graphics.fill(cellX, y, cellX + 16, y + 16, NpcGui.SLOT_FILL);
            graphics.renderItem(stack, cellX, y);
            graphics.renderItemDecorations(this.font, stack, cellX, y);
            if (mouseX >= cellX && mouseX < cellX + 16 && mouseY >= y && mouseY < y + 16)
            {
                hovered = stack;
            }
        }
        return hovered;
    }

    /** Whether the player carries everything the trade asks (the server checks again). */
    private boolean canPay(NpcTrades.View trade)
    {
        Player player = Minecraft.getInstance().player;
        return player != null && NpcTrades.canPay(player, trade.input());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int left = this.left();
        int top = this.top();
        int row = (int) Math.floor((mouseY - top - TOP) / ROW);
        if (button == 0 && row >= 0 && row < this.visibleRows() && mouseX >= left + 4 && mouseX < left + this.panelWidth() - 4)
        {
            NpcTrades.View trade = this.trades.get(this.scroll + row);
            if (trade.available() > 0 && this.canPay(trade))
            {
                ModNetwork.sendToServer(new TradePacket.Request(this.entityId, this.scroll + row));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        this.scroll -= (int) Math.signum(delta);
        return true;
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
