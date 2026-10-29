package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.decor.AwSoundBlockEntity;
import com.ancientempires.network.ModNetwork;
import com.ancientempires.network.SoundBlockPacket;
import com.ancientempires.network.ScannerSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Client screens opened from items (kept apart so servers never load them). */
public final class ClientScreens
{
    private ClientScreens()
    {
    }

    public static void openScanner(InteractionHand hand)
    {
        Minecraft.getInstance().setScreen(new ScannerScreen(hand));
    }

    public static void openSoundBlock(BlockPos pos, CompoundTag tune)
    {
        Minecraft.getInstance().setScreen(new SoundBlockScreen(pos, tune));
    }

    /**
     * AW's sound block editor: search the game's sounds and click one to add it,
     * click a chosen one to drop it; the player and sound ranges (blocks), the
     * pause between plays (seconds), random or in order, and whether it keeps
     * playing while someone is near or greets them once.
     */
    private static final class SoundBlockScreen extends Screen
    {
        private static final int RESULTS = 6;
        private static final int SHOWN = 6;

        private final BlockPos pos;
        private final CompoundTag tune;
        private final List<String> sounds = new ArrayList<>();
        private final List<Button> listButtons = new ArrayList<>();
        private EditBox search;
        private EditBox playerRange;
        private EditBox soundRange;
        private EditBox minDelay;
        private EditBox maxDelay;
        private boolean random;
        private boolean whenInRange;

        SoundBlockScreen(BlockPos pos, CompoundTag tune)
        {
            super(Component.translatable("screen.ancientempires.sound_block"));
            this.pos = pos;
            this.tune = tune;
            tune.getList("Sounds", Tag.TAG_STRING).forEach(sound -> this.sounds.add(sound.getAsString()));
            this.random = tune.getBoolean("Random");
            this.whenInRange = !tune.contains("WhenInRange") || tune.getBoolean("WhenInRange");
        }

        @Override
        protected void init()
        {
            int left = this.width / 2 - 150;
            int top = this.height / 2 - 100;
            String query = this.search == null ? "" : this.search.getValue();
            this.search = new EditBox(this.font, left, top + 12, 145, 18, Component.translatable("screen.ancientempires.sound_block.search"));
            this.search.setValue(query);
            this.search.setResponder(text -> this.refreshLists());
            this.addRenderableWidget(this.search);
            this.setInitialFocus(this.search);
            int row = top + 150;
            this.playerRange = this.number(left, row, this.tune.contains("PlayerRange") ? this.tune.getInt("PlayerRange") : 16, "player_range");
            this.soundRange = this.number(left + 76, row, this.tune.contains("SoundRange") ? this.tune.getInt("SoundRange") : 16, "sound_range");
            this.minDelay = this.number(left + 152, row, this.tune.getInt("MinDelay"), "min_delay");
            this.maxDelay = this.number(left + 228, row, this.tune.getInt("MaxDelay"), "max_delay");
            this.addRenderableWidget(Button.builder(this.orderLabel(), button ->
            {
                this.random = !this.random;
                button.setMessage(this.orderLabel());
            }).bounds(left, row + 26, 147, 20).build());
            this.addRenderableWidget(Button.builder(this.playsLabel(), button ->
            {
                this.whenInRange = !this.whenInRange;
                button.setMessage(this.playsLabel());
            }).bounds(left + 153, row + 26, 147, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("screen.ancientempires.scanner.save"), button -> this.save())
                    .bounds(left + 52, row + 52, 96, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                    .bounds(left + 152, row + 52, 96, 20).build());
            this.refreshLists();
        }

        private EditBox number(int x, int y, int value, String key)
        {
            EditBox box = new EditBox(this.font, x, y, 72, 18, Component.translatable("screen.ancientempires.sound_block." + key));
            box.setValue(Integer.toString(value));
            box.setFilter(text -> text.isEmpty() || text.length() <= 4 && text.chars().allMatch(Character::isDigit));
            this.addRenderableWidget(box);
            return box;
        }

        private Component orderLabel()
        {
            return Component.translatable(this.random ? "screen.ancientempires.sound_block.random" : "screen.ancientempires.sound_block.in_order");
        }

        private Component playsLabel()
        {
            return Component.translatable(this.whenInRange ? "screen.ancientempires.sound_block.while_near" : "screen.ancientempires.sound_block.on_arrival");
        }

        /** The search results (left, click to add; this mod's sounds first) and the chosen sounds (right, click to drop). */
        private void refreshLists()
        {
            this.listButtons.forEach(this::removeWidget);
            this.listButtons.clear();
            int left = this.width / 2 - 150;
            int top = this.height / 2 - 100;
            String query = this.search.getValue().toLowerCase(Locale.ROOT);
            List<String> matches = Minecraft.getInstance().getSoundManager().getAvailableSounds().stream().map(ResourceLocation::toString)
                    .filter(id -> id.contains(query))
                    .sorted(Comparator.comparing((String id) -> !id.startsWith(AncientEmpires.MOD_ID + ":")).thenComparing(id -> id))
                    .limit(RESULTS).toList();
            int y = top + 34;
            for (String id : matches)
            {
                this.listButtons.add(this.addRenderableWidget(Button.builder(Component.literal(shorten(id)), button ->
                {
                    if (this.sounds.size() < AwSoundBlockEntity.MAX_SOUNDS)
                    {
                        this.sounds.add(id);
                        this.refreshLists();
                    }
                }).bounds(left, y, 145, 18).tooltip(Tooltip.create(Component.literal(id))).build()));
                y += 19;
            }
            y = top + 12;
            for (int i = 0; i < Math.min(SHOWN, this.sounds.size()); i++)
            {
                int index = i;
                String id = this.sounds.get(i);
                this.listButtons.add(this.addRenderableWidget(Button.builder(Component.literal("x " + shorten(id)), button ->
                {
                    this.sounds.remove(index);
                    this.refreshLists();
                }).bounds(left + 155, y, 145, 18).tooltip(Tooltip.create(Component.literal(id))).build()));
                y += 19;
            }
        }

        private static String shorten(String id)
        {
            String path = id.substring(id.indexOf(':') + 1);
            return path.length() > 22 ? "..." + path.substring(path.length() - 19) : path;
        }

        private static int read(EditBox box, int fallback)
        {
            try
            {
                return Integer.parseInt(box.getValue());
            }
            catch (NumberFormatException exception)
            {
                return fallback;
            }
        }

        private void save()
        {
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            this.sounds.forEach(sound -> list.add(StringTag.valueOf(sound)));
            tag.put("Sounds", list);
            tag.putInt("PlayerRange", Math.max(1, read(this.playerRange, 16)));
            tag.putInt("SoundRange", Math.max(1, read(this.soundRange, 16)));
            tag.putInt("MinDelay", read(this.minDelay, 0));
            tag.putInt("MaxDelay", read(this.maxDelay, 0));
            tag.putBoolean("Random", this.random);
            tag.putBoolean("WhenInRange", this.whenInRange);
            ModNetwork.sendToServer(new SoundBlockPacket(this.pos, tag));
            this.onClose();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            this.renderBackground(graphics);
            int left = this.width / 2 - 150;
            int top = this.height / 2 - 100;
            graphics.drawCenteredString(this.font, this.title, this.width / 2, top - 12, 0xFFFFFF);
            graphics.drawString(this.font, Component.translatable("screen.ancientempires.sound_block.search"), left, top + 1, 0xA0A0A0);
            graphics.drawString(this.font, Component.translatable("screen.ancientempires.sound_block.chosen", this.sounds.size()), left + 155, top + 1, 0xA0A0A0);
            if (this.sounds.size() > SHOWN)
            {
                graphics.drawString(this.font, Component.translatable("screen.ancientempires.sound_block.more", this.sounds.size() - SHOWN), left + 155, top + 12 + 19 * SHOWN + 2, 0xA0A0A0);
            }
            String[] labels = {"player_range", "sound_range", "min_delay", "max_delay"};
            for (int i = 0; i < labels.length; i++)
            {
                FitText.draw(graphics, this.font, Component.translatable("screen.ancientempires.sound_block." + labels[i]).getString(), left + 76 * i, top + 139, 72, 8,
                        0xFFA0A0A0, false, true);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen()
        {
            return false;
        }
    }

    /** Names a scan and saves it: the name is the template's file name (lower case, letters, digits, underscores). */
    private static final class ScannerScreen extends Screen
    {
        private final InteractionHand hand;
        private EditBox name;

        ScannerScreen(InteractionHand hand)
        {
            super(Component.translatable("screen.ancientempires.scanner"));
            this.hand = hand;
        }

        @Override
        protected void init()
        {
            int x = this.width / 2 - 100;
            int y = this.height / 2 - 20;
            this.name = new EditBox(this.font, x, y, 200, 20, Component.translatable("screen.ancientempires.scanner.name"));
            this.name.setMaxLength(64);
            this.addRenderableWidget(this.name);
            this.setInitialFocus(this.name);
            this.addRenderableWidget(Button.builder(Component.translatable("screen.ancientempires.scanner.save"), button -> this.save())
                    .bounds(x, y + 28, 98, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                    .bounds(x + 102, y + 28, 98, 20).build());
        }

        private void save()
        {
            if (!this.name.getValue().isBlank())
            {
                ModNetwork.sendToServer(new ScannerSavePacket(this.hand, this.name.getValue()));
                this.onClose();
            }
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers)
        {
            if (key == 257 || key == 335)
            {
                this.save();
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            this.renderBackground(graphics);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen()
        {
            return false;
        }
    }
}
