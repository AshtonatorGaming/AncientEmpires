package com.ancientempires.entity.npc;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.item.InstrumentItem;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

/**
 * Ancient Warfare's bard (NpcBard, NpcAISing): an NPC on the Worker path (or a
 * faction's townsfolk) holding an instrument plays one of AW's songs for it
 * when a player is within {@code bard.listenRange}, strumming as it goes, then
 * rests {@code bard.pauseMin} to {@code pauseMax} ticks. Beyond AW, a song
 * cheers its allies within {@code bard.range} ({@link #cheer}): soldiers' morale
 * rises by {@code bard.moraleBonus}, workers work {@code bard.workBonus} faster.
 * A fight, a flight, following, or sleep breaks the song off.
 */
public final class NpcBardGoal extends Goal
{
    private static final int CHECK_INTERVAL = 40;
    private static final int STRUM_TICKS = 12;

    private final NpcEntity npc;
    private int nextCheck;
    private int playing;
    private InstrumentItem.Song song;

    NpcBardGoal(NpcEntity npc)
    {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean free()
    {
        return this.npc.getMainHandItem().getItem() instanceof InstrumentItem && this.npc.getTarget() == null && !this.npc.isFollowing()
                && !this.npc.isSleeping();
    }

    @Override
    public boolean canUse()
    {
        if (this.npc.tickCount < this.nextCheck)
        {
            return false;
        }
        this.nextCheck = this.npc.tickCount + CHECK_INTERVAL + (this.npc.getId() & 15);
        return this.free() && this.npc.level().getNearestPlayer(this.npc, AncientEmpiresConfig.bardListenRange) != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.playing > 0 && this.free();
    }

    @Override
    public void start()
    {
        InstrumentItem instrument = (InstrumentItem) this.npc.getMainHandItem().getItem();
        this.song = instrument.kind().song(this.npc.getRandom());
        this.playing = this.song.ticks();
        this.npc.playingSong = this.song.event().getLocation();
        this.npc.getNavigation().stop();
        this.npc.level().playSound(null, this.npc.getX(), this.npc.getY() + 1.0D, this.npc.getZ(), this.song.event(), SoundSource.RECORDS, 2.0F, 1.0F);
    }

    @Override
    public void stop()
    {
        if (this.playing > 0 && this.npc.level() instanceof ServerLevel level)
        {
            // Broken off: the song stops for whoever heard it. The stop goes by the sound's id, so a listener who
            // also hears another bard playing the same piece is left alone (it would silence that one too).
            ResourceLocation id = this.song.event().getLocation();
            ClientboundStopSoundPacket silence = new ClientboundStopSoundPacket(id, SoundSource.RECORDS);
            for (ServerPlayer player : level.players())
            {
                if (player.distanceToSqr(this.npc) < 64.0D * 64.0D && level.getEntitiesOfClass(NpcEntity.class, player.getBoundingBox().inflate(64.0D),
                        other -> other != this.npc && id.equals(other.playingSong)).isEmpty())
                {
                    player.connection.send(silence);
                }
            }
        }
        this.npc.playingSong = null;
        this.playing = 0;
        int min = AncientEmpiresConfig.bardPauseMin;
        int max = Math.max(min, AncientEmpiresConfig.bardPauseMax);
        this.nextCheck = this.npc.tickCount + min + this.npc.getRandom().nextInt(max - min + 1);
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        this.playing--;
        if (this.playing % STRUM_TICKS == 0)
        {
            this.npc.swing(InteractionHand.MAIN_HAND);
        }
        Player listener = this.npc.level().getNearestPlayer(this.npc, AncientEmpiresConfig.bardListenRange);
        if (listener != null)
        {
            this.npc.getLookControl().setLookAt(listener);
        }
        int pulse = AncientEmpiresConfig.bardPulseTicks;
        if (this.playing % pulse == 0)
        {
            cheer(this.npc, pulse + 20);
        }
    }

    /** The song reaches the bard's allies (same owner, or same faction) within {@code bard.range}: cheered for {@code ticks}. */
    public static int cheer(NpcEntity bard, int ticks)
    {
        double range = AncientEmpiresConfig.bardRange;
        int cheered = 0;
        for (NpcEntity other : bard.level().getEntitiesOfClass(NpcEntity.class, new AABB(bard.blockPosition()).inflate(range),
                other -> other != bard && bard.getRelations().isAlly(other)))
        {
            other.hearSong(ticks);
            cheered++;
        }
        return cheered;
    }
}
