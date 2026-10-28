package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcPath;
import com.ancientempires.inventory.NpcMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * The path chooser. Worker and soldier are final for players, so the panel
 * says so before the click; admins also get settler, to undo.
 */
@OnlyIn(Dist.CLIENT)
final class NpcPathChooser extends NpcPopupScreen
{
    private static final int WIDTH = 176;
    private static final int HEIGHT = 92;
    /** Wrap width for the path descriptions, so the tooltip stays compact. */
    private static final int TOOLTIP_W = 150;
    /** Settler sits between the two paths it can split into. For a player it is the current path. */
    private static final List<NpcPath> CHOICES = List.of(NpcPath.WORKER, NpcPath.SETTLER, NpcPath.SOLDIER);

    private final NpcMenu menu;
    private final NpcEntity npc;

    NpcPathChooser(NpcMenu menu, NpcEntity npc)
    {
        super(Component.translatable("gui.ancientempires.path.title"), WIDTH, HEIGHT);
        this.menu = menu;
        this.npc = npc;
    }

    @Override
    protected void init()
    {
        super.init();
        int gap = 4;
        int buttonW = (WIDTH - 12 - gap * (CHOICES.size() - 1)) / CHOICES.size();
        for (int index = 0; index < CHOICES.size(); index++)
        {
            NpcPath path = CHOICES.get(index);
            Component name = Component.translatable(NpcPath.translationKey(path.id()));
            ItemStack icon = pathIcon(path);
            List<Component> description = List.of(Component.translatable("gui.ancientempires.path." + path.id() + ".desc"));
            this.addRenderableWidget(new NpcButton(this.left + 6 + index * (buttonW + gap), this.top + HEIGHT - 40, buttonW, 34, name,
                    (graphics, x, y, width, height) ->
                    {
                        graphics.renderItem(icon, x + (width - 16) / 2, y + 4);
                        FitText.draw(graphics, this.font, name.getString(), x + 2, y + height - 12, width - 4, 10,
                                this.npc.getNpcPath() == path ? NpcGui.GOLD : NpcGui.NAME_INK, true, false);
                    },
                    pressed -> this.pick(path, pressed))
                    .tooltip(() -> description, TOOLTIP_W));
        }
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        FitText.draw(graphics, this.font, this.title.getString(), this.left + 6, this.top + 5, WIDTH - 12, 10, NpcGui.NAME_INK, true, true);
        Player player = Minecraft.getInstance().player;
        boolean admin = player != null && NpcEntity.mayToggleCreative(player);
        Component note = Component.translatable(admin ? "gui.ancientempires.path.admin" : "gui.ancientempires.path.final");
        int y = this.top + 19;
        for (FormattedCharSequence line : this.font.split(note, WIDTH - 12))
        {
            if (y + 9 > this.top + 19 + 36)
            {
                break;
            }
            graphics.drawString(this.font, line, this.left + (WIDTH - this.font.width(line)) / 2, y, NpcGui.NAME_INK, false);
            y += 9;
        }
    }

    private boolean pick(NpcPath path, int pressed)
    {
        if (pressed != 0)
        {
            return false;
        }
        if (path != this.npc.getNpcPath() && this.minecraft != null && this.minecraft.gameMode != null)
        {
            int buttonId = switch (path)
            {
                case SETTLER -> NpcMenu.BUTTON_PATH_SETTLER;
                case WORKER -> NpcMenu.BUTTON_PATH_WORKER;
                case SOLDIER -> NpcMenu.BUTTON_PATH_SOLDIER;
            };
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
        this.onClose();
        return true;
    }

    /** What each path looks like at a glance in the chooser. */
    private static ItemStack pathIcon(NpcPath path)
    {
        return switch (path)
        {
            case SETTLER -> new ItemStack(Items.CAMPFIRE);
            case WORKER -> new ItemStack(Items.IRON_PICKAXE);
            case SOLDIER -> new ItemStack(Items.IRON_SWORD);
        };
    }
}
