package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.NpcUpkeep;
import com.ancientempires.block.NpcSpawnerBlockEntity;
import com.ancientempires.block.TownHallBlockEntity;
import com.ancientempires.client.NpcSkinPrefetch;
import com.ancientempires.combat.AttackSwing;
import com.ancientempires.combat.NpcBowAttackGoal;
import com.ancientempires.combat.NpcMeleeAttackGoal;
import com.ancientempires.combat.NpcSkirmishGoal;
import com.ancientempires.combat.SpartanThrowing;
import com.ancientempires.combat.WeaponClassifier;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.inventory.NpcMenu;
import com.ancientempires.gate.NpcGateGoal;
import com.ancientempires.gate.NpcGateNavigation;
import com.ancientempires.item.CaptureWandItem;
import com.ancientempires.item.SignalFlagItem;
import com.ancientempires.item.InsigniaItem;
import com.ancientempires.item.WarHornItem;
import com.ancientempires.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The one NPC entity ({@code ancientempires:npc}). Every role, trade, and
 * faction is this class: what an NPC does comes from its equipment, its path
 * ({@link NpcPath}), its roster row ({@link NpcRoster}), and its saved flags,
 * never from a subclass. The main hand is a vanilla equipment slot, so weapon
 * attributes still apply. Extra slots live in {@link NpcInventory}. Behaviour
 * lives in the goals {@link #registerGoals} adds and in helpers it owns (guard,
 * morale, mount, sidearm, target memory); this class keeps state, saving, and
 * the vanilla hooks they need.
 * An owned NPC has an owner uuid, a home, and the follow flag. A faction NPC
 * has a faction id and no owner. Opening a faction NPC does not claim it.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public class NpcEntity extends PathfinderMob
{
    private static final String OWNER_KEY = "Owner";
    private static final ThreadLocal<UUID> SPAWN_OWNER = new ThreadLocal<>();
    private static final ThreadLocal<String> SPAWN_ROSTER = new ThreadLocal<>();
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    static final EntityDataAccessor<Boolean> DATA_ATTACK_WINDOW = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<String> DATA_ATTACK_ANIMATION = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<String> DATA_PRESENTATION_ID = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<Integer> DATA_PRESENTATION_LENGTH = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> DATA_PRESENTATION_UPSWING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Integer> DATA_PRESENTATION_WINDUP = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> DATA_PRESENTATION_SERIAL = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FOLLOWING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CREATIVE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_HOLDING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_HOME = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<String> DATA_HOME_DIM = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SKIN = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    /** Empty for an owned or unclaimed NPC. A faction NPC stores an id such as {@code empire}. */
    private static final EntityDataAccessor<String> DATA_FACTION = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    /** Path id, for the client's civilian checks and the inventory screen. */
    private static final EntityDataAccessor<String> DATA_PATH = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    /** The trade the roster rank names, a fallback for the role label. */
    private static final EntityDataAccessor<String> DATA_TRADE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_LEVIED = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    /** Off-hand beat of an empty-hand fist combo. Cleared with the presentation, not the attack window. */
    static final EntityDataAccessor<Boolean> DATA_ATTACK_OFFHAND = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    /** 1 is a normal player body. Dwarf and giant eggs store a different scale. */
    private static final EntityDataAccessor<Float> DATA_BODY_SCALE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    /** {@link NpcStatus} ordinal, for the icon over the head. */
    private static final EntityDataAccessor<Byte> DATA_STATUS = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_STAGGERED = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    /** {@link NpcBadge} ordinal, for the badge beside the status icon. */
    private static final EntityDataAccessor<Byte> DATA_BADGE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BYTE);
    /** Level 1 to {@code levels.maxLevel}, and the XP toward the next, for the inventory. */
    static final EntityDataAccessor<Byte> DATA_LEVEL = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Float> DATA_XP = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    /** {@link NpcRank} ordinal, for the inventory label. */
    static final EntityDataAccessor<Byte> DATA_RANK = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BYTE);
    /** The banner a standard bearer carries (off hand or backpack), drawn on its back. */
    private static final EntityDataAccessor<ItemStack> DATA_BANNER = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final String FOLLOWING_KEY = "Following";
    private static final String CREATIVE_KEY = "Creative";
    private static final String HOME_KEY = "Home";
    private static final String HOME_DIM_KEY = "HomeDim";
    private static final String SKIN_KEY = "Skin";
    private static final String FACTION_KEY = "Faction";
    private static final int FACTION_LIMIT = 32;
    private static final String BODY_SCALE_KEY = "BodyScale";
    private static final String CUSTOM_NAME_KEY = "CustomName";
    /** Packed eggs only. The spawn merge cannot delete the placer's Owner key. */
    private static final String NO_OWNER_KEY = "AeNoOwner";
    /** Packed eggs only. Hover text for an unnamed NPC must not stick as the entity name. */
    private static final String UNNAMED_KEY = "AeUnnamed";
    /** Packed eggs only. The spawn merge cannot delete the home set at placement. */
    private static final String NO_HOME_KEY = "AeNoHome";
    private static final String SPAWNER_KEY = "AeSpawner";
    private static final String COMMAND_KEY = "AeCommand";
    private static final String SPAWNER_SLOT_KEY = "AeSpawnerSlot";
    private static final String GARRISON_KEY = "AeGarrison";
    /** The roster row the NPC was spawned from. Repack returns it to that scroll. */
    private static final String ROSTER_KEY = "AeRoster";
    private static final String FEINT_KEY = "AeFeintChance";
    private static final String SKILL_KEY = "AeSkill";
    private static final String PATH_KEY = "AePath";
    /** What earlier builds saved the path as. Read only. */
    private static final String[] LEGACY_PATH_KEYS = {"AeCalling", "AeRole"};
    private static final String LEVY_KEY = "AeLevy";
    private static final String REVENGE_KEY = "AeRevenge";

    private final NpcInventory inventory = new NpcInventory(this);
    private final NpcSidearm sidearm = new NpcSidearm(this);
    private final NpcTargetMemory targetMemory = new NpcTargetMemory(this);
    private final NpcMount mount = new NpcMount(this);
    /** Roster row id, or empty for an NPC from the generic scroll or one spawned before rows were saved. */
    private String rosterId = "";
    /** Creative player escorting a faction NPC in follow mode. Server only, not saved. */
    @Nullable
    private UUID followLeader;
    /** Set by the melee goal while it commits to a Better Mob Combat swing. */
    private boolean swingCommitted;
    /** The shield: raising, blocking, guard, bash, stagger. */
    private final NpcGuard guard = new NpcGuard(this);
    private final NpcMorale morale = new NpcMorale(this);
    private final NpcChain chain = new NpcChain(this);
    private final NpcProgress progress = new NpcProgress(this);
    private final NpcAttack attack = new NpcAttack(this);
    private final NpcHold hold = new NpcHold(this);
    private final NpcNeeds needs = new NpcNeeds(this);
    /** The NPC spawner that made it (its home), its slot there, and whether it is a garrison. Not packed. */
    @Nullable
    private BlockPos spawnerPos;
    private int spawnerSlot = -1;
    private boolean garrison;
    /** Command inherited from a fallen commander (AeCommand), and the aura reaching this NPC now. */
    private float inheritedCommand;
    private int inspiredUntil;
    private float inspiredStrength;
    /** Own feint chance; below zero means the skill tier decides. */
    private float feintChance = -1.0F;
    /** Cached skill tier from the roster rank; below zero until first asked. */
    private int skillTier = -1;
    /** Saved per-NPC skill tier ({@code AeSkill}); below zero when unset. */
    private int skillOverride = -1;
    /** On the night watch (NpcNightLight): a little harder-hitting and steadier. */
    private boolean onWatch;
    /** Cached roster rank; null until first asked. */
    @Nullable
    private String rosterSubtype;
    /** Tick until which a cornered civilian fights instead of running. */
    private int standGroundUntil;
    /** tickCount until which a horn rally shows; not saved. */
    private int rallyUntil;
    /** The path this NPC was given; null means the roster rank decides. */
    @Nullable
    private NpcPath chosenPath;
    private int proficiencyVersion = Integer.MIN_VALUE;
    private float meleeProficiency = 1.0F;
    /** Server only: a would-be target a civilian flees instead. */
    @Nullable
    private LivingEntity fearSource;
    /** Players this NPC holds a faction-mate's death against, with the game time it ends. Saved as {@code AeRevenge}. */
    private final Map<UUID, Long> revenge = new HashMap<>();
    /** Set for a death a Town Hall records, from {@code upkeep.keepOnDeath}: those items stay for the record. */
    private boolean keepEquipmentOnDeath;
    private boolean keepBackpackOnDeath;


    /** Bumped on any hand change. Not saved; a fresh load starts every cache cold. */
    private int handsVersion;

    /** Client render flag. The inventory preview sets this so the nametag is skipped for that draw. */
    private boolean nameplateSuppressed;

    /** Cache for {@link #getSkinTexture()}. Cleared when {@link #DATA_SKIN} changes. */
    @Nullable
    private ResourceLocation skinTexture;
    private boolean skinTextureResolved;

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level)
    {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        this.xpReward = 0;
        NpcSafety.applyPathCosts(this);
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            this.setDropChance(slot, 1.0F);
        }
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER, Optional.empty());
        this.entityData.define(DATA_ATTACK_WINDOW, false);
        this.entityData.define(DATA_ATTACK_ANIMATION, "");
        this.entityData.define(DATA_PRESENTATION_ID, "");
        this.entityData.define(DATA_PRESENTATION_LENGTH, 0);
        this.entityData.define(DATA_PRESENTATION_UPSWING, 0.0F);
        this.entityData.define(DATA_PRESENTATION_WINDUP, 0);
        this.entityData.define(DATA_PRESENTATION_SERIAL, 0);
        this.entityData.define(DATA_FOLLOWING, false);
        this.entityData.define(DATA_CREATIVE, false);
        this.entityData.define(DATA_HOLDING, false);
        this.entityData.define(DATA_HOME, Optional.empty());
        this.entityData.define(DATA_HOME_DIM, "");
        this.entityData.define(DATA_SKIN, "");
        this.entityData.define(DATA_FACTION, "");
        this.entityData.define(DATA_PATH, "");
        this.entityData.define(DATA_TRADE, "");
        this.entityData.define(DATA_LEVIED, false);
        this.entityData.define(DATA_ATTACK_OFFHAND, false);
        this.entityData.define(DATA_BODY_SCALE, 1.0F);
        this.entityData.define(DATA_STATUS, (byte) 0);
        this.entityData.define(DATA_STAGGERED, false);
        this.entityData.define(DATA_BADGE, (byte) 0);
        this.entityData.define(DATA_LEVEL, (byte) 1);
        this.entityData.define(DATA_RANK, (byte) 0);
        this.entityData.define(DATA_BANNER, ItemStack.EMPTY);
        this.entityData.define(DATA_XP, 0.0F);
    }

    /**
     * Paths may run through doors a hand can open, and through open doors.
     * Any door built on {@code DoorBlock} counts, modded ones included; iron
     * doors stay closed. Read once when the NPC is created. With {@code structures.npcUse}, paths also run
     * through the gates the NPC may open ({@link NpcGateNavigation}).
     */
    @Override
    protected PathNavigation createNavigation(Level level)
    {
        GroundPathNavigation navigation = new NpcGateNavigation(this, level);
        navigation.setCanOpenDoors(AncientEmpiresConfig.openDoors);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    /**
     * The tallest drop a path may include. Vanilla raises this for a healthy mob
     * with a target, which is how mobs leap off cliffs after the player; NPCs
     * keep {@code hazard.maxFall} in and out of a fight.
     */
    @Override
    public int getMaxFallDistance()
    {
        return Math.max(0, AncientEmpiresConfig.hazardMaxFall);
    }

    /**
     * While this NPC steers a mount, every goal that moves it moves the mount:
     * vanilla stops a mob-ridden mount's own movement but nothing drives it.
     * The mount paths with its own size and speed.
     */
    @Override
    public PathNavigation getNavigation()
    {
        if (this.getVehicle() instanceof Mob ridden && this.mount.steers(ridden))
        {
            return ridden.getNavigation();
        }
        return super.getNavigation();
    }

    @Override
    public void stopRiding()
    {
        Entity vehicle = this.getVehicle();
        super.stopRiding();
        if (vehicle != null && !this.level().isClientSide)
        {
            this.mount.onDismount(vehicle);
        }
    }

    /** A rider and its mount never count each other as enemies. */
    @Override
    public boolean isAlliedTo(Entity other)
    {
        if (other == this.getVehicle() || (other != null && other.getVehicle() == this))
        {
            return true;
        }
        return super.isAlliedTo(other);
    }

    /** Movement speed: a player's walking pace at a goal multiplier of 1.0. */
    public static final double BASE_SPEED = 0.32D;
    /** The old base, which made NPCs walk at 80% of a player's pace; updated on load. */
    private static final double OLD_BASE_SPEED = 0.28D;

    public static AttributeSupplier.Builder createAttributes()
    {
        // Player-like attack damage and attack speed so held weapons apply their
        // modifiers the same way they do for a player. Attack speed is not on mobs
        // by default, so it has to be added here.
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                // A mob's speed counts twice (Mob.setSpeed also sets the forward input), so its
                // pace goes with the square: 0.32 at a multiplier of 1.0 walks as fast as a player.
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                // No knockback beyond the 0.4 every hit gives, as for players and zombies; 0.4 here
                // shoved players 50% farther than they shoved NPCs, out of reach every exchange.
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D);
    }

    /**
     * The goals every NPC keeps; the rest come by path ({@link #refreshGoalSet}):
     * a fighter (soldier, or a levied civilian) carries the combat, order, and
     * target goals, a civilian the trades. An NPC only pays for goals it can use:
     * the goal selector asks every idle goal whether to start, every other tick.
     */
    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (AncientEmpiresConfig.openDoors)
        {
            // Ancient Warfare's NpcAIDoor: open the doors on the path, close them once past.
            // No flags, so it runs beside whatever goal is moving the NPC.
            this.goalSelector.addGoal(0, new OpenDoorGoal(this, AncientEmpiresConfig.closeDoors));
            this.goalSelector.addGoal(0, new NpcNightDoorsGoal(this));
        }
        // Gates as doors, for a player's own NPCs and only with gates.npcUse on (off by default).
        this.goalSelector.addGoal(0, new NpcGateGoal(this));
        // Civilians and routing soldiers run instead of fighting; it holds the move flag above the combat goals.
        this.goalSelector.addGoal(1, new NpcFleeGoal(this));
        this.goalSelector.addGoal(1, new NpcBurningGoal(this));
        this.goalSelector.addGoal(1, new NpcSunGoal(this));
        // Everyone keeps melee: a cornered civilian stands and fights with what it holds.
        this.goalSelector.addGoal(2, new NpcMeleeAttackGoal(this));
        this.goalSelector.addGoal(3, new NpcUpkeepGoal(this));
        this.goalSelector.addGoal(4, new NpcFollowOwnerGoal(this, 1.12D, 3.0F));
        this.goalSelector.addGoal(4, new NpcSleepGoal(this));
        this.goalSelector.addGoal(5, new NpcReturnHomeGoal(this, 0.9D));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // Hurt: alert allies (civilians too), and a cornered civilian's stand.
        this.targetSelector.addGoal(3, new NpcHurtByTargetGoal(this));
    }

    /** Goals only a fighter (a soldier, or a levied civilian) can use. */
    private void addFighterGoals(List<Goal> goals, List<Goal> targets)
    {
        // A surrendered soldier kneels; it outranks every goal that moves.
        goals.add(this.addGoal(0, new NpcSurrenderGoal(this)));
        // A bow keeps this goal for the whole fight. Melee does not run while it does.
        goals.add(this.addGoal(1, new NpcBowAttackGoal(this)));
        // A Spartan throwing weapon keeps the skirmish goal; a drawn sidearm hands the fight to melee.
        goals.add(this.addGoal(1, new NpcSkirmishGoal(this)));
        // No flags: blocks beside the melee goal, lowered while the NPC swings.
        goals.add(this.addGoal(1, new NpcShieldGoal(this)));
        goals.add(this.addGoal(1, new NpcMedicGoal(this)));
        goals.add(this.addGoal(3, new NpcOrderGoal(this)));
        goals.add(this.addGoal(3, new NpcFormationGoal(this)));
        goals.add(this.addGoal(3, new NpcHoldPositionGoal(this, 0.9D)));
        // Ancient Warfare's order: the owner's fight first, then the NPC's own, then scanning.
        targets.add(this.addTarget(1, new NpcOwnerDefenseGoal(this, NpcOwnerDefenseGoal.Mode.DEFEND)));
        targets.add(this.addTarget(2, new NpcOwnerDefenseGoal(this, NpcOwnerDefenseGoal.Mode.ASSIST)));
        if (AncientEmpiresConfig.targetLegacyScan)
        {
            // The selectors only count candidates for the debug stats; the filter is unchanged.
            targets.add(this.addTarget(4, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                    living -> NpcTargetCache.countLegacyCandidate())));
            targets.add(this.addTarget(5, new NearestAttackableTargetGoal<>(this, NpcEntity.class, 10, true, false,
                    living -> living instanceof NpcEntity other && this.acceptsTarget(other) && NpcTargetCache.countLegacyCandidate())));
        }
        else
        {
            targets.add(this.addTarget(4, new NpcTargetGoal(this)));
        }
    }

    /** Goals only a civilian (settler or worker, not levied) can use: the trades. */
    private void addCivilianGoals(List<Goal> goals)
    {
        goals.add(this.addGoal(4, new NpcPriestGoal(this)));
        goals.add(this.addGoal(4, new NpcGovernorGoal(this)));
        // Every hands-on trade (farmer, woodcutter, miner, rancher, hunter, fisher, cook) as one goal: the tool picks it.
        goals.add(this.addGoal(4, new NpcJobGoal(this)));
    }

    private Goal addGoal(int priority, Goal goal)
    {
        this.goalSelector.addGoal(priority, goal);
        return goal;
    }

    private Goal addTarget(int priority, Goal goal)
    {
        this.targetSelector.addGoal(priority, goal);
        return goal;
    }

    /** Which path's goals are registered now: null for none yet, true for a fighter's, false for a civilian's. */
    @Nullable
    private Boolean fighterGoals;
    private final List<Goal> pathGoals = new ArrayList<>();
    private final List<Goal> pathTargets = new ArrayList<>();

    /**
     * Swaps the path's goals in when the NPC becomes a fighter or a civilian
     * (its path chosen or read from its roster row, a levy called or
     * dismissed). One compare a tick otherwise.
     */
    private void refreshGoalSet()
    {
        boolean fighter = this.getNpcPath().combat() || this.isLevied();
        if (this.fighterGoals != null && this.fighterGoals == fighter)
        {
            return;
        }
        this.pathGoals.forEach(this.goalSelector::removeGoal);
        this.pathTargets.forEach(this.targetSelector::removeGoal);
        this.pathGoals.clear();
        this.pathTargets.clear();
        if (fighter)
        {
            this.addFighterGoals(this.pathGoals, this.pathTargets);
        }
        else
        {
            this.addCivilianGoals(this.pathGoals);
        }
        this.fighterGoals = fighter;
    }

    /**
     * Same side: the same faction for faction NPCs, the same owner for owned ones.
     * Allies answer each other's alerts.
     */
    public boolean isAlly(NpcEntity other)
    {
        if (other == this)
        {
            return false;
        }
        if (this.isFactionNpc())
        {
            return this.getFaction().equals(other.getFaction());
        }
        return sameOwner(this, other);
    }

    /**
     * What the automatic scan may pick: any monster, or an NPC that
     * {@link #acceptsTarget} allows. Vanilla's combat checks run after this.
     */
    boolean isScanTarget(LivingEntity living)
    {
        if (living.getFirstPassenger() instanceof NpcEntity)
        {
            // An NPC's mount (a gremlin's spider, an undead's skeleton horse); the rider is the target.
            return false;
        }
        if (living instanceof Player player)
        {
            return this.isHostileTo(player);
        }
        if (living instanceof NpcEntity target)
        {
            return this.acceptsTarget(target);
        }
        if (this.isFactionNpc() && AncientEmpiresConfig.relationsEnabled)
        {
            return NpcFactions.hunts(this.getFaction(), living);
        }
        return living instanceof Enemy;
    }

    /**
     * Automatic scans only. A same-owner pair is never a target. Factions attack
     * the factions on their hostile list, and owned NPCs of players they are
     * hostile to; an owned NPC attacks faction NPCs hostile to its owner.
     * A hit still uses the hurt-by goal, which only rejects a same-owner attacker.
     */
    private boolean acceptsTarget(NpcEntity target)
    {
        if (target == this || !target.isAlive() || sameOwner(this, target) || target.morale.isSurrendered())
        {
            return false;
        }
        if (!this.chases(target))
        {
            return false;
        }
        if (this.isFactionNpc())
        {
            if (target.isFactionNpc())
            {
                return NpcFactions.hostile(this.getFaction(), target.getFaction());
            }
            return target.isOwnedNpc() && this.isHostileToPlayer(target.getOwner());
        }
        if (!this.isOwnedNpc() || !target.isFactionNpc())
        {
            return false;
        }
        return !AncientEmpiresConfig.relationsEnabled || target.isHostileToPlayer(this.getOwner());
    }

    /**
     * Whether this NPC goes after a target: anyone fighting, and a routing NPC
     * only within {@code morale.chaseRange} on foot, so a won fight does not
     * scatter the winners across the field; riders run routers down.
     */
    boolean chases(LivingEntity target)
    {
        if (!(target instanceof NpcEntity npc) || !npc.morale.isBroken() || this.isPassenger() || AncientEmpiresConfig.moraleChaseRange <= 0.0D)
        {
            return true;
        }
        return this.distanceToSqr(target) <= AncientEmpiresConfig.moraleChaseRange * AncientEmpiresConfig.moraleChaseRange;
    }

    /** A faction NPC's view of a player in the world. Creative and spectator players are never enemies. */
    public boolean isHostileTo(Player player)
    {
        return !player.isCreative() && !player.isSpectator() && this.isHostileToPlayer(player.getUUID());
    }

    /**
     * Whether this faction NPC treats the player (and the player's NPCs) as an
     * enemy: standing below zero, or a recent kill of one of its own nearby
     * (revenge, as in Ancient Warfare). Owned NPCs never do.
     */
    public boolean isHostileToPlayer(@Nullable UUID player)
    {
        if (player == null || !this.isFactionNpc() || !AncientEmpiresConfig.relationsEnabled || this.level().isClientSide)
        {
            return false;
        }
        Long until = this.revenge.get(player);
        if (until != null)
        {
            if (until > this.level().getGameTime())
            {
                return true;
            }
            this.revenge.remove(player);
        }
        MinecraftServer server = this.level().getServer();
        return server != null && NpcStandings.get(server).hostile(player, this.getFaction());
    }

    /** This NPC holds the kill of one of its own against the player for {@code relations.revengeTicks}. */
    void markRevenge(UUID player)
    {
        this.revenge.put(player, this.level().getGameTime() + AncientEmpiresConfig.relationsRevengeTicks);
    }

    /**
     * Ancient Warfare's standing change and revenge: killing a faction NPC, or
     * having one's owned NPCs kill it, costs the player that faction's
     * {@code kill} standing, and the dead NPC's faction-mates within
     * {@code relations.revengeRange} turn on the player for a while.
     */
    private void recordKill(DamageSource source)
    {
        if (!this.isFactionNpc() || !AncientEmpiresConfig.relationsEnabled || !(this.level() instanceof ServerLevel level))
        {
            return;
        }
        Entity killer = source.getEntity();
        UUID player = null;
        if (killer instanceof Player killerPlayer)
        {
            if (!killerPlayer.isCreative() || AncientEmpiresConfig.relationsCountCreative)
            {
                player = killerPlayer.getUUID();
            }
        }
        else if (killer instanceof NpcEntity npc && npc.isOwnedNpc())
        {
            player = npc.getOwner();
        }
        if (player == null)
        {
            return;
        }
        String faction = this.getFaction();
        NpcStandings standings = NpcStandings.get(level.getServer());
        boolean wasHostile = standings.hostile(player, faction);
        int now = standings.adjust(player, faction, NpcFactions.killChange(faction));
        Player online = level.getServer().getPlayerList().getPlayer(player);
        if (online != null && !wasHostile && now < 0)
        {
            online.displayClientMessage(Component.translatable("message.ancientempires.standing.hostile", NpcFactions.displayName(faction)), false);
        }
        double range = AncientEmpiresConfig.relationsRevengeRange;
        UUID avenged = player;
        for (NpcEntity mate : level.getEntitiesOfClass(NpcEntity.class, this.getBoundingBox().inflate(range),
                other -> other != this && other.isAlive() && faction.equals(other.getFaction())))
        {
            mate.markRevenge(avenged);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target)
    {
        if (target instanceof NpcEntity other && sameOwner(this, other))
        {
            return false;
        }
        // A stray hit from the owner, or an ally's alert, never turns an NPC on its owner.
        UUID owner = this.getOwner();
        if (owner != null && target instanceof Player player && owner.equals(player.getUUID()))
        {
            return false;
        }
        return super.canAttack(target);
    }

    static boolean sameOwner(NpcEntity left, NpcEntity right)
    {
        UUID owner = left.getOwner();
        return owner != null && owner.equals(right.getOwner());
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions)
    {
        return dimensions.height * (1.62F / 1.8F);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        EntityDimensions base = super.getDimensions(pose);
        float scale = this.getBodyScale();
        if (scale == 1.0F)
        {
            return base;
        }
        return base.scale(scale);
    }

    /**
     * Sits in the saddle as a player does. Mobs ride with no offset, which left
     * the player-shaped NPC floating above its mount; a player sits 0.35 lower.
     * Scaled with the body, so dwarves and giants sit right too.
     */
    @Override
    public double getMyRidingOffset()
    {
        return -0.35D * this.getBodyScale();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);
        if (DATA_BODY_SCALE.equals(key))
        {
            this.refreshDimensions();
        }
        else if (DATA_SKIN.equals(key))
        {
            this.skinTextureResolved = false;
            if (this.level().isClientSide)
            {
                // Start loading the new skin off the render thread before its first frame.
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> NpcSkinPrefetch.request(this));
            }
        }
        else if (DATA_HOME.equals(key))
        {
            this.updateHomeRestriction();
        }
    }

    /**
     * Ancient Warfare kept a home as a vanilla restriction, so idle wandering
     * stays near it. Every way the home changes (spawn, buttons, load) passes
     * through the synced value, so this follows it.
     */
    private void updateHomeRestriction()
    {
        BlockPos home = this.getHome();
        if (home == null)
        {
            this.clearRestriction();
            return;
        }
        this.restrictTo(home, AncientEmpiresConfig.homeWanderRadius);
    }

    /**
     * {@link #parseSkinId} for the current skin, resolved once per skin change.
     * The renderer asks for this more than once per frame. Null means the default skin.
     */
    @Nullable
    public ResourceLocation getSkinTexture()
    {
        if (!this.skinTextureResolved)
        {
            this.skinTexture = parseSkinId(this.getSkinId());
            this.skinTextureResolved = true;
        }
        return this.skinTexture;
    }

    public float getBodyScale()
    {
        return this.entityData.get(DATA_BODY_SCALE);
    }

    public void setBodyScale(float scale)
    {
        this.entityData.set(DATA_BODY_SCALE, Mth.clamp(scale, 0.25F, 4.0F));
    }

    /**
     * Held across the spawn egg's {@code useOn}. {@link #onFinalizeSpawn} reads
     * it before the entity is tracked. Empty for dispensers and commands.
     */
    public static void beginSpawnClaim(UUID ownerId)
    {
        SPAWN_OWNER.set(ownerId);
    }

    public static void endSpawnClaim()
    {
        SPAWN_OWNER.remove();
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event)
    {
        if (!(event.getEntity() instanceof NpcEntity npc))
        {
            return;
        }
        String rosterId = SPAWN_ROSTER.get();
        if (rosterId != null)
        {
            NpcRoster.Entry entry = NpcRoster.byId(rosterId);
            if (entry != null)
            {
                entry.apply(npc);
                npc.rosterId = entry.id();
                npc.forgetRosterCaches();
                if (entry.owned() && npc.getOwner() == null)
                {
                    UUID pending = SPAWN_OWNER.get();
                    if (pending != null)
                    {
                        npc.setOwner(pending);
                    }
                }
            }
        }
        // A fresh spawn stands on its home. A saved home in the egg replaces this.
        // AeNoHome clears it again, because the egg merge cannot delete this key.
        if (npc.getHome() == null)
        {
            npc.entityData.set(DATA_HOME, Optional.of(npc.blockPosition()));
            npc.entityData.set(DATA_HOME_DIM, npc.level().dimension().location().toString());
        }
    }

    /**
     * Held across a roster egg's spawn, including a dispenser.
     * Faction rows do not claim the placer. Owned rows read {@link #SPAWN_OWNER}.
     */
    /** Sets the NPC's home in its current dimension. Immigration uses this for newcomers. */
    /** Vanilla eating ends here; what was eaten counts toward the NPC's rations. */
    @Override
    public ItemStack eat(Level level, ItemStack food)
    {
        if (!level.isClientSide)
        {
            this.needs.ate(food);
        }
        return super.eat(level, food);
    }

    public void assignHome(BlockPos home)
    {
        this.entityData.set(DATA_HOME, Optional.of(home.immutable()));
        this.entityData.set(DATA_HOME_DIM, this.level().dimension().location().toString());
    }

    public static void beginSpawnRoster(String rosterId)
    {
        SPAWN_ROSTER.set(rosterId);
    }

    public static void endSpawnRoster()
    {
        SPAWN_ROSTER.remove();
    }

    public NpcInventory getInventory()
    {
        return this.inventory;
    }

    public NpcSidearm getSidearm()
    {
        return this.sidearm;
    }

    public NpcTargetMemory getTargetMemory()
    {
        return this.targetMemory;
    }

    public boolean hasShieldInOffhand()
    {
        return WeaponClassifier.isShield(this.getOffhandItem());
    }

    /** Morale: steady, wavering, routing, shattered, surrendered. */
    /** This NPC's place in a chain of command (leader, unit, order, formation). */
    public NpcChain getChain()
    {
        return this.chain;
    }

    public NpcMorale getMorale()
    {
        return this.morale;
    }

    /** The shield and everything done with it. */
    public NpcGuard getGuard()
    {
        return this.guard;
    }

    /** True while an axe hit or a broken guard keeps the shield down. */
    public boolean isShieldDisabled()
    {
        return this.guard.isDisabled();
    }

    public boolean isRaisingShield()
    {
        return this.isUsingItem() && this.getUsedItemHand() == InteractionHand.OFF_HAND && WeaponClassifier.isShield(this.getUseItem());
    }

    /** Staggered by a perfect block: the client reads the synced flag for the recoil pose. */
    public boolean isStaggered()
    {
        if (this.level().isClientSide)
        {
            return this.entityData.get(DATA_STAGGERED);
        }
        return this.guard.isStaggered();
    }

    void setStaggeredFlag(boolean staggered)
    {
        this.entityData.set(DATA_STAGGERED, staggered);
    }

    /**
     * A hit taken on the shield does not shove the NPC back (vanilla knocks a blocking
     * mob back as if hit; a blocking player stands firm). Bashes still knock it back.
     */
    @Override
    public void knockback(double strength, double x, double z)
    {
        if (this.guard.blockedThisTick())
        {
            return;
        }
        if (this.getLastHurtByMob() instanceof NpcEntity && this.getLastHurtByMobTimestamp() == this.tickCount)
        {
            // Hit by another NPC: a lighter shove, so a fighter mid-swing is not pushed out of reach.
            strength *= AncientEmpiresConfig.npcVsNpcKnockback;
        }
        super.knockback(strength, x, z);
    }

    /** A hit taken on the raised shield outside vanilla's blocking (its first ticks): wear shield and guard. */
    public void takeOnShield(float amount)
    {
        this.hurtCurrentlyUsedShield(amount);
    }

    /** Chance to try a perfect block, by skill tier. */
    public double getParryChance()
    {
        return this.skillValue(AncientEmpiresConfig.parryChanceByTier);
    }

    /** Up to how many ticks a perfect-block raise is mistimed, by skill tier. */
    public int getParryError()
    {
        return (int) Math.round(this.skillValue(AncientEmpiresConfig.parryErrorByTier));
    }

    /** The skill tier's guard multiplier ({@code skill.guardByTier}). */
    double getGuardScale()
    {
        return this.skillValue(AncientEmpiresConfig.guardByTier);
    }

    /**
     * The NPC is committed to its own swing, so the shield stays down, as a
     * player cannot block and attack at once: for the whole wind-up. Before it,
     * {@link NpcGuard#readyToStrike} lowers the shield and keeps it from going back up.
     * The Better Mob Combat path sets the flag.
     */
    public boolean isSwingCommitted()
    {
        return this.swingCommitted || this.attack.windowOpen();
    }

    public void setSwingCommitted(boolean committed)
    {
        this.swingCommitted = committed;
    }

    /**
     * Vanilla's shield check, but by where the NPC faces. Vanilla reads the view
     * vector from the entity's yaw, which for a mob is its last heading while
     * walking: an NPC that walked one way and turned to its attacker blocked hits
     * from behind and let through hits from the front it was facing. The head
     * yaw is what is seen, and what the perfect-block check already uses.
     */
    @Override
    public boolean isDamageSourceBlocked(DamageSource source)
    {
        if (source.is(DamageTypeTags.BYPASSES_SHIELD) || !this.isBlocking()
                || source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0)
        {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null)
        {
            return false;
        }
        Vec3 toward = from.vectorTo(this.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        return toward.dot(Vec3.directionFromRotation(0.0F, this.getYHeadRot())) < 0.0D;
    }

    /** Vanilla calls this for every blocked melee hit; the guard decides what follows. */
    @Override
    protected void blockUsingShield(LivingEntity attacker)
    {
        super.blockUsingShield(attacker);
        this.guard.onBlocked(attacker);
    }



    /**
     * Chance, 0 to 1, that a swing at a guarded target is a feint. Comes from
     * the skill tier unless this NPC has its own value ({@code AeFeintChance}).
     */
    public float getFeintChance()
    {
        if (this.isLevied())
        {
            return 0.0F;
        }
        if (this.feintChance >= 0.0F)
        {
            return this.feintChance;
        }
        return (float) this.skillValue(AncientEmpiresConfig.feintChanceByTier);
    }

    public void setFeintChance(float chance)
    {
        this.feintChance = Mth.clamp(chance, 0.0F, 1.0F);
    }

    /** Chance, 0 to 1, that an NPC without a shield backsteps out of an NPC's visible wind-up. */
    public double getDodgeChance()
    {
        return this.isLevied() ? 0.0D : this.skillValue(AncientEmpiresConfig.dodgeChanceByTier);
    }

    /** Chance, 0 to 1, that a blocked hit is answered with a shield bash. */
    public double getBashChance()
    {
        if (this.isLevied())
        {
            return 0.0D;
        }
        return this.skillValue(AncientEmpiresConfig.bashChanceByTier);
    }

    /** Most ticks before the shield comes up against a threat. Skilled NPCs react faster. */
    public int getShieldReactionTicks()
    {
        return (int) Math.round(this.skillValue(AncientEmpiresConfig.shieldReactionByTier));
    }

    /**
     * Combat skill, 0 to 3, from the roster rank: ordinary 0, elite 1, leader 2,
     * elite leader 3. {@code AeSkill} overrides it for a single NPC, so later
     * roles and promotions can set it. Cached; rank only changes on load or spawn.
     */
    public int getSkillTier()
    {
        if (this.skillOverride >= 0)
        {
            return this.skillOverride;
        }
        if (this.skillTier < 0)
        {
            this.skillTier = NpcProgress.tierOf(this.getRosterSubtype());
        }
        return this.skillTier;
    }

    /** The roster rank, such as {@code soldier.elite} or {@code civilian.female}; empty when unknown. Cached. */
    String getRosterSubtype()
    {
        if (this.rosterSubtype == null)
        {
            NpcRoster.Entry entry = this.rosterEntry();
            this.rosterSubtype = entry == null ? "" : entry.subtype();
        }
        return this.rosterSubtype;
    }

    /** Spawn and load can change the roster row, so the values read from it are worked out again. */
    private void forgetRosterCaches()
    {
        this.skillTier = -1;
        this.rosterSubtype = null;
        this.syncPath();
    }

    /**
     * Ancient Warfare's civilians, workers, and traders did not fight; they fled.
     * Here that is the path: settlers and workers run. A weapon in hand does
     * not change it: an armed farmer still runs, and only fights when cornered.
     */
    public boolean isCivilian()
    {
        return AncientEmpiresConfig.civiliansFlee && !this.getNpcPath().combat();
    }

    /**
     * The NPC's path: the one it was given ({@code AePath}), otherwise the
     * one its roster rank implies. The client reads the synced copy.
     */
    public NpcPath getNpcPath()
    {
        if (this.level().isClientSide)
        {
            NpcPath synced = NpcPath.byId(this.entityData.get(DATA_PATH));
            return synced != null ? synced : NpcPath.SOLDIER;
        }
        if (this.chosenPath != null)
        {
            return this.chosenPath;
        }
        return NpcPath.fromRank(this.getRosterSubtype());
    }

    /** The role label: what the main hand says, then the rank's trade, then the path. */
    public String getRoleId()
    {
        if (this.getNpcPath().combat() && !this.getCarriedBanner().isEmpty())
        {
            return "standard_bearer";
        }
        return this.getNpcPath().roleId(this.getMainHandItem(), this.getOffhandItem(), this.entityData.get(DATA_TRADE));
    }

    /**
     * Gives the NPC a path, kept through saves and repacking. Players reach
     * this only through {@link #choosePath}; a new path ends any levy.
     */
    public void setNpcPath(NpcPath path)
    {
        this.chosenPath = path;
        this.standGroundUntil = 0;
        this.entityData.set(DATA_LEVIED, false);
        this.proficiencyVersion = Integer.MIN_VALUE;
        this.handsVersion++;
        this.syncPath();
        if (this.avoidsFights())
        {
            this.setTarget(null);
        }
    }

    /**
     * Who may pick a path: an admin, for any NPC and any path; or the
     * owner of a settler, once. Worker and soldier are final for players.
     * The screen asks this too.
     */
    public boolean mayChoosePath(Player player)
    {
        if (mayToggleCreative(player))
        {
            return true;
        }
        return this.isOwnedNpc() && player.getUUID().equals(this.getOwner()) && this.getNpcPath() == NpcPath.SETTLER;
    }

    private void choosePath(Player player, NpcPath path)
    {
        if (!this.mayChoosePath(player) || path == this.getNpcPath())
        {
            return;
        }
        if (path == NpcPath.SETTLER && !mayToggleCreative(player))
        {
            return;
        }
        this.setNpcPath(path);
        player.displayClientMessage(Component.translatable("message.ancientempires.npc.path",
                Component.translatable(NpcPath.translationKey(path.id()))), true);
    }

    private void syncPath()
    {
        if (!this.level().isClientSide)
        {
            this.entityData.set(DATA_PATH, this.getNpcPath().id());
            this.entityData.set(DATA_TRADE, NpcPath.rankTrade(this.getRosterSubtype()));
        }
    }

    /**
     * A civilian that is not standing its ground: it holds no target and runs.
     * Everything that would start a fight checks this.
     */
    public boolean avoidsFights()
    {
        if (this.morale.isSurrendered())
        {
            return true;
        }
        if (this.tickCount < this.standGroundUntil)
        {
            return false;
        }
        if (this.morale.isBroken())
        {
            // A routing soldier runs as a civilian does.
            return true;
        }
        if (!this.isCivilian())
        {
            return false;
        }
        // A levy fights until it is badly hurt, then breaks and runs like any civilian.
        return !this.isLevied() || this.getHealth() < this.getMaxHealth() * AncientEmpiresConfig.levyBreakHealth;
    }

    /** A civilian called up to fight in an emergency. Saved as {@code AeLevy}. A soldier is never a levy. */
    public boolean isLevied()
    {
        return this.entityData.get(DATA_LEVIED) && this.isCivilian();
    }

    /**
     * The owner's War Horn. Rally: soldiers wake, drop their posts, and follow.
     * Call to arms: the same, and settlers and workers are levied to follow and
     * fight too. Stand down: levies end, nobody follows, and fights are broken
     * off, so everyone goes home or back to work.
     */
    public void answerHorn(WarHornItem.Call call)
    {
        if (this.isSleeping())
        {
            this.stopSleeping();
        }
        if (call == WarHornItem.Call.STAND_DOWN)
        {
            this.setLevied(false);
            this.setFollowing(false);
            this.setTarget(null);
            this.rallyUntil = 0;
            return;
        }
        if (!this.getNpcPath().combat())
        {
            if (call != WarHornItem.Call.CALL_TO_ARMS || !this.isCivilian())
            {
                return;
            }
            this.setLevied(true);
        }
        this.morale.hornRally();
        this.getHold().setHolding(false);
        this.setFollowing(true);
        this.rallyUntil = this.tickCount + AncientEmpiresConfig.hornRallyTicks;
    }

    /** Answered a rally lately (the alarm icon). */
    public boolean isRallied()
    {
        return this.tickCount < this.rallyUntil;
    }

    /**
     * Raises or dismisses a levy. Only civilians can be levied; a soldier is
     * always a fighter. The swing profile is worked out again for the debuffs.
     */
    public void setLevied(boolean levied)
    {
        this.entityData.set(DATA_LEVIED, levied && this.isCivilian());
        this.handsVersion++;
        this.proficiencyVersion = Integer.MIN_VALUE;
        if (!this.isLevied() && this.avoidsFights())
        {
            this.setTarget(null);
        }
    }

    private void toggleLevy(Player player)
    {
        if (!this.isCivilian())
        {
            return;
        }
        boolean levied = !this.isLevied();
        this.setLevied(levied);
        player.displayClientMessage(Component.translatable(levied
                ? "message.ancientempires.npc.levy_on"
                : "message.ancientempires.npc.levy_off"), true);
    }

    /**
     * Ancient Warfare's soldiers were trained; ours fight best with what their
     * role is for. A multiplier on melee damage, from the AncientTactics trial:
     * an archer (a bow or crossbow in hand or backpack) hits at
     * {@code proficiency.archerMelee}, a skirmisher (a throwing weapon or
     * trident) at {@code skirmisherMelee}, and a levy at {@code levyDamage} on
     * top. Recomputed only when the hands or backpack change.
     */
    public float getMeleeProficiency()
    {
        int version = this.handsVersion * 31 + this.inventory.getBackpackVersion();
        if (version != this.proficiencyVersion)
        {
            this.proficiencyVersion = version;
            this.meleeProficiency = (float) (this.unitMeleeMultiplier() * this.getLevyDamage() * this.progress.levelDamage());
        }
        return this.meleeProficiency;
    }

    /** Damage multiplier for arrows and other shots: only a levy is worse at it. */
    public float getRangedProficiency()
    {
        return (float) (this.getLevyDamage() * this.progress.levelDamage());
    }

    private double getLevyDamage()
    {
        return this.isLevied() ? AncientEmpiresConfig.levyDamage : 1.0D;
    }

    private double unitMeleeMultiplier()
    {
        if (NpcMedicGoal.isMedic(this))
        {
            // A medic trades its shield hand for bandages, and trains to heal, not to fight.
            return AncientEmpiresConfig.medicMeleeProficiency;
        }
        boolean skirmisher = false;
        for (int slot = 0; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            ItemStack stack = this.inventory.getItem(slot);
            WeaponClassifier.Kind kind = WeaponClassifier.kind(stack);
            if (kind == WeaponClassifier.Kind.BOW || stack.getItem() instanceof CrossbowItem)
            {
                return AncientEmpiresConfig.archerMeleeProficiency;
            }
            skirmisher |= kind == WeaponClassifier.Kind.OTHER_RANGED;
        }
        return skirmisher ? AncientEmpiresConfig.skirmisherMeleeProficiency : 1.0D;
    }

    /**
     * Cornered with nowhere to run: fight the threat for a while with whatever
     * is in hand, then try running again.
     */
    /** Cornered while fleeing: a router may surrender, otherwise it turns and fights. */
    void cornered(LivingEntity threat)
    {
        if (!this.morale.trySurrender())
        {
            this.standGround(threat);
        }
    }

    /**
     * Morale broke (a rout, a shatter, a surrender hit): drop the fight and
     * run from it, as a civilian runs from what it was about to fight.
     */
    void onBroken()
    {
        LivingEntity target = this.getTarget();
        this.standGroundUntil = 0;
        super.setTarget(null);
        if (target != null && target.isAlive())
        {
            this.fearSource = target;
        }
    }

    /** A shattered faction soldier leaves the field: gone, as if killed, for a garrison. */
    void desert()
    {
        NpcSpawnerBlockEntity spawner = this.garrison ? this.spawner() : null;
        if (spawner != null)
        {
            spawner.onGarrisonDeath(this.spawnerSlot);
            this.garrison = false;
        }
        this.mount.desert();
        this.discard();
    }

    void standGround(LivingEntity threat)
    {
        this.standGroundUntil = this.tickCount + AncientEmpiresConfig.civilianStandGroundTicks;
        super.setTarget(threat);
    }

    /**
     * A cornered civilian fighting its way out. It fights only what cornered it:
     * no scanning for targets, no defending the owner, no chasing.
     */
    public boolean isStandingGround()
    {
        return (this.isCivilian() || this.morale.isBroken()) && this.tickCount < this.standGroundUntil;
    }

    /**
     * Whether a civilian treats this attacker as a threat. A player only counts
     * once the civilian is really being hurt ({@code civilians.playerThreatHealth});
     * a slap from a passing player does not start a panic or a fight.
     */
    public boolean civilianFears(LivingEntity other)
    {
        if (!(other instanceof Player) || this.morale.isBroken())
        {
            return true;
        }
        return this.getHealth() < this.getMaxHealth() * AncientEmpiresConfig.civilianPlayerThreatHealth;
    }

    /**
     * A civilian avoiding fights never holds a target: any goal or alert that
     * would give it one marks it as something to flee instead.
     */
    @Override
    public void setTarget(@Nullable LivingEntity target)
    {
        if (target != null && !this.level().isClientSide && this.avoidsFights())
        {
            this.fearSource = target;
            target = null;
        }
        super.setTarget(target);
    }

    /** Something a civilian was about to fight, so it flees it instead. Read once. */
    @Nullable
    LivingEntity takeFearSource()
    {
        LivingEntity source = this.fearSource;
        this.fearSource = null;
        return source;
    }

    /**
     * A four-entry {@code skill} list read at this NPC's skill: along the list by
     * level ({@link NpcLeveling#position}), or exactly at the {@code AeSkill}
     * override's entry.
     */
    double skillValue(List<? extends Number> values)
    {
        double position = this.skillOverride >= 0 ? this.skillOverride
                : NpcLeveling.position(this.getLevel(), AncientEmpiresConfig.levelMax);
        return NpcLeveling.lerp(values, position);
    }

    public int getLevel()
    {
        return this.progress.level();
    }

    /** XP toward the next level. */
    public float getXp()
    {
        return this.progress.xp();
    }

    /** XP from this level to the next, or 0 at the cap. */
    public double getXpToNext()
    {
        return this.progress.xpToNext();
    }

    /** Settlers do not level; workers and soldiers do. */
    public boolean levels()
    {
        return this.progress.levels();
    }

    /** The level's band, 0 to 3 (see {@link NpcProgress#band}). */
    public int getLevelBand()
    {
        return this.progress.band();
    }

    /** Leadership: a promoted rank, else the roster's (see {@link NpcProgress#rank}). */
    public NpcRank getRank()
    {
        return this.progress.rank();
    }

    public void setRank(NpcRank rank)
    {
        this.progress.setRank(rank);
    }

    /** The rank's name in this NPC's faction (Huscarl, Alpha, Captain...). */
    public Component getRankName()
    {
        return this.getRank().displayName(this.getFaction());
    }

    public void setLevel(int level)
    {
        this.progress.setLevel(level);
    }

    public void addXp(double amount)
    {
        this.progress.addXp(amount);
    }

    /** Translation key of a band name for the path: soldiers and workers name them differently. */
    public static String bandKey(NpcPath path, int band)
    {
        return NpcProgress.bandKey(path, band);
    }

    /** The level changed: the proficiencies (which read it) are worked out again. */
    void invalidateProficiency()
    {
        this.proficiencyVersion = Integer.MIN_VALUE;
    }

    /** The swing as it happens: combo, attack window, and what the client plays for it. */
    public NpcAttack getAttack()
    {
        return this.attack;
    }

    /** Hold position, the archer sentry: the flag and the post. */
    public NpcHold getHold()
    {
        return this.hold;
    }

    /** Meals, the claimed bed, and the last worksite. */
    public NpcNeeds getNeeds()
    {
        return this.needs;
    }

    public NpcProgress getProgress()
    {
        return this.progress;
    }

    /** Daylight on an undead or vampire NPC: a helmet takes the wear, as a zombie's does; bare-headed, it burns. */
    private void burnInSun()
    {
        ItemStack helmet = this.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty())
        {
            this.setSecondsOnFire(8);
            return;
        }
        if (helmet.isDamageableItem())
        {
            helmet.setDamageValue(helmet.getDamageValue() + this.random.nextInt(2));
            if (helmet.getDamageValue() >= helmet.getMaxDamage())
            {
                this.broadcastBreakEvent(EquipmentSlot.HEAD);
                this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
        }
    }

    void setOnWatch(boolean watch)
    {
        this.onWatch = watch;
    }

    /**
     * The banner a soldier carries as standard bearer: in its off hand, or else
     * the first in its backpack (worn on its back, hands free). Synced; empty for none.
     */
    public ItemStack getCarriedBanner()
    {
        return this.entityData.get(DATA_BANNER);
    }

    private ItemStack findBanner()
    {
        if (!this.getNpcPath().combat())
        {
            return ItemStack.EMPTY;
        }
        if (this.getOffhandItem().getItem() instanceof BannerItem)
        {
            return this.getOffhandItem();
        }
        for (int slot = NpcInventory.BACKPACK_START; slot < NpcInventory.SLOT_COUNT; slot++)
        {
            if (this.inventory.getItem(slot).getItem() instanceof BannerItem)
            {
                return this.inventory.getItem(slot);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Awake on the night watch (or in the dark): steadier and harder-hitting. */
    public boolean isOnWatch()
    {
        return this.onWatch;
    }

    /** A governor at the Town Hall oversees this worker for {@code ticks}: it works faster. */
    void govern(int ticks)
    {
        this.progress.govern(ticks);
    }

    /** Work speed multiplier from level and a governor, for workers' action times. */
    public double getWorkSpeed()
    {
        return this.progress.workSpeed();
    }

    /** Tool wear multiplier from level: skilled hands wear tools less. */
    public double getToolWearScale()
    {
        return this.progress.toolWearScale();
    }

    /** A faction NPC's starting level by rank; the roster row rolls it on first placement. */
    void rollLevel(String subtype)
    {
        this.progress.roll(subtype);
    }

    /**
     * A blocked hit wears the shield, as it does a player's: three damage or
     * more costs one point plus the damage. Creative NPCs keep their gear.
     */
    @Override
    protected void hurtCurrentlyUsedShield(float amount)
    {
        this.guard.onShieldHit(amount);
        ItemStack shield = this.getUseItem();
        if (this.isCreative() || amount < 3.0F || !WeaponClassifier.isShield(shield))
        {
            return;
        }
        InteractionHand hand = this.getUsedItemHand();
        shield.hurtAndBreak(1 + Mth.floor(amount), this, npc -> npc.broadcastBreakEvent(hand));
        if (shield.isEmpty())
        {
            this.setItemSlot(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            this.stopUsingItem();
            this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
        }
    }

    @Nullable
    public UUID getOwner()
    {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwner(@Nullable UUID owner)
    {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(owner));
    }

    public String getFaction()
    {
        return this.entityData.get(DATA_FACTION);
    }

    /**
     * A non-empty faction clears the owner and the follow flag. Faction NPCs are not owned.
     */
    public void assignFaction(String faction)
    {
        String id = sanitizeFaction(faction);
        this.entityData.set(DATA_FACTION, id);
        if (!id.isEmpty())
        {
            this.setOwner(null);
            this.setFollowing(false);
        }
    }

    public boolean isFactionNpc()
    {
        return !this.getFaction().isEmpty();
    }

    public boolean isOwnedNpc()
    {
        return this.getOwner() != null && !this.isFactionNpc();
    }

    private static String sanitizeFaction(String faction)
    {
        String trimmed = faction == null ? "" : faction.trim();
        if (trimmed.length() > FACTION_LIMIT)
        {
            return trimmed.substring(0, FACTION_LIMIT);
        }
        return trimmed;
    }

    /**
     * Who may open the inventory and use its commands: an admin (see
     * {@link #mayToggleCreative}) for any NPC, otherwise the owner. Faction NPCs
     * answer to admins only; recruiting them is a later system. An unowned,
     * factionless NPC is claimed by the first player who opens it.
     */
    public boolean canOpenInventory(Player player)
    {
        if (player.isSpectator())
        {
            return false;
        }
        if (mayToggleCreative(player))
        {
            return true;
        }
        if (this.isFactionNpc())
        {
            return false;
        }
        UUID owner = this.getOwner();
        return owner == null || owner.equals(player.getUUID());
    }

    public boolean isFollowing()
    {
        return this.entityData.get(DATA_FOLLOWING);
    }

    /**
     * Who follow mode walks toward: the owner, or the creative player escorting
     * a faction NPC. Null when there is neither.
     */
    @Nullable
    public UUID getFollowLeader()
    {
        if (this.isFactionNpc())
        {
            return this.followLeader;
        }
        return this.getOwner();
    }

    public void setFollowing(boolean following)
    {
        this.entityData.set(DATA_FOLLOWING, following);
    }

    public boolean isCreative()
    {
        return this.entityData.get(DATA_CREATIVE);
    }

    public void setCreative(boolean creative)
    {
        this.entityData.set(DATA_CREATIVE, creative);
        this.applyDropChances();
    }

    /**
     * A bow in the main hand, including a Spartan longbow. A crossbow is not a bow.
     */
    public boolean isHoldingBow()
    {
        ItemStack hand = this.getMainHandItem();
        if (hand.isEmpty() || hand.getItem() instanceof CrossbowItem)
        {
            return false;
        }
        return hand.getItem() instanceof BowItem;
    }

    /**
     * Server draw, synced through the vanilla using-item flags. The renderer
     * uses this to pose the arms.
     */
    public boolean isDrawingBow()
    {
        return this.isUsingItem()
                && this.getUsedItemHand() == InteractionHand.MAIN_HAND
                && this.isHoldingBow();
    }

    @Nullable
    public BlockPos getHome()
    {
        return this.entityData.get(DATA_HOME).orElse(null);
    }

    public String getHomeDimension()
    {
        return this.entityData.get(DATA_HOME_DIM);
    }

    public String getSkinId()
    {
        return this.entityData.get(DATA_SKIN);
    }

    /**
     * A catalog id such as {@code viking.soldier}, or a resource location a skin
     * pack can render. Invalid text is ignored.
     */
    @Nullable
    public static ResourceLocation parseSkinId(String skin)
    {
        if (skin == null || skin.isEmpty())
        {
            return null;
        }
        ResourceLocation mapped = NpcSkins.texture(skin);
        if (mapped != null)
        {
            return mapped;
        }
        NpcVariant variant = NpcVariant.bySkinId(skin);
        if (variant != null)
        {
            return variant.texture();
        }
        try
        {
            return ResourceLocation.parse(skin);
        }
        catch (IllegalArgumentException exception)
        {
            return null;
        }
    }

    /**
     * Stores a skin id without the inventory permission check. Spawn eggs use this.
     */
    public void assignSkin(String skin)
    {
        String trimmed = skin == null ? "" : skin.trim();
        if (trimmed.length() > 128)
        {
            trimmed = trimmed.substring(0, 128);
        }
        this.entityData.set(DATA_SKIN, trimmed);
    }

    /**
     * Container buttons from the inventory screen. The id values live on {@link NpcMenu}.
     */
    public boolean handleMenuButton(Player player, int id)
    {
        if (!this.canOpenInventory(player))
        {
            return false;
        }
        if (id == NpcMenu.BUTTON_FOLLOW)
        {
            this.toggleFollow(player);
            return true;
        }
        if (id == NpcMenu.BUTTON_SET_HOME)
        {
            this.entityData.set(DATA_HOME, Optional.of(this.blockPosition()));
            this.entityData.set(DATA_HOME_DIM, this.level().dimension().location().toString());
            player.displayClientMessage(Component.translatable("message.ancientempires.npc.home_set"), true);
            return true;
        }
        if (id == NpcMenu.BUTTON_CLEAR_HOME)
        {
            this.entityData.set(DATA_HOME, Optional.empty());
            this.entityData.set(DATA_HOME_DIM, "");
            player.displayClientMessage(Component.translatable("message.ancientempires.npc.home_cleared"), true);
            return true;
        }
        if (id == NpcMenu.BUTTON_REPACK)
        {
            return this.packIntoItem(player);
        }
        if (id == NpcMenu.BUTTON_HOLD)
        {
            this.toggleHold(player);
            return true;
        }
        if (id == NpcMenu.BUTTON_LEVY)
        {
            this.toggleLevy(player);
            return true;
        }
        if (id == NpcMenu.BUTTON_PATH_WORKER || id == NpcMenu.BUTTON_PATH_SOLDIER || id == NpcMenu.BUTTON_PATH_SETTLER)
        {
            this.choosePath(player, id == NpcMenu.BUTTON_PATH_WORKER ? NpcPath.WORKER
                    : id == NpcMenu.BUTTON_PATH_SOLDIER ? NpcPath.SOLDIER : NpcPath.SETTLER);
            return true;
        }
        if (id == NpcMenu.BUTTON_MOUNT)
        {
            this.mount.command(player);
            return true;
        }
        if (id == NpcMenu.BUTTON_CREATIVE)
        {
            this.toggleCreative(player);
            return true;
        }
        return false;
    }

    /**
     * Turns this NPC into the worker egg and removes the entity without
     * dropping its gear or backpack. The egg's EntityTag is applied after the
     * spawn claim, so absent owner and absent name are explicit flags.
     */
    private boolean packIntoItem(Player player)
    {
        if (this.level().isClientSide || !this.isAlive())
        {
            return false;
        }
        // The egg carries the bow as the primary weapon, the same as a reload.
        this.sidearm.restore();
        this.mount.beforeRepack();
        this.getNeeds().releaseBed();
        // Out of any unit: placed again it is a new NPC, and its unit stands alone meanwhile.
        this.chain.depart(false);
        // A repacked NPC forgets its home: placed again, the spot it lands on is its new one.
        ItemStack packed = this.createPackedItem(false);
        if (packed.isEmpty())
        {
            return false;
        }
        if (!player.getInventory().add(packed))
        {
            player.drop(packed, false);
        }
        player.displayClientMessage(Component.translatable("message.ancientempires.npc.repacked"), true);
        player.closeContainer();
        this.discard();
        return true;
    }

    /**
     * The capture wand's pick-up, as AW's: this NPC as a scroll (row, gear,
     * skin, name, level), readied the way a repack readies it, and taken out of
     * the world. Its home is not kept; a spawner gives it a new one.
     */
    public ItemStack captureScroll()
    {
        this.sidearm.restore();
        this.mount.beforeRepack();
        this.getNeeds().releaseBed();
        this.chain.depart(false);
        ItemStack scroll = this.createPackedItem(false);
        if (!scroll.isEmpty())
        {
            this.discard();
        }
        return scroll;
    }

    /**
     * The scroll this NPC came from, so a faction soldier keeps its faction
     * colors and class icon. The egg's spawn re-applies its roster row first,
     * then the packed state overwrites gear, skin, faction, owner, and name.
     * An NPC with no known row uses the generic owned scroll.
     */
    private ItemStack createPackedItem(boolean keepHome)
    {
        NpcNightLight.stripIssued(this);
        NpcRoster.Entry entry = this.rosterEntry();
        Item egg = entry == null ? null : ModItems.eggFor(entry.id());
        if (egg == null)
        {
            if (ModItems.OWNED_EGGS.isEmpty())
            {
                return ItemStack.EMPTY;
            }
            egg = ModItems.OWNED_EGGS.get(0).get();
        }
        ItemStack stack = new ItemStack(egg);
        CompoundTag entityTag = new CompoundTag();
        Component customName = this.getCustomName();
        boolean unnamed = customName == null || customName.getString().isBlank();
        this.writePackedState(entityTag, customName, unnamed, keepHome);
        stack.getOrCreateTag().put("EntityTag", entityTag);
        if (unnamed)
        {
            // A known row's scroll already names the NPC's class; only the generic one needs a label.
            if (entry == null)
            {
                stack.setHoverName(Component.translatable("item.ancientempires.unnamed_npc"));
            }
        }
        else if (entry == null || !customName.getString().equals(entry.name()))
        {
            // A renamed NPC shows its own name. One still named after its row keeps the scroll's.
            stack.setHoverName(customName);
        }
        return stack;
    }

    /**
     * Saves the known or guessed row, so an older NPC learns its row on its next save.
     */
    private void writeRoster(CompoundTag tag)
    {
        NpcRoster.Entry entry = this.rosterEntry();
        if (entry != null)
        {
            tag.putString(ROSTER_KEY, entry.id());
        }
    }

    /**
     * The saved roster row, or a guess from faction and skin for an NPC spawned
     * before the row was saved. Owned NPCs without a row have no guess.
     */
    @Nullable
    public NpcRoster.Entry rosterEntry()
    {
        NpcRoster.Entry saved = NpcRoster.byId(this.rosterId);
        if (saved != null)
        {
            return saved;
        }
        return NpcRoster.guess(this.getFaction(), this.getSkinId());
    }

    /**
     * Gear, backpack slots, skin, faction, home, owner, name, flags, and body scale.
     * Position, motion, and the old UUID stay off this tag.
     */
    private void writePackedState(CompoundTag tag, @Nullable Component customName, boolean unnamed, boolean keepHome)
    {
        ListTag hands = new ListTag();
        hands.add(this.saveEquipment(EquipmentSlot.MAINHAND));
        hands.add(this.saveEquipment(EquipmentSlot.OFFHAND));
        tag.put("HandItems", hands);

        ListTag armor = new ListTag();
        armor.add(this.saveEquipment(EquipmentSlot.FEET));
        armor.add(this.saveEquipment(EquipmentSlot.LEGS));
        armor.add(this.saveEquipment(EquipmentSlot.CHEST));
        armor.add(this.saveEquipment(EquipmentSlot.HEAD));
        tag.put("ArmorItems", armor);

        this.inventory.save(tag);
        this.sidearm.save(tag);
        this.mount.savePacked(tag);
        this.writeRoster(tag);
        if (this.skillOverride >= 0)
        {
            tag.putInt(SKILL_KEY, this.skillOverride);
        }
        this.progress.save(tag);
        // Always written: the scroll may be the generic one, whose row would imply another path.
        tag.putString(PATH_KEY, this.getNpcPath().id());
        tag.putBoolean(LEVY_KEY, this.isLevied());
        if (this.feintChance >= 0.0F)
        {
            tag.putFloat(FEINT_KEY, this.feintChance);
        }
        tag.putString(SKIN_KEY, this.getSkinId());
        tag.putString(FACTION_KEY, this.getFaction());
        tag.putBoolean(FOLLOWING_KEY, this.isFollowing());
        tag.putBoolean(CREATIVE_KEY, this.isCreative());
        this.hold.save(tag);
        tag.putFloat(BODY_SCALE_KEY, this.getBodyScale());
        BlockPos home = this.getHome();
        if (keepHome && home != null)
        {
            tag.putLong(HOME_KEY, home.asLong());
            tag.putString(HOME_DIM_KEY, this.getHomeDimension());
        }
        else if (keepHome)
        {
            tag.putBoolean(NO_HOME_KEY, true);
        }
        UUID owner = this.getOwner();
        if (owner == null)
        {
            tag.putBoolean(NO_OWNER_KEY, true);
        }
        else
        {
            tag.putUUID(OWNER_KEY, owner);
        }
        if (unnamed)
        {
            tag.putBoolean(UNNAMED_KEY, true);
        }
        else if (customName != null)
        {
            tag.putString(CUSTOM_NAME_KEY, Component.Serializer.toJson(customName));
        }
    }

    private CompoundTag saveEquipment(EquipmentSlot slot)
    {
        ItemStack stack = this.getItemBySlot(slot);
        if (stack.isEmpty())
        {
            return new CompoundTag();
        }
        return stack.copy().save(new CompoundTag());
    }

    public void applyCustomName(Player player, String name)
    {
        if (!this.canOpenInventory(player) || this.distanceToSqr(player) > 64.0D)
        {
            return;
        }
        String trimmed = name == null ? "" : name.replace('\r', ' ').replace('\n', ' ').trim();
        if (trimmed.length() > 32)
        {
            trimmed = trimmed.substring(0, 32);
        }
        if (trimmed.isEmpty())
        {
            this.setCustomName(null);
            this.setCustomNameVisible(false);
            return;
        }
        this.setCustomName(Component.literal(trimmed));
        // The client renderer decides whether the world nametag is drawn.
        this.setCustomNameVisible(false);
    }

    public void setNameplateSuppressed(boolean suppressed)
    {
        this.nameplateSuppressed = suppressed;
    }

    public boolean isNameplateSuppressed()
    {
        return this.nameplateSuppressed;
    }

    public void applySkin(Player player, String skin)
    {
        if (!this.canOpenInventory(player) || this.distanceToSqr(player) > 64.0D)
        {
            return;
        }
        String trimmed = skin == null ? "" : skin.trim();
        if (trimmed.length() > 128)
        {
            trimmed = trimmed.substring(0, 128);
        }
        if (!trimmed.isEmpty() && NpcSkins.byId(trimmed) == null && parseSkinId(trimmed) == null)
        {
            return;
        }
        this.entityData.set(DATA_SKIN, trimmed);
    }

    /**
     * An owned NPC follows its owner. A faction NPC has no owner, so a creative
     * player (an admin escort, as in Ancient Warfare) becomes the one it follows.
     * That leader is not saved; a reload ends the escort.
     */
    private void toggleFollow(Player player)
    {
        boolean escort = this.isFactionNpc() && mayToggleCreative(player);
        if (this.getOwner() == null && !escort)
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.npc.follow_no_owner"), true);
            return;
        }
        boolean following = !this.isFollowing();
        this.followLeader = following && escort ? player.getUUID() : null;
        this.setFollowing(following);
        player.displayClientMessage(Component.translatable(following
                ? "message.ancientempires.npc.follow_on"
                : "message.ancientempires.npc.follow_off"), true);
    }

    private void toggleHold(Player player)
    {
        if (!this.isHoldingBow())
        {
            return;
        }
        boolean holding = !this.getHold().holding();
        this.getHold().setHolding(holding);
        if (holding)
        {
            this.getHold().capture();
        }
        else
        {
            this.getHold().clear();
        }
        player.displayClientMessage(Component.translatable(holding
                ? "message.ancientempires.npc.hold_on"
                : "message.ancientempires.npc.hold_off"), true);
    }

    /**
     * Creative game mode, or a player who already has the instabuild ability.
     * The inventory screen polls this every tick. Permission level is not the
     * gate: a survival host with cheats is still level 2 or higher.
     */
    public static boolean mayToggleCreative(Player player)
    {
        return player.isCreative() || player.getAbilities().instabuild;
    }

    private void toggleCreative(Player player)
    {
        if (!mayToggleCreative(player))
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.npc.creative_denied"), true);
            return;
        }
        boolean creative = !this.isCreative();
        this.setCreative(creative);
        player.displayClientMessage(Component.translatable(creative
                ? "message.ancientempires.npc.creative_on"
                : "message.ancientempires.npc.creative_off"), true);
    }

    private void applyDropChances()
    {
        float chance = this.isCreative() ? 0.0F : 1.0F;
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            this.setDropChance(slot, chance);
        }
    }

    /**
     * Vanilla advances the arm swing only for monsters and players, so a
     * {@link PathfinderMob}'s {@code swing} would start and never play out:
     * work, deposits, bandaging, and the vanilla attack fallback showed no arm
     * movement. Runs on both sides, as {@code Monster.aiStep} does.
     */
    @Override
    public void aiStep()
    {
        this.updateSwingTime();
        if (aiTiming && !this.level().isClientSide)
        {
            long start = System.nanoTime();
            super.aiStep();
            int path = this.getNpcPath().ordinal();
            AI_NANOS[path] += System.nanoTime() - start;
            AI_STEPS[path]++;
            return;
        }
        super.aiStep();
    }

    /** Benchmark only (StructureGameTests.npcGoalCost): time every server AI step, by path. Off in play. */
    public static boolean aiTiming;
    private static final long[] AI_NANOS = new long[NpcPath.values().length];
    private static final long[] AI_STEPS = new long[NpcPath.values().length];
    private static final long[] AI_CUSTOM_NANOS = new long[NpcPath.values().length];

    public static void resetAiTiming()
    {
        Arrays.fill(AI_NANOS, 0L);
        Arrays.fill(AI_STEPS, 0L);
        Arrays.fill(AI_CUSTOM_NANOS, 0L);
    }

    /** Average microseconds per AI step of NPCs on {@code path} since the reset. */
    public static double aiMicros(NpcPath path)
    {
        return AI_STEPS[path.ordinal()] == 0L ? 0.0D : AI_NANOS[path.ordinal()] / 1000.0D / AI_STEPS[path.ordinal()];
    }

    /** Of that, the part spent in this mod's own per-tick work ({@code customServerAiStep}). */
    public static double aiCustomMicros(NpcPath path)
    {
        return AI_STEPS[path.ordinal()] == 0L ? 0.0D : AI_CUSTOM_NANOS[path.ordinal()] / 1000.0D / AI_STEPS[path.ordinal()];
    }

    /** Reused by updateStatus, so the half-second status check allocates nothing. */
    private final List<Goal> statusGoals = new ArrayList<>(4);

    /** The badge beside the status icon: levied, or a soldier's morale. */
    public NpcBadge getBadge()
    {
        return NpcBadge.byId(this.entityData.get(DATA_BADGE));
    }

    /** What the NPC is doing, for the icon over its head. */
    public NpcStatus getStatus()
    {
        return NpcStatus.byId(this.entityData.get(DATA_STATUS));
    }

    private void updateStatus()
    {
        List<Goal> running = this.statusGoals;
        running.clear();
        this.goalSelector.getRunningGoals().forEach(wrapped -> running.add(wrapped.getGoal()));
        byte status = (byte) NpcStatus.of(this, running).ordinal();
        running.clear();
        if (this.entityData.get(DATA_STATUS) != status)
        {
            this.entityData.set(DATA_STATUS, status);
        }
        ItemStack banner = this.findBanner();
        if (!ItemStack.matches(banner, this.entityData.get(DATA_BANNER)))
        {
            this.entityData.set(DATA_BANNER, banner.copyWithCount(1));
        }
        byte rank = (byte) this.getRank().ordinal();
        if (this.entityData.get(DATA_RANK) != rank)
        {
            this.entityData.set(DATA_RANK, rank);
        }
        byte badge = (byte) NpcBadge.of(this).ordinal();
        if (this.entityData.get(DATA_BADGE) != badge)
        {
            this.entityData.set(DATA_BADGE, badge);
        }
    }

    @Override
    protected void customServerAiStep()
    {
        long customStart = aiTiming ? System.nanoTime() : 0L;
        this.customServerAiStepTimed();
        if (aiTiming)
        {
            AI_CUSTOM_NANOS[this.getNpcPath().ordinal()] += System.nanoTime() - customStart;
        }
    }

    private void customServerAiStepTimed()
    {
        super.customServerAiStep();
        this.refreshGoalSet();
        LivingEntity target = this.getTarget();
        if (target != null && this.avoidsFights())
        {
            // A cornered civilian's stand is over; it runs again rather than keep fighting.
            this.setTarget(null);
        }
        else if (target != null && (this.tickCount + this.getId()) % 10 == 0 && !this.chases(target))
        {
            // The enemy broke and got clear: let it go and hold the field.
            this.setTarget(null);
        }
        else if (target != null && this.isStandingGround() && !this.isLevied()
                && this.distanceToSqr(target) > AncientEmpiresConfig.civilianStandBreakRange * AncientEmpiresConfig.civilianStandBreakRange)
        {
            // The threat backed off. A civilian does not give chase; it goes back to running.
            this.standGroundUntil = 0;
            this.setTarget(null);
        }
        if (target != null && this.getNavigation().isDone() && !this.isPassenger())
        {
            // Standing in a fight, the heading follows the eyes. A mob's body turns to its heading
            // whenever it moves at all (a knock, a sidestep), and a stale heading from the last walk
            // twisted the body toward a movement that never happened. Sidesteps go by it too.
            this.setYRot(this.getYHeadRot());
        }
        // Named so vanilla's profiler (/debug, F3) and Spark show this mod's share of the tick.
        this.level().getProfiler().push("ancientempires_npc");
        this.mount.tick();
        this.targetMemory.tick();
        this.sidearm.tick();
        this.guard.tick();
        this.chain.tick();
        NpcCommand.tick(this);
        if (this.inspiredStrength > 0.0F && !this.isInspired())
        {
            // Out of the aura a while: the bonuses lapse.
            this.inspiredStrength = 0.0F;
            NpcCommand.removeBonuses(this);
        }
        NpcParryPlanner.tick(this);
        this.morale.tick();
        if ((this.tickCount + this.getId()) % 20 == 0 && this.getNavigation() instanceof GroundPathNavigation ground)
        {
            // Vampires plan every path through the shade by day (vanilla's avoid-sun, as skeletons use),
            // so wandering, going home or working does not walk them into the sun.
            ground.setAvoidSun(AncientEmpiresConfig.sunlightEnabled && this.level().isDay()
                    && NpcFactions.sunlight(this.getFaction()) == NpcFactions.Sunlight.AVOID);
        }
        if (AncientEmpiresConfig.sunlightEnabled && NpcFactions.sunlight(this.getFaction()) != NpcFactions.Sunlight.NONE && this.isSunBurnTick())
        {
            this.burnInSun();
        }
        if (this.getTarget() == null && (this.tickCount + this.getId()) % AncientEmpiresConfig.skirmishRestockTicks == 0
                && SpartanThrowing.isThrowing(this.getMainHandItem()))
        {
            // Spent javelins and knives come back between fights, one at a time.
            SpartanThrowing.restockOne(this.getMainHandItem());
        }
        if ((this.tickCount + this.getId()) % NpcNightLight.CHECK_TICKS == 0)
        {
            NpcNightLight.update(this);
        }
        if ((this.tickCount + this.getId()) % NpcStatus.UPDATE_TICKS == 0)
        {
            this.updateStatus();
        }
        this.level().getProfiler().pop();
        this.attack.tick();
    }

    /**
     * Replacing a worn stack must not spawn the previous one. Drop chance stays
     * at 0 while the slot changes, then returns to the death-loot chance.
     * The stored stack is a copy, so the cursor can keep the stack it passed in.
     */
    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack)
    {
        float chance = this.getEquipmentDropChance(slot);
        this.setDropChance(slot, 0.0F);
        super.setItemSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        this.setDropChance(slot, this.isCreative() ? 0.0F : chance);
    }

    @Override
    public void onEquipItem(EquipmentSlot slot, ItemStack previous, ItemStack current)
    {
        super.onEquipItem(slot, previous, current);
        if (slot.getType() == EquipmentSlot.Type.HAND)
        {
            this.handsVersion++;
        }
        if (slot == EquipmentSlot.MAINHAND && !ItemStack.isSameItemSameTags(previous, current))
        {
            this.attack.resetCombo();
        }
    }

    /**
     * Server tick check for changes made in place, such as a weapon that broke
     * or lost durability. {@link #onEquipItem} covers a slot that was replaced.
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event)
    {
        if (event.getEntity() instanceof NpcEntity npc && event.getSlot().getType() == EquipmentSlot.Type.HAND)
        {
            npc.handsVersion++;
        }
    }

    /**
     * Changes whenever either hand changes. Goals compare it with the value
     * they last saw instead of comparing stacks every tick.
     */
    public int getHandsVersion()
    {
        return this.handsVersion;
    }

    /**
     * Right-click opens the inventory. Giving and taking happen in that screen.
     * An unowned NPC is claimed by the first player who opens it.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator())
        {
            return InteractionResult.PASS;
        }
        // An NPC the player may not command (any faction NPC, unless an admin) takes no right-click:
        // the click passes on to the held item, so a shield raised while looking at an enemy blocks
        // instead of showing a message.
        if (!this.canOpenInventory(player))
        {
            return InteractionResult.PASS;
        }
        if (player.getItemInHand(hand).getItem() instanceof InsigniaItem insignia)
        {
            return insignia.promote(this, player, player.getItemInHand(hand));
        }
        if (player.getItemInHand(hand).getItem() instanceof CaptureWandItem || player.getItemInHand(hand).getItem() instanceof SignalFlagItem)
        {
            // The wand captures this NPC (its interactLivingEntity), rather than opening the inventory.
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer)
        {
            if (this.getOwner() == null && this.getFaction().isEmpty())
            {
                this.setOwner(player.getUUID());
            }
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (containerId, playerInventory, opener) -> new NpcMenu(containerId, playerInventory, this),
                    this.getDisplayName()
            ), buffer -> buffer.writeVarInt(this.getId()));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /** Command passed to this NPC when its commander fell (0 for none); see {@link NpcCommand}. */
    public float getInheritedCommand()
    {
        return this.inheritedCommand;
    }

    void setInheritedCommand(float strength)
    {
        this.inheritedCommand = strength;
    }

    /** Inspired by a commander within the last few seconds. Morale will read this. */
    public boolean isInspired()
    {
        return this.tickCount < this.inspiredUntil;
    }

    /** The strength of the aura reaching this NPC, 0 when none. */
    float getInspiredStrength()
    {
        return this.isInspired() ? this.inspiredStrength : 0.0F;
    }

    /** A commander's aura reached this NPC: its bonuses hold for {@code ticks}, at the strongest commander's strength. */
    void inspire(float strength, int ticks)
    {
        if (!this.isInspired() || strength > this.inspiredStrength)
        {
            this.inspiredStrength = strength;
            NpcCommand.applyBonuses(this, strength);
        }
        this.inspiredUntil = this.tickCount + ticks;
    }

    /**
     * Spawned by an NPC spawner: its home is the spawner, and a garrison NPC is
     * taken back in when every player is far, or tells the spawner when it dies.
     */
    public void linkSpawner(BlockPos spawner, int slot, boolean garrison)
    {
        this.spawnerPos = spawner.immutable();
        this.spawnerSlot = slot;
        this.garrison = garrison;
        this.assignHome(spawner);
    }

    @Nullable
    public BlockPos getSpawnerPos()
    {
        return this.spawnerPos;
    }

    @Nullable
    private NpcSpawnerBlockEntity spawner()
    {
        if (this.spawnerPos == null || !this.level().isLoaded(this.spawnerPos))
        {
            return null;
        }
        return this.level().getBlockEntity(this.spawnerPos) instanceof NpcSpawnerBlockEntity spawner ? spawner : null;
    }

    /**
     * A garrison NPC goes back into its spawner once every player is past its
     * return range, as Ancient Warfare's one-shot spawns despawned and their
     * spawner came back, so the structure is manned again on the next visit.
     */
    @Override
    public void checkDespawn()
    {
        if (this.garrison && !this.level().isClientSide && (this.tickCount + this.getId()) % 20 == 0)
        {
            NpcSpawnerBlockEntity spawner = this.spawner();
            if (spawner == null)
            {
                return;
            }
            double range = spawner.returnRange();
            Player near = this.level().getNearestPlayer(this, range);
            if (near == null || near.isSpectator())
            {
                spawner.onGarrisonReturn();
                this.discard();
            }
            return;
        }
        super.checkDespawn();
    }

    /**
     * Never faster than a player sprints ({@code combat.maxSpeed}): move
     * controls set the speed as attribute times the goal's multiplier, so the
     * multiplier is capped here for every goal at once (fleeing ran at 1.2,
     * past a sprint).
     */
    @Override
    public void setSpeed(float speed)
    {
        float cap = (float) (this.getAttributeValue(Attributes.MOVEMENT_SPEED) * AncientEmpiresConfig.maxSpeed);
        super.setSpeed(Math.min(speed, cap));
    }

    /**
     * Vanilla plays the hurt sound even for a hit taken on a shield. A player
     * blocking hears only the shield, so an NPC that blocked stays quiet too.
     */
    @Override
    protected void playHurtSound(DamageSource source)
    {
        if (!this.guard.blockedThisTick())
        {
            super.playHurtSound(source);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        this.guard.setIncoming(source.getEntity());
        boolean hurt = super.hurt(source, amount);
        this.guard.setIncoming(null);
        if (hurt && !this.level().isClientSide)
        {
            this.morale.onHurt(source);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source)
    {
        if (!this.isRemoved() && !this.dead && !this.level().isClientSide)
        {
            // A commander falls: command passes to the best soldier near it, and its army feels it.
            float command = NpcCommand.strength(this);
            // The unit's next in line takes this NPC's place in the chain, and its command.
            NpcEntity heir = this.chain.depart(true);
            NpcCommand.succeed(this, source.getEntity() instanceof LivingEntity killer ? killer : null, heir);
            NpcMorale.onDeath(this, command, source.getEntity());
        }
        if (this.garrison && !this.isRemoved() && !this.dead && this.spawner() != null)
        {
            // Killed: gone for good from its garrison.
            this.spawner().onGarrisonDeath(this.spawnerSlot);
            this.garrison = false;
        }
        boolean alive = !this.isRemoved() && !this.dead;
        boolean recorded = alive && this.isOwnedNpc() && !this.isCreative() && this.level() instanceof ServerLevel server
                && NpcUpkeep.recordsDeathOf(server, this);
        if (recorded)
        {
            // AW Tweaked's keep-equipment option: what is kept goes into the hall's record, not on the ground.
            AncientEmpiresConfig.KeepOnDeath keep = AncientEmpiresConfig.upkeepKeepOnDeath;
            this.keepEquipmentOnDeath = keep == AncientEmpiresConfig.KeepOnDeath.EQUIPMENT || keep == AncientEmpiresConfig.KeepOnDeath.ALL;
            this.keepBackpackOnDeath = keep == AncientEmpiresConfig.KeepOnDeath.BACKPACK || keep == AncientEmpiresConfig.KeepOnDeath.ALL;
            if (this.keepEquipmentOnDeath || this.keepBackpackOnDeath)
            {
                this.sidearm.restore();
            }
        }
        super.die(source);
        if (alive)
        {
            this.getNeeds().releaseBed();
        }
        if (alive)
        {
            this.recordKill(source);
            if (recorded && this.level() instanceof ServerLevel level)
            {
                // After the death drops: the record holds only what the setting kept, for a priest to raise.
                // A revived NPC comes back to the home it had.
                NpcUpkeep.recordFallen(level, this, this.createPackedItem(true), source.getLocalizedDeathMessage(this));
            }
        }
    }

    /**
     * A faction NPC drops AW's loot for its type (coin: copper for soldiers
     * and archers, silver for riders and priests, gold for leaders; the undead
     * their own), from the converted {@code ancientempires:aw/entities} tables;
     * types AW gave none, and owned NPCs, drop only their gear.
     */
    @Override
    protected ResourceLocation getDefaultLootTable()
    {
        NpcRoster.Entry entry = this.rosterEntry();
        if (this.isOwnedNpc() || entry == null)
        {
            return BuiltInLootTables.EMPTY;
        }
        String type = entry.subtype().replace('.', '_');
        return AW_DROPS.contains(type)
                ? ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "aw/entities/" + ("undead".equals(this.getFaction()) ? "undead_" : "") + type)
                : BuiltInLootTables.EMPTY;
    }

    /** NPC types AW's drop tables cover (the undead have all but the siege engineer's; the table lookup copes). */
    private static final Set<String> AW_DROPS = Set.of("soldier", "soldier_elite", "archer", "archer_elite", "leader", "leader_elite",
            "cavalry", "mounted_archer", "priest", "siege_engineer");

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean hitByPlayer)
    {
        NpcNightLight.stripIssued(this);
        if (!this.keepEquipmentOnDeath)
        {
            super.dropCustomDeathLoot(source, looting, hitByPlayer);
        }
        if (this.isCreative() || this.keepBackpackOnDeath)
        {
            return;
        }
        for (ItemStack stack : this.inventory.removeBackpack())
        {
            this.spawnAtLocation(stack);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        this.inventory.save(tag);
        this.sidearm.save(tag);
        this.mount.save(tag);
        this.morale.save(tag);
        this.chain.save(tag);
        this.needs.save(tag);
        if (!this.revenge.isEmpty())
        {
            ListTag grudges = new ListTag();
            for (Map.Entry<UUID, Long> entry : this.revenge.entrySet())
            {
                CompoundTag grudge = new CompoundTag();
                grudge.putUUID("Player", entry.getKey());
                grudge.putLong("Until", entry.getValue());
                grudges.add(grudge);
            }
            tag.put(REVENGE_KEY, grudges);
        }
        this.writeRoster(tag);
        if (this.skillOverride >= 0)
        {
            tag.putInt(SKILL_KEY, this.skillOverride);
        }
        if (this.chosenPath != null)
        {
            tag.putString(PATH_KEY, this.chosenPath.id());
        }
        if (this.isLevied())
        {
            tag.putBoolean(LEVY_KEY, true);
        }
        if (this.feintChance >= 0.0F)
        {
            tag.putFloat(FEINT_KEY, this.feintChance);
        }
        UUID owner = this.getOwner();
        if (owner != null)
        {
            tag.putUUID(OWNER_KEY, owner);
        }
        tag.putBoolean(FOLLOWING_KEY, this.isFollowing());
        tag.putBoolean(CREATIVE_KEY, this.isCreative());
        this.hold.save(tag);
        BlockPos home = this.getHome();
        if (home != null)
        {
            tag.putLong(HOME_KEY, home.asLong());
            tag.putString(HOME_DIM_KEY, this.getHomeDimension());
        }
        if (!this.getSkinId().isEmpty())
        {
            tag.putString(SKIN_KEY, this.getSkinId());
        }
        tag.putString(FACTION_KEY, this.getFaction());
        tag.putFloat(BODY_SCALE_KEY, this.getBodyScale());
        if (this.inheritedCommand > 0.0F)
        {
            tag.putFloat(COMMAND_KEY, this.inheritedCommand);
        }
        this.progress.save(tag);
        if (this.spawnerPos != null)
        {
            tag.putLong(SPAWNER_KEY, this.spawnerPos.asLong());
            tag.putInt(SPAWNER_SLOT_KEY, this.spawnerSlot);
            tag.putBoolean(GARRISON_KEY, this.garrison);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.inheritedCommand = tag.getFloat(COMMAND_KEY);
        this.progress.load(tag);
        this.spawnerPos = tag.contains(SPAWNER_KEY) ? BlockPos.of(tag.getLong(SPAWNER_KEY)) : null;
        this.spawnerSlot = tag.contains(SPAWNER_SLOT_KEY) ? tag.getInt(SPAWNER_SLOT_KEY) : -1;
        this.garrison = tag.getBoolean(GARRISON_KEY);
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && Math.abs(speed.getBaseValue() - OLD_BASE_SPEED) < 1.0E-6D)
        {
            speed.setBaseValue(BASE_SPEED);
        }
        AttributeInstance knockback = this.getAttribute(Attributes.ATTACK_KNOCKBACK);
        if (knockback != null && Math.abs(knockback.getBaseValue() - 0.4D) < 1.0E-6D)
        {
            knockback.setBaseValue(0.0D);
        }
        this.inventory.load(tag);
        // After the hands (super) and the backpack, so a saved swap can be undone.
        this.sidearm.load(tag);
        this.mount.load(tag);
        this.morale.load(tag);
        this.chain.load(tag);
        this.needs.load(tag);
        this.revenge.clear();
        for (Tag element : tag.getList(REVENGE_KEY, Tag.TAG_COMPOUND))
        {
            CompoundTag grudge = (CompoundTag) element;
            if (grudge.hasUUID("Player"))
            {
                this.revenge.put(grudge.getUUID("Player"), grudge.getLong("Until"));
            }
        }
        if (tag.contains(ROSTER_KEY))
        {
            this.rosterId = tag.getString(ROSTER_KEY);
        }
        if (tag.contains(FEINT_KEY))
        {
            this.setFeintChance(tag.getFloat(FEINT_KEY));
        }
        if (tag.contains(SKILL_KEY))
        {
            this.skillOverride = Mth.clamp(tag.getInt(SKILL_KEY), 0, 3);
        }
        if (tag.contains(PATH_KEY))
        {
            this.chosenPath = NpcPath.byId(tag.getString(PATH_KEY));
        }
        else
        {
            for (String legacy : LEGACY_PATH_KEYS)
            {
                if (tag.contains(legacy))
                {
                    this.chosenPath = NpcPath.byId(tag.getString(legacy));
                    break;
                }
            }
        }
        this.entityData.set(DATA_LEVIED, tag.getBoolean(LEVY_KEY));
        this.forgetRosterCaches();
        if (tag.getBoolean(NO_OWNER_KEY))
        {
            this.setOwner(null);
        }
        else
        {
            this.setOwner(tag.hasUUID(OWNER_KEY) ? tag.getUUID(OWNER_KEY) : null);
        }
        this.setFollowing(tag.getBoolean(FOLLOWING_KEY));
        this.setCreative(tag.getBoolean(CREATIVE_KEY));
        this.hold.load(tag);
        if (tag.getBoolean(NO_HOME_KEY))
        {
            this.entityData.set(DATA_HOME, Optional.empty());
            this.entityData.set(DATA_HOME_DIM, "");
        }
        else if (tag.contains(HOME_KEY))
        {
            this.entityData.set(DATA_HOME, Optional.of(BlockPos.of(tag.getLong(HOME_KEY))));
            this.entityData.set(DATA_HOME_DIM, tag.getString(HOME_DIM_KEY));
        }
        else
        {
            this.entityData.set(DATA_HOME, Optional.empty());
            this.entityData.set(DATA_HOME_DIM, "");
        }
        this.entityData.set(DATA_SKIN, tag.getString(SKIN_KEY));
        if (tag.contains(BODY_SCALE_KEY))
        {
            this.setBodyScale(tag.getFloat(BODY_SCALE_KEY));
        }
        if (tag.getBoolean(UNNAMED_KEY))
        {
            this.setCustomName(null);
            this.setCustomNameVisible(false);
        }
        if (tag.contains(FACTION_KEY))
        {
            this.assignFaction(tag.getString(FACTION_KEY));
        }
        if (!this.progress.loaded(tag) && this.isFactionNpc())
        {
            // Saved before levels: a level for its rank, as a new one would roll.
            this.rollLevel(this.getRosterSubtype());
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }
}
