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
 * <p>
 * Every shape lays out the unit's melee soldiers; archers and riders stand in
 * rows of their own behind them ({@link #support}), except in a square, where
 * they stand inside it.
 */
public enum NpcFormation
{
    /** Wide rows: the fighting formation. */
    LINE(1.0D),
    /** Narrow and deep: for marching, paths, and gates. */
    COLUMN(1.0D),
    /** A tight line, shields raised while it stands. */
    SHIELD_WALL(0.75D),
    /** A point of one, each row two wider, the leader just behind the last: for charges and breaking a line. */
    WEDGE(1.0D),
    /** A hollow square facing out on every side, the leader, archers, and riders inside: holds against riders. */
    SQUARE(1.0D),
    /** Wide rows at double spacing: throwers and archers in the open. */
    SKIRMISH(2.0D);

    /** How far a leader's front rank stands ahead of it, in spacings. */
    private static final double FRONT_LEAD = 0.6D;

    private final double spacingFactor;

    NpcFormation(double spacingFactor)
    {
        this.spacingFactor = spacingFactor;
    }

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
        if ("loose".equals(name))
        {
            // Saves from before the skirmish line was named.
            return SKIRMISH;
        }
        for (NpcFormation formation : values())
        {
            if (formation.id().equals(name))
            {
                return formation;
            }
        }
        return LINE;
    }

    /** Whether its soldiers keep their shields raised while it stands. */
    public boolean shieldsUp()
    {
        return this == SHIELD_WALL;
    }

    /** Whether each soldier faces out from the leader rather than the unit's way. */
    public boolean facesOut()
    {
        return this == SQUARE;
    }

    /** The shape archers and riders take behind the melee soldiers: a wedge's and a square's are lines. */
    public NpcFormation support()
    {
        return this == WEDGE || this == SQUARE ? LINE : this;
    }

    /** Soldiers abreast in one row (a line's; a wedge and a square work out their own). */
    public int width(int lineWidth, int columnWidth)
    {
        return this == COLUMN ? Math.max(1, columnWidth) : Math.max(1, lineWidth);
    }

    /** Blocks between neighbours. */
    public double spacing(double spacing)
    {
        return spacing * this.spacingFactor;
    }

    /** Rows {@code count} soldiers fill. */
    public int rows(int count, int lineWidth, int columnWidth)
    {
        if (count <= 0)
        {
            return 0;
        }
        if (this == WEDGE)
        {
            // Rows of 1, 3, 5, ...: r rows hold r * r.
            return (int) Math.ceil(Math.sqrt(count) - 1.0E-9);
        }
        if (this == SQUARE)
        {
            return squareSide(count);
        }
        int width = this.width(lineWidth, columnWidth);
        return (count + width - 1) / width;
    }

    /** Blocks the front rank stands ahead of its leader. */
    public double lead(double spacing)
    {
        if (this == SQUARE)
        {
            return 0.0D;
        }
        return this.spacing(spacing) * FRONT_LEAD;
    }

    /** Blocks a unit of {@code count} reaches behind its leader (its rear ranks). */
    public double depthBehind(int count, int lineWidth, int columnWidth, double spacing)
    {
        double most = 0.0D;
        for (int i = 0; i < count; i++)
        {
            most = Math.max(most, this.soldierSlot(i, count, lineWidth, columnWidth, spacing)[1]);
        }
        return most;
    }

    /** The furthest back any of {@code count} soldiers stands, or one spacing ahead of the front rank for none. */
    public double rearmost(int count, int lineWidth, int columnWidth, double spacing)
    {
        double most = -this.lead(spacing) - this.spacing(spacing);
        for (int i = 0; i < count; i++)
        {
            most = Math.max(most, this.soldierSlot(i, count, lineWidth, columnWidth, spacing)[1]);
        }
        return most;
    }

    /** Blocks from the leftmost to the rightmost of {@code count} soldiers, plus one spacing: how wide the unit stands. */
    public double frontage(int count, int lineWidth, int columnWidth, double spacing)
    {
        double left = 0.0D;
        double right = 0.0D;
        for (int i = 0; i < count; i++)
        {
            double at = this.soldierSlot(i, count, lineWidth, columnWidth, spacing)[0];
            left = Math.min(left, at);
            right = Math.max(right, at);
        }
        return right - left + this.spacing(spacing);
    }

    /**
     * Where soldier {@code index} of {@code count} stands: {right, back} from
     * the leader. In a line or column the front rank is just ahead of it and
     * the rest behind, rows centred, the last, short row too.
     */
    public double[] soldierSlot(int index, int count, int lineWidth, int columnWidth, double spacing)
    {
        double gap = this.spacing(spacing);
        if (this == WEDGE)
        {
            return wedgeSlot(index, count, gap, this.lead(spacing));
        }
        if (this == SQUARE)
        {
            return squareSlot(index, count, gap);
        }
        int width = this.width(lineWidth, columnWidth);
        int row = index / width;
        int inRow = Math.min(width, count - row * width);
        int column = index % width;
        double right = (column - (inRow - 1) / 2.0D) * gap;
        double back = row * gap - this.lead(spacing);
        return new double[] {right, back};
    }

    /** Row r holds 2r + 1, the point furthest ahead; the last row is a lead ahead of the leader. */
    private static double[] wedgeSlot(int index, int count, double gap, double lead)
    {
        int rows = (int) Math.ceil(Math.sqrt(count) - 1.0E-9);
        int row = (int) Math.floor(Math.sqrt(index) + 1.0E-9);
        int first = row * row;
        int inRow = Math.min(2 * row + 1, count - first);
        int column = index - first;
        double right = (column - (inRow - 1) / 2.0D) * gap;
        double back = -(rows - 1 - row) * gap - lead;
        return new double[] {right, back};
    }

    /** Soldiers on a side of the smallest square whose rim holds {@code count} (at least 2). */
    public static int squareSide(int count)
    {
        int side = 2;
        while (4 * (side - 1) < count)
        {
            side++;
        }
        return side;
    }

    /**
     * Spread evenly round the rim of a square centred on the leader, starting
     * at the front left corner and going clockwise seen from above (front,
     * right side, rear, left side).
     */
    private static double[] squareSlot(int index, int count, double gap)
    {
        int side = squareSide(count);
        int rim = 4 * (side - 1);
        int at = (int) Math.floor((double) index * rim / count);
        int edge = at / (side - 1);
        int step = at % (side - 1);
        double half = (side - 1) / 2.0D * gap;
        return switch (edge)
        {
            case 0 -> new double[] {-half + step * gap, -half};
            case 1 -> new double[] {half, -half + step * gap};
            case 2 -> new double[] {half - step * gap, half};
            default -> new double[] {-half, half - step * gap};
        };
    }

    /**
     * Where archer or rider {@code index} of {@code count} stands inside a
     * square of {@code side}: rows as wide as the square's inside, centred on
     * the leader.
     */
    public static double[] insideSquare(int index, int count, int side, double gap)
    {
        int width = Math.max(1, side - 2);
        int rows = (count + width - 1) / width;
        int row = index / width;
        int inRow = Math.min(width, count - row * width);
        int column = index % width;
        return new double[] {(column - (inRow - 1) / 2.0D) * gap, (row - (rows - 1) / 2.0D) * gap};
    }

    /** The furthest ahead any of {@code count} soldiers stands (the most negative back). */
    public double frontmost(int count, int lineWidth, int columnWidth, double spacing)
    {
        double most = 0.0D;
        for (int i = 0; i < count; i++)
        {
            most = Math.min(most, this.soldierSlot(i, count, lineWidth, columnWidth, spacing)[1]);
        }
        return most;
    }

    /**
     * Where sub-leader {@code index} of {@code count} stands: ahead of this
     * leader's own soldiers (who reach {@code ownFront} blocks ahead of it, 0
     * for none) by a gap and the depth its unit reaches behind it
     * ({@code unitBehind}). In a column the units stand one ahead of another
     * (each {@code unitDepth} deep, front rank to rear); in any other shape
     * side by side (each {@code frontage} wide).
     */
    public double[] officerSlot(int index, int count, double ownFront, double frontage, double unitBehind, double unitDepth, double spacing)
    {
        double gap = this.spacing(spacing);
        double ahead = Math.max(0.0D, ownFront) + gap + unitBehind;
        if (this == COLUMN)
        {
            return new double[] {0.0D, -(ahead + index * (unitDepth + gap))};
        }
        double width = frontage + gap;
        return new double[] {(index - (count - 1) / 2.0D) * width, -ahead};
    }

    /** The way forward for a mob's yaw, {x, z}: yaw 0 looks south (+z). */
    public static double[] forward(float yaw)
    {
        double radians = Math.toRadians(yaw);
        return new double[] {-Math.sin(radians), Math.cos(radians)};
    }

    /** A mob's yaw looking from one point toward another. */
    public static float yawToward(double fromX, double fromZ, double toX, double toZ)
    {
        return (float) (Math.toDegrees(Math.atan2(toZ - fromZ, toX - fromX)) - 90.0D);
    }
}
