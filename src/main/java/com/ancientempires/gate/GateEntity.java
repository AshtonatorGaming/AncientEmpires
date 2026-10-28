package com.ancientempires.gate;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcStandings;
import com.ancientempires.registry.ModBlocks;
import com.ancientempires.registry.ModEntities;
import com.ancientempires.structure.StructureRecords;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * AW's gate. It spans a rectangle one block thick ({@code pos1}, {@code pos2},
 * kept in the order given: a single gate slides away from {@code pos1}). Closed,
 * it fills the rectangle with {@link GateProxyBlock}s, so walking, mobs, and
 * paths treat it as a wall; opening clears them and the gate's wall moves along
 * its track ({@code edge}, 0 closed to {@code edgeMax} open: blocks for a
 * sliding wall, degrees for a drawbridge, which lays proxies flat as its deck
 * once down). Right-click (or click a proxy) to open or close; redstone at
 * either bottom corner toggles it, as AW's did.
 * <p>
 * Who may use it: admins; its owner (one a player built); for a gate of an
 * unclaimed faction structure, anyone not hostile to that faction; once
 * claimed, the claimer. A gate with neither is anyone's (AW's empty owner).
 * Damage follows AW: arrows do nothing, explosions ten times, an axe half
 * against wood, a pickaxe a half or third against iron, anything else a
 * quarter. Owners and admins sneak-right-click to pack it back into its item.
 */
public class GateEntity extends Entity implements IEntityAdditionalSpawnData
{
    private static final EntityDataAccessor<Float> EDGE = SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> STATUS = SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> HEALTH = SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.INT);
    private static final int SOUND_TICKS = 15;

    private BlockPos pos1 = BlockPos.ZERO;
    private BlockPos pos2 = BlockPos.ZERO;
    private GateType type = GateType.VERTICAL_WOOD;
    private Direction facing = Direction.NORTH;
    @Nullable
    private UUID owner;
    private float previousEdge;
    private int hurtCooldown;
    private int soundTicks;
    private boolean poweredA;
    private boolean poweredB;

    public GateEntity(EntityType<? extends GateEntity> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * A new gate over {@code pos1} to {@code pos2} (one block thick), closed,
     * facing {@code facing} (a drawbridge falls that way). Null when the area
     * is not one block thick.
     */
    @Nullable
    public static GateEntity create(Level level, BlockPos pos1, BlockPos pos2, GateType type, Direction facing, @Nullable UUID owner)
    {
        if (pos1.getX() != pos2.getX() && pos1.getZ() != pos2.getZ())
        {
            return null;
        }
        GateEntity gate = new GateEntity(ModEntities.GATE.get(), level);
        gate.pos1 = pos1.immutable();
        gate.pos2 = pos2.immutable();
        gate.type = type;
        gate.owner = owner;
        // AW turned the facing to lie across the gate's plane.
        Direction across = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
        if (pos1.getX() == pos2.getX() ? across.getAxis() == Direction.Axis.Z : across.getAxis() == Direction.Axis.X)
        {
            across = across.getClockWise();
        }
        gate.facing = across;
        gate.entityData.set(HEALTH, AncientEmpiresConfig.gateHealth);
        gate.placeAt();
        // Built closed: the opening is blocked at once, ticking or not.
        gate.fill(gate.wallArea());
        return gate;
    }

    private void placeAt()
    {
        BlockPos min = this.min();
        BlockPos max = this.max();
        boolean wideX = this.wideOnX();
        float width = this.width();
        this.setPos(min.getX() + (wideX ? width * 0.5D : 0.5D), min.getY(), min.getZ() + (wideX ? 0.5D : width * 0.5D));
    }

    public BlockPos pos1()
    {
        return this.pos1;
    }

    public BlockPos pos2()
    {
        return this.pos2;
    }

    public BlockPos min()
    {
        return new BlockPos(Math.min(this.pos1.getX(), this.pos2.getX()), Math.min(this.pos1.getY(), this.pos2.getY()), Math.min(this.pos1.getZ(), this.pos2.getZ()));
    }

    public BlockPos max()
    {
        return new BlockPos(Math.max(this.pos1.getX(), this.pos2.getX()), Math.max(this.pos1.getY(), this.pos2.getY()), Math.max(this.pos1.getZ(), this.pos2.getZ()));
    }

    public GateType gateType()
    {
        return this.type;
    }

    public Direction facing()
    {
        return this.facing;
    }

    public boolean wideOnX()
    {
        return this.pos1.getX() != this.pos2.getX();
    }

    public float width()
    {
        return this.wideOnX() ? Math.abs(this.pos1.getX() - this.pos2.getX()) + 1 : Math.abs(this.pos1.getZ() - this.pos2.getZ()) + 1;
    }

    public float height()
    {
        return Math.abs(this.pos1.getY() - this.pos2.getY()) + 1;
    }

    /** How far the gate can move: blocks for a wall, degrees for a drawbridge. */
    public float edgeMax()
    {
        return switch (this.type.motion)
        {
            case VERTICAL -> this.height();
            case SLIDE -> this.width();
            case SPLIT -> this.width() * 0.5F;
            case BRIDGE -> 90.0F;
        };
    }

    public float edge()
    {
        return this.entityData.get(EDGE);
    }

    /** The edge between the last tick and this one, for smooth drawing. */
    public float edge(float partialTick)
    {
        return this.previousEdge + (this.edge() - this.previousEdge) * partialTick;
    }

    /** 1 opening, -1 closing, 0 still. */
    public byte status()
    {
        return this.entityData.get(STATUS);
    }

    public int health()
    {
        return this.entityData.get(HEALTH);
    }

    @Override
    protected void defineSynchedData()
    {
        this.entityData.define(EDGE, 0.0F);
        this.entityData.define(STATUS, (byte) 0);
        this.entityData.define(HEALTH, 80);
    }

    @Override
    public void tick()
    {
        super.tick();
        this.previousEdge = this.edge();
        if (this.level().isClientSide)
        {
            return;
        }
        if (this.hurtCooldown > 0)
        {
            this.hurtCooldown--;
        }
        if (this.tickCount % 4 == 0)
        {
            this.checkPower();
        }
        if (this.tickCount == 1 && this.edge() == 0.0F && this.status() == 0)
        {
            // Loaded closed (or just placed): make sure the opening is blocked.
            this.fill(this.wallArea());
        }
        byte status = this.status();
        if (status == 0)
        {
            return;
        }
        float speed = this.type.motion == GateType.Motion.BRIDGE ? AncientEmpiresConfig.gateBridgeSpeed : AncientEmpiresConfig.gateMoveSpeed;
        float edge = this.edge() + status * speed;
        if (this.soundTicks++ % SOUND_TICKS == 0)
        {
            this.level().playSound(null, this.pos1, this.type.moveSound(), SoundSource.BLOCKS, 1.0F, 0.8F + this.random.nextFloat() * 0.2F);
        }
        if (edge >= this.edgeMax())
        {
            edge = this.edgeMax();
            this.entityData.set(STATUS, (byte) 0);
            if (this.type.motion == GateType.Motion.BRIDGE)
            {
                this.fill(this.deckArea());
            }
        }
        else if (edge <= 0.0F)
        {
            BlockPos[] wall = this.wallArea();
            if (!this.level().getEntitiesOfClass(LivingEntity.class, new AABB(wall[0].getX(), wall[0].getY(), wall[0].getZ(),
                    wall[1].getX() + 1, wall[1].getY() + 1, wall[1].getZ() + 1), LivingEntity::isAlive).isEmpty())
            {
                // Someone stands in the opening: open again rather than shut them in the wall.
                edge = 0.0F;
                this.entityData.set(STATUS, (byte) 1);
            }
            else
            {
                edge = 0.0F;
                this.entityData.set(STATUS, (byte) 0);
                this.fill(wall);
            }
        }
        this.entityData.set(EDGE, edge);
        this.setBoundingBox(this.makeBoundingBox());
    }

    /** Opens a closed or closing gate, closes an open or opening one. */
    public void toggle()
    {
        byte status = this.status();
        boolean opening = status == 1 || (status == 0 && this.edge() > 0.0F);
        if (opening)
        {
            this.entityData.set(STATUS, (byte) -1);
            if (this.type.motion == GateType.Motion.BRIDGE)
            {
                this.clear(this.deckArea());
            }
            return;
        }
        if (this.type.motion == GateType.Motion.BRIDGE && !this.deckClear())
        {
            return;
        }
        this.entityData.set(STATUS, (byte) 1);
        this.clear(this.wallArea());
        this.soundTicks = 0;
    }

    /** The opening, as two corners. */
    private BlockPos[] wallArea()
    {
        return new BlockPos[] {this.min(), this.max()};
    }

    /** Where a lowered drawbridge lies: from its foot outward as far as it is tall. */
    private BlockPos[] deckArea()
    {
        BlockPos min = this.min();
        BlockPos max = this.max();
        int reach = max.getY() - min.getY();
        BlockPos far = max.below(reach).relative(this.facing, reach);
        return new BlockPos[] {new BlockPos(Math.min(min.getX(), far.getX()), min.getY(), Math.min(min.getZ(), far.getZ())),
                new BlockPos(Math.max(max.getX(), far.getX()), min.getY(), Math.max(max.getZ(), far.getZ()))};
    }

    /** A drawbridge lowers only onto open air (or its own deck), as AW checked. */
    private boolean deckClear()
    {
        BlockPos[] deck = this.deckArea();
        for (BlockPos pos : BlockPos.betweenClosed(deck[0], deck[1]))
        {
            if (!this.level().isEmptyBlock(pos) && !this.level().getBlockState(pos).is(ModBlocks.GATE_PROXY.get()) && !this.inWall(pos))
            {
                return false;
            }
        }
        return true;
    }

    private boolean inWall(BlockPos pos)
    {
        BlockPos min = this.min();
        BlockPos max = this.max();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /** Proxies into the area's empty cells (anything else standing there stays). */
    private void fill(BlockPos[] area)
    {
        for (BlockPos pos : BlockPos.betweenClosed(area[0], area[1]))
        {
            if (this.level().getBlockState(pos).canBeReplaced())
            {
                this.level().setBlock(pos, ModBlocks.GATE_PROXY.get().defaultBlockState(), 3);
            }
        }
    }

    private void clear(BlockPos[] area)
    {
        for (BlockPos pos : BlockPos.betweenClosed(area[0], area[1]))
        {
            if (this.level().getBlockState(pos).is(ModBlocks.GATE_PROXY.get()))
            {
                this.level().removeBlock(pos, false);
            }
        }
    }

    private void checkPower()
    {
        int y = Math.min(this.pos1.getY(), this.pos2.getY());
        boolean a = this.level().getBestNeighborSignal(new BlockPos(this.pos1.getX(), y, this.pos1.getZ())) > 0;
        boolean b = this.level().getBestNeighborSignal(new BlockPos(this.pos2.getX(), y, this.pos2.getZ())) > 0;
        boolean pulse = (a && !this.poweredA) || (b && !this.poweredB);
        this.poweredA = a;
        this.poweredB = b;
        if (pulse)
        {
            this.toggle();
        }
    }

    /** Opens it if it is closed or closing. */
    public void open()
    {
        if (this.status() == -1 || (this.status() == 0 && this.edge() < this.edgeMax()))
        {
            this.toggle();
        }
    }

    /** Closes it if it is open or opening. */
    public void close()
    {
        if (this.status() == 1 || (this.status() == 0 && this.edge() > 0.0F))
        {
            this.toggle();
        }
    }

    /**
     * Whether an NPC may open this gate by itself ({@code gates.npcUse}, beyond
     * AW): only a player's own NPC, through its owner's gate (built by the owner,
     * or in a structure the owner claimed), never a drawbridge (AW kept those
     * from soldiers too), never a held fort's gate, never while it is fighting,
     * and never with a monster or a faction NPC hostile to its owner within
     * {@code npcHostileRange} of the gate: opening for a friend opens for
     * whoever is outside.
     */
    public boolean mayNpcUse(NpcEntity npc)
    {
        if (!AncientEmpiresConfig.gateNpcUse || this.type.motion == GateType.Motion.BRIDGE || !npc.isOwnedNpc() || npc.getTarget() != null)
        {
            return false;
        }
        UUID master = npc.getOwner();
        if (master == null)
        {
            return false;
        }
        boolean theirs;
        if (this.owner != null)
        {
            theirs = this.owner.equals(master);
        }
        else
        {
            StructureRecords.Record record = this.level() instanceof ServerLevel server ? StructureRecords.get(server).at(this.blockPosition()) : null;
            theirs = record != null && master.equals(record.claimedBy);
        }
        return theirs && !this.enemyNear(master);
    }

    private boolean enemyNear(UUID master)
    {
        double range = AncientEmpiresConfig.gateNpcHostileRange;
        if (range <= 0.0D)
        {
            return false;
        }
        return !this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range), living -> living.isAlive()
                && (living instanceof Enemy || living instanceof NpcEntity other && other.isFactionNpc() && other.isHostileToPlayer(master))).isEmpty();
    }

    /** Whether this player may open, close, or (sneaking, as owner) pack the gate. */
    public boolean mayUse(Player player)
    {
        if (player.isCreative())
        {
            return true;
        }
        if (this.owner != null)
        {
            Player owner = this.level().getPlayerByUUID(this.owner);
            return this.owner.equals(player.getUUID()) || (owner != null && owner.isAlliedTo(player));
        }
        if (this.level() instanceof ServerLevel server)
        {
            StructureRecords.Record record = StructureRecords.get(server).at(this.blockPosition());
            if (record != null && !record.faction.isEmpty())
            {
                return record.claimed() ? player.getUUID().equals(record.claimedBy)
                        : !NpcStandings.get(server.getServer()).hostile(player.getUUID(), record.faction);
            }
        }
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        if (!this.mayUse(player))
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.gate.not_yours"), true);
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown() && (player.isCreative() || player.getUUID().equals(this.owner)))
        {
            this.repack(player);
            return InteractionResult.CONSUME;
        }
        this.toggle();
        return InteractionResult.CONSUME;
    }

    /** Back into its Gate Spawner, the opening cleared. */
    private void repack(Player player)
    {
        ItemStack item = new ItemStack(GateSpawnerItem.itemFor(this.type));
        if (!player.isCreative() && !player.getInventory().add(item))
        {
            player.drop(item, false);
        }
        this.discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (this.level().isClientSide || this.isRemoved() || amount <= 0.0F || this.isInvulnerableTo(source)
                || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FIRE)
                || source.getDirectEntity() instanceof AbstractArrow)
        {
            return false;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION))
        {
            amount *= 10.0F;
        }
        else
        {
            if (this.hurtCooldown > 0)
            {
                return false;
            }
            this.hurtCooldown = 10;
            if (source.getEntity() instanceof LivingEntity attacker && !attacker.getMainHandItem().isEmpty())
            {
                Item held = attacker.getMainHandItem().getItem();
                if (!this.type.iron)
                {
                    amount = held instanceof AxeItem ? amount / 2.0F : amount / 4.0F;
                }
                else if (held instanceof PickaxeItem pickaxe)
                {
                    amount = pickaxe.getTier() == Tiers.DIAMOND || pickaxe.getTier() == Tiers.NETHERITE ? amount / 2.0F : amount / 3.0F;
                }
                else
                {
                    amount /= 4.0F;
                }
            }
        }
        this.playSound(this.type.hurtSound(), 1.0F, 1.0F);
        int health = (int) (this.health() - amount);
        this.entityData.set(HEALTH, health);
        if (health <= 0)
        {
            this.playSound(this.type.breakSound(), 1.0F, 1.0F);
            this.discard();
        }
        return true;
    }

    @Override
    public void remove(RemovalReason reason)
    {
        // Destroyed or packed (not merely unloaded with its chunk): the opening is freed.
        if (!this.level().isClientSide && reason.shouldDestroy())
        {
            this.clear(this.wallArea());
            if (this.type.motion == GateType.Motion.BRIDGE)
            {
                this.clear(this.deckArea());
            }
        }
        super.remove(reason);
    }

    /**
     * Where it can be clicked and hit: the whole opening while closed or
     * moving, the frame's top once a wall is open, the deck once a drawbridge
     * is down. Never collides itself: the proxies do that.
     */
    @Override
    protected AABB makeBoundingBox()
    {
        if (this.pos1 == null || this.pos2 == null)
        {
            return super.makeBoundingBox();
        }
        BlockPos min = this.min();
        BlockPos max = this.max();
        boolean open = this.edge() >= this.edgeMax() && this.status() == 0;
        if (open && this.type.motion == GateType.Motion.BRIDGE)
        {
            BlockPos[] deck = this.deckArea();
            return new AABB(deck[0].getX(), deck[0].getY(), deck[0].getZ(), deck[1].getX() + 1, deck[1].getY() + 0.5D, deck[1].getZ() + 1);
        }
        double bottom = open ? max.getY() + 0.5D : min.getY();
        return new AABB(min.getX(), bottom, min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1).inflate(0.01D);
    }

    @Override
    public void setPos(double x, double y, double z)
    {
        this.setPosRaw(x, y, z);
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    public boolean isPickable()
    {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public void push(Entity entity)
    {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance)
    {
        return distance < 160.0D * 160.0D;
    }

    @Override
    public ItemStack getPickResult()
    {
        return new ItemStack(GateSpawnerItem.itemFor(this.type));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        this.pos1 = BlockPos.of(tag.getLong("Pos1"));
        this.pos2 = BlockPos.of(tag.getLong("Pos2"));
        this.type = GateType.byOrdinal(tag.getInt("Type"));
        this.facing = Direction.from2DDataValue(tag.getInt("Facing"));
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.entityData.set(EDGE, tag.getFloat("Edge"));
        this.entityData.set(STATUS, tag.getByte("Status"));
        this.entityData.set(HEALTH, tag.contains("Health") ? tag.getInt("Health") : AncientEmpiresConfig.gateHealth);
        this.previousEdge = this.edge();
        this.placeAt();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        tag.putLong("Pos1", this.pos1.asLong());
        tag.putLong("Pos2", this.pos2.asLong());
        tag.putInt("Type", this.type.ordinal());
        tag.putInt("Facing", this.facing.get2DDataValue());
        if (this.owner != null)
        {
            tag.putUUID("Owner", this.owner);
        }
        tag.putFloat("Edge", this.edge());
        tag.putByte("Status", this.status());
        tag.putInt("Health", this.health());
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(this.pos1);
        buffer.writeBlockPos(this.pos2);
        buffer.writeVarInt(this.type.ordinal());
        buffer.writeVarInt(this.facing.get2DDataValue());
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer)
    {
        this.pos1 = buffer.readBlockPos();
        this.pos2 = buffer.readBlockPos();
        this.type = GateType.byOrdinal(buffer.readVarInt());
        this.facing = Direction.from2DDataValue(buffer.readVarInt());
        this.placeAt();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket()
    {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
