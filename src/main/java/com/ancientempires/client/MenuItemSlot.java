package com.ancientempires.client;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.core.mixins.client.AbstractContainerScreenAccessor;
import brachy.modularui.core.mixins.client.ScreenAccessor;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.integration.recipeviewer.entry.item.ItemStackList;
import brachy.modularui.screen.ClientScreenHandler;
import brachy.modularui.screen.RichTooltip;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widgets.slot.ItemSlot;
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
    private final Slot vanillaSlot;

    public MenuItemSlot(Slot vanillaSlot)
    {
        this.vanillaSlot = vanillaSlot;
    }

    @Override
    public void onUpdate()
    {
        // ItemSlot.onUpdate reads a ModularSlot sync handler this widget does not have.
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

        this.drawEmptyIcon(context);

        float depth = context.getCurrentDrawingZ() + 100.0F;
        context.graphicsPose().pushPose();
        context.graphicsPose().translate(0.0F, 0.0F, depth);
        if (!hideItem)
        {
            if (dragPreview)
            {
                GuiDraw.drawRect(context.getGraphics(), 1, 1, 16, 16, 0x80FFFFFF);
            }
            if (!shown.isEmpty())
            {
                RenderSystem.enableDepthTest();
                context.getGraphics().renderItem(shown, 1, 1);
                if (amount < 0)
                {
                    amount = shown.getCount();
                }
                GuiDraw.drawStandardSlotAmountText(context, amount, format, this.getArea(), depth);
                int cachedCount = shown.getCount();
                shown.setCount(1);
                context.getGraphics().renderItemDecorations(((ScreenAccessor) guiScreen).getFont(), shown, 1, 1, null);
                shown.setCount(cachedCount);
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
        context.getGraphics().blit(1, 1, 0, 16, 16, sprite);
    }
}
