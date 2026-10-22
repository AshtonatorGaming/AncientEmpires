package com.ancientempires.entity.npc;

import com.ancientempires.combat.WeaponClassifier;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * The path an NPC is on, chosen once. Ancient Warfare made you craft a worker or
 * a combat NPC; here an owned NPC starts as a {@link #COMMONER}, a plain person
 * kept for flavor, and its owner may later make it a {@link #CIVILIAN} or a
 * {@link #SOLDIER}. That choice is final (admins aside). Faction NPCs take theirs
 * from the roster rank and never change it in play.
 * <p>
 * The calling decides whether the NPC fights or runs. What it does within the
 * calling, its role, comes from its equipment (a bow makes an archer, a hoe a
 * farmer), as in Albion Online; see {@link #roleId}.
 */
public enum NpcCalling
{
    COMMONER(false),
    CIVILIAN(false),
    SOLDIER(true);

    /**
     * Rank heads that name a trade. They label an NPC whose equipment says nothing
     * more specific; civilian trades also put the rank on the civilian path.
     */
    private static final Set<String> CIVILIAN_TRADES = Set.of("worker", "farmer", "miner", "woodcutter", "craftsman",
            "researcher", "courier", "trader", "priest", "bard");
    private static final Set<String> SOLDIER_TRADES = Set.of("medic", "engineer", "siege_engineer", "spellcaster",
            "cavalry", "mounted_archer");

    private final boolean combat;
    private final String id;

    NpcCalling(boolean combat)
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

    /**
     * Reads a saved calling. Also takes the role ids an earlier build saved under
     * {@code AeRole} (soldier, medic, farmer, ...) and maps them to their path.
     */
    @Nullable
    public static NpcCalling byId(@Nullable String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }
        for (NpcCalling calling : values())
        {
            if (calling.id.equals(id))
            {
                return calling;
            }
        }
        if (CIVILIAN_TRADES.contains(id))
        {
            return CIVILIAN;
        }
        if (SOLDIER_TRADES.contains(id))
        {
            return SOLDIER;
        }
        return null;
    }

    /**
     * The calling a roster rank implies. The part before the first dot names it
     * ({@code civilian.female}, {@code soldier.elite}); a rank that is not a
     * commoner or a civilian trade is a soldier, so unknown ranks keep fighting.
     */
    public static NpcCalling fromRank(String rank)
    {
        String head = head(rank);
        if (head.equals("commoner"))
        {
            return COMMONER;
        }
        if (head.equals("civilian") || CIVILIAN_TRADES.contains(head))
        {
            return CIVILIAN;
        }
        return SOLDIER;
    }

    /** The trade a roster rank names, or empty. Only a fallback label; it has no behavior yet. */
    public static String rankTrade(String rank)
    {
        String head = head(rank);
        boolean trade = (CIVILIAN_TRADES.contains(head) && !head.equals("worker")) || SOLDIER_TRADES.contains(head);
        return trade ? head : "";
    }

    /**
     * The role shown for an NPC on this path: what its main hand says first, then
     * the trade its rank names, then the calling itself. A commoner is always a
     * commoner, and an armed civilian is still a civilian.
     */
    public String roleId(ItemStack mainHand, String trade)
    {
        if (this == COMMONER)
        {
            return this.id;
        }
        String tool = this.combat ? weaponRole(mainHand) : toolRole(mainHand);
        if (tool != null)
        {
            return tool;
        }
        return trade.isEmpty() ? this.id : trade;
    }

    public static String translationKey(String roleId)
    {
        return "role.ancientempires." + roleId;
    }

    @Nullable
    private static String weaponRole(ItemStack stack)
    {
        WeaponClassifier.Kind kind = WeaponClassifier.kind(stack);
        if (kind == WeaponClassifier.Kind.BOW)
        {
            return "archer";
        }
        if (kind == WeaponClassifier.Kind.OTHER_RANGED)
        {
            return stack.getItem() instanceof CrossbowItem ? "crossbowman" : "skirmisher";
        }
        return null;
    }

    @Nullable
    private static String toolRole(ItemStack stack)
    {
        if (stack.getItem() instanceof HoeItem)
        {
            return "farmer";
        }
        if (stack.getItem() instanceof PickaxeItem)
        {
            return "miner";
        }
        if (stack.getItem() instanceof AxeItem)
        {
            return "woodcutter";
        }
        if (stack.getItem() instanceof FishingRodItem)
        {
            return "fisher";
        }
        return null;
    }

    private static String head(String rank)
    {
        int dot = rank.indexOf('.');
        return dot >= 0 ? rank.substring(0, dot) : rank;
    }
}
