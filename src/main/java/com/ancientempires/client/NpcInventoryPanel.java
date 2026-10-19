package com.ancientempires.client;

import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.EntityDrawable;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.drawable.Icon;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.value.StringValue;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.Widget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcInventory;
import com.ancientempires.inventory.NpcLayout;
import com.ancientempires.inventory.NpcMenu;
import com.ancientempires.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Parchment panel for the NPC inventory. Frames are nine-slices and filled
 * rectangles, so the layout does not depend on a hand-authored texture.
 * Positions are gui pixels. {@link ModularPanel} centers itself, and a gui-scale
 * change runs the screen resize again.
 */
@OnlyIn(Dist.CLIENT)
public final class NpcInventoryPanel
{
    private static final int SLOT = NpcLayout.SLOT;
    private static final int PREVIEW_X = NpcLayout.PREVIEW_X;
    private static final int PREVIEW_Y = NpcLayout.PREVIEW_Y;
    private static final int PREVIEW_W = NpcLayout.PREVIEW_W;
    private static final int PREVIEW_H = NpcLayout.PREVIEW_H;
    private static final int CROSS_X = NpcLayout.CROSS_X;
    private static final int CROSS_Y = NpcLayout.CROSS_Y;
    private static final int BACKPACK_X = NpcLayout.BACKPACK_X;
    private static final int BACKPACK_Y = NpcLayout.BACKPACK_Y;
    private static final int NAME_Y = NpcLayout.NAME_Y;
    private static final int NAME_H = NpcLayout.NAME_H;
    private static final int BUTTON_SIZE = NpcLayout.BUTTON_SIZE;
    private static final int BUTTON_GAP = NpcLayout.BUTTON_GAP;
    private static final int BUTTON_Y = NpcLayout.BUTTON_Y;
    private static final int BUTTON_X = NpcLayout.BUTTON_X;
    private static final int PLAYER_X = NpcLayout.PLAYER_X;
    private static final int PLAYER_Y = NpcLayout.PLAYER_Y;
    private static final int HOTBAR_Y = NpcLayout.HOTBAR_Y;

    private static final int PARCHMENT = 0xFF4A3B2A;
    private static final int GOLD = 0xFFC6A56A;
    private static final int FRAME = 0xFF8A7048;
    private static final int INK = 0xFF120E0C;
    private static final int BUTTON = 0xFF3A2E24;
    private static final int BUTTON_ON = 0xFF6A5430;
    private static final int BUTTON_HOVER = 0xFF8A6A3C;
    private static final int NAME_INK = 0xFFE6D2A2;
    private static final int NAME_HINT = 0xFF8A7050;

    private NpcInventoryPanel()
    {
    }

    public static ModularPanel<?> create(NpcMenu menu)
    {
        NpcEntity npc = menu.getNpc();
        ModularPanel<?> panel = ModularPanel.defaultPanel("npc_inventory", NpcLayout.WIDTH, NpcLayout.HEIGHT);
        panel.background(
                GuiTextures.MC_BACKGROUND.withColorOverride(PARCHMENT),
                new Rectangle().color(GOLD).hollow(1.25F));
        panel.child(nameField(menu));
        panel.child(cross(menu));
        panel.child(preview(menu, npc));
        panel.child(itemGrid(BACKPACK_X, BACKPACK_Y, 3, 3, NpcInventory.BACKPACK_START, menu));
        panel.child(itemGrid(PLAYER_X, PLAYER_Y, 9, 3, NpcInventory.SLOT_COUNT, menu));
        panel.child(itemGrid(PLAYER_X, HOTBAR_Y, 9, 1, NpcInventory.SLOT_COUNT + 27, menu));
        panel.child(command(0, GuiTextures.MOVE_TO, "gui.ancientempires.npc.follow", NpcMenu.BUTTON_FOLLOW, menu, active(npc, NpcEntity::isFollowing)));
        panel.child(command(1, GuiTextures.BOOKMARK, "gui.ancientempires.npc.set_home", NpcMenu.BUTTON_SET_HOME, menu, active(npc, entity -> entity.getHome() != null)));
        panel.child(command(2, GuiTextures.CROSS, "gui.ancientempires.npc.clear_home", NpcMenu.BUTTON_CLEAR_HOME, menu, null));
        panel.child(command(3, GuiTextures.REFRESH, "gui.ancientempires.npc.repack", NpcMenu.BUTTON_REPACK, menu, null));
        panel.child(command(4, GuiTextures.FAVORITE, "gui.ancientempires.npc.creative", NpcMenu.BUTTON_CREATIVE, menu, active(npc, NpcEntity::isCreative)));
        return panel;
    }

    private static TextFieldWidget nameField(NpcMenu menu)
    {
        return new TextFieldWidget()
                .pos(PREVIEW_X, NAME_Y)
                .size(PREVIEW_W, NAME_H)
                .background(new Rectangle().color(INK))
                .setTextColor(NAME_INK)
                .hintText(Component.translatable("gui.ancientempires.npc.name"))
                .hintColor(NAME_HINT)
                .setMaxLength(32)
                .value(new StringValue.Dynamic(() -> currentName(menu), value -> submitName(menu, value)));
    }

    private static String currentName(NpcMenu menu)
    {
        NpcEntity npc = menu.getNpc();
        if (npc == null || npc.getCustomName() == null)
        {
            return "";
        }
        return npc.getCustomName().getString();
    }

    private static void submitName(NpcMenu menu, String value)
    {
        NpcEntity npc = menu.getNpc();
        if (npc == null || value.equals(currentName(menu)))
        {
            return;
        }
        ModNetwork.sendName(npc.getId(), value);
    }

    private static Grid cross(NpcMenu menu)
    {
        Grid grid = new Grid()
                .pos(CROSS_X, CROSS_Y)
                .size(SLOT * 3, SLOT * 4)
                .minColWidth(SLOT)
                .minRowHeight(SLOT)
                .minElementMargin(0);
        grid.row(spacer(), slot(menu, NpcInventory.HEAD_SLOT), spacer());
        grid.row(slot(menu, NpcInventory.MAIN_HAND_SLOT), slot(menu, NpcInventory.CHEST_SLOT), slot(menu, NpcInventory.OFFHAND_SLOT));
        grid.row(spacer(), slot(menu, NpcInventory.LEGS_SLOT), spacer());
        grid.row(spacer(), slot(menu, NpcInventory.FEET_SLOT), spacer());
        return grid;
    }

    private static ParentWidget<?> preview(NpcMenu menu, NpcEntity npc)
    {
        ParentWidget<?> frame = new ParentWidget<>()
                .pos(PREVIEW_X, PREVIEW_Y)
                .size(PREVIEW_W, PREVIEW_H)
                .background(new Rectangle().color(INK), GuiTextures.DISPLAY.withColorOverride(FRAME));
        if (npc != null)
        {
            frame.child(new EntityDrawable<>(npc).followMouse().asWidget()
                    .pos(4, 4)
                    .size(PREVIEW_W - 16, PREVIEW_H - 20));
        }
        frame.child(new ButtonWidget<>()
                .pos(PREVIEW_W - 14, PREVIEW_H - 14)
                .size(12, 12)
                .background(new Rectangle().color(BUTTON))
                .hoverBackground(new Rectangle().color(BUTTON_HOVER))
                .overlay(new Icon(GuiTextures.IMAGE).size(8).center().margin(2))
                .onMousePressed((context, button) -> openSkin(menu, button))
                .addTooltipLine(Component.translatable("gui.ancientempires.npc.skin").getString()));
        return frame;
    }

    private static boolean openSkin(NpcMenu menu, int button)
    {
        if (button != 0)
        {
            return false;
        }
        NpcEntity npc = menu.getNpc();
        if (npc != null)
        {
            NpcSkinHooks.open(npc);
        }
        return true;
    }

    private static Grid itemGrid(int x, int y, int columns, int rows, int firstIndex, NpcMenu menu)
    {
        Grid grid = new Grid()
                .pos(x, y)
                .size(columns * SLOT, rows * SLOT)
                .minColWidth(SLOT)
                .minRowHeight(SLOT)
                .minElementMargin(0);
        for (int row = 0; row < rows; row++)
        {
            List<IWidget> line = new ArrayList<>();
            for (int column = 0; column < columns; column++)
            {
                line.add(slot(menu, firstIndex + row * columns + column));
            }
            grid.row(line);
        }
        return grid;
    }

    private static MenuItemSlot slot(NpcMenu menu, int index)
    {
        return new MenuItemSlot(menu.getSlot(index));
    }

    private static Widget<?> spacer()
    {
        return new Widget<>().size(SLOT);
    }

    private static ButtonWidget<?> command(int index, UITexture icon, String tooltip, int buttonId, NpcMenu menu, BooleanSupplier active)
    {
        ButtonWidget<?> button = new ButtonWidget<>()
                .pos(BUTTON_X + index * (BUTTON_SIZE + BUTTON_GAP), BUTTON_Y)
                .size(BUTTON_SIZE, BUTTON_SIZE)
                .background(new Rectangle().color(BUTTON))
                .hoverBackground(new Rectangle().color(BUTTON_HOVER))
                .overlay(new Icon(icon).size(11).center().margin(2))
                .onMousePressed((context, pressed) -> sendButton(menu, buttonId, pressed))
                .addTooltipLine(Component.translatable(tooltip).getString());
        if (active != null)
        {
            button.onUpdateListener(widget -> widget.background(new Rectangle().color(active.getAsBoolean() ? BUTTON_ON : BUTTON)));
        }
        return button;
    }

    private static boolean sendButton(NpcMenu menu, int buttonId, int pressed)
    {
        if (pressed != 0)
        {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null)
        {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
        return true;
    }

    private static BooleanSupplier active(NpcEntity npc, Predicate<NpcEntity> test)
    {
        if (npc == null)
        {
            return null;
        }
        return () -> test.test(npc);
    }
}
