package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcSkins;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Skin button page. Faction pages, then that faction's classes, then the skins
 * for the selected class. The custom tab is one list. Apply stores the skin id
 * the world model already reads.
 */
@OnlyIn(Dist.CLIENT)
public final class NpcSkinSelector extends NpcPopupScreen
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
    /** Rows leave room on the right for the scroll bar. */
    private static final int ROW_W = LIST_W - 10;
    private static final int FACTION_SLOTS = 4;
    private static final int CLASS_SLOTS = 3;
    private static final int CHIP_LEFT = MARGIN + ARROW + 2;
    private static final int CHIP_RIGHT = PANEL_W - MARGIN - ARROW - COUNTER_W - 4;
    private static final int FACTION_CHIP_W = (CHIP_RIGHT - CHIP_LEFT - (FACTION_SLOTS - 1) * 2) / FACTION_SLOTS;
    private static final int CLASS_CHIP_W = (CHIP_RIGHT - CHIP_LEFT - (CLASS_SLOTS - 1) * 2) / CLASS_SLOTS;
    private static final int SCROLL_BAR_W = 4;

    private final NpcEntity npc;
    private final NpcEntity preview;
    private final NpcEntityPreview previewRenderer;
    private String factionId = "";
    private String classId = "";
    private int factionPage;
    private int classPage;
    private String query = "";
    private String listKey = "";
    private String selected = "";
    private int scroll;
    private final List<NpcSkins.Entry> shown = new ArrayList<>();
    private final List<Chip> factionChips = new ArrayList<>();
    private final List<Chip> classChips = new ArrayList<>();

    private NpcSkinSelector(NpcEntity npc)
    {
        super(Component.translatable("gui.ancientempires.skin.title"), PANEL_W, PANEL_H);
        this.npc = npc;
        Minecraft minecraft = Minecraft.getInstance();
        this.preview = new NpcEntity(ModEntities.NPC.get(), minecraft.level);
        this.preview.setBodyScale(npc.getBodyScale());
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            this.preview.setItemSlot(slot, npc.getItemBySlot(slot).copy());
        }
        this.previewRenderer = new NpcEntityPreview(this.preview);
        NpcSkins.Entry current = NpcSkins.byId(npc.getSkinId());
        if (current != null)
        {
            this.factionId = current.factionId();
            this.classId = current.classId();
            this.selected = current.id();
            this.preview.assignSkin(current.id());
            this.factionPage = NpcSkins.indexOfFaction(current.factionId()) / FACTION_SLOTS;
            NpcSkins.Faction faction = NpcSkins.faction(current.factionId());
            int classIndex = faction == null ? 0 : faction.indexOfClass(current.classId());
            this.classPage = classIndex / CLASS_SLOTS;
        }
        else
        {
            this.preview.assignSkin(npc.getSkinId());
            if (!NpcSkins.factions().isEmpty())
            {
                NpcSkins.Faction faction = NpcSkins.factions().get(0);
                this.factionId = faction.id();
                if (!faction.classes().isEmpty())
                {
                    this.classId = faction.classes().get(0).id();
                }
            }
        }
    }

    public static void open(NpcEntity npc)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof NpcScreen) || minecraft.level == null || npc == null)
        {
            if (minecraft.player != null)
            {
                minecraft.player.displayClientMessage(Component.translatable("message.ancientempires.npc.skin_unavailable"), true);
            }
            return;
        }
        NpcPopupScreen.open(new NpcSkinSelector(npc));
    }

    @Override
    protected void init()
    {
        super.init();
        this.factionChips.clear();
        this.classChips.clear();
        this.addRenderableWidget(new NpcButton(this.left + PANEL_W - 18, this.top + 4, 12, 12, Component.translatable("gui.cancel"),
                (graphics, x, y, width, height) -> NpcGui.iconCentered(graphics, NpcGui.Icon.CLOSE, x, y, width, height, 8, 8),
                pressed ->
                {
                    if (pressed != 0)
                    {
                        return false;
                    }
                    this.onClose();
                    return true;
                }));

        this.arrow(MARGIN, ROW_Y, "<", true, -1);
        this.arrow(PANEL_W - MARGIN - ARROW, ROW_Y, ">", true, 1);
        for (int index = 0; index < FACTION_SLOTS; index++)
        {
            this.factionChips.add(this.chip(CHIP_LEFT + index * (FACTION_CHIP_W + 2), ROW_Y, FACTION_CHIP_W, true));
        }
        this.arrow(MARGIN, CLASS_Y, "<", false, -1);
        this.arrow(PANEL_W - MARGIN - ARROW, CLASS_Y, ">", false, 1);
        for (int index = 0; index < CLASS_SLOTS; index++)
        {
            this.classChips.add(this.chip(CHIP_LEFT + index * (CLASS_CHIP_W + 2), CLASS_Y, CLASS_CHIP_W, false));
        }

        // Unbordered, the text sits at the box's top, so the box starts 3 px down to center it.
        EditBox search = new EditBox(this.font, this.left + MARGIN + 4, this.top + SEARCH_Y + 3, PANEL_W - MARGIN * 2 - 8, SEARCH_H - 3,
                Component.translatable("gui.ancientempires.skin.search"));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setTextColor(NpcGui.NAME_INK);
        search.setHint(Component.translatable("gui.ancientempires.skin.search").withStyle(style -> style.withColor(NpcGui.NAME_HINT & 0xFFFFFF)));
        search.setValue(this.query);
        search.setResponder(this::queryChanged);
        this.addRenderableWidget(search);

        this.addRenderableWidget(new SkinList(this.left + MARGIN, this.top + BODY_Y));
        this.addRenderableWidget(new NpcButton(this.left + PREVIEW_X, this.top + PANEL_H - MARGIN - 16, PREVIEW_W, 16,
                Component.translatable("gui.ancientempires.skin.apply"),
                (graphics, x, y, width, height) -> FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.skin.apply").getString(),
                        x + 2, y, width - 4, height, NpcGui.NAME_INK, true, false),
                this::apply));
        this.layout();
    }

    private void arrow(int x, int y, String label, boolean faction, int delta)
    {
        this.addRenderableWidget(new NpcButton(this.left + x, this.top + y, ARROW, CHIP_H, Component.literal(label),
                (graphics, left, top, width, height) -> FitText.draw(graphics, this.font, label, left + 2, top, width - 4, height, NpcGui.NAME_INK, true, false),
                pressed -> this.changePage(faction, delta, pressed))
                // A dead page stays visible and does not take a hover color.
                .live(() -> this.canTurn(faction, delta))
                .fill(() -> this.canTurn(faction, delta) ? NpcGui.BUTTON : NpcGui.BUTTON_OFF));
    }

    private Chip chip(int x, int y, int width, boolean faction)
    {
        Chip chip = new Chip();
        chip.button = new NpcButton(this.left + x, this.top + y, width, CHIP_H, Component.empty(),
                (graphics, left, top, w, h) -> FitText.draw(graphics, this.font, chip.label, left + 2, top, w - 4, h, NpcGui.NAME_INK, true, false),
                pressed -> faction ? this.chooseFaction(chip, pressed) : this.chooseClass(chip, pressed))
                .fill(() -> chip.id.equals(faction ? this.factionId : this.classId) ? NpcGui.BUTTON_ON : NpcGui.BUTTON);
        this.addRenderableWidget(chip.button);
        return chip;
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        int left = this.left;
        int top = this.top;
        FitText.draw(graphics, this.font, this.title.getString(), left + MARGIN, top + 4, PANEL_W - 28, 12, NpcGui.NAME_INK, false, true);
        FitText.draw(graphics, this.font, pageLabel(this.factionPage, NpcSkins.factions().size(), FACTION_SLOTS),
                left + CHIP_RIGHT + 2, top + ROW_Y, COUNTER_W, CHIP_H, NpcGui.NAME_HINT, true, false);
        FitText.draw(graphics, this.font, this.classPageLabel(), left + CHIP_RIGHT + 2, top + CLASS_Y, COUNTER_W, CHIP_H, NpcGui.NAME_HINT, true, false);
        graphics.fill(left + MARGIN, top + SEARCH_Y, left + PANEL_W - MARGIN, top + SEARCH_Y + SEARCH_H, NpcGui.INK);
        graphics.fill(left + PREVIEW_X, top + BODY_Y, left + PREVIEW_X + PREVIEW_W, top + BODY_Y + PREVIEW_H, NpcGui.INK);
        this.previewRenderer.render(graphics, left + PREVIEW_X + 2, top + BODY_Y + 2, PREVIEW_W - 4, PREVIEW_H - 4, mouseX, mouseY);
        NpcSkins.Entry entry = NpcSkins.byId(this.selected);
        if (entry != null)
        {
            FitText.draw(graphics, this.font, entry.name(), left + PREVIEW_X, top + BODY_Y + PREVIEW_H + 2, PREVIEW_W, 12, NpcGui.NAME_INK, true, false);
        }
    }

    @Override
    public void removed()
    {
        this.preview.discard();
        super.removed();
    }

    private boolean canTurn(boolean faction, int delta)
    {
        int page = faction ? this.factionPage : this.classPage;
        int next = page + delta;
        return next >= 0 && next < this.pages(faction);
    }

    private int pages(boolean faction)
    {
        if (faction)
        {
            return pageCount(NpcSkins.factions().size(), FACTION_SLOTS);
        }
        NpcSkins.Faction group = NpcSkins.faction(this.factionId);
        int count = group == null || group.flat() ? 0 : group.classes().size();
        return pageCount(count, CLASS_SLOTS);
    }

    private boolean changePage(boolean faction, int delta, int button)
    {
        if (button != 0)
        {
            return false;
        }
        if (!this.canTurn(faction, delta))
        {
            return true;
        }
        if (faction)
        {
            this.factionPage += delta;
        }
        else
        {
            this.classPage += delta;
        }
        this.layout();
        return true;
    }

    private boolean chooseFaction(Chip chip, int button)
    {
        if (button != 0 || chip.id.isEmpty() || chip.id.equals(this.factionId))
        {
            return button == 0;
        }
        this.factionId = chip.id;
        this.classPage = 0;
        NpcSkins.Faction faction = NpcSkins.faction(this.factionId);
        this.classId = "";
        if (faction != null && !faction.classes().isEmpty())
        {
            this.classId = faction.classes().get(0).id();
        }
        this.focusFirstSkin();
        this.layout();
        return true;
    }

    private boolean chooseClass(Chip chip, int button)
    {
        if (button != 0 || chip.id.isEmpty() || chip.id.equals(this.classId))
        {
            return button == 0;
        }
        this.classId = chip.id;
        this.focusFirstSkin();
        this.layout();
        return true;
    }

    private void focusFirstSkin()
    {
        NpcSkins.NpcClass npcClass = NpcSkins.npcClass(this.factionId, this.classId);
        if (npcClass == null || npcClass.skins().isEmpty())
        {
            return;
        }
        NpcSkins.Entry entry = npcClass.skins().get(0);
        this.selected = entry.id();
        this.preview.assignSkin(entry.id());
    }

    private void select(NpcSkins.Entry entry)
    {
        this.selected = entry.id();
        this.preview.assignSkin(entry.id());
    }

    private boolean apply(int button)
    {
        if (button != 0)
        {
            return false;
        }
        if (NpcSkins.byId(this.selected) == null)
        {
            return true;
        }
        this.npc.assignSkin(this.selected);
        ModNetwork.sendSkin(this.npc.getId(), this.selected);
        this.onClose();
        return true;
    }

    private void queryChanged(String value)
    {
        String next = value == null ? "" : value;
        if (next.equals(this.query))
        {
            return;
        }
        this.query = next;
        this.layout();
    }

    private static String pageLabel(int page, int count, int slots)
    {
        if (count <= 0)
        {
            return "";
        }
        return (page + 1) + "/" + pageCount(count, slots);
    }

    private String classPageLabel()
    {
        NpcSkins.Faction faction = NpcSkins.faction(this.factionId);
        if (faction == null || faction.flat())
        {
            return "";
        }
        return pageLabel(this.classPage, faction.classes().size(), CLASS_SLOTS);
    }

    private static int pageCount(int count, int slots)
    {
        if (count <= 0)
        {
            return 1;
        }
        return (count + slots - 1) / slots;
    }

    /** Binds the chips to the pages and filters the list; run after any change of page, faction, class, or search. */
    private void layout()
    {
        List<NpcSkins.Faction> factions = NpcSkins.factions();
        List<String> factionIds = new ArrayList<>();
        List<String> factionNames = new ArrayList<>();
        for (NpcSkins.Faction faction : factions)
        {
            factionIds.add(faction.id());
            factionNames.add(faction.name());
        }
        bindChips(this.factionChips, factionIds, factionNames, this.factionPage);

        NpcSkins.Faction faction = NpcSkins.faction(this.factionId);
        boolean flat = faction != null && faction.flat();
        List<String> classIds = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        if (faction != null && !flat)
        {
            int classPages = pageCount(faction.classes().size(), CLASS_SLOTS);
            if (this.classPage >= classPages)
            {
                this.classPage = classPages - 1;
            }
            for (NpcSkins.NpcClass npcClass : faction.classes())
            {
                classIds.add(npcClass.id());
                classNames.add(npcClass.name());
            }
        }
        else if (flat && !faction.classes().isEmpty() && faction.npcClass(this.classId) == null)
        {
            this.classId = faction.classes().get(0).id();
        }
        bindChips(this.classChips, classIds, classNames, this.classPage);

        String needle = this.query.toLowerCase(Locale.ROOT).trim();
        NpcSkins.NpcClass npcClass = NpcSkins.npcClass(this.factionId, this.classId);
        this.shown.clear();
        if (npcClass != null)
        {
            for (NpcSkins.Entry entry : npcClass.skins())
            {
                if (matches(entry, needle))
                {
                    this.shown.add(entry);
                }
            }
        }
        String key = this.factionId + "\n" + this.classId + "\n" + needle;
        if (!key.equals(this.listKey))
        {
            this.listKey = key;
            this.scroll = 0;
        }
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll());
    }

    private static void bindChips(List<Chip> chips, List<String> ids, List<String> labels, int page)
    {
        int start = page * chips.size();
        for (int index = 0; index < chips.size(); index++)
        {
            Chip chip = chips.get(index);
            int source = start + index;
            boolean used = source < ids.size();
            chip.id = used ? ids.get(source) : "";
            chip.label = used ? labels.get(source) : "";
            chip.button.visible = used;
        }
    }

    private int maxScroll()
    {
        return Math.max(0, this.shown.size() * ROW_H - LIST_H);
    }

    private static boolean matches(NpcSkins.Entry entry, String needle)
    {
        if (needle.isEmpty())
        {
            return true;
        }
        String name = entry.name().toLowerCase(Locale.ROOT);
        String id = entry.id().toLowerCase(Locale.ROOT);
        if (isNumber(needle))
        {
            return hasNumber(name, needle) || hasNumber(id, needle);
        }
        return name.contains(needle) || id.contains(needle);
    }

    private static boolean isNumber(String needle)
    {
        for (int index = 0; index < needle.length(); index++)
        {
            if (!Character.isDigit(needle.charAt(index)))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNumber(String text, String needle)
    {
        int limit = needle.length();
        for (int index = 0; index <= text.length() - limit; index++)
        {
            if (!text.startsWith(needle, index))
            {
                continue;
            }
            boolean before = index > 0 && Character.isDigit(text.charAt(index - 1));
            int after = index + limit;
            boolean afterDigit = after < text.length() && Character.isDigit(text.charAt(after));
            if (!before && !afterDigit)
            {
                return true;
            }
        }
        return false;
    }

    private static final class Chip
    {
        private String id = "";
        private String label = "";
        private NpcButton button;
    }

    /**
     * The skins of the chosen class (or the search's matches), one row each,
     * drawn only where they show, so the custom catalog scrolls without a widget
     * per skin. Wheel or drag the bar to scroll.
     */
    private final class SkinList extends AbstractWidget
    {
        private boolean draggingBar;

        private SkinList(int x, int y)
        {
            super(x, y, LIST_W, LIST_H, Component.translatable("gui.ancientempires.skin.title"));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            NpcSkinSelector screen = NpcSkinSelector.this;
            int x = this.getX();
            int y = this.getY();
            graphics.fill(x, y, x + this.width, y + this.height, NpcGui.INK);
            if (screen.shown.isEmpty())
            {
                FitText.draw(graphics, screen.font, Component.translatable("gui.ancientempires.skin.empty").getString(), x + 2, y, LIST_W - 16, ROW_H,
                        NpcGui.NAME_HINT, false, false);
                return;
            }
            graphics.enableScissor(x, y, x + this.width, y + this.height);
            int first = screen.scroll / ROW_H;
            int last = Math.min(screen.shown.size() - 1, (screen.scroll + this.height) / ROW_H);
            NpcSkins.Entry hovered = this.rowAt(mouseX, mouseY);
            for (int row = first; row <= last; row++)
            {
                NpcSkins.Entry entry = screen.shown.get(row);
                int rowY = y + row * ROW_H - screen.scroll;
                int fill = entry == hovered ? NpcGui.BUTTON_HOVER : entry.id().equals(screen.selected) ? NpcGui.BUTTON_ON : NpcGui.BUTTON;
                graphics.fill(x, rowY, x + ROW_W, rowY + ROW_H, fill);
                FitText.draw(graphics, screen.font, entry.name(), x + 2, rowY, ROW_W - 4, ROW_H, NpcGui.NAME_INK, false, false);
            }
            graphics.disableScissor();
            int max = screen.maxScroll();
            if (max > 0)
            {
                int barX = x + this.width - SCROLL_BAR_W - 2;
                int thumbH = this.thumbHeight();
                int thumbY = y + (this.height - thumbH) * screen.scroll / max;
                graphics.fill(barX, y, barX + SCROLL_BAR_W, y + this.height, NpcGui.BUTTON_OFF);
                graphics.fill(barX, thumbY, barX + SCROLL_BAR_W, thumbY + thumbH, this.draggingBar ? NpcGui.BUTTON_HOVER : NpcGui.BUTTON_ON);
            }
            // Names drawn shrunk (or cut) read in full on hover.
            if (hovered != null && !FitText.fits(screen.font, hovered.name(), ROW_W - 4))
            {
                screen.setTooltipForNextRenderPass(Component.literal(hovered.name()));
            }
        }

        private int thumbHeight()
        {
            int content = NpcSkinSelector.this.shown.size() * ROW_H;
            return Math.max(12, this.height * this.height / Math.max(1, content));
        }

        private NpcSkins.Entry rowAt(double mouseX, double mouseY)
        {
            if (mouseX < this.getX() || mouseX >= this.getX() + ROW_W || mouseY < this.getY() || mouseY >= this.getY() + this.height)
            {
                return null;
            }
            int row = (int) (mouseY - this.getY() + NpcSkinSelector.this.scroll) / ROW_H;
            List<NpcSkins.Entry> shown = NpcSkinSelector.this.shown;
            return row >= 0 && row < shown.size() ? shown.get(row) : null;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button)
        {
            // A release outside the list never reaches it.
            this.draggingBar = false;
            if (button != 0 || !this.isMouseOver(mouseX, mouseY))
            {
                return false;
            }
            if (NpcSkinSelector.this.maxScroll() > 0 && mouseX >= this.getX() + this.width - SCROLL_BAR_W - 3)
            {
                this.draggingBar = true;
                this.scrollToMouse(mouseY);
                return true;
            }
            NpcSkins.Entry entry = this.rowAt(mouseX, mouseY);
            if (entry != null)
            {
                NpcSkinSelector.this.select(entry);
                this.playDownSound(Minecraft.getInstance().getSoundManager());
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
        {
            if (!this.draggingBar)
            {
                return false;
            }
            this.scrollToMouse(mouseY);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button)
        {
            this.draggingBar = false;
            return false;
        }

        private void scrollToMouse(double mouseY)
        {
            int thumbH = this.thumbHeight();
            double track = Math.max(1, this.height - thumbH);
            double fraction = (mouseY - this.getY() - thumbH / 2.0D) / track;
            NpcSkinSelector screen = NpcSkinSelector.this;
            screen.scroll = Mth.clamp((int) Math.round(fraction * screen.maxScroll()), 0, screen.maxScroll());
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta)
        {
            NpcSkinSelector screen = NpcSkinSelector.this;
            screen.scroll = Mth.clamp(screen.scroll - (int) Math.round(delta * ROW_H), 0, screen.maxScroll());
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output)
        {
            this.defaultButtonNarrationText(output);
        }
    }
}
