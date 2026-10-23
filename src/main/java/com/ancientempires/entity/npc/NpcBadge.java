package com.ancientempires.entity.npc;

import org.jetbrains.annotations.Nullable;

/**
 * The badge drawn beside the status icon ({@link NpcStatus}): a levy's
 * pitchfork, or how a soldier's morale stands ({@link NpcMorale}). Worked out
 * with the status and synced as one byte.
 */
public enum NpcBadge
{
    NONE(null),
    LEVIED("levied"),
    INSPIRED("inspired"),
    WAVERING("wavering"),
    ROUTING("routing"),
    SHATTERED("shattered"),
    SURRENDERED("surrendered");

    private static final NpcBadge[] VALUES = values();

    @Nullable
    private final String icon;

    NpcBadge(@Nullable String icon)
    {
        this.icon = icon;
    }

    /** Texture name under {@code textures/gui/status}, or null for none. */
    @Nullable
    public String icon()
    {
        return this.icon;
    }

    public static NpcBadge byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    static NpcBadge of(NpcEntity npc)
    {
        if (npc.isLevied())
        {
            return LEVIED;
        }
        NpcMorale morale = npc.getMorale();
        if (!morale.applies())
        {
            return NONE;
        }
        return switch (morale.state())
        {
            case SURRENDERED -> SURRENDERED;
            case SHATTERED -> SHATTERED;
            case ROUTING -> ROUTING;
            case WAVERING -> WAVERING;
            // In a fight only: a garrison at peace around its leader would all wear stars.
            case STEADY -> npc.isInspired() && npc.getTarget() != null ? INSPIRED : NONE;
        };
    }
}
