package com.ancientempires.entity.npc;

import java.util.Locale;

/**
 * How a leader arranges the soldiers under it, as offsets from itself: to its
 * right and behind it, in blocks. The leader stands at the front; its own
 * soldiers fill rows behind it, and any leaders under it (a captain's
 * sergeants) stand behind those with their squads behind them in turn. Pure
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

    /**
     * Where soldier {@code index} of {@code count} stands: {right, back} from
     * the leader. Rows are centred; the last, short row is centred too.
     */
    public double[] soldierSlot(int index, int count, int lineWidth, int columnWidth, double spacing)
    {
        int width = this.width(lineWidth, columnWidth);
        double gap = this.spacing(spacing);
        int row = index / width;
        int inRow = Math.min(width, count - row * width);
        int column = index % width;
        double right = (column - (inRow - 1) / 2.0D) * gap;
        double back = gap + row * gap;
        return new double[] {right, back};
    }

    /**
     * Where sub-leader {@code index} of {@code count} stands, behind the
     * leader's own {@code ownRows} rows: side by side in a line (each squad
     * {@code frontage} wide), one behind another in a column ({@code depth} deep).
     */
    public double[] officerSlot(int index, int count, int ownRows, double frontage, double depth, double spacing)
    {
        double gap = this.spacing(spacing);
        double base = gap + ownRows * gap + gap;
        if (this == COLUMN)
        {
            return new double[] {0.0D, base + index * (depth + gap)};
        }
        double width = frontage + gap;
        return new double[] {(index - (count - 1) / 2.0D) * width, base};
    }
}
