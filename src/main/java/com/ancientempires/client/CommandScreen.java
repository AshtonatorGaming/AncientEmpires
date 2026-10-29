package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcArmy;
import com.ancientempires.entity.npc.NpcFormation;
import com.ancientempires.entity.npc.NpcOrder;
import com.ancientempires.network.ArmyActionPacket;
import com.ancientempires.network.ArmyPacket;
import com.ancientempires.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The command screen (K; docs/chain-of-command.md, phase 4): the player's army
 * as a tree, top leaders first with their officers and soldiers under them,
 * then the soldiers in no unit. Select a row for its orders (Follow, Free,
 * Hold where they stand, Fall back to you), its formation, rejoining its
 * leader, leaving its unit, moving to another leader (Move, then click the
 * leader), naming the unit, and taking the unit up on the Signal Flag in hand.
 * The server checks every action and sends the army back, which redraws this.
 */
@OnlyIn(Dist.CLIENT)
public final class CommandScreen extends Screen
{
    private static final int ROW_H = 12;
    private static final int TOP = 28;
    private static final int PANEL = 0xC0101010;
    private static final int GOLD = 0xFFC6A56A;
    private static final int SELECTED = 0x60C6A56A;
    private static final int MOVING = 0x6040A0FF;
    private static final int HOVER = 0x30FFFFFF;
    private static final int BUTTON_W = 96;

    /** One line of the tree: a soldier at a depth, or a heading (entry null). */
    private record Row(@Nullable ArmyPacket.Entry entry, int depth, @Nullable Component heading)
    {
    }

    private List<ArmyPacket.Entry> entries;
    private final Map<UUID, ArmyPacket.Entry> byId = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    @Nullable
    private UUID selected;
    @Nullable
    private UUID moving;
    private int scroll;
    private EditBox unitName;

    private CommandScreen(List<ArmyPacket.Entry> entries)
    {
        super(Component.translatable("gui.ancientempires.army.title"));
        this.entries = entries;
    }

    /** The army arrived: opens the screen (from the key) or refreshes the open one (after an action). */
    public static void show(boolean open, List<ArmyPacket.Entry> entries)
    {
        ClientArmy.set(entries);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CommandScreen screen)
        {
            screen.update(entries);
        }
        else if (open)
        {
            minecraft.setScreen(new CommandScreen(entries));
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
        this.rebuild();
    }

    private int listWidth()
    {
        return Math.min(340, (this.width - 40) * 3 / 5);
    }

    private int listLeft()
    {
        return Math.max(8, (this.width - this.listWidth() - BUTTON_W * 2 - 30) / 2);
    }

    private int visibleRows()
    {
        return Math.max(1, (this.height - TOP - 16) / ROW_H);
    }

    /** Lays out the tree and the selected row's buttons again. */
    private void rebuild()
    {
        this.byId.clear();
        for (ArmyPacket.Entry entry : this.entries)
        {
            this.byId.put(entry.id(), entry);
        }
        if (this.selected != null && !this.byId.containsKey(this.selected))
        {
            this.selected = null;
        }
        this.rows.clear();
        Map<UUID, List<ArmyPacket.Entry>> under = new HashMap<>();
        List<ArmyPacket.Entry> tops = new ArrayList<>();
        List<ArmyPacket.Entry> loose = new ArrayList<>();
        for (ArmyPacket.Entry entry : this.entries)
        {
            if (entry.leader() != null && this.byId.containsKey(entry.leader()))
            {
                under.computeIfAbsent(entry.leader(), key -> new ArrayList<>()).add(entry);
            }
            else if (entry.members() > 0 || entry.rank() > 0)
            {
                tops.add(entry);
            }
            else
            {
                loose.add(entry);
            }
        }
        Comparator<ArmyPacket.Entry> order = Comparator.comparingInt((ArmyPacket.Entry entry) -> -entry.rank()).thenComparingDouble(ArmyPacket.Entry::distance);
        tops.sort(order);
        Set<UUID> seen = new HashSet<>();
        for (ArmyPacket.Entry top : tops)
        {
            this.addTree(top, 0, under, order, seen);
        }
        if (!loose.isEmpty())
        {
            this.rows.add(new Row(null, 0, Component.translatable("gui.ancientempires.army.unassigned", loose.size())));
            loose.sort(order);
            loose.forEach(entry -> this.rows.add(new Row(entry, 1, null)));
        }
        this.scroll = Math.max(0, Math.min(this.scroll, this.rows.size() - this.visibleRows()));
        this.buttons();
    }

    private void addTree(ArmyPacket.Entry entry, int depth, Map<UUID, List<ArmyPacket.Entry>> under, Comparator<ArmyPacket.Entry> order, Set<UUID> seen)
    {
        if (!seen.add(entry.id()))
        {
            return;
        }
        this.rows.add(new Row(entry, depth, null));
        List<ArmyPacket.Entry> children = under.getOrDefault(entry.id(), List.of());
        List<ArmyPacket.Entry> sorted = new ArrayList<>(children);
        sorted.sort(order);
        for (ArmyPacket.Entry child : sorted)
        {
            this.addTree(child, depth + 1, under, order, seen);
        }
    }

    /** The selected soldier's buttons, beside the list. */
    private void buttons()
    {
        this.clearWidgets();
        ArmyPacket.Entry entry = this.selected == null ? null : this.byId.get(this.selected);
        if (entry == null)
        {
            return;
        }
        int x = this.listLeft() + this.listWidth() + 14;
        int y = TOP + 40;
        boolean leads = entry.members() > 0 || entry.rank() > 0;
        if (leads)
        {
            this.button(x, y, "follow", NpcArmy.Action.FOLLOW);
            this.button(x + BUTTON_W + 4, y, "free", NpcArmy.Action.FREE);
            y += 22;
            this.button(x, y, "hold", NpcArmy.Action.HOLD);
            this.button(x + BUTTON_W + 4, y, "fall_back", NpcArmy.Action.FALL_BACK);
            y += 22;
            this.button(x, y, "formation", NpcArmy.Action.FORMATION);
            this.button(x + BUTTON_W + 4, y, "flag", NpcArmy.Action.FLAG);
            y += 22;
            this.addRenderableWidget(Button.builder(Component.translatable("gui.ancientempires.army.number",
                    entry.unitNumber() == 0 ? "-" : Integer.toString(entry.unitNumber())), button -> this.send(NpcArmy.Action.NUMBER, entry.id(), null, ""))
                    .bounds(x, y, BUTTON_W * 2 + 4, 20).build());
            y += 22;
        }
        if (entry.leader() != null)
        {
            if (entry.ownOrder())
            {
                this.button(x, y, "rejoin", NpcArmy.Action.REJOIN);
            }
            this.button(x + BUTTON_W + 4, y, "remove", NpcArmy.Action.REMOVE);
            y += 22;
        }
        this.addRenderableWidget(Button.builder(Component.translatable(this.moving != null ? "gui.ancientempires.army.cancel_move" : "gui.ancientempires.army.move"), button ->
        {
            this.moving = this.moving == null ? entry.id() : null;
            this.buttons();
        }).bounds(x, y, BUTTON_W * 2 + 4, 20).build());
        y += 26;
        if (leads)
        {
            this.unitName = new EditBox(this.font, x, y, BUTTON_W + 30, 18, Component.translatable("gui.ancientempires.army.name"));
            this.unitName.setMaxLength(NpcArmy.NAME_LENGTH);
            this.unitName.setValue(entry.unitName());
            this.addRenderableWidget(this.unitName);
            this.addRenderableWidget(Button.builder(Component.translatable("gui.ancientempires.army.rename"),
                    button -> this.send(NpcArmy.Action.RENAME, entry.id(), null, this.unitName.getValue())).bounds(x + BUTTON_W + 34, y - 1, BUTTON_W - 30, 20).build());
        }
    }

    private void button(int x, int y, String key, NpcArmy.Action action)
    {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.ancientempires.army." + key),
                button -> this.send(action, this.selected, null, "")).bounds(x, y, BUTTON_W, 20).build());
    }

    private void send(NpcArmy.Action action, @Nullable UUID subject, @Nullable UUID other, String text)
    {
        if (subject != null)
        {
            ModNetwork.sendToServer(new ArmyActionPacket(false, action, subject, other, text));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int left = this.listLeft();
        int row = (int) Math.floor((mouseY - TOP) / ROW_H) + this.scroll;
        if (mouseX >= left && mouseX < left + this.listWidth() && mouseY >= TOP && row >= 0 && row < this.rows.size()
                && row - this.scroll < this.visibleRows())
        {
            ArmyPacket.Entry entry = this.rows.get(row).entry();
            if (entry != null)
            {
                if (this.moving != null && !this.moving.equals(entry.id()))
                {
                    // Move: the clicked one is the new leader.
                    this.send(NpcArmy.Action.MOVE, this.moving, entry.id(), "");
                    this.moving = null;
                }
                this.selected = entry.id();
                this.buttons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        this.scroll = Math.max(0, Math.min(this.scroll - (int) Math.signum(delta) * 3, this.rows.size() - this.visibleRows()));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        int left = this.listLeft();
        int width = this.listWidth();
        int shown = Math.min(this.visibleRows(), this.rows.size() - this.scroll);
        graphics.fill(left - 4, TOP - 4, left + width + 4, TOP + Math.max(1, shown) * ROW_H + 4, PANEL);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, GOLD);
        if (this.rows.isEmpty())
        {
            graphics.drawString(this.font, Component.translatable("gui.ancientempires.army.empty"), left, TOP, 0xFFA0A0A0);
        }
        for (int index = 0; index < shown; index++)
        {
            Row row = this.rows.get(index + this.scroll);
            int y = TOP + index * ROW_H;
            if (row.entry() == null)
            {
                graphics.drawString(this.font, row.heading(), left, y + 2, GOLD);
                continue;
            }
            ArmyPacket.Entry entry = row.entry();
            if (entry.id().equals(this.moving))
            {
                graphics.fill(left - 2, y, left + width + 2, y + ROW_H, MOVING);
            }
            else if (entry.id().equals(this.selected))
            {
                graphics.fill(left - 2, y, left + width + 2, y + ROW_H, SELECTED);
            }
            else if (mouseX >= left && mouseX < left + width && mouseY >= y && mouseY < y + ROW_H)
            {
                graphics.fill(left - 2, y, left + width + 2, y + ROW_H, HOVER);
            }
            this.drawRow(graphics, entry, left + row.depth() * 10, y, left + width);
        }
        this.drawDetails(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphics graphics, ArmyPacket.Entry entry, int x, int y, int right)
    {
        // Health as a thin bar under the row, the rest as text: name, kind, rank and level, the unit's order, distance.
        int healthW = 24;
        graphics.fill(right - healthW, y + 9, right, y + 10, 0xFF3A1010);
        graphics.fill(right - healthW, y + 9, right - healthW + Math.round(healthW * Math.min(1.0F, entry.health())), y + 10, 0xFF40C040);
        String kind = entry.mounted() ? "♞ " : entry.archer() ? "➶ " : "";
        String meters = Math.round(entry.distance()) + "m";
        graphics.drawString(this.font, meters, right - this.font.width(meters), y + 1, 0xFF909090);
        String level = "Lv" + entry.level();
        String unit = (entry.members() > 0 ? " (" + entry.members() + ")" : "") + (entry.unitNumber() > 0 && entry.members() > 0 ? " [" + entry.unitNumber() + "]" : "");
        Component name = parse(entry.name());
        String label = kind + name.getString() + unit;
        int nameRoom = right - x - this.font.width(meters) - 70;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(label, Math.max(20, nameRoom)), x, y + 1, 0xFFE0E0E0);
        String orderText = entry.members() > 0 || entry.leader() == null
                ? Component.translatable("order.ancientempires." + NpcOrder.values()[Math.min(entry.order(), NpcOrder.values().length - 1)].id()).getString() : "";
        int orderX = right - this.font.width(meters) - 66;
        graphics.drawString(this.font, level, orderX, y + 1, 0xFFB0B0B0);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(orderText, 34), orderX + 26, y + 1, entry.ownOrder() ? GOLD : 0xFF808080);
    }

    private void drawDetails(GuiGraphics graphics)
    {
        ArmyPacket.Entry entry = this.selected == null ? null : this.byId.get(this.selected);
        int x = this.listLeft() + this.listWidth() + 14;
        if (entry == null)
        {
            graphics.drawString(this.font, Component.translatable("gui.ancientempires.army.pick"), x, TOP, 0xFFA0A0A0);
            return;
        }
        graphics.drawString(this.font, parse(entry.name()), x, TOP, GOLD);
        NpcOrder order = NpcOrder.values()[Math.min(entry.order(), NpcOrder.values().length - 1)];
        NpcFormation formation = NpcFormation.values()[Math.min(entry.formation(), NpcFormation.values().length - 1)];
        Component unit = entry.unitName().isEmpty() ? Component.empty() : Component.literal(" «" + entry.unitName() + "»");
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.army.details", parse(entry.rankName()), entry.level(), entry.members()).append(unit),
                x, TOP + 12, 0xFFD0D0D0);
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.army.orders", Component.translatable("order.ancientempires." + order.id()),
                Component.translatable("formation.ancientempires." + formation.id())), x, TOP + 24, 0xFFD0D0D0);
        if (this.moving != null)
        {
            graphics.drawString(this.font, Component.translatable("gui.ancientempires.army.moving"), x, this.height - 20, 0xFF60B0FF);
        }
    }

    static Component parse(String json)
    {
        try
        {
            Component parsed = Component.Serializer.fromJson(json);
            return parsed == null ? Component.literal(json) : parsed;
        }
        catch (RuntimeException exception)
        {
            return Component.literal(json);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
