package com.ancientempires.entity.npc;

import java.util.Locale;

/**
 * How a leader arranges the soldiers under it, as offsets from itself: to its
 * right and behind it, in blocks (negative behind is ahead). Soldiers form the
 * front, as armies did: a leader stands just behind its own front rank, where
 * its men are between it and the enemy and it still sees the line. Leaders
 * under it (a captain's sergeants) stand ahead with their units, so a captain
 * ends up behind its squads and a general behind its captains. Pure
 * arithmetic, so it is tested without the game.
 */
public enum NpcFormation
{
    /** Wide rows: the fighting formation. */
    LINE,
    /** Narrow and deep: for marching, paths, and gates. */
    COLUMN,
    /** Wide rows with double spacing: skirmishers, archers in the open. */
    LOOSE;

    /** How far a leader's front rank stands ahead of it, in spacings. */
    private static final double FRONT_LEAD = 0.6D;

    public String id()
    {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public NpcFormation next()
    {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public static NpcFormation byName(String name)
    {
        for (NpcFormation formation : values())
        {
            if (formation.id().equals(name))
            {
                return formation;
            }
        }
        return LINE;
    }

    /** Soldiers abreast in one row. */
    public int width(int lineWidth, int columnWidth)
    {
        return this == COLUMN ? Math.max(1, columnWidth) : Math.max(1, lineWidth);
    }

    /** Blocks between neighbours. */
    public double spacing(double spacing)
    {
        return this == LOOSE ? spacing * 2.0D : spacing;
    }

    /** Rows {@code count} soldiers fill. */
    public int rows(int count, int lineWidth, int columnWidth)
    {
        int width = this.width(lineWidth, columnWidth);
        return (count + width - 1) / width;
    }

    /** Blocks the front rank stands ahead of its leader. */
    public double lead(double spacing)
    {
        return this.spacing(spacing) * FRONT_LEAD;
    }

    /** Blocks a unit of {@code count} reaches behind its leader (its rear ranks). */
    public double depthBehind(int count, int lineWidth, int columnWidth, double spacing)
    {
        int rows = this.rows(count, lineWidth, columnWidth);
        return Math.max(0.0D, (rows - 1) * this.spacing(spacing) - this.lead(spacing));
    }

    /**
     * Where soldier {@code index} of {@code count} stands: {right, back} from
     * the leader. The front rank is just ahead of it, the rest behind; rows are
     * centred, the last, short row too.
     */
    public double[] soldierSlot(int index, int count, int lineWidth, int columnWidth, double spacing)
    {
        int width = this.width(lineWidth, columnWidth);
        double gap = this.spacing(spacing);
        int row = index / width;
        int inRow = Math.min(width, count - row * width);
        int column = index % width;
        double right = (column - (inRow - 1) / 2.0D) * gap;
        double back = row * gap - this.lead(spacing);
        return new double[] {right, back};
    }

    /**
     * Where sub-leader {@code index} of {@code count} stands: ahead of this
     * leader's own front rank by a gap and the depth its unit reaches behind
     * it ({@code unitBehind}). In a line the units stand side by side (each
     * {@code frontage} wide), in a column one ahead of another (each
     * {@code unitDepth} deep, front rank to rear).
     */
    public double[] officerSlot(int index, int count, int ownRows, double frontage, double unitBehind, double unitDepth, double spacing)
    {
        double gap = this.spacing(spacing);
        double ahead = (ownRows > 0 ? this.lead(spacing) : 0.0D) + gap + unitBehind;
        if (this == COLUMN)
        {
            return new double[] {0.0D, -(ahead + index * (unitDepth + gap))};
        }
        double width = frontage + gap;
        return new double[] {(index - (count - 1) / 2.0D) * width, -ahead};
    }
}
