package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ancient Warfare 2 Tweaked's faction relations, from {@code factions.json}:
 * which factions each one attacks (one-directional, as in AW), a new player's
 * standing with it, how a kill or a trade moves that standing, and which mobs
 * it hunts. Player standings themselves live in {@link NpcStandings}.
 */
public final class NpcFactions
{
    private static final String RESOURCE = "/assets/ancientempires/npc/factions.json";

    private static final Map<String, Faction> FACTIONS = new HashMap<>();
    /** Ids of the townsfolk list; types are resolved on first use, after registries load. */
    private static final Set<String> TOWNSFOLK_IDS = new HashSet<>();
    @Nullable
    private static Set<EntityType<?>> townsfolk;

    static
    {
        load();
    }

    private NpcFactions()
    {
    }

    /**
     * One faction's row. {@code huntsTownsfolk} is AW's "friendly" target list, used by the
     * evil factions. {@code nightLights}: its guards carry torches at night (off for beasts,
     * giants, demons, and the like); missing means yes.
     * {@code fearless}: its soldiers never waver or rout (morale); missing means no.
     */
    public record Faction(String id, Set<String> hostile, int standing, int kill, int trade, boolean huntsTownsfolk, boolean nightLights, boolean fearless)
    {
    }

    @Nullable
    public static Faction get(String id)
    {
        return id == null ? null : FACTIONS.get(id);
    }

    /** Whether a faction's guards carry lights at night. Unknown factions do. */
    public static boolean carriesLights(String id)
    {
        Faction faction = get(id);
        return faction == null || faction.nightLights();
    }

    /** Whether a faction never breaks (morale): the undead, demons, monsters. Unknown factions can. */
    public static boolean fearless(String id)
    {
        Faction faction = get(id);
        return faction != null && faction.fearless();
    }

    /** Whether {@code attacker}'s NPCs attack {@code target}'s. Not symmetric. */
    public static boolean hostile(String attacker, String target)
    {
        if (attacker == null || target == null || attacker.isEmpty() || attacker.equals(target))
        {
            return false;
        }
        Faction faction = FACTIONS.get(attacker);
        return faction != null && faction.hostile().contains(target);
    }

    /** A new player's standing with this faction. Unknown factions start neutral. */
    public static int defaultStanding(String id)
    {
        Faction faction = FACTIONS.get(id);
        return faction == null ? 0 : faction.standing();
    }

    public static int killChange(String id)
    {
        Faction faction = FACTIONS.get(id);
        return faction == null ? 0 : faction.kill();
    }

    public static int tradeChange(String id)
    {
        Faction faction = FACTIONS.get(id);
        return faction == null ? 0 : faction.trade();
    }

    public static Set<String> ids()
    {
        return FACTIONS.keySet();
    }

    /** A faction's name for chat: its id, capitalized ({@code norska} as Norska). */
    public static Component displayName(String id)
    {
        if (id == null || id.isEmpty())
        {
            return Component.empty();
        }
        String name = Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
        return Component.translatableWithFallback("faction.ancientempires." + id, name);
    }

    /**
     * Whether this faction's NPCs hunt the mob. Good factions hunt monsters
     * (any {@link Enemy}); evil ones leave monsters alone and hunt townsfolk
     * (villagers, golems). Factions missing from the file hunt monsters.
     */
    public static boolean hunts(String id, LivingEntity mob)
    {
        Faction faction = FACTIONS.get(id);
        if (faction != null && faction.huntsTownsfolk())
        {
            return townsfolk().contains(mob.getType());
        }
        return mob instanceof Enemy;
    }

    /** Villagers, golems, and the rest of AW's "friendly" list. */
    public static boolean isTownsfolk(LivingEntity mob)
    {
        return townsfolk().contains(mob.getType());
    }

    private static Set<EntityType<?>> townsfolk()
    {
        Set<EntityType<?>> types = townsfolk;
        if (types == null)
        {
            types = new HashSet<>();
            for (String id : TOWNSFOLK_IDS)
            {
                ResourceLocation location = ResourceLocation.tryParse(id);
                if (location != null && ForgeRegistries.ENTITY_TYPES.containsKey(location))
                {
                    types.add(ForgeRegistries.ENTITY_TYPES.getValue(location));
                }
            }
            townsfolk = types;
        }
        return types;
    }

    private static void load()
    {
        InputStream stream = NpcFactions.class.getResourceAsStream(RESOURCE);
        if (stream == null)
        {
            AncientEmpires.LOGGER.error("Missing faction table {}", RESOURCE);
            return;
        }
        try (InputStream in = stream; Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
        {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("mob_lists") && root.getAsJsonObject("mob_lists").has("townsfolk"))
            {
                for (JsonElement element : root.getAsJsonObject("mob_lists").getAsJsonArray("townsfolk"))
                {
                    TOWNSFOLK_IDS.add(element.getAsString());
                }
            }
            for (JsonElement element : root.getAsJsonArray("factions"))
            {
                JsonObject object = element.getAsJsonObject();
                String id = object.get("id").getAsString();
                Set<String> hostile = new HashSet<>();
                for (JsonElement other : object.getAsJsonArray("hostile"))
                {
                    hostile.add(other.getAsString());
                }
                FACTIONS.put(id, new Faction(id, Set.copyOf(hostile), integer(object, "standing"), integer(object, "kill"),
                        integer(object, "trade"), "townsfolk".equals(object.has("hunts") ? object.get("hunts").getAsString() : ""),
                        !object.has("nightLights") || object.get("nightLights").getAsBoolean(),
                        object.has("fearless") && object.get("fearless").getAsBoolean()));
            }
        }
        catch (Exception exception)
        {
            AncientEmpires.LOGGER.error("Could not read the faction table", exception);
        }
    }

    private static int integer(JsonObject object, String key)
    {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }
}
