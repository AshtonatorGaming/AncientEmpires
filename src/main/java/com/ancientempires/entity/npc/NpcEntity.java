package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.ancientempires.combat.AttackSwing;
import com.ancientempires.combat.NpcBowAttackGoal;
import com.ancientempires.combat.NpcMeleeAttackGoal;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
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
    private static final EntityDataAccessor<Integer> DATA_PRESENTATION_SERIAL = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FOLLOWING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CREATIVE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOLDING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_HOME = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<String> DATA_HOME_DIM = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SKIN = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    /** Empty for an owned or unclaimed NPC. A faction NPC stores an id such as {@code empire}. */
    private static final EntityDataAccessor<String> DATA_FACTION = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
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

    private final NpcInventory inventory = new NpcInventory(this);
    private final NpcSidearm sidearm = new NpcSidearm(this);

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
        this.entityData.define(DATA_PRESENTATION_SERIAL, 0);
        this.entityData.define(DATA_FOLLOWING, false);
        this.entityData.define(DATA_CREATIVE, false);
        this.entityData.define(DATA_HOLDING, false);
        this.entityData.define(DATA_HOME, Optional.empty());
        this.entityData.define(DATA_HOME_DIM, "");
        this.entityData.define(DATA_SKIN, "");
        this.entityData.define(DATA_FACTION, "");
        this.entityData.define(DATA_ATTACK_OFFHAND, false);
        this.entityData.define(DATA_BODY_SCALE, 1.0F);
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
        // A bow keeps this goal for the whole fight. Melee does not run while it does.
        this.goalSelector.addGoal(1, new NpcBowAttackGoal(this));
        this.goalSelector.addGoal(2, new NpcMeleeAttackGoal(this));
        this.goalSelector.addGoal(3, new NpcHoldPositionGoal(this, 0.9D));
        this.goalSelector.addGoal(4, new NpcFollowOwnerGoal(this, 1.0D, 3.0F));
        this.goalSelector.addGoal(5, new NpcReturnHomeGoal(this, 0.9D, 2.0F));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new NpcHurtByTargetGoal(this));
        if (AncientEmpiresConfig.targetLegacyScan)
        {
            // The selectors only count candidates for the debug stats; the filter is unchanged.
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                    living -> NpcTargetCache.countLegacyCandidate()));
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, NpcEntity.class, 10, true, false,
                    living -> this.acceptsTarget(living) && NpcTargetCache.countLegacyCandidate()));
        }
        else
        {
            this.targetSelector.addGoal(2, new NpcTargetGoal(this));
        }
    }

    /**
     * What the automatic scan may pick: any monster, or an NPC that
     * {@link #acceptsTarget} allows. Vanilla's combat checks run after this.
     */
    boolean isScanTarget(LivingEntity living)
    {
        return living instanceof Monster || this.acceptsTarget(living);
    }

    /**
     * Automatic scans only. A same-owner pair is never a target. An owned NPC hunts
     * faction NPCs. A faction NPC hunts the hostile list, not players and not owned NPCs.
     * A hit still uses the hurt-by goal, which only rejects a same-owner attacker.
     */
    private boolean acceptsTarget(LivingEntity living)
    {
        if (!(living instanceof NpcEntity target) || target == this || !target.isAlive())
        {
            return false;
        }
        if (sameOwner(this, target))
        {
            return false;
        }
        if (this.isFactionNpc())
        {
            return target.isFactionNpc() && NpcFactions.hostile(this.getFaction(), target.getFaction());
        }
        if (!this.isOwnedNpc())
        {
            return false;
        }
        return target.isFactionNpc();
    }

    @Override
    public boolean canAttack(LivingEntity target)
    {
        if (target instanceof NpcEntity other && sameOwner(this, other))
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

    public boolean canOpenInventory(Player player)
    {
        if (player.isSpectator())
        {
            return false;
        }
        if (player.getAbilities().instabuild)
        {
            return true;
        }
        UUID owner = this.getOwner();
        return owner == null || owner.equals(player.getUUID());
    }

    public boolean isFollowing()
    {
        return this.entityData.get(DATA_FOLLOWING);
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

    private ItemStack createPackedItem()
    {
        if (ModItems.OWNED_EGGS.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(ModItems.OWNED_EGGS.get(0).get());
        CompoundTag entityTag = new CompoundTag();
        Component customName = this.getCustomName();
        boolean unnamed = customName == null || customName.getString().isBlank();
        this.writePackedState(entityTag, customName, unnamed);
        stack.getOrCreateTag().put("EntityTag", entityTag);
        if (unnamed)
        {
            stack.setHoverName(Component.translatable("item.ancientempires.unnamed_npc"));
        }
        else
        {
            stack.setHoverName(customName);
        }
        return stack;
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

    private void toggleFollow(Player player)
    {
        if (this.getOwner() == null)
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.npc.follow_no_owner"), true);
            return;
        }
        boolean following = !this.isFollowing();
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
                player.displayClientMessage(Component.translatable("message.ancientempires.npc.not_owner"), true);
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
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean hitByPlayer)
    {
        super.dropCustomDeathLoot(source, looting, hitByPlayer);
        if (this.isCreative())
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
