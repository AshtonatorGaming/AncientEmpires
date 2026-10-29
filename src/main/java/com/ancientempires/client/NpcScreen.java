package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcInventory;
import com.ancientempires.entity.npc.NpcPath;
import com.ancientempires.entity.npc.NpcRank;
import com.ancientempires.inventory.NpcLayout;
import com.ancientempires.inventory.NpcMenu;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.registry.ModMenus;
import com.ancientempires.registry.ModItems;
import com.ancientempires.item.OrderItem;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * The NPC inventory in the parchment style: name field, equipment cross around
 * a paperdoll, 3x3 backpack, command row, and the player's inventory. A plain
 * container screen on {@link NpcMenu}, so recipe viewers (EMI, JEI) treat it
 * like any other. Slots sit where {@link NpcLayout} put them; the path chooser,
 * stats sheet, and skin selector open as popups over it.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class NpcScreen extends AbstractContainerScreen<NpcMenu>
{
    private static final int SLOT = NpcLayout.SLOT;
    /** Command order. Hold, creative, and levy leave the row when they do not apply. */
    private static final int HOLD = 4;
    private static final int CREATIVE = 5;
    private static final int LEVY = 6;
    /** How long a sent name may take to come back before the field shows the NPC's name again. */
    private static final int NAME_SYNC_TICKS = 40;

    /** The screen's pages, picked from the strip on the left: gear (the inventory), the character sheet, the orders. */
    private enum Tab
    {
        GEAR, SHEET, ORDERS
    }

    private final NpcEntityPreview preview;
    private final List<NpcButton> commands = new ArrayList<>();
    private Tab tab = Tab.GEAR;
    private NpcButton skinButton;
    private NpcButton roleButton;
    private EditBox nameField;
    private boolean nameWasFocused;
    /** A name sent and not yet seen back from the server, and how long to wait for it. */
    private String sentName;
    private int sentWait;

    public NpcScreen(NpcMenu menu, Inventory playerInventory, Component title)
    {
        super(menu, playerInventory, title);
        this.imageWidth = NpcLayout.WIDTH;
        this.imageHeight = NpcLayout.HEIGHT;
        NpcEntity npc = menu.getNpc();
        this.preview = npc == null ? null : new NpcEntityPreview(npc);
    }

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event)
    {
        event.enqueueWork(() ->
        {
            MenuScreens.register(ModMenus.NPC_INVENTORY.get(), NpcScreen::new);
            NpcSkinHooks.setOpener(NpcSkinSelector::open);
        });
    }

    @Override
    protected void init()
    {
        super.init();
        NpcEntity npc = this.menu.getNpc();
        // Unbordered, the text sits at the box's top, so the box starts 4 px down to center it.
        this.nameField = new FitEditBox(this.font, this.leftPos + NpcLayout.PREVIEW_X + 4, this.topPos + NpcLayout.NAME_Y + 4,
                NpcLayout.PREVIEW_W - 8, NpcLayout.NAME_H - 4, Component.translatable("gui.ancientempires.npc.name"));
        this.nameField.setBordered(false);
        this.nameField.setMaxLength(32);
        this.nameField.setTextColor(NpcGui.NAME_INK);
        this.nameField.setHint(Component.translatable("gui.ancientempires.npc.name").withStyle(style -> style.withColor(NpcGui.NAME_HINT & 0xFFFFFF)));
        this.nameField.setValue(this.currentName());
        this.addRenderableWidget(this.nameField);

        int frameX = this.leftPos + NpcLayout.PREVIEW_X;
        int frameY = this.topPos + NpcLayout.PREVIEW_Y;
        this.skinButton = this.addRenderableWidget(new NpcButton(frameX + NpcLayout.PREVIEW_W - 14, frameY + NpcLayout.PREVIEW_H - 14, 12, 12,
                Component.translatable("gui.ancientempires.npc.skin"),
                (graphics, x, y, width, height) -> NpcGui.iconCentered(graphics, NpcGui.Icon.SKIN, x, y, width, height, 8, 8),
                this::openSkin)
                .tooltip(Component.translatable("gui.ancientempires.npc.skin")));
        if (npc != null)
        {
            this.roleButton = this.addRenderableWidget(this.roleButton(npc, frameX + 2, frameY + NpcLayout.PREVIEW_H - 14));
        }
        this.tabButton(Tab.GEAR, new ItemStack(Items.IRON_CHESTPLATE), "gui.ancientempires.npc.tab.gear");
        this.tabButton(Tab.SHEET, new ItemStack(Items.WRITABLE_BOOK), "gui.ancientempires.npc.tab.sheet");
        this.tabButton(Tab.ORDERS, new ItemStack(ModItems.ROUTING_ORDER.get()), "gui.ancientempires.npc.tab.orders");
        this.showTab(this.tab);

        this.commands.clear();
        this.command(NpcGui.Icon.FOLLOW, "gui.ancientempires.npc.follow", NpcMenu.BUTTON_FOLLOW, active(npc, NpcEntity::isFollowing));
        this.command(NpcGui.Icon.SET_HOME, "gui.ancientempires.npc.set_home", NpcMenu.BUTTON_SET_HOME, active(npc, entity -> entity.getHome() != null));
        this.command(NpcGui.Icon.CLOSE, "gui.ancientempires.npc.clear_home", NpcMenu.BUTTON_CLEAR_HOME, null);
        this.command(NpcGui.Icon.REPACK, "gui.ancientempires.npc.repack", NpcMenu.BUTTON_REPACK, null);
        this.command(NpcGui.Icon.HOLD, "gui.ancientempires.npc.hold", NpcMenu.BUTTON_HOLD, active(npc, holder -> holder.getHold().holding()));
        this.command(NpcGui.Icon.CREATIVE, "gui.ancientempires.npc.creative", NpcMenu.BUTTON_CREATIVE, active(npc, NpcEntity::isCreative));
        this.command(NpcGui.Icon.LEVY, "gui.ancientempires.npc.levy", NpcMenu.BUTTON_LEVY, active(npc, NpcEntity::isLevied));
        this.command(NpcGui.Icon.MOUNT, "gui.ancientempires.npc.mount", NpcMenu.BUTTON_MOUNT, active(npc, NpcEntity::isPassenger));
        this.commands.get(HOLD).visible = showHold(npc);
        this.commands.get(CREATIVE).visible = showCreative();
        this.commands.get(LEVY).visible = showLevy(npc);
        this.placeCommands();
    }

    /** A tab in the strip left of the panel, its icon an item. */
    private void tabButton(Tab tab, ItemStack icon, String tooltip)
    {
        int x = this.leftPos - 22;
        int y = this.topPos + 8 + tab.ordinal() * 22;
        this.addRenderableWidget(new NpcButton(x, y, 20, 20, Component.translatable(tooltip),
                (graphics, left, top, width, height) -> graphics.renderItem(icon, left + 2, top + 2),
                pressed -> pressed == 0 && this.showTab(tab))
                .fill(() -> this.tab == tab ? NpcGui.BUTTON_ON : NpcGui.BUTTON)
                .tooltip(Component.translatable(tooltip)));
    }

    /** Shows a tab: the NPC's slots, the skin and role buttons only with the gear. */
    private boolean showTab(Tab tab)
    {
        this.tab = tab;
        boolean gear = tab == Tab.GEAR;
        this.menu.setNpcSlotsShown(gear);
        if (this.skinButton != null)
        {
            this.skinButton.visible = gear;
        }
        if (this.roleButton != null)
        {
            this.roleButton.visible = gear;
        }
        return true;
    }

    private void command(NpcGui.Icon icon, String tooltip, int buttonId, BooleanSupplier on)
    {
        NpcButton button = new NpcButton(0, 0, NpcLayout.BUTTON_SIZE, NpcLayout.BUTTON_SIZE, Component.translatable(tooltip),
                (graphics, x, y, width, height) ->
                {
                    if (icon == NpcGui.Icon.FOLLOW)
                    {
                        // The move-to glyph is half a cell wide; drawn taller than wide, as before.
                        NpcGui.iconCentered(graphics, icon, x, y, width, height, 8, 12);
                    }
                    else
                    {
                        NpcGui.iconCentered(graphics, icon, x, y, width, height, 11, 11);
                    }
                },
                pressed -> pressed == 0 && this.sendButton(buttonId))
                .tooltip(Component.translatable(tooltip));
        if (on != null)
        {
            button.fill(() -> on.getAsBoolean() ? NpcGui.BUTTON_ON : NpcGui.BUTTON);
        }
        this.commands.add(button);
        this.addRenderableWidget(button);
    }

    /** Centers the shown commands on the row. */
    private void placeCommands()
    {
        int shown = 0;
        for (NpcButton button : this.commands)
        {
            if (button.visible)
            {
                shown++;
            }
        }
        int origin = NpcLayout.buttonRowX(Math.max(shown, 1));
        int slot = 0;
        for (NpcButton button : this.commands)
        {
            if (!button.visible)
            {
                continue;
            }
            button.setPosition(this.leftPos + origin + slot * (NpcLayout.BUTTON_SIZE + NpcLayout.BUTTON_GAP), this.topPos + NpcLayout.BUTTON_Y);
            slot++;
        }
    }

    /**
     * Hold is the archer sentry, so it is shown only while a bow is in the main hand.
     * Creative and levy are the other buttons that can leave the row. All reflow when they change.
     */
    private void layoutCommands()
    {
        NpcEntity npc = this.menu.getNpc();
        boolean hold = showHold(npc);
        boolean creative = showCreative();
        boolean levy = showLevy(npc);
        NpcButton holdButton = this.commands.get(HOLD);
        NpcButton creativeButton = this.commands.get(CREATIVE);
        NpcButton levyButton = this.commands.get(LEVY);
        if (holdButton.visible == hold && creativeButton.visible == creative && levyButton.visible == levy)
        {
            return;
        }
        holdButton.visible = hold;
        creativeButton.visible = creative;
        levyButton.visible = levy;
        this.placeCommands();
    }

    private static boolean showHold(NpcEntity npc)
    {
        return npc != null && npc.isHoldingBow();
    }

    /** Levy is for civilians. Whoever has the screen open may command it. */
    private static boolean showLevy(NpcEntity npc)
    {
        return npc != null && npc.isCivilian();
    }

    private static boolean showCreative()
    {
        Player player = Minecraft.getInstance().player;
        return player != null && NpcEntity.mayToggleCreative(player);
    }

    private static BooleanSupplier active(NpcEntity npc, Predicate<NpcEntity> test)
    {
        if (npc == null)
        {
            return null;
        }
        return () -> test.test(npc);
    }

    private boolean sendButton(int buttonId)
    {
        if (this.minecraft != null && this.minecraft.gameMode != null)
        {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
        return true;
    }

    /**
     * The NPC's role name, beside the skin button: what its equipment makes it
     * within its path. Left click on a settler its owner may decide for (gold
     * text marks that, and admins may for any NPC) opens the path chooser;
     * otherwise, or on right click, the stats sheet.
     */
    private NpcButton roleButton(NpcEntity npc, int x, int y)
    {
        return new NpcButton(x, y, NpcLayout.PREVIEW_W - 18, 12, Component.translatable("gui.ancientempires.npc.role"),
                (graphics, left, top, width, height) -> FitText.draw(graphics, this.font, roleLabel(npc).getString(), left + 2, top, width - 4, height,
                        mayChoose(npc) ? NpcGui.GOLD : NpcGui.NAME_INK, true, false),
                pressed -> this.openPaths(npc, pressed))
                .tooltip(() -> roleTooltip(npc), 0);
    }

    private static List<Component> roleTooltip(NpcEntity npc)
    {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(mayChoose(npc) ? "gui.ancientempires.npc.path" : "gui.ancientempires.npc.role"));
        lines.add(Component.translatable("gui.ancientempires.npc.stats_hint"));
        if (npc.getRank() != NpcRank.SOLDIER)
        {
            lines.add(Component.translatable("gui.ancientempires.npc.rank_role", npc.getRankName(),
                    Component.translatable(NpcPath.translationKey(npc.getRoleId()))));
        }
        if (npc.levels())
        {
            // The band and the way to the next level.
            lines.add(Component.translatable(npc.getXpToNext() > 0.0D ? "gui.ancientempires.npc.level_progress" : "gui.ancientempires.npc.level_max",
                    Component.translatable(NpcEntity.bandKey(npc.getNpcPath(), npc.getLevelBand())), npc.getLevel(),
                    (int) npc.getXp(), (int) Math.ceil(npc.getXpToNext())));
        }
        return lines;
    }

    /** The role (or a ranked soldier's rank), with the level for NPCs that level: "Archer, Lv 13", "Captain, Lv 14". */
    private static Component roleLabel(NpcEntity npc)
    {
        Component role = Component.translatable(NpcPath.translationKey(npc.getRoleId()));
        // A ranked soldier is known by its rank; the tooltip still names its role. The level has its own badge.
        return npc.getRank() != NpcRank.SOLDIER ? npc.getRankName() : role;
    }

    private static boolean mayChoose(NpcEntity npc)
    {
        Player player = Minecraft.getInstance().player;
        return player != null && npc.mayChoosePath(player);
    }

    private boolean openPaths(NpcEntity npc, int pressed)
    {
        if (pressed == 0 && mayChoose(npc))
        {
            NpcPopupScreen.open(new NpcPathChooser(this.menu, npc));
            return true;
        }
        if (pressed == 0 || pressed == 1)
        {
            NpcPopupScreen.open(new NpcStatsSheet(this.menu, npc));
            return true;
        }
        return false;
    }

    private boolean openSkin(int pressed)
    {
        if (pressed != 0)
        {
            return false;
        }
        NpcEntity npc = this.menu.getNpc();
        if (npc != null)
        {
            NpcSkinHooks.open(npc);
        }
        return true;
    }

    private String currentName()
    {
        NpcEntity npc = this.menu.getNpc();
        if (npc == null || npc.getCustomName() == null)
        {
            return "";
        }
        return npc.getCustomName().getString();
    }

    private void submitName()
    {
        NpcEntity npc = this.menu.getNpc();
        if (npc == null || this.nameField == null)
        {
            return;
        }
        String value = this.nameField.getValue();
        if (value.equals(this.currentName()) || value.equals(this.sentName))
        {
            return;
        }
        ModNetwork.sendName(npc.getId(), value);
        this.sentName = value;
        this.sentWait = NAME_SYNC_TICKS;
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();
        this.nameField.tick();
        boolean focused = this.nameField.isFocused();
        if (this.nameWasFocused && !focused)
        {
            this.submitName();
        }
        this.nameWasFocused = focused;
        String name = this.currentName();
        if (this.sentName != null && (this.sentName.equals(name) || --this.sentWait <= 0))
        {
            this.sentName = null;
        }
        if (!focused && this.sentName == null && !this.nameField.getValue().equals(name))
        {
            // Renamed elsewhere (or the server refused): show the name the NPC has.
            this.nameField.setValue(name);
        }
        this.layoutCommands();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (this.nameField.isFocused() && !this.nameField.isMouseOver(mouseX, mouseY))
        {
            // A click anywhere else, slots included, ends the edit.
            this.setFocused(null);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (this.nameField.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE)
        {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
            {
                this.setFocused(null);
                this.submitName();
                this.nameWasFocused = false;
                return true;
            }
            // Typing a name must not close the screen on the inventory key.
            return this.nameField.keyPressed(keyCode, scanCode, modifiers) || this.nameField.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed()
    {
        this.submitName();
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        if (this.hoveredSlot != null && !this.hoveredSlot.hasItem() && this.hoveredSlot.isActive()
                && (this.hoveredSlot.index == NpcInventory.ORDERS_SLOT || this.hoveredSlot.index == NpcInventory.UPKEEP_SLOT))
        {
            // What an empty order slot is for.
            String key = this.hoveredSlot.index == NpcInventory.ORDERS_SLOT ? "gui.ancientempires.npc.orders_slot" : "gui.ancientempires.npc.upkeep_slot";
            graphics.renderTooltip(this.font, this.font.split(Component.translatable(key), 180), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int left = this.leftPos;
        int top = this.topPos;
        NpcGui.bevel(graphics, left, top, this.imageWidth, this.imageHeight, NpcGui.PARCHMENT);
        graphics.fill(left + NpcLayout.PREVIEW_X, top + NpcLayout.NAME_Y, left + NpcLayout.PREVIEW_X + NpcLayout.PREVIEW_W,
                top + NpcLayout.NAME_Y + NpcLayout.NAME_H, NpcGui.INK);
        if (this.tab == Tab.GEAR)
        {
            NpcGui.bevel(graphics, left + NpcLayout.PREVIEW_X, top + NpcLayout.PREVIEW_Y, NpcLayout.PREVIEW_W, NpcLayout.PREVIEW_H, NpcGui.INK);
            if (this.preview != null)
            {
                this.preview.render(graphics, left + NpcLayout.PREVIEW_X + NpcLayout.PREVIEW_ENTITY_X, top + NpcLayout.PREVIEW_Y + NpcLayout.PREVIEW_ENTITY_Y,
                        NpcLayout.PREVIEW_ENTITY_W, NpcLayout.PREVIEW_ENTITY_H, mouseX, mouseY);
            }
        }
        else
        {
            this.renderPage(graphics, left, top, mouseX, mouseY);
        }
        for (int index = 0; index < this.menu.slots.size(); index++)
        {
            Slot slot = this.menu.slots.get(index);
            if (slot.isActive())
            {
                this.renderCell(graphics, slot, index, mouseX, mouseY);
            }
        }
    }

    /** The page of the Sheet or Orders tab, over the area the gear takes on the Gear tab. */
    private void renderPage(GuiGraphics graphics, int left, int top, int mouseX, int mouseY)
    {
        int x = left + NpcLayout.CROSS_X;
        int y = top + NpcLayout.PREVIEW_Y;
        int width = NpcLayout.WIDTH - NpcLayout.CROSS_X * 2;
        int height = NpcLayout.BUTTON_Y - NpcLayout.PREVIEW_Y - 4;
        NpcGui.bevel(graphics, x, y, width, height, NpcGui.INK);
        NpcEntity npc = this.menu.getNpc();
        if (npc == null)
        {
            return;
        }
        if (this.tab == Tab.SHEET)
        {
            // The NPC itself beside its sheet, so the page is about someone.
            int previewW = 58;
            if (this.preview != null)
            {
                this.preview.render(graphics, x + 2, y + 2, previewW, height - 4, mouseX, mouseY);
            }
            NpcStatsSheet.draw(graphics, this.font, this.menu, npc, x + previewW + 8, y + 4, width - previewW - 14);
            return;
        }
        int inner = width - 12;
        int line = y + 5;
        line = this.orderSection(graphics, Component.translatable("gui.ancientempires.npc.orders_title"), this.orders(npc), x + 6, line, inner, 6);
        line += 4;
        this.orderSection(graphics, Component.translatable("gui.ancientempires.npc.upkeep_title"), npc.getInventory().upkeepItem(), x + 6, line, inner, 2);
    }

    /** The order the NPC works by: a Routing Order in hand (a courier's), else the orders slot's. */
    private ItemStack orders(NpcEntity npc)
    {
        ItemStack hand = npc.getMainHandItem();
        return hand.getItem() instanceof OrderItem ? hand : npc.getInventory().ordersItem();
    }

    /** A heading, the order's name (or what happens without one), and up to {@code most} of its stops. Returns the next line. */
    private int orderSection(GuiGraphics graphics, Component heading, ItemStack order, int x, int line, int width, int most)
    {
        FitText.draw(graphics, this.font, heading.getString(), x, line, width, 10, NpcGui.GOLD, false, true);
        line += 11;
        if (order.isEmpty())
        {
            String none = heading.getString().equals(Component.translatable("gui.ancientempires.npc.upkeep_title").getString())
                    ? "gui.ancientempires.npc.upkeep_none" : "gui.ancientempires.npc.orders_none";
            FitText.draw(graphics, this.font, Component.translatable(none).getString(), x + 4, line, width - 4, 10, NpcGui.NAME_HINT, false, false);
            return line + 11;
        }
        FitText.draw(graphics, this.font, order.getHoverName().getString(), x + 4, line, width - 4, 10, NpcGui.NAME_INK, false, true);
        line += 11;
        List<Component> stops = OrderItem.lines(order);
        for (int i = 0; i < Math.min(most, stops.size()); i++)
        {
            FitText.draw(graphics, this.font, stops.get(i).getString(), x + 8, line, width - 8, 10, NpcGui.NAME_INK, false, false);
            line += 10;
        }
        if (stops.size() > most)
        {
            FitText.draw(graphics, this.font, Component.translatable("item.ancientempires.order.more", stops.size() - most).getString(), x + 8, line, width - 8, 10,
                    NpcGui.NAME_HINT, false, false);
            line += 10;
        }
        return line;
    }

    /**
     * A slot's 18 px cell, one pixel outside the item. Neighbors share an edge, so
     * a grid reads as one frame. The hands reach one pixel lower, to the line
     * between chest and legs.
     */
    private void renderCell(GuiGraphics graphics, Slot slot, int index, int mouseX, int mouseY)
    {
        int x = this.leftPos + slot.x - 1;
        int y = this.topPos + slot.y - 1;
        int height = SLOT;
        boolean shareBottom;
        boolean shareRight;
        if (index < NpcInventory.BACKPACK_START)
        {
            boolean hand = index == NpcInventory.MAIN_HAND_SLOT || index == NpcInventory.OFFHAND_SLOT;
            height = hand ? SLOT + 1 : SLOT;
            shareBottom = index == NpcInventory.HEAD_SLOT || index == NpcInventory.CHEST_SLOT || index == NpcInventory.LEGS_SLOT;
            shareRight = index == NpcInventory.MAIN_HAND_SLOT || index == NpcInventory.CHEST_SLOT;
        }
        else if (index < NpcInventory.SLOT_COUNT)
        {
            int cell = index - NpcInventory.BACKPACK_START;
            shareBottom = cell / 3 < 2;
            shareRight = cell % 3 < 2;
        }
        else if (index < NpcInventory.CONTAINER_SIZE)
        {
            // The two order slots, a pair under the backpack.
            shareBottom = false;
            shareRight = index == NpcInventory.ORDERS_SLOT;
        }
        else
        {
            int cell = index - NpcInventory.CONTAINER_SIZE;
            // The hotbar stands apart from the three rows above it.
            shareBottom = cell < 18;
            shareRight = cell % 9 < 8;
        }
        boolean hover = mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + height;
        NpcGui.bevel(graphics, x, y, SLOT, height, hover ? NpcGui.SLOT_HOVER : NpcGui.SLOT_FILL, shareBottom, shareRight);
        ResourceLocation icon = slot.hasItem() ? null : this.menu.emptyIcon(index);
        if (icon != null && this.minecraft != null)
        {
            // A faint parchment-tinted outline of what goes here.
            TextureAtlasSprite sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon);
            RenderSystem.enableBlend();
            graphics.setColor(0.92F, 0.82F, 0.62F, 0.35F);
            graphics.blit(this.leftPos + slot.x, this.topPos + slot.y, 0, 16, 16, sprite);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    /** The cell's hover fill marks the slot; vanilla's white square over the item is left out. */
    @Override
    public int getSlotColor(int index)
    {
        return 0;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        int backpackW = 3 * SLOT;
        if (this.tab == Tab.GEAR)
        {
            FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.npc.backpack").getString(),
                    NpcLayout.BACKPACK_X, NpcLayout.BACKPACK_LABEL_Y, backpackW, NpcLayout.SECTION_LABEL_H, NpcGui.NAME_INK, true, true);
        }
        FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.npc.inventory").getString(),
                NpcLayout.PLAYER_X, NpcLayout.PLAYER_LABEL_Y, 9 * SLOT, NpcLayout.SECTION_LABEL_H, NpcGui.NAME_INK, false, true);
        NpcEntity npc = this.menu.getNpc();
        if (npc != null && npc.levels() && this.tab == Tab.GEAR)
        {
            // The level in the preview's top corner, so the role label below keeps its width for long titles.
            FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.npc.level_badge", npc.getLevel()).getString(),
                    NpcLayout.PREVIEW_X + 3, NpcLayout.PREVIEW_Y + 3, 30, 9, NpcGui.GOLD, false, true);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
