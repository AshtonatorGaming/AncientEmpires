package com.ancientempires.block;

import net.minecraft.network.FriendlyByteBuf;

/**
 * A worksite's area as a rectangle of columns, in blocks relative to the
 * worksite block (x east, z south), corners inclusive. Shared by the server,
 * which keeps it, and the bounds screen, which checks a change before sending.
 */
public record WorksiteArea(int minX, int minZ, int maxX, int maxZ)
{
    /** A {@code width} by {@code length} area centered on the block (the extra column of an even size goes east or south). */
    public static WorksiteArea centered(int width, int length)
    {
        int west = (width - 1) / 2;
        int north = (length - 1) / 2;
        return new WorksiteArea(-west, -north, width - 1 - west, length - 1 - north);
    }

    /** East to west, in blocks. */
    public int width()
    {
        return this.maxX - this.minX + 1;
    }

    /** North to south, in blocks. */
    public int length()
    {
        return this.maxZ - this.minZ + 1;
    }

    public WorksiteArea moved(int dx, int dz)
    {
        return new WorksiteArea(this.minX + dx, this.minZ + dz, this.maxX + dx, this.maxZ + dz);
    }

    /** Grows or shrinks the east edge ({@code dx}) and the south edge ({@code dz}). */
    public WorksiteArea resized(int dx, int dz)
    {
        return new WorksiteArea(this.minX, this.minZ, this.maxX + dx, this.maxZ + dz);
    }

    /** Whether the block's own column is inside. */
    public boolean containsBlock()
    {
        return this.minX <= 0 && this.maxX >= 0 && this.minZ <= 0 && this.maxZ >= 0;
    }

    /** Blocks from the worksite block to the nearest column of the area, along the farther axis. */
    public int gap()
    {
        int gapX = Math.max(0, Math.max(this.minX, -this.maxX));
        int gapZ = Math.max(0, Math.max(this.minZ, -this.maxZ));
        return Math.max(gapX, gapZ);
    }

    /**
     * Within {@code maxSize} each way, at most {@code reach} from the block,
     * and clear of the block's own column where the site digs under itself.
     */
    public boolean fits(int maxSize, int reach, boolean mayContainBlock)
    {
        return this.width() >= 1 && this.length() >= 1 && this.width() <= maxSize && this.length() <= maxSize
                && this.gap() <= reach + 1 && (mayContainBlock || !this.containsBlock());
    }

    public void write(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.minX);
        buffer.writeVarInt(this.minZ);
        buffer.writeVarInt(this.maxX);
        buffer.writeVarInt(this.maxZ);
    }

    public static WorksiteArea read(FriendlyByteBuf buffer)
    {
        return new WorksiteArea(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }
}
