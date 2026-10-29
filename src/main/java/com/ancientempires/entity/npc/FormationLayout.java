package com.ancientempires.entity.npc;

/**
 * A unit's slots as offsets from its leader, {right, back} in blocks, by kind:
 * melee on foot in the front ranks, archers in the rows behind them, riders
 * behind those at their own spacing (inside a square: archers and riders in
 * the middle; a horse-only unit's riders are its front ranks), and the units
 * of officers under it standing ahead. The server lays its NPCs out with it
 * ({@link NpcChain}) and the client draws the placement ghosts with it, so a
 * preview is the result. Pure arithmetic, tested without the game.
 *
 * @param melee    one slot per melee soldier, in order
 * @param archers  one per archer
 * @param riders   one per rider (the front ranks' kind when the unit is horse alone)
 * @param officers one per officer leading a unit of its own
 * @param mounted  whether the riders were laid out as the front ranks (then {@code melee} holds them)
 */
public record FormationLayout(double[][] melee, double[][] archers, double[][] riders, double[][] officers, boolean mounted)
{
    /**
     * The numbers a layout is worked out with: soldiers abreast in a line and a
     * column, blocks between neighbours (the unit's spread already in), riders'
     * spacing as a multiple of that, and the size an officer's unit is assumed to be.
     */
    public record Settings(int lineWidth, int columnWidth, double spacing, double riderFactor, int squadSize)
    {
    }

    /** Lays out a unit of {@code melee}, {@code archers}, and {@code riders} soldiers and {@code officers} units in {@code shape}. */
    public static FormationLayout of(NpcFormation shape, int melee, int archers, int riders, int officers, Settings settings)
    {
        int lineWidth = settings.lineWidth();
        int columnWidth = settings.columnWidth();
        double spacing = settings.spacing();
        NpcFormation support = shape.support();
        double riderSpacing = spacing * settings.riderFactor();
        int riderWidth = Math.max(1, lineWidth / 2);
        boolean mounted = melee == 0 && archers == 0 && riders > 0;
        if (mounted)
        {
            // Horse alone: the riders are the front ranks, at their own spacing.
            melee = riders;
            riders = 0;
        }
        double meleeSpacing = mounted ? riderSpacing : spacing;
        int meleeWidth = mounted ? riderWidth : lineWidth;
        double front = 0.0D;
        double[][] meleeSlots = new double[melee][];
        for (int i = 0; i < melee; i++)
        {
            meleeSlots[i] = shape.soldierSlot(i, melee, meleeWidth, columnWidth, meleeSpacing);
            front = Math.max(front, -meleeSlots[i][1]);
        }
        double[][] archerSlots = new double[archers][];
        double[][] riderSlots = new double[riders][];
        if (shape == NpcFormation.SQUARE)
        {
            // Archers and riders inside the square.
            int side = NpcFormation.squareSide(Math.max(1, melee));
            for (int i = 0; i < archers + riders; i++)
            {
                double[] slot = NpcFormation.insideSquare(i, archers + riders, side, shape.spacing(spacing));
                if (i < archers)
                {
                    archerSlots[i] = slot;
                }
                else
                {
                    riderSlots[i - archers] = slot;
                }
            }
        }
        else
        {
            // Archers start a row of their own behind the last melee row, however short that one is.
            double rowGap = support.spacing(spacing);
            double rear = melee == 0 ? -support.lead(spacing) - rowGap : shape.rearmost(melee, meleeWidth, columnWidth, meleeSpacing);
            double archerShift = rear + rowGap + support.lead(spacing);
            for (int i = 0; i < archers; i++)
            {
                double[] slot = support.soldierSlot(i, archers, lineWidth, columnWidth, spacing);
                slot[1] += archerShift;
                front = Math.max(front, -slot[1]);
                archerSlots[i] = slot;
            }
            if (archers > 0)
            {
                rear = support.rearmost(archers, lineWidth, columnWidth, spacing) + archerShift;
            }
            NpcFormation riderShape = shape == NpcFormation.WEDGE ? NpcFormation.WEDGE : support;
            double riderBack = Math.max(0.0D, rear + rowGap) + riderShape.spacing(riderSpacing)
                    - riderShape.frontmost(riders, riderWidth, columnWidth, riderSpacing);
            for (int i = 0; i < riders; i++)
            {
                double[] slot = riderShape.soldierSlot(i, riders, riderWidth, columnWidth, riderSpacing);
                slot[1] += riderBack;
                riderSlots[i] = slot;
            }
        }
        // Officers' units, assumed full: as wide and deep as a whole squad in this formation, standing ahead.
        int squad = settings.squadSize();
        double frontage = shape.frontage(squad, lineWidth, columnWidth, spacing);
        double behind = shape.depthBehind(squad, lineWidth, columnWidth, spacing);
        double depth = -shape.frontmost(squad, lineWidth, columnWidth, spacing) + behind;
        double[][] officerSlots = new double[officers][];
        for (int i = 0; i < officers; i++)
        {
            officerSlots[i] = shape.officerSlot(i, officers, front, frontage, behind, depth, spacing);
        }
        return new FormationLayout(meleeSlots, archerSlots, riderSlots, officerSlots, mounted);
    }

    /** How far ahead of its leader the unit's front stands (an officer's unit counted from its leader's front rank). */
    public double front(NpcFormation shape, double spacing)
    {
        double most = 0.0D;
        for (double[][] group : new double[][][] {this.melee, this.archers, this.riders})
        {
            for (double[] slot : group)
            {
                most = Math.max(most, -slot[1]);
            }
        }
        for (double[] slot : this.officers)
        {
            most = Math.max(most, -slot[1] + shape.lead(spacing));
        }
        return most;
    }

    /** Blocks from the unit's left edge to its right (officers' units taken as {@code officerFrontage} wide). */
    public double span(double spacing, double officerFrontage)
    {
        double left = 0.0D;
        double right = 0.0D;
        for (double[][] group : new double[][][] {this.melee, this.archers, this.riders})
        {
            for (double[] slot : group)
            {
                left = Math.min(left, slot[0]);
                right = Math.max(right, slot[0]);
            }
        }
        double half = spacing / 2.0D;
        for (double[] slot : this.officers)
        {
            left = Math.min(left, slot[0] - officerFrontage / 2.0D + half);
            right = Math.max(right, slot[0] + officerFrontage / 2.0D - half);
        }
        return right - left + spacing;
    }

    /** One unit about to be placed: its shape, who is in it by kind, and its own layout numbers. */
    public record Unit(NpcFormation shape, int melee, int archers, int riders, int officers, Settings settings)
    {
        FormationLayout layout(Settings with)
        {
            return FormationLayout.of(this.shape, this.melee, this.archers, this.riders, this.officers, with);
        }

        /** Soldiers in its front ranks: melee, else archers, else riders. */
        int fronts()
        {
            return this.melee > 0 ? this.melee : this.archers > 0 ? this.archers : this.riders;
        }

        /** Blocks between neighbours in its front ranks. */
        double frontSpacing()
        {
            double spacing = this.shape.spacing(this.settings.spacing());
            return this.melee == 0 && this.archers == 0 ? spacing * this.settings.riderFactor() : spacing;
        }
    }

    /**
     * Where a unit placed by {@link #place} stands: its leader's spot, and
     * soldiers abreast in its line (0: as it was).
     */
    public record Placed(double x, double z, int width)
    {
    }

    /**
     * Units given one spot or one front line, side by side across it in their
     * order, all facing {@code facing}. With {@code length} 0 the spot is a
     * click: each unit keeps its width and they stand abreast across it. A
     * longer {@code length} is a dragged front line through the spot: it is
     * shared out among them by their front ranks, each standing as many
     * abreast as its share holds. Either way each unit's front stands on the
     * line and its leader behind it, as far as its front reaches ahead of it.
     */
    public static Placed[] place(Unit[] units, double x, double z, float facing, double length, double gap)
    {
        double[] forward = NpcFormation.forward(facing);
        double rightX = -forward[1];
        double rightZ = forward[0];
        int count = units.length;
        int[] widths = new int[count];
        double[] middles;
        if (length > 0.0D)
        {
            int[] fronts = new int[count];
            double[] spacings = new double[count];
            for (int i = 0; i < count; i++)
            {
                fronts[i] = units[i].fronts();
                spacings[i] = units[i].frontSpacing();
            }
            double[] shared = shareLine(length, fronts, spacings, gap);
            middles = new double[count];
            for (int i = 0; i < count; i++)
            {
                widths[i] = (int) shared[i];
                middles[i] = shared[count + i];
            }
        }
        else
        {
            double[] spans = new double[count];
            for (int i = 0; i < count; i++)
            {
                Settings settings = units[i].settings();
                NpcFormation shape = units[i].shape();
                double officerFrontage = shape.frontage(settings.squadSize(), settings.lineWidth(), settings.columnWidth(), settings.spacing());
                spans[i] = units[i].layout(settings).span(shape.spacing(settings.spacing()), officerFrontage);
            }
            middles = sideBySide(spans, gap);
        }
        Placed[] placed = new Placed[count];
        for (int i = 0; i < count; i++)
        {
            Settings settings = units[i].settings();
            Settings with = widths[i] > 0 ? new Settings(widths[i], settings.columnWidth(), settings.spacing(), settings.riderFactor(), settings.squadSize()) : settings;
            double front = units[i].layout(with).front(units[i].shape(), with.spacing());
            placed[i] = new Placed(x + rightX * middles[i] - forward[0] * front, z + rightZ * middles[i] - forward[1] * front, widths[i]);
        }
        return placed;
    }

    /**
     * Units standing side by side across a spot, each {@code widths} wide with
     * {@code gap} between: the offset of each one's middle to the right of
     * the spot.
     */
    public static double[] sideBySide(double[] widths, double gap)
    {
        double total = 0.0D;
        for (int i = 0; i < widths.length; i++)
        {
            total += widths[i] + (i > 0 ? gap : 0.0D);
        }
        double[] middles = new double[widths.length];
        double offset = -total / 2.0D;
        for (int i = 0; i < widths.length; i++)
        {
            middles[i] = offset + widths[i] / 2.0D;
            offset += widths[i] + gap;
        }
        return middles;
    }

    /**
     * A front line {@code length} blocks long shared out among units with
     * {@code fronts} soldiers each (their melee, or whoever stands in front),
     * {@code spacings} apart, {@code gap} between units: how many soldiers
     * abreast each one stands (at least 2, at most its own number), and the
     * offset of its middle along the line from the line's middle.
     *
     * @return {abreast..., middle...}: the first half soldiers abreast, the second the middles
     */
    public static double[] shareLine(double length, int[] fronts, double[] spacings, double gap)
    {
        int count = fronts.length;
        double[] result = new double[count * 2];
        int total = 0;
        for (int front : fronts)
        {
            total += Math.max(1, front);
        }
        double usable = Math.max(0.0D, length - gap * (count - 1));
        double[] widths = new double[count];
        for (int i = 0; i < count; i++)
        {
            double share = usable * Math.max(1, fronts[i]) / total;
            int abreast = (int) Math.floor(share / spacings[i] + 1.0E-9) + 1;
            abreast = Math.max(2, Math.min(Math.max(2, fronts[i]), abreast));
            result[i] = abreast;
            widths[i] = (abreast - 1) * spacings[i] + spacings[i];
        }
        double[] middles = sideBySide(widths, gap);
        System.arraycopy(middles, 0, result, count, count);
        return result;
    }
}
