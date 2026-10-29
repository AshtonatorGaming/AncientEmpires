package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcArmy;
import com.ancientempires.network.ArmyPacket;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.network.UnitOrderPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Mount & Blade's battle orders, for the chain of command: hold the order key
 * (V) and this wheel opens around the crosshair, the view frozen where it was
 * aimed. Number keys 1 to 9 pick which units take the order (0: all; the
 * choice is remembered), the mouse points at an order, and a click or letting
 * go of the key gives it. Escape, or letting go in the middle, gives nothing.
 */
@OnlyIn(Dist.CLIENT)
public final class CommandWheelScreen extends Screen
{
    private static final int RADIUS = 72;
    private static final int DEAD_ZONE = 22;
    private static final int GOLD = 0xFFC6A56A;
    private static final NpcArmy.Command[] ORDERS = NpcArmy.Command.values();

    /** The units picked last time; empty for all. */
    private static final Set<Integer> SELECTED = new TreeSet<>();

    private int hovered = -1;

    private CommandWheelScreen()
    {
        super(Component.translatable("gui.ancientempires.army.wheel"));
    }

    public static void open()
    {
        Minecraft.getInstance().setScreen(new CommandWheelScreen());
        // A fresh list of units for the side panel; the last one shows meanwhile.
        ModNetwork.requestArmyRefresh();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.hovered = this.pick(mouseX, mouseY);
        for (int i = 0; i < ORDERS.length; i++)
        {
            double angle = Math.PI * 2.0D * i / ORDERS.length - Math.PI / 2.0D;
            int x = cx + (int) Math.round(Math.cos(angle) * RADIUS);
            int y = cy + (int) Math.round(Math.sin(angle) * RADIUS);
            Component label = Component.translatable("gui.ancientempires.army.command." + ORDERS[i].name().toLowerCase(Locale.ROOT));
            int w = this.font.width(label) + 10;
            boolean on = i == this.hovered;
            graphics.fill(x - w / 2, y - 8, x + w / 2, y + 8, on ? 0xD0705A30 : 0xB0101010);
            graphics.drawCenteredString(this.font, label, x, y - 4, on ? 0xFFFFFFFF : GOLD);
        }
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        this.drawUnits(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** The player's numbered units down the left, those taking the order marked. */
    private void drawUnits(GuiGraphics graphics)
    {
        TreeMap<Integer, ArmyPacket.Entry> units = new TreeMap<>();
        for (ArmyPacket.Entry entry : ClientArmy.latest())
        {
            if (entry.unitNumber() > 0 && entry.members() > 0)
            {
                units.putIfAbsent(entry.unitNumber(), entry);
            }
        }
        int x = 10;
        int y = this.height / 2 - (units.size() + 2) * 6;
        graphics.drawString(this.font, Component.translatable(SELECTED.isEmpty() ? "gui.ancientempires.army.all_selected" : "gui.ancientempires.army.some_selected"),
                x, y, GOLD);
        y += 14;
        if (units.isEmpty())
        {
            graphics.drawString(this.font, Component.translatable("gui.ancientempires.army.no_units_yet"), x, y, 0xFFA0A0A0);
        }
        for (var unit : units.entrySet())
        {
            boolean on = SELECTED.isEmpty() || SELECTED.contains(unit.getKey());
            ArmyPacket.Entry entry = unit.getValue();
            String name = entry.unitName().isEmpty() ? CommandScreen.parse(entry.name()).getString() : entry.unitName();
            String line = unit.getKey() + "  " + name + " (" + entry.members() + ")";
            graphics.drawString(this.font, line, x, y, on ? 0xFFFFFFFF : 0xFF707070);
            y += 12;
        }
    }

    private int pick(double mouseX, double mouseY)
    {
        double dx = mouseX - this.width / 2.0D;
        double dy = mouseY - this.height / 2.0D;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE)
        {
            return -1;
        }
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0D;
        int slice = (int) Math.round(angle / (Math.PI * 2.0D) * ORDERS.length);
        return Math.floorMod(slice, ORDERS.length);
    }

    private void give()
    {
        if (this.hovered >= 0)
        {
            ModNetwork.sendToServer(new UnitOrderPacket(new TreeSet<>(SELECTED), ORDERS[this.hovered]));
        }
        this.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        this.hovered = this.pick(mouseX, mouseY);
        this.give();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers)
    {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9)
        {
            int unit = key - GLFW.GLFW_KEY_0;
            if (!SELECTED.remove(unit))
            {
                SELECTED.add(unit);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_GRAVE_ACCENT)
        {
            SELECTED.clear();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers)
    {
        if (ModKeys.ORDERS.matches(key, scanCode))
        {
            this.give();
            return true;
        }
        return super.keyReleased(key, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics)
    {
        // No darkening: the battlefield stays in view.
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
