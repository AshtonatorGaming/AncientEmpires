package com.ancientempires.client;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.core.mixins.client.AbstractContainerScreenAccessor;
import brachy.modularui.core.mixins.client.ScreenAccessor;
import brachy.modularui.core.mixins.client.SlotAccessor;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.integration.recipeviewer.entry.item.ItemStackList;
import brachy.modularui.screen.ClientScreenHandler;
import brachy.modularui.screen.RichTooltip;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.sizer.Area;
import brachy.modularui.widgets.slot.ItemSlot;
import com.ancientempires.inventory.NpcLayout;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * An {@link ItemSlot} bound to a vanilla menu slot. The stock widget only draws
 * a {@code ModularSlot}, which would replace {@code NpcMenu}. Clicks still go
 * through {@link ClientScreenHandler} into the container screen.
 */
@OnlyIn(Dist.CLIENT)
public class MenuItemSlot extends ItemSlot
{
    /** Vanilla cell. The item is 16 px, inset by 1 px, with no extra padding. */
    private static final int CELL = NpcLayout.SLOT;
    private static final int ITEM = 16;
    private static final int INSET = (CELL - ITEM) / 2;

    private final Slot vanillaSlot;
    /** Position and size relative to the immediate parent. Cross slots are parented to the panel. */
    private int cellX;
    private int cellY;
    private int cellW = CELL;
    private int cellH = CELL;

    public MenuItemSlot(Slot vanillaSlot)
    {
        this.vanillaSlot = vanillaSlot;
    }

    public void place(int x, int y)
    {
        this.place(x, y, CELL, CELL);
    }

    public void place(int x, int y, int width, int height)
    {
        this.cellX = x;
        this.cellY = y;
        this.cellW = width;
        this.cellH = height;
        this.pos(x, y);
        this.size(width, height);
        this.margin(0);
        this.padding(0);
    }

    @Override
    public void onInit()
    {
        super.onInit();
        // ItemSlot.onInit sets both axes to 18. Put the placed size back.
        this.place(this.cellX, this.cellY, this.cellW, this.cellH);
        this.disableThemeBackground(true);
        this.disableHoverThemeBackground(true);
    }

    @Override
    public void postResize()
    {
        super.postResize();
        // The resizer can leave a box other than the placed cell. The item is drawn
        // at a fixed 16 px, so a larger box shows as empty padding.
        Area area = this.getArea();
        Area parent = this.getParentArea();
        area.setRelativePos(this.cellX, this.cellY);
        area.x = parent.x + this.cellX;
        area.y = parent.y + this.cellY;
        area.width = this.cellW;
        area.height = this.cellH;
        area.getPadding().all(0);
        area.getMargin().all(0);
        Area panel = this.getPanel().getArea();
        SlotAccessor access = (SlotAccessor) this.vanillaSlot;
        access.setX(area.x - panel.x + INSET);
        access.setY(area.y - panel.y + INSET);
    }

    @Override
    public void onUpdate()
    {
        // ItemSlot.onUpdate reads a ModularSlot sync handler this widget does not have.
    }

    @Override
    protected void drawOverlay(ModularGuiContext context)
    {
        // ItemSlot draws a 16x16 hover rect at (1, 1). The slot fill is the full cell.
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme)
    {
        Lighting.setupFor3DItems();
        this.drawContents(context);
        super.drawOverlay(context, widgetTheme);
    }

    @Override
    public void drawForeground(ModularGuiContext context)
    {
        RichTooltip tooltip = this.getTooltip();
        if (tooltip != null && this.isHovering() && !context.getUISettings().drawTooltipExternally())
        {
            tooltip.draw(context);
        }
    }

    @Override
    public RichTooltip getTooltip()
    {
        ItemStack stack = this.vanillaSlot.getItem();
        if (stack.isEmpty())
        {
            return null;
        }
        RichTooltip tooltip = super.tooltip();
        tooltip.reset();
        tooltip.parent(this);
        tooltip.addFromItem(stack);
        return tooltip;
    }

    @Override
    public Interactable.Result onMousePressed(int button)
    {
        ClientScreenHandler.clickSlot(this.getScreen(), this.vanillaSlot);
        return Interactable.Result.SUCCESS;
    }

    @Override
    public Slot getVanillaSlot()
    {
        return this.vanillaSlot;
    }

    @Override
    public ItemStackList getIngredients()
    {
        return ItemStackList.of(this.vanillaSlot.getItem());
    }

    private void drawContents(ModularGuiContext context)
    {
        Screen guiScreen = this.getScreen().getScreenWrapper().wrappedScreen();
        if (!(guiScreen instanceof AbstractContainerScreen<?> container))
        {
            return;
        }
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) container;
        ItemStack shown = this.vanillaSlot.getItem();
        ItemStack carried = ItemStack.EMPTY;
        if (guiScreen.getMinecraft().player != null)
        {
            carried = guiScreen.getMinecraft().player.containerMenu.getCarried();
        }
        boolean dragPreview = false;
        int amount = -1;
        String format = null;
        boolean hideItem = this.vanillaSlot == accessor.getClickedSlot()
                && !accessor.getDraggingItem().isEmpty()
                && !accessor.getIsSplittingStack();

        if (this.vanillaSlot == accessor.getClickedSlot() && !accessor.getDraggingItem().isEmpty()
                && accessor.getIsSplittingStack() && !shown.isEmpty())
        {
            shown = shown.copy();
            shown.setCount(Math.max(1, shown.getCount() / 2));
        }
        else if (accessor.getIsQuickCrafting() && accessor.getQuickCraftSlots().contains(this.vanillaSlot) && !carried.isEmpty())
        {
            if (accessor.getQuickCraftSlots().size() == 1)
            {
                return;
            }
            AbstractContainerMenu menu = container.getMenu();
            if (AbstractContainerMenu.canItemQuickReplace(this.vanillaSlot, carried, true) && menu.canDragTo(this.vanillaSlot))
            {
                shown = carried.copy();
                dragPreview = true;
                int maxSize = Math.min(shown.getMaxStackSize(), this.vanillaSlot.getMaxStackSize(shown));
                amount = this.vanillaSlot.getItem().getCount();
                amount += AbstractContainerMenu.getQuickCraftPlaceCount(accessor.getQuickCraftSlots(), accessor.getQuickCraftingType(), shown);
                if (amount > maxSize)
                {
                    amount = maxSize;
                    format = ChatFormatting.YELLOW.toString();
                }
            }
            else
            {
                accessor.getQuickCraftSlots().remove(this.vanillaSlot);
                accessor.invokeRecalculateQuickCraftRemaining();
            }
        }

        float depth = context.getCurrentDrawingZ() + 100.0F;
        context.graphicsPose().pushPose();
        context.graphicsPose().translate(0.0F, 0.0F, depth);
        if (!hideItem)
        {
            if (dragPreview)
            {
                GuiDraw.drawRect(context.getGraphics(), INSET, INSET, ITEM, ITEM, 0x80FFFFFF);
            }
            if (shown.isEmpty())
            {
                this.drawEmptyIcon(context);
            }
            if (!shown.isEmpty())
            {
                RenderSystem.enableDepthTest();
                context.getGraphics().renderItem(shown, INSET, INSET);
                if (amount < 0)
                {
                    amount = shown.getCount();
                }
                GuiDraw.drawStandardSlotAmountText(context, amount, format, this.getArea(), depth);
                // Decorations hide the count by drawing a one-stack. That copy
                // must not be the entity's offhand stack, or the count sticks at 1.
                ItemStack decorated = shown.copy();
                decorated.setCount(1);
                context.getGraphics().renderItemDecorations(((ScreenAccessor) guiScreen).getFont(), decorated, INSET, INSET, null);
                RenderSystem.disableDepthTest();
            }
        }
        context.graphicsPose().popPose();
    }

    private void drawEmptyIcon(ModularGuiContext context)
    {
        if (!this.vanillaSlot.getItem().isEmpty())
        {
            return;
        }
        Pair<ResourceLocation, ResourceLocation> icon = this.vanillaSlot.getNoItemIcon();
        if (icon == null)
        {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(icon.getFirst()).apply(icon.getSecond());
        RenderSystem.disableDepthTest();
        context.getGraphics().blit(INSET, INSET, 0, ITEM, ITEM, sprite);
    }
}
