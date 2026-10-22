package com.ancientempires.item;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcRoster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Spawn egg for one roster row. An owned job egg records the placer.
 * A faction egg records that row and does not claim the placer.
 * The claim is read from the spawn event before the tracking packet.
 */
public class NpcSpawnEggItem extends ForgeSpawnEggItem
{
    private static final int WHITE = 0xFFFFFFFF;
    private static final int FACTION_TOP = 0xFFEF5757;

    @Nullable
    private final String rosterId;

    public NpcSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Item.Properties properties, @Nullable String rosterId)
    {
        super(type, backgroundColor, highlightColor, properties);
        this.rosterId = rosterId;
        if (rosterId != null)
        {
            DispenserBlock.registerBehavior(this, new DispenseNpc(this));
        }
    }

    @Nullable
    public String rosterId()
    {
        return this.rosterId;
    }

    /**
     * The scroll tint. Forge registers its own color handler for every Forge
     * spawn egg, and it reads this method, so this is the one source of the
     * colors. Whichever handler registers last, the scroll stays tinted.
     * Layer 1 is the faction color, layer 2 the wax. Owned eggs stay white.
     */
    @Override
    public int getColor(int tint)
    {
        NpcRoster.Entry entry = this.rosterId == null ? null : NpcRoster.byId(this.rosterId);
        if (entry == null || entry.owned())
        {
            return WHITE;
        }
        if (tint == 1)
        {
            return entry.color();
        }
        if (tint == 2)
        {
            return FACTION_TOP;
        }
        return WHITE;
    }

    @Override
    public Component getName(ItemStack stack)
    {
        if (this.rosterId != null)
        {
            NpcRoster.Entry entry = NpcRoster.byId(this.rosterId);
            if (entry != null && !entry.name().isEmpty())
            {
                return Component.literal(entry.name());
            }
        }
        return super.getName(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        UUID ownerId = player == null ? null : player.getUUID();
        boolean server = !context.getLevel().isClientSide;
        boolean rosterSpawn = this.rosterId != null;
        boolean claim = server && ownerId != null && this.claimsOwner();
        if (server && rosterSpawn)
        {
            NpcEntity.beginSpawnRoster(this.rosterId);
        }
        if (claim)
        {
            NpcEntity.beginSpawnClaim(ownerId);
        }
        try
        {
            return super.useOn(context);
        }
        finally
        {
            if (server && rosterSpawn)
            {
                NpcEntity.endSpawnRoster();
            }
            if (claim)
            {
                NpcEntity.endSpawnClaim();
            }
        }
    }

    /**
     * Faction rows never claim. Owned job rows claim the player who places them.
     */
    /**
     * Brings an NPC out of its scroll with no player placing it: a priest
     * raising the fallen. The scroll's saved data (name, look, path, gear) is
     * applied as when placed, and the owner given here claims it.
     */
    @Nullable
    public static NpcEntity spawnFrom(ServerLevel level, ItemStack scroll, BlockPos pos, @Nullable UUID owner)
    {
        if (!(scroll.getItem() instanceof NpcSpawnEggItem egg))
        {
            return null;
        }
        if (egg.rosterId != null)
        {
            NpcEntity.beginSpawnRoster(egg.rosterId);
        }
        if (owner != null)
        {
            NpcEntity.beginSpawnClaim(owner);
        }
        try
        {
            if (egg.getType(scroll.getTag()).spawn(level, scroll, null, pos, MobSpawnType.MOB_SUMMONED, true, false) instanceof NpcEntity npc)
            {
                if (owner != null && npc.getOwner() == null && !npc.isFactionNpc())
                {
                    npc.setOwner(owner);
                }
                return npc;
            }
            return null;
        }
        finally
        {
            NpcEntity.endSpawnRoster();
            NpcEntity.endSpawnClaim();
        }
    }

    private boolean claimsOwner()
    {
        if (this.rosterId == null)
        {
            return false;
        }
        NpcRoster.Entry entry = NpcRoster.byId(this.rosterId);
        return entry != null && entry.owned();
    }

    /**
     * Replaces the vanilla egg dispense so a dispensed NPC still receives its kit.
     * A dispenser has no player, so the spawn is not claimed.
     */
    private static final class DispenseNpc extends DefaultDispenseItemBehavior
    {
        private final NpcSpawnEggItem egg;

        private DispenseNpc(NpcSpawnEggItem egg)
        {
            this.egg = egg;
        }

        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack)
        {
            Direction direction = source.getBlockState().getValue(DispenserBlock.FACING);
            Level level = source.getLevel();
            if (!(level instanceof ServerLevel server))
            {
                return stack;
            }
            if (this.egg.rosterId != null)
            {
                NpcEntity.beginSpawnRoster(this.egg.rosterId);
            }
            try
            {
                EntityType<?> type = this.egg.getType(stack.getTag());
                type.spawn(server, stack, null, source.getPos().relative(direction), MobSpawnType.DISPENSER, direction != Direction.UP, false);
            }
            catch (RuntimeException exception)
            {
                AncientEmpires.LOGGER.error("Could not dispense an NPC spawn egg at {}", source.getPos(), exception);
                return ItemStack.EMPTY;
            }
            finally
            {
                NpcEntity.endSpawnRoster();
            }
            stack.shrink(1);
            level.gameEvent(GameEvent.ENTITY_PLACE, source.getPos(), GameEvent.Context.of(source.getBlockState()));
            return stack;
        }
    }
}
