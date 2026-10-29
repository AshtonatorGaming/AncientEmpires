package com.ancientempires.client;

import com.ancientempires.entity.npc.NpcArmy;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcFormation;
import com.ancientempires.entity.npc.NpcOrder;
import com.ancientempires.entity.npc.NpcStance;
import com.ancientempires.network.ArmyPacket;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.network.UnitOrderPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The battle wheel (docs/army-command-design.md): hold the order key (V) and a
 * ring of orders opens around the crosshair, the view frozen where it was
 * aimed. It is a tree: Formation, Stance, and Maneuver open rings of their
 * own, each with Back. The units bar across the top shows the numbered units;
 * 1 to 9 (or a click) pick them, 0 picks all, and the picked ones glow in the
 * world while the wheel is up. Letting go of the key over an order gives it,
 * over a submenu opens it; a click does the same; right click goes back up.
 * Move and Defend close the wheel into placement ({@link PlacementMode}).
 */
@OnlyIn(Dist.CLIENT)
public final class BattleWheelScreen extends Screen
{
    private static final int INNER = 46;
    private static final int OUTER = 112;
    /** How far a hovered slice stands out of the ring. */
    private static final int POP = 6;
    private static final int LABEL_RADIUS = OUTER + 12;
    private static final int GOLD = 0xFFE2C27A;
    private static final int PARCHMENT = 0xFFE9DCC0;
    private static final int BAR_WIDTH = 84;
    private static final int BAR_HEIGHT = 26;
    private static final int BAR_TOP = 8;
    private static final double AIM_RANGE = 96.0D;

    /**
     * A slice: an order to give (or to place, for Move and Defend), or a
     * submenu to open (0 is the top, as Back), with an item or a glyph for icon.
     */
    private record Slice(String id, @Nullable NpcArmy.Command command, int menu, ItemStack icon, String glyph, boolean placed)
    {
        static Slice order(NpcArmy.Command command, ItemStack icon)
        {
            return new Slice(command.id(), command, -1, icon, "", false);
        }

        static Slice order(NpcArmy.Command command, String glyph)
        {
            return new Slice(command.id(), command, -1, ItemStack.EMPTY, glyph, false);
        }

        static Slice menu(String id, int menu, ItemStack icon)
        {
            return new Slice(id, null, menu, icon, "", false);
        }

        String key()
        {
            return this.command == null ? "gui.ancientempires.army.menu." + this.id : "gui.ancientempires.army.command." + this.id;
        }
    }

    private static final Slice BACK = new Slice("back", null, 0, ItemStack.EMPTY, "↩", false);
    private static final Slice[][] MENUS = {
            {new Slice("move", NpcArmy.Command.HOLD, -1, new ItemStack(Items.COMPASS), "", true),
                    new Slice(NpcArmy.Command.DEFEND.id(), NpcArmy.Command.DEFEND, -1, new ItemStack(Items.SHIELD), "", true),
                    Slice.order(NpcArmy.Command.CHARGE, new ItemStack(Items.IRON_SWORD)),
                    Slice.order(NpcArmy.Command.FOLLOW, new ItemStack(Items.LEAD)),
                    Slice.order(NpcArmy.Command.FALL_BACK, new ItemStack(Items.WHITE_BANNER)),
                    Slice.order(NpcArmy.Command.FREE, new ItemStack(Items.FEATHER)),
                    Slice.menu("formation", 1, new ItemStack(Items.ARMOR_STAND)),
                    Slice.menu("stance", 2, new ItemStack(Items.TARGET)),
                    Slice.menu("maneuver", 3, new ItemStack(Items.LEATHER_BOOTS))},
            {Slice.order(NpcArmy.Command.LINE, new ItemStack(Items.IRON_SWORD)), Slice.order(NpcArmy.Command.COLUMN, new ItemStack(Items.LADDER)),
                    Slice.order(NpcArmy.Command.SHIELD_WALL, new ItemStack(Items.SHIELD)), Slice.order(NpcArmy.Command.WEDGE, new ItemStack(Items.ARROW)),
                    Slice.order(NpcArmy.Command.SQUARE, new ItemStack(Items.ITEM_FRAME)), Slice.order(NpcArmy.Command.SKIRMISH, new ItemStack(Items.SNOWBALL)),
                    BACK},
            {Slice.order(NpcArmy.Command.AGGRESSIVE, new ItemStack(Items.DIAMOND_SWORD)), Slice.order(NpcArmy.Command.HOLD_LINE, new ItemStack(Items.IRON_CHESTPLATE)),
                    Slice.order(NpcArmy.Command.PASSIVE, new ItemStack(Items.POPPY)), Slice.order(NpcArmy.Command.FIRE_AT_WILL, new ItemStack(Items.BOW)),
                    Slice.order(NpcArmy.Command.HOLD_FIRE, new ItemStack(Items.STRING)), Slice.order(NpcArmy.Command.VOLLEY, new ItemStack(Items.SPECTRAL_ARROW)),
                    Slice.order(NpcArmy.Command.SHIELDS, new ItemStack(Items.SHIELD)), BACK},
            {Slice.order(NpcArmy.Command.FACE, new ItemStack(Items.SPYGLASS)), Slice.order(NpcArmy.Command.ABOUT_TURN, new ItemStack(Items.RECOVERY_COMPASS)),
                    Slice.order(NpcArmy.Command.LEFT, "←"), Slice.order(NpcArmy.Command.RIGHT, "→"),
                    Slice.order(NpcArmy.Command.ADVANCE, "↑"), Slice.order(NpcArmy.Command.BACK, "↓"),
                    Slice.order(NpcArmy.Command.CLOSE_RANKS, "→←"), Slice.order(NpcArmy.Command.SPREAD_OUT, "←→"),
                    Slice.order(NpcArmy.Command.RELEASE, new ItemStack(Items.NETHERITE_SWORD)), BACK}};

    private int hovered = -1;
    private int menu;
    /** The creature aimed at when the wheel opened (Charge names it). */
    @Nullable
    private final Component aimed;
    private boolean placing;

    private BattleWheelScreen(@Nullable Component aimed)
    {
        super(Component.translatable("gui.ancientempires.army.wheel"));
        this.aimed = aimed;
    }

    public static void open()
    {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new BattleWheelScreen(aimedCreature(minecraft)));
        ArmyPicks.setGlowing(true);
        // A fresh list of units for the bar; the last one shows meanwhile.
        ModNetwork.requestArmyRefresh();
    }

    /** The creature (not one of the player's own) the crosshair is on, up to the order range, as Charge's server side picks it. */
    @Nullable
    private static Component aimedCreature(Minecraft minecraft)
    {
        Entity camera = minecraft.getCameraEntity();
        if (camera == null || minecraft.player == null)
        {
            return null;
        }
        Vec3 eye = camera.getEyePosition(1.0F);
        Vec3 look = camera.getViewVector(1.0F);
        HitResult block = camera.pick(AIM_RANGE, 1.0F, false);
        Vec3 reach = block.getType() == HitResult.Type.MISS ? eye.add(look.scale(AIM_RANGE)) : block.getLocation();
        Predicate<Entity> foe = entity -> entity instanceof LivingEntity living && living.isAlive() && entity != minecraft.player
                && !(entity instanceof NpcEntity npc && minecraft.player.getUUID().equals(npc.getOwner()));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(camera, eye, reach, camera.getBoundingBox().expandTowards(reach.subtract(eye)).inflate(1.0D), foe,
                AIM_RANGE * AIM_RANGE);
        return hit == null ? null : hit.getEntity().getDisplayName();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.hovered = this.pick(mouseX, mouseY);
        Slice[] slices = MENUS[this.menu];
        double step = Math.PI * 2.0D / slices.length;
        double gap = 0.03D;
        for (int i = 0; i < slices.length; i++)
        {
            double from = i * step - Math.PI / 2.0D - step / 2.0D + gap;
            double to = from + step - gap * 2.0D;
            boolean on = i == this.hovered;
            int outer = OUTER + (on ? POP : 0);
            arc(graphics, cx, cy, INNER, outer, from, to, on ? 0xE86B4E24 : 0xC8201810);
            if (this.current(slices[i]))
            {
                arc(graphics, cx, cy, outer - 3, outer, from, to, GOLD);
            }
            else
            {
                arc(graphics, cx, cy, outer - 1, outer, from, to, 0xFF8A6A3A);
            }
        }
        disc(graphics, cx, cy, INNER - 4, 0xD8201810);
        for (int i = 0; i < slices.length; i++)
        {
            double angle = i * step - Math.PI / 2.0D;
            boolean on = i == this.hovered;
            int middle = (INNER + OUTER) / 2 + (on ? POP / 2 : 0);
            int x = cx + (int) Math.round(Math.cos(angle) * middle);
            int y = cy + (int) Math.round(Math.sin(angle) * middle);
            Slice slice = slices[i];
            if (!slice.icon().isEmpty())
            {
                graphics.renderItem(slice.icon(), x - 8, y - 8);
            }
            else
            {
                graphics.drawCenteredString(this.font, slice.glyph(), x, y - 4, on ? 0xFFFFFFFF : GOLD);
            }
            this.label(graphics, slice, angle, cx, cy, on);
        }
        this.centre(graphics, cx, cy);
        this.drawBar(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** The slice's name just outside the ring, leaning away from the middle. */
    private void label(GuiGraphics graphics, Slice slice, double angle, int cx, int cy, boolean on)
    {
        Component text = this.name(slice);
        int width = this.font.width(text);
        double cos = Math.cos(angle);
        int x = cx + (int) Math.round(cos * LABEL_RADIUS);
        int y = cy + (int) Math.round(Math.sin(angle) * LABEL_RADIUS) - 4;
        int left = cos > 0.3D ? x : cos < -0.3D ? x - width : x - width / 2;
        graphics.fill(left - 3, y - 2, left + width + 3, y + 10, on ? 0xD06B4E24 : 0x90101010);
        graphics.drawString(this.font, text, left, y, on ? 0xFFFFFFFF : PARCHMENT);
    }

    /** The hovered slice's name and what it does, in the middle of the ring. */
    private void centre(GuiGraphics graphics, int cx, int cy)
    {
        if (this.hovered < 0)
        {
            graphics.drawCenteredString(this.font, Component.translatable("gui.ancientempires.army.menu.title." + this.menu), cx, cy - 4, GOLD);
            return;
        }
        Slice slice = MENUS[this.menu][this.hovered];
        graphics.drawCenteredString(this.font, this.name(slice), cx, cy - 14, GOLD);
        List<FormattedCharSequence> lines = this.font.split(Component.translatable(slice.key() + ".hint"), (INNER - 6) * 2);
        int y = cy - 2;
        for (FormattedCharSequence line : lines.subList(0, Math.min(3, lines.size())))
        {
            graphics.drawCenteredString(this.font, line, cx, y, 0xFFC8B48C);
            y += 9;
        }
    }

    private Component name(Slice slice)
    {
        if (slice.command() == NpcArmy.Command.CHARGE && this.aimed != null)
        {
            return Component.translatable("gui.ancientempires.army.command.charge_at", this.aimed);
        }
        Component name = Component.translatable(slice.key());
        return slice.command() == null && slice != BACK ? name.copy().append(" ›") : name;
    }

    /** Whether every picked unit already has what this slice sets (its formation, or a stance it holds). */
    private boolean current(Slice slice)
    {
        NpcArmy.Command command = slice.command();
        if (command == null)
        {
            return false;
        }
        List<ArmyPacket.Entry> units = ArmyPicks.pickedUnits();
        if (units.isEmpty())
        {
            return false;
        }
        for (ArmyPacket.Entry unit : units)
        {
            NpcFormation formation = NpcFormation.values()[Math.min(unit.formation(), NpcFormation.values().length - 1)];
            NpcStance stance = unit.unpackStance();
            boolean has = switch (command)
            {
                case LINE -> formation == NpcFormation.LINE;
                case COLUMN -> formation == NpcFormation.COLUMN;
                case SHIELD_WALL -> formation == NpcFormation.SHIELD_WALL;
                case WEDGE -> formation == NpcFormation.WEDGE;
                case SQUARE -> formation == NpcFormation.SQUARE;
                case SKIRMISH -> formation == NpcFormation.SKIRMISH;
                case AGGRESSIVE -> stance.engage() == NpcStance.Engage.AGGRESSIVE;
                case HOLD_LINE -> stance.engage() == NpcStance.Engage.HOLD_LINE;
                case PASSIVE -> stance.engage() == NpcStance.Engage.PASSIVE;
                case FIRE_AT_WILL -> stance.fire() == NpcStance.Fire.AT_WILL;
                case HOLD_FIRE -> stance.fire() == NpcStance.Fire.HOLD;
                case VOLLEY -> stance.fire() == NpcStance.Fire.VOLLEY;
                case SHIELDS -> stance.shieldsUp();
                default -> false;
            };
            if (!has)
            {
                return false;
            }
        }
        return true;
    }

    /** The numbered units across the top: "All" first, then each with its size, order, and a morale pip. */
    private void drawBar(GuiGraphics graphics, int mouseX, int mouseY)
    {
        Map<Integer, ArmyPacket.Entry> units = ArmyPicks.units();
        int count = units.size() + 1;
        int left = this.width / 2 - (count * (BAR_WIDTH + 4) - 4) / 2;
        boolean all = ArmyPicks.selected().isEmpty();
        this.box(graphics, left, all, this.overBar(mouseX, mouseY) == 0);
        graphics.drawCenteredString(this.font, Component.translatable("gui.ancientempires.army.all_units"), left + BAR_WIDTH / 2, BAR_TOP + 5, GOLD);
        graphics.drawCenteredString(this.font, Component.translatable("gui.ancientempires.army.all_key"), left + BAR_WIDTH / 2, BAR_TOP + 15, 0xFFA08C6A);
        if (units.isEmpty())
        {
            graphics.drawString(this.font, Component.translatable("gui.ancientempires.army.no_units_yet"), left + BAR_WIDTH + 8, BAR_TOP + 9, 0xFFA0A0A0);
        }
        int x = left + BAR_WIDTH + 4;
        for (Map.Entry<Integer, ArmyPacket.Entry> unit : units.entrySet())
        {
            ArmyPacket.Entry leader = unit.getValue();
            boolean picked = !all && ArmyPicks.selected().contains(unit.getKey());
            this.box(graphics, x, picked || all, this.overBar(mouseX, mouseY) == unit.getKey());
            graphics.pose().pushPose();
            graphics.pose().translate(x + 4, BAR_TOP + 5, 0);
            graphics.pose().scale(1.5F, 1.5F, 1.0F);
            graphics.drawString(this.font, String.valueOf(unit.getKey()), 0, 0, picked || all ? GOLD : 0xFF907A58);
            graphics.pose().popPose();
            String name = leader.unitName().isEmpty() ? ClientArmy.parse(leader.name()).getString() : leader.unitName();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(name, BAR_WIDTH - 24), x + 18, BAR_TOP + 4, PARCHMENT);
            NpcOrder order = NpcOrder.values()[Math.min(leader.order(), NpcOrder.values().length - 1)];
            Component line = Component.literal(leader.members() + " · ").append(Component.translatable("order.ancientempires." + order.id()));
            graphics.drawString(this.font, line, x + 18, BAR_TOP + 15, 0xFFC8B48C);
            float morale = leader.morale();
            int pip = morale >= 60.0F ? 0xFF4F8A3A : morale >= 30.0F ? 0xFFB8862A : 0xFFC0392B;
            graphics.fill(x + BAR_WIDTH - 7, BAR_TOP + 4, x + BAR_WIDTH - 3, BAR_TOP + 8, pip);
            x += BAR_WIDTH + 4;
        }
    }

    private void box(GuiGraphics graphics, int x, boolean picked, boolean hovered)
    {
        int border = picked ? GOLD : 0xFF5A4128;
        graphics.fill(x - 1, BAR_TOP - 1, x + BAR_WIDTH + 1, BAR_TOP + BAR_HEIGHT + 1, border);
        graphics.fill(x, BAR_TOP, x + BAR_WIDTH, BAR_TOP + BAR_HEIGHT, hovered ? 0xE04A3A26 : picked ? 0xD82B2218 : 0xB8181410);
    }

    /** The unit number of the bar box under the mouse (0 for All), or -1. */
    private int overBar(double mouseX, double mouseY)
    {
        if (mouseY < BAR_TOP || mouseY > BAR_TOP + BAR_HEIGHT)
        {
            return -1;
        }
        Map<Integer, ArmyPacket.Entry> units = ArmyPicks.units();
        int count = units.size() + 1;
        int left = this.width / 2 - (count * (BAR_WIDTH + 4) - 4) / 2;
        int index = (int) Math.floor((mouseX - left) / (BAR_WIDTH + 4));
        if (mouseX < left || index >= count || mouseX - left - index * (BAR_WIDTH + 4) > BAR_WIDTH)
        {
            return -1;
        }
        if (index == 0)
        {
            return 0;
        }
        return units.keySet().stream().skip(index - 1).findFirst().orElse(-1);
    }

    private int pick(double mouseX, double mouseY)
    {
        double dx = mouseX - this.width / 2.0D;
        double dy = mouseY - this.height / 2.0D;
        if (dx * dx + dy * dy < INNER * INNER)
        {
            return -1;
        }
        int count = MENUS[this.menu].length;
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0D;
        int slice = (int) Math.round(angle / (Math.PI * 2.0D) * count);
        return Math.floorMod(slice, count);
    }

    /** Gives the hovered order and closes, opens the hovered submenu, or hands Move and Defend to placement. */
    private void give()
    {
        Slice slice = this.hovered >= 0 ? MENUS[this.menu][this.hovered] : null;
        if (slice != null && slice.command() == null)
        {
            this.menu = slice.menu();
            this.hovered = -1;
            return;
        }
        if (slice != null && slice.placed())
        {
            this.placing = true;
            this.onClose();
            PlacementMode.start(slice.command());
            return;
        }
        if (slice != null)
        {
            ModNetwork.sendToServer(new UnitOrderPacket(Set.copyOf(ArmyPicks.selected()), slice.command()));
        }
        this.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int unit = this.overBar(mouseX, mouseY);
        if (unit >= 0)
        {
            ArmyPicks.toggle(unit);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        {
            if (this.menu != 0)
            {
                this.menu = 0;
            }
            else
            {
                this.onClose();
            }
            return true;
        }
        this.hovered = this.pick(mouseX, mouseY);
        this.give();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers)
    {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9)
        {
            ArmyPicks.toggle(key - GLFW.GLFW_KEY_0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_GRAVE_ACCENT)
        {
            ArmyPicks.toggle(0);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers)
    {
        if (ModKeys.ORDERS.matches(key, scanCode))
        {
            this.give();
            return true;
        }
        return super.keyReleased(key, scanCode, modifiers);
    }

    @Override
    public void removed()
    {
        if (!this.placing)
        {
            ArmyPicks.setGlowing(false);
        }
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics graphics)
    {
        // No darkening: the battlefield stays in view.
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /** A ring segment from {@code from} to {@code to} radians, between two radii, in one colour. */
    private static void arc(GuiGraphics graphics, int cx, int cy, int inner, int outer, double from, double to, int argb)
    {
        // Drawn at once, not batched: whatever the screen queued before goes first.
        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        int steps = Math.max(4, (int) Math.ceil((to - from) / 0.05D));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int a = argb >>> 24;
        int r = argb >> 16 & 255;
        int g = argb >> 8 & 255;
        int b = argb & 255;
        for (int i = 0; i <= steps; i++)
        {
            double angle = from + (to - from) * i / steps;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            buffer.vertex(matrix, cx + cos * outer, cy + sin * outer, 0.0F).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, cx + cos * inner, cy + sin * inner, 0.0F).color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    private static void disc(GuiGraphics graphics, int cx, int cy, int radius, int argb)
    {
        arc(graphics, cx, cy, 0, radius, 0.0D, Math.PI * 2.0D, argb);
    }
}
