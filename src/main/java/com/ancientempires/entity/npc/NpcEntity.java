package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import com.ancientempires.combat.AttackSwing;
import com.ancientempires.combat.NpcMeleeAttackGoal;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.inventory.NpcMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
 * upkeep stay off this class. The owner is the player who placed the spawn egg,
 * or the first player to open an unowned NPC.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public class NpcEntity extends PathfinderMob
{
    private static final String OWNER_KEY = "Owner";
    private static final ThreadLocal<UUID> SPAWN_OWNER = new ThreadLocal<>();
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_ATTACK_WINDOW = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_ATTACK_ANIMATION = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_PRESENTATION_ID = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_PRESENTATION_LENGTH = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PRESENTATION_UPSWING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_PRESENTATION_SERIAL = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);

    private final NpcInventory inventory = new NpcInventory(this);

    private int comboIndex;
    private int ticksSinceSwing;
    private int lastSwingCooldown;

    private boolean attackWindowOpen;
    private int attackWindupRemaining;
    @Nullable
    private String attackAnimation;

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
        this.goalSelector.addGoal(2, new NpcMeleeAttackGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions)
    {
        return 1.62F;
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
        if (!(event.getEntity() instanceof NpcEntity npc) || npc.getOwner() != null)
        {
            return;
        }
        UUID pending = SPAWN_OWNER.get();
        if (pending != null)
        {
            npc.setOwner(pending);
        }
    }

    public NpcInventory getInventory()
    {
        return this.inventory;
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

    @Override
    protected void customServerAiStep()
    {
        super.customServerAiStep();
        this.ticksSinceSwing++;
        if (this.comboIndex != 0 && this.lastSwingCooldown > 0
                && this.ticksSinceSwing > this.lastSwingCooldown * AncientEmpiresConfig.comboResetRate)
        {
            this.comboIndex = 0;
        }
    }

    @Override
    public void onEquipItem(EquipmentSlot slot, ItemStack previous, ItemStack current)
    {
        super.onEquipItem(slot, previous, current);
        if (slot == EquipmentSlot.MAINHAND && !ItemStack.isSameItemSameTags(previous, current))
        {
            this.comboIndex = 0;
            this.ticksSinceSwing = 0;
        }
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
            if (this.getOwner() == null)
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
        UUID owner = this.getOwner();
        if (owner != null)
        {
            tag.putUUID(OWNER_KEY, owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.inventory.load(tag);
        this.setOwner(tag.hasUUID(OWNER_KEY) ? tag.getUUID(OWNER_KEY) : null);
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }
}
