package com.ancientempires.config;

import com.ancientempires.AncientEmpires;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

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

    private static final ForgeConfigSpec.BooleanValue SIDEARM_ENABLED;
    private static final ForgeConfigSpec.DoubleValue SIDEARM_CORNERED_RANGE;
    private static final ForgeConfigSpec.DoubleValue SIDEARM_RELEASE_RANGE;
    private static final ForgeConfigSpec.IntValue SIDEARM_RELEASE_TICKS;
    private static final ForgeConfigSpec.IntValue SIDEARM_SWAP_COOLDOWN;

    private static final ForgeConfigSpec.BooleanValue TARGET_LEGACY_SCAN;
    private static final ForgeConfigSpec.IntValue TARGET_SCAN_INTERVAL;
    private static final ForgeConfigSpec.IntValue TARGET_RETARGET_INTERVAL;
    private static final ForgeConfigSpec.DoubleValue TARGET_SWITCH_MARGIN;
    private static final ForgeConfigSpec.DoubleValue TARGET_SCAN_RANGE;
    private static final ForgeConfigSpec.IntValue TARGET_CACHE_TICKS;
    private static final ForgeConfigSpec.IntValue TARGET_MAX_SIGHT_CHECKS;
    private static final ForgeConfigSpec.BooleanValue TARGET_DEBUG_STATS;

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
        SWEEP_EFFECTS = builder
                .comment("A landed sword slash wider than forwardAngle plays the player sweep sound and crescent. Better Mob Combat swings use that mod's own effect instead.")
                .define("sweepEffects", true);

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
    public static boolean sweepEffects = true;

    public static boolean sidearmEnabled = true;
    public static double sidearmCorneredRange = 3.0D;
    public static double sidearmReleaseRange = 6.0D;
    public static int sidearmReleaseTicks = 20;
    public static int sidearmSwapCooldown = 40;

    public static boolean targetLegacyScan = false;
    public static int targetScanInterval = 10;
    public static int targetRetargetInterval = 40;
    public static double targetSwitchMargin = 3.0D;
    public static double targetScanRange = 32.0D;
    public static int targetCacheTicks = 5;
    public static int targetMaxSightChecks = 8;
    public static boolean targetDebugStats = false;

    private AncientEmpiresConfig()
    {
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

        sidearmEnabled = SIDEARM_ENABLED.get();
        sidearmCorneredRange = SIDEARM_CORNERED_RANGE.get();
        sidearmReleaseRange = SIDEARM_RELEASE_RANGE.get();
        sidearmReleaseTicks = SIDEARM_RELEASE_TICKS.get();
        sidearmSwapCooldown = SIDEARM_SWAP_COOLDOWN.get();

        targetLegacyScan = TARGET_LEGACY_SCAN.get();
        targetScanInterval = TARGET_SCAN_INTERVAL.get();
        targetRetargetInterval = TARGET_RETARGET_INTERVAL.get();
        targetSwitchMargin = TARGET_SWITCH_MARGIN.get();
        targetScanRange = TARGET_SCAN_RANGE.get();
        targetCacheTicks = TARGET_CACHE_TICKS.get();
        targetMaxSightChecks = TARGET_MAX_SIGHT_CHECKS.get();
        targetDebugStats = TARGET_DEBUG_STATS.get();
    }
}
