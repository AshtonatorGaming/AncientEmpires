package com.ancientempires.block.decor;

import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.Map;

/**
 * What some of AW's decor pieces hold: a gravestone's loot (in the persistent
 * {@code AeLoot}, handled by AwLoot like a loot chest's), and the mob a stake
 * or statue shows ({@code mob}, an entity id; burning on a stake). The mob is
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
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.mob = tag.getString("Mob");
        this.burning = tag.getBoolean("Burning");
        this.yaw = tag.getFloat("Yaw");
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
