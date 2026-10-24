package com.ancientempires.config;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcShieldBash;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

/**
 * Combat timing and reach. Every value the attack goal reads lives here so a pack
 * can tune soldiers without another mod.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AncientEmpiresConfig
{
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.DoubleValue UNARMED_RANGE;
    private static final ForgeConfigSpec.IntValue UNARMED_WINDUP_TICKS;
    private static final ForgeConfigSpec.IntValue UNARMED_COOLDOWN_TICKS;
    private static final ForgeConfigSpec.DoubleValue UNARMED_DAMAGE_MULTIPLIER;
    private static final ForgeConfigSpec.BooleanValue UNARMED_PREFER_BETTER_COMBAT;

    private static final ForgeConfigSpec.DoubleValue VANILLA_RANGE;
    private static final ForgeConfigSpec.DoubleValue VANILLA_WINDUP_FRACTION;
    private static final ForgeConfigSpec.IntValue VANILLA_MIN_COOLDOWN_TICKS;

    private static final ForgeConfigSpec.BooleanValue BETTER_COMBAT_ENABLED;
    private static final ForgeConfigSpec.DoubleValue UPSWING_MULTIPLIER;
    private static final ForgeConfigSpec.BooleanValue READ_UPSWING_FROM_MOD;
    private static final ForgeConfigSpec.IntValue MIN_ATTACK_INTERVAL;

    private static final ForgeConfigSpec.DoubleValue FORWARD_ANGLE;
    private static final ForgeConfigSpec.DoubleValue SWEEP_ANGLE;
    private static final ForgeConfigSpec.DoubleValue TURN_SPEED;
    private static final ForgeConfigSpec.DoubleValue TURN_SPEED_IN_RANGE;
    private static final ForgeConfigSpec.DoubleValue MAX_YAW_ERROR;
    private static final ForgeConfigSpec.IntValue FORCE_SWING_TICKS;
    private static final ForgeConfigSpec.DoubleValue RANGE_SCALE_BASELINE;
    private static final ForgeConfigSpec.DoubleValue RANGE_SCALE_MIN;
    private static final ForgeConfigSpec.DoubleValue RANGE_SCALE_MAX;
    private static final ForgeConfigSpec.DoubleValue TALL_AIM_FACTOR;
    private static final ForgeConfigSpec.DoubleValue TALL_PITCH_OFFSET;
    private static final ForgeConfigSpec.BooleanValue CANCEL_IF_OUT_OF_RANGE;
    private static final ForgeConfigSpec.DoubleValue WINDUP_RANGE_SLACK;
    private static final ForgeConfigSpec.DoubleValue WINDUP_KEEP_RANGE;
    private static final ForgeConfigSpec.DoubleValue MIN_GAP;
    private static final ForgeConfigSpec.DoubleValue MIN_GAP_REACH;
    private static final ForgeConfigSpec.BooleanValue STOP_MOVING_DURING_WINDUP;
    private static final ForgeConfigSpec.DoubleValue WINDUP_MOVE_SPEED;
    private static final ForgeConfigSpec.BooleanValue FOOTWORK;
    private static final ForgeConfigSpec.DoubleValue SIDESTEP_CHANCE;
    private static final ForgeConfigSpec.IntValue FOOTWORK_CROWD;
    private static final ForgeConfigSpec.DoubleValue DUEL_STRAFE;
    private static final ForgeConfigSpec.DoubleValue DUEL_BACKPEDAL;
    private static final ForgeConfigSpec.DoubleValue DUEL_RANGE;
    private static final ForgeConfigSpec.DoubleValue SIDESTEP_SPEED;
    private static final ForgeConfigSpec.DoubleValue SHIELD_SIDESTEP_SCALE;
    private static final ForgeConfigSpec.DoubleValue PRESSURE_CHANCE;
    private static final ForgeConfigSpec.IntValue ATTACK_PAUSE;
    private static final ForgeConfigSpec.DoubleValue APPROACH_SPEED;
    private static final ForgeConfigSpec.IntValue PATH_RECALC_TICKS;
    private static final ForgeConfigSpec.BooleanValue REQUIRE_LINE_OF_SIGHT;
    private static final ForgeConfigSpec.BooleanValue BYPASS_HURT_COOLDOWN;
    private static final ForgeConfigSpec.DoubleValue COMBO_RESET_RATE;
    private static final ForgeConfigSpec.BooleanValue SWEEP_EFFECTS;
    private static final ForgeConfigSpec.BooleanValue DUAL_WIELD;
    private static final ForgeConfigSpec.DoubleValue NPC_WINDUP_SCALE;
    private static final ForgeConfigSpec.DoubleValue MAX_SPEED;
    private static final ForgeConfigSpec.DoubleValue NPC_VS_NPC_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue NPC_VS_NPC_KNOCKBACK;
    private static final ForgeConfigSpec.DoubleValue ENGAGE_RANGE_FACTOR;

    private static final ForgeConfigSpec.BooleanValue SIDEARM_ENABLED;
    private static final ForgeConfigSpec.DoubleValue SIDEARM_CORNERED_RANGE;
    private static final ForgeConfigSpec.DoubleValue SIDEARM_RELEASE_RANGE;
    private static final ForgeConfigSpec.IntValue SIDEARM_RELEASE_TICKS;
    private static final ForgeConfigSpec.IntValue SIDEARM_SWAP_COOLDOWN;

    private static final ForgeConfigSpec.BooleanValue OPEN_DOORS;
    private static final ForgeConfigSpec.BooleanValue CLOSE_DOORS;
    private static final ForgeConfigSpec.BooleanValue NIGHT_DOOR_RESTRICTION;

    private static final ForgeConfigSpec.IntValue HOME_WANDER_RADIUS;
    private static final ForgeConfigSpec.DoubleValue HOME_DAY_RANGE;
    private static final ForgeConfigSpec.DoubleValue OWNED_DAY_LEASH;
    private static final ForgeConfigSpec.DoubleValue FACTION_DAY_LEASH;
    private static final ForgeConfigSpec.DoubleValue HOME_NIGHT_RANGE;
    private static final ForgeConfigSpec.DoubleValue HOME_NIGHT_LEASH;
    private static final ForgeConfigSpec.BooleanValue HOME_RAIN_SHELTER;
    private static final ForgeConfigSpec.DoubleValue FACTION_ARCHER_POST;

    private static final ForgeConfigSpec.BooleanValue SHIELD_BLOCKING;
    private static final ForgeConfigSpec.DoubleValue SHIELD_BLOCK_RANGE;
    private static final ForgeConfigSpec.IntValue SHIELD_HOLD_TICKS;
    private static final ForgeConfigSpec.BooleanValue SHIELD_GUARD_IN_RANGE;
    private static final ForgeConfigSpec.IntValue SHIELD_MIN_BLOCK_TICKS;
    private static final ForgeConfigSpec.IntValue SHIELD_STRIKE_DELAY_TICKS;
    private static final ForgeConfigSpec.IntValue SHIELD_RIPOSTE_TICKS;
    private static final ForgeConfigSpec.BooleanValue SHIELD_INSTANT_BLOCK;
    private static final ForgeConfigSpec.DoubleValue SHIELD_STRIKE_CHANCE;
    private static final ForgeConfigSpec.IntValue SHIELD_GUARD_POINTS;
    private static final ForgeConfigSpec.DoubleValue SHIELD_GUARD_REGEN;
    private static final ForgeConfigSpec.IntValue SHIELD_GUARD_BREAK_TICKS;
    private static final ForgeConfigSpec.DoubleValue SHIELD_ADVANCE_SPEED;
    private static final ForgeConfigSpec.IntValue SHIELD_BASH_WINDUP;
    private static final ForgeConfigSpec.DoubleValue SHIELD_AIM_WATCH_RANGE;
    private static final ForgeConfigSpec.IntValue SHIELD_DISABLE_TICKS;
    private static final ForgeConfigSpec.DoubleValue SHIELD_NPC_AXE_CHANCE;
    private static final ForgeConfigSpec.EnumValue<NpcShieldBash.Mode> SHIELD_BASH_MODE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> SKILL_FEINT_CHANCE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> SKILL_BASH_CHANCE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> SKILL_SHIELD_REACTION;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> SKILL_GUARD;
    private static final ForgeConfigSpec.DoubleValue SHIELD_BASH_RANGE;
    private static final ForgeConfigSpec.IntValue SHIELD_BASH_COOLDOWN;
    private static final ForgeConfigSpec.IntValue TOWER_BASH_COOLDOWN;
    private static final ForgeConfigSpec.IntValue SHIELD_BASH_RECOVERY;
    private static final ForgeConfigSpec.IntValue SHIELD_FOLLOW_THROUGH;
    private static final ForgeConfigSpec.IntValue SHIELD_MOB_PATIENCE;
    private static final ForgeConfigSpec.IntValue SHIELD_PRESS_MIN_BLOCK;
    private static final ForgeConfigSpec.DoubleValue SHIELD_PRESS_STRIKE;
    private static final ForgeConfigSpec.IntValue SHIELD_DUEL_BLOCK_MIN;
    private static final ForgeConfigSpec.IntValue SHIELD_DUEL_BLOCK_MAX;
    private static final ForgeConfigSpec.IntValue SHIELD_DUEL_OPEN_MIN;
    private static final ForgeConfigSpec.IntValue SHIELD_DUEL_OPEN_MAX;
    private static final ForgeConfigSpec.IntValue SHIELD_ARROW_BLOCK;
    private static final ForgeConfigSpec.BooleanValue FEINTS;
    private static final ForgeConfigSpec.IntValue FEINT_RECOVERY;

    private static final ForgeConfigSpec.BooleanValue CIVILIANS_FLEE;
    private static final ForgeConfigSpec.DoubleValue CIVILIAN_FLEE_RANGE;
    private static final ForgeConfigSpec.DoubleValue CIVILIAN_FLEE_HEIGHT;
    private static final ForgeConfigSpec.DoubleValue CIVILIAN_FLEE_DISTANCE;
    private static final ForgeConfigSpec.DoubleValue CIVILIAN_FLEE_SPEED;
    private static final ForgeConfigSpec.IntValue CIVILIAN_STAY_AWAY;
    private static final ForgeConfigSpec.IntValue CIVILIAN_SOLDIER_CALM;
    private static final ForgeConfigSpec.DoubleValue CIVILIAN_CORNER_RANGE;
    private static final ForgeConfigSpec.IntValue CIVILIAN_CORNER_TICKS;
    private static final ForgeConfigSpec.IntValue CIVILIAN_STAND_GROUND;
    private static final ForgeConfigSpec.DoubleValue CIVILIAN_STAND_BREAK_RANGE;
    private static final ForgeConfigSpec.DoubleValue CIVILIAN_PLAYER_THREAT_HEALTH;

    private static final ForgeConfigSpec.DoubleValue ARCHER_MELEE;
    private static final ForgeConfigSpec.DoubleValue SKIRMISHER_MELEE;
    private static final ForgeConfigSpec.DoubleValue LEVY_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue LEVY_WINDUP;
    private static final ForgeConfigSpec.DoubleValue LEVY_BREAK;

    private static final ForgeConfigSpec.BooleanValue OWNER_DEFENSE;
    private static final ForgeConfigSpec.DoubleValue OWNER_DEFENSE_RANGE;
    private static final ForgeConfigSpec.DoubleValue ALLY_ALERT_RANGE;
    private static final ForgeConfigSpec.DoubleValue ALLY_ALERT_HEIGHT;
    private static final ForgeConfigSpec.DoubleValue CAVALRY_MOUNT_SPEED;
    private static final ForgeConfigSpec.DoubleValue CAVALRY_REACH_BONUS;
    private static final ForgeConfigSpec.DoubleValue CAVALRY_REMOUNT_RANGE;
    private static final ForgeConfigSpec.DoubleValue CAVALRY_MOUNT_RANGE;
    private static final ForgeConfigSpec.BooleanValue RELATIONS_ENABLED;
    private static final ForgeConfigSpec.DoubleValue RELATIONS_REVENGE_RANGE;
    private static final ForgeConfigSpec.IntValue RELATIONS_REVENGE_TICKS;
    private static final ForgeConfigSpec.IntValue RELATIONS_STANDING_LIMIT;
    private static final ForgeConfigSpec.BooleanValue RELATIONS_COUNT_CREATIVE;
    private static final ForgeConfigSpec.BooleanValue IMMIGRATION_ENABLED;
    private static final ForgeConfigSpec.DoubleValue IMMIGRATION_CHANCE;
    private static final ForgeConfigSpec.IntValue IMMIGRATION_MAX_GROUP;
    private static final ForgeConfigSpec.IntValue IMMIGRATION_BED_RADIUS;
    private static final ForgeConfigSpec.DoubleValue IMMIGRATION_SPAWN_DISTANCE;
    private static final ForgeConfigSpec.IntValue IMMIGRATION_FOOD_PER_SETTLER;
    private static final ForgeConfigSpec.IntValue IMMIGRATION_FOOD_CAPACITY;
    private static final ForgeConfigSpec.IntValue FARM_DEFAULT_SIZE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> FARM_MAX_SIZE;
    private static final ForgeConfigSpec.IntValue WORK_SCAN_TICKS;
    private static final ForgeConfigSpec.IntValue WORK_CLAIM_TICKS;
    private static final ForgeConfigSpec.IntValue WORK_ACTION_TICKS;
    private static final ForgeConfigSpec.IntValue WORK_SUPPLY_RESERVE;
    private static final ForgeConfigSpec.IntValue WORK_SEARCH_RANGE;
    private static final ForgeConfigSpec.DoubleValue WORK_WALK_SPEED;
    private static final ForgeConfigSpec.IntValue WORK_AREA_REACH;
    private static final ForgeConfigSpec.IntValue TREE_DEFAULT_SIZE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> TREE_MAX_SIZE;
    private static final ForgeConfigSpec.IntValue TREE_SPACING;
    private static final ForgeConfigSpec.IntValue TREE_REACH;
    private static final ForgeConfigSpec.IntValue TREE_MAX_LOGS;
    private static final ForgeConfigSpec.IntValue QUARRY_DEFAULT_SIZE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> QUARRY_MAX_SIZE;
    private static final ForgeConfigSpec.IntValue QUARRY_MAX_DEPTH;
    private static final ForgeConfigSpec.IntValue QUARRY_STAIR_WIDTH;
    private static final ForgeConfigSpec.BooleanValue QUARRY_STAIR_STONE;
    private static final ForgeConfigSpec.DoubleValue WORK_TOOL_WEAR;
    private static final ForgeConfigSpec.IntValue RANCH_DEFAULT_SIZE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> RANCH_MAX_SIZE;
    private static final ForgeConfigSpec.IntValue RANCH_HERD_SIZE;
    private static final ForgeConfigSpec.IntValue HUNT_DEFAULT_SIZE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> HUNT_MAX_SIZE;
    private static final ForgeConfigSpec.IntValue HUNT_KEEP_PER_KIND;
    private static final ForgeConfigSpec.IntValue HUNT_GAME_CAP;
    private static final ForgeConfigSpec.IntValue HUNT_SPAWN_INTERVAL;
    private static final ForgeConfigSpec.DoubleValue HUNT_SHOOT_RANGE;
    private static final ForgeConfigSpec.IntValue FISH_DEFAULT_SIZE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> FISH_MAX_SIZE;
    private static final ForgeConfigSpec.BooleanValue FISH_RECAST;
    private static final ForgeConfigSpec.IntValue COOK_TICKS;
    private static final ForgeConfigSpec.IntValue COOK_BUNDLE_NUTRITION;
    private static final ForgeConfigSpec.IntValue COOK_BUNDLE_TARGET;
    private static final ForgeConfigSpec.BooleanValue UPKEEP_ENABLED;
    private static final ForgeConfigSpec.DoubleValue UPKEEP_RANGE;
    private static final ForgeConfigSpec.IntValue UPKEEP_RATION;
    private static final ForgeConfigSpec.IntValue UPKEEP_BUNDLE_RATIONS;
    private static final ForgeConfigSpec.IntValue UPKEEP_MEAL_INTERVAL;
    private static final ForgeConfigSpec.IntValue UPKEEP_CARRY_RATIONS;
    private static final ForgeConfigSpec.EnumValue<Delivery> UPKEEP_DELIVERY;
    private static final ForgeConfigSpec.BooleanValue UPKEEP_REQUIRE_HALL;
    private static final ForgeConfigSpec.BooleanValue BURNING_PANIC;
    private static final ForgeConfigSpec.DoubleValue MEDIC_BANDAGE_HEAL;
    private static final ForgeConfigSpec.DoubleValue MEDIC_RANGE;
    private static final ForgeConfigSpec.BooleanValue PARRY_ENABLED;
    private static final ForgeConfigSpec.IntValue PARRY_WINDOW;
    private static final ForgeConfigSpec.IntValue PARRY_COOLDOWN;
    private static final ForgeConfigSpec.DoubleValue PARRY_VS_PLAYER;
    private static final ForgeConfigSpec.IntValue PARRY_STAGGER;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> PARRY_CHANCE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> PARRY_ERROR;
    private static final ForgeConfigSpec.BooleanValue COMMAND_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> COMMAND_STRENGTH;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> COMMAND_RADIUS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> COMMAND_PROMOTE_LEVELS;
    private static final ForgeConfigSpec.IntValue COMMAND_TICKS;
    private static final ForgeConfigSpec.DoubleValue COMMAND_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue COMMAND_ARMOR;
    private static final ForgeConfigSpec.DoubleValue COMMAND_SUCCESSION_RANGE;
    private static final ForgeConfigSpec.DoubleValue COMMAND_SUCCESSION_SCALE;
    private static final ForgeConfigSpec.DoubleValue COMMAND_MIN_SCALE;
    private static final ForgeConfigSpec.BooleanValue MORALE_ENABLED;
    private static final ForgeConfigSpec.BooleanValue MORALE_OWNED;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> MORALE_BASE;
    private static final ForgeConfigSpec.IntValue MORALE_UPDATE_TICKS;
    private static final ForgeConfigSpec.DoubleValue MORALE_RANGE;
    private static final ForgeConfigSpec.DoubleValue MORALE_WAVER_BELOW;
    private static final ForgeConfigSpec.DoubleValue MORALE_ROUT_BELOW;
    private static final ForgeConfigSpec.DoubleValue MORALE_RALLY_ABOVE;
    private static final ForgeConfigSpec.IntValue MORALE_MIN_ROUT_TICKS;
    private static final ForgeConfigSpec.IntValue MORALE_SHATTER_AFTER;
    private static final ForgeConfigSpec.DoubleValue MORALE_FALL_RATE;
    private static final ForgeConfigSpec.DoubleValue MORALE_RISE_RATE;
    private static final ForgeConfigSpec.DoubleValue MORALE_OUTNUMBER;
    private static final ForgeConfigSpec.DoubleValue MORALE_WOUNDS;
    private static final ForgeConfigSpec.DoubleValue MORALE_FLANK;
    private static final ForgeConfigSpec.IntValue MORALE_FLANK_TICKS;
    private static final ForgeConfigSpec.DoubleValue MORALE_HUNGER;
    private static final ForgeConfigSpec.DoubleValue MORALE_ROUTING_ALLY;
    private static final ForgeConfigSpec.DoubleValue MORALE_ROUTING_CAP;
    private static final ForgeConfigSpec.DoubleValue MORALE_INSPIRED;
    private static final ForgeConfigSpec.DoubleValue MORALE_COMMANDER_SHOCK;
    private static final ForgeConfigSpec.DoubleValue MORALE_COMMANDER_RANGE;
    private static final ForgeConfigSpec.DoubleValue MORALE_ALLY_SHOCK;
    private static final ForgeConfigSpec.DoubleValue MORALE_ALLY_RANGE;
    private static final ForgeConfigSpec.DoubleValue MORALE_KILL_BOOST;
    private static final ForgeConfigSpec.DoubleValue MORALE_SHOCK_DECAY;
    private static final ForgeConfigSpec.DoubleValue MORALE_HORN;
    private static final ForgeConfigSpec.BooleanValue MORALE_SURRENDER;
    private static final ForgeConfigSpec.DoubleValue MORALE_SURRENDER_HEALTH;
    private static final ForgeConfigSpec.DoubleValue MORALE_SURRENDER_CHANCE;
    private static final ForgeConfigSpec.IntValue MORALE_SURRENDER_RELEASE;
    private static final ForgeConfigSpec.BooleanValue MORALE_DESERT;
    private static final ForgeConfigSpec.DoubleValue MORALE_DESERT_RANGE;
    private static final ForgeConfigSpec.IntValue MORALE_RECOVER_TICKS;
    private static final ForgeConfigSpec.DoubleValue MORALE_OWNED_BASE;
    private static final ForgeConfigSpec.DoubleValue MORALE_OWNER_PRESENCE;
    private static final ForgeConfigSpec.DoubleValue MORALE_OWNER_RANGE;
    private static final ForgeConfigSpec.DoubleValue MORALE_CHASE_RANGE;
    private static final ForgeConfigSpec.BooleanValue LEVEL_ENABLED;
    private static final ForgeConfigSpec.IntValue LEVEL_MAX;
    private static final ForgeConfigSpec.DoubleValue LEVEL_XP_BASE;
    private static final ForgeConfigSpec.DoubleValue LEVEL_XP_STEP;
    private static final ForgeConfigSpec.DoubleValue LEVEL_XP_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue LEVEL_XP_KILL;
    private static final ForgeConfigSpec.DoubleValue LEVEL_XP_KILL_LEVEL;
    private static final ForgeConfigSpec.DoubleValue LEVEL_XP_BLOCK;
    private static final ForgeConfigSpec.DoubleValue LEVEL_XP_PERFECT;
    private static final ForgeConfigSpec.DoubleValue LEVEL_XP_WORK;
    private static final ForgeConfigSpec.DoubleValue LEVEL_HEALTH;
    private static final ForgeConfigSpec.DoubleValue LEVEL_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue LEVEL_WORK_SPEED;
    private static final ForgeConfigSpec.DoubleValue LEVEL_TOOL_WEAR;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> LEVEL_FACTION_MIN;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> LEVEL_FACTION_MAX;
    private static final ForgeConfigSpec.BooleanValue LEVEL_ANNOUNCE;
    private static final ForgeConfigSpec.BooleanValue NIGHT_LIGHT_ENABLED;
    private static final ForgeConfigSpec.BooleanValue NIGHT_LIGHT_FACTION_GUARDS;
    private static final ForgeConfigSpec.IntValue NIGHT_LIGHT_DARKNESS;
    private static final ForgeConfigSpec.DoubleValue HORN_RANGE;
    private static final ForgeConfigSpec.IntValue HORN_COOLDOWN_TICKS;
    private static final ForgeConfigSpec.IntValue HORN_RALLY_TICKS;
    private static final ForgeConfigSpec.DoubleValue MEDIC_HEAL_BELOW;
    private static final ForgeConfigSpec.IntValue MEDIC_BIND_TICKS;
    private static final ForgeConfigSpec.IntValue MEDIC_COOLDOWN_TICKS;
    private static final ForgeConfigSpec.DoubleValue MEDIC_MELEE;
    private static final ForgeConfigSpec.IntValue PRIEST_REVIVE_TICKS;
    private static final ForgeConfigSpec.BooleanValue PRIEST_COST_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> PRIEST_COST_ITEM;
    private static final ForgeConfigSpec.IntValue PRIEST_COST_COUNT;
    private static final ForgeConfigSpec.BooleanValue SLEEP_ENABLED;
    private static final ForgeConfigSpec.BooleanValue SLEEP_SOLDIERS;
    private static final ForgeConfigSpec.IntValue SLEEP_BED_RANGE;
    private static final ForgeConfigSpec.IntValue HAZARD_MAX_FALL;
    private static final ForgeConfigSpec.DoubleValue HAZARD_WATER_COST;
    private static final ForgeConfigSpec.DoubleValue HAZARD_FIRE_COST;
    private static final ForgeConfigSpec.BooleanValue HAZARD_AVOID_WATER_SPOTS;
    private static final ForgeConfigSpec.IntValue HAZARD_SAFE_SPOT_TRIES;
    private static final ForgeConfigSpec.BooleanValue THREAT_SCORING;
    private static final ForgeConfigSpec.DoubleValue THREAT_OWNER_WEIGHT;
    private static final ForgeConfigSpec.DoubleValue THREAT_ALLY_WEIGHT;
    private static final ForgeConfigSpec.DoubleValue THREAT_SELF_WEIGHT;
    private static final ForgeConfigSpec.DoubleValue THREAT_WOUNDED_WEIGHT;
    private static final ForgeConfigSpec.DoubleValue THREAT_FACING_WEIGHT;
    private static final ForgeConfigSpec.DoubleValue THREAT_ROLE_WEIGHT;
    private static final ForgeConfigSpec.IntValue UPKEEP_FALLEN_RECORDS;
    private static final ForgeConfigSpec.EnumValue<KeepOnDeath> UPKEEP_KEEP_ON_DEATH;

    private static final ForgeConfigSpec.BooleanValue TARGET_LEGACY_SCAN;
    private static final ForgeConfigSpec.IntValue TARGET_SCAN_INTERVAL;
    private static final ForgeConfigSpec.IntValue TARGET_RETARGET_INTERVAL;
    private static final ForgeConfigSpec.DoubleValue TARGET_SWITCH_MARGIN;
    private static final ForgeConfigSpec.DoubleValue TARGET_SCAN_RANGE;
    private static final ForgeConfigSpec.IntValue TARGET_CACHE_TICKS;
    private static final ForgeConfigSpec.IntValue TARGET_MAX_SIGHT_CHECKS;
    private static final ForgeConfigSpec.BooleanValue TARGET_DEBUG_STATS;
    private static final ForgeConfigSpec.IntValue TARGET_MEMORY;
    private static final ForgeConfigSpec.IntValue TARGET_PURSUIT;

    static
    {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("combat");

        builder.push("unarmed");
        UNARMED_RANGE = builder
                .comment("Reach of an empty-hand attack, in blocks, when Better Combat's fist profile is not used.")
                .defineInRange("range", 2.0D, 0.5D, 8.0D);
        UNARMED_WINDUP_TICKS = builder
                .comment("Ticks after the swing starts before an empty-hand hit lands.")
                .defineInRange("windupTicks", 4, 1, 200);
        UNARMED_COOLDOWN_TICKS = builder
                .comment("Ticks from the start of one empty-hand swing until the next can start. Includes the wind-up.")
                .defineInRange("cooldownTicks", 10, 1, 200);
        UNARMED_DAMAGE_MULTIPLIER = builder
                .comment("Multiplies the NPC attack damage attribute for an empty-hand hit.")
                .defineInRange("damageMultiplier", 1.0D, 0.0D, 100.0D);
        UNARMED_PREFER_BETTER_COMBAT = builder
                .comment("When Better Combat is loaded, empty hands use its fist weapon attributes instead of the values above. No item is equipped.")
                .define("preferBetterCombatProfile", true);
        builder.pop();

        builder.push("vanilla");
        VANILLA_RANGE = builder
                .comment("Reach used for a held weapon that has no Better Combat attributes.")
                .defineInRange("range", 2.5D, 0.5D, 16.0D);
        VANILLA_WINDUP_FRACTION = builder
                .comment("Fraction of the weapon cooldown spent winding up when Better Combat timing is unavailable.")
                .defineInRange("windupFraction", 0.35D, 0.0D, 1.0D);
        VANILLA_MIN_COOLDOWN_TICKS = builder
                .comment("Shortest weapon cooldown, in ticks, for the internal fallback.")
                .defineInRange("minCooldownTicks", 6, 1, 200);
        builder.pop();

        builder.push("betterCombat");
        BETTER_COMBAT_ENABLED = builder
                .comment("Read Better Combat weapon attributes when that mod is loaded. Turn this off to force the internal fallback.")
                .define("enabled", true);
        UPSWING_MULTIPLIER = builder
                .comment("Scales a Better Combat attack's upswing. 0.5 matches Better Combat's default and lands the hit about 25% through a 0.5-upswing weapon.")
                .defineInRange("upswingMultiplier", 0.5D, 0.2D, 1.0D);
        READ_UPSWING_FROM_MOD = builder
                .comment("When true, and Better Combat is loaded, use its server upswing_multiplier instead of upswingMultiplier above.")
                .define("readUpswingFromMod", true);
        MIN_ATTACK_INTERVAL = builder
                .comment("Minimum ticks between the starts of two attacks driven by Better Combat attributes.")
                .defineInRange("minAttackInterval", 2, 1, 40);
        builder.pop();

        FORWARD_ANGLE = builder
                .comment("Cone, in degrees, for punches, stabs, and other attacks that do not define a sweep angle. The target must sit inside this arc in front of the NPC.")
                .defineInRange("forwardAngle", 50.0D, 1.0D, 360.0D);
        SWEEP_ANGLE = builder
                .comment("Arc, in degrees, used when a sweeping attack does not specify its own angle.")
                .defineInRange("sweepAngle", 120.0D, 1.0D, 360.0D);
        TURN_SPEED = builder
                .comment("Degrees of body yaw the NPC may turn per tick while chasing. The body, not the look vector, has to face the target.")
                .defineInRange("turnSpeed", 12.0D, 1.0D, 180.0D);
        TURN_SPEED_IN_RANGE = builder
                .comment("Degrees of body yaw per tick once the target is inside weapon range. Faster than turnSpeed so the swing is not stuck on the last step.")
                .defineInRange("turnSpeedInRange", 24.0D, 1.0D, 180.0D);
        MAX_YAW_ERROR = builder
                .comment("Largest body-yaw error, in degrees, that can still land a hit. The weapon arc is also required, so this cannot widen a narrow attack. 180 leaves the weapon arc in charge.")
                .defineInRange("maxYawError", 180.0D, 1.0D, 180.0D);
        FORCE_SWING_TICKS = builder
                .comment("Ticks spent in range but not yet facing before the NPC swings anyway. Stops a bad turn from freezing combat. 0 waits forever.")
                .defineInRange("forceSwingTicks", 10, 0, 200);
        RANGE_SCALE_BASELINE = builder
                .comment("Hitbox height, in blocks, that counts as 1.0x weapon range. A 1.8-tall NPC keeps the weapon's own reach.")
                .defineInRange("rangeScaleBaseline", 1.8D, 0.5D, 8.0D);
        RANGE_SCALE_MIN = builder
                .comment("Smallest reach multiplier from hitbox height. Ready for a shorter NPC later.")
                .defineInRange("rangeScaleMin", 0.25D, 0.05D, 4.0D);
        RANGE_SCALE_MAX = builder
                .comment("Largest reach multiplier from hitbox height. Ready for a taller NPC later.")
                .defineInRange("rangeScaleMax", 4.0D, 1.0D, 16.0D);
        TALL_AIM_FACTOR = builder
                .comment("When the NPC's hitbox is taller than the target's times this factor, the swing aims at the target's body instead of its eyes. A 1.8 NPC against a 1.8 target does not qualify at 1.5.")
                .defineInRange("tallAimFactor", 1.5D, 1.0D, 8.0D);
        TALL_PITCH_OFFSET = builder
                .comment("Extra degrees downward added to that tall aim. Ignored when the height test does not pass.")
                .defineInRange("tallPitchOffset", 15.0D, 0.0D, 80.0D);
        CANCEL_IF_OUT_OF_RANGE = builder
                .comment("Drop a wind-up that leaves weapon range before the hit frame. The swing still goes on cooldown.")
                .define("cancelIfOutOfRange", true);
        WINDUP_RANGE_SLACK = builder
                .comment("Extra blocks of reach allowed during wind-up so stopping to swing does not instantly cancel. The hit itself uses the real range.")
                .defineInRange("windupRangeSlack", 0.5D, 0.0D, 4.0D);
        WINDUP_KEEP_RANGE = builder
                .comment("During a wind-up the NPC keeps closing until the target is within this fraction of its reach, so a target drifting a little does not turn the swing into a sure miss (a real backstep still dodges it).")
                .defineInRange("windupKeepRange", 0.8D, 0.1D, 1.0D);
        MIN_GAP_REACH = builder
                .comment("Fighters keep the foe at least this fraction of their reach away (centre to centre), backing off when pressed closer, so they fight at weapon length instead of hugging.")
                .defineInRange("minGapReach", 0.55D, 0.0D, 1.0D);
        MIN_GAP = builder
                .comment("And never closer than their two bodies plus this many blocks.")
                .defineInRange("minGap", 0.5D, 0.0D, 3.0D);
        STOP_MOVING_DURING_WINDUP = builder
                .comment("Stop pathing once the target is in range during wind-up.")
                .define("stopMovingDuringWindup", true);
        FOOTWORK = builder
                .comment("Inside reach, while waiting to strike or recovering, NPCs now and then take a quick sidestep (stepping in at the edge of reach, giving ground when pressed) instead of standing like posts.")
                .define("footwork", true);
        SIDESTEP_CHANCE = builder
                .comment("Inside reach an NPC mostly stands; after each pause of 1.5 to 3.5 seconds, the chance it takes one quick sidestep.")
                .defineInRange("footworkChance", 0.35D, 0.0D, 1.0D);
        FOOTWORK_CROWD = builder
                .comment("Footwork is for duels: with this many other NPCs within 2.5 blocks, an NPC stands its ground instead of stepping (crowded lines turned into a scramble).")
                .defineInRange("crowdLimit", 3, 1, 20);
        DUEL_RANGE = builder
                .comment("Duel mode: no other NPC within this many blocks of an NPC and its foe. Duelists circle (steps kept to one side, shorter rests), and step back out of reach after their own blow, so trading hits takes timing. Otherwise line mode: hold the place, little footwork.")
                .defineInRange("duelRange", 5.0D, 0.0D, 32.0D);
        DUEL_STRAFE = builder
                .comment("In a duel the NPC never stands still (ToroQuest's guards): it drifts sideways at this input (0 to 1; a little more behind a raised shield) in its stance's direction, which changes now and then, while closing to the edge of reach.")
                .defineInRange("duelDrift", 0.25D, 0.0D, 1.0D);
        DUEL_BACKPEDAL = builder
                .comment("Chance a new duel stance is a backpedal: out of the foe's reach, then in again. After its own blow a duelist backpedals half the time.")
                .defineInRange("backpedalChance", 0.15D, 0.0D, 1.0D);
        SIDESTEP_SPEED = builder
                .comment("Sidestep input, 0 to 1. High enough that the legs walk; slower drifts read as sliding.")
                .defineInRange("sidestepSpeed", 1.0D, 0.1D, 1.0D);
        SHIELD_SIDESTEP_SCALE = builder
                .comment("When both fighters carry shields, sidesteps come this much less often: a standoff, not a dance.")
                .defineInRange("shieldSidestepScale", 0.5D, 0.0D, 1.0D);
        ATTACK_PAUSE = builder
                .comment("Extra ticks after every NPC swing before the next, on top of the weapon's own cooldown, so attacks are not spammed. Ancient Warfare used only the weapon cooldown (20 ticks over attack speed); 0 matches it. 6 gives a readable pace without idling.")
                .defineInRange("swingDelay", 6, 0, 40);
        PRESSURE_CHANCE = builder
                .comment("Against a raised shield, the chance each tick that an NPC without one starts its swing. Below 1 it picks its moments instead of hammering the shield as fast as its weapon allows.")
                .defineInRange("pressureChance", 0.12D, 0.01D, 1.0D);
        WINDUP_MOVE_SPEED = builder
                .comment("Path speed multiplier while winding up and still short of the target.")
                .defineInRange("windupMoveSpeed", 0.7D, 0.0D, 2.0D);
        APPROACH_SPEED = builder
                .comment("Path speed multiplier while closing to weapon range. A mob's speed counts twice (push and pace), so the multiplier's effect is squared: 1.0 is a player's walk, 1.12 a sprint.")
                .defineInRange("approachSpeed", 1.12D, 0.1D, 2.0D);
        PATH_RECALC_TICKS = builder
                .comment("Ticks between path recalculations while chasing.")
                .defineInRange("pathRecalcTicks", 8, 1, 40);
        REQUIRE_LINE_OF_SIGHT = builder
                .comment("Require line of sight before starting a swing and again on the hit frame.")
                .define("requireLineOfSight", true);
        BYPASS_HURT_COOLDOWN = builder
                .comment("NPC hits on other mobs ignore their short hurt invulnerability so fast weapons and fists land. Players always keep theirs.")
                .define("bypassHurtCooldown", true);
        COMBO_RESET_RATE = builder
                .comment("Combo step resets after this many weapon-cooldowns without a swing.")
                .defineInRange("comboResetRate", 3.0D, 0.5D, 20.0D);
        NPC_WINDUP_SCALE = builder
                .comment("Multiplies every NPC wind-up, so NPCs swing a little slower than a player with the same weapon. Recovery is unchanged, so the whole swing gets longer. 1.0 matches players.")
                .defineInRange("npcWindupScale", 1.25D, 0.5D, 3.0D);
        MAX_SPEED = builder
                .comment("The fastest an NPC moves, as a speed multiplier: 1.14 is a player's sprint (a mob's pace goes with the square of it). Fleeing, chasing, and every other goal are held to it; riding is the mount's own.")
                .defineInRange("maxSpeed", 1.14D, 0.5D, 3.0D);
        NPC_VS_NPC_DAMAGE = builder
                .comment("Damage an NPC deals to another NPC, as a fraction, so battles between them last longer. Fights with players are unchanged.")
                .defineInRange("npcVsNpcDamage", 0.7D, 0.05D, 4.0D);
        NPC_VS_NPC_KNOCKBACK = builder
                .comment("Knockback an NPC takes from another NPC's hit, as a fraction of vanilla's. Full knockback shoved the one hit out of reach mid-swing, so its swing missed and whoever hit first won.")
                .defineInRange("npcVsNpcKnockback", 0.3D, 0.0D, 2.0D);
        ENGAGE_RANGE_FACTOR = builder
                .comment("A swing starts only inside this fraction of the weapon's reach, so a target stepping back during the wind-up is still hit. The hit checks the full reach. 1.0 swings from the edge.")
                .defineInRange("engageRangeFactor", 0.85D, 0.5D, 1.0D);
        FEINTS = builder
                .comment("NPCs with a feint chance (set by specialized roles, zero otherwise) sometimes break off a swing halfway against a guarded target, like Better Combat's feint.")
                .define("feints", true);
        FEINT_RECOVERY = builder
                .comment("Ticks after a feint before the next swing can start.")
                .defineInRange("feintRecoveryTicks", 6, 0, 100);
        DUAL_WIELD = builder
                .comment("Two one-handed Better Combat weapons alternate main-hand and off-hand swings, as Better Combat does for players. Off-hand swings use that weapon's damage, speed, reach, and attacks.")
                .define("dualWield", true);
        SWEEP_EFFECTS = builder
                .comment("A landed sword slash wider than forwardAngle plays the vanilla sweep sound and crescent. Off by default; Better Combat Particle trails cover NPC swings when that mod is installed.")
                .define("sweepEffects", false);

        builder.pop();

        builder.push("sidearm");
        SIDEARM_ENABLED = builder
                .comment("An archer whose target gets this close swaps its bow for the best melee weapon in its backpack. Archers on hold position never swap.")
                .define("enabled", true);
        SIDEARM_CORNERED_RANGE = builder
                .comment("Distance, in blocks, at which an archer swaps to its sidearm.")
                .defineInRange("corneredRange", 3.0D, 0.5D, 16.0D);
        SIDEARM_RELEASE_RANGE = builder
                .comment("Distance, in blocks, the target must stay beyond before the bow comes back.")
                .defineInRange("releaseRange", 6.0D, 1.0D, 32.0D);
        SIDEARM_RELEASE_TICKS = builder
                .comment("Ticks the target must stay beyond releaseRange before the bow comes back.")
                .defineInRange("releaseTicks", 20, 0, 400);
        SIDEARM_SWAP_COOLDOWN = builder
                .comment("Ticks after any swap before the next one, so the NPC does not flip between weapons. A dead or lost target still brings the bow back at once.")
                .defineInRange("swapCooldownTicks", 40, 0, 400);
        builder.pop();

        builder.push("navigation");
        OPEN_DOORS = builder
                .comment("NPCs path through and open doors a hand can open (wooden and modded doors built on the vanilla door). Iron doors stay shut. Applies to NPCs loaded after the change.")
                .define("openDoors", true);
        CLOSE_DOORS = builder
                .comment("NPCs close a door behind them once they are through, as Ancient Warfare soldiers did.")
                .define("closeDoors", true);
        NIGHT_DOOR_RESTRICTION = builder
                .comment("At night NPCs do not open doors, as in Ancient Warfare, so they stay in or out until morning. NPCs that are following someone or fighting still use doors.")
                .define("nightDoorRestriction", true);
        builder.pop();

        builder.push("home");
        HOME_WANDER_RADIUS = builder
                .comment("Idle wandering stays within this many blocks of home. Ancient Warfare's default home area.")
                .defineInRange("wanderRadius", 5, 1, 64);
        HOME_DAY_RANGE = builder
                .comment("By day, an NPC farther than this from home, with nothing to fight, walks back.")
                .defineInRange("dayRange", 50.0D, 2.0D, 256.0D);
        OWNED_DAY_LEASH = builder
                .comment("By day, an owned NPC walking home stops once it is this close.")
                .defineInRange("ownedDayLeash", 20.0D, 1.0D, 256.0D);
        FACTION_DAY_LEASH = builder
                .comment("By day, a faction NPC walking home stops once it is this close.")
                .defineInRange("factionDayLeash", 30.0D, 1.0D, 256.0D);
        HOME_NIGHT_RANGE = builder
                .comment("Faction NPCs keep night hours: at night, one farther than this from home walks back. Owned soldiers do not, as in Ancient Warfare.")
                .defineInRange("nightRange", 5.0D, 1.0D, 256.0D);
        HOME_NIGHT_LEASH = builder
                .comment("At night, or when sheltering from rain, an NPC walking home stops once it is this close.")
                .defineInRange("nightLeash", 5.0D, 1.0D, 256.0D);
        HOME_RAIN_SHELTER = builder
                .comment("An NPC caught in the rain goes home and stays there until the rain stops.")
                .define("rainShelter", true);
        FACTION_ARCHER_POST = builder
                .comment("Out of combat, a faction archer holds a post within this many blocks of home. 0 turns it off.")
                .defineInRange("factionArcherPost", 3.0D, 0.0D, 32.0D);
        builder.pop();

        builder.push("shield");
        SHIELD_BLOCKING = builder
                .comment("NPCs raise an off-hand shield (vanilla or modded, anything that can shield-block) against a target that is swinging at them or aiming a bow at them, as in Ancient Warfare.")
                .define("enabled", true);
        SHIELD_BLOCK_RANGE = builder
                .comment("A swinging attacker must be within this many blocks to be blocked. Bow and crossbow aim is blocked at any range.")
                .defineInRange("blockRange", 4.0D, 1.0D, 16.0D);
        SHIELD_AIM_WATCH_RANGE = builder
                .comment("NPCs also raise the shield against anyone within this many blocks drawing a bow or crossbow at them, not only their own target. A player counts when looking straight at the NPC; never its owner. 0 turns this off.")
                .defineInRange("aimWatchRange", 24.0D, 0.0D, 64.0D);
        SHIELD_HOLD_TICKS = builder
                .comment("Ticks the shield stays up after the threat stops swinging or aiming.")
                .defineInRange("holdTicks", 40, 1, 200);
        SHIELD_GUARD_IN_RANGE = builder
                .comment("Guard stance: keep the shield up the whole time an enemy is within blockRange, not only while it swings. Vanilla shields only block after 5 ticks raised, so blocking only on the swing is close to a parry.")
                .define("guardInRange", true);
        SHIELD_MIN_BLOCK_TICKS = builder
                .comment("A raised shield is a commitment: it stays up at least this many ticks before the NPC may lower it to attack (vanilla shields only block after 5). An NPC never attacks with its shield up.")
                .defineInRange("minBlockTicks", 12, 0, 100);
        SHIELD_STRIKE_DELAY_TICKS = builder
                .comment("Ticks after lowering the shield before the NPC can begin a swing: the opening a player can punish.")
                .defineInRange("strikeDelayTicks", 6, 0, 40);
        SHIELD_INSTANT_BLOCK = builder
                .comment("A raised shield blocks from its first tick, for players and NPCs alike, so a shield that looks raised is raised. Vanilla waits five ticks. Mods like Responsive Shields do the same, and then this has nothing to do.")
                .define("instantBlock", true);
        SHIELD_RIPOSTE_TICKS = builder
                .comment("After blocking a hit, the NPC may strike back within this many ticks without waiting out minBlockTicks. It never lowers the shield while the target is mid-swing.")
                .defineInRange("riposteTicks", 20, 0, 100);
        SHIELD_STRIKE_CHANCE = builder
                .comment("Once the minimum block is over, the chance each tick that a guarding NPC lowers its shield to strike. Below 1, strikes come at a random beat, so they are hard to time and two guards do not open together.")
                .defineInRange("strikeChance", 0.15D, 0.01D, 1.0D);
        SHIELD_GUARD_POINTS = builder
                .comment("An NPC shield's guard, like stamina in Chivalry: each blocked hit drains it by the damage stopped (at least 1). At zero the guard breaks and the shield is knocked down for guardBreakTicks, so shield walls do not stalemate.")
                .defineInRange("guardMax", 30, 1, 200);
        SHIELD_GUARD_REGEN = builder
                .comment("Guard recovered per tick with the shield down (a quarter of this while it is up).")
                .defineInRange("guardRegen", 0.2D, 0.0D, 10.0D);
        SHIELD_GUARD_BREAK_TICKS = builder
                .comment("Ticks a broken guard keeps the shield down.")
                .defineInRange("guardBreakTicks", 40, 0, 400);
        SHIELD_ADVANCE_SPEED = builder
                .comment("Path speed multiplier while advancing behind a raised shield (Ancient Warfare walked at half speed).")
                .defineInRange("advanceSpeed", 0.7D, 0.1D, 2.0D);
        SHIELD_DISABLE_TICKS = builder
                .comment("Ticks an axe hit keeps an NPC's shield down, as it does a player's.")
                .defineInRange("axeDisableTicks", 100, 0, 400);
        SHIELD_NPC_AXE_CHANCE = builder
                .comment("Chance an NPC's axe knocks a shield out with a blocked hit. A player's axe always does (vanilla); a mob's did a quarter of the time, and an army of axemen disabling every shield made shield walls useless.")
                .defineInRange("npcAxeDisableChance", 0.25D, 0.0D, 1.0D);
        SHIELD_BASH_MODE = builder
                .comment("Which shields NPCs can bash with after blocking a hit, mirroring Spartan Shields' bash. SPARTAN: shields in spartanshields:shields_with_bash (nothing without that mod). ALL: any shield. OFF: no bashing.")
                .defineEnum("bashMode", NpcShieldBash.Mode.SPARTAN);
        SHIELD_BASH_WINDUP = builder
                .comment("Ticks an NPC braces behind its raised shield, with a warning sound, before the bash lands, so players can read it.")
                .defineInRange("bashWindupTicks", 6, 0, 40);
        SHIELD_BASH_RANGE = builder
                .comment("Reach of a bash, in blocks, plus half the target's width.")
                .defineInRange("bashRange", 3.0D, 0.5D, 8.0D);
        SHIELD_BASH_COOLDOWN = builder
                .comment("Ticks between bashes. Spartan Shields' default for a player.")
                .defineInRange("bashCooldownTicks", 30, 1, 1000);
        TOWER_BASH_COOLDOWN = builder
                .comment("Ticks between bashes with a tower shield. Spartan Shields' default for a player.")
                .defineInRange("towerBashCooldownTicks", 50, 1, 1000);
        SHIELD_BASH_RECOVERY = builder
                .comment("Ticks after a bash before the NPC raises its shield or swings again: the bash is the counter on its own, not the start of a combo.")
                .defineInRange("bashRecoveryTicks", 10, 0, 100);
        SHIELD_FOLLOW_THROUGH = builder
                .comment("Ticks after its own swing lands that an NPC keeps its shield down: the arm is still following through, as a player's is.")
                .defineInRange("followThroughTicks", 8, 0, 40);
        SHIELD_MOB_PATIENCE = builder
                .comment("Against a mob (no wind-up to read), a shield NPC baits: shield up until the mob's blow lands on it, then ripostes while the mob's attack recovers. If the mob has not struck for this many ticks, it takes the opening anyway. Mobs that cannot hit back (not after this NPC, out of reach) are just attacked.")
                .defineInRange("mobPatienceTicks", 40, 0, 400);
        SHIELD_PRESS_MIN_BLOCK = builder
                .comment("Against a foe that cannot block (no shield, or its shield knocked out) a shield NPC presses: its shield comes down after this many ticks up instead of minBlockTicks, and it strikes at pressStrikeChance per tick instead of strikeChance. It still guards; it just goes for the kill.")
                .defineInRange("pressMinBlockTicks", 4, 0, 100);
        SHIELD_PRESS_STRIKE = builder.defineInRange("pressStrikeChance", 0.5D, 0.01D, 1.0D);
        SHIELD_DUEL_BLOCK_MIN = builder
                .comment("Duels (ToroQuest style): once the foe has struck, the shield goes up in spells of between duelBlockMin and duelBlockMax ticks (shorter up close), then stays down for between duelOpenMin and duelOpenMax while the NPC attacks. Half the time a blow on the shield cuts a spell to half a second. Lines keep the steady guard.")
                .defineInRange("duelBlockMin", 20, 1, 400);
        SHIELD_DUEL_BLOCK_MAX = builder.defineInRange("duelBlockMax", 80, 1, 400);
        SHIELD_DUEL_OPEN_MIN = builder.defineInRange("duelOpenMin", 30, 0, 400);
        SHIELD_DUEL_OPEN_MAX = builder.defineInRange("duelOpenMax", 65, 0, 400);
        SHIELD_ARROW_BLOCK = builder
                .comment("An arrow in flight at the NPC from a foe puts its shield up for this many ticks.")
                .defineInRange("arrowBlockTicks", 25, 0, 200);
        builder.pop();

        builder.comment("Combat skill by roster rank, four values each: ordinary, elite, leader, elite leader. An NPC's AeSkill tag (0 to 3) overrides its rank.").push("skill");
        SKILL_FEINT_CHANCE = builder
                .comment("Chance, 0 to 1, that a swing at a guarded target is a feint. An NPC's own AeFeintChance overrides this.")
                .defineList("feintChance", List.of(0.0D, 0.1D, 0.2D, 0.3D), AncientEmpiresConfig::isChance);
        SKILL_BASH_CHANCE = builder
                .comment("Chance, 0 to 1, that a blocked hit is answered with a shield bash (see shield.bashMode).")
                .defineList("bashChancePerBlock", List.of(0.08D, 0.12D, 0.18D, 0.25D), AncientEmpiresConfig::isChance);
        SKILL_SHIELD_REACTION = builder
                .comment("Most ticks before the shield comes up against a threat; each block waits a random amount up to this.")
                .defineList("shieldReactionTicks", List.of(8, 6, 4, 2), value -> value instanceof Integer ticks && ticks >= 0 && ticks <= 40);
        SKILL_GUARD = builder
                .comment("Shield guard multiplier per skill tier (shield.guardMax times this): a leader's guard outlasts a grunt's.")
                .defineList("guardByTier", List.of(1.0D, 1.25D, 1.5D, 1.75D), value -> value instanceof Double d && d > 0.0D && d <= 10.0D);
        builder.pop();

        builder.comment("Ancient Warfare's civilians, workers, and traders fled instead of fighting.").push("civilians");
        CIVILIANS_FLEE = builder
                .comment("Settlers and workers (including traders, priests, bards, townsfolk) flee threats and fights only when cornered or levied. Off: every NPC fights.")
                .define("enabled", true);
        CIVILIAN_FLEE_RANGE = builder
                .comment("A civilian notices a visible threat this many blocks away, and runs home only if the danger is farther than this from home.")
                .defineInRange("fleeRange", 16.0D, 2.0D, 64.0D);
        CIVILIAN_FLEE_HEIGHT = builder
                .comment("Vertical reach of that look-around, in blocks.")
                .defineInRange("fleeHeight", 3.0D, 1.0D, 16.0D);
        CIVILIAN_FLEE_DISTANCE = builder
                .comment("How far, in blocks, a civilian runs from a threat when home is no refuge.")
                .defineInRange("fleeDistance", 16.0D, 2.0D, 64.0D);
        CIVILIAN_FLEE_SPEED = builder
                .comment("Speed multiplier while fleeing.")
                .defineInRange("fleeSpeed", 1.2D, 0.5D, 3.0D);
        CIVILIAN_STAY_AWAY = builder
                .comment("Ticks a civilian keeps away after losing sight of the threat.")
                .defineInRange("stayAwayTicks", 50, 0, 1200);
        CIVILIAN_SOLDIER_CALM = builder
                .comment("Ticks taken off that for each allied soldier nearby, as Ancient Warfare's fear level did. Also the shortest stay.")
                .defineInRange("soldierCalmTicks", 10, 0, 200);
        CIVILIAN_CORNER_RANGE = builder
                .comment("A threat this close, in blocks, with no way left to run, corners a civilian.")
                .defineInRange("cornerRange", 2.5D, 0.5D, 8.0D);
        CIVILIAN_CORNER_TICKS = builder
                .comment("Ticks spent cornered before a civilian stands its ground and fights with whatever it holds.")
                .defineInRange("cornerTicks", 20, 0, 200);
        CIVILIAN_STAND_GROUND = builder
                .comment("Ticks a cornered civilian fights before trying to run again.")
                .defineInRange("standGroundTicks", 100, 1, 1200);
        CIVILIAN_STAND_BREAK_RANGE = builder
                .comment("A cornered civilian stops fighting once its threat backs off this far, in blocks. It never chases.")
                .defineInRange("standBreakRange", 6.0D, 1.0D, 32.0D);
        CIVILIAN_PLAYER_THREAT_HEALTH = builder
                .comment("Civilians ignore a player who hits them (no running, no fighting back, no path for help) until their health drops below this fraction.")
                .defineInRange("playerThreatHealth", 0.5D, 0.0D, 1.0D);
        builder.pop();

        builder.comment("How well an NPC fights outside its trade, from the AncientTactics trial, and the emergency levy.").push("proficiency");
        ARCHER_MELEE = builder
                .comment("Melee damage multiplier for an archer: a bow or crossbow in hand or backpack, so a sidearm swap counts. AncientTactics used 0.5.")
                .defineInRange("archerMelee", 0.5D, 0.0D, 2.0D);
        SKIRMISHER_MELEE = builder
                .comment("Melee damage multiplier for a skirmisher: a throwing weapon or trident in hand or backpack. AncientTactics used 0.75.")
                .defineInRange("skirmisherMelee", 0.75D, 0.0D, 2.0D);
        LEVY_DAMAGE = builder
                .comment("Damage multiplier, melee and arrows, for a levied civilian. Stacks with the two above.")
                .defineInRange("levyDamage", 0.7D, 0.0D, 2.0D);
        LEVY_WINDUP = builder
                .comment("Extra wind-up multiplier for a levied civilian, on top of combat.npcWindupScale.")
                .defineInRange("levyWindupScale", 1.3D, 0.5D, 3.0D);
        LEVY_BREAK = builder
                .comment("Below this fraction of its health a levy breaks and runs like any civilian. Levies never bash or feint.")
                .defineInRange("levyBreakHealth", 0.3D, 0.0D, 1.0D);
        builder.pop();

        builder.push("allies");
        OWNER_DEFENSE = builder
                .comment("Owned NPCs attack whoever hurts their owner, and whatever their owner attacks, as in Ancient Warfare.")
                .define("ownerDefense", true);
        OWNER_DEFENSE_RANGE = builder
                .comment("How close, in blocks, an owned NPC must be to its owner to join the owner's fight.")
                .defineInRange("ownerDefenseRange", 32.0D, 4.0D, 128.0D);
        ALLY_ALERT_RANGE = builder
                .comment("When an NPC is hurt, allies within this many blocks (same faction, or same owner) that have no target attack the attacker. 0 turns it off.")
                .defineInRange("alertRange", 16.0D, 0.0D, 64.0D);
        ALLY_ALERT_HEIGHT = builder
                .comment("Vertical reach of that alert, in blocks, above and below the hurt NPC.")
                .defineInRange("alertHeight", 8.0D, 0.0D, 32.0D);
        builder.pop();

        builder.comment("Riders: faction cavalry and mounted archers, and owned NPCs put on a mount.").push("cavalry");
        CAVALRY_MOUNT_SPEED = builder
                .comment("Movement speed multiplier on a mount while an NPC rides it. Ancient Warfare used 1.5.")
                .defineInRange("mountSpeed", 1.5D, 0.5D, 4.0D);
        CAVALRY_REACH_BONUS = builder
                .comment("Extra melee reach, in blocks, from the saddle. A rider sits higher, so ground targets are farther away.")
                .defineInRange("reachBonus", 1.0D, 0.0D, 4.0D);
        CAVALRY_REMOUNT_RANGE = builder
                .comment("A rider thrown from a living mount walks back to it within this many blocks. Farther, the mount is given up.")
                .defineInRange("remountRange", 16.0D, 2.0D, 64.0D);
        CAVALRY_MOUNT_RANGE = builder
                .comment("How close, in blocks, a mount must be for the inventory Mount command.")
                .defineInRange("mountRange", 6.0D, 1.0D, 32.0D);
        builder.pop();

        builder.comment("Faction relations and player standing, from Ancient Warfare 2 Tweaked. The table is assets/ancientempires/npc/factions.json.").push("relations");
        RELATIONS_ENABLED = builder
                .comment("Factions fight the factions they list, and players (and their NPCs) whose standing with them is below zero. Off: faction NPCs ignore players and owned NPCs fight every faction.")
                .define("enabled", true);
        RELATIONS_REVENGE_RANGE = builder
                .comment("When a player (or their NPC) kills a faction NPC, its faction-mates within this many blocks turn on that player. Ancient Warfare used 50.")
                .defineInRange("revengeRange", 50.0D, 0.0D, 128.0D);
        RELATIONS_REVENGE_TICKS = builder
                .comment("How long that grudge lasts, in ticks. Ancient Warfare used 6000 (5 minutes).")
                .defineInRange("revengeTicks", 6000, 0, 72000);
        RELATIONS_STANDING_LIMIT = builder
                .comment("Standings stay between minus and plus this value.")
                .defineInRange("standingLimit", 1000, 1, 100000);
        RELATIONS_COUNT_CREATIVE = builder
                .comment("Whether kills by players in creative mode change standing and start grudges.")
                .define("countCreativeKills", false);
        builder.pop();

        builder.comment("Settlers' Posts: Dwarf Fortress style immigration. Checked once per in-game day, at the day change.").push("immigration");
        IMMIGRATION_ENABLED = builder
                .comment("Whether open posts draw settlers at all.")
                .define("enabled", true);
        IMMIGRATION_CHANCE = builder
                .comment("Chance each day that an open post with room and food gets a group.")
                .defineInRange("chance", 0.5D, 0.0D, 1.0D);
        IMMIGRATION_MAX_GROUP = builder
                .comment("Largest group in one day. Free beds and food can make it smaller.")
                .defineInRange("maxGroup", 3, 1, 16);
        IMMIGRATION_BED_RADIUS = builder
                .comment("Beds within this many blocks of the post count as room, less the owner's NPCs already there.")
                .defineInRange("bedRadius", 32, 4, 96);
        IMMIGRATION_SPAWN_DISTANCE = builder
                .comment("How far out, in blocks, newcomers appear before walking in.")
                .defineInRange("spawnDistance", 24.0D, 4.0D, 64.0D);
        IMMIGRATION_FOOD_PER_SETTLER = builder
                .comment("Food bundles each newcomer takes from the post's store. 0 makes beds the only limit.")
                .defineInRange("foodPerSettler", 1, 0, 64);
        IMMIGRATION_FOOD_CAPACITY = builder
                .comment("Food bundles a post can store.")
                .defineInRange("foodCapacity", 64, 1, 1024);
        builder.pop();

        builder.comment("Workers at worksites. The tool in a Worker-path NPC's main hand picks its trade (hoe, axe, pickaxe, shears, bow, fishing rod, bowl); it works its owner's nearest worksite of that kind by hand.").push("work");
        FARM_DEFAULT_SIZE = builder
                .comment("A new crop farm's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. Farmers hold a hoe.")
                .defineInRange("farmDefaultSize", 9, 1, 129);
        FARM_MAX_SIZE = builder
                .comment("Largest crop farm width or length: without an upgrade, with the medium upgrade, with the large one.")
                .defineList("farmMaxSize", List.of(15, 21, 27), AncientEmpiresConfig::isSize);
        WORK_SCAN_TICKS = builder
                .comment("Ticks between rebuilds of a worksite's to-do list. It is rebuilt only while a worker asks for work.")
                .defineInRange("scanTicks", 100, 10, 1200);
        WORK_CLAIM_TICKS = builder
                .comment("How long a worker's hold on a tile or animal lasts, in ticks, so two workers do not go for the same one.")
                .defineInRange("claimTicks", 400, 20, 2400);
        WORK_ACTION_TICKS = builder
                .comment("Ticks from a worker's swing to the action landing (digging takes a player's mining time instead).")
                .defineInRange("actionTicks", 12, 1, 100);
        WORK_SUPPLY_RESERVE = builder
                .comment("Supplies a worker keeps on hand (a farmer's seeds, a woodcutter's saplings, a rancher's feed); the rest goes into the worksite's store.")
                .defineInRange("supplyReserve", 16, 1, 64);
        WORK_SEARCH_RANGE = builder
                .comment("How far from its home, in blocks, a worker looks for its owner's worksite.")
                .defineInRange("searchRange", 32, 8, 96);
        WORK_WALK_SPEED = builder
                .comment("Walking speed while working, as a multiple of the NPC's speed.")
                .defineInRange("walkSpeed", 0.9D, 0.3D, 1.5D);
        WORK_AREA_REACH = builder
                .comment("How far, in blocks, a worksite's area may sit from its block (0: it must touch or contain the block).")
                .defineInRange("areaReach", 8, 0, 32);
        WORK_TOOL_WEAR = builder
                .comment("Chance each work action wears the worker's tool by one, as a player's would be (Unbreaking still applies). 1 is a player's rate, 0 never wears. Ancient Warfare's workers did not wear tools.")
                .defineInRange("toolWear", 0.2D, 0.0D, 1.0D);
        TREE_DEFAULT_SIZE = builder
                .comment("A new tree farm's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. Woodcutters hold an axe.")
                .defineInRange("treeDefaultSize", 13, 1, 129);
        TREE_MAX_SIZE = builder
                .comment("Largest tree farm width or length: without an upgrade, with the medium upgrade, with the large one.")
                .defineList("treeMaxSize", List.of(21, 27, 33), AncientEmpiresConfig::isSize);
        TREE_SPACING = builder
                .comment("Saplings are planted on a grid this many blocks apart, so grown trees have room.")
                .defineInRange("treeSpacing", 3, 1, 8);
        TREE_REACH = builder
                .comment("How far sideways from its base a tree's logs and branches can reach and still count as that tree.")
                .defineInRange("treeReach", 4, 1, 12);
        TREE_MAX_LOGS = builder
                .comment("Most logs one tree can have; bigger structures are not felled past this.")
                .defineInRange("treeMaxLogs", 128, 8, 1024);
        QUARRY_DEFAULT_SIZE = builder
                .comment("A new quarry pit's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. The pit starts in front of the block. Miners hold a pickaxe. Quarries take the quarry upgrades (Ancient Warfare: 16, 32, 64).")
                .defineInRange("quarryDefaultSize", 7, 1, 129);
        QUARRY_MAX_SIZE = builder
                .comment("Largest quarry pit width or length: without an upgrade, with the medium upgrade, with the large one.")
                .defineList("quarryMaxSize", List.of(16, 32, 64), AncientEmpiresConfig::isSize);
        QUARRY_MAX_DEPTH = builder
                .comment("Layers a quarry digs below its block before it is done.")
                .defineInRange("quarryMaxDepth", 32, 1, 256);
        QUARRY_STAIR_WIDTH = builder
                .comment("Width of the spiral stair a quarry leaves around its rim, in blocks. Wider is safer to walk; corners narrow so nobody hits their head.")
                .defineInRange("quarryStairWidth", 2, 1, 3);
        QUARRY_STAIR_STONE = builder
                .comment("Miners dig out stair blocks that are not plain stone (ore, dirt, sand, gravel, gaps) and lay stone they carry (the ancientempires:stair_stone item tag) in their place.")
                .define("quarryStairStone", true);
        RANCH_DEFAULT_SIZE = builder
                .comment("A new animal pen's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. Ranchers hold shears.")
                .defineInRange("ranchDefaultSize", 11, 1, 129);
        RANCH_MAX_SIZE = builder
                .comment("Largest animal pen width or length: without an upgrade, with the medium upgrade, with the large one.")
                .defineList("ranchMaxSize", List.of(21, 27, 33), AncientEmpiresConfig::isSize);
        RANCH_HERD_SIZE = builder
                .comment("Adults of each kind a pen keeps: ranchers breed up to it and cull past it.")
                .defineInRange("ranchHerdSize", 8, 2, 64);
        HUNT_DEFAULT_SIZE = builder
                .comment("A new hunting ground's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. Hunters hold a bow.")
                .defineInRange("huntDefaultSize", 49, 1, 129);
        HUNT_MAX_SIZE = builder
                .comment("Largest hunting ground width or length: without an upgrade, with the medium upgrade, with the large one.")
                .defineList("huntMaxSize", List.of(81, 97, 129), AncientEmpiresConfig::isSize);
        HUNT_KEEP_PER_KIND = builder
                .comment("Animals of each kind hunters leave in the ground, so it recovers.")
                .defineInRange("huntKeepPerKind", 2, 0, 16);
        HUNT_GAME_CAP = builder
                .comment("Game wanders in (from the biome's creature list, modded animals too) only while the ground holds fewer than this.")
                .defineInRange("huntGameCap", 6, 0, 64);
        HUNT_SPAWN_INTERVAL = builder
                .comment("Ticks between creatures wandering into a worked hunting ground. This sets a hunter's yield.")
                .defineInRange("huntSpawnInterval", 2400, 200, 72000);
        HUNT_SHOOT_RANGE = builder
                .comment("How far, in blocks, a hunter shoots from, with a clear line.")
                .defineInRange("huntShootRange", 16.0D, 4.0D, 48.0D);
        FISH_DEFAULT_SIZE = builder
                .comment("A new fishing dock's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. Fishers hold a fishing rod.")
                .defineInRange("fishDefaultSize", 9, 1, 129);
        FISH_MAX_SIZE = builder
                .comment("Largest fishing dock width or length: without an upgrade, with the medium upgrade, with the large one.")
                .defineList("fishMaxSize", List.of(17, 23, 29), AncientEmpiresConfig::isSize);
        FISH_RECAST = builder
                .comment("A fisher casts again into the same water after a catch, until its backpack is full. Off: it picks new water each time.")
                .define("fishRecast", true);
        COOK_TICKS = builder
                .comment("Ticks a cook takes to smoke one raw food, and the fuel it burns (a smoker takes 100). Cooks hold a bowl.")
                .defineInRange("cookTicks", 100, 10, 1200);
        COOK_BUNDLE_NUTRITION = builder
                .comment("Hunger of cooked food a cook packs into one food bundle. The crafting recipe takes about 37 and a water bottle.")
                .defineInRange("cookBundleNutrition", 24, 1, 200);
        COOK_BUNDLE_TARGET = builder
                .comment("A cook stops packing bundles once the kitchen holds this many.")
                .defineInRange("cookBundleTarget", 16, 1, 1024);
        builder.pop();

        builder.comment("Food and the Town Hall, as in Ancient Warfare. NPCs feed themselves; nothing here runs on a timer.").push("upkeep");
        UPKEEP_ENABLED = builder
                .comment("Whether owned NPCs need food. Each eats a ration a day from its backpack, and fetches more from its owner's Town Hall. Off: nobody goes hungry.")
                .define("enabled", true);
        UPKEEP_RANGE = builder
                .comment("NPCs fetch food from, and have their deaths recorded at, their owner's hall within this many blocks. Ancient Warfare used about 100.")
                .defineInRange("range", 100.0D, 8.0D, 256.0D);
        UPKEEP_RATION = builder
                .comment("Nutrition one NPC eats a day. Food counts by its hunger value (bread is 5).")
                .defineInRange("ration", 6, 1, 40);
        UPKEEP_BUNDLE_RATIONS = builder
                .comment("Daily rations in one food bundle.")
                .defineInRange("bundleRations", 8, 1, 64);
        UPKEEP_MEAL_INTERVAL = builder
                .comment("Ticks between an NPC's meals. 24000 is one Minecraft day. A new NPC's first meal is one interval after it appears.")
                .defineInRange("mealInterval", 24000, 1200, 240000);
        UPKEEP_CARRY_RATIONS = builder
                .comment("Rations an NPC takes from its hall when its backpack has no food, so it can eat on the road (campaigns) for a while.")
                .defineInRange("carryRations", 3, 1, 32);
        UPKEEP_DELIVERY = builder
                .comment("WALK: an NPC out of food walks to its hall to take some. TELEPORT: the food appears in its backpack, from the hall's store.")
                .defineEnum("delivery", Delivery.WALK);
        UPKEEP_REQUIRE_HALL = builder
                .comment("If on, an NPC with no food and no Town Hall in reach goes hungry. Off: only NPCs near a hall depend on it; others live off the land.")
                .define("requireHall", false);
        BURNING_PANIC = builder
                .comment("An NPC on fire and not fighting runs for water nearby, as vanilla mobs panic. Civilians always do.")
                .define("burningPanic", true);
        UPKEEP_FALLEN_RECORDS = builder
                .comment("Fallen NPCs a hall remembers for revival; the oldest go first.")
                .defineInRange("fallenRecords", 16, 1, 128);
        UPKEEP_KEEP_ON_DEATH = builder
                .comment("Like Ancient Warfare Tweaked's keep-equipment option: when a Town Hall records an owned NPC's death, what it keeps for revival instead of dropping. NONE, EQUIPMENT (armor and hands), BACKPACK, or ALL. Deaths no hall records drop everything as usual.")
                .defineEnum("keepOnDeath", KeepOnDeath.NONE);
        builder.pop();

        builder.comment("Perfect blocks, for players and NPCs alike: a shield raised just before a melee hit lands turns it aside whole, costing no guard, and the attacker is staggered (slowed, no swing). Held up longer it is an ordinary block.").push("perfectBlock");
        PARRY_ENABLED = builder
                .comment("Perfect blocks for players and NPCs.")
                .define("enabled", true);
        PARRY_WINDOW = builder
                .comment("A hit landing within this many ticks of raising the shield is a perfect block.")
                .defineInRange("windowTicks", 6, 1, 40);
        PARRY_COOLDOWN = builder
                .comment("A raise counts for a perfect block only this many ticks after the last raise, so tapping the shield does not farm them.")
                .defineInRange("cooldownTicks", 24, 0, 200);
        PARRY_VS_PLAYER = builder
                .comment("A player's swing shows no wind-up for an NPC to time against, so against players a shield NPC whose raised shield takes a hit turns it into a perfect block at this fraction of its skill's chanceByTier (a recruit almost never, a champion now and then), at most once per cooldownTicks.")
                .defineInRange("vsPlayerScale", 0.35D, 0.0D, 1.0D);
        PARRY_STAGGER = builder
                .comment("Ticks an attacker is staggered by a perfect block: slowed, and no swing.")
                .defineInRange("staggerTicks", 20, 0, 100);
        PARRY_CHANCE = builder
                .comment("Per skill tier (ordinary, elite, leader, elite leader): the chance a shield NPC with its shield down holds the raise for a perfect block against an NPC's visible wind-up, instead of raising at once. Players show no wind-up, so against them it raises at once.")
                .defineList("chanceByTier", List.of(0.05D, 0.15D, 0.3D, 0.45D), AncientEmpiresConfig::isChance);
        PARRY_ERROR = builder
                .comment("Per skill tier: up to how many ticks an NPC's perfect-block raise is mistimed. Late, the hit lands; early, an ordinary block.")
                .defineList("timingErrorByTier", List.of(6, 4, 3, 2), value -> value instanceof Integer ticks && ticks >= 0 && ticks <= 40);
        builder.pop();

        builder.comment("Command: ranked soldiers (sergeants, captains, generals) inspire the allied soldiers around them (more damage with any melee weapon, more armour). When a commander falls, the highest-ranked allied soldier near it takes command, weakened, and turns on the killer.").push("command");
        COMMAND_ENABLED = builder.comment("Commanders and their aura.").define("enabled", true);
        COMMAND_STRENGTH = builder.comment("Aura strength by rank: soldier, sergeant, captain, general (0 commands nobody). Faction leaders are captains, elite leaders generals.")
                .defineList("strengthByRank", List.of(0.0D, 0.5D, 1.0D, 1.25D), value -> value instanceof Double d && d >= 0.0D && d <= 4.0D);
        COMMAND_RADIUS = builder.comment("Blocks the aura reaches, by rank.")
                .defineList("radiusByRank", List.of(0.0D, 8.0D, 12.0D, 16.0D), value -> value instanceof Double d && d >= 0.0D && d <= 64.0D);
        COMMAND_PROMOTE_LEVELS = builder.comment("Level a player's soldier needs to be promoted to each rank with its baton: soldier, sergeant, captain, general.")
                .defineList("promoteLevels", List.of(1, 6, 12, 18), value -> value instanceof Integer i && i >= 1 && i <= 100);
        COMMAND_TICKS = builder.comment("Ticks between aura pulses; it lasts two pulses and a second, so it lapses soon after leaving.").defineInRange("auraTicks", 40, 10, 400);
        COMMAND_DAMAGE = builder.comment("Extra melee damage for the inspired, as a fraction (0.15 is 15% more).").defineInRange("damageBonus", 0.15D, 0.0D, 2.0D);
        COMMAND_ARMOR = builder.comment("Extra armour points for the inspired.").defineInRange("armorBonus", 2.0D, 0.0D, 20.0D);
        COMMAND_SUCCESSION_RANGE = builder.comment("Blocks from a fallen commander within which a successor is found.").defineInRange("successionRange", 16.0D, 2.0D, 64.0D);
        COMMAND_SUCCESSION_SCALE = builder.comment("A successor's aura, as a fraction of the fallen commander's.").defineInRange("successionScale", 0.6D, 0.1D, 1.0D);
        COMMAND_MIN_SCALE = builder.comment("The weakest a passed-down aura gets, however many times command passes.").defineInRange("minScale", 0.3D, 0.05D, 1.0D);
        builder.pop();

        builder.comment("Morale, Total War style: soldiers waver, rout, and shatter. Each soldier's morale (0 to 100) drifts toward what its situation is worth: its rank's base, a commander's aura, the odds around it, its wounds, a recent hit from behind, hunger, and routing allies; deaths and kills nearby shock it. Below routBelow it runs (as civilians do, fighting only when cornered) and rallies once recovered; the third rout shatters it for good. Fearless factions (factions.json) never break.").push("morale");
        MORALE_ENABLED = builder.comment("Morale on. Off, soldiers fight to the death as before.").define("enabled", true);
        MORALE_OWNED = builder.comment("Players' own soldiers have morale too.").define("ownedNpcs", true);
        MORALE_BASE = builder.comment("Base morale by skill tier: ordinary, elite, leader, elite leader.")
                .defineList("baseByTier", List.of(60.0D, 70.0D, 80.0D, 90.0D), value -> value instanceof Double d && d >= 0.0D && d <= 100.0D);
        MORALE_UPDATE_TICKS = builder.comment("Ticks between morale checks per soldier (staggered).").defineInRange("updateTicks", 20, 5, 200);
        MORALE_RANGE = builder.comment("Blocks around a soldier counted for the odds and routing allies.").defineInRange("range", 12.0D, 4.0D, 48.0D);
        MORALE_WAVER_BELOW = builder.comment("Below this, the soldier wavers (a warning badge).").defineInRange("waverBelow", 50.0D, 0.0D, 100.0D);
        MORALE_ROUT_BELOW = builder.comment("Below this, the soldier routs.").defineInRange("routAt", 12.0D, 0.0D, 100.0D);
        MORALE_RALLY_ABOVE = builder.comment("A routing soldier rallies once its morale is back above this.").defineInRange("rallyAbove", 40.0D, 0.0D, 100.0D);
        MORALE_MIN_ROUT_TICKS = builder.comment("A rout lasts at least this long before it can rally.").defineInRange("minRoutTicks", 200, 0, 6000);
        MORALE_SHATTER_AFTER = builder.comment("The rout that shatters a soldier: it cannot rally again, and faction soldiers leave the field.").defineInRange("shatterAfterRouts", 3, 1, 20);
        MORALE_FALL_RATE = builder.comment("Fraction of the gap closed per check when morale is falling.").defineInRange("fallSpeed", 0.15D, 0.01D, 1.0D);
        MORALE_RISE_RATE = builder.comment("Fraction of the gap closed per check when morale is rising (slower: heart is lost faster than it is regained).").defineInRange("riseRate", 0.1D, 0.01D, 1.0D);
        MORALE_OUTNUMBER = builder.comment("Morale from the odds: the full amount when alone against many (or many against none). Each side is weighed by health, so a giant counts for several men.").defineInRange("oddsWeight", 15.0D, 0.0D, 100.0D);
        MORALE_WOUNDS = builder.comment("Morale lost at the brink of death, in proportion to health lost.").defineInRange("woundsWeight", 20.0D, 0.0D, 100.0D);
        MORALE_FLANK = builder.comment("Morale lost while recently hit from behind or the side.").defineInRange("flankPenalty", 15.0D, 0.0D, 100.0D);
        MORALE_FLANK_TICKS = builder.comment("How long a hit from behind counts.").defineInRange("flankTicks", 60, 0, 1200);
        MORALE_HUNGER = builder.comment("Morale lost while hungry (upkeep).").defineInRange("hungerPenalty", 10.0D, 0.0D, 100.0D);
        MORALE_ROUTING_ALLY = builder.comment("Morale lost for each routing ally in range: panic spreads.").defineInRange("panicPerRouter", 4.0D, 0.0D, 100.0D);
        MORALE_ROUTING_CAP = builder.comment("The most morale routing allies can take away.").defineInRange("routingAllyCap", 24.0D, 0.0D, 100.0D);
        MORALE_INSPIRED = builder.comment("Morale from a commander's aura, times the commander's strength.").defineInRange("inspiredBonus", 20.0D, 0.0D, 100.0D);
        MORALE_COMMANDER_SHOCK = builder.comment("Shock when an allied commander falls.").defineInRange("commanderDeathShock", 35.0D, 0.0D, 100.0D);
        MORALE_COMMANDER_RANGE = builder.comment("Blocks within which a commander's death is felt.").defineInRange("commanderDeathRange", 32.0D, 0.0D, 128.0D);
        MORALE_ALLY_SHOCK = builder.comment("Shock when an ally falls close by.").defineInRange("allyDeathShock", 5.0D, 0.0D, 100.0D);
        MORALE_ALLY_RANGE = builder.comment("Blocks within which an ally's death is felt.").defineInRange("allyDeathRange", 8.0D, 0.0D, 64.0D);
        MORALE_KILL_BOOST = builder.comment("Morale gained by landing a kill.").defineInRange("killBoost", 6.0D, 0.0D, 100.0D);
        MORALE_SHOCK_DECAY = builder.comment("How much a shock wears off per check.").defineInRange("shockDecay", 1.5D, 0.0D, 100.0D);
        MORALE_HORN = builder.comment("A War Horn rally lifts its soldiers' morale to at least this, and rallies the routing (not the shattered).").defineInRange("hornRally", 60.0D, 0.0D, 100.0D);
        MORALE_SURRENDER = builder.comment("Cornered, badly hurt, routing faction soldiers may surrender: they kneel and nobody attacks them. Hit one and it shatters and runs.").define("surrender", true);
        MORALE_SURRENDER_HEALTH = builder.comment("Health fraction below which a cornered router may surrender.").defineInRange("surrenderHealth", 0.4D, 0.0D, 1.0D);
        MORALE_SURRENDER_CHANCE = builder.comment("Chance to surrender each time it is cornered (otherwise it fights its way out).").defineInRange("surrenderChance", 0.5D, 0.0D, 1.0D);
        MORALE_SURRENDER_RELEASE = builder.comment("With no enemy in range this long, a surrendered soldier gets up and leaves, shattered.").defineInRange("surrenderReleaseTicks", 600, 20, 24000);
        MORALE_DESERT = builder.comment("Shattered faction soldiers leave the field (despawn) once no player is near. Named ones stay.").define("shatteredDesert", true);
        MORALE_DESERT_RANGE = builder.comment("No player within this many blocks: a shattered faction soldier is gone.").defineInRange("desertRange", 48.0D, 8.0D, 256.0D);
        MORALE_RECOVER_TICKS = builder.comment("A player's shattered soldier recovers after this long with no enemy in range.").defineInRange("shatteredRecoverTicks", 1200, 20, 24000);
        MORALE_OWNED_BASE = builder.comment("Extra base morale for players' own soldiers: losing your army unexpectedly feels worse than routing an enemy feels good.").defineInRange("ownedBaseBonus", 10.0D, 0.0D, 100.0D);
        MORALE_OWNER_PRESENCE = builder.comment("Morale a player's soldiers draw from their owner fighting nearby, as from a general.").defineInRange("ownerPresenceBonus", 15.0D, 0.0D, 100.0D);
        MORALE_OWNER_RANGE = builder.comment("Blocks within which the owner's presence counts.").defineInRange("ownerPresenceRange", 16.0D, 0.0D, 64.0D);
        MORALE_CHASE_RANGE = builder.comment("Foot soldiers let routing enemies go once they are this far: they hold the field instead of scattering after them. Riders run them down at any range, as cavalry does. 0 chases everywhere.").defineInRange("chaseRange", 8.0D, 0.0D, 64.0D);
        builder.pop();

        builder.comment("Levels: NPCs (soldiers and workers; not settlers) earn XP and level up to maxLevel. A level is mostly skill: the skill section's four-entry lists are read along the levels (entry 1 at level 1, entry 4 at the cap), so every level counts. Bands of five: soldiers Recruit, Regular, Veteran, Champion; workers Apprentice, Journeyman, Expert, Master. Ancient Warfare capped at 10 and gave XP only for kills.").push("levels");
        LEVEL_ENABLED = builder.comment("NPCs earn XP and level up. Off, levels stay as they are.").define("enabled", true);
        LEVEL_MAX = builder.comment("The highest level.").defineInRange("maxLevel", 20, 4, 100);
        LEVEL_XP_BASE = builder.comment("XP from level 1 to 2.").defineInRange("xpBase", 20.0D, 1.0D, 100000.0D);
        LEVEL_XP_STEP = builder.comment("Extra XP needed for each level after that (20 then 35, 50, ...: 720 XP to level 10, 2945 to 20).").defineInRange("xpStep", 15.0D, 0.0D, 100000.0D);
        LEVEL_XP_DAMAGE = builder.comment("XP per point of damage dealt to an enemy, after armour (2 points is a heart).").defineInRange("xpPerDamage", 0.2D, 0.0D, 100.0D);
        LEVEL_XP_KILL = builder.comment("XP for a kill.").defineInRange("xpPerKill", 3.0D, 0.0D, 1000.0D);
        LEVEL_XP_KILL_LEVEL = builder.comment("Extra XP per level of a slain NPC (other creatures count as level 1).").defineInRange("xpPerVictimLevel", 0.5D, 0.0D, 1000.0D);
        LEVEL_XP_BLOCK = builder.comment("XP for a hit taken on the shield.").defineInRange("xpPerBlock", 0.4D, 0.0D, 1000.0D);
        LEVEL_XP_PERFECT = builder.comment("XP for a perfect block.").defineInRange("xpPerPerfectBlock", 2.0D, 0.0D, 1000.0D);
        LEVEL_XP_WORK = builder.comment("XP for each work action (a harvest, a log, a block dug, a catch, a dish).").defineInRange("xpPerWork", 0.1D, 0.0D, 1000.0D);
        LEVEL_HEALTH = builder.comment("Max health per level past the first (0.25: about two and a half hearts at 20).").defineInRange("healthPerLevel", 0.25D, 0.0D, 10.0D);
        LEVEL_DAMAGE = builder.comment("Melee and ranged damage per level past the first, as a fraction (0.01: 19% more at 20; Ancient Warfare gave 0.05).").defineInRange("damagePerLevel", 0.01D, 0.0D, 1.0D);
        LEVEL_WORK_SPEED = builder.comment("Workers: work speed per level past the first, as a fraction (0.03: 57% faster at 20; Ancient Warfare gave 0.05).").defineInRange("workSpeedPerLevel", 0.03D, 0.0D, 1.0D);
        LEVEL_TOOL_WEAR = builder.comment("Workers: tool wear cut per level past the first, as a fraction (0.02: 38% less at 20).").defineInRange("toolWearCutPerLevel", 0.02D, 0.0D, 0.1D);
        LEVEL_FACTION_MIN = builder.comment("Faction NPCs start at a random level by rank: the lowest for ordinary, elite, leader, elite leader.")
                .defineList("factionMinByTier", List.of(1, 8, 12, 16), value -> value instanceof Integer i && i >= 1 && i <= 100);
        LEVEL_FACTION_MAX = builder.comment("The highest starting level by rank.")
                .defineList("factionMaxByTier", List.of(5, 12, 16, 20), value -> value instanceof Integer i && i >= 1 && i <= 100);
        LEVEL_ANNOUNCE = builder.comment("Tell an owner on screen when one of their NPCs levels up.").define("announce", true);
        builder.pop();

        builder.comment("Night watch lights: soldiers awake at night or in the dark hold up a torch or lantern (the ancientempires:night_lights item tag) in a free off hand. Dynamic light mods light them.").push("nightLight");
        NIGHT_LIGHT_ENABLED = builder
                .comment("Soldiers carry lights at night. Owned ones use torches or lanterns from their backpack and put them back at dawn.")
                .define("enabled", true);
        NIGHT_LIGHT_FACTION_GUARDS = builder
                .comment("Faction soldiers with a free off hand are issued a torch or lantern for the night. It never drops or repacks.")
                .define("factionGuards", true);
        NIGHT_LIGHT_DARKNESS = builder
                .comment("Below this light level (0 to 15) it counts as dark by day too, as in caves.")
                .defineInRange("darkness", 7, 0, 16);
        builder.pop();

        builder.comment("The War Horn: blown by an owner to rally their soldiers (Rally), also levy their workers and settlers (Call to Arms), or send everyone back (Stand Down). Sneak-use switches the call.").push("horn");
        HORN_RANGE = builder
                .comment("Blocks from the horn within which the owner's NPCs answer it.")
                .defineInRange("range", 48.0D, 8.0D, 256.0D);
        HORN_COOLDOWN_TICKS = builder
                .comment("Ticks before the horn can be blown again.")
                .defineInRange("cooldownTicks", 100, 0, 1200);
        HORN_RALLY_TICKS = builder
                .comment("Ticks an NPC that answered a rally shows the alarm icon.")
                .defineInRange("rallyTicks", 600, 20, 6000);
        builder.pop();

        builder.comment("Medics: soldiers with bandages in the off hand.").push("medic");
        MEDIC_BANDAGE_HEAL = builder
                .comment("Health one bandage restores, for a medic's patient or a player using one.")
                .defineInRange("bandageHeal", 6.0D, 1.0D, 40.0D);
        MEDIC_RANGE = builder
                .comment("How far, in blocks, a medic looks for the wounded: allies, its owner, and itself.")
                .defineInRange("range", 16.0D, 2.0D, 48.0D);
        MEDIC_HEAL_BELOW = builder
                .comment("A medic tends those below this fraction of their health, the worst first.")
                .defineInRange("healBelow", 0.7D, 0.1D, 1.0D);
        MEDIC_BIND_TICKS = builder
                .comment("Ticks a medic spends binding a wound once beside the patient.")
                .defineInRange("bindTicks", 20, 1, 200);
        MEDIC_COOLDOWN_TICKS = builder
                .comment("Ticks between bandages.")
                .defineInRange("cooldownTicks", 40, 0, 1200);
        MEDIC_MELEE = builder
                .comment("Melee damage multiplier for a medic: its hands are for healing, so not every soldier should be one.")
                .defineInRange("meleeProficiency", 0.75D, 0.0D, 2.0D);
        builder.pop();

        builder.comment("Priests: workers holding a book raise the fallen recorded at their owner's Town Hall.").push("priest");
        PRIEST_REVIVE_TICKS = builder
                .comment("Ticks a priest prays at the hall before one of the fallen rises.")
                .defineInRange("reviveTicks", 100, 20, 2400);
        PRIEST_COST_ENABLED = builder
                .comment("Whether raising the fallen costs anything. The cost is taken from the hall's store.")
                .define("costEnabled", true);
        PRIEST_COST_ITEM = builder
                .comment("Item id of the cost.")
                .define("costItem", "minecraft:gold_ingot");
        PRIEST_COST_COUNT = builder
                .comment("How many of the cost item one raising takes.")
                .defineInRange("costCount", 2, 1, 64);
        builder.pop();

        builder.comment("Sleeping in beds at night, as villagers do.").push("sleep");
        SLEEP_ENABLED = builder
                .comment("Owned workers and settlers claim a free bed near home and sleep in it at night.")
                .define("enabled", true);
        SLEEP_SOLDIERS = builder
                .comment("Soldiers sleep too. Off by default: they stay on watch (room for day and night patrols later).")
                .define("soldiers", false);
        SLEEP_BED_RANGE = builder
                .comment("How far from its home, in blocks, an NPC looks for a free bed to claim.")
                .defineInRange("bedRange", 32, 4, 96);
        builder.pop();

        builder.comment("Hazard avoidance: paths and the spots NPCs pick for themselves stay clear of lava, fire, cliffs, and deep water.").push("hazard");
        HAZARD_MAX_FALL = builder
                .comment("Tallest drop, in blocks, an NPC's path may include. Vanilla lets a healthy mob in a fight take bigger ones, which is how mobs leap off cliffs.")
                .defineInRange("maxFall", 3, 0, 16);
        HAZARD_WATER_COST = builder
                .comment("Path cost of water, as vanilla's malus: higher walks further to stay dry. -1 forbids it.")
                .defineInRange("waterCost", 8.0D, -1.0D, 64.0D);
        HAZARD_FIRE_COST = builder
                .comment("Path cost next to fire (twice this through it). Vanilla uses 8.")
                .defineInRange("fireCost", 16.0D, -1.0D, 64.0D);
        HAZARD_AVOID_WATER_SPOTS = builder
                .comment("Fleeing, wandering, and repositioning never pick a spot in deep water.")
                .define("avoidWaterSpots", true);
        HAZARD_SAFE_SPOT_TRIES = builder
                .comment("Random spots tried before an NPC gives up looking for a safe one this tick.")
                .defineInRange("safeSpotTries", 10, 1, 40);
        builder.pop();

        builder.push("targeting");
        TARGET_LEGACY_SCAN = builder
                .comment("Use the two vanilla nearest-target goals instead of the shared scan. For comparison and as a fallback. Applies to NPCs loaded after the change.")
                .define("legacyScan", false);
        TARGET_SCAN_INTERVAL = builder
                .comment("Ticks between target scans for an NPC with no target. Each NPC is offset by its entity id so scans spread across ticks.")
                .defineInRange("scanInterval", 10, 1, 200);
        TARGET_RETARGET_INTERVAL = builder
                .comment("Ticks between rescans while an NPC already has a scanned target. 0 keeps the current target until it is lost.")
                .defineInRange("retargetInterval", 40, 0, 400);
        TARGET_SWITCH_MARGIN = builder
                .comment("A rescan only switches when the new target scores at least this much better (in effective blocks) than the current one.")
                .defineInRange("switchMargin", 3.0D, 0.0D, 32.0D);
        THREAT_SCORING = builder
                .comment("Threat scoring: a target's score is its distance less the bonuses below (in blocks); the lowest wins. Off: the nearest hostile in sight.")
                .define("threatScoring", true);
        THREAT_OWNER_WEIGHT = builder
                .comment("Bonus for an enemy attacking the NPC's owner.")
                .defineInRange("ownerThreatWeight", 16.0D, 0.0D, 64.0D);
        THREAT_ALLY_WEIGHT = builder
                .comment("Bonus for an enemy attacking an ally (same faction or owner).")
                .defineInRange("allyThreatWeight", 8.0D, 0.0D, 64.0D);
        THREAT_SELF_WEIGHT = builder
                .comment("Bonus for an enemy attacking the NPC itself.")
                .defineInRange("selfThreatWeight", 4.0D, 0.0D, 64.0D);
        THREAT_WOUNDED_WEIGHT = builder
                .comment("Bonus for a wounded enemy, scaled by its missing health (all of it at no health): finish them off.")
                .defineInRange("woundedWeight", 6.0D, 0.0D, 64.0D);
        THREAT_FACING_WEIGHT = builder
                .comment("Bonus for an enemy straight in front of the NPC, less to the side, none behind.")
                .defineInRange("facingWeight", 2.0D, 0.0D, 64.0D);
        THREAT_ROLE_WEIGHT = builder
                .comment("Role match: an archer's bonus for an enemy with a bow, crossbow, or throwing weapon.")
                .defineInRange("roleWeight", 6.0D, 0.0D, 64.0D);
        TARGET_SCAN_RANGE = builder
                .comment("Search radius, in blocks. The NPC's follow range still caps it.")
                .defineInRange("scanRange", 32.0D, 4.0D, 64.0D);
        TARGET_CACHE_TICKS = builder
                .comment("Ticks a shared candidate list stays valid for every NPC in the same 16x8x16 cell. 0 queries per NPC.")
                .defineInRange("cacheTicks", 5, 0, 40);
        TARGET_MAX_SIGHT_CHECKS = builder
                .comment("Line-of-sight checks per scan, nearest candidate first. The scan stops at the first visible one.")
                .defineInRange("maxSightChecks", 8, 1, 64);
        TARGET_DEBUG_STATS = builder
                .comment("Log target-scan counts per second to the server log every 10 seconds.")
                .define("debugStats", false);
        TARGET_MEMORY = builder
                .comment("Ticks an NPC searches where it last saw its target before giving up. The clock starts once it reaches that spot.")
                .defineInRange("memoryTicks", 100, 0, 1200);
        TARGET_PURSUIT = builder
                .comment("Most ticks an NPC chases a target it cannot see, counted from the last sighting, travel included.")
                .defineInRange("pursuitTicks", 600, 20, 6000);
        builder.pop();

        SPEC = builder.build();
    }

    public static double unarmedRange = 2.0D;
    public static int unarmedWindupTicks = 4;
    public static int unarmedCooldownTicks = 10;
    public static double unarmedDamageMultiplier = 1.0D;
    public static boolean unarmedPreferBetterCombat = true;

    public static double vanillaRange = 2.5D;
    public static double vanillaWindupFraction = 0.35D;
    public static int vanillaMinCooldownTicks = 6;

    public static boolean betterCombatEnabled = true;
    public static double upswingMultiplier = 0.5D;
    public static boolean readUpswingFromMod = true;
    public static int minAttackInterval = 2;

    public static double forwardAngle = 50.0D;
    public static double sweepAngle = 120.0D;
    public static double turnSpeed = 12.0D;
    public static double turnSpeedInRange = 24.0D;
    public static double maxYawError = 180.0D;
    public static int forceSwingTicks = 10;
    public static double rangeScaleBaseline = 1.8D;
    public static double rangeScaleMin = 0.25D;
    public static double rangeScaleMax = 4.0D;
    public static double tallAimFactor = 1.5D;
    public static double tallPitchOffset = 15.0D;
    public static boolean cancelIfOutOfRange = true;
    public static double windupRangeSlack = 0.5D;
    public static double windupKeepRange = 0.8D;
    public static double minGap = 0.5D;
    public static double minGapReach = 0.55D;
    public static boolean stopMovingDuringWindup = true;
    public static double windupMoveSpeed = 0.7D;
    public static boolean footwork = true;
    public static double sidestepChance = 0.35D;
    public static int footworkCrowd = 3;
    public static double duelRange = 5.0D;
    public static double duelStrafe = 0.25D;
    public static double duelBackpedalChance = 0.15D;
    public static double sidestepSpeed = 1.0D;
    public static double shieldSidestepScale = 0.5D;
    public static double pressureChance = 0.12D;
    public static int attackPause = 6;
    public static double approachSpeed = 1.12D;
    public static int pathRecalcTicks = 8;
    public static boolean requireLineOfSight = true;
    public static boolean bypassHurtCooldown = true;
    public static double comboResetRate = 3.0D;
    public static boolean sweepEffects = false;
    public static boolean dualWield = true;
    public static double npcWindupScale = 1.25D;
    public static double maxSpeed = 1.14D;
    public static double npcVsNpcDamage = 0.7D;
    public static double npcVsNpcKnockback = 0.3D;
    public static double engageRangeFactor = 0.85D;

    public static boolean sidearmEnabled = true;
    public static double sidearmCorneredRange = 3.0D;
    public static double sidearmReleaseRange = 6.0D;
    public static int sidearmReleaseTicks = 20;
    public static int sidearmSwapCooldown = 40;

    public static boolean openDoors = true;
    public static boolean closeDoors = true;
    public static boolean nightDoorRestriction = true;

    public static int homeWanderRadius = 5;
    public static double homeDayRange = 50.0D;
    public static double ownedDayLeash = 20.0D;
    public static double factionDayLeash = 30.0D;
    public static double homeNightRange = 5.0D;
    public static double homeNightLeash = 5.0D;
    public static boolean homeRainShelter = true;
    public static double factionArcherPost = 3.0D;

    public static boolean shieldBlocking = true;
    public static double shieldBlockRange = 4.0D;
    public static int shieldHoldTicks = 40;
    public static boolean shieldGuardInRange = true;
    public static int shieldMinBlockTicks = 12;
    public static int shieldStrikeDelayTicks = 6;
    public static int shieldRiposteTicks = 20;
    public static boolean shieldInstantBlock = true;
    public static double shieldStrikeChance = 0.15D;
    public static int shieldGuardPoints = 30;
    public static double shieldGuardRegen = 0.2D;
    public static int shieldGuardBreakTicks = 40;
    public static double shieldAdvanceSpeed = 0.7D;
    public static int shieldBashWindupTicks = 6;
    public static double shieldAimWatchRange = 24.0D;
    public static int shieldDisableTicks = 100;
    public static double shieldNpcAxeChance = 0.25D;
    public static NpcShieldBash.Mode shieldBashMode = NpcShieldBash.Mode.SPARTAN;
    /** Per skill tier: ordinary, elite, leader, elite leader. */
    public static List<? extends Double> feintChanceByTier = List.of(0.0D, 0.1D, 0.2D, 0.3D);
    public static List<? extends Double> bashChanceByTier = List.of(0.08D, 0.12D, 0.18D, 0.25D);
    public static List<? extends Integer> shieldReactionByTier = List.of(8, 6, 4, 2);
    public static List<? extends Double> guardByTier = List.of(1.0D, 1.25D, 1.5D, 1.75D);
    public static double shieldBashRange = 3.0D;
    public static int shieldBashCooldownTicks = 30;
    public static int towerBashCooldownTicks = 50;
    public static int shieldBashRecoveryTicks = 10;
    public static int shieldFollowThroughTicks = 8;
    public static int shieldMobPatienceTicks = 40;
    public static int shieldPressMinBlockTicks = 4;
    public static double shieldPressStrikeChance = 0.5D;
    public static int shieldDuelBlockMin = 20;
    public static int shieldDuelBlockMax = 80;
    public static int shieldDuelOpenMin = 30;
    public static int shieldDuelOpenMax = 65;
    public static int shieldArrowBlockTicks = 25;
    public static boolean feints = true;
    public static int feintRecoveryTicks = 6;

    public static boolean civiliansFlee = true;
    public static double civilianFleeRange = 16.0D;
    public static double civilianFleeHeight = 3.0D;
    public static double civilianFleeDistance = 16.0D;
    public static double civilianFleeSpeed = 1.2D;
    public static int civilianStayAwayTicks = 50;
    public static int civilianSoldierCalmTicks = 10;
    public static double civilianCornerRange = 2.5D;
    public static int civilianCornerTicks = 20;
    public static int civilianStandGroundTicks = 100;
    public static double civilianStandBreakRange = 6.0D;
    public static double civilianPlayerThreatHealth = 0.5D;

    public static double archerMeleeProficiency = 0.5D;
    public static double skirmisherMeleeProficiency = 0.75D;
    public static double levyDamage = 0.7D;
    public static double levyWindupScale = 1.3D;
    public static double levyBreakHealth = 0.3D;

    public static boolean ownerDefense = true;
    public static double ownerDefenseRange = 32.0D;
    public static double allyAlertRange = 16.0D;
    public static double allyAlertHeight = 8.0D;
    public static double cavalryMountSpeed = 1.5D;
    public static double cavalryReachBonus = 1.0D;
    public static double cavalryRemountRange = 16.0D;
    public static double cavalryMountRange = 6.0D;
    public static boolean relationsEnabled = true;
    public static double relationsRevengeRange = 50.0D;
    public static int relationsRevengeTicks = 6000;
    public static int relationsStandingLimit = 1000;
    public static boolean relationsCountCreative = false;
    public static boolean immigrationEnabled = true;
    public static double immigrationChance = 0.5D;
    public static int immigrationMaxGroup = 3;
    public static int immigrationBedRadius = 32;
    public static double immigrationSpawnDistance = 24.0D;
    public static int immigrationFoodPerSettler = 1;
    public static int immigrationFoodCapacity = 64;
    public static int farmDefaultSize = 9;
    public static List<? extends Integer> farmMaxSize = List.of(15, 21, 27);
    public static int workScanTicks = 100;
    public static int workClaimTicks = 400;
    public static int workActionTicks = 12;
    public static int workSupplyReserve = 16;
    public static int workSearchRange = 32;
    public static double workWalkSpeed = 0.9D;
    public static int workAreaReach = 8;
    public static int treeDefaultSize = 13;
    public static List<? extends Integer> treeMaxSize = List.of(21, 27, 33);
    public static int treeSpacing = 3;
    public static int treeReach = 4;
    public static int treeMaxLogs = 128;
    public static int quarryDefaultSize = 7;
    public static List<? extends Integer> quarryMaxSize = List.of(16, 32, 64);
    public static int quarryMaxDepth = 32;
    public static int quarryStairWidth = 2;
    public static boolean quarryStairStone = true;
    public static double workToolWear = 0.2D;
    public static int ranchDefaultSize = 11;
    public static List<? extends Integer> ranchMaxSize = List.of(21, 27, 33);
    public static int ranchHerdSize = 8;
    public static int huntDefaultSize = 49;
    public static List<? extends Integer> huntMaxSize = List.of(81, 97, 129);
    public static int huntKeepPerKind = 2;
    public static int huntGameCap = 6;
    public static int huntSpawnInterval = 2400;
    public static double huntShootRange = 16.0D;
    public static int fishDefaultSize = 9;
    public static List<? extends Integer> fishMaxSize = List.of(17, 23, 29);
    public static boolean fishRecast = true;
    public static int cookTicks = 100;
    public static int cookBundleNutrition = 24;
    public static int cookBundleTarget = 16;
    public static boolean upkeepEnabled = true;
    public static double upkeepRange = 100.0D;
    public static int upkeepRation = 6;
    public static int upkeepBundleRations = 8;
    public static int upkeepMealInterval = 24000;
    public static int upkeepCarryRations = 3;
    public static Delivery upkeepDelivery = Delivery.WALK;
    public static boolean upkeepRequireHall = false;
    public static boolean burningPanic = true;
    public static double medicBandageHeal = 6.0D;
    public static double medicRange = 16.0D;
    public static boolean parryEnabled = true;
    public static int parryWindowTicks = 6;
    public static int parryCooldownTicks = 24;
    public static double parryVsPlayerScale = 0.35D;
    public static int parryStaggerTicks = 20;
    public static List<? extends Double> parryChanceByTier = List.of(0.05D, 0.15D, 0.3D, 0.45D);
    public static List<? extends Integer> parryErrorByTier = List.of(6, 4, 3, 2);
    public static boolean commandEnabled = true;
    public static List<? extends Double> commandStrengthByRank = List.of(0.0D, 0.5D, 1.0D, 1.25D);
    public static List<? extends Double> commandRadiusByRank = List.of(0.0D, 8.0D, 12.0D, 16.0D);
    public static List<? extends Integer> commandPromoteLevels = List.of(1, 6, 12, 18);
    public static int commandAuraTicks = 40;
    public static double commandDamageBonus = 0.15D;
    public static double commandArmorBonus = 2.0D;
    public static double commandSuccessionRange = 16.0D;
    public static double commandSuccessionScale = 0.6D;
    public static double commandMinScale = 0.3D;
    public static boolean moraleEnabled = true;
    public static boolean moraleOwnedNpcs = true;
    public static List<? extends Double> moraleBaseByTier = List.of(60.0D, 70.0D, 80.0D, 90.0D);
    public static int moraleUpdateTicks = 20;
    public static double moraleRange = 12.0D;
    public static double moraleWaverBelow = 50.0D;
    public static double moraleRoutBelow = 12.0D;
    public static double moraleRallyAbove = 40.0D;
    public static int moraleMinRoutTicks = 200;
    public static int moraleShatterAfterRouts = 3;
    public static double moraleFallRate = 0.15D;
    public static double moraleRiseRate = 0.1D;
    public static double moraleOutnumberWeight = 15.0D;
    public static double moraleWoundWeight = 20.0D;
    public static double moraleFlankPenalty = 15.0D;
    public static int moraleFlankTicks = 60;
    public static double moraleHungerPenalty = 10.0D;
    public static double moraleRoutingAllyPenalty = 4.0D;
    public static double moraleRoutingAllyCap = 24.0D;
    public static double moraleInspiredBonus = 20.0D;
    public static double moraleCommanderDeathShock = 35.0D;
    public static double moraleCommanderDeathRange = 32.0D;
    public static double moraleAllyDeathShock = 5.0D;
    public static double moraleAllyDeathRange = 8.0D;
    public static double moraleKillBoost = 6.0D;
    public static double moraleShockDecay = 1.5D;
    public static double moraleHornRally = 60.0D;
    public static boolean moraleSurrender = true;
    public static double moraleSurrenderHealth = 0.4D;
    public static double moraleSurrenderChance = 0.5D;
    public static int moraleSurrenderReleaseTicks = 600;
    public static boolean moraleShatteredDesert = true;
    public static double moraleDesertRange = 48.0D;
    public static int moraleShatteredRecoverTicks = 1200;
    public static double moraleOwnedBaseBonus = 10.0D;
    public static double moraleOwnerPresenceBonus = 15.0D;
    public static double moraleOwnerPresenceRange = 16.0D;
    public static double moraleChaseRange = 8.0D;
    public static boolean levelEnabled = true;
    public static int levelMax = 20;
    public static double levelXpBase = 20.0D;
    public static double levelXpStep = 15.0D;
    public static double levelXpPerDamage = 0.2D;
    public static double levelXpPerKill = 3.0D;
    public static double levelXpPerKillLevel = 0.5D;
    public static double levelXpPerBlock = 0.4D;
    public static double levelXpPerPerfectBlock = 2.0D;
    public static double levelXpPerWork = 0.1D;
    public static double levelHealthPerLevel = 0.25D;
    public static double levelDamagePerLevel = 0.01D;
    public static double levelWorkSpeedPerLevel = 0.03D;
    public static double levelToolWearCutPerLevel = 0.02D;
    public static List<? extends Integer> levelFactionMin = List.of(1, 8, 12, 16);
    public static List<? extends Integer> levelFactionMax = List.of(5, 12, 16, 20);
    public static boolean levelAnnounce = true;
    public static boolean nightLightEnabled = true;
    public static boolean nightLightFactionGuards = true;
    public static int nightLightDarkness = 7;
    public static double hornRange = 48.0D;
    public static int hornCooldownTicks = 100;
    public static int hornRallyTicks = 600;
    public static double medicHealBelow = 0.7D;
    public static int medicBindTicks = 20;
    public static int medicCooldownTicks = 40;
    public static double medicMeleeProficiency = 0.75D;
    public static int priestReviveTicks = 100;
    public static boolean priestCostEnabled = true;
    public static String priestCostItem = "minecraft:gold_ingot";
    public static int priestCostCount = 2;
    public static boolean sleepEnabled = true;
    public static boolean sleepSoldiers = false;
    public static int sleepBedRange = 32;
    public static int hazardMaxFall = 3;
    public static double hazardWaterCost = 8.0D;
    public static double hazardFireCost = 16.0D;
    public static boolean hazardAvoidWaterSpots = true;
    public static int hazardSafeSpotTries = 10;
    public static boolean threatScoring = true;
    public static double threatOwnerWeight = 16.0D;
    public static double threatAllyWeight = 8.0D;
    public static double threatSelfWeight = 4.0D;
    public static double threatWoundedWeight = 6.0D;
    public static double threatFacingWeight = 2.0D;
    public static double threatRoleWeight = 6.0D;

    /** How an NPC out of food gets more from its Town Hall: walking there, or delivered to its backpack. */
    public enum Delivery
    {
        WALK, TELEPORT
    }
    public static int upkeepFallenRecords = 16;
    public static KeepOnDeath upkeepKeepOnDeath = KeepOnDeath.NONE;

    /** What an NPC whose death a Town Hall records keeps for revival instead of dropping. */
    public enum KeepOnDeath
    {
        NONE, EQUIPMENT, BACKPACK, ALL
    }

    public static boolean targetLegacyScan = false;
    public static int targetScanInterval = 10;
    public static int targetRetargetInterval = 40;
    public static double targetSwitchMargin = 3.0D;
    public static double targetScanRange = 32.0D;
    public static int targetCacheTicks = 5;
    public static int targetMaxSightChecks = 8;
    public static boolean targetDebugStats = false;
    public static int targetMemoryTicks = 100;
    public static int targetPursuitTicks = 600;

    private AncientEmpiresConfig()
    {
    }

    private static boolean isSize(Object value)
    {
        return value instanceof Integer size && size >= 1 && size <= 129;
    }

    private static boolean isChance(Object value)
    {
        return value instanceof Number number && number.doubleValue() >= 0.0D && number.doubleValue() <= 1.0D;
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event)
    {
        if (event.getConfig().getSpec() != SPEC)
        {
            return;
        }
        bake();
    }

    private static void bake()
    {
        unarmedRange = UNARMED_RANGE.get();
        unarmedWindupTicks = UNARMED_WINDUP_TICKS.get();
        unarmedCooldownTicks = UNARMED_COOLDOWN_TICKS.get();
        unarmedDamageMultiplier = UNARMED_DAMAGE_MULTIPLIER.get();
        unarmedPreferBetterCombat = UNARMED_PREFER_BETTER_COMBAT.get();

        vanillaRange = VANILLA_RANGE.get();
        vanillaWindupFraction = VANILLA_WINDUP_FRACTION.get();
        vanillaMinCooldownTicks = VANILLA_MIN_COOLDOWN_TICKS.get();

        betterCombatEnabled = BETTER_COMBAT_ENABLED.get();
        upswingMultiplier = UPSWING_MULTIPLIER.get();
        readUpswingFromMod = READ_UPSWING_FROM_MOD.get();
        minAttackInterval = MIN_ATTACK_INTERVAL.get();

        forwardAngle = FORWARD_ANGLE.get();
        sweepAngle = SWEEP_ANGLE.get();
        turnSpeed = TURN_SPEED.get();
        turnSpeedInRange = TURN_SPEED_IN_RANGE.get();
        maxYawError = MAX_YAW_ERROR.get();
        forceSwingTicks = FORCE_SWING_TICKS.get();
        rangeScaleBaseline = RANGE_SCALE_BASELINE.get();
        rangeScaleMin = RANGE_SCALE_MIN.get();
        rangeScaleMax = RANGE_SCALE_MAX.get();
        tallAimFactor = TALL_AIM_FACTOR.get();
        tallPitchOffset = TALL_PITCH_OFFSET.get();
        cancelIfOutOfRange = CANCEL_IF_OUT_OF_RANGE.get();
        windupRangeSlack = WINDUP_RANGE_SLACK.get();
        windupKeepRange = WINDUP_KEEP_RANGE.get();
        minGap = MIN_GAP.get();
        minGapReach = MIN_GAP_REACH.get();
        stopMovingDuringWindup = STOP_MOVING_DURING_WINDUP.get();
        windupMoveSpeed = WINDUP_MOVE_SPEED.get();
        footwork = FOOTWORK.get();
        sidestepChance = SIDESTEP_CHANCE.get();
        footworkCrowd = FOOTWORK_CROWD.get();
        duelRange = DUEL_RANGE.get();
        duelStrafe = DUEL_STRAFE.get();
        duelBackpedalChance = DUEL_BACKPEDAL.get();
        sidestepSpeed = SIDESTEP_SPEED.get();
        shieldSidestepScale = SHIELD_SIDESTEP_SCALE.get();
        pressureChance = PRESSURE_CHANCE.get();
        attackPause = ATTACK_PAUSE.get();
        approachSpeed = APPROACH_SPEED.get();
        pathRecalcTicks = PATH_RECALC_TICKS.get();
        requireLineOfSight = REQUIRE_LINE_OF_SIGHT.get();
        bypassHurtCooldown = BYPASS_HURT_COOLDOWN.get();
        comboResetRate = COMBO_RESET_RATE.get();
        sweepEffects = SWEEP_EFFECTS.get();
        dualWield = DUAL_WIELD.get();
        npcWindupScale = NPC_WINDUP_SCALE.get();
        maxSpeed = MAX_SPEED.get();
        npcVsNpcDamage = NPC_VS_NPC_DAMAGE.get();
        npcVsNpcKnockback = NPC_VS_NPC_KNOCKBACK.get();
        engageRangeFactor = ENGAGE_RANGE_FACTOR.get();

        sidearmEnabled = SIDEARM_ENABLED.get();
        sidearmCorneredRange = SIDEARM_CORNERED_RANGE.get();
        sidearmReleaseRange = SIDEARM_RELEASE_RANGE.get();
        sidearmReleaseTicks = SIDEARM_RELEASE_TICKS.get();
        sidearmSwapCooldown = SIDEARM_SWAP_COOLDOWN.get();

        openDoors = OPEN_DOORS.get();
        closeDoors = CLOSE_DOORS.get();
        nightDoorRestriction = NIGHT_DOOR_RESTRICTION.get();

        homeWanderRadius = HOME_WANDER_RADIUS.get();
        homeDayRange = HOME_DAY_RANGE.get();
        ownedDayLeash = OWNED_DAY_LEASH.get();
        factionDayLeash = FACTION_DAY_LEASH.get();
        homeNightRange = HOME_NIGHT_RANGE.get();
        homeNightLeash = HOME_NIGHT_LEASH.get();
        homeRainShelter = HOME_RAIN_SHELTER.get();
        factionArcherPost = FACTION_ARCHER_POST.get();

        shieldBlocking = SHIELD_BLOCKING.get();
        shieldBlockRange = SHIELD_BLOCK_RANGE.get();
        shieldHoldTicks = SHIELD_HOLD_TICKS.get();
        shieldGuardInRange = SHIELD_GUARD_IN_RANGE.get();
        shieldMinBlockTicks = SHIELD_MIN_BLOCK_TICKS.get();
        shieldStrikeDelayTicks = SHIELD_STRIKE_DELAY_TICKS.get();
        shieldRiposteTicks = SHIELD_RIPOSTE_TICKS.get();
        shieldInstantBlock = SHIELD_INSTANT_BLOCK.get();
        shieldStrikeChance = SHIELD_STRIKE_CHANCE.get();
        shieldGuardPoints = SHIELD_GUARD_POINTS.get();
        shieldGuardRegen = SHIELD_GUARD_REGEN.get();
        shieldGuardBreakTicks = SHIELD_GUARD_BREAK_TICKS.get();
        shieldAdvanceSpeed = SHIELD_ADVANCE_SPEED.get();
        shieldBashWindupTicks = SHIELD_BASH_WINDUP.get();
        shieldAimWatchRange = SHIELD_AIM_WATCH_RANGE.get();
        shieldDisableTicks = SHIELD_DISABLE_TICKS.get();
        shieldNpcAxeChance = SHIELD_NPC_AXE_CHANCE.get();
        shieldBashMode = SHIELD_BASH_MODE.get();
        feintChanceByTier = SKILL_FEINT_CHANCE.get();
        bashChanceByTier = SKILL_BASH_CHANCE.get();
        shieldReactionByTier = SKILL_SHIELD_REACTION.get();
        guardByTier = SKILL_GUARD.get();
        shieldBashRange = SHIELD_BASH_RANGE.get();
        shieldBashCooldownTicks = SHIELD_BASH_COOLDOWN.get();
        towerBashCooldownTicks = TOWER_BASH_COOLDOWN.get();
        shieldBashRecoveryTicks = SHIELD_BASH_RECOVERY.get();
        shieldFollowThroughTicks = SHIELD_FOLLOW_THROUGH.get();
        shieldMobPatienceTicks = SHIELD_MOB_PATIENCE.get();
        shieldPressMinBlockTicks = SHIELD_PRESS_MIN_BLOCK.get();
        shieldPressStrikeChance = SHIELD_PRESS_STRIKE.get();
        shieldDuelBlockMin = SHIELD_DUEL_BLOCK_MIN.get();
        shieldDuelBlockMax = Math.max(shieldDuelBlockMin, SHIELD_DUEL_BLOCK_MAX.get());
        shieldDuelOpenMin = SHIELD_DUEL_OPEN_MIN.get();
        shieldDuelOpenMax = Math.max(shieldDuelOpenMin, SHIELD_DUEL_OPEN_MAX.get());
        shieldArrowBlockTicks = SHIELD_ARROW_BLOCK.get();
        feints = FEINTS.get();
        feintRecoveryTicks = FEINT_RECOVERY.get();

        civiliansFlee = CIVILIANS_FLEE.get();
        civilianFleeRange = CIVILIAN_FLEE_RANGE.get();
        civilianFleeHeight = CIVILIAN_FLEE_HEIGHT.get();
        civilianFleeDistance = CIVILIAN_FLEE_DISTANCE.get();
        civilianFleeSpeed = CIVILIAN_FLEE_SPEED.get();
        civilianStayAwayTicks = CIVILIAN_STAY_AWAY.get();
        civilianSoldierCalmTicks = CIVILIAN_SOLDIER_CALM.get();
        civilianCornerRange = CIVILIAN_CORNER_RANGE.get();
        civilianCornerTicks = CIVILIAN_CORNER_TICKS.get();
        civilianStandGroundTicks = CIVILIAN_STAND_GROUND.get();
        civilianStandBreakRange = CIVILIAN_STAND_BREAK_RANGE.get();
        civilianPlayerThreatHealth = CIVILIAN_PLAYER_THREAT_HEALTH.get();

        archerMeleeProficiency = ARCHER_MELEE.get();
        skirmisherMeleeProficiency = SKIRMISHER_MELEE.get();
        levyDamage = LEVY_DAMAGE.get();
        levyWindupScale = LEVY_WINDUP.get();
        levyBreakHealth = LEVY_BREAK.get();

        ownerDefense = OWNER_DEFENSE.get();
        ownerDefenseRange = OWNER_DEFENSE_RANGE.get();
        allyAlertRange = ALLY_ALERT_RANGE.get();
        allyAlertHeight = ALLY_ALERT_HEIGHT.get();
        cavalryMountSpeed = CAVALRY_MOUNT_SPEED.get();
        cavalryReachBonus = CAVALRY_REACH_BONUS.get();
        cavalryRemountRange = CAVALRY_REMOUNT_RANGE.get();
        cavalryMountRange = CAVALRY_MOUNT_RANGE.get();
        relationsEnabled = RELATIONS_ENABLED.get();
        relationsRevengeRange = RELATIONS_REVENGE_RANGE.get();
        relationsRevengeTicks = RELATIONS_REVENGE_TICKS.get();
        relationsStandingLimit = RELATIONS_STANDING_LIMIT.get();
        relationsCountCreative = RELATIONS_COUNT_CREATIVE.get();
        immigrationEnabled = IMMIGRATION_ENABLED.get();
        immigrationChance = IMMIGRATION_CHANCE.get();
        immigrationMaxGroup = IMMIGRATION_MAX_GROUP.get();
        immigrationBedRadius = IMMIGRATION_BED_RADIUS.get();
        immigrationSpawnDistance = IMMIGRATION_SPAWN_DISTANCE.get();
        immigrationFoodPerSettler = IMMIGRATION_FOOD_PER_SETTLER.get();
        immigrationFoodCapacity = IMMIGRATION_FOOD_CAPACITY.get();
        farmDefaultSize = FARM_DEFAULT_SIZE.get();
        farmMaxSize = FARM_MAX_SIZE.get();
        workScanTicks = WORK_SCAN_TICKS.get();
        workClaimTicks = WORK_CLAIM_TICKS.get();
        workActionTicks = WORK_ACTION_TICKS.get();
        workSupplyReserve = WORK_SUPPLY_RESERVE.get();
        workSearchRange = WORK_SEARCH_RANGE.get();
        workWalkSpeed = WORK_WALK_SPEED.get();
        workAreaReach = WORK_AREA_REACH.get();
        treeDefaultSize = TREE_DEFAULT_SIZE.get();
        treeMaxSize = TREE_MAX_SIZE.get();
        treeSpacing = TREE_SPACING.get();
        treeReach = TREE_REACH.get();
        treeMaxLogs = TREE_MAX_LOGS.get();
        quarryDefaultSize = QUARRY_DEFAULT_SIZE.get();
        quarryMaxSize = QUARRY_MAX_SIZE.get();
        quarryMaxDepth = QUARRY_MAX_DEPTH.get();
        quarryStairWidth = QUARRY_STAIR_WIDTH.get();
        quarryStairStone = QUARRY_STAIR_STONE.get();
        workToolWear = WORK_TOOL_WEAR.get();
        ranchDefaultSize = RANCH_DEFAULT_SIZE.get();
        ranchMaxSize = RANCH_MAX_SIZE.get();
        ranchHerdSize = RANCH_HERD_SIZE.get();
        huntDefaultSize = HUNT_DEFAULT_SIZE.get();
        huntMaxSize = HUNT_MAX_SIZE.get();
        huntKeepPerKind = HUNT_KEEP_PER_KIND.get();
        huntGameCap = HUNT_GAME_CAP.get();
        huntSpawnInterval = HUNT_SPAWN_INTERVAL.get();
        huntShootRange = HUNT_SHOOT_RANGE.get();
        fishDefaultSize = FISH_DEFAULT_SIZE.get();
        fishMaxSize = FISH_MAX_SIZE.get();
        fishRecast = FISH_RECAST.get();
        cookTicks = COOK_TICKS.get();
        cookBundleNutrition = COOK_BUNDLE_NUTRITION.get();
        cookBundleTarget = COOK_BUNDLE_TARGET.get();
        upkeepEnabled = UPKEEP_ENABLED.get();
        upkeepRange = UPKEEP_RANGE.get();
        upkeepRation = UPKEEP_RATION.get();
        upkeepBundleRations = UPKEEP_BUNDLE_RATIONS.get();
        upkeepMealInterval = UPKEEP_MEAL_INTERVAL.get();
        upkeepCarryRations = UPKEEP_CARRY_RATIONS.get();
        upkeepDelivery = UPKEEP_DELIVERY.get();
        upkeepRequireHall = UPKEEP_REQUIRE_HALL.get();
        burningPanic = BURNING_PANIC.get();
        medicBandageHeal = MEDIC_BANDAGE_HEAL.get();
        medicRange = MEDIC_RANGE.get();
        parryEnabled = PARRY_ENABLED.get();
        parryWindowTicks = PARRY_WINDOW.get();
        parryCooldownTicks = PARRY_COOLDOWN.get();
        parryVsPlayerScale = PARRY_VS_PLAYER.get();
        parryStaggerTicks = PARRY_STAGGER.get();
        parryChanceByTier = PARRY_CHANCE.get();
        parryErrorByTier = PARRY_ERROR.get();
        commandEnabled = COMMAND_ENABLED.get();
        commandStrengthByRank = COMMAND_STRENGTH.get();
        commandRadiusByRank = COMMAND_RADIUS.get();
        commandPromoteLevels = COMMAND_PROMOTE_LEVELS.get();
        commandAuraTicks = COMMAND_TICKS.get();
        commandDamageBonus = COMMAND_DAMAGE.get();
        commandArmorBonus = COMMAND_ARMOR.get();
        commandSuccessionRange = COMMAND_SUCCESSION_RANGE.get();
        commandSuccessionScale = COMMAND_SUCCESSION_SCALE.get();
        commandMinScale = COMMAND_MIN_SCALE.get();
        moraleEnabled = MORALE_ENABLED.get();
        moraleOwnedNpcs = MORALE_OWNED.get();
        moraleBaseByTier = MORALE_BASE.get();
        moraleUpdateTicks = MORALE_UPDATE_TICKS.get();
        moraleRange = MORALE_RANGE.get();
        moraleWaverBelow = MORALE_WAVER_BELOW.get();
        moraleRoutBelow = MORALE_ROUT_BELOW.get();
        moraleRallyAbove = MORALE_RALLY_ABOVE.get();
        moraleMinRoutTicks = MORALE_MIN_ROUT_TICKS.get();
        moraleShatterAfterRouts = MORALE_SHATTER_AFTER.get();
        moraleFallRate = MORALE_FALL_RATE.get();
        moraleRiseRate = MORALE_RISE_RATE.get();
        moraleOutnumberWeight = MORALE_OUTNUMBER.get();
        moraleWoundWeight = MORALE_WOUNDS.get();
        moraleFlankPenalty = MORALE_FLANK.get();
        moraleFlankTicks = MORALE_FLANK_TICKS.get();
        moraleHungerPenalty = MORALE_HUNGER.get();
        moraleRoutingAllyPenalty = MORALE_ROUTING_ALLY.get();
        moraleRoutingAllyCap = MORALE_ROUTING_CAP.get();
        moraleInspiredBonus = MORALE_INSPIRED.get();
        moraleCommanderDeathShock = MORALE_COMMANDER_SHOCK.get();
        moraleCommanderDeathRange = MORALE_COMMANDER_RANGE.get();
        moraleAllyDeathShock = MORALE_ALLY_SHOCK.get();
        moraleAllyDeathRange = MORALE_ALLY_RANGE.get();
        moraleKillBoost = MORALE_KILL_BOOST.get();
        moraleShockDecay = MORALE_SHOCK_DECAY.get();
        moraleHornRally = MORALE_HORN.get();
        moraleSurrender = MORALE_SURRENDER.get();
        moraleSurrenderHealth = MORALE_SURRENDER_HEALTH.get();
        moraleSurrenderChance = MORALE_SURRENDER_CHANCE.get();
        moraleSurrenderReleaseTicks = MORALE_SURRENDER_RELEASE.get();
        moraleShatteredDesert = MORALE_DESERT.get();
        moraleDesertRange = MORALE_DESERT_RANGE.get();
        moraleShatteredRecoverTicks = MORALE_RECOVER_TICKS.get();
        moraleOwnedBaseBonus = MORALE_OWNED_BASE.get();
        moraleOwnerPresenceBonus = MORALE_OWNER_PRESENCE.get();
        moraleOwnerPresenceRange = MORALE_OWNER_RANGE.get();
        moraleChaseRange = MORALE_CHASE_RANGE.get();
        levelEnabled = LEVEL_ENABLED.get();
        levelMax = LEVEL_MAX.get();
        levelXpBase = LEVEL_XP_BASE.get();
        levelXpStep = LEVEL_XP_STEP.get();
        levelXpPerDamage = LEVEL_XP_DAMAGE.get();
        levelXpPerKill = LEVEL_XP_KILL.get();
        levelXpPerKillLevel = LEVEL_XP_KILL_LEVEL.get();
        levelXpPerBlock = LEVEL_XP_BLOCK.get();
        levelXpPerPerfectBlock = LEVEL_XP_PERFECT.get();
        levelXpPerWork = LEVEL_XP_WORK.get();
        levelHealthPerLevel = LEVEL_HEALTH.get();
        levelDamagePerLevel = LEVEL_DAMAGE.get();
        levelWorkSpeedPerLevel = LEVEL_WORK_SPEED.get();
        levelToolWearCutPerLevel = LEVEL_TOOL_WEAR.get();
        levelFactionMin = LEVEL_FACTION_MIN.get();
        levelFactionMax = LEVEL_FACTION_MAX.get();
        levelAnnounce = LEVEL_ANNOUNCE.get();
        nightLightEnabled = NIGHT_LIGHT_ENABLED.get();
        nightLightFactionGuards = NIGHT_LIGHT_FACTION_GUARDS.get();
        nightLightDarkness = NIGHT_LIGHT_DARKNESS.get();
        hornRange = HORN_RANGE.get();
        hornCooldownTicks = HORN_COOLDOWN_TICKS.get();
        hornRallyTicks = HORN_RALLY_TICKS.get();
        medicHealBelow = MEDIC_HEAL_BELOW.get();
        medicBindTicks = MEDIC_BIND_TICKS.get();
        medicCooldownTicks = MEDIC_COOLDOWN_TICKS.get();
        medicMeleeProficiency = MEDIC_MELEE.get();
        priestReviveTicks = PRIEST_REVIVE_TICKS.get();
        priestCostEnabled = PRIEST_COST_ENABLED.get();
        priestCostItem = PRIEST_COST_ITEM.get();
        priestCostCount = PRIEST_COST_COUNT.get();
        sleepEnabled = SLEEP_ENABLED.get();
        sleepSoldiers = SLEEP_SOLDIERS.get();
        sleepBedRange = SLEEP_BED_RANGE.get();
        hazardMaxFall = HAZARD_MAX_FALL.get();
        hazardWaterCost = HAZARD_WATER_COST.get();
        hazardFireCost = HAZARD_FIRE_COST.get();
        hazardAvoidWaterSpots = HAZARD_AVOID_WATER_SPOTS.get();
        hazardSafeSpotTries = HAZARD_SAFE_SPOT_TRIES.get();
        threatScoring = THREAT_SCORING.get();
        threatOwnerWeight = THREAT_OWNER_WEIGHT.get();
        threatAllyWeight = THREAT_ALLY_WEIGHT.get();
        threatSelfWeight = THREAT_SELF_WEIGHT.get();
        threatWoundedWeight = THREAT_WOUNDED_WEIGHT.get();
        threatFacingWeight = THREAT_FACING_WEIGHT.get();
        threatRoleWeight = THREAT_ROLE_WEIGHT.get();
        upkeepFallenRecords = UPKEEP_FALLEN_RECORDS.get();
        upkeepKeepOnDeath = UPKEEP_KEEP_ON_DEATH.get();

        targetLegacyScan = TARGET_LEGACY_SCAN.get();
        targetScanInterval = TARGET_SCAN_INTERVAL.get();
        targetRetargetInterval = TARGET_RETARGET_INTERVAL.get();
        targetSwitchMargin = TARGET_SWITCH_MARGIN.get();
        targetScanRange = TARGET_SCAN_RANGE.get();
        targetCacheTicks = TARGET_CACHE_TICKS.get();
        targetMaxSightChecks = TARGET_MAX_SIGHT_CHECKS.get();
        targetDebugStats = TARGET_DEBUG_STATS.get();
        targetMemoryTicks = TARGET_MEMORY.get();
        targetPursuitTicks = TARGET_PURSUIT.get();
    }
}
