package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.NpcUpkeep;
import com.ancientempires.combat.AttackSwing;
import com.ancientempires.combat.NpcBowAttackGoal;
import com.ancientempires.combat.NpcMeleeAttackGoal;
import com.ancientempires.combat.WeaponClassifier;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.inventory.NpcMenu;
import com.ancientempires.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The base soldier. The main hand is a vanilla equipment slot, so weapon
 * attributes still apply. Extra slots live in {@link NpcInventory}. Jobs and
 * upkeep stay off this class.
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
    private static final EntityDataAccessor<Boolean> DATA_ATTACK_WINDOW = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_ATTACK_ANIMATION = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_PRESENTATION_ID = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_PRESENTATION_LENGTH = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PRESENTATION_UPSWING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_PRESENTATION_WINDUP = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PRESENTATION_SERIAL = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FOLLOWING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CREATIVE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOLDING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
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
    private static final EntityDataAccessor<Boolean> DATA_ATTACK_OFFHAND = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    /** 1 is a normal player body. Dwarf and giant eggs store a different scale. */
    private static final EntityDataAccessor<Float> DATA_BODY_SCALE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final String FOLLOWING_KEY = "Following";
    private static final String CREATIVE_KEY = "Creative";
    private static final String HOLDING_KEY = "HoldPosition";
    private static final String HOLD_KEY = "HoldPos";
    private static final String HOLD_DIM_KEY = "HoldDim";
    /** How far a sentry may be from the spot captured when hold was turned on. */
    public static final double HOLD_LEASH = 4.0D;
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
    /** The roster row the NPC was spawned from. Repack returns it to that scroll. */
    private static final String ROSTER_KEY = "AeRoster";
    private static final String FEINT_KEY = "AeFeintChance";
    private static final String SKILL_KEY = "AeSkill";
    private static final String PATH_KEY = "AePath";
    /** What earlier builds saved the path as. Read only. */
    private static final String[] LEGACY_PATH_KEYS = {"AeCalling", "AeRole"};
    private static final String LEVY_KEY = "AeLevy";
    private static final String REVENGE_KEY = "AeRevenge";
    private static final String WORKSITE_KEY = "AeWorksite";
    private static final String HUNGRY_KEY = "AeHungry";
    private static final String NEXT_MEAL_KEY = "AeNextMeal";
    private static final String MEAL_CREDIT_KEY = "AeMealCredit";

    private final NpcInventory inventory = new NpcInventory(this);
    private final NpcSidearm sidearm = new NpcSidearm(this);
    private final NpcTargetMemory targetMemory = new NpcTargetMemory(this);
    private final NpcMount mount = new NpcMount(this);
    /** Roster row id, or empty for an NPC from the generic scroll or one spawned before rows were saved. */
    private String rosterId = "";
    /** Creative player escorting a faction NPC in follow mode. Server only, not saved. */
    @Nullable
    private UUID followLeader;
    /** Server tick until which an axe hit keeps the shield down. */
    private int shieldDisabledUntil;
    /** Set by the melee goal while it commits to a Better Mob Combat swing. */
    private boolean swingCommitted;
    /** Attacker to shield-bash after a blocked hit. Server only. */
    @Nullable
    private LivingEntity pendingBash;
    private int bashReadyTick;
    /** Tick until which the shield stays down after a bash. */
    private int shieldDownUntil;
    /** Own feint chance; below zero means the skill tier decides. */
    private float feintChance = -1.0F;
    /** Cached skill tier from the roster rank; below zero until first asked. */
    private int skillTier = -1;
    /** Saved per-NPC skill tier ({@code AeSkill}); below zero when unset. */
    private int skillOverride = -1;
    /** Cached roster rank; null until first asked. */
    @Nullable
    private String rosterSubtype;
    /** Tick until which a cornered civilian fights instead of running. */
    private int standGroundUntil;
    /** The path this NPC was given; null means the roster rank decides. */
    @Nullable
    private NpcPath chosenPath;
    private int proficiencyVersion = Integer.MIN_VALUE;
    private float meleeProficiency = 1.0F;
    /** Server only: a would-be target a civilian flees instead. */
    @Nullable
    private LivingEntity fearSource;
    /** Players this NPC holds a faction-mate's death against, with the game time it ends. Saved as {@code AeRevenge}. */
    private final java.util.Map<UUID, Long> revenge = new java.util.HashMap<>();
    /** Saved as {@code AeWorksite}. */
    @Nullable
    private BlockPos worksite;
    /** Saved as {@code AeHungry}. */
    private boolean hungry;
    /** Set for a death a Town Hall records, from {@code upkeep.keepOnDeath}: those items stay for the record. */
    private boolean keepEquipmentOnDeath;
    private boolean keepBackpackOnDeath;
    /** Game time the next meal is due; unset until the NPC first ticks. Saved as {@code AeNextMeal}. */
    private long nextMeal = -1L;
    /** Rations eaten ahead (a food bundle covers several days). Saved as {@code AeMealCredit}. */
    private double mealCredit;

    /** Server post for {@link #HOLDING_KEY}. The client only needs the boolean. */
    @Nullable
    private BlockPos holdAnchor;
    private String holdDimension = "";

    private int comboIndex;
    private int ticksSinceSwing;
    /** Bumped on any hand change. Not saved; a fresh load starts every cache cold. */
    private int handsVersion;
    private int lastSwingCooldown;

    private boolean attackWindowOpen;
    private int attackWindupRemaining;
    @Nullable
    private String attackAnimation;

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
    }

    /**
     * Paths may run through doors a hand can open, and through open doors.
     * Any door built on {@code DoorBlock} counts, modded ones included; iron
     * doors stay closed. Read once when the NPC is created.
     */
    @Override
    protected PathNavigation createNavigation(Level level)
    {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(AncientEmpiresConfig.openDoors);
        navigation.setCanPassDoors(true);
        return navigation;
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

    public static AttributeSupplier.Builder createAttributes()
    {
        // Player-like attack damage and attack speed so held weapons apply their
        // modifiers the same way they do for a player. Attack speed is not on mobs
        // by default, so it has to be added here.
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D);
    }

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
        // A bow keeps this goal for the whole fight. Melee does not run while it does.
        this.goalSelector.addGoal(1, new NpcBowAttackGoal(this));
        // No flags: blocks beside the melee goal, lowered while the NPC swings.
        this.goalSelector.addGoal(1, new NpcShieldGoal(this));
        // Civilians run instead of fighting; it holds the move flag above the combat goals.
        this.goalSelector.addGoal(1, new NpcFleeGoal(this));
        this.goalSelector.addGoal(1, new NpcBurningGoal(this));
        this.goalSelector.addGoal(2, new NpcMeleeAttackGoal(this));
        this.goalSelector.addGoal(3, new NpcHoldPositionGoal(this, 0.9D));
        this.goalSelector.addGoal(3, new NpcUpkeepGoal(this));
        this.goalSelector.addGoal(4, new NpcFollowOwnerGoal(this, 1.0D, 3.0F));
        this.goalSelector.addGoal(4, new NpcFarmerGoal(this));
        this.goalSelector.addGoal(4, new NpcWoodcutterGoal(this));
        this.goalSelector.addGoal(4, new NpcMinerGoal(this));
        this.goalSelector.addGoal(5, new NpcReturnHomeGoal(this, 0.9D));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // Ancient Warfare's order: the owner's fight first, then the NPC's own, then scanning.
        this.targetSelector.addGoal(1, new NpcOwnerDefenseGoal(this, NpcOwnerDefenseGoal.Mode.DEFEND));
        this.targetSelector.addGoal(2, new NpcOwnerDefenseGoal(this, NpcOwnerDefenseGoal.Mode.ASSIST));
        this.targetSelector.addGoal(3, new NpcHurtByTargetGoal(this));
        if (AncientEmpiresConfig.targetLegacyScan)
        {
            // The selectors only count candidates for the debug stats; the filter is unchanged.
            this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                    living -> NpcTargetCache.countLegacyCandidate()));
            this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, NpcEntity.class, 10, true, false,
                    living -> living instanceof NpcEntity other && this.acceptsTarget(other) && NpcTargetCache.countLegacyCandidate()));
        }
        else
        {
            this.targetSelector.addGoal(4, new NpcTargetGoal(this));
        }
    }

    /**
     * Same side: the same faction for faction NPCs, the same owner for owned ones.
     * Allies answer each other's alerts.
     */
    boolean isAlly(NpcEntity other)
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
        if (target == this || !target.isAlive() || sameOwner(this, target))
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

    private static boolean sameOwner(NpcEntity left, NpcEntity right)
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
    /** The worksite this NPC last worked at, such as its crop farm; checked again before each shift. */
    @Nullable
    public BlockPos getWorksite()
    {
        return this.worksite;
    }

    public void setWorksite(@Nullable BlockPos worksite)
    {
        this.worksite = worksite == null ? null : worksite.immutable();
    }

    /** Missed its last upkeep: a hungry worker does not work. Set by the Town Hall. */
    public boolean isHungry()
    {
        return this.hungry;
    }

    public void setHungry(boolean hungry)
    {
        this.hungry = hungry;
    }

    /** An owned NPC whose meal is due ({@code upkeep.mealInterval}). Faction NPCs live off their faction. */
    public boolean isMealDue()
    {
        if (!AncientEmpiresConfig.upkeepEnabled || !this.isOwnedNpc() || this.isCreative())
        {
            return false;
        }
        long now = this.level().getGameTime();
        if (this.nextMeal < 0L)
        {
            // A new NPC arrives fed.
            this.nextMeal = now + AncientEmpiresConfig.upkeepMealInterval;
        }
        return now >= this.nextMeal;
    }

    /** Takes a meal from rations already eaten ahead. Returns false if there are none. */
    public boolean mealFromCredit()
    {
        if (this.mealCredit < 1.0D)
        {
            return false;
        }
        this.mealCredit -= 1.0D;
        this.nextMeal = this.level().getGameTime() + AncientEmpiresConfig.upkeepMealInterval;
        this.hungry = false;
        return true;
    }

    /** Vanilla eating ends here; what was eaten counts toward the NPC's rations. */
    @Override
    public ItemStack eat(Level level, ItemStack food)
    {
        if (!level.isClientSide)
        {
            this.mealCredit += com.ancientempires.block.TownHallBlockEntity.rations(food);
            this.mealFromCredit();
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

    /** True while an axe hit keeps the shield down. */
    public boolean isShieldDisabled()
    {
        return this.tickCount < this.shieldDisabledUntil;
    }

    public boolean isRaisingShield()
    {
        return this.isUsingItem() && this.getUsedItemHand() == InteractionHand.OFF_HAND && WeaponClassifier.isShield(this.getUseItem());
    }

    public void raiseShield()
    {
        if (!this.isUsingItem() && this.hasShieldInOffhand() && !this.isShieldDisabled() && this.tickCount >= this.shieldDownUntil)
        {
            this.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    public void lowerShield()
    {
        if (this.isRaisingShield())
        {
            this.stopUsingItem();
        }
    }

    /**
     * The NPC is committed to its own swing, so the shield stays down, as a
     * player cannot block and attack at once. On the internal path that is only
     * the last {@code shield.dropTicks} of the wind-up, so a guard drops for the
     * strike and comes straight back; the Better Mob Combat path sets the flag.
     */
    public boolean isSwingCommitted()
    {
        if (this.swingCommitted)
        {
            return true;
        }
        int drop = AncientEmpiresConfig.shieldDropTicks;
        return this.attackWindowOpen && (drop < 0 || this.attackWindupRemaining <= drop);
    }

    public void setSwingCommitted(boolean committed)
    {
        this.swingCommitted = committed;
    }

    /**
     * Vanilla calls this for every blocked melee hit. An axe disables the shield
     * the way it does a player's.
     */
    @Override
    protected void blockUsingShield(LivingEntity attacker)
    {
        super.blockUsingShield(attacker);
        if (attacker.getMainHandItem().canDisableShield(this.getUseItem(), this, attacker))
        {
            this.shieldDisabledUntil = this.tickCount + AncientEmpiresConfig.shieldDisableTicks;
            this.lowerShield();
            this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
            return;
        }
        // A blocked hit is the moment to shove back, as a player would with Spartan's bash key.
        if (NpcShieldBash.canBash(this) && this.getRandom().nextDouble() < this.getBashChance())
        {
            this.pendingBash = attacker;
        }
    }

    /** The attacker to counter-bash, once; the shield goal calls this each tick. */
    @Nullable
    LivingEntity takePendingBash()
    {
        LivingEntity attacker = this.pendingBash;
        this.pendingBash = null;
        return attacker;
    }

    boolean isBashReady()
    {
        return this.tickCount >= this.bashReadyTick;
    }

    void startBashCooldown(int ticks)
    {
        this.bashReadyTick = this.tickCount + ticks;
        this.shieldDownUntil = this.tickCount + AncientEmpiresConfig.shieldBashRecoveryTicks;
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
        return (float) tierValue(AncientEmpiresConfig.feintChanceByTier, this.getSkillTier());
    }

    public void setFeintChance(float chance)
    {
        this.feintChance = Mth.clamp(chance, 0.0F, 1.0F);
    }

    /** Chance, 0 to 1, that a blocked hit is answered with a shield bash. */
    public double getBashChance()
    {
        if (this.isLevied())
        {
            return 0.0D;
        }
        return tierValue(AncientEmpiresConfig.bashChanceByTier, this.getSkillTier());
    }

    /** Most ticks before the shield comes up against a threat. Skilled NPCs react faster. */
    public int getShieldReactionTicks()
    {
        return (int) Math.round(tierValue(AncientEmpiresConfig.shieldReactionByTier, this.getSkillTier()));
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
            this.skillTier = tierOf(this.getRosterSubtype());
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
        return this.getNpcPath().roleId(this.getMainHandItem(), this.entityData.get(DATA_TRADE));
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
        if (!this.isCivilian() || this.tickCount < this.standGroundUntil)
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
            this.meleeProficiency = (float) (this.unitMeleeMultiplier() * this.getLevyDamage());
        }
        return this.meleeProficiency;
    }

    /** Damage multiplier for arrows and other shots: only a levy is worse at it. */
    public float getRangedProficiency()
    {
        return (float) this.getLevyDamage();
    }

    private double getLevyDamage()
    {
        return this.isLevied() ? AncientEmpiresConfig.levyDamage : 1.0D;
    }

    private double unitMeleeMultiplier()
    {
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
        return this.isCivilian() && this.tickCount < this.standGroundUntil;
    }

    /**
     * Whether a civilian treats this attacker as a threat. A player only counts
     * once the civilian is really being hurt ({@code civilians.playerThreatHealth});
     * a slap from a passing player does not start a panic or a fight.
     */
    public boolean civilianFears(LivingEntity other)
    {
        if (!(other instanceof Player))
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

    private static int tierOf(String subtype)
    {
        boolean elite = subtype.endsWith(".elite");
        if (subtype.startsWith("leader"))
        {
            return elite ? 3 : 2;
        }
        return elite ? 1 : 0;
    }

    private static double tierValue(java.util.List<? extends Number> values, int tier)
    {
        if (values.isEmpty())
        {
            return 0.0D;
        }
        return values.get(Mth.clamp(tier, 0, values.size() - 1)).doubleValue();
    }

    /**
     * A blocked hit wears the shield, as it does a player's: three damage or
     * more costs one point plus the damage. Creative NPCs keep their gear.
     */
    @Override
    protected void hurtCurrentlyUsedShield(float amount)
    {
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

    public boolean isHoldingPosition()
    {
        return this.entityData.get(DATA_HOLDING);
    }

    /**
     * Hold is the archer sentry. A sword or an empty hand ignores the flag.
     */
    public boolean isArcherHolding()
    {
        return this.isHoldingBow() && this.isHoldingPosition();
    }

    public void setHoldingPosition(boolean holding)
    {
        this.entityData.set(DATA_HOLDING, holding);
    }

    @Nullable
    public BlockPos getHoldAnchor()
    {
        return this.holdAnchor;
    }

    public void captureHoldAnchor()
    {
        this.holdAnchor = this.blockPosition();
        this.holdDimension = this.level().dimension().location().toString();
    }

    public void clearHoldAnchor()
    {
        this.holdAnchor = null;
        this.holdDimension = "";
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

    public boolean isOutsideHold()
    {
        if (!this.isHoldingPosition())
        {
            return false;
        }
        this.ensureHoldAnchor();
        BlockPos anchor = this.holdAnchor;
        if (anchor == null)
        {
            return false;
        }
        double x = anchor.getX() + 0.5D;
        double z = anchor.getZ() + 0.5D;
        return this.distanceToSqr(x, anchor.getY(), z) > HOLD_LEASH * HOLD_LEASH;
    }

    public void moveTowardHold(double speed)
    {
        this.ensureHoldAnchor();
        BlockPos anchor = this.holdAnchor;
        if (anchor == null)
        {
            return;
        }
        this.getNavigation().moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, speed);
    }

    private void ensureHoldAnchor()
    {
        String here = this.level().dimension().location().toString();
        if (this.holdAnchor == null || this.holdDimension.isEmpty() || !this.holdDimension.equals(here))
        {
            this.captureHoldAnchor();
        }
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
        ItemStack packed = this.createPackedItem();
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
     * The scroll this NPC came from, so a faction soldier keeps its faction
     * colors and class icon. The egg's spawn re-applies its roster row first,
     * then the packed state overwrites gear, skin, faction, owner, and name.
     * An NPC with no known row uses the generic owned scroll.
     */
    private ItemStack createPackedItem()
    {
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
        this.writePackedState(entityTag, customName, unnamed);
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
    NpcRoster.Entry rosterEntry()
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
    private void writePackedState(CompoundTag tag, @Nullable Component customName, boolean unnamed)
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
        tag.putBoolean(HOLDING_KEY, this.isHoldingPosition());
        this.writeHoldAnchor(tag);
        tag.putFloat(BODY_SCALE_KEY, this.getBodyScale());
        BlockPos home = this.getHome();
        if (home != null)
        {
            tag.putLong(HOME_KEY, home.asLong());
            tag.putString(HOME_DIM_KEY, this.getHomeDimension());
        }
        else
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

    private void writeHoldAnchor(CompoundTag tag)
    {
        BlockPos anchor = this.holdAnchor;
        if (!this.isHoldingPosition() || anchor == null)
        {
            return;
        }
        tag.putLong(HOLD_KEY, anchor.asLong());
        tag.putString(HOLD_DIM_KEY, this.holdDimension);
    }

    private void readHoldAnchor(CompoundTag tag)
    {
        this.setHoldingPosition(tag.getBoolean(HOLDING_KEY));
        if (!this.isHoldingPosition())
        {
            this.clearHoldAnchor();
            return;
        }
        if (tag.contains(HOLD_KEY))
        {
            this.holdAnchor = BlockPos.of(tag.getLong(HOLD_KEY));
            this.holdDimension = tag.getString(HOLD_DIM_KEY);
        }
        else
        {
            this.captureHoldAnchor();
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
        boolean holding = !this.isHoldingPosition();
        this.setHoldingPosition(holding);
        if (holding)
        {
            this.captureHoldAnchor();
        }
        else
        {
            this.clearHoldAnchor();
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

    public int getComboIndex()
    {
        return this.comboIndex;
    }

    /**
     * Called when a swing finishes, hit or miss. The next swing advances the combo.
     */
    public void completeSwing(int cooldownTicks)
    {
        this.comboIndex++;
        this.ticksSinceSwing = 0;
        this.lastSwingCooldown = Math.max(1, cooldownTicks);
    }

    public void openAttackWindow(AttackSwing swing)
    {
        this.attackWindowOpen = true;
        this.attackWindupRemaining = swing.windupTicks();
        this.attackAnimation = swing.animationId();
        String id = swing.animationId() == null ? "" : swing.animationId();
        this.entityData.set(DATA_ATTACK_WINDOW, true);
        this.entityData.set(DATA_ATTACK_ANIMATION, id);
        if (id.isEmpty())
        {
            this.clearPresentation();
            return;
        }
        this.entityData.set(DATA_PRESENTATION_ID, id);
        this.entityData.set(DATA_PRESENTATION_LENGTH, Math.max(1, swing.cooldownTicks()));
        this.entityData.set(DATA_PRESENTATION_UPSWING, swing.animationUpswing());
        this.entityData.set(DATA_PRESENTATION_WINDUP, swing.windupTicks());
        this.entityData.set(DATA_ATTACK_OFFHAND, swing.offHand());
        this.entityData.set(DATA_PRESENTATION_SERIAL, this.entityData.get(DATA_PRESENTATION_SERIAL) + 1);
    }

    public void tickAttackWindow()
    {
        if (this.attackWindupRemaining > 0)
        {
            this.attackWindupRemaining--;
        }
    }

    public void closeAttackWindow()
    {
        this.attackWindowOpen = false;
        this.attackWindupRemaining = 0;
        this.attackAnimation = null;
        this.entityData.set(DATA_ATTACK_WINDOW, false);
        this.entityData.set(DATA_ATTACK_ANIMATION, "");
    }

    /**
     * Stops the swing and the animation that was started for it. A finished
     * wind-up uses {@link #closeAttackWindow()} instead, so the follow-through
     * can keep playing for the rest of the cooldown.
     */
    public void cancelAttackPresentation()
    {
        this.closeAttackWindow();
        this.clearPresentation();
    }

    private void clearPresentation()
    {
        if (this.entityData.get(DATA_PRESENTATION_ID).isEmpty() && this.entityData.get(DATA_PRESENTATION_SERIAL) == 0)
        {
            return;
        }
        this.entityData.set(DATA_PRESENTATION_ID, "");
        this.entityData.set(DATA_PRESENTATION_LENGTH, 0);
        this.entityData.set(DATA_PRESENTATION_UPSWING, 0.0F);
        this.entityData.set(DATA_PRESENTATION_WINDUP, 0);
        this.entityData.set(DATA_ATTACK_OFFHAND, false);
        this.entityData.set(DATA_PRESENTATION_SERIAL, this.entityData.get(DATA_PRESENTATION_SERIAL) + 1);
    }

    public boolean isAttackWindowOpen()
    {
        if (this.level().isClientSide)
        {
            return this.entityData.get(DATA_ATTACK_WINDOW);
        }
        return this.attackWindowOpen;
    }

    public int getAttackWindupRemaining()
    {
        return this.attackWindupRemaining;
    }

    /**
     * Animation id from the swing that is currently winding up. Empty when the
     * NPC is not in an attack window. The client copy reads the synced value.
     */
    @Nullable
    public String getAttackAnimation()
    {
        if (this.level().isClientSide)
        {
            String synced = this.entityData.get(DATA_ATTACK_ANIMATION);
            return synced.isEmpty() ? null : synced;
        }
        return this.attackAnimation;
    }

    /**
     * Animation started when the attack window opened. It stays set through the
     * recovery so the client can finish the same clip Better Combat would play
     * for the full cooldown. Empty after a cancelled swing.
     */
    public String getAttackPresentationId()
    {
        return this.entityData.get(DATA_PRESENTATION_ID);
    }

    public int getAttackPresentationLength()
    {
        return this.entityData.get(DATA_PRESENTATION_LENGTH);
    }

    public float getAttackPresentationUpswing()
    {
        return this.entityData.get(DATA_PRESENTATION_UPSWING);
    }

    /**
     * Server wind-up ticks of the current swing. The client times the clip so
     * its hit frame lands on this tick, even when NPC wind-ups are scaled.
     */
    public int getAttackPresentationWindup()
    {
        return this.entityData.get(DATA_PRESENTATION_WINDUP);
    }

    public int getAttackPresentationSerial()
    {
        return this.entityData.get(DATA_PRESENTATION_SERIAL);
    }

    /**
     * True when the current presentation is the off-hand beat of an empty-hand
     * fist combo. A held weapon never sets this. It stays set through recovery
     * and clears only when the presentation itself is cleared.
     */
    public boolean isAttackOffhand()
    {
        return this.entityData.get(DATA_ATTACK_OFFHAND);
    }

    @Override
    protected void customServerAiStep()
    {
        super.customServerAiStep();
        LivingEntity target = this.getTarget();
        if (target != null && this.avoidsFights())
        {
            // A cornered civilian's stand is over; it runs again rather than keep fighting.
            this.setTarget(null);
        }
        else if (target != null && this.isStandingGround() && !this.isLevied()
                && this.distanceToSqr(target) > AncientEmpiresConfig.civilianStandBreakRange * AncientEmpiresConfig.civilianStandBreakRange)
        {
            // The threat backed off. A civilian does not give chase; it goes back to running.
            this.standGroundUntil = 0;
            this.setTarget(null);
        }
        this.mount.tick();
        this.targetMemory.tick();
        this.sidearm.tick();
        this.ticksSinceSwing++;
        if (this.comboIndex != 0 && this.lastSwingCooldown > 0
                && this.ticksSinceSwing > this.lastSwingCooldown * AncientEmpiresConfig.comboResetRate)
        {
            this.comboIndex = 0;
        }
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
            this.comboIndex = 0;
            this.ticksSinceSwing = 0;
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
        if (!this.canOpenInventory(player))
        {
            if (!this.level().isClientSide)
            {
                player.displayClientMessage(Component.translatable(this.isFactionNpc()
                        ? "message.ancientempires.npc.faction_locked"
                        : "message.ancientempires.npc.not_owner"), true);
            }
            return InteractionResult.CONSUME;
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

    @Override
    public void die(DamageSource source)
    {
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
            this.recordKill(source);
            if (recorded && this.level() instanceof ServerLevel level)
            {
                // After the death drops: the record holds only what the setting kept, for a priest to raise.
                NpcUpkeep.recordFallen(level, this, this.createPackedItem(), source.getLocalizedDeathMessage(this));
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean hitByPlayer)
    {
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
        if (this.worksite != null)
        {
            tag.putLong(WORKSITE_KEY, this.worksite.asLong());
        }
        if (this.hungry)
        {
            tag.putBoolean(HUNGRY_KEY, true);
        }
        if (this.nextMeal >= 0L)
        {
            tag.putLong(NEXT_MEAL_KEY, this.nextMeal);
        }
        if (this.mealCredit > 0.0D)
        {
            tag.putDouble(MEAL_CREDIT_KEY, this.mealCredit);
        }
        if (!this.revenge.isEmpty())
        {
            net.minecraft.nbt.ListTag grudges = new net.minecraft.nbt.ListTag();
            for (java.util.Map.Entry<UUID, Long> entry : this.revenge.entrySet())
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
        tag.putBoolean(HOLDING_KEY, this.isHoldingPosition());
        this.writeHoldAnchor(tag);
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.inventory.load(tag);
        // After the hands (super) and the backpack, so a saved swap can be undone.
        this.sidearm.load(tag);
        this.mount.load(tag);
        this.worksite = tag.contains(WORKSITE_KEY) ? BlockPos.of(tag.getLong(WORKSITE_KEY)) : null;
        this.hungry = tag.getBoolean(HUNGRY_KEY);
        this.nextMeal = tag.contains(NEXT_MEAL_KEY) ? tag.getLong(NEXT_MEAL_KEY) : -1L;
        this.mealCredit = tag.getDouble(MEAL_CREDIT_KEY);
        this.revenge.clear();
        for (net.minecraft.nbt.Tag element : tag.getList(REVENGE_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND))
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
        this.readHoldAnchor(tag);
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
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }
}
