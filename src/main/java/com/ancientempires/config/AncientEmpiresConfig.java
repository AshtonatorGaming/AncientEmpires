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
    private static final ForgeConfigSpec.BooleanValue STOP_MOVING_DURING_WINDUP;
    private static final ForgeConfigSpec.DoubleValue WINDUP_MOVE_SPEED;
    private static final ForgeConfigSpec.DoubleValue APPROACH_SPEED;
    private static final ForgeConfigSpec.IntValue PATH_RECALC_TICKS;
    private static final ForgeConfigSpec.BooleanValue REQUIRE_LINE_OF_SIGHT;
    private static final ForgeConfigSpec.BooleanValue BYPASS_HURT_COOLDOWN;
    private static final ForgeConfigSpec.DoubleValue COMBO_RESET_RATE;
    private static final ForgeConfigSpec.BooleanValue SWEEP_EFFECTS;
    private static final ForgeConfigSpec.BooleanValue DUAL_WIELD;
    private static final ForgeConfigSpec.DoubleValue NPC_WINDUP_SCALE;
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
    private static final ForgeConfigSpec.DoubleValue SHIELD_AIM_WATCH_RANGE;
    private static final ForgeConfigSpec.IntValue SHIELD_DISABLE_TICKS;
    private static final ForgeConfigSpec.EnumValue<NpcShieldBash.Mode> SHIELD_BASH_MODE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> SKILL_FEINT_CHANCE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> SKILL_BASH_CHANCE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> SKILL_SHIELD_REACTION;
    private static final ForgeConfigSpec.DoubleValue SHIELD_BASH_RANGE;
    private static final ForgeConfigSpec.IntValue SHIELD_BASH_COOLDOWN;
    private static final ForgeConfigSpec.IntValue TOWER_BASH_COOLDOWN;
    private static final ForgeConfigSpec.IntValue SHIELD_BASH_RECOVERY;
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
    private static final ForgeConfigSpec.IntValue FARM_DEFAULT_RADIUS;
    private static final ForgeConfigSpec.IntValue FARM_MAX_RADIUS;
    private static final ForgeConfigSpec.IntValue FARM_SCAN_TICKS;
    private static final ForgeConfigSpec.IntValue FARM_CLAIM_TICKS;
    private static final ForgeConfigSpec.IntValue FARM_ACTION_TICKS;
    private static final ForgeConfigSpec.IntValue FARM_SEED_RESERVE;
    private static final ForgeConfigSpec.IntValue FARM_SEARCH_RANGE;
    private static final ForgeConfigSpec.DoubleValue FARM_WALK_SPEED;

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
        STOP_MOVING_DURING_WINDUP = builder
                .comment("Stop pathing once the target is in range during wind-up.")
                .define("stopMovingDuringWindup", true);
        WINDUP_MOVE_SPEED = builder
                .comment("Path speed multiplier while winding up and still short of the target.")
                .defineInRange("windupMoveSpeed", 0.7D, 0.0D, 2.0D);
        APPROACH_SPEED = builder
                .comment("Path speed multiplier while closing to weapon range.")
                .defineInRange("approachSpeed", 1.0D, 0.1D, 2.0D);
        PATH_RECALC_TICKS = builder
                .comment("Ticks between path recalculations while chasing.")
                .defineInRange("pathRecalcTicks", 8, 1, 40);
        REQUIRE_LINE_OF_SIGHT = builder
                .comment("Require line of sight before starting a swing and again on the hit frame.")
                .define("requireLineOfSight", true);
        BYPASS_HURT_COOLDOWN = builder
                .comment("NPC hits ignore the target's short hurt invulnerability so fast weapons and fists land.")
                .define("bypassHurtCooldown", true);
        COMBO_RESET_RATE = builder
                .comment("Combo step resets after this many weapon-cooldowns without a swing.")
                .defineInRange("comboResetRate", 3.0D, 0.5D, 20.0D);
        NPC_WINDUP_SCALE = builder
                .comment("Multiplies every NPC wind-up, so NPCs swing a little slower than a player with the same weapon. Recovery is unchanged, so the whole swing gets longer. 1.0 matches players.")
                .defineInRange("npcWindupScale", 1.25D, 0.5D, 3.0D);
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
        SHIELD_DISABLE_TICKS = builder
                .comment("Ticks an axe hit keeps an NPC's shield down, as it does a player's.")
                .defineInRange("axeDisableTicks", 100, 0, 400);
        SHIELD_BASH_MODE = builder
                .comment("Which shields NPCs can bash with after blocking a hit, mirroring Spartan Shields' bash. SPARTAN: shields in spartanshields:shields_with_bash (nothing without that mod). ALL: any shield. OFF: no bashing.")
                .defineEnum("bashMode", NpcShieldBash.Mode.SPARTAN);
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
                .comment("Ticks the shield stays down after a bash.")
                .defineInRange("bashRecoveryTicks", 10, 0, 100);
        builder.pop();

        builder.comment("Combat skill by roster rank, four values each: ordinary, elite, leader, elite leader. An NPC's AeSkill tag (0 to 3) overrides its rank.").push("skill");
        SKILL_FEINT_CHANCE = builder
                .comment("Chance, 0 to 1, that a swing at a guarded target is a feint. An NPC's own AeFeintChance overrides this.")
                .defineList("feintChance", List.of(0.0D, 0.1D, 0.2D, 0.3D), AncientEmpiresConfig::isChance);
        SKILL_BASH_CHANCE = builder
                .comment("Chance, 0 to 1, that a blocked hit is answered with a shield bash (see shield.bashMode).")
                .defineList("bashChance", List.of(0.3D, 0.45D, 0.6D, 0.75D), AncientEmpiresConfig::isChance);
        SKILL_SHIELD_REACTION = builder
                .comment("Most ticks before the shield comes up against a threat; each block waits a random amount up to this.")
                .defineList("shieldReactionTicks", List.of(8, 6, 4, 2), value -> value instanceof Integer ticks && ticks >= 0 && ticks <= 40);
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

        builder.comment("Workers at worksites. A Worker-path NPC with a hoe farms its owner's nearest crop farm by hand.").push("work");
        FARM_DEFAULT_RADIUS = builder
                .comment("A new crop farm's area, in blocks from the farm block to its edge (4 is 9 by 9). Sneak-click the block to change it.")
                .defineInRange("farmDefaultRadius", 4, 1, 16);
        FARM_MAX_RADIUS = builder
                .comment("Largest crop farm area, in blocks from the farm block (7 is 15 by 15; Ancient Warfare allowed 16 wide).")
                .defineInRange("farmMaxRadius", 7, 1, 16);
        FARM_SCAN_TICKS = builder
                .comment("Ticks between rebuilds of a farm's to-do list. It is rebuilt only while a farmer asks for work.")
                .defineInRange("scanTicks", 100, 10, 1200);
        FARM_CLAIM_TICKS = builder
                .comment("How long a farmer's hold on a tile lasts, in ticks, so two farmers do not walk to the same crop.")
                .defineInRange("claimTicks", 400, 20, 2400);
        FARM_ACTION_TICKS = builder
                .comment("Ticks from a farmer's swing to the till, plant, or harvest landing.")
                .defineInRange("actionTicks", 12, 1, 100);
        FARM_SEED_RESERVE = builder
                .comment("Seeds a farmer keeps on hand for planting; the rest goes into the farm's store.")
                .defineInRange("seedReserve", 16, 1, 64);
        FARM_SEARCH_RANGE = builder
                .comment("How far from its home, in blocks, a farmer looks for its owner's crop farm.")
                .defineInRange("searchRange", 32, 8, 96);
        FARM_WALK_SPEED = builder
                .comment("Walking speed while working, as a multiple of the NPC's speed.")
                .defineInRange("walkSpeed", 0.8D, 0.3D, 1.5D);
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
                .comment("A rescan only switches when the new target is at least this many blocks closer than the current one.")
                .defineInRange("switchMargin", 3.0D, 0.0D, 32.0D);
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
    public static boolean stopMovingDuringWindup = true;
    public static double windupMoveSpeed = 0.7D;
    public static double approachSpeed = 1.0D;
    public static int pathRecalcTicks = 8;
    public static boolean requireLineOfSight = true;
    public static boolean bypassHurtCooldown = true;
    public static double comboResetRate = 3.0D;
    public static boolean sweepEffects = false;
    public static boolean dualWield = true;
    public static double npcWindupScale = 1.25D;
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
    public static double shieldAimWatchRange = 24.0D;
    public static int shieldDisableTicks = 100;
    public static NpcShieldBash.Mode shieldBashMode = NpcShieldBash.Mode.SPARTAN;
    /** Per skill tier: ordinary, elite, leader, elite leader. */
    public static List<? extends Double> feintChanceByTier = List.of(0.0D, 0.1D, 0.2D, 0.3D);
    public static List<? extends Double> bashChanceByTier = List.of(0.3D, 0.45D, 0.6D, 0.75D);
    public static List<? extends Integer> shieldReactionByTier = List.of(8, 6, 4, 2);
    public static double shieldBashRange = 3.0D;
    public static int shieldBashCooldownTicks = 30;
    public static int towerBashCooldownTicks = 50;
    public static int shieldBashRecoveryTicks = 10;
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
    public static int farmDefaultRadius = 4;
    public static int farmMaxRadius = 7;
    public static int farmScanTicks = 100;
    public static int farmClaimTicks = 400;
    public static int farmActionTicks = 12;
    public static int farmSeedReserve = 16;
    public static int farmSearchRange = 32;
    public static double farmWalkSpeed = 0.8D;

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
        stopMovingDuringWindup = STOP_MOVING_DURING_WINDUP.get();
        windupMoveSpeed = WINDUP_MOVE_SPEED.get();
        approachSpeed = APPROACH_SPEED.get();
        pathRecalcTicks = PATH_RECALC_TICKS.get();
        requireLineOfSight = REQUIRE_LINE_OF_SIGHT.get();
        bypassHurtCooldown = BYPASS_HURT_COOLDOWN.get();
        comboResetRate = COMBO_RESET_RATE.get();
        sweepEffects = SWEEP_EFFECTS.get();
        dualWield = DUAL_WIELD.get();
        npcWindupScale = NPC_WINDUP_SCALE.get();
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
        shieldAimWatchRange = SHIELD_AIM_WATCH_RANGE.get();
        shieldDisableTicks = SHIELD_DISABLE_TICKS.get();
        shieldBashMode = SHIELD_BASH_MODE.get();
        feintChanceByTier = SKILL_FEINT_CHANCE.get();
        bashChanceByTier = SKILL_BASH_CHANCE.get();
        shieldReactionByTier = SKILL_SHIELD_REACTION.get();
        shieldBashRange = SHIELD_BASH_RANGE.get();
        shieldBashCooldownTicks = SHIELD_BASH_COOLDOWN.get();
        towerBashCooldownTicks = TOWER_BASH_COOLDOWN.get();
        shieldBashRecoveryTicks = SHIELD_BASH_RECOVERY.get();
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
        farmDefaultRadius = FARM_DEFAULT_RADIUS.get();
        farmMaxRadius = FARM_MAX_RADIUS.get();
        farmScanTicks = FARM_SCAN_TICKS.get();
        farmClaimTicks = FARM_CLAIM_TICKS.get();
        farmActionTicks = FARM_ACTION_TICKS.get();
        farmSeedReserve = FARM_SEED_RESERVE.get();
        farmSearchRange = FARM_SEARCH_RANGE.get();
        farmWalkSpeed = FARM_WALK_SPEED.get();

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
