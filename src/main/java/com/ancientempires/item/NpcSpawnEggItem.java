package com.ancientempires.item;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcRoster;
import com.ancientempires.entity.npc.NpcVariant;
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
 * Spawn egg that records the player as owner while an owned NPC is created.
 * A variant egg also records its skin and loadout for the same spawn.
 * A roster egg records a faction entry and does not claim the placer.
 * The claim is read from the spawn event before the tracking packet.
 */
public class NpcSpawnEggItem extends ForgeSpawnEggItem
{
    @Nullable
    private final NpcVariant variant;
    @Nullable
    private final String rosterId;

    public NpcSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Item.Properties properties, @Nullable NpcVariant variant, @Nullable String rosterId)
    {
        super(type, backgroundColor, highlightColor, properties);
        this.variant = variant;
        this.rosterId = rosterId;
        if (variant != null || rosterId != null)
        {
            DispenserBlock.registerBehavior(this, new DispenseNpc(this));
        }
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
        boolean claim = server && !rosterSpawn && ownerId != null;
        if (server && rosterSpawn)
        {
            NpcEntity.beginSpawnRoster(this.rosterId);
        }
        if (claim)
        {
            NpcEntity.beginSpawnClaim(ownerId);
        }
        if (server && !rosterSpawn && this.variant != null)
        {
            NpcEntity.beginSpawnVariant(this.variant);
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
            if (server && !rosterSpawn && this.variant != null)
            {
                NpcEntity.endSpawnVariant();
            }
        }
    }

    /**
     * Replaces the vanilla egg dispense so a dispensed variant or faction still receives its kit.
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
            else if (this.egg.variant != null)
            {
                NpcEntity.beginSpawnVariant(this.egg.variant);
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
                NpcEntity.endSpawnVariant();
            }
            stack.shrink(1);
            level.gameEvent(GameEvent.ENTITY_PLACE, source.getPos(), GameEvent.Context.of(source.getBlockState()));
            return stack;
        }
    }
}
