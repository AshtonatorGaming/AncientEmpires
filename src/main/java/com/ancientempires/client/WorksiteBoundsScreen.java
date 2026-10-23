package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.WorksiteArea;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.network.WorksiteBoundsPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Ancient Warfare's worksite bounds screen: move the area north, south, east,
 * or west, and set its width and length, within the worksite's limit (raised
 * by size upgrades) and {@code work.areaReach} of the block. A top-down map
 * shows the area and the block; the area is outlined in the world while the
 * screen is open. Each change is sent at once and checked again by the server.
 * Shift moves or resizes by five.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WorksiteBoundsScreen extends Screen
{
    private static final int PANEL_W = 236;
    private static final int PANEL_H = 190;
    private static final int MAP = 112;
    private static final int PANEL = 0xD0101010;
    private static final int GOLD = 0xFFC6A56A;
    private static final int INK = 0xFFE6D2A2;
    private static final int AREA = 0xA05FBF4A;
    private static final int AREA_EDGE = 0xFF8FE07A;
    private static final int BLOCK = 0xFFE0B040;
    private static final int WARN = 0xFFFF7060;

    private WorksiteBoundsPacket.State state;
    private final List<Button> controls = new ArrayList<>();
    private Component warning = Component.empty();

    private WorksiteBoundsScreen(WorksiteBoundsPacket.State state)
    {
        super(Component.translatable("gui.ancientempires.bounds.title"));
        this.state = state;
    }

    /** Opens the screen, or refreshes it when it is already open on this worksite. */
    public static void show(WorksiteBoundsPacket.State state)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WorksiteBoundsScreen open && open.state.pos().equals(state.pos()))
        {
            open.state = state;
            open.updateControls();
            return;
        }
        minecraft.setScreen(new WorksiteBoundsScreen(state));
    }

    @Override
    protected void init()
    {
        this.controls.clear();
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int cx = left + 12 + MAP + 12;
        int bw = 44;
        // Compass of move buttons.
        this.control(cx + bw / 2 + 2, top + 26, bw, "gui.ancientempires.bounds.north", 0, -1, false);
        this.control(cx, top + 48, bw, "gui.ancientempires.bounds.west", -1, 0, false);
        this.control(cx + bw + 4, top + 48, bw, "gui.ancientempires.bounds.east", 1, 0, false);
        this.control(cx + bw / 2 + 2, top + 70, bw, "gui.ancientempires.bounds.south", 0, 1, false);
        // Width and length.
        this.control(cx, top + 100, bw, "gui.ancientempires.bounds.narrower", -1, 0, true);
        this.control(cx + bw + 4, top + 100, bw, "gui.ancientempires.bounds.wider", 1, 0, true);
        this.control(cx, top + 122, bw, "gui.ancientempires.bounds.shorter", 0, -1, true);
        this.control(cx + bw + 4, top + 122, bw, "gui.ancientempires.bounds.longer", 0, 1, true);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(left + PANEL_W / 2 - 30, top + PANEL_H - 24, 60, 18).build());
        this.updateControls();
    }

    private void control(int x, int y, int width, String key, int dx, int dz, boolean resize)
    {
        Button button = Button.builder(Component.translatable(key), pressed -> this.change(dx, dz, resize))
                .bounds(x, y, width, 18).build();
        this.controls.add(button);
        this.addRenderableWidget(button);
    }

    private void updateControls()
    {
        for (Button button : this.controls)
        {
            button.active = !this.state.locked();
        }
    }

    private void change(int dx, int dz, boolean resize)
    {
        int step = hasShiftDown() ? 5 : 1;
        WorksiteArea area = this.state.area();
        WorksiteArea next = resize ? area.resized(dx * step, dz * step) : area.moved(dx * step, dz * step);
        if (resize && step > 1)
        {
            // A shift-resize stops at the limit instead of failing outright.
            next = clampSize(next, this.state.maxSize());
        }
        Component problem = this.problem(next);
        if (problem != null)
        {
            this.warning = problem;
            return;
        }
        this.warning = Component.empty();
        this.state = new WorksiteBoundsPacket.State(this.state.pos(), next, this.state.maxSize(), this.state.reach(),
                this.state.mayContainBlock(), this.state.locked(), this.state.tier(), this.state.kind());
        ModNetwork.sendWorksiteArea(this.state.pos(), next);
    }

    private static WorksiteArea clampSize(WorksiteArea area, int maxSize)
    {
        int maxX = area.minX() + Math.max(0, Math.min(area.width(), maxSize) - 1);
        int maxZ = area.minZ() + Math.max(0, Math.min(area.length(), maxSize) - 1);
        return new WorksiteArea(area.minX(), area.minZ(), Math.max(area.minX(), maxX), Math.max(area.minZ(), maxZ));
    }

    /** Why an area is refused, or null when it fits. Mirrors {@link WorksiteArea#fits}. */
    private Component problem(WorksiteArea area)
    {
        if (area.width() < 1 || area.length() < 1)
        {
            return Component.translatable("gui.ancientempires.bounds.too_small");
        }
        if (area.width() > this.state.maxSize() || area.length() > this.state.maxSize())
        {
            return Component.translatable("gui.ancientempires.bounds.too_large", this.state.maxSize());
        }
        if (area.gap() > this.state.reach() + 1)
        {
            return Component.translatable("gui.ancientempires.bounds.too_far", this.state.reach());
        }
        if (!this.state.mayContainBlock() && area.containsBlock())
        {
            return Component.translatable("gui.ancientempires.bounds.covers_block");
        }
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        // No dimming: the outlined area stays visible in the world behind.
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, PANEL);
        graphics.drawCenteredString(this.font, this.title, left + PANEL_W / 2, top + 7, GOLD);
        this.renderMap(graphics, left + 12, top + 24);
        WorksiteArea area = this.state.area();
        int textX = left + 12 + MAP + 12;
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.bounds.size", area.width(), area.length(), this.state.maxSize()),
                textX, top + 146, INK, false);
        graphics.drawString(this.font, Component.translatable("gui.ancientempires.bounds.upgrade." + this.state.tier()),
                left + 12, top + 24 + MAP + 4, 0xFFA09070, false);
        if (this.state.locked())
        {
            FitText.draw(graphics, this.font, Component.translatable("gui.ancientempires.bounds.locked").getString(),
                    left + 8, top + 158, PANEL_W - 16, 9, WARN, true, false);
        }
        else if (!this.warning.getString().isEmpty())
        {
            FitText.draw(graphics, this.font, this.warning.getString(), left + 8, top + 158, PANEL_W - 16, 9, WARN, true, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Top-down, north up: the area in green, the worksite block in gold. */
    private void renderMap(GuiGraphics graphics, int x, int y)
    {
        graphics.fill(x - 1, y - 1, x + MAP + 1, y + MAP + 1, 0xFF3A3026);
        graphics.fill(x, y, x + MAP, y + MAP, 0xFF1A1612);
        WorksiteArea area = this.state.area();
        int minX = Math.min(area.minX(), 0) - 1;
        int maxX = Math.max(area.maxX(), 0) + 1;
        int minZ = Math.min(area.minZ(), 0) - 1;
        int maxZ = Math.max(area.maxZ(), 0) + 1;
        int span = Math.max(maxX - minX + 1, maxZ - minZ + 1);
        float cell = (float) MAP / span;
        // Centre the drawing on the shorter axis.
        float offX = x + (MAP - (maxX - minX + 1) * cell) / 2.0F;
        float offZ = y + (MAP - (maxZ - minZ + 1) * cell) / 2.0F;
        int ax0 = Math.round(offX + (area.minX() - minX) * cell);
        int az0 = Math.round(offZ + (area.minZ() - minZ) * cell);
        int ax1 = Math.round(offX + (area.maxX() - minX + 1) * cell);
        int az1 = Math.round(offZ + (area.maxZ() - minZ + 1) * cell);
        graphics.fill(ax0, az0, ax1, az1, AREA);
        graphics.renderOutline(ax0, az0, Math.max(1, ax1 - ax0), Math.max(1, az1 - az0), AREA_EDGE);
        int bx0 = Math.round(offX + (0 - minX) * cell);
        int bz0 = Math.round(offZ + (0 - minZ) * cell);
        int size = Math.max(2, Math.round(cell));
        graphics.fill(bx0, bz0, bx0 + size, bz0 + size, BLOCK);
        graphics.drawCenteredString(this.font, "N", x + MAP / 2, y + 2, 0xFFB0A080);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /** The open screen's area as a box in the world, a little above and below the block. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !(Minecraft.getInstance().screen instanceof WorksiteBoundsScreen screen))
        {
            return;
        }
        BlockPos pos = screen.state.pos();
        WorksiteArea area = screen.state.area();
        AABB box = new AABB(pos.getX() + area.minX(), pos.getY() - 1, pos.getZ() + area.minZ(),
                pos.getX() + area.maxX() + 1, pos.getY() + 2, pos.getZ() + area.maxZ() + 1);
        Camera camera = event.getCamera();
        Vec3 view = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-view.x, -view.y, -view.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, 0.45F, 0.9F, 0.35F, 1.0F);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}
