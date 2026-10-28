package com.ancientempires.block.decor;

import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.Map;

/**
 * What some of AW's decor pieces hold: a gravestone's loot (in the persistent
 * {@code AeLoot}, handled by AwLoot like a loot chest's), an altar candle's or
 * cloth's colour (AW's {@code TileColored}: RGB, -1 white; candles also a flame
 * colour), and the mob a stake or statue shows ({@code mob}, an entity id; burning on a stake). The mob is
 * drawn frozen by its own renderer; AW's statue poses (per-part turns) are not
 * kept, so a statue stands as the plain mob.
 */
public class AwDecorBlockEntity extends BlockEntity
{
    /** AW's statue names to entity ids. */
    private static final Map<String, String> STATUES = Map.of("zombie", "minecraft:zombie", "enderman", "minecraft:enderman",
            "skeleton", "minecraft:skeleton", "vex", "minecraft:vex", "zombie villager", "minecraft:zombie_villager",
            "elder guardian", "minecraft:elder_guardian", "guardian", "minecraft:guardian");

    private String mob = "";
    private boolean burning;
    private float yaw;
    private int color = -1;
    private int flameColor = -1;

    public AwDecorBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.DECOR.get(), pos, state);
    }

    public String mob()
    {
        return this.mob;
    }

    public boolean burning()
    {
        return this.burning;
    }

    public float yaw()
    {
        return this.yaw;
    }

    public int color()
    {
        return this.color;
    }

    public int flameColor()
    {
        return this.flameColor;
    }

    public void setColor(int color, int flameColor)
    {
        this.color = color;
        this.flameColor = flameColor;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide)
        {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * AW's colour from its tile data: a custom RGB colour, or a dye (AW counted
     * dyes backwards, 15 being white), or none.
     */
    public static int colorFrom(CompoundTag teData)
    {
        if (teData.getBoolean("customColor"))
        {
            return teData.contains("color") ? teData.getInt("color") : -1;
        }
        int dye = teData.contains("dyeColor") ? teData.getInt("dyeColor") : -1;
        if (dye < 0 || dye > 15)
        {
            return -1;
        }
        float[] rgb = DyeColor.byId(15 - dye).getTextureDiffuseColors();
        return (int) (rgb[0] * 255.0F) << 16 | (int) (rgb[1] * 255.0F) << 8 | (int) (rgb[2] * 255.0F);
    }

    public void setMob(String mob, boolean burning, float yaw)
    {
        this.mob = mob;
        this.burning = burning;
        this.yaw = yaw;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide)
        {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** A stake's mob ({@code entityName}) or a statue's ({@code entityStatueInfo.statueEntityName}) from AW's data. */
    public static String mobFrom(CompoundTag teData)
    {
        if (!teData.getString("entityName").isEmpty())
        {
            return teData.getString("entityName");
        }
        String statue = teData.getCompound("entityStatueInfo").getString("statueEntityName").toLowerCase(Locale.ROOT);
        return STATUES.getOrDefault(statue, statue.isEmpty() ? "" : "minecraft:zombie");
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putString("Mob", this.mob);
        tag.putBoolean("Burning", this.burning);
        tag.putFloat("Yaw", this.yaw);
        tag.putInt("Color", this.color);
        tag.putInt("FlameColor", this.flameColor);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.mob = tag.getString("Mob");
        this.burning = tag.getBoolean("Burning");
        this.yaw = tag.getFloat("Yaw");
        this.color = tag.contains("Color") ? tag.getInt("Color") : -1;
        this.flameColor = tag.contains("FlameColor") ? tag.getInt("FlameColor") : -1;
    }

    /** A new colour from the server redraws the block (its tint is baked into the chunk). */
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet)
    {
        super.onDataPacket(connection, packet);
        if (this.level != null)
        {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL_IMMEDIATE);
        }
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
