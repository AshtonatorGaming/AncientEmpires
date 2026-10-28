package com.ancientempires.block.decor;

import com.ancientempires.AncientEmpires;
import com.ancientempires.registry.ModBlockEntities;
import com.ancientempires.structure.LegacyFixer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Sound Block plays, from AW's {@code tuneData}: its sounds (AW's own,
 * shipped under our name, or vanilla's), how near a player must be
 * ({@code playerRange}) and how far it carries ({@code soundRange}), the pause
 * between plays (min to max seconds), in order or at random, and whether it
 * keeps playing while someone is near or only greets them as they come.
 * Checked once a second.
 */
public class AwSoundBlockEntity extends BlockEntity
{
    private BlockState disguise = Blocks.STONE_BRICKS.defaultBlockState();
    private final List<String> sounds = new ArrayList<>();
    private int playerRange = 16;
    private int soundRange = 16;
    private int minDelay;
    private int maxDelay;
    private boolean random;
    private boolean whenInRange = true;
    private int next;
    private int cooldown;
    private boolean someoneNear;

    public AwSoundBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.SOUND_BLOCK.get(), pos, state);
    }

    public BlockState disguise()
    {
        return this.disguise;
    }

    /** Takes AW's saved sound block: its disguise ({@code block}, {@code meta}, 1.12 names) and its tune. */
    public void importAw(CompoundTag teData)
    {
        String item = LegacyFixer.itemId(teData.getString("block"), teData.getInt("meta"));
        if (BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(item)) instanceof BlockItem block)
        {
            this.disguise = block.getBlock().defaultBlockState();
        }
        CompoundTag tune = teData.getCompound("tuneData");
        this.sounds.clear();
        for (Tag entry : tune.getList("entries", Tag.TAG_COMPOUND))
        {
            String name = ((CompoundTag) entry).getString("name");
            this.sounds.add(name.startsWith("ancientwarfarestructure:") ? AncientEmpires.MOD_ID + name.substring(name.indexOf(':')) : name);
        }
        this.playerRange = Math.max(1, tune.getInt("playerRange"));
        this.soundRange = Math.max(1, tune.getInt("soundRange"));
        this.minDelay = tune.getInt("minDelay");
        this.maxDelay = Math.max(this.minDelay, tune.getInt("maxDelay"));
        this.random = tune.getBoolean("random");
        this.whenInRange = tune.getBoolean("whenInRange");
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AwSoundBlockEntity sound)
    {
        if (level.getGameTime() % 20L != Math.floorMod(pos.asLong(), 20L) || sound.sounds.isEmpty())
        {
            return;
        }
        boolean near = level.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, sound.playerRange, false) != null;
        boolean arrived = near && !sound.someoneNear;
        sound.someoneNear = near;
        if (sound.cooldown > 0)
        {
            sound.cooldown--;
            return;
        }
        if (!near || !sound.whenInRange && !arrived)
        {
            return;
        }
        int index = sound.random ? level.getRandom().nextInt(sound.sounds.size()) : sound.next++ % sound.sounds.size();
        ResourceLocation id = ResourceLocation.tryParse(sound.sounds.get(index));
        if (id != null)
        {
            level.playSound(null, pos, SoundEvent.createVariableRangeEvent(id), SoundSource.AMBIENT, sound.soundRange / 16.0F, 1.0F);
        }
        // AW waited the sound's length (about 5 s) plus the pause.
        sound.cooldown = 5 + sound.minDelay + (sound.maxDelay > sound.minDelay ? level.getRandom().nextInt(sound.maxDelay - sound.minDelay + 1) : 0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.put("Disguise", NbtUtils.writeBlockState(this.disguise));
        ListTag list = new ListTag();
        this.sounds.forEach(sound -> list.add(StringTag.valueOf(sound)));
        tag.put("Sounds", list);
        tag.putInt("PlayerRange", this.playerRange);
        tag.putInt("SoundRange", this.soundRange);
        tag.putInt("MinDelay", this.minDelay);
        tag.putInt("MaxDelay", this.maxDelay);
        tag.putBoolean("Random", this.random);
        tag.putBoolean("WhenInRange", this.whenInRange);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        if (tag.contains("Disguise"))
        {
            this.disguise = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Disguise"));
        }
        this.sounds.clear();
        tag.getList("Sounds", Tag.TAG_STRING).forEach(sound -> this.sounds.add(sound.getAsString()));
        this.playerRange = Math.max(1, tag.getInt("PlayerRange"));
        this.soundRange = Math.max(1, tag.getInt("SoundRange"));
        this.minDelay = tag.getInt("MinDelay");
        this.maxDelay = tag.getInt("MaxDelay");
        this.random = tag.getBoolean("Random");
        this.whenInRange = tag.getBoolean("WhenInRange");
    }

    @Override
    public CompoundTag getUpdateTag()
    {
        CompoundTag tag = super.getUpdateTag();
        tag.put("Disguise", NbtUtils.writeBlockState(this.disguise));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
