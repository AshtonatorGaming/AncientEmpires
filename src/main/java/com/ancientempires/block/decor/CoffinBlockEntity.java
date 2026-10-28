package com.ancientempires.block.decor;

import com.ancientempires.AncientEmpires;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A coffin's lid and lie: one of AW's eight ways (its {@code CoffinDirection},
 * 45 degrees apart), upright or lying, and whether it is open. The lid swings
 * open over AW's time once opened ({@link #lidProgress}); a coffin loaded open
 * stays open.
 */
public class CoffinBlockEntity extends BlockEntity
{
    public static final String[] DIRECTIONS = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
    /** Ticks the lid takes to open (AW's {@code getTotalOpenTime}). */
    public static final int OPEN_TICKS = 20;

    private int direction;
    private boolean upright;
    private boolean open;
    private long openedAt = -1L;

    public CoffinBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.COFFIN.get(), pos, state);
    }

    /** AW's direction for a player's look, as its coffins took on placing. */
    public static int directionFacing(float yaw)
    {
        return switch (Direction.fromYRot(yaw))
        {
            case EAST -> 2;
            case SOUTH -> 4;
            case WEST -> 6;
            default -> 0;
        };
    }

    /** The coffin's turn about the up axis, in degrees (AW: north 0, east 90). */
    public float angle()
    {
        return this.direction * 45.0F;
    }

    public boolean upright()
    {
        return this.upright;
    }

    public boolean isOpen()
    {
        return this.open;
    }

    /** 0 shut, 1 fully open, partway while the lid swings. */
    public float lidProgress(float partialTick)
    {
        if (!this.open)
        {
            return 0.0F;
        }
        if (this.openedAt < 0L || this.level == null)
        {
            return 1.0F;
        }
        return Mth.clamp((this.level.getGameTime() - this.openedAt + partialTick) / OPEN_TICKS, 0.0F, 1.0F);
    }

    public void setDirection(int direction)
    {
        this.direction = Math.floorMod(direction, 8);
        this.changed();
    }

    /** Takes AW's coffin data ({@code direction}, {@code upright}, {@code open}), turned with the structure. */
    public void importAw(CompoundTag teData, Rotation rotation)
    {
        String name = teData.getString("direction");
        int index = 0;
        for (int i = 0; i < DIRECTIONS.length; i++)
        {
            if (DIRECTIONS[i].equals(name))
            {
                index = i;
            }
        }
        this.direction = Math.floorMod(index + 2 * rotation.ordinal(), 8);
        this.upright = teData.getBoolean("upright");
        this.open = teData.getBoolean("open");
        this.changed();
    }

    public void open()
    {
        if (this.open || this.level == null)
        {
            return;
        }
        this.open = true;
        this.openedAt = this.level.getGameTime();
        String sound = switch (this.getBlockState().getValue(CoffinBlock.VARIANT))
        {
            case "stone" -> "stone_coffin";
            case "sandstone" -> "sandstone_sarcophagus";
            case "prismarine" -> "prismarine_coffin";
            case "demonic" -> "demonic_coffin";
            default -> "coffin_opens";
        };
        this.level.playSound(null, this.worldPosition, SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, sound)),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        this.changed();
    }

    private void changed()
    {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide)
        {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** A coffin is drawn up to three blocks from its first one. */
    @Override
    public AABB getRenderBoundingBox()
    {
        return new AABB(this.worldPosition).inflate(3.0D);
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putInt("Direction", this.direction);
        tag.putBoolean("Upright", this.upright);
        tag.putBoolean("Open", this.open);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.direction = Math.floorMod(tag.getInt("Direction"), 8);
        this.upright = tag.getBoolean("Upright");
        boolean wasOpen = this.open;
        this.open = tag.getBoolean("Open");
        if (tag.contains("OpenedAt"))
        {
            this.openedAt = this.open && !wasOpen ? tag.getLong("OpenedAt") : this.openedAt;
        }
    }

    @Override
    public CompoundTag getUpdateTag()
    {
        CompoundTag tag = this.saveWithoutMetadata();
        tag.putLong("OpenedAt", this.openedAt);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
