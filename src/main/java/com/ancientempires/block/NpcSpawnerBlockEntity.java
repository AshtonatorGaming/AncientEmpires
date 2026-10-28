package com.ancientempires.block;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcSafety;
import com.ancientempires.item.MobScrollItem;
import com.ancientempires.item.NpcSpawnEggItem;
import com.ancientempires.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Ancient Warfare's advanced spawner, kept to its features and made simpler to
 * set up. What it spawns is nine slots of scrolls: an NPC scroll (a roster
 * scroll, or a repacked NPC, which comes back exactly) or a vanilla spawn egg,
 * the stack size being how many.
 * <ul>
 * <li>GARRISON (a structure's people, AW's one-shot spawner): when a player
 * comes within {@code playerRange} it sends everyone out, standing guard
 * around it as their home. Killed ones are gone for good. When every player
 * is past {@code returnRange}, the survivors are taken back in, so they stand
 * at their posts again next time. Once all are dead, the block removes itself.</li>
 * <li>REPEATING: vanilla's spawner, one at a time every {@code minDelay} to
 * {@code maxDelay} ticks while a player is in range, up to {@code maxNearby}
 * alive, {@code totalLimit} in all (0 for no limit).</li>
 * <li>REDSTONE: one spawn on each redstone pulse, up to {@code maxNearby}.</li>
 * </ul>
 * Light sensitive: only in the dark. Shown: a solid block everyone sees;
 * otherwise only admins do. Only admins (creative) open or change it. Nothing ticks for a spawner no player
 * is near beyond one distance check a second.
 */
public final class NpcSpawnerBlockEntity extends BlockEntity
{
    public static final int SLOTS = 9;
    /** The settings, in the order the screen shows them; each a ContainerData index. */
    public static final int MODE = 0;
    public static final int PLAYER_RANGE = 1;
    public static final int SPAWN_RADIUS = 2;
    public static final int RETURN_RANGE = 3;
    public static final int MIN_DELAY = 4;
    public static final int MAX_DELAY = 5;
    public static final int MAX_NEARBY = 6;
    public static final int TOTAL_LIMIT = 7;
    public static final int LIGHT_SENSITIVE = 8;
    public static final int OUT = 9;
    public static final int SPAWNED = 10;
    public static final int CLEAR_XP = 11;
    public static final int POOL = 12;
    public static final int HOSTILE = 13;
    public static final int SHOWN = 14;
    public static final int DATA_COUNT = 15;

    public enum Mode
    {
        GARRISON,
        REPEATING,
        REDSTONE
    }

    /** Lowest and highest value of each number setting, and its step. */
    private static final int[][] LIMITS = {
            {0, 2, 1},
            {4, 64, 2},
            {0, 16, 1},
            {16, 128, 8},
            {20, 2400, 20},
            {20, 4800, 20},
            {1, 32, 1},
            {0, 500, 1},
            {0, 1, 1},
            {0, 0, 0},
            {0, 0, 0},
            {0, 1000, 5},
            {0, 1, 1},
            {0, 1, 1},
            {0, 1, 1}};
    private static final int CHECK_TICKS = 20;
    private static final int PLACE_TRIES = 12;

    private final SimpleContainer slots = new SimpleContainer(SLOTS)
    {
        @Override
        public void setChanged()
        {
            super.setChanged();
            NpcSpawnerBlockEntity.this.setChanged();
            NpcSpawnerBlockEntity.this.relabel();
        }

        @Override
        public boolean stillValid(Player player)
        {
            return Container.stillValidBlockEntity(NpcSpawnerBlockEntity.this, player) && NpcEntity.mayToggleCreative(player);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack)
        {
            return spawns(stack);
        }
    };
    private Mode mode = Mode.GARRISON;
    /** Client side: what the admin nametag reads (synced; see {@link #label()}). */
    private String label = "";
    private int playerRange = 16;
    private int spawnRadius = 4;
    private int returnRange = 48;
    private int minDelay = 200;
    private int maxDelay = 800;
    private int maxNearby = 6;
    private int totalLimit;
    private boolean lightSensitive;
    /** GARRISON: experience dropped when the last of the garrison dies and the spawner goes. */
    private int clearXp;
    /** GARRISON: one slot, picked by weight the first time, instead of all of them. */
    private boolean pool;
    /** Its structure cannot be claimed while it holds anyone (AW's hostile spawner). */
    private boolean hostile = true;
    /** GARRISON: pool pick and count rolls done, on the first trigger. */
    private boolean settled;
    /** GARRISON: NPCs of this spawner out in the world now. */
    private int out;
    /** REPEATING: spawned so far, against totalLimit. */
    private int spawned;
    private int delay = -1;
    private boolean powered;

    private final ContainerData data = new ContainerData()
    {
        @Override
        public int get(int index)
        {
            return NpcSpawnerBlockEntity.this.get(index);
        }

        @Override
        public void set(int index, int value)
        {
            NpcSpawnerBlockEntity.this.set(index, value);
        }

        @Override
        public int getCount()
        {
            return DATA_COUNT;
        }
    };

    public NpcSpawnerBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.NPC_SPAWNER.get(), pos, state);
    }

    /** What a slot takes: an NPC scroll or a vanilla spawn egg. */
    public static boolean spawns(ItemStack stack)
    {
        return stack.getItem() instanceof NpcSpawnEggItem || stack.getItem() instanceof SpawnEggItem || stack.getItem() instanceof MobScrollItem;
    }

    public SimpleContainer slots()
    {
        return this.slots;
    }

    public ContainerData data()
    {
        return this.data;
    }

    int get(int index)
    {
        return switch (index)
        {
            case MODE -> this.mode.ordinal();
            case PLAYER_RANGE -> this.playerRange;
            case SPAWN_RADIUS -> this.spawnRadius;
            case RETURN_RANGE -> this.returnRange;
            case MIN_DELAY -> this.minDelay;
            case MAX_DELAY -> this.maxDelay;
            case MAX_NEARBY -> this.maxNearby;
            case TOTAL_LIMIT -> this.totalLimit;
            case LIGHT_SENSITIVE -> this.lightSensitive ? 1 : 0;
            case OUT -> this.out;
            case SPAWNED -> this.spawned;
            case CLEAR_XP -> this.clearXp;
            case POOL -> this.pool ? 1 : 0;
            case HOSTILE -> this.hostile ? 1 : 0;
            case SHOWN -> this.getBlockState().hasProperty(NpcSpawnerBlock.SHOWN) && this.getBlockState().getValue(NpcSpawnerBlock.SHOWN) ? 1 : 0;
            default -> 0;
        };
    }

    void set(int index, int value)
    {
        switch (index)
        {
            case MODE -> this.mode = Mode.values()[Math.floorMod(value, Mode.values().length)];
            case PLAYER_RANGE -> this.playerRange = value;
            case SPAWN_RADIUS -> this.spawnRadius = value;
            case RETURN_RANGE -> this.returnRange = value;
            case MIN_DELAY -> this.minDelay = value;
            case MAX_DELAY -> this.maxDelay = value;
            case MAX_NEARBY -> this.maxNearby = value;
            case TOTAL_LIMIT -> this.totalLimit = value;
            case LIGHT_SENSITIVE -> this.lightSensitive = value != 0;
            case OUT -> this.out = value;
            case SPAWNED -> this.spawned = value;
            case CLEAR_XP -> this.clearXp = value;
            case POOL -> this.pool = value != 0;
            case HOSTILE -> this.hostile = value != 0;
            case SHOWN -> this.setShown(value != 0);
            default ->
            {
            }
        }
    }

    /**
     * A setting button: {@code setting * 4 + op}, op 0 down, 1 up, 2 down by ten
     * steps, 3 up by ten. The mode and the toggles just change. Clamped, and the
     * delays kept in order.
     */
    public void press(int button)
    {
        int setting = button / 4;
        int op = button % 4;
        if (setting < 0 || setting >= LIMITS.length || setting == OUT || setting == SPAWNED)
        {
            return;
        }
        int[] limit = LIMITS[setting];
        int step = limit[2] * (op >= 2 ? 10 : 1) * (op % 2 == 0 ? -1 : 1);
        int value = this.get(setting);
        if (setting == MODE)
        {
            value = Math.floorMod(value + Integer.signum(step), Mode.values().length);
        }
        else if (setting == LIGHT_SENSITIVE || setting == POOL || setting == HOSTILE || setting == SHOWN)
        {
            value = value == 0 ? 1 : 0;
        }
        else
        {
            value = Math.max(limit[0], Math.min(limit[1], value + step));
        }
        this.set(setting, value);
        if (this.minDelay > this.maxDelay)
        {
            if (setting == MIN_DELAY)
            {
                this.maxDelay = this.minDelay;
            }
            else
            {
                this.minDelay = this.maxDelay;
            }
        }
        this.setChanged();
    }

    /** Shown (a solid block everyone sees) or hidden (admins only); kept in the block state, which the client draws from. */
    private void setShown(boolean shown)
    {
        BlockState state = this.getBlockState();
        if (this.level != null && !this.level.isClientSide && state.hasProperty(NpcSpawnerBlock.SHOWN) && state.getValue(NpcSpawnerBlock.SHOWN) != shown)
        {
            this.level.setBlock(this.worldPosition, state.setValue(NpcSpawnerBlock.SHOWN, shown), 3);
        }
    }

    /** Server tick. One player-distance check a second; spawning only when it is time. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, NpcSpawnerBlockEntity spawner)
    {
        if (!(level instanceof ServerLevel server) || (level.getGameTime() + pos.asLong()) % CHECK_TICKS != 0)
        {
            return;
        }
        level.getProfiler().push("ancientempires_spawner");
        switch (spawner.mode)
        {
            case GARRISON -> spawner.tickGarrison(server);
            case REPEATING -> spawner.tickRepeating(server);
            case REDSTONE -> spawner.tickRedstone(server);
        }
        level.getProfiler().pop();
    }

    private void tickGarrison(ServerLevel level)
    {
        if (this.out > 0 && !this.playerWithin(level, this.returnRange) && this.nearbyCount(level) == 0)
        {
            // Out, but none of ours is anywhere near and nobody is watching: they were removed some
            // other way (a command, an admin repack). Arm again rather than wait for them forever.
            this.out = 0;
            this.setChanged();
        }
        if (this.out > 0 || this.isEmpty() || !this.lightAllows(level) || !this.playerWithin(level, this.playerRange))
        {
            return;
        }
        if (!this.settled)
        {
            this.settle(level.getRandom());
        }
        // Everyone out, each slot's stack being how many of that one.
        for (int slot = 0; slot < SLOTS; slot++)
        {
            ItemStack stack = this.slots.getItem(slot);
            for (int copy = 0; copy < stack.getCount(); copy++)
            {
                if (this.spawnOne(level, stack, slot, true))
                {
                    this.out++;
                }
            }
        }
        this.setChanged();
    }

    private void tickRepeating(ServerLevel level)
    {
        if (this.isEmpty() || (this.totalLimit > 0 && this.spawned >= this.totalLimit) || !this.lightAllows(level)
                || !this.playerWithin(level, this.playerRange))
        {
            return;
        }
        if (this.delay < 0)
        {
            this.delay = this.nextDelay(level.getRandom());
        }
        this.delay -= CHECK_TICKS;
        if (this.delay > 0)
        {
            return;
        }
        this.delay = this.nextDelay(level.getRandom());
        this.spawnWave(level);
    }

    private void tickRedstone(ServerLevel level)
    {
        boolean now = level.hasNeighborSignal(this.worldPosition);
        if (now && !this.powered && !this.isEmpty() && this.lightAllows(level))
        {
            this.spawnWave(level);
        }
        if (now != this.powered)
        {
            this.powered = now;
            this.setChanged();
        }
    }

    /** One spawn from a random filled slot, if fewer than maxNearby of this spawner's are about. */
    private void spawnWave(ServerLevel level)
    {
        if (this.nearbyCount(level) >= this.maxNearby)
        {
            return;
        }
        int filled = 0;
        for (int slot = 0; slot < SLOTS; slot++)
        {
            filled += this.slots.getItem(slot).isEmpty() ? 0 : 1;
        }
        int pick = level.getRandom().nextInt(filled);
        for (int slot = 0; slot < SLOTS; slot++)
        {
            ItemStack stack = this.slots.getItem(slot);
            if (!stack.isEmpty() && pick-- == 0)
            {
                if (this.spawnOne(level, stack, slot, false))
                {
                    this.spawned++;
                    this.setChanged();
                }
                return;
            }
        }
    }

    /** One entity from a slot's scroll or egg, on safe ground within spawnRadius, its home here. */
    private boolean spawnOne(ServerLevel level, ItemStack stack, int slot, boolean garrison)
    {
        BlockPos at = this.findSpot(level);
        if (at == null)
        {
            return false;
        }
        ItemStack one = stack.copyWithCount(1);
        Entity spawned;
        if (one.getItem() instanceof NpcSpawnEggItem)
        {
            spawned = NpcSpawnEggItem.spawnFrom(level, one, at, null);
        }
        else if (one.getItem() instanceof MobScrollItem)
        {
            spawned = MobScrollItem.spawn(level, one, at);
        }
        else if (one.getItem() instanceof SpawnEggItem egg)
        {
            EntityType<?> type = egg.getType(one.getTag());
            spawned = type.spawn(level, one, null, at, MobSpawnType.SPAWNER, true, false);
        }
        else
        {
            return false;
        }
        if (spawned instanceof NpcEntity npc)
        {
            npc.linkSpawner(this.worldPosition, slot, garrison);
        }
        else if (spawned instanceof Mob mob && garrison)
        {
            mob.setPersistenceRequired();
        }
        return spawned != null;
    }

    @Nullable
    private BlockPos findSpot(ServerLevel level)
    {
        if (this.spawnRadius == 0)
        {
            // A post: on the spawner's own spot (hidden, it never collides), or on top of it when shown.
            BlockPos here = this.getBlockState().getValue(NpcSpawnerBlock.SHOWN) ? this.worldPosition.above() : this.worldPosition;
            return level.isEmptyBlock(here.above()) ? here : null;
        }
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < PLACE_TRIES; attempt++)
        {
            BlockPos at = this.worldPosition.offset(random.nextInt(this.spawnRadius * 2 + 1) - this.spawnRadius, random.nextInt(3) - 1,
                    random.nextInt(this.spawnRadius * 2 + 1) - this.spawnRadius);
            if (level.isEmptyBlock(at) && level.isEmptyBlock(at.above()) && !level.isEmptyBlock(at.below()) && NpcSafety.isSafeSpot(level, at))
            {
                return at;
            }
        }
        return null;
    }

    /** A scroll's least count (AW's minToSpawn); the stack size is the most. Rolled once, on the first trigger. */
    public static final String MIN_COUNT_KEY = "AeMinCount";

    /**
     * GARRISON, the first time it is set off: each slot's count is rolled
     * between its scroll's {@link #MIN_COUNT_KEY} and its stack size, and with
     * pool on, one slot is kept (picked by weight, its count) and the rest
     * emptied. Done once, so the garrison stays the same between visits.
     */
    private void settle(RandomSource random)
    {
        for (int slot = 0; slot < SLOTS; slot++)
        {
            ItemStack stack = this.slots.getItem(slot);
            if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains(MIN_COUNT_KEY))
            {
                continue;
            }
            int min = Math.max(0, Math.min(stack.getCount(), stack.getTag().getInt(MIN_COUNT_KEY)));
            int count = min + random.nextInt(stack.getCount() - min + 1);
            stack.getTag().remove(MIN_COUNT_KEY);
            if (stack.getTag().isEmpty())
            {
                stack.setTag(null);
            }
            this.slots.setItem(slot, count == 0 ? ItemStack.EMPTY : stack.copyWithCount(count));
        }
        if (this.pool)
        {
            int total = 0;
            for (int slot = 0; slot < SLOTS; slot++)
            {
                total += this.slots.getItem(slot).getCount();
            }
            int pick = total > 0 ? random.nextInt(total) : -1;
            for (int slot = 0; slot < SLOTS; slot++)
            {
                int count = this.slots.getItem(slot).getCount();
                if (pick >= 0 && pick < count)
                {
                    pick = -1;
                    continue;
                }
                pick -= count;
                this.slots.setItem(slot, ItemStack.EMPTY);
            }
        }
        this.settled = true;
        this.setChanged();
    }

    /** A post for one soldier: its people stand on the spawner's own spot. The capture wand places these. */
    public void makePost()
    {
        this.spawnRadius = 0;
        this.setChanged();
    }

    /** Hostile, and still holding people: its structure cannot be claimed yet. */
    public boolean blocksClaim()
    {
        return this.hostile && !this.isEmpty();
    }

    /** GARRISON: one of ours was killed. It is gone for good, and when none are left the spawner goes too. */
    public void onGarrisonDeath(int slot)
    {
        if (slot >= 0 && slot < SLOTS)
        {
            this.slots.getItem(slot).shrink(1);
            this.slots.setChanged();
        }
        this.out = Math.max(0, this.out - 1);
        this.setChanged();
        if (this.out == 0 && this.isEmpty() && this.level != null)
        {
            // Cleared: the structure's people are dead.
            if (this.clearXp > 0 && this.level instanceof ServerLevel server)
            {
                ExperienceOrb.award(server, this.worldPosition.getCenter(), this.clearXp);
            }
            this.level.removeBlock(this.worldPosition, false);
        }
    }

    /** GARRISON: one of ours went back in, every player being far. It comes out again next time. */
    public void onGarrisonReturn()
    {
        this.out = Math.max(0, this.out - 1);
        this.setChanged();
    }

    public int returnRange()
    {
        return this.returnRange;
    }

    private boolean isEmpty()
    {
        for (int slot = 0; slot < SLOTS; slot++)
        {
            if (!this.slots.getItem(slot).isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    private boolean lightAllows(ServerLevel level)
    {
        // Sky and block light together, with the time of day: dark at night or underground.
        return !this.lightSensitive || level.getMaxLocalRawBrightness(this.worldPosition.above()) <= 7;
    }

    /** A survival or adventure player within range: an admin building a structure does not set it off. */
    private boolean playerWithin(ServerLevel level, double range)
    {
        for (Player player : level.players())
        {
            if (!player.isSpectator() && !player.isCreative() && player.distanceToSqr(this.worldPosition.getCenter()) <= range * range)
            {
                return true;
            }
        }
        return false;
    }

    private int nearbyCount(ServerLevel level)
    {
        double reach = this.spawnRadius + 8.0D;
        return level.getEntitiesOfClass(Mob.class, new AABB(this.worldPosition).inflate(reach),
                mob -> mob instanceof NpcEntity npc ? this.worldPosition.equals(npc.getSpawnerPos()) : mob.isAlive()).size();
    }

    private int nextDelay(RandomSource random)
    {
        return this.minDelay + (this.maxDelay > this.minDelay ? random.nextInt(this.maxDelay - this.minDelay + 1) : 0);
    }

    /**
     * What admins see over the spawner, as AW's spawner showed its first mob's
     * name: the first scroll's NPC custom name, else the scroll's own name,
     * then how many it holds and how many other kinds.
     */
    public Component label()
    {
        ItemStack first = ItemStack.EMPTY;
        int count = 0;
        int kinds = 0;
        for (int slot = 0; slot < SLOTS; slot++)
        {
            ItemStack stack = this.slots.getItem(slot);
            if (stack.isEmpty())
            {
                continue;
            }
            if (first.isEmpty())
            {
                first = stack;
            }
            count += stack.getCount();
            kinds++;
        }
        if (first.isEmpty())
        {
            return Component.translatable("block.ancientempires.npc_spawner.empty");
        }
        Component name = first.getHoverName();
        String custom = first.getTag() == null ? "" : first.getTag().getCompound("EntityTag").getString("CustomName");
        if (!custom.isEmpty())
        {
            try
            {
                Component parsed = Component.Serializer.fromJson(custom);
                if (parsed != null)
                {
                    name = parsed;
                }
            }
            catch (RuntimeException exception)
            {
                name = Component.literal(custom);
            }
        }
        return kinds > 1 ? Component.translatable("block.ancientempires.npc_spawner.label_more", name, count, kinds - 1)
                : Component.translatable("block.ancientempires.npc_spawner.label", name, count);
    }

    /** Tall enough for the admin nametag over it (NpcSpawnerRenderer). */
    @Override
    public AABB getRenderBoundingBox()
    {
        return new AABB(this.worldPosition).expandTowards(0.0D, 1.0D, 0.0D);
    }

    public String clientLabel()
    {
        return this.label;
    }

    /** Sends the new nametag to watching clients, from the server thread only (never during generation). */
    private void relabel()
    {
        if (this.level instanceof ServerLevel server && server.getServer().isSameThread())
        {
            server.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Only the nametag goes to clients; the scrolls stay on the server. */
    @Override
    public CompoundTag getUpdateTag()
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("Label", Component.Serializer.toJson(this.label()));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag)
    {
        this.label = tag.getString("Label");
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet)
    {
        if (packet.getTag() != null)
        {
            this.handleUpdateTag(packet.getTag());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < SLOTS; slot++)
        {
            items.set(slot, this.slots.getItem(slot));
        }
        ContainerHelper.saveAllItems(tag, items);
        tag.putString("Mode", this.mode.name());
        tag.putInt("PlayerRange", this.playerRange);
        tag.putInt("SpawnRadius", this.spawnRadius);
        tag.putInt("ReturnRange", this.returnRange);
        tag.putInt("MinDelay", this.minDelay);
        tag.putInt("MaxDelay", this.maxDelay);
        tag.putInt("MaxNearby", this.maxNearby);
        tag.putInt("TotalLimit", this.totalLimit);
        tag.putBoolean("LightSensitive", this.lightSensitive);
        tag.putInt("Out", this.out);
        tag.putInt("Spawned", this.spawned);
        tag.putBoolean("Powered", this.powered);
        tag.putInt("ClearXp", this.clearXp);
        tag.putBoolean("Pool", this.pool);
        tag.putBoolean("Hostile", this.hostile);
        tag.putBoolean("Settled", this.settled);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        for (int slot = 0; slot < SLOTS; slot++)
        {
            this.slots.setItem(slot, items.get(slot));
        }
        try
        {
            this.mode = Mode.valueOf(tag.getString("Mode"));
        }
        catch (IllegalArgumentException exception)
        {
            this.mode = Mode.GARRISON;
        }
        this.playerRange = tag.contains("PlayerRange") ? tag.getInt("PlayerRange") : 16;
        this.spawnRadius = tag.contains("SpawnRadius") ? tag.getInt("SpawnRadius") : 4;
        this.returnRange = tag.contains("ReturnRange") ? tag.getInt("ReturnRange") : 48;
        this.minDelay = tag.contains("MinDelay") ? tag.getInt("MinDelay") : 200;
        this.maxDelay = tag.contains("MaxDelay") ? tag.getInt("MaxDelay") : 800;
        this.maxNearby = tag.contains("MaxNearby") ? tag.getInt("MaxNearby") : 6;
        this.totalLimit = tag.getInt("TotalLimit");
        this.lightSensitive = tag.getBoolean("LightSensitive");
        this.out = tag.getInt("Out");
        this.spawned = tag.getInt("Spawned");
        this.powered = tag.getBoolean("Powered");
        this.clearXp = tag.getInt("ClearXp");
        this.pool = tag.getBoolean("Pool");
        this.hostile = !tag.contains("Hostile") || tag.getBoolean("Hostile");
        this.settled = tag.getBoolean("Settled");
    }
}
