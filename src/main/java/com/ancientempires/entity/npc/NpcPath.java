package com.ancientempires.entity.npc;

import com.ancientempires.combat.WeaponClassifier;
import com.ancientempires.item.BandageItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.BookItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EnchantedBookItem;
import com.ancientempires.item.RoutingOrderItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.EmptyMapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.WritableBookItem;
import net.minecraft.world.item.WrittenBookItem;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * The path an NPC is on, chosen once. Ancient Warfare made you craft a worker or
 * a combat NPC; here an owned NPC starts as a {@link #SETTLER}, a plain person
 * kept for flavor, and its owner may later make it a {@link #WORKER} or a
 * {@link #SOLDIER}. That choice is final (admins aside). Faction NPCs take theirs
 * from the roster rank and never change it in play.
 * <p>
 * The path decides whether the NPC fights or runs. What it does within the
 * path, its role, comes from its equipment (a bow makes an archer, a hoe a
 * farmer), as in Albion Online; see {@link #roleId}.
 */
public enum NpcPath
{
    SETTLER(false),
    WORKER(false),
    SOLDIER(true);

    /**
     * Rank heads that name a trade. They label an NPC whose equipment says nothing
     * more specific; worker trades also put the rank on the worker path.
     */
    private static final Set<String> WORKER_TRADES = Set.of("worker", "farmer", "miner", "woodcutter", "craftsman",
            "researcher", "courier", "trader", "priest", "bard");
    private static final Set<String> SOLDIER_TRADES = Set.of("medic", "engineer", "siege_engineer", "spellcaster",
            "cavalry", "mounted_archer");

    private final boolean combat;
    private final String id;

    NpcPath(boolean combat)
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
     * Reads a saved path. Also takes ids earlier builds saved: the role ids
     * under {@code AeRole} (medic, farmer, ...) and the old names commoner and
     * civilian, and maps them to their path.
     */
    @Nullable
    public static NpcPath byId(@Nullable String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }
        for (NpcPath path : values())
        {
            if (path.id.equals(id))
            {
                return path;
            }
        }
        if (id.equals("commoner"))
        {
            return SETTLER;
        }
        if (id.equals("civilian") || WORKER_TRADES.contains(id))
        {
            return WORKER;
        }
        if (SOLDIER_TRADES.contains(id))
        {
            return SOLDIER;
        }
        return null;
    }

    /**
     * The path a roster rank implies. The part before the first dot names it
     * ({@code civilian.female}, {@code soldier.elite}). The {@code commoner} rank
     * (the generic scroll) is a settler; townsfolk ({@code civilian}) and worker
     * trades are workers; anything else is a soldier, so unknown ranks keep fighting.
     */
    public static NpcPath fromRank(String rank)
    {
        String head = head(rank);
        if (head.equals("commoner") || head.equals("settler"))
        {
            return SETTLER;
        }
        if (head.equals("civilian") || WORKER_TRADES.contains(head))
        {
            return WORKER;
        }
        return SOLDIER;
    }

    /**
     * The trade a roster rank names, or empty. Only a fallback label; it has no
     * behavior yet. Faction townsfolk read as civilians, not workers.
     */
    public static String rankTrade(String rank)
    {
        String head = head(rank);
        boolean trade = head.equals("civilian") || (WORKER_TRADES.contains(head) && !head.equals("worker"))
                || SOLDIER_TRADES.contains(head);
        return trade ? head : "";
    }

    /**
     * The role shown for an NPC on this path: what its main hand says first, then
     * the trade its rank names, then the path itself. A settler is always a
     * settler, and an armed worker is still a worker.
     */
    public String roleId(ItemStack mainHand, ItemStack offHand, String trade)
    {
        if (this == SETTLER)
        {
            return this.id;
        }
        if (this.combat && offHand.getItem() instanceof BandageItem)
        {
            // Bandages in the off hand make a medic, whatever the main hand holds.
            return "medic";
        }
        if (this.combat && offHand.getItem() instanceof BannerItem)
        {
            // A banner in the off hand: the standard bearer (morale aura, NpcMorale).
            return "standard_bearer";
        }
        String tool = this.combat ? weaponRole(mainHand) : toolRole(mainHand);
        if (tool != null)
        {
            return tool;
        }
        // A soldier with nothing to name it by is a footman.
        return trade.isEmpty() ? this.combat ? "footman" : this.id : trade;
    }

    /** A book of any kind: a worker holding one is a priest. */
    public static boolean isScripture(ItemStack stack)
    {
        return stack.getItem() instanceof BookItem
                || stack.getItem() instanceof WritableBookItem
                || stack.getItem() instanceof WrittenBookItem
                || stack.getItem() instanceof EnchantedBookItem;
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
        return stack.isEmpty() ? null : MELEE_ROLES.computeIfAbsent(stack.getItem(), NpcPath::meleeRole).orElse(null);
    }

    /** Each melee item's infantry role, worked out once. */
    private static final Map<Item, Optional<String>> MELEE_ROLES = new ConcurrentHashMap<>();

    /**
     * Infantry named by the weapon in hand, as archers are by the bow: polearms make
     * halberdiers, spears spearmen, maces and hammers macemen, axes axemen, blades
     * swordsmen. Spartan's weapons are told apart by name (checked in that order, so
     * a poleaxe is no axe); vanilla's by kind. Anything else is a footman.
     */
    private static Optional<String> meleeRole(Item item)
    {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        String name = id == null ? "" : id.getPath();
        if (name.contains("halberd") || name.contains("glaive") || name.contains("poleaxe") || name.contains("pollaxe") || name.contains("scythe"))
        {
            return Optional.of("halberdier");
        }
        if (item instanceof TridentItem || name.contains("spear") || name.contains("pike") || name.contains("lance") || name.contains("trident"))
        {
            return Optional.of("spearman");
        }
        if (name.contains("mace") || name.contains("hammer") || name.contains("club") || name.contains("morning_star") || name.contains("flail"))
        {
            return Optional.of("maceman");
        }
        if (!(item instanceof PickaxeItem) && (item instanceof AxeItem || name.contains("axe") && !name.contains("pickaxe")))
        {
            return Optional.of("axeman");
        }
        if (item instanceof SwordItem || name.contains("sword") || name.contains("saber") || name.contains("sabre") || name.contains("katana")
                || name.contains("rapier") || name.contains("dagger") || name.contains("blade") || name.contains("cutlass") || name.contains("scimitar")
                || name.contains("falchion"))
        {
            return Optional.of("swordsman");
        }
        return Optional.empty();
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
        if (isScripture(stack))
        {
            return "priest";
        }
        if (stack.getItem() instanceof MapItem || stack.getItem() instanceof EmptyMapItem)
        {
            return "governor";
        }
        if (stack.getItem() instanceof ShearsItem)
        {
            return "rancher";
        }
        if (stack.getItem() instanceof BowItem)
        {
            return "hunter";
        }
        if (stack.is(Items.BOWL))
        {
            return "cook";
        }
        if (stack.getItem() instanceof RoutingOrderItem)
        {
            return "courier";
        }
        return null;
    }

    private static String head(String rank)
    {
        int dot = rank.indexOf('.');
        return dot >= 0 ? rank.substring(0, dot) : rank;
    }
}
