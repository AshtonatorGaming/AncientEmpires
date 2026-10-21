package com.ancientempires.client;

import brachy.modularui.api.IPanelHandler;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.drawable.Icon;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.utils.Alignment;
import brachy.modularui.value.StringValue;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.ScrollWidget;
import brachy.modularui.widget.scroll.VerticalScrollData;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcSkins;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Skin button page. Faction pages, then that faction's classes, then the skins
 * for the selected class. Apply stores the skin id the world model already reads.
 */
@OnlyIn(Dist.CLIENT)
public final class NpcSkinSelector
{
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 222;
    private static final int MARGIN = 6;
    private static final int ARROW = 14;
    private static final int COUNTER_W = 36;
    private static final int ROW_Y = 20;
    private static final int CLASS_Y = 36;
    private static final int CHIP_H = 14;
    private static final int SEARCH_Y = 54;
    private static final int SEARCH_H = 14;
    private static final int BODY_Y = 72;
    private static final int PREVIEW_W = 80;
    private static final int PREVIEW_H = 108;
    private static final int PREVIEW_X = PANEL_W - MARGIN - PREVIEW_W;
    private static final int LIST_W = PREVIEW_X - MARGIN - 4;
    private static final int LIST_H = PANEL_H - BODY_Y - MARGIN;
    private static final int ROW_H = 14;
    private static final int FACTION_SLOTS = 4;
    private static final int CLASS_SLOTS = 3;
    private static final int CHIP_LEFT = MARGIN + ARROW + 2;
    private static final int CHIP_RIGHT = PANEL_W - MARGIN - ARROW - COUNTER_W - 4;
    private static final int FACTION_CHIP_W = (CHIP_RIGHT - CHIP_LEFT - (FACTION_SLOTS - 1) * 2) / FACTION_SLOTS;
    private static final int CLASS_CHIP_W = (CHIP_RIGHT - CHIP_LEFT - (CLASS_SLOTS - 1) * 2) / CLASS_SLOTS;

    private static final int PARCHMENT = 0xFF4A3B2A;
    private static final int INK = 0xFF120E0C;
    private static final int BUTTON = 0xFF3A2E24;
    private static final int BUTTON_ON = 0xFF6A5430;
    private static final int BUTTON_HOVER = 0xFF8A6A3C;
    private static final int NAME_INK = 0xFFE6D2A2;
    private static final int NAME_HINT = 0xFF8A7050;

    private NpcSkinSelector()
    {
    }

    public static void open(NpcEntity npc)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof NpcScreen npcScreen) || minecraft.level == null || npc == null)
        {
            if (minecraft.player != null)
            {
                minecraft.player.displayClientMessage(Component.translatable("message.ancientempires.npc.skin_unavailable"), true);
            }
            return;
        }
        ModularPanel<?> parent = npcScreen.screen().getMainPanel();
        IPanelHandler.simple(parent, (ignored, player) -> build(npc), true).openPanel();
    }

    private static ModularPanel<?> build(NpcEntity npc)
    {
        Minecraft minecraft = Minecraft.getInstance();
        NpcEntity preview = new NpcEntity(ModEntities.NPC.get(), minecraft.level);
        preview.setBodyScale(npc.getBodyScale());
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            preview.setItemSlot(slot, npc.getItemBySlot(slot).copy());
        }
        State state = new State(preview);
        NpcSkins.Entry current = NpcSkins.byId(npc.getSkinId());
        if (current != null)
        {
            state.factionId = current.factionId();
            state.classId = current.classId();
            state.selected = current.id();
            preview.assignSkin(current.id());
            state.factionPage = NpcSkins.indexOfFaction(current.factionId()) / FACTION_SLOTS;
            NpcSkins.Faction faction = NpcSkins.faction(current.factionId());
            int classIndex = faction == null ? 0 : faction.indexOfClass(current.classId());
            state.classPage = classIndex / CLASS_SLOTS;
        }
        else
        {
            preview.assignSkin(npc.getSkinId());
            if (!NpcSkins.factions().isEmpty())
            {
                NpcSkins.Faction faction = NpcSkins.factions().get(0);
                state.factionId = faction.id();
                if (!faction.classes().isEmpty())
                {
                    state.classId = faction.classes().get(0).id();
                }
            }
        }

        ModularPanel<?> panel = ModularPanel.defaultPanel("npc_skin", PANEL_W, PANEL_H)
                .background(new Rectangle().color(PARCHMENT))
                .hoverBackground(new Rectangle().color(PARCHMENT))
                .disablePanelsBelow(true)
                .closeOnOutOfBoundsClick(true)
                .onCloseAction(preview::discard);

        panel.child(new TextWidget<>(Component.translatable("gui.ancientempires.skin.title"))
                .pos(MARGIN, 4)
                .size(PANEL_W - 28, 12)
                .color(NAME_INK)
                .shadow(Boolean.TRUE)
                .textAlign(Alignment.CenterLeft));
        panel.child(new ButtonWidget<>()
                .pos(PANEL_W - 18, 4)
                .size(12, 12)
                .background(new Rectangle().color(BUTTON))
                .hoverBackground(new Rectangle().color(BUTTON_HOVER))
                .overlay(new Icon(GuiTextures.CROSS).size(8).center().margin(2))
                .onMousePressed((context, button) -> close(panel, button)));

        state.factionPrev = arrow(MARGIN, ROW_Y, "<");
        state.factionNext = arrow(PANEL_W - MARGIN - ARROW, ROW_Y, ">");
        state.factionPrev.onMousePressed((context, button) -> changePage(state, true, -1, button));
        state.factionNext.onMousePressed((context, button) -> changePage(state, true, 1, button));
        panel.child(state.factionPrev);
        panel.child(state.factionNext);
        for (int index = 0; index < FACTION_SLOTS; index++)
        {
            Chip chip = new Chip();
            chip.button = chipButton(CHIP_LEFT + index * (FACTION_CHIP_W + 2), ROW_Y, FACTION_CHIP_W, chip);
            chip.button.onMousePressed((context, button) -> chooseFaction(state, chip, button));
            state.factionChips.add(chip);
            panel.child(chip.button);
        }
        panel.child(new TextWidget<>(() -> Component.literal(pageLabel(state.factionPage, NpcSkins.factions().size(), FACTION_SLOTS)))
                .pos(CHIP_RIGHT + 2, ROW_Y)
                .size(COUNTER_W, CHIP_H)
                .color(NAME_HINT)
                .shadow(Boolean.FALSE)
                .textAlign(Alignment.Center));

        state.classPrev = arrow(MARGIN, CLASS_Y, "<");
        state.classNext = arrow(PANEL_W - MARGIN - ARROW, CLASS_Y, ">");
        state.classPrev.onMousePressed((context, button) -> changePage(state, false, -1, button));
        state.classNext.onMousePressed((context, button) -> changePage(state, false, 1, button));
        panel.child(state.classPrev);
        panel.child(state.classNext);
        for (int index = 0; index < CLASS_SLOTS; index++)
        {
            Chip chip = new Chip();
            chip.button = chipButton(CHIP_LEFT + index * (CLASS_CHIP_W + 2), CLASS_Y, CLASS_CHIP_W, chip);
            chip.button.onMousePressed((context, button) -> chooseClass(state, chip, button));
            state.classChips.add(chip);
            panel.child(chip.button);
        }
        panel.child(new TextWidget<>(() -> Component.literal(classPageLabel(state)))
                .pos(CHIP_RIGHT + 2, CLASS_Y)
                .size(COUNTER_W, CHIP_H)
                .color(NAME_HINT)
                .shadow(Boolean.FALSE)
                .textAlign(Alignment.Center));

        ParentWidget<?> list = new ParentWidget<>().size(LIST_W - 8, ROW_H);
        TextWidget<?> empty = new TextWidget<>(Component.translatable("gui.ancientempires.skin.empty"))
                .pos(4, 4)
                .size(LIST_W - 16, 12)
                .color(NAME_HINT)
                .shadow(Boolean.FALSE)
                .textAlign(Alignment.CenterLeft);
        list.child(empty);
        int pool = Math.max(1, NpcSkins.maxPool());
        for (int index = 0; index < pool; index++)
        {
            SkinSlot slot = new SkinSlot();
            ButtonWidget<?> button = new ButtonWidget<>()
                    .size(LIST_W - 12, ROW_H - 1)
                    .background(new Rectangle().color(BUTTON))
                    .hoverBackground(new Rectangle().color(BUTTON_HOVER))
                    .overlay(new SkinLabel(() -> Component.literal(slot.label), false))
                    .onMousePressed((context, click) -> select(state, slot, click));
            slot.button = button;
            state.slots.add(slot);
            list.child(button);
        }
        ScrollWidget<?> scroll = new ScrollWidget<>(new VerticalScrollData())
                .pos(MARGIN, BODY_Y)
                .size(LIST_W, LIST_H)
                .background(new Rectangle().color(INK));
        scroll.child(list);
        panel.child(scroll);
        state.list = list;
        state.empty = empty;

        ParentWidget<?> frame = new ParentWidget<>()
                .pos(PREVIEW_X, BODY_Y)
                .size(PREVIEW_W, PREVIEW_H)
                .background(new Rectangle().color(INK));
        frame.child(new NpcEntityPreview(preview)
                .asWidget()
                .pos(2, 2)
                .size(PREVIEW_W - 4, PREVIEW_H - 4));
        panel.child(frame);
        panel.child(new TextWidget<>(() -> selectedName(state))
                .pos(PREVIEW_X, BODY_Y + PREVIEW_H + 2)
                .size(PREVIEW_W, 12)
                .maxWidth(PREVIEW_W)
                .color(NAME_INK)
                .shadow(Boolean.FALSE)
                .textAlign(Alignment.Center));
        panel.child(new ButtonWidget<>()
                .pos(PREVIEW_X, PANEL_H - MARGIN - 16)
                .size(PREVIEW_W, 16)
                .background(new Rectangle().color(BUTTON))
                .hoverBackground(new Rectangle().color(BUTTON_HOVER))
                .overlay(new SkinLabel(() -> Component.translatable("gui.ancientempires.skin.apply"), true))
                .onMousePressed((context, button) -> apply(panel, npc, state, button)));

        TextFieldWidget search = new TextFieldWidget()
                .pos(MARGIN, SEARCH_Y)
                .size(PANEL_W - MARGIN * 2, SEARCH_H)
                .background(new Rectangle().color(INK))
                .setTextColor(NAME_INK)
                .hintText(Component.translatable("gui.ancientempires.skin.search"))
                .hintColor(NAME_HINT)
                .setMaxLength(64)
                .acceptsExpressions(false)
                .autoUpdateOnChange(true)
                .value(new StringValue.Dynamic(() -> state.query, value -> queryChanged(state, value)))
                .onUpdateListener(widget ->
                {
                    if (!state.dirty)
                    {
                        return;
                    }
                    state.dirty = false;
                    layout(state);
                }, true);
        panel.child(search);
        layout(state);
        state.dirty = false;
        return panel;
    }

    private static ButtonWidget<?> arrow(int x, int y, String label)
    {
        return new ButtonWidget<>()
                .pos(x, y)
                .size(ARROW, CHIP_H)
                .background(new Rectangle().color(BUTTON))
                .hoverBackground(new Rectangle().color(BUTTON_HOVER))
                .overlay(new SkinLabel(() -> Component.literal(label), true));
    }

    private static ButtonWidget<?> chipButton(int x, int y, int width, Chip chip)
    {
        return new ButtonWidget<>()
                .pos(x, y)
                .size(width, CHIP_H)
                .background(new Rectangle().color(BUTTON))
                .hoverBackground(new Rectangle().color(BUTTON_HOVER))
                .overlay(new SkinLabel(() -> Component.literal(chip.label), true));
    }

    private static boolean close(ModularPanel<?> panel, int button)
    {
        if (button != 0)
        {
            return false;
        }
        panel.closeIfOpen();
        return true;
    }

    private static boolean changePage(State state, boolean faction, int delta, int button)
    {
        if (button != 0)
        {
            return false;
        }
        if (faction)
        {
            int pages = pageCount(NpcSkins.factions().size(), FACTION_SLOTS);
            int next = state.factionPage + delta;
            if (next < 0 || next >= pages)
            {
                return true;
            }
            state.factionPage = next;
        }
        else
        {
            NpcSkins.Faction group = NpcSkins.faction(state.factionId);
            int count = group == null ? 0 : group.classes().size();
            int pages = pageCount(count, CLASS_SLOTS);
            int next = state.classPage + delta;
            if (next < 0 || next >= pages)
            {
                return true;
            }
            state.classPage = next;
        }
        state.dirty = true;
        return true;
    }

    private static boolean chooseFaction(State state, Chip chip, int button)
    {
        if (button != 0 || chip.id.isEmpty() || chip.id.equals(state.factionId))
        {
            return button == 0;
        }
        state.factionId = chip.id;
        state.classPage = 0;
        NpcSkins.Faction faction = NpcSkins.faction(state.factionId);
        state.classId = "";
        if (faction != null && !faction.classes().isEmpty())
        {
            state.classId = faction.classes().get(0).id();
        }
        focusFirstSkin(state);
        state.dirty = true;
        return true;
    }

    private static boolean chooseClass(State state, Chip chip, int button)
    {
        if (button != 0 || chip.id.isEmpty() || chip.id.equals(state.classId))
        {
            return button == 0;
        }
        state.classId = chip.id;
        focusFirstSkin(state);
        state.dirty = true;
        return true;
    }

    private static void focusFirstSkin(State state)
    {
        NpcSkins.NpcClass npcClass = NpcSkins.npcClass(state.factionId, state.classId);
        if (npcClass == null || npcClass.skins().isEmpty())
        {
            return;
        }
        NpcSkins.Entry entry = npcClass.skins().get(0);
        state.selected = entry.id();
        state.preview.assignSkin(entry.id());
    }

    private static boolean select(State state, SkinSlot slot, int button)
    {
        if (button != 0 || slot.entry == null)
        {
            return button == 0;
        }
        state.selected = slot.entry.id();
        state.preview.assignSkin(slot.entry.id());
        state.dirty = true;
        return true;
    }

    private static boolean apply(ModularPanel<?> panel, NpcEntity npc, State state, int button)
    {
        if (button != 0)
        {
            return false;
        }
        if (NpcSkins.byId(state.selected) == null)
        {
            return true;
        }
        npc.assignSkin(state.selected);
        ModNetwork.sendSkin(npc.getId(), state.selected);
        panel.closeIfOpen();
        return true;
    }

    private static void queryChanged(State state, String value)
    {
        String next = value == null ? "" : value;
        if (next.equals(state.query))
        {
            return;
        }
        state.query = next;
        state.dirty = true;
    }

    private static Component selectedName(State state)
    {
        NpcSkins.Entry entry = NpcSkins.byId(state.selected);
        if (entry == null)
        {
            return Component.empty();
        }
        return Component.literal(entry.name());
    }

    private static String pageLabel(int page, int count, int slots)
    {
        if (count <= 0)
        {
            return "";
        }
        return (page + 1) + "/" + pageCount(count, slots);
    }

    private static String classPageLabel(State state)
    {
        NpcSkins.Faction faction = NpcSkins.faction(state.factionId);
        int count = faction == null ? 0 : faction.classes().size();
        return pageLabel(state.classPage, count, CLASS_SLOTS);
    }

    private static int pageCount(int count, int slots)
    {
        if (count <= 0)
        {
            return 1;
        }
        return (count + slots - 1) / slots;
    }

    private static void layout(State state)
    {
        List<NpcSkins.Faction> factions = NpcSkins.factions();
        List<String> factionIds = new ArrayList<>();
        List<String> factionNames = new ArrayList<>();
        for (NpcSkins.Faction faction : factions)
        {
            factionIds.add(faction.id());
            factionNames.add(faction.name());
        }
        bindChips(state.factionChips, factionIds, factionNames, state.factionPage, state.factionId, FACTION_CHIP_W, ROW_Y);
        int factionPages = pageCount(factions.size(), FACTION_SLOTS);
        state.factionPrev.setEnabled(state.factionPage > 0);
        state.factionNext.setEnabled(state.factionPage + 1 < factionPages);

        NpcSkins.Faction faction = NpcSkins.faction(state.factionId);
        List<String> classIds = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        if (faction != null)
        {
            int classPages = pageCount(faction.classes().size(), CLASS_SLOTS);
            if (state.classPage >= classPages)
            {
                state.classPage = classPages - 1;
            }
            for (NpcSkins.NpcClass npcClass : faction.classes())
            {
                classIds.add(npcClass.id());
                classNames.add(npcClass.name());
            }
        }
        bindChips(state.classChips, classIds, classNames, state.classPage, state.classId, CLASS_CHIP_W, CLASS_Y);
        int classPages = pageCount(classIds.size(), CLASS_SLOTS);
        state.classPrev.setEnabled(state.classPage > 0);
        state.classNext.setEnabled(state.classPage + 1 < classPages);

        String needle = state.query.toLowerCase(Locale.ROOT).trim();
        NpcSkins.NpcClass npcClass = NpcSkins.npcClass(state.factionId, state.classId);
        List<NpcSkins.Entry> shown = new ArrayList<>();
        if (npcClass != null)
        {
            for (NpcSkins.Entry entry : npcClass.skins())
            {
                if (matches(entry, needle))
                {
                    shown.add(entry);
                }
            }
        }
        int y = 0;
        for (int index = 0; index < state.slots.size(); index++)
        {
            SkinSlot slot = state.slots.get(index);
            if (index >= shown.size())
            {
                slot.entry = null;
                slot.label = "";
                slot.button.setEnabled(false);
                slot.button.pos(0, 0).size(0, 0).scheduleResize();
                continue;
            }
            NpcSkins.Entry entry = shown.get(index);
            slot.entry = entry;
            slot.label = entry.name();
            slot.button.setEnabled(true);
            int color = entry.id().equals(state.selected) ? BUTTON_ON : BUTTON;
            slot.button.pos(2, y).size(LIST_W - 12, ROW_H - 1).background(new Rectangle().color(color)).scheduleResize();
            y += ROW_H;
        }
        boolean none = y == 0;
        state.empty.setEnabled(none);
        state.empty.pos(4, 4).size(none ? LIST_W - 16 : 0, none ? 12 : 0);
        state.list.size(LIST_W - 8, Math.max(ROW_H, y));
        state.list.scheduleResize();
    }

    private static void bindChips(List<Chip> chips, List<String> ids, List<String> labels, int page, String selected, int chipW, int y)
    {
        int start = page * chips.size();
        for (int index = 0; index < chips.size(); index++)
        {
            Chip chip = chips.get(index);
            int source = start + index;
            if (source >= ids.size())
            {
                chip.id = "";
                chip.label = "";
                chip.button.setEnabled(false);
                chip.button.pos(0, 0).size(0, 0).scheduleResize();
                continue;
            }
            chip.id = ids.get(source);
            chip.label = labels.get(source);
            chip.button.setEnabled(true);
            int color = chip.id.equals(selected) ? BUTTON_ON : BUTTON;
            int x = CHIP_LEFT + index * (chipW + 2);
            chip.button.pos(x, y).size(chipW, CHIP_H).background(new Rectangle().color(color)).scheduleResize();
        }
    }

    private static boolean matches(NpcSkins.Entry entry, String needle)
    {
        if (needle.isEmpty())
        {
            return true;
        }
        if (entry.id().toLowerCase(Locale.ROOT).contains(needle))
        {
            return true;
        }
        return entry.name().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static final class State
    {
        private String factionId = "";
        private String classId = "";
        private int factionPage;
        private int classPage;
        private String query = "";
        private String selected = "";
        private boolean dirty = true;
        private final NpcEntity preview;
        private final List<Chip> factionChips = new ArrayList<>();
        private final List<Chip> classChips = new ArrayList<>();
        private final List<SkinSlot> slots = new ArrayList<>();
        private ButtonWidget<?> factionPrev;
        private ButtonWidget<?> factionNext;
        private ButtonWidget<?> classPrev;
        private ButtonWidget<?> classNext;
        private ParentWidget<?> list;
        private TextWidget<?> empty;

        private State(NpcEntity preview)
        {
            this.preview = preview;
        }
    }

    private static final class Chip
    {
        private String id = "";
        private String label = "";
        private ButtonWidget<?> button;
    }

    private static final class SkinSlot
    {
        private NpcSkins.Entry entry;
        private String label = "";
        private ButtonWidget<?> button;
    }

    private static final class SkinLabel implements IDrawable
    {
        private final Supplier<Component> text;
        private final boolean center;

        private SkinLabel(Supplier<Component> text, boolean center)
        {
            this.text = text;
            this.center = center;
        }

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme)
        {
            Font font = Minecraft.getInstance().font;
            String line = this.text.get().getString();
            int max = Math.max(1, width - 4);
            if (font.width(line) > max)
            {
                line = font.plainSubstrByWidth(line, max);
            }
            int textX = this.center ? x + (width - font.width(line)) / 2 : x + 2;
            int textY = y + (height - 8) / 2;
            GuiGraphics graphics = context.getGraphics();
            graphics.drawString(font, line, textX, textY, NAME_INK, false);
        }
    }
}
