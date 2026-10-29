package com.ancientempires.client;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcArmy;
import com.ancientempires.entity.npc.NpcChain;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcFormation;
import com.ancientempires.entity.npc.NpcOrder;
import com.ancientempires.entity.npc.NpcRank;
import com.ancientempires.entity.npc.NpcStance;
import com.ancientempires.network.ArmyActionPacket;
import com.ancientempires.network.ArmyPacket;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.registry.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The War Table (K; docs/army-command-design.md): the player's army laid out
 * as a banner hall, the ledger of the unit picked, and the reserves.
 * <p>
 * The chart hangs a banner for every leader (sergeant and up, or anyone
 * leading men), its general's over its captains' over its sergeants', joined
 * by ink lines: the cloth in the unit's colour (by its number), the leader's
 * face on it, the rank's insignia, and the name, strength, and order beneath;
 * the tails shorten as the unit's health drops and the cloth darkens when its
 * leader breaks. Drag the background to pan, scroll to zoom.
 * <p>
 * The ledger is the picked unit's page: the leader in full, its numbers, its
 * order and settings (click to change), and the roll of its soldiers. The
 * reserves along the bottom are soldiers in no unit.
 * <p>
 * Everything is organised by dragging: a face onto a banner enlists that
 * soldier (or moves it between units), a banner onto a higher one puts that
 * unit under it, a face onto the reserves or a banner onto the bare table
 * takes it out. A drop that cannot be made shows why and sends nothing; the
 * server checks every one again and sends the army back, which redraws this.
 * Hovering a banner or a face outlines those soldiers in the world. Opened
 * while looking at one of your soldiers, it starts with that soldier in hand.
 */
@OnlyIn(Dist.CLIENT)
public final class WarTableScreen extends Screen
{
    private static final int LEATHER = 0xFF1C1610;
    private static final int TABLE = 0xFF2B2218;
    private static final int INK = 0xFF8A6A3A;
    private static final int GOLD = 0xFFE2C27A;
    private static final int PARCHMENT = 0xFFE8D9B5;
    private static final int PARCHMENT_EDGE = 0xFFB89A68;
    private static final int TEXT_DARK = 0xFF3A2A14;
    private static final int TEXT_MID = 0xFF6B5434;
    private static final int LIGHT = 0xFFE9DCC0;
    private static final int GOOD = 0xFF4F8A3A;
    private static final int FAIR = 0xFFB8862A;
    private static final int BAD = 0xFFC0392B;
    /** Heraldic colours by unit number (0: no number). */
    private static final int[] UNIT_COLOURS = {0xFF5A4E40, 0xFF7A1F1F, 0xFF1F3F6B, 0xFF2F5A2A, 0xFF8A6A1F, 0xFF4F2A6B, 0xFF1F5A5A, 0xFF8A3F1F, 0xFF3A4250,
            0xFF2A2A2A};
    private static final int HEADER = 26;
    private static final int RESERVES = 46;
    private static final int ROW = 14;
    private static final int ROW_GAP = 40;
    private static final int SIBLING_GAP = 16;
    private static final int LABELS = 28;
    private static final double NEARBY = 64.0D;
    /** Ticks a located unit keeps glowing after the table closes. */
    private static final int LOCATE_TICKS = 200;

    /** A soldier to start with in hand: the one looked at when the key was pressed. */
    @Nullable
    private static UUID carryNext;

    /** A banner on the chart, in chart space: its middle, its top, its size. */
    private record Node(ArmyPacket.Entry entry, int depth, double x, double y, int w, int h)
    {
    }

    /** Something clickable this frame: an action, or a soldier or unit that can be picked up. */
    private record Hit(int x, int y, int w, int h, @Nullable Runnable action, @Nullable UUID subject, @Nullable Component tip)
    {
        boolean over(double mx, double my)
        {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    private List<ArmyPacket.Entry> entries;
    private final Map<UUID, ArmyPacket.Entry> byId = new HashMap<>();
    private final Map<UUID, List<ArmyPacket.Entry>> children = new HashMap<>();
    private final List<Node> nodes = new ArrayList<>();
    private final List<ArmyPacket.Entry> reserves = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private double treeWidth;
    private double treeHeight;
    @Nullable
    private UUID selected;
    /** The soldier or unit in hand, and whether it was picked up by a click (dropped by the next) rather than a drag. */
    @Nullable
    private UUID held;
    private boolean carried;
    @Nullable
    private UUID pressed;
    private double pressX;
    private double pressY;
    private boolean panning;
    private double panX;
    private double panY;
    private float zoom = 1.0F;
    private int rosterScroll;
    private boolean nearbyOnly;
    @Nullable
    private UUID glowFor;
    private EditBox nameBox;
    /** The mouse this frame, for the hand-drawn buttons. */
    private int mouseX;
    private int mouseY;

    private WarTableScreen(List<ArmyPacket.Entry> entries)
    {
        super(Component.translatable("gui.ancientempires.war_table.title"));
        this.entries = entries;
    }

    /** The next opening starts with this soldier in hand. */
    public static void carryNext(@Nullable UUID soldier)
    {
        carryNext = soldier;
    }

    /** The army arrived: opens the table (from the key) or refreshes the open one (after an action). */
    public static void show(boolean open, List<ArmyPacket.Entry> entries)
    {
        ClientArmy.set(entries);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WarTableScreen screen)
        {
            screen.update(entries);
        }
        else if (open)
        {
            WarTableScreen screen = new WarTableScreen(entries);
            if (carryNext != null && entries.stream().anyMatch(entry -> entry.id().equals(carryNext)))
            {
                screen.held = carryNext;
                screen.carried = true;
            }
            carryNext = null;
            minecraft.setScreen(screen);
        }
    }

    private void update(List<ArmyPacket.Entry> entries)
    {
        this.entries = entries;
        this.rebuild();
    }

    @Override
    protected void init()
    {
        this.nameBox = new EditBox(this.font, 0, 0, 100, 14, Component.translatable("gui.ancientempires.army.unit_name"));
        this.nameBox.setMaxLength(NpcArmy.NAME_LENGTH);
        this.nameBox.setBordered(true);
        this.addRenderableWidget(this.nameBox);
        this.rebuild();
    }

    // ---- Layout -----------------------------------------------------------------------------------

    private int ledgerWidth()
    {
        return Mth.clamp(this.width * 36 / 100, 170, 240);
    }

    private int chartLeft()
    {
        return 8;
    }

    private int chartRight()
    {
        return this.width - this.ledgerWidth() - 14;
    }

    private int chartTop()
    {
        return HEADER + 4;
    }

    private int chartBottom()
    {
        return this.height - RESERVES - 6;
    }

    private boolean shown(ArmyPacket.Entry entry)
    {
        return !this.nearbyOnly || entry.distance() <= NEARBY;
    }

    /** A banner for every leader: anyone leading men, or of sergeant's rank and up. */
    private static boolean leads(ArmyPacket.Entry entry)
    {
        return entry.members() > 0 || entry.rank() > NpcRank.SOLDIER.ordinal();
    }

    private void rebuild()
    {
        this.byId.clear();
        this.children.clear();
        for (ArmyPacket.Entry entry : this.entries)
        {
            this.byId.put(entry.id(), entry);
        }
        for (ArmyPacket.Entry entry : this.entries)
        {
            if (entry.leader() != null && this.byId.containsKey(entry.leader()))
            {
                this.children.computeIfAbsent(entry.leader(), id -> new ArrayList<>()).add(entry);
            }
        }
        this.nodes.clear();
        this.reserves.clear();
        List<ArmyPacket.Entry> roots = new ArrayList<>();
        for (ArmyPacket.Entry entry : this.entries)
        {
            boolean top = entry.leader() == null || !this.byId.containsKey(entry.leader());
            if (top && leads(entry) && this.shown(entry))
            {
                roots.add(entry);
            }
            else if (top && !leads(entry) && this.shown(entry))
            {
                this.reserves.add(entry);
            }
        }
        // Generals first, then by unit number.
        roots.sort((a, b) -> a.rank() != b.rank() ? b.rank() - a.rank() : a.unitNumber() - b.unitNumber());
        double x = 0.0D;
        double deepest = 0.0D;
        for (ArmyPacket.Entry root : roots)
        {
            double width = this.subtreeWidth(root);
            deepest = Math.max(deepest, this.place(root, 0, x + width / 2.0D));
            x += width + SIBLING_GAP;
        }
        this.treeWidth = Math.max(0.0D, x - SIBLING_GAP);
        this.treeHeight = deepest;
        if (this.selected == null || !this.byId.containsKey(this.selected))
        {
            this.selected = roots.isEmpty() ? null : roots.get(0).id();
        }
        if (this.held != null && !this.byId.containsKey(this.held))
        {
            this.held = null;
        }
        ArmyPacket.Entry unit = this.selected == null ? null : this.byId.get(this.selected);
        if (this.nameBox != null && unit != null && !this.nameBox.isFocused())
        {
            this.nameBox.setValue(unit.unitName());
        }
    }

    private List<ArmyPacket.Entry> officersOf(ArmyPacket.Entry entry)
    {
        List<ArmyPacket.Entry> officers = new ArrayList<>();
        for (ArmyPacket.Entry child : this.children.getOrDefault(entry.id(), List.of()))
        {
            if (leads(child))
            {
                officers.add(child);
            }
        }
        officers.sort((a, b) -> a.unitNumber() - b.unitNumber());
        return officers;
    }

    private List<ArmyPacket.Entry> soldiersOf(ArmyPacket.Entry entry)
    {
        List<ArmyPacket.Entry> soldiers = new ArrayList<>();
        for (ArmyPacket.Entry child : this.children.getOrDefault(entry.id(), List.of()))
        {
            if (!leads(child))
            {
                soldiers.add(child);
            }
        }
        return soldiers;
    }

    private static int bannerWidth(ArmyPacket.Entry entry)
    {
        return entry.rank() >= NpcRank.GENERAL.ordinal() ? 58 : entry.rank() >= NpcRank.CAPTAIN.ordinal() ? 48 : 40;
    }

    private static int bannerHeight(ArmyPacket.Entry entry)
    {
        return entry.rank() >= NpcRank.GENERAL.ordinal() ? 76 : entry.rank() >= NpcRank.CAPTAIN.ordinal() ? 64 : 54;
    }

    private double subtreeWidth(ArmyPacket.Entry entry)
    {
        double own = Math.max(bannerWidth(entry), 64);
        List<ArmyPacket.Entry> officers = this.officersOf(entry);
        double under = 0.0D;
        for (ArmyPacket.Entry officer : officers)
        {
            under += this.subtreeWidth(officer);
        }
        under += Math.max(0, officers.size() - 1) * SIBLING_GAP;
        return Math.max(own, under);
    }

    /** Hangs this banner and those under it; the bottom of the lowest, in chart space. */
    private double place(ArmyPacket.Entry entry, int depth, double middle)
    {
        double y = 40.0D + depth * (76 + LABELS + ROW_GAP);
        int w = bannerWidth(entry);
        int h = bannerHeight(entry);
        this.nodes.add(new Node(entry, depth, middle, y, w, h));
        double bottom = y + h + LABELS;
        List<ArmyPacket.Entry> officers = this.officersOf(entry);
        double total = 0.0D;
        for (ArmyPacket.Entry officer : officers)
        {
            total += this.subtreeWidth(officer);
        }
        total += Math.max(0, officers.size() - 1) * SIBLING_GAP;
        double x = middle - total / 2.0D;
        for (ArmyPacket.Entry officer : officers)
        {
            double width = this.subtreeWidth(officer);
            bottom = Math.max(bottom, this.place(officer, depth + 1, x + width / 2.0D));
            x += width + SIBLING_GAP;
        }
        return bottom;
    }

    /** Where chart space starts on screen: centred in the chart area, moved by panning. */
    private double originX()
    {
        return (this.chartLeft() + this.chartRight()) / 2.0D - this.treeWidth * this.zoom / 2.0D + this.panX;
    }

    private double originY()
    {
        return this.chartTop() + 6 + this.panY;
    }

    private boolean inChart(double mx, double my)
    {
        return mx >= this.chartLeft() && mx < this.chartRight() && my >= this.chartTop() && my < this.chartBottom();
    }

    @Nullable
    private Node nodeAt(double mx, double my)
    {
        if (!this.inChart(mx, my))
        {
            return null;
        }
        double cx = (mx - this.originX()) / this.zoom;
        double cy = (my - this.originY()) / this.zoom;
        for (Node node : this.nodes)
        {
            if (cx >= node.x() - node.w() / 2.0D - 4 && cx <= node.x() + node.w() / 2.0D + 4 && cy >= node.y() - 4 && cy <= node.y() + node.h() + LABELS)
            {
                return node;
            }
        }
        return null;
    }

    // ---- Drawing ----------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.hits.clear();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        graphics.fill(0, 0, this.width, this.height, LEATHER);
        this.drawHeader(graphics);
        this.drawChart(graphics, mouseX, mouseY);
        this.drawLedger(graphics, mouseX, mouseY);
        this.drawReserves(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.drawHeld(graphics, mouseX, mouseY);
        this.hoverGlow(mouseX, mouseY);
        for (Hit hit : this.hits)
        {
            if (hit.tip() != null && hit.over(mouseX, mouseY) && this.held == null)
            {
                graphics.renderTooltip(this.font, hit.tip(), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawHeader(GuiGraphics graphics)
    {
        graphics.fill(0, 0, this.width, HEADER, TABLE);
        graphics.fill(0, HEADER - 1, this.width, HEADER, INK);
        graphics.pose().pushPose();
        graphics.pose().translate(10, 7, 0);
        graphics.pose().scale(1.4F, 1.4F, 1.0F);
        graphics.drawString(this.font, this.title, 0, 0, GOLD);
        graphics.pose().popPose();
        int right = this.width - 10;
        Component count = Component.translatable("gui.ancientempires.war_table.count", this.entries.size());
        right -= this.font.width(count);
        graphics.drawString(this.font, count, right, 9, LIGHT);
        right -= 12;
        right = this.toggle(graphics, right, Component.translatable("gui.ancientempires.war_table.all_loaded"), !this.nearbyOnly, () -> this.setNearby(false));
        right -= 4;
        this.toggle(graphics, right, Component.translatable("gui.ancientempires.war_table.nearby"), this.nearbyOnly, () -> this.setNearby(true));
    }

    private int toggle(GuiGraphics graphics, int right, Component label, boolean on, Runnable action)
    {
        int w = this.font.width(label) + 12;
        int x = right - w;
        graphics.fill(x, 6, right, 20, on ? 0xFF4A3A26 : 0xFF221A12);
        graphics.drawCenteredString(this.font, label, x + w / 2, 9, on ? GOLD : 0xFFA08C6A);
        this.hits.add(new Hit(x, 6, w, 14, action, null, null));
        return x;
    }

    private void setNearby(boolean nearby)
    {
        this.nearbyOnly = nearby;
        this.rebuild();
    }

    private void drawChart(GuiGraphics graphics, int mouseX, int mouseY)
    {
        int left = this.chartLeft();
        int top = this.chartTop();
        int right = this.chartRight();
        int bottom = this.chartBottom();
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, INK);
        graphics.fill(left, top, right, bottom, TABLE);
        if (this.nodes.isEmpty())
        {
            graphics.drawCenteredString(this.font, Component.translatable("gui.ancientempires.war_table.no_leaders"), (left + right) / 2, (top + bottom) / 2 - 4,
                    0xFFA08C6A);
            return;
        }
        graphics.enableScissor(left, top, right, bottom);
        graphics.pose().pushPose();
        graphics.pose().translate(this.originX(), this.originY(), 0);
        graphics.pose().scale(this.zoom, this.zoom, 1.0F);
        Node hovered = this.nodeAt(mouseX, mouseY);
        // You, at the head of it all.
        double middle = this.treeWidth / 2.0D;
        this.drawYou(graphics, (int) middle);
        for (Node node : this.nodes)
        {
            if (node.depth() == 0)
            {
                this.connect(graphics, middle, 30, node.x(), node.y() - 4);
            }
            for (Node other : this.nodes)
            {
                if (node.entry().id().equals(other.entry().leader()))
                {
                    this.connect(graphics, node.x(), node.y() + node.h() + LABELS - 2, other.x(), other.y() - 4);
                }
            }
        }
        for (Node node : this.nodes)
        {
            this.drawBanner(graphics, node, node == hovered);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.war_table.chart_help"), left + 4, bottom - 11, 0xFF7A6A50);
    }

    private void drawYou(GuiGraphics graphics, int middle)
    {
        Minecraft minecraft = Minecraft.getInstance();
        graphics.fill(middle - 26, 4, middle + 26, 28, 0xFF4A3A26);
        graphics.fill(middle - 25, 5, middle + 25, 27, 0xFF221A12);
        if (minecraft.player != null)
        {
            PlayerFaceRenderer.draw(graphics, minecraft.player.getSkinTextureLocation(), middle - 21, 8, 16);
        }
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.war_table.you"), middle - 2, 12, GOLD);
    }

    /** An ink line from a banner down to one under it: down, across, down. */
    private void connect(GuiGraphics graphics, double fromX, double fromY, double toX, double toY)
    {
        int mid = (int) ((fromY + toY) / 2.0D);
        graphics.fill((int) fromX, (int) fromY, (int) fromX + 1, mid + 1, INK);
        graphics.fill((int) Math.min(fromX, toX), mid, (int) Math.max(fromX, toX) + 1, mid + 1, INK);
        graphics.fill((int) toX, mid, (int) toX + 1, (int) toY, INK);
    }

    private void drawBanner(GuiGraphics graphics, Node node, boolean hovered)
    {
        ArmyPacket.Entry entry = node.entry();
        int w = node.w();
        int h = node.h();
        float x0 = (float) (node.x() - w / 2.0D);
        float y0 = (float) node.y();
        float health = this.unitHealth(entry);
        int cloth = UNIT_COLOURS[Mth.clamp(entry.unitNumber(), 0, 9)];
        if (entry.broken())
        {
            cloth = darken(cloth, 0.45F);
        }
        boolean picked = entry.id().equals(this.selected);
        boolean target = this.held != null && hovered;
        if (target)
        {
            int edge = this.refusal(this.held, entry) == null ? GOOD : BAD;
            this.cloth(graphics, x0 - 2, y0 - 2, w + 4, h + 4, 1.0F, edge);
        }
        else if (picked || hovered)
        {
            this.cloth(graphics, x0 - 2, y0 - 2, w + 4, h + 4, health, picked ? GOLD : 0xFFA08C6A);
        }
        this.cloth(graphics, x0, y0, w, h, health, cloth);
        // The pole, with gold finials.
        graphics.fill((int) x0 - 4, (int) y0 - 3, (int) (x0 + w) + 4, (int) y0, 0xFF5A4128);
        graphics.fill((int) x0 - 6, (int) y0 - 4, (int) x0 - 3, (int) y0 + 1, GOLD);
        graphics.fill((int) (x0 + w) + 3, (int) y0 - 4, (int) (x0 + w) + 6, (int) y0 + 1, GOLD);
        int cx = (int) node.x();
        // The leader's face in a gold frame, the number in the corner, the rank's insignia below.
        int face = w >= 56 ? 20 : 16;
        graphics.fill(cx - face / 2 - 1, (int) y0 + 6, cx + face / 2 + 1, (int) y0 + 8 + face, GOLD);
        PlayerFaceRenderer.draw(graphics, skin(entry), cx - face / 2, (int) y0 + 7, face);
        if (entry.unitNumber() > 0)
        {
            graphics.drawString(this.font, String.valueOf(entry.unitNumber()), (int) x0 + 3, (int) y0 + 3, GOLD);
        }
        ItemStack insignia = insignia(entry.rank());
        if (!insignia.isEmpty())
        {
            graphics.renderItem(insignia, cx - 8, (int) y0 + face + 11);
        }
        // Name, strength and order beneath.
        String name = entry.unitName().isEmpty() ? ClientArmy.parse(entry.name()).getString() : entry.unitName();
        int labelY = (int) y0 + h + 3;
        int labelW = Math.max(w + 24, 70);
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(name, labelW), cx, labelY, picked ? GOLD : LIGHT);
        NpcOrder order = NpcOrder.values()[Math.min(entry.order(), NpcOrder.values().length - 1)];
        Component line = Component.literal(this.strength(entry) + " · ").append(Component.translatable("order.ancientempires." + order.id()));
        graphics.drawCenteredString(this.font, line, cx, labelY + 10, 0xFFC8B48C);
        int barW = w;
        bar(graphics, cx - barW / 2, labelY + 20, barW, 2, health, health >= 0.6F ? GOOD : health >= 0.3F ? FAIR : BAD);
        float morale = entry.morale() / 100.0F;
        bar(graphics, cx - barW / 2, labelY + 23, barW, 2, morale, 0xFF3A6AA0);
    }

    /** The cloth: a body and two swallow tails, shorter as the unit's health drops. */
    private void cloth(GuiGraphics graphics, float x, float y, float w, float h, float health, int argb)
    {
        float body = h * 0.74F;
        float tail = (h - body) * (0.35F + 0.65F * Mth.clamp(health, 0.0F, 1.0F));
        float mid = x + w / 2.0F;
        polygon(graphics, argb, x, y, x + w, y, x + w, y + body, x, y + body);
        polygon(graphics, argb, x, y + body, mid, y + body, x, y + body + tail);
        polygon(graphics, argb, mid, y + body, x + w, y + body, x + w, y + body + tail);
    }

    /** "12/16" for a squad, "3 sq · 41" for a captain or general. */
    private String strength(ArmyPacket.Entry entry)
    {
        int soldiers = this.soldiersOf(entry).size();
        int officers = this.officersOf(entry).size();
        if (officers == 0)
        {
            return soldiers + "/" + AncientEmpiresConfig.chainSquadSize;
        }
        return Component.translatable("gui.ancientempires.war_table.companies", officers, this.headcount(entry)).getString();
    }

    /** Everyone under this leader, all the way down. */
    private int headcount(ArmyPacket.Entry entry)
    {
        int count = 0;
        for (ArmyPacket.Entry child : this.children.getOrDefault(entry.id(), List.of()))
        {
            count += 1 + this.headcount(child);
        }
        return count;
    }

    /** The unit's average health share, its leader and soldiers. */
    private float unitHealth(ArmyPacket.Entry entry)
    {
        float total = entry.health();
        int count = 1;
        for (ArmyPacket.Entry soldier : this.soldiersOf(entry))
        {
            total += soldier.health();
            count++;
        }
        return total / count;
    }

    private void drawLedger(GuiGraphics graphics, int mouseX, int mouseY)
    {
        int left = this.width - this.ledgerWidth() - 6;
        int right = this.width - 6;
        int top = this.chartTop();
        int bottom = this.chartBottom();
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, PARCHMENT_EDGE);
        graphics.fill(left, top, right, bottom, PARCHMENT);
        ArmyPacket.Entry unit = this.selected == null ? null : this.byId.get(this.selected);
        this.nameBox.visible = unit != null;
        if (unit == null)
        {
            graphics.drawCenteredString(this.font, Component.translatable("gui.ancientempires.war_table.pick"), (left + right) / 2, top + 20, TEXT_MID);
            return;
        }
        int x = left + 8;
        int y = top + 8;
        // The leader in full (if it is near enough to be seen), else its face.
        graphics.fill(x - 1, y - 1, x + 43, y + 59, PARCHMENT_EDGE);
        graphics.fill(x, y, x + 42, y + 58, 0xFFD8C49A);
        LivingEntity body = this.loaded(unit.id());
        if (body != null)
        {
            graphics.enableScissor(x, y, x + 42, y + 58);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + 21, y + 54, 24, x + 21 - mouseX, y + 20 - mouseY, body);
            graphics.disableScissor();
        }
        else
        {
            PlayerFaceRenderer.draw(graphics, skin(unit), x + 9, y + 17, 24);
        }
        int tx = x + 50;
        int width = right - tx - 6;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(ClientArmy.parse(unit.name()).getString(), width), tx, y, TEXT_DARK, false);
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.war_table.rank_level", ClientArmy.parse(unit.rankName()), unit.level()),
                tx, y + 11, TEXT_MID, false);
        graphics.drawString(this.font, Component.translatable(unit.band()), tx, y + 21, TEXT_MID, false);
        // The unit's name, edited in place; Enter saves it.
        this.nameBox.setX(tx);
        this.nameBox.setY(y + 33);
        this.nameBox.setWidth(Math.max(40, width - 22));
        String number = unit.unitNumber() == 0 ? "-" : String.valueOf(unit.unitNumber());
        this.button(graphics, right - 26, y + 33, 20, 14, Component.literal(number),
                () -> this.send(NpcArmy.Action.NUMBER, unit.id(), null, ""), Component.translatable("gui.ancientempires.war_table.number_tip"));
        y += 64;
        float morale = unit.morale() / 100.0F;
        String state = unit.broken() ? "broken" : unit.morale() < 40.0F ? "shaken" : "steady";
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.war_table.health"), x, y, TEXT_MID, false);
        bar(graphics, x + 48, y + 2, right - x - 56, 4, this.unitHealth(unit), GOOD);
        y += 10;
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.war_table.morale." + state), x, y, TEXT_MID, false);
        bar(graphics, x + 48, y + 2, right - x - 56, 4, morale, 0xFF3A6AA0);
        y += 14;
        // Settings, each a click to change.
        NpcOrder order = NpcOrder.values()[Math.min(unit.order(), NpcOrder.values().length - 1)];
        NpcFormation formation = NpcFormation.values()[Math.min(unit.formation(), NpcFormation.values().length - 1)];
        NpcStance stance = unit.unpackStance();
        int half = (right - x - 12) / 2;
        this.button(graphics, x, y, half, 14, Component.translatable("formation.ancientempires." + formation.id()),
                () -> this.send(NpcArmy.Action.SET_FORMATION, unit.id(), null, formation.next().id()), Component.translatable("gui.ancientempires.war_table.formation_tip"));
        NpcStance.Engage engage = NpcStance.Engage.values()[(stance.engage().ordinal() + 1) % NpcStance.Engage.values().length];
        this.button(graphics, x + half + 4, y, half, 14, Component.translatable("gui.ancientempires.army.command." + engageId(stance.engage())),
                () -> this.send(NpcArmy.Action.SET_ENGAGE, unit.id(), null, engage.name()), Component.translatable("gui.ancientempires.war_table.engage_tip"));
        y += 17;
        NpcStance.Fire fire = NpcStance.Fire.values()[(stance.fire().ordinal() + 1) % NpcStance.Fire.values().length];
        this.button(graphics, x, y, half, 14, Component.translatable("gui.ancientempires.army.command." + fireId(stance.fire())),
                () -> this.send(NpcArmy.Action.SET_FIRE, unit.id(), null, fire.name()), Component.translatable("gui.ancientempires.war_table.fire_tip"));
        this.button(graphics, x + half + 4, y, half, 14, Component.translatable(stance.shieldsUp() ? "gui.ancientempires.war_table.shields_up" : "gui.ancientempires.war_table.shields_down"),
                () -> this.send(NpcArmy.Action.SHIELDS, unit.id(), null, ""), null);
        y += 19;
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.war_table.order", Component.translatable("order.ancientempires." + order.id())),
                x, y, TEXT_DARK, false);
        y += 11;
        int quarter = (right - x - 18) / 4;
        this.button(graphics, x, y, quarter, 14, Component.translatable("gui.ancientempires.war_table.follow"), () -> this.send(NpcArmy.Action.FOLLOW, unit.id(), null, ""), null);
        this.button(graphics, x + quarter + 4, y, quarter, 14, Component.translatable("gui.ancientempires.war_table.hold"), () -> this.send(NpcArmy.Action.HOLD, unit.id(), null, ""),
                Component.translatable("gui.ancientempires.war_table.hold_tip"));
        this.button(graphics, x + (quarter + 4) * 2, y, quarter, 14, Component.translatable("gui.ancientempires.war_table.fall_back"),
                () -> this.send(NpcArmy.Action.FALL_BACK, unit.id(), null, ""), null);
        this.button(graphics, x + (quarter + 4) * 3, y, quarter, 14, Component.translatable("gui.ancientempires.war_table.free"), () -> this.send(NpcArmy.Action.FREE, unit.id(), null, ""),
                null);
        y += 18;
        this.button(graphics, x, y, right - x - 8, 14, Component.translatable("gui.ancientempires.war_table.locate"), () -> this.locate(unit), null);
        y += 20;
        this.drawRoster(graphics, unit, x, y, right - 8, bottom - 6, mouseX, mouseY);
    }

    /** The roll of the unit's own soldiers: face, name, trade, level, health, morale. Each row can be picked up. */
    private void drawRoster(GuiGraphics graphics, ArmyPacket.Entry unit, int left, int top, int right, int bottom, int mouseX, int mouseY)
    {
        List<ArmyPacket.Entry> soldiers = this.soldiersOf(unit);
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.war_table.roster", soldiers.size(), AncientEmpiresConfig.chainSquadSize), left, top, TEXT_DARK,
                false);
        graphics.fill(left, top + 10, right, top + 11, PARCHMENT_EDGE);
        int y = top + 13;
        int rows = Math.max(1, (bottom - y) / ROW);
        this.rosterScroll = Mth.clamp(this.rosterScroll, 0, Math.max(0, soldiers.size() - rows));
        if (soldiers.isEmpty())
        {
            graphics.drawString(this.font, Component.translatable("gui.ancientempires.war_table.roster_empty"), left, y + 2, TEXT_MID, false);
            return;
        }
        for (int i = this.rosterScroll; i < Math.min(soldiers.size(), this.rosterScroll + rows); i++)
        {
            ArmyPacket.Entry soldier = soldiers.get(i);
            boolean over = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW;
            if (over)
            {
                graphics.fill(left - 2, y - 1, right + 2, y + ROW - 1, 0x30000000);
            }
            PlayerFaceRenderer.draw(graphics, skin(soldier), left, y, 10);
            int nameW = (right - left) * 42 / 100;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(ClientArmy.parse(soldier.name()).getString(), nameW), left + 13, y + 1, TEXT_DARK, false);
            String role = Component.translatable("role.ancientempires." + soldier.role()).getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(role, (right - left) * 28 / 100), left + 15 + nameW, y + 1, TEXT_MID, false);
            String level = "Lv " + soldier.level();
            int healthX = right - 22;
            graphics.drawString(this.font, level, healthX - 4 - this.font.width(level), y + 1, TEXT_MID, false);
            bar(graphics, healthX, y + 3, 16, 3, soldier.health(), soldier.health() >= 0.6F ? GOOD : soldier.health() >= 0.3F ? FAIR : BAD);
            int pip = soldier.broken() ? BAD : soldier.morale() >= 60.0F ? GOOD : FAIR;
            graphics.fill(right - 4, y + 2, right, y + 6, pip);
            this.hits.add(new Hit(left, y, right - left, ROW, null, soldier.id(), Component.translatable(soldier.band())));
            y += ROW;
        }
    }

    private void drawReserves(GuiGraphics graphics, int mouseX, int mouseY)
    {
        int top = this.height - RESERVES;
        int bottom = this.height - 6;
        boolean dropping = this.held != null && mouseY >= top - 2;
        graphics.fill(7, top - 1, this.width - 7, bottom + 1, dropping ? GOLD : INK);
        graphics.fill(8, top, this.width - 8, bottom, TABLE);
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.war_table.reserves"), 14, top + 5, GOLD);
        graphics.drawString(this.font, Component.translatable(this.reserves.isEmpty() ? "gui.ancientempires.war_table.reserves_empty"
                : "gui.ancientempires.war_table.reserves_help"), 14, top + 27, 0xFF7A6A50);
        int x = 90;
        for (ArmyPacket.Entry soldier : this.reserves)
        {
            if (x + 18 > this.width - 12)
            {
                break;
            }
            if (soldier.id().equals(this.held))
            {
                x += 21;
                continue;
            }
            graphics.fill(x - 1, top + 4, x + 19, top + 24, mouseX >= x && mouseX < x + 18 && mouseY >= top + 5 && mouseY < top + 23 ? GOLD : 0xFF4A3A26);
            PlayerFaceRenderer.draw(graphics, skin(soldier), x, top + 5, 18);
            this.hits.add(new Hit(x, top + 5, 18, 18, null, soldier.id(), Component.translatable("gui.ancientempires.war_table.reserve_tip",
                    ClientArmy.parse(soldier.name()), Component.translatable("role.ancientempires." + soldier.role()), soldier.level())));
            x += 21;
        }
    }

    /** The soldier or unit in hand, at the cursor, with whether it can go where it is over. */
    private void drawHeld(GuiGraphics graphics, int mouseX, int mouseY)
    {
        ArmyPacket.Entry held = this.held == null ? null : this.byId.get(this.held);
        if (held == null)
        {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(mouseX - 11, mouseY - 11, mouseX + 11, mouseY + 11, GOLD);
        PlayerFaceRenderer.draw(graphics, skin(held), mouseX - 10, mouseY - 10, 20);
        Component note = this.dropNote(mouseX, mouseY, held);
        if (note != null)
        {
            int w = this.font.width(note);
            graphics.fill(mouseX + 14, mouseY - 4, mouseX + 20 + w, mouseY + 8, 0xE0101010);
            graphics.drawString(this.font, note, mouseX + 17, mouseY - 2, LIGHT);
        }
        graphics.pose().popPose();
    }

    @Nullable
    private Component dropNote(double mouseX, double mouseY, ArmyPacket.Entry held)
    {
        Node node = this.nodeAt(mouseX, mouseY);
        if (node != null)
        {
            String refusal = this.refusal(held.id(), node.entry());
            Component unit = ClientArmy.parse(node.entry().name());
            return refusal == null ? Component.translatable("gui.ancientempires.war_table.drop_under", unit) : Component.translatable(refusal);
        }
        if (mouseY >= this.height - RESERVES - 2 && held.leader() != null)
        {
            return Component.translatable("gui.ancientempires.war_table.drop_reserves");
        }
        if (this.inChart(mouseX, mouseY) && held.leader() != null && leads(held))
        {
            return Component.translatable("gui.ancientempires.war_table.drop_detach");
        }
        return this.carried ? Component.translatable("gui.ancientempires.war_table.carrying") : null;
    }

    private void button(GuiGraphics graphics, int x, int y, int w, int h, Component label, Runnable action, @Nullable Component tip)
    {
        boolean over = this.mouseX >= x && this.mouseX < x + w && this.mouseY >= y && this.mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, over ? 0xFF8A6A3A : PARCHMENT_EDGE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, over ? 0xFFF3E6C8 : 0xFFDCCBA4);
        graphics.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(label.getString(), w - 4)), x + w / 2, y + 3, TEXT_DARK);
        this.hits.add(new Hit(x, y, w, h, action, null, tip));
    }

    // ---- Rules the server checks again ------------------------------------------------------------

    /**
     * Why {@code subject} cannot go under {@code target} (a lang key), or null
     * if it can: as {@link NpcChain#toggle} rules, read from the list.
     */
    @Nullable
    private String refusal(UUID subject, ArmyPacket.Entry target)
    {
        ArmyPacket.Entry moved = this.byId.get(subject);
        if (moved == null || moved.id().equals(target.id()))
        {
            return "gui.ancientempires.war_table.refuse.self";
        }
        if (target.id().equals(moved.leader()))
        {
            return "gui.ancientempires.war_table.refuse.already";
        }
        if (target.rank() == NpcRank.SOLDIER.ordinal())
        {
            return "gui.ancientempires.war_table.refuse.not_leader";
        }
        if (moved.rank() >= target.rank())
        {
            return "gui.ancientempires.war_table.refuse.rank";
        }
        boolean officer = moved.rank() != NpcRank.SOLDIER.ordinal();
        NpcRank rank = NpcRank.values()[Math.min(target.rank(), NpcRank.values().length - 1)];
        int officers = 0;
        int soldiers = 0;
        for (ArmyPacket.Entry child : this.children.getOrDefault(target.id(), List.of()))
        {
            if (child.rank() != NpcRank.SOLDIER.ordinal())
            {
                officers++;
            }
            else
            {
                soldiers++;
            }
        }
        if (officer ? officers >= NpcChain.officerCap(rank) : soldiers >= AncientEmpiresConfig.chainSquadSize)
        {
            return "gui.ancientempires.war_table.refuse.full";
        }
        Set<UUID> seen = new HashSet<>();
        for (ArmyPacket.Entry above = target; above != null && seen.add(above.id()); above = above.leader() == null ? null : this.byId.get(above.leader()))
        {
            if (above.id().equals(subject))
            {
                return "gui.ancientempires.war_table.refuse.loop";
            }
        }
        return null;
    }

    // ---- Input ------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (super.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
        if (this.nameBox.isFocused())
        {
            this.nameBox.setFocused(false);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        {
            this.held = null;
            this.carried = false;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
        {
            return false;
        }
        if (this.held != null && this.carried)
        {
            // Carried by a click: this click drops it.
            this.drop(mouseX, mouseY);
            return true;
        }
        for (Hit hit : this.hits)
        {
            if (hit.over(mouseX, mouseY))
            {
                if (hit.action() != null)
                {
                    hit.action().run();
                    return true;
                }
                if (hit.subject() != null)
                {
                    this.pressed = hit.subject();
                    this.pressX = mouseX;
                    this.pressY = mouseY;
                    return true;
                }
            }
        }
        Node node = this.nodeAt(mouseX, mouseY);
        if (node != null)
        {
            this.pressed = node.entry().id();
            this.pressX = mouseX;
            this.pressY = mouseY;
            return true;
        }
        if (this.inChart(mouseX, mouseY))
        {
            this.panning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if (this.panning)
        {
            this.panX += dragX;
            this.panY += dragY;
            return true;
        }
        if (this.pressed != null && this.held == null && (Math.abs(mouseX - this.pressX) > 4.0D || Math.abs(mouseY - this.pressY) > 4.0D))
        {
            this.held = this.pressed;
            this.carried = false;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        this.panning = false;
        UUID pressed = this.pressed;
        this.pressed = null;
        if (this.held != null && !this.carried)
        {
            this.drop(mouseX, mouseY);
            return true;
        }
        if (pressed != null)
        {
            ArmyPacket.Entry entry = this.byId.get(pressed);
            if (entry != null && leads(entry))
            {
                // A click on a banner opens its page.
                this.selected = pressed;
                this.rosterScroll = 0;
                this.nameBox.setValue(entry.unitName());
            }
            else if (entry != null)
            {
                // A click on a face picks it up, to drop with the next click.
                this.held = pressed;
                this.carried = true;
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Puts what is in hand where the mouse is: under a banner, into the reserves, or off on its own. */
    private void drop(double mouseX, double mouseY)
    {
        UUID subject = this.held;
        this.held = null;
        this.carried = false;
        ArmyPacket.Entry moved = subject == null ? null : this.byId.get(subject);
        if (moved == null)
        {
            return;
        }
        Node node = this.nodeAt(mouseX, mouseY);
        if (node != null)
        {
            String refusal = this.refusal(subject, node.entry());
            if (refusal == null)
            {
                this.send(NpcArmy.Action.MOVE, subject, node.entry().id(), "");
            }
            else if (Minecraft.getInstance().player != null)
            {
                Minecraft.getInstance().player.displayClientMessage(Component.translatable(refusal), true);
            }
            return;
        }
        boolean toReserves = mouseY >= this.height - RESERVES - 2;
        boolean offOnItsOwn = this.inChart(mouseX, mouseY) && leads(moved);
        if (moved.leader() != null && (toReserves || offOnItsOwn))
        {
            this.send(NpcArmy.Action.REMOVE, subject, null, "");
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (this.inChart(mouseX, mouseY))
        {
            // Zoom about the cursor.
            float before = this.zoom;
            this.zoom = Mth.clamp(this.zoom * (delta > 0.0D ? 1.15F : 1.0F / 1.15F), 0.4F, 2.0F);
            double cx = (mouseX - this.originX()) / before;
            double cy = (mouseY - this.originY()) / before;
            this.panX += (mouseX - this.originX()) - cx * this.zoom;
            this.panY += (mouseY - this.originY()) - cy * this.zoom;
            return true;
        }
        if (mouseX >= this.width - this.ledgerWidth() - 6)
        {
            this.rosterScroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers)
    {
        if (this.nameBox.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER))
        {
            if (this.selected != null)
            {
                this.send(NpcArmy.Action.RENAME, this.selected, null, this.nameBox.getValue());
            }
            this.nameBox.setFocused(false);
            return true;
        }
        if (this.held != null && key == GLFW.GLFW_KEY_ESCAPE)
        {
            this.held = null;
            this.carried = false;
            return true;
        }
        if (!this.nameBox.isFocused() && ModKeys.COMMAND.matches(key, scanCode))
        {
            this.onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void send(NpcArmy.Action action, @Nullable UUID subject, @Nullable UUID other, String text)
    {
        if (subject != null)
        {
            ModNetwork.sendToServer(new ArmyActionPacket(false, action, subject, other, text));
        }
    }

    /** The unit outlined in the world for a while, and the table closed so it can be found. */
    private void locate(ArmyPacket.Entry unit)
    {
        ArmyPicks.glowFor(this.everyoneIn(unit), LOCATE_TICKS);
        this.glowFor = null;
        this.onClose();
    }

    private Set<UUID> everyoneIn(ArmyPacket.Entry unit)
    {
        Set<UUID> all = new HashSet<>();
        List<ArmyPacket.Entry> open = new ArrayList<>(List.of(unit));
        while (!open.isEmpty())
        {
            ArmyPacket.Entry next = open.remove(open.size() - 1);
            if (all.add(next.id()))
            {
                open.addAll(this.children.getOrDefault(next.id(), List.of()));
            }
        }
        return all;
    }

    /** What the mouse is over glows in the world: a banner's whole unit, or one soldier. */
    private void hoverGlow(int mouseX, int mouseY)
    {
        UUID over = null;
        Set<UUID> glow = Set.of();
        Node node = this.nodeAt(mouseX, mouseY);
        if (node != null)
        {
            over = node.entry().id();
            glow = this.everyoneIn(node.entry());
        }
        else
        {
            for (Hit hit : this.hits)
            {
                if (hit.subject() != null && hit.over(mouseX, mouseY))
                {
                    over = hit.subject();
                    glow = Set.of(over);
                    break;
                }
            }
        }
        if (over == null ? this.glowFor != null : !over.equals(this.glowFor))
        {
            this.glowFor = over;
            ArmyPicks.glowOnly(glow);
        }
    }

    @Override
    public void removed()
    {
        if (this.glowFor != null)
        {
            ArmyPicks.glowOnly(Set.of());
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    // ---- Helpers ----------------------------------------------------------------------------------

    @Nullable
    private LivingEntity loaded(UUID id)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
        {
            return null;
        }
        for (Entity entity : minecraft.level.entitiesForRendering())
        {
            if (entity instanceof LivingEntity living && entity.getUUID().equals(id))
            {
                return living;
            }
        }
        return null;
    }

    private static ResourceLocation skin(ArmyPacket.Entry entry)
    {
        ResourceLocation skin = entry.skin().isEmpty() ? null : NpcEntity.parseSkinId(entry.skin());
        return skin != null ? skin : DefaultPlayerSkin.getDefaultSkin(entry.id());
    }

    private static ItemStack insignia(int rank)
    {
        if (rank >= NpcRank.GENERAL.ordinal())
        {
            return new ItemStack(ModItems.GENERAL_INSIGNIA.get());
        }
        if (rank >= NpcRank.CAPTAIN.ordinal())
        {
            return new ItemStack(ModItems.CAPTAIN_INSIGNIA.get());
        }
        return rank >= NpcRank.SERGEANT.ordinal() ? new ItemStack(ModItems.SERGEANT_INSIGNIA.get()) : ItemStack.EMPTY;
    }

    private static String engageId(NpcStance.Engage engage)
    {
        return switch (engage)
        {
            case AGGRESSIVE -> "aggressive";
            case HOLD_LINE -> "hold_line";
            case PASSIVE -> "passive";
        };
    }

    private static String fireId(NpcStance.Fire fire)
    {
        return switch (fire)
        {
            case AT_WILL -> "fire_at_will";
            case HOLD -> "hold_fire";
            case VOLLEY -> "volley";
        };
    }

    private static void bar(GuiGraphics graphics, int x, int y, int w, int h, float share, int argb)
    {
        graphics.fill(x, y, x + w, y + h, 0x60000000);
        graphics.fill(x, y, x + Math.round(w * Mth.clamp(share, 0.0F, 1.0F)), y + h, argb);
    }

    private static int darken(int argb, float by)
    {
        int r = (int) ((argb >> 16 & 255) * (1.0F - by));
        int g = (int) ((argb >> 8 & 255) * (1.0F - by));
        int b = (int) ((argb & 255) * (1.0F - by));
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** A filled convex polygon (points in order, x then y), drawn at once in the current pose. */
    private static void polygon(GuiGraphics graphics, int argb, float... points)
    {
        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        int a = argb >>> 24;
        int r = argb >> 16 & 255;
        int g = argb >> 8 & 255;
        int b = argb & 255;
        for (int i = 0; i + 1 < points.length; i += 2)
        {
            buffer.vertex(matrix, points[i], points[i + 1], 0.0F).color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
}
