package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.FormationLayout;
import com.ancientempires.entity.npc.NpcArmy;
import com.ancientempires.entity.npc.NpcFormation;
import com.ancientempires.network.ArmyPacket;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.network.UnitOrderPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Placing a Hold or Defend (docs/army-command-design.md), chosen on the battle
 * wheel: the crosshair is live again, and every soldier of the picked units
 * shows as a ghost on the ground where it will stand. A click there gives the
 * order, facing away from the player (the scroll wheel turns it); holding the
 * button and turning the view drags the front line from where it was pressed
 * to where it is let go, its length the frontage, facing away from the player
 * across it. Right click, the order key, or any screen cancels. The ghosts are
 * laid out by the same {@link FormationLayout} as the server's slots.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, value = Dist.CLIENT)
public final class PlacementMode
{
    private static final double AIM_RANGE = 96.0D;
    private static final float TURN_STEP = 15.0F;
    /** Ghost colours, one per unit in turn (gold first, as the glow). */
    private static final int[] COLOURS = {0xE2C27A, 0x7AB8E2, 0xE27A7A, 0x8AE27A, 0xC27AE2, 0xE2A07A};

    @Nullable
    private static NpcArmy.Command command;
    @Nullable
    private static Vec3 pressedAt;
    private static float turn;
    @Nullable
    private static Vec3 aim;
    /** The ghosts and what they were worked out for, kept until any of it changes. */
    private static List<Ghost> ghosts = List.of();
    @Nullable
    private static String ghostsFor;

    private record Ghost(Vec3 at, int colour, boolean leader)
    {
    }

    private PlacementMode()
    {
    }

    public static boolean active()
    {
        return command != null;
    }

    public static void start(NpcArmy.Command order)
    {
        command = order;
        pressedAt = null;
        turn = 0.0F;
        ghostsFor = null;
        ArmyPicks.setGlowing(true);
    }

    public static void cancel()
    {
        command = null;
        pressedAt = null;
        ghosts = List.of();
        ghostsFor = null;
        ArmyPicks.setGlowing(false);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || command == null)
        {
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null)
        {
            // A screen (pause, inventory) or leaving the world calls it off.
            cancel();
            return;
        }
        aim = aimPoint(minecraft);
        refreshGhosts(minecraft);
    }

    /** The ground spot under the crosshair, up to the order range: the open cell above the block looked at. */
    @Nullable
    private static Vec3 aimPoint(Minecraft minecraft)
    {
        Entity camera = minecraft.getCameraEntity();
        HitResult hit = camera == null ? null : camera.pick(AIM_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() == HitResult.Type.MISS)
        {
            return null;
        }
        BlockPos open = block.getBlockPos().relative(block.getDirection());
        return new Vec3(block.getLocation().x, open.getY(), block.getLocation().z);
    }

    /** The placement as it stands now: a spot, or the line dragged from where the button went down. */
    @Nullable
    private static NpcArmy.Placement placement(Minecraft minecraft)
    {
        if (aim == null || minecraft.player == null)
        {
            return null;
        }
        Vec3 from = pressedAt != null ? pressedAt : aim;
        Vec3 to = aim;
        NpcArmy.Placement spot = new NpcArmy.Placement(from, from, Mth.wrapDegrees(minecraft.player.getYRot() + turn));
        NpcArmy.Placement line = new NpcArmy.Placement(from, to, 0.0F);
        if (pressedAt == null || !line.line())
        {
            return spot;
        }
        // Across the line, the side away from the player.
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double nx = -dz;
        double nz = dx;
        Vec3 middle = from.add(to).scale(0.5D);
        if (nx * (middle.x - minecraft.player.getX()) + nz * (middle.z - minecraft.player.getZ()) < 0.0D)
        {
            nx = -nx;
            nz = -nz;
        }
        return new NpcArmy.Placement(from, to, NpcFormation.yawToward(0.0D, 0.0D, nx, nz));
    }

    private static void refreshGhosts(Minecraft minecraft)
    {
        NpcArmy.Placement placement = placement(minecraft);
        List<ArmyPacket.Entry> units = ArmyPicks.pickedUnits();
        if (placement == null || units.isEmpty())
        {
            ghosts = List.of();
            ghostsFor = null;
            return;
        }
        String key = BlockPos.containing(placement.from()) + "|" + BlockPos.containing(placement.to()) + "|" + Math.round(placement.facing()) + "|"
                + units.hashCode();
        if (key.equals(ghostsFor))
        {
            return;
        }
        ghostsFor = key;
        FormationLayout.Unit[] shapes = new FormationLayout.Unit[units.size()];
        for (int i = 0; i < shapes.length; i++)
        {
            shapes[i] = ArmyPicks.shape(units.get(i));
        }
        boolean line = placement.line();
        Vec3 at = line ? placement.from().add(placement.to()).scale(0.5D) : placement.from();
        FormationLayout.Placed[] placed = FormationLayout.place(shapes, at.x, at.z, placement.facing(), line ? placement.length() : 0.0D, NpcArmy.unitGap());
        double[] forward = NpcFormation.forward(placement.facing());
        double rightX = -forward[1];
        double rightZ = forward[0];
        List<Ghost> made = new ArrayList<>();
        for (int i = 0; i < placed.length; i++)
        {
            FormationLayout.Unit unit = shapes[i];
            FormationLayout.Settings settings = unit.settings();
            if (placed[i].width() > 0)
            {
                settings = new FormationLayout.Settings(placed[i].width(), settings.columnWidth(), settings.spacing(), settings.riderFactor(), settings.squadSize());
            }
            FormationLayout layout = FormationLayout.of(unit.shape(), unit.melee(), unit.archers(), unit.riders(), unit.officers(), settings);
            int colour = COLOURS[i % COLOURS.length];
            BlockPos leader = NpcArmy.ground(minecraft.level, placed[i].x(), at.y, placed[i].z());
            made.add(new Ghost(Vec3.atBottomCenterOf(leader), colour, true));
            for (double[][] group : new double[][][] {layout.melee(), layout.archers(), layout.riders(), layout.officers()})
            {
                for (double[] slot : group)
                {
                    double x = placed[i].x() + rightX * slot[0] - forward[0] * slot[1];
                    double z = placed[i].z() + rightZ * slot[0] - forward[1] * slot[1];
                    BlockPos ground = NpcArmy.ground(minecraft.level, x, at.y, z);
                    made.add(new Ghost(new Vec3(x, ground.getY(), z), colour, group == layout.officers()));
                }
            }
        }
        ghosts = made;
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (command == null || minecraft.screen != null)
        {
            return;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        {
            if (event.getAction() == GLFW.GLFW_PRESS)
            {
                cancel();
            }
            event.setCanceled(true);
        }
        else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT)
        {
            if (event.getAction() == GLFW.GLFW_PRESS)
            {
                pressedAt = aim;
            }
            else if (event.getAction() == GLFW.GLFW_RELEASE && pressedAt != null)
            {
                give(minecraft);
            }
            event.setCanceled(true);
        }
    }

    private static void give(Minecraft minecraft)
    {
        NpcArmy.Placement placement = placement(minecraft);
        NpcArmy.Command order = command;
        if (placement != null && order != null)
        {
            ModNetwork.sendToServer(new UnitOrderPacket(Set.copyOf(ArmyPicks.selected()), order, placement));
        }
        cancel();
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event)
    {
        if (command != null && Minecraft.getInstance().screen == null)
        {
            turn += event.getScrollDelta() > 0.0D ? -TURN_STEP : TURN_STEP;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || command == null || ghosts.isEmpty())
        {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(BlockOutlines.Types.LINES_THROUGH);
        for (Ghost ghost : ghosts)
        {
            float r = (ghost.colour() >> 16 & 255) / 255.0F;
            float g = (ghost.colour() >> 8 & 255) / 255.0F;
            float b = (ghost.colour() & 255) / 255.0F;
            Vec3 at = ghost.at();
            double half = ghost.leader() ? 0.4D : 0.3D;
            AABB box = new AABB(at.x - half, at.y, at.z - half, at.x + half, at.y + (ghost.leader() ? 1.8D : 0.1D), at.z + half);
            LevelRenderer.renderLineBox(pose, lines, box, r, g, b, ghost.leader() ? 0.9F : 0.75F);
        }
        buffers.endBatch(BlockOutlines.Types.LINES_THROUGH);
        pose.popPose();
    }

    /** What the buttons do, under the crosshair while placing. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (command == null || event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type())
        {
            return;
        }
        int x = event.getWindow().getGuiScaledWidth() / 2;
        int y = event.getWindow().getGuiScaledHeight() / 2 + 14;
        Component title = Component.translatable("gui.ancientempires.army.placing." + command.id());
        event.getGuiGraphics().drawCenteredString(minecraft.font, title, x, y, 0xFFE2C27A);
        Component help = Component.translatable(aim == null ? "gui.ancientempires.army.placing.no_ground"
                : pressedAt != null ? "gui.ancientempires.army.placing.dragging" : "gui.ancientempires.army.placing.help");
        event.getGuiGraphics().drawCenteredString(minecraft.font, help, x, y + 11, 0xFFE9DCC0);
    }
}
