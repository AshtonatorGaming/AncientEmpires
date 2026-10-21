package com.ancientempires.client;

import brachy.modularui.api.IMuiScreen;
import brachy.modularui.screen.ClientScreenHandler;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import com.ancientempires.AncientEmpires;
import com.ancientempires.inventory.NpcLayout;
import com.ancientempires.inventory.NpcMenu;
import com.ancientempires.registry.ModMenus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * ModularUI view of {@link NpcMenu}. The menu, the container id, and the slot
 * click packet are unchanged. This screen only draws and routes input.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class NpcScreen extends AbstractContainerScreen<NpcMenu> implements IMuiScreen
{
    private final ModularScreen modularScreen;
    private boolean contentReady;

    public NpcScreen(NpcMenu menu, Inventory playerInventory, Component title)
    {
        super(menu, playerInventory, title);
        this.imageWidth = NpcLayout.WIDTH;
        this.imageHeight = NpcLayout.HEIGHT;
        // ModularScreen needs a panel before the screen opens. The shell stays empty here.
        // construct only binds this wrapper. It does not initialise widgets.
        ModularPanel<?> panel = ModularPanel.defaultPanel("npc_inventory", NpcLayout.WIDTH, NpcLayout.HEIGHT);
        this.modularScreen = new NpcModularScreen(panel);
        this.modularScreen.pausesGame(false);
        this.modularScreen.construct(this);
    }

    @Override
    public ModularScreen screen()
    {
        return this.modularScreen;
    }

    @Override
    protected void init()
    {
        this.imageWidth = NpcLayout.WIDTH;
        this.imageHeight = NpcLayout.HEIGHT;
        // Opening has already made this the current screen. Settings have to exist
        // before Init.Post, which is what initialises the panel and its widgets.
        if (!this.modularScreen.getContext().hasSettings())
        {
            this.modularScreen.getContext().setSettings(new UISettings());
        }
        if (!this.contentReady)
        {
            NpcInventoryPanel.populate(this.modularScreen.getMainPanel(), this.menu);
            this.contentReady = true;
        }
        super.init();
    }

    /**
     * {@code AbstractContainerScreen.render} keeps drawing vanilla slots after
     * {@code ScreenEvent.Render.Pre}. ModularUI draws the panel from that event,
     * so this stops once the event is cancelled.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        if (MinecraftForge.EVENT_BUS.post(new ScreenEvent.Render.Pre(this, graphics, mouseX, mouseY, partialTick)))
        {
            return;
        }
        ClientScreenHandler.drawContainer(graphics, this.modularScreen, this, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics)
    {
        this.handleDrawBackground(graphics, super::renderBackground);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
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

    /**
     * Stock {@link ModularScreen#isClientOnly()} asks the menu for a
     * {@code ModularContainerMenu}. This screen keeps {@link NpcMenu}.
     */
    private static final class NpcModularScreen extends ModularScreen
    {
        private NpcModularScreen(ModularPanel<?> panel)
        {
            super("npc_inventory", panel);
        }

        @Override
        public boolean isClientOnly()
        {
            return true;
        }
    }
}
