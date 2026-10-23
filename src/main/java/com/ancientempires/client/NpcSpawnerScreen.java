package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.NpcSpawnerBlockEntity;
import com.ancientempires.inventory.NpcSpawnerMenu;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The NPC spawner, in the parchment style of the NPC and worksite screens:
 * the scrolls it spawns on the left with what it is doing under them, and on
 * the right only the settings its mode uses, each a labelled row with
 * minus and plus (shift for ten steps) and a tooltip saying what it does.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class NpcSpawnerScreen extends AbstractContainerScreen<NpcSpawnerMenu>
{
    private static final int PARCHMENT = 0xFF4A3B2A;
    private static final int RIM_LIGHT = 0xFF6A5640;
    private static final int RIM_DARK = 0xFF2A2018;
    private static final int SLOT_FILL = 0xFF2C241C;
    private static final int SLOT_SHADE = 0xFF1A1410;
    private static final int SLOT_EDGE = 0xFF5A4834;
    private static final int GOLD = 0xFFC6A56A;
    private static final int INK = 0xFFE6D2A2;
    private static final int DIM = 0xFFA09070;
    private static final int PANEL_X = 78;
    private static final int ROW_TOP = 46;
    private static final int ROW_H = 13;

    /** A settings row: which setting, and its label key. */
    private record Row(int setting, String key)
    {
    }

    private int shownMode = -1;
    private final List<Row> rows = new ArrayList<>();

    public NpcSpawnerScreen(NpcSpawnerMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.imageWidth = NpcSpawnerMenu.WIDTH;
        this.imageHeight = NpcSpawnerMenu.HEIGHT;
        this.inventoryLabelX = NpcSpawnerMenu.PLAYER_X;
        this.inventoryLabelY = NpcSpawnerMenu.PLAYER_Y - 11;
    }

    @SubscribeEvent
    public static void register(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.NPC_SPAWNER.get(), NpcSpawnerScreen::new));
    }

    @Override
    protected void init()
    {
        super.init();
        this.shownMode = this.menu.get(NpcSpawnerBlockEntity.MODE);
        this.rows.clear();
        this.rows.add(new Row(NpcSpawnerBlockEntity.PLAYER_RANGE, "player_range"));
        this.rows.add(new Row(NpcSpawnerBlockEntity.SPAWN_RADIUS, "spawn_radius"));
        switch (NpcSpawnerBlockEntity.Mode.values()[this.shownMode])
        {
            case GARRISON -> this.rows.add(new Row(NpcSpawnerBlockEntity.RETURN_RANGE, "return_range"));
            case REPEATING ->
            {
                this.rows.add(new Row(NpcSpawnerBlockEntity.MIN_DELAY, "min_delay"));
                this.rows.add(new Row(NpcSpawnerBlockEntity.MAX_DELAY, "max_delay"));
                this.rows.add(new Row(NpcSpawnerBlockEntity.MAX_NEARBY, "max_nearby"));
                this.rows.add(new Row(NpcSpawnerBlockEntity.TOTAL_LIMIT, "total_limit"));
            }
            case REDSTONE -> this.rows.add(new Row(NpcSpawnerBlockEntity.MAX_NEARBY, "max_nearby"));
        }
        int left = this.leftPos + PANEL_X;
        int right = this.leftPos + this.imageWidth - 10;
        this.addRenderableWidget(Button.builder(Component.empty(), button -> this.press(NpcSpawnerBlockEntity.MODE, true))
                .bounds(left, this.topPos + 26, right - left, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.ancientempires.spawner.mode.tooltip")))
                .build());
        for (int index = 0; index < this.rows.size(); index++)
        {
            Row row = this.rows.get(index);
            int y = this.topPos + ROW_TOP + index * ROW_H;
            this.addRenderableWidget(Button.builder(Component.literal("-"), button -> this.press(row.setting(), false))
                    .bounds(right - 28, y, 13, 12).build());
            this.addRenderableWidget(Button.builder(Component.literal("+"), button -> this.press(row.setting(), true))
                    .bounds(right - 13, y, 13, 12).build());
        }
        int toggleY = this.topPos + ROW_TOP + this.rows.size() * ROW_H + 3;
        int half = (right - left - 2) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.ancientempires.spawner.light"),
                        button -> this.press(NpcSpawnerBlockEntity.LIGHT_SENSITIVE, true))
                .bounds(left, toggleY, half, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.ancientempires.spawner.light.tooltip")))
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.ancientempires.spawner.hidden"),
                        button -> this.press(NpcSpawnerBlockEntity.HIDDEN, true))
                .bounds(left + half + 2, toggleY, half, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.ancientempires.spawner.hidden.tooltip")))
                .build());
    }

    private void press(int setting, boolean up)
    {
        int op = (up ? 1 : 0) + (hasShiftDown() ? 2 : 0);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null)
        {
            minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, setting * 4 + op);
        }
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();
        if (this.menu.get(NpcSpawnerBlockEntity.MODE) != this.shownMode)
        {
            // The mode changed: show its settings.
            this.rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        for (int index = 0; index < this.rows.size(); index++)
        {
            int y = this.topPos + ROW_TOP + index * ROW_H;
            if (mouseX >= this.leftPos + PANEL_X && mouseX < this.leftPos + this.imageWidth - 40 && mouseY >= y && mouseY < y + 12)
            {
                graphics.renderTooltip(this.font, this.font.split(
                        Component.translatable("gui.ancientempires.spawner." + this.rows.get(index).key() + ".tooltip"), 180), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, PARCHMENT);
        graphics.fill(left, top, left + this.imageWidth, top + 1, RIM_LIGHT);
        graphics.fill(left, top, left + 1, top + this.imageHeight, RIM_LIGHT);
        graphics.fill(left, top + this.imageHeight - 1, left + this.imageWidth, top + this.imageHeight, RIM_DARK);
        graphics.fill(left + this.imageWidth - 1, top, left + this.imageWidth, top + this.imageHeight, RIM_DARK);
        // A faint rule between the scrolls and the settings.
        graphics.fill(left + PANEL_X - 6, top + 24, left + PANEL_X - 5, top + NpcSpawnerMenu.PLAYER_Y - 16, RIM_DARK);
        for (Slot slot : this.menu.slots)
        {
            int x = left + slot.x;
            int y = top + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
            graphics.fill(x - 1, y - 1, x + 16, y + 16, SLOT_SHADE);
            graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.drawString(this.font, this.title, 8, 7, GOLD, false);
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.spawner.spawns"), NpcSpawnerMenu.SLOTS_X, 19, INK, false);
        FitText.draw(graphics, this.font, this.status().getString(), NpcSpawnerMenu.SLOTS_X - 2, NpcSpawnerMenu.SLOTS_Y + 58, 60, 9, DIM, false, false);
        FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.spawner.mode." + this.modeKey()).getString(),
                PANEL_X + 2, 30, this.imageWidth - PANEL_X - 14, 9, GOLD, true, false);
        int right = this.imageWidth - 10;
        for (int index = 0; index < this.rows.size(); index++)
        {
            Row row = this.rows.get(index);
            int y = ROW_TOP + index * ROW_H + 2;
            FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.spawner." + row.key()).getString(),
                    PANEL_X, y, right - PANEL_X - 62, 9, INK, false, false);
            FitText.draw(graphics, this.font, this.value(row.setting()), right - 62, y, 32, 9, GOLD, true, false);
        }
        int toggleY = ROW_TOP + this.rows.size() * ROW_H + 3 + 17;
        String light = Component.translatable(this.menu.get(NpcSpawnerBlockEntity.LIGHT_SENSITIVE) != 0
                ? "gui.ancientempires.spawner.light.on" : "gui.ancientempires.spawner.light.off").getString();
        String hidden = Component.translatable(this.menu.get(NpcSpawnerBlockEntity.HIDDEN) != 0
                ? "gui.ancientempires.spawner.hidden.on" : "gui.ancientempires.spawner.hidden.off").getString();
        int half = (right - PANEL_X - 2) / 2;
        FitText.draw(graphics, this.font, light, PANEL_X, toggleY, half, 9, DIM, true, false);
        FitText.draw(graphics, this.font, hidden, PANEL_X + half + 2, toggleY, half, 9, DIM, true, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, INK, false);
    }

    private String modeKey()
    {
        return NpcSpawnerBlockEntity.Mode.values()[this.menu.get(NpcSpawnerBlockEntity.MODE)].name().toLowerCase(Locale.ROOT);
    }

    /** What the spawner is doing, under its scrolls. */
    private Component status()
    {
        return switch (NpcSpawnerBlockEntity.Mode.values()[this.menu.get(NpcSpawnerBlockEntity.MODE)])
        {
            case GARRISON -> this.menu.get(NpcSpawnerBlockEntity.OUT) > 0
                    ? Component.translatable("gui.ancientempires.spawner.status.out", this.menu.get(NpcSpawnerBlockEntity.OUT))
                    : Component.translatable("gui.ancientempires.spawner.status.armed");
            case REPEATING -> this.menu.get(NpcSpawnerBlockEntity.TOTAL_LIMIT) > 0
                    ? Component.translatable("gui.ancientempires.spawner.status.limited", this.menu.get(NpcSpawnerBlockEntity.SPAWNED),
                    this.menu.get(NpcSpawnerBlockEntity.TOTAL_LIMIT))
                    : Component.translatable("gui.ancientempires.spawner.status.spawned", this.menu.get(NpcSpawnerBlockEntity.SPAWNED));
            case REDSTONE -> Component.translatable("gui.ancientempires.spawner.status.redstone");
        };
    }

    /** A setting for display: delays in seconds, a total limit of 0 as no limit, ranges in blocks. */
    private String value(int setting)
    {
        int value = this.menu.get(setting);
        if (setting == NpcSpawnerBlockEntity.MIN_DELAY || setting == NpcSpawnerBlockEntity.MAX_DELAY)
        {
            return Component.translatable("gui.ancientempires.spawner.seconds", value / 20).getString();
        }
        if (setting == NpcSpawnerBlockEntity.TOTAL_LIMIT && value == 0)
        {
            return Component.translatable("gui.ancientempires.spawner.no_limit").getString();
        }
        return Integer.toString(value);
    }
}
