package com.ancientempires.item;

import com.ancientempires.AncientEmpires;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Ancient Warfare's bard instruments: lute, flute, harp, drum. A player plays a
 * note on one (AW's use); a Worker-path NPC holding one in its main hand is a
 * bard (NpcBardGoal), playing AW's songs for the instrument.
 */
public class InstrumentItem extends Item
{
    /** One of AW's bard songs: its sound ({@code sounds.json}) and how long it plays. */
    public record Song(String sound, int ticks)
    {
        public SoundEvent event()
        {
            return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, this.sound));
        }
    }

    private static final Song LUTE_SONG = new Song("bard.lute", 28 * 20);
    private static final Song MASTER = new Song("bard.master", 33 * 20);
    private static final Song LORD = new Song("bard.lord", 55 * 20);
    private static final Song TELLER_1 = new Song("bard.teller_1", 20 * 20);
    private static final Song TELLER_2 = new Song("bard.teller_2", 30 * 20);
    private static final Song DRUMS = new Song("bard.drums", 75 * 20);

    /** The four instruments, each with its note and its songs. */
    public enum Kind
    {
        LUTE(SoundEvents.NOTE_BLOCK_GUITAR.value(), List.of(LUTE_SONG, MASTER)),
        FLUTE(SoundEvents.NOTE_BLOCK_FLUTE.value(), List.of(TELLER_1, TELLER_2)),
        HARP(SoundEvents.NOTE_BLOCK_HARP.value(), List.of(LORD, MASTER)),
        DRUM(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), List.of(DRUMS));

        private final SoundEvent note;
        private final List<Song> songs;

        Kind(SoundEvent note, List<Song> songs)
        {
            this.note = note;
            this.songs = songs;
        }

        public Song song(RandomSource random)
        {
            return this.songs.get(random.nextInt(this.songs.size()));
        }
    }

    private final Kind kind;

    public InstrumentItem(Kind kind, Properties properties)
    {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind()
    {
        return this.kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        if (!level.isClientSide)
        {
            // A note anywhere on the instrument's two octaves.
            float pitch = (float) Math.pow(2.0D, (level.getRandom().nextInt(25) - 12) / 12.0D);
            level.playSound(null, player.getX(), player.getY() + 0.5D, player.getZ(), this.kind.note, SoundSource.PLAYERS, 2.0F, pitch);
        }
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
