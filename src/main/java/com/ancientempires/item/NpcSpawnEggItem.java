package com.ancientempires.item;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcVariant;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
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

import java.util.function.Supplier;

/**
 * Spawn egg that records the player as owner while the entity is created.
 * A variant egg also records its skin and loadout for the same spawn.
 * The claim is read from the spawn event before the tracking packet.
 */
public class NpcSpawnEggItem extends ForgeSpawnEggItem
{
    @Nullable
    private final NpcVariant variant;

    public NpcSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Item.Properties properties, @Nullable NpcVariant variant)
    {
        super(type, backgroundColor, highlightColor, properties);
        this.variant = variant;
        if (variant != null)
        {
            DispenserBlock.registerBehavior(this, new DispenseVariant(this));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        boolean claim = player != null && !context.getLevel().isClientSide;
        if (claim)
        {
            NpcEntity.beginSpawnClaim(player.getUUID());
            if (this.variant != null)
            {
                NpcEntity.beginSpawnVariant(this.variant);
            }
        }
        try
        {
            return super.useOn(context);
        }
        finally
        {
            if (claim)
            {
                NpcEntity.endSpawnClaim();
                NpcEntity.endSpawnVariant();
            }
        }
    }

    /**
     * Replaces the vanilla egg dispense so a dispensed variant still receives its kit.
     */
    private static final class DispenseVariant extends DefaultDispenseItemBehavior
    {
        private final NpcSpawnEggItem egg;

        private DispenseVariant(NpcSpawnEggItem egg)
        {
            this.egg = egg;
        }

        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack)
        {
            Direction direction = source.getBlockState().getValue(DispenserBlock.FACING);
            Level level = source.getLevel();
            if (!(level instanceof ServerLevel server) || this.egg.variant == null)
            {
                return stack;
            }
            NpcEntity.beginSpawnVariant(this.egg.variant);
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
                NpcEntity.endSpawnVariant();
            }
            stack.shrink(1);
            level.gameEvent(GameEvent.ENTITY_PLACE, source.getPos(), GameEvent.Context.of(source.getBlockState()));
            return stack;
        }
    }
}
