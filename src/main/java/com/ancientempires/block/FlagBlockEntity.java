package com.ancientempires.block;

import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A flag's faction, whether it is a structure's protection flag (and which
 * {@code StructureRecords} entry it guards), and who claimed it. Synced to
 * clients, which draw the faction's banner, or once claimed the claimer's two
 * colours (AW drew a claimed flag in its player's colours).
 */
public class FlagBlockEntity extends BlockEntity
{
    private String faction = "";
    private boolean protection;
    private int record;
    @Nullable
    private UUID claimedBy;
    private String claimedName = "";

    public FlagBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.FLAG.get(), pos, state);
    }

    public String faction()
    {
        return this.faction;
    }

    public boolean isProtection()
    {
        return this.protection;
    }

    public int record()
    {
        return this.record;
    }

    @Nullable
    public UUID claimedBy()
    {
        return this.claimedBy;
    }

    public String claimedName()
    {
        return this.claimedName;
    }

    public void setFaction(String faction)
    {
        this.faction = faction == null ? "" : faction;
        this.changed();
    }

    /** A protection flag guarding {@code record} (0: not yet tied to a structure). */
    public void setProtection(boolean protection, int record)
    {
        this.protection = protection;
        this.record = record;
        this.changed();
    }

    public void claim(UUID player, String name)
    {
        this.claimedBy = player;
        this.claimedName = name;
        this.changed();
    }

    /** The claimer's colour for the top or bottom half: from their UUID, so it stays the same. */
    public int claimColor(boolean top)
    {
        if (this.claimedBy == null)
        {
            return 0xFFFFFF;
        }
        long bits = top ? this.claimedBy.getMostSignificantBits() : this.claimedBy.getLeastSignificantBits();
        float hue = (bits >>> 40 & 0xFFFF) / 65535.0F;
        return Mth.hsvToRgb(hue, 0.65F, top ? 0.85F : 0.7F) & 0xFFFFFF;
    }

    private void changed()
    {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide)
        {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putString("Faction", this.faction);
        tag.putBoolean("Protection", this.protection);
        tag.putInt("Record", this.record);
        if (this.claimedBy != null)
        {
            tag.putUUID("ClaimedBy", this.claimedBy);
            tag.putString("ClaimedName", this.claimedName);
        }
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.faction = tag.getString("Faction");
        this.protection = tag.getBoolean("Protection");
        this.record = tag.getInt("Record");
        this.claimedBy = tag.hasUUID("ClaimedBy") ? tag.getUUID("ClaimedBy") : null;
        this.claimedName = tag.getString("ClaimedName");
    }

    @Override
    public CompoundTag getUpdateTag()
    {
        return this.saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
