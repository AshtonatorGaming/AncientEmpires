package com.ancientempires.entity.npc;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * What an NPC does, separate from its roster rank (which picks skins and skill
 * tier). Ancient Warfare split this into entity classes; here it is data. Every
 * role that is not {@link #combat()} sits under the civilian umbrella: it runs
 * from danger and fights only when cornered or levied.
 * <p>
 * Worker jobs have no worksite behavior yet; for now they are civilians with a
 * name. Combat roles share the soldier behavior, and equipment still decides
 * the style (a bow makes an archer).
 */
public enum NpcRole
{
    COMMONER(false),
    SOLDIER(true),
    MEDIC(true),
    ENGINEER(true),
    SIEGE_ENGINEER(true),
    WORKER(false),
    FARMER(false),
    MINER(false),
    WOODCUTTER(false),
    CRAFTSMAN(false),
    RESEARCHER(false),
    COURIER(false),
    TRADER(false),
    PRIEST(false),
    BARD(false);

    /** Roles an owner can pick in the inventory screen, in button order. */
    public static final List<NpcRole> OWNED_CHOICES = List.of(COMMONER, SOLDIER, WORKER, FARMER, MINER, WOODCUTTER,
            CRAFTSMAN, RESEARCHER, COURIER, TRADER, PRIEST, BARD, MEDIC, ENGINEER, SIEGE_ENGINEER);

    private final boolean combat;
    private final String id;

    NpcRole(boolean combat)
    {
        this.combat = combat;
        this.id = this.name().toLowerCase(Locale.ROOT);
    }

    public boolean combat()
    {
        return this.combat;
    }

    public String id()
    {
        return this.id;
    }

    public String translationKey()
    {
        return "role.ancientempires." + this.id;
    }

    @Nullable
    public static NpcRole byId(@Nullable String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }
        for (NpcRole role : values())
        {
            if (role.id.equals(id))
            {
                return role;
            }
        }
        return null;
    }

    /**
     * The role a roster rank implies. The part before the first dot names it
     * ({@code civilian.female}, {@code soldier.elite}); any rank that is not a
     * known civilian or support job is a soldier, so unknown ranks keep fighting.
     */
    public static NpcRole fromRank(String rank)
    {
        int dot = rank.indexOf('.');
        String head = dot >= 0 ? rank.substring(0, dot) : rank;
        if (head.equals("civilian") || head.equals("commoner"))
        {
            return COMMONER;
        }
        NpcRole named = byId(head);
        return named != null ? named : SOLDIER;
    }
}
