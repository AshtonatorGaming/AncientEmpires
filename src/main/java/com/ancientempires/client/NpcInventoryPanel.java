package com.ancientempires.client;

import brachy.modularui.api.drawable.IDrawable;
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
    private static final int GOLD = 0xFFC6A56A;

    private NpcInventoryPanel()
    {
    }

    public static void populate(ModularPanel<?> panel, NpcMenu menu)
    {
        NpcEntity npc = menu.getNpc();
        panel.background(new SlotBevel(PARCHMENT, false, false));
        panel.hoverBackground(new SlotBevel(PARCHMENT, false, false));
        panel.child(nameField(menu));
        addCross(panel, menu);
        panel.child(preview(menu, npc));
        int backpackW = span(3);
        int inventoryW = span(9);
        int inventoryX = (NpcLayout.WIDTH - inventoryW) / 2;
        panel.child(sectionLabel(BACKPACK_X, BACKPACK_LABEL_Y, backpackW, "gui.ancientempires.npc.backpack", Alignment.Center));
        panel.child(itemGrid(BACKPACK_X, BACKPACK_Y, 3, 3, NpcInventory.BACKPACK_START, menu));
        panel.child(sectionLabel(inventoryX, PLAYER_LABEL_Y, inventoryW, "gui.ancientempires.npc.inventory", Alignment.CenterLeft));
        panel.child(itemGrid(inventoryX, PLAYER_Y, 9, 3, NpcInventory.SLOT_COUNT, menu));
        panel.child(itemGrid(inventoryX, HOTBAR_Y, 9, 1, NpcInventory.SLOT_COUNT + 27, menu));
        addCommands(panel, menu, npc);
    }

    private static void addCommands(ModularPanel<?> panel, NpcMenu menu, NpcEntity npc)
    {
        boolean creative = showCreative();
        boolean hold = showHold(npc);
        ButtonWidget<?>[] row = new ButtonWidget<?>[6];
        row[0] = command(0, 0, GuiTextures.MOVE_RIGHT, "gui.ancientempires.npc.follow", NpcMenu.BUTTON_FOLLOW, menu, active(npc, NpcEntity::isFollowing));
        row[1] = command(0, 1, GuiTextures.BOOKMARK, "gui.ancientempires.npc.set_home", NpcMenu.BUTTON_SET_HOME, menu, active(npc, entity -> entity.getHome() != null));
        row[2] = command(0, 2, GuiTextures.CROSS, "gui.ancientempires.npc.clear_home", NpcMenu.BUTTON_CLEAR_HOME, menu, null);
        row[3] = command(0, 3, GuiTextures.REFRESH, "gui.ancientempires.npc.repack", NpcMenu.BUTTON_REPACK, menu, null);
        row[4] = command(0, 4, GuiTextures.LOCKED, "gui.ancientempires.npc.hold", NpcMenu.BUTTON_HOLD, menu, active(npc, NpcEntity::isHoldingPosition));
        row[5] = command(0, 5, GuiTextures.FAVORITE, "gui.ancientempires.npc.creative", NpcMenu.BUTTON_CREATIVE, menu, active(npc, NpcEntity::isCreative));
        row[4].setEnabled(hold);
        row[5].setEnabled(creative);
        placeCommands(row);
        // Repack has no on-state color, so this poll can reflow hold and creative.
        row[3].onUpdateListener(widget -> layoutCommands(row, npc), true);
        for (ButtonWidget<?> button : row)
        {
            panel.child(button);
        }
    }

    /**
     * Hold is the archer sentry, so it is shown only while a bow is in the main hand.
     * Creative stays the other button that can leave the row. Both reflow when they change.
     */
    private static void layoutCommands(ButtonWidget<?>[] row, NpcEntity npc)
    {
        boolean hold = showHold(npc);
        boolean creative = showCreative();
        if (row[4].isEnabled() == hold && row[5].isEnabled() == creative)
        {
            return;
        }
        row[4].setEnabled(hold);
        row[5].setEnabled(creative);
        placeCommands(row);
    }

    private static void placeCommands(ButtonWidget<?>[] row)
    {
        int shown = 0;
        for (ButtonWidget<?> button : row)
        {
            if (button.isEnabled())
            {
                shown++;
            }
        }
        int origin = NpcLayout.buttonRowX(Math.max(shown, 1));
        int slot = 0;
        for (ButtonWidget<?> button : row)
        {
            if (!button.isEnabled())
            {
                continue;
            }
            button.pos(origin + slot * (BUTTON_SIZE + BUTTON_GAP), BUTTON_Y);
            button.scheduleResize();
            slot++;
        }
    }

    private static boolean showHold(NpcEntity npc)
    {
        return npc != null && npc.isHoldingBow();
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

    private static void addCross(ModularPanel<?> panel, NpcMenu menu)
    {
        // Panel coordinates from NpcLayout. Empty cells stay empty.
        // The chest's right edge is its last pixel. The offhand has to start one
        // pixel left of CROSS_X + 2 * SLOT or a parchment gap shows between them.
        // The chest's bottom edge is the legs' top line, one pixel under an 18 px
        // box, so the hands extend down by that pixel.
        panel.child(slot(menu, NpcInventory.HEAD_SLOT, CROSS_X + SLOT, CROSS_Y, false, true));
        panel.child(slot(menu, NpcInventory.CHEST_SLOT, CROSS_X + SLOT, CROSS_Y + SLOT, true, true));
        panel.child(handSlot(menu, NpcInventory.MAIN_HAND_SLOT, CROSS_X, CROSS_Y + SLOT, true));
        panel.child(handSlot(menu, NpcInventory.OFFHAND_SLOT, CROSS_X + 2 * SLOT - 1, CROSS_Y + SLOT, false));
        panel.child(slot(menu, NpcInventory.LEGS_SLOT, CROSS_X + SLOT, CROSS_Y + 2 * SLOT, false, true));
        panel.child(slot(menu, NpcInventory.FEET_SLOT, CROSS_X + SLOT, CROSS_Y + 3 * SLOT, false, false));
    }

    private static MenuItemSlot handSlot(NpcMenu menu, int index, int x, int y, boolean shareRight)
    {
        MenuItemSlot slot = slot(menu, index, x, y, shareRight, false);
        slot.place(x, y, SLOT, SLOT + 1);
        return slot;
    }

    private static ParentWidget<?> preview(NpcMenu menu, NpcEntity npc)
    {
        ParentWidget<?> frame = new ParentWidget<>()
                .pos(PREVIEW_X, PREVIEW_Y)
                .size(PREVIEW_W, PREVIEW_H)
                .background(new SlotBevel(INK, false, false))
                .hoverBackground(new SlotBevel(INK, false, false));
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
        if (npc != null)
        {
            frame.child(roleButton(menu, npc));
        }
        return frame;
    }

    /**
     * The NPC's role, beside the skin button. Left click steps forward through
     * the roles, right click back. Only the owner, or a creative player, sees it.
     */
    private static ButtonWidget<?> roleButton(NpcMenu menu, NpcEntity npc)
    {
        ButtonWidget<?> button = new ButtonWidget<>()
                .pos(2, PREVIEW_H - 14)
                .size(PREVIEW_W - 18, 12)
                .background(new Rectangle().color(BUTTON))
                .hoverBackground(new Rectangle().color(BUTTON_HOVER))
                .overlay(new RoleLabel(npc))
                .onMousePressed((context, pressed) -> cycleRole(menu, pressed))
                .addTooltipLine(Component.translatable("gui.ancientempires.npc.role").getString());
        Player player = Minecraft.getInstance().player;
        button.setEnabled(player != null && npc.mayChangeRole(player));
        return button;
    }

    private static boolean cycleRole(NpcMenu menu, int pressed)
    {
        if (pressed == 0)
        {
            return sendButton(menu, NpcMenu.BUTTON_ROLE_NEXT, 0);
        }
        if (pressed == 1)
        {
            return sendButton(menu, NpcMenu.BUTTON_ROLE_PREV, 0);
        }
        return false;
    }

    /** The role name, read from the synced role each frame, trimmed to the button. */
    private static final class RoleLabel implements IDrawable
    {
        private final NpcEntity npc;

        private RoleLabel(NpcEntity npc)
        {
            this.npc = npc;
        }

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme)
        {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            String line = Component.translatable(this.npc.getRole().translationKey()).getString();
            int max = Math.max(1, width - 4);
            if (font.width(line) > max)
            {
                line = font.plainSubstrByWidth(line, max);
            }
            int textX = x + (width - font.width(line)) / 2;
            int textY = y + (height - 8) / 2;
            context.getGraphics().drawString(font, line, textX, textY, NAME_INK, false);
        }
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

    /** Adjacent 18 px slots overlap by 1 px so they share a border and the 16 px item meets it. */
    private static int span(int count)
    {
        return SLOT + (count - 1) * (SLOT - 1);
    }

    private static ParentWidget<?> itemGrid(int x, int y, int columns, int rows, int firstIndex, NpcMenu menu)
    {
        ParentWidget<?> grid = cellGrid(x, y, columns, rows);
        int step = SLOT - 1;
        for (int row = 0; row < rows; row++)
        {
            for (int column = 0; column < columns; column++)
            {
                boolean shareRight = column + 1 < columns;
                boolean shareBottom = row + 1 < rows;
                grid.child(slot(menu, firstIndex + row * columns + column, column * step, row * step, shareRight, shareBottom));
            }
        }
        return grid;
    }

    private static ParentWidget<?> cellGrid(int x, int y, int columns, int rows)
    {
        ParentWidget<?> grid = new ParentWidget<>();
        grid.pos(x, y);
        grid.size(span(columns), span(rows));
        grid.padding(0);
        grid.margin(0);
        grid.disableCoverChildren();
        grid.disableThemeBackground(true);
        grid.disableHoverThemeBackground(true);
        return grid;
    }

    private static MenuItemSlot slot(NpcMenu menu, int index, int x, int y, boolean shareRight, boolean shareBottom)
    {
        MenuItemSlot slot = new MenuItemSlot(menu.getSlot(index));
        slot.place(x, y);
        slot.background(new SlotBevel(SLOT_FILL, shareBottom, shareRight));
        slot.hoverBackground(new SlotBevel(SLOT_HOVER, shareBottom, shareRight));
        slot.disableThemeBackground(true);
        slot.disableHoverThemeBackground(true);
        return slot;
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
     * Fill and a 1px gold stroke on the same rectangle. The slot below or to
     * the right owns a shared edge, so the join stays a single line.
     */
    private static final class SlotBevel implements IDrawable
    {
        private final int fill;
        private final boolean neighborBottom;
        private final boolean neighborRight;

        private SlotBevel(int fill, boolean neighborBottom, boolean neighborRight)
        {
            this.fill = fill;
            this.neighborBottom = neighborBottom;
            this.neighborRight = neighborRight;
        }

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme)
        {
            GuiGraphics graphics = context.getGraphics();
            GuiDraw.drawRect(graphics, x, y, width, height, this.fill);
            GuiDraw.drawRect(graphics, x, y, width, 1, GOLD);
            GuiDraw.drawRect(graphics, x, y, 1, height, GOLD);
            if (!this.neighborBottom)
            {
                GuiDraw.drawRect(graphics, x, y + height - 1, width, 1, GOLD);
            }
            if (!this.neighborRight)
            {
                GuiDraw.drawRect(graphics, x + width - 1, y, 1, height, GOLD);
            }
        }
    }
}
