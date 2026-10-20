package com.ancientempires.client;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.drawable.Icon;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.utils.Alignment;
import brachy.modularui.value.StringValue;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.Widget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcInventory;
import com.ancientempires.inventory.NpcLayout;
import com.ancientempires.inventory.NpcMenu;
import com.ancientempires.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
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
    private static final int PREVIEW_ENTITY_X = NpcLayout.PREVIEW_ENTITY_X;
    private static final int PREVIEW_ENTITY_Y = NpcLayout.PREVIEW_ENTITY_Y;
    private static final int PREVIEW_ENTITY_W = NpcLayout.PREVIEW_ENTITY_W;
    private static final int PREVIEW_ENTITY_H = NpcLayout.PREVIEW_ENTITY_H;
    private static final int CROSS_X = NpcLayout.CROSS_X;
    private static final int CROSS_Y = NpcLayout.CROSS_Y;
    private static final int BACKPACK_X = NpcLayout.BACKPACK_X;
    private static final int BACKPACK_Y = NpcLayout.BACKPACK_Y;
    private static final int NAME_Y = NpcLayout.NAME_Y;
    private static final int NAME_H = NpcLayout.NAME_H;
    private static final int BUTTON_SIZE = NpcLayout.BUTTON_SIZE;
    private static final int BUTTON_GAP = NpcLayout.BUTTON_GAP;
    private static final int BUTTON_Y = NpcLayout.BUTTON_Y;
    private static final int PLAYER_X = NpcLayout.PLAYER_X;
    private static final int PLAYER_Y = NpcLayout.PLAYER_Y;
    private static final int HOTBAR_Y = NpcLayout.HOTBAR_Y;
    private static final int SECTION_LABEL_H = NpcLayout.SECTION_LABEL_H;
    private static final int BACKPACK_LABEL_Y = NpcLayout.BACKPACK_LABEL_Y;
    private static final int PLAYER_LABEL_Y = NpcLayout.PLAYER_LABEL_Y;

    private static final int PARCHMENT = 0xFF4A3B2A;
    private static final int INK = 0xFF120E0C;
    private static final int BUTTON = 0xFF3A2E24;
    private static final int BUTTON_ON = 0xFF6A5430;
    private static final int BUTTON_HOVER = 0xFF8A6A3C;
    private static final int NAME_INK = 0xFFE6D2A2;
    private static final int NAME_HINT = 0xFF8A7050;
    private static final int SLOT_FILL = 0xFF2C241C;
    private static final int SLOT_HOVER = 0xFF3A2E24;
    private static final int BEVEL_LIGHT = 0xFFE0C48A;
    private static final int BEVEL_DARK = 0xFF241C14;

    private NpcInventoryPanel()
    {
    }

    public static void populate(ModularPanel<?> panel, NpcMenu menu)
    {
        NpcEntity npc = menu.getNpc();
        panel.background(new SlotBevel(PARCHMENT, true, true, true, true));
        panel.child(nameField(menu));
        panel.child(cross(menu));
        panel.child(preview(menu, npc));
        panel.child(sectionLabel(BACKPACK_X, BACKPACK_LABEL_Y, SLOT * 3, "gui.ancientempires.npc.backpack", Alignment.Center));
        panel.child(itemGrid(BACKPACK_X, BACKPACK_Y, 3, 3, NpcInventory.BACKPACK_START, menu));
        panel.child(sectionLabel(PLAYER_X, PLAYER_LABEL_Y, SLOT * 9, "gui.ancientempires.npc.inventory", Alignment.CenterLeft));
        panel.child(itemGrid(PLAYER_X, PLAYER_Y, 9, 3, NpcInventory.SLOT_COUNT, menu));
        panel.child(itemGrid(PLAYER_X, HOTBAR_Y, 9, 1, NpcInventory.SLOT_COUNT + 27, menu));
        addCommands(panel, menu, npc);
    }

    private static void addCommands(ModularPanel<?> panel, NpcMenu menu, NpcEntity npc)
    {
        boolean creative = showCreative();
        int origin = NpcLayout.buttonRowX(creative ? 5 : 4);
        panel.child(command(origin, 0, GuiTextures.MOVE_RIGHT, "gui.ancientempires.npc.follow", NpcMenu.BUTTON_FOLLOW, menu, active(npc, NpcEntity::isFollowing)));
        panel.child(command(origin, 1, GuiTextures.BOOKMARK, "gui.ancientempires.npc.set_home", NpcMenu.BUTTON_SET_HOME, menu, active(npc, entity -> entity.getHome() != null)));
        panel.child(command(origin, 2, GuiTextures.CROSS, "gui.ancientempires.npc.clear_home", NpcMenu.BUTTON_CLEAR_HOME, menu, null));
        panel.child(command(origin, 3, GuiTextures.REFRESH, "gui.ancientempires.npc.repack", NpcMenu.BUTTON_REPACK, menu, null));
        if (creative)
        {
            panel.child(command(origin, 4, GuiTextures.FAVORITE, "gui.ancientempires.npc.creative", NpcMenu.BUTTON_CREATIVE, menu, active(npc, NpcEntity::isCreative)));
        }
    }

    private static boolean showCreative()
    {
        Player player = Minecraft.getInstance().player;
        return player != null && NpcEntity.mayToggleCreative(player);
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
                .background(new SlotBevel(INK, true, true, true, true));
        if (npc != null)
        {
            frame.child(new NpcEntityPreview(npc)
                    .asWidget()
                    .pos(PREVIEW_ENTITY_X, PREVIEW_ENTITY_Y)
                    .size(PREVIEW_ENTITY_W, PREVIEW_ENTITY_H));
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

    private static TextWidget<?> sectionLabel(int x, int y, int width, String key, Alignment align)
    {
        return new TextWidget<>(Component.translatable(key))
                .pos(x, y)
                .size(width, SECTION_LABEL_H)
                .maxWidth(width)
                .color(NAME_INK)
                .shadow(Boolean.TRUE)
                .textAlign(align);
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
        boolean drawRight = !hasNeighbor(index, 1, 0);
        boolean drawBottom = !hasNeighbor(index, 0, 1);
        MenuItemSlot slot = new MenuItemSlot(menu.getSlot(index));
        // Each slot draws its top and left. The slot to the right or below owns the shared edge.
        slot.background(new SlotBevel(SLOT_FILL, true, true, drawBottom, drawRight));
        slot.hoverBackground(new SlotBevel(SLOT_HOVER, true, true, drawBottom, drawRight));
        return slot;
    }

    private static boolean hasNeighbor(int index, int dx, int dy)
    {
        int x = NpcLayout.cellX(index) + dx * SLOT;
        int y = NpcLayout.cellY(index) + dy * SLOT;
        int limit = NpcInventory.SLOT_COUNT + 36;
        for (int other = 0; other < limit; other++)
        {
            if (other != index && NpcLayout.cellX(other) == x && NpcLayout.cellY(other) == y)
            {
                return true;
            }
        }
        return false;
    }

    private static Widget<?> spacer()
    {
        return new Widget<>().size(SLOT);
    }

    private static ButtonWidget<?> command(int origin, int index, UITexture icon, String tooltip, int buttonId, NpcMenu menu, BooleanSupplier active)
    {
        Icon mark = new Icon(icon).center();
        if (icon == GuiTextures.MOVE_RIGHT)
        {
            // The move-to glyph has a hole, and the dark button shows through as a dot.
            mark.size(8, 12);
        }
        else
        {
            mark.size(11).margin(2);
        }
        ButtonWidget<?> button = new ButtonWidget<>()
                .pos(origin + index * (BUTTON_SIZE + BUTTON_GAP), BUTTON_Y)
                .size(BUTTON_SIZE, BUTTON_SIZE)
                .background(new Rectangle().color(BUTTON))
                .hoverBackground(new Rectangle().color(BUTTON_HOVER))
                .overlay(mark)
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

    /**
     * One-pixel bevel. Top and left are the light edge. Bottom and right are
     * the dark edge, and only the slot that owns a shared side draws it.
     */
    private static final class SlotBevel implements IDrawable
    {
        private final int fill;
        private final boolean top;
        private final boolean left;
        private final boolean bottom;
        private final boolean right;

        private SlotBevel(int fill, boolean top, boolean left, boolean bottom, boolean right)
        {
            this.fill = fill;
            this.top = top;
            this.left = left;
            this.bottom = bottom;
            this.right = right;
        }

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme)
        {
            GuiGraphics graphics = context.getGraphics();
            GuiDraw.drawRect(graphics, x, y, width, height, this.fill);
            if (this.top)
            {
                GuiDraw.drawRect(graphics, x, y, width, 1, BEVEL_LIGHT);
            }
            if (this.left)
            {
                GuiDraw.drawRect(graphics, x, y, 1, height, BEVEL_LIGHT);
            }
            if (this.bottom)
            {
                GuiDraw.drawRect(graphics, x, y + height - 1, width, 1, BEVEL_DARK);
            }
            if (this.right)
            {
                GuiDraw.drawRect(graphics, x + width - 1, y, 1, height, BEVEL_DARK);
            }
        }
    }
}
