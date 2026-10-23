package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.WorksiteBlockEntity;
import com.ancientempires.inventory.WorksiteMenu;
import com.ancientempires.registry.ModMenus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;

/**
 * A worksite's storage, in the NPC inventory's parchment style: the supply
 * groups along the top, each labelled, with a faint picture of what goes in
 * an empty slot; the store below; the player's inventory last.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WorksiteScreen extends AbstractContainerScreen<WorksiteMenu>
{
    private static final int PARCHMENT = 0xFF4A3B2A;
    private static final int RIM_LIGHT = 0xFF6A5640;
    private static final int RIM_DARK = 0xFF2A2018;
    private static final int SLOT_FILL = 0xFF2C241C;
    private static final int SLOT_SHADE = 0xFF1A1410;
    private static final int SLOT_EDGE = 0xFF5A4834;
    private static final int GOLD = 0xFFC6A56A;
    private static final int INK = 0xFFE6D2A2;
    /** Laid over a ghost item so it reads as a hint, not an item. */
    private static final int GHOST_VEIL = 0xB02C241C;

    public WorksiteScreen(WorksiteMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.imageWidth = WorksiteMenu.WIDTH;
        this.imageHeight = menu.height();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = menu.playerY() - 11;
    }

    @SubscribeEvent
    public static void register(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.WORKSITE.get(), WorksiteScreen::new));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int left = this.leftPos;
        int top = this.topPos;
        // Parchment panel with a raised rim, as the NPC inventory.
        graphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, PARCHMENT);
        graphics.fill(left, top, left + this.imageWidth, top + 1, RIM_LIGHT);
        graphics.fill(left, top, left + 1, top + this.imageHeight, RIM_LIGHT);
        graphics.fill(left, top + this.imageHeight - 1, left + this.imageWidth, top + this.imageHeight, RIM_DARK);
        graphics.fill(left + this.imageWidth - 1, top, left + this.imageWidth, top + this.imageHeight, RIM_DARK);
        for (Slot slot : this.menu.slots)
        {
            this.slotWell(graphics, left + slot.x, top + slot.y);
        }
        this.renderSupplies(graphics, left, top);
    }

    /** One sunken slot. */
    private void slotWell(GuiGraphics graphics, int x, int y)
    {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, SLOT_SHADE);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    /** Each supply group's name over its slots, and a faint picture in its empty slots. */
    private void renderSupplies(GuiGraphics graphics, int left, int top)
    {
        List<WorksiteBlockEntity.Supply> supplies = this.menu.supplies();
        int slotIndex = 0;
        for (int group = 0; group < supplies.size(); group++)
        {
            WorksiteBlockEntity.Supply supply = supplies.get(group);
            int groupX = left + this.menu.supplyX(group, 0);
            int groupW = supply.slots() * WorksiteMenu.SLOT;
            String label = Component.translatable("gui.ancientempires.supply." + supply.key()).getString();
            FitText.draw(graphics, this.font, label, groupX - 1, top + WorksiteMenu.SUPPLY_LABEL_Y, groupW, 8, INK, true, false);
            for (int slot = 0; slot < supply.slots(); slot++)
            {
                if (!this.menu.slots.get(slotIndex + slot).hasItem())
                {
                    int x = left + this.menu.supplyX(group, slot);
                    int y = top + WorksiteMenu.SUPPLY_Y;
                    graphics.renderFakeItem(supply.icon().get(), x, y);
                    graphics.pose().pushPose();
                    // Above the item, which draws at z 100 and more.
                    graphics.pose().translate(0.0F, 0.0F, 250.0F);
                    graphics.fill(x, y, x + 16, y + 16, GHOST_VEIL);
                    graphics.pose().popPose();
                }
            }
            slotIndex += supply.slots();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, GOLD, false);
        if (!this.menu.supplies().isEmpty())
        {
            graphics.drawString(this.font, Component.translatable("gui.ancientempires.supply.store"), 8, this.menu.storeY() - 10, INK, false);
        }
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, INK, false);
    }
}
