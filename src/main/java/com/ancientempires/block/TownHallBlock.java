package com.ancientempires.block;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.inventory.WorksiteMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Town Hall. Right-click opens its food store (food bundles or any food;
 * hoppers work too); its owner's NPCs nearby come here for rations. Sneak-right-click empty-handed shows how many of the
 * owner's NPCs it reaches, how many went hungry, its food, and the fallen it
 * has recorded. Owner or admin only. Powered by redstone (a lever, a button, a
 * tripwire), it sounds the alarm (AW's): every {@link #ALARM_PULSE} ticks its
 * owner's NPCs within {@code upkeep.range} answer it (NpcAlarmGoal), until the
 * power goes. Scheduled ticks only while it sounds; an idle hall costs nothing.
 */
public final class TownHallBlock extends BaseEntityBlock
{
    public TownHallBlock(Properties properties)
    {
        super(properties);
    }

    /** Ticks between the alarm's calls, and how long one call holds an NPC. */
    private static final int ALARM_PULSE = 40;
    private static final int ALARM_HOLD = 100;

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving)
    {
        super.neighborChanged(state, level, pos, neighbor, from, moving);
        if (level instanceof ServerLevel server && level.hasNeighborSignal(pos) && !server.getBlockTicks().hasScheduledTick(pos, this))
        {
            server.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.9F);
            if (level.getBlockEntity(pos) instanceof TownHallBlockEntity hall && hall.owner() != null)
            {
                Player owner = level.getPlayerByUUID(hall.owner());
                if (owner != null)
                {
                    owner.displayClientMessage(Component.translatable("message.ancientempires.town_hall.alarm"), true);
                }
            }
            server.scheduleTick(pos, this, 1);
        }
    }

    /** The alarm's call: the owner's NPCs in range answer it, and the bell rings now and then; again while powered. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (!level.hasNeighborSignal(pos))
        {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TownHallBlockEntity hall && hall.owner() != null)
        {
            double range = AncientEmpiresConfig.upkeepRange;
            for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, new AABB(pos).inflate(range, range / 2.0D, range),
                    npc -> hall.owner().equals(npc.getOwner())))
            {
                npc.alarm(pos, level.getGameTime() + ALARM_HOLD);
            }
        }
        if (level.getGameTime() % (ALARM_PULSE * 2L) < ALARM_PULSE)
        {
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.9F);
        }
        level.scheduleTick(pos, this, ALARM_PULSE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new TownHallBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof TownHallBlockEntity hall)
        {
            hall.setOwner(placer instanceof Player player ? player.getUUID() : null);
            NpcUpkeep.register(server, pos);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof TownHallBlockEntity hall) || !(level instanceof ServerLevel server))
        {
            return InteractionResult.PASS;
        }
        if (!NpcEntity.mayToggleCreative(player) && hall.owner() != null && !player.getUUID().equals(hall.owner()))
        {
            player.displayClientMessage(Component.translatable("message.ancientempires.farm.not_owner"), true);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty())
        {
            this.showStatus(server, pos, hall, player);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen((ServerPlayer) player, new SimpleMenuProvider((id, inventory, opener) ->
                new WorksiteMenu(id, inventory, hall), state.getBlock().getName()), pos);
        return InteractionResult.CONSUME;
    }

    /**
     * The owner's NPCs in reach and how many are hungry, the food in store, and
     * the fallen it has recorded, newest first: how each died, where, and how long ago.
     */
    private void showStatus(ServerLevel level, BlockPos pos, TownHallBlockEntity hall, Player player)
    {
        int total = 0;
        int hungry = 0;
        if (hall.owner() != null)
        {
            for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, new AABB(pos).inflate(AncientEmpiresConfig.upkeepRange),
                    npc -> hall.owner().equals(npc.getOwner())))
            {
                total++;
                if (npc.getNeeds().hungry())
                {
                    hungry++;
                }
            }
        }
        List<TownHallBlockEntity.Fallen> fallen = hall.fallen();
        player.displayClientMessage(Component.translatable("message.ancientempires.hall.status",
                total, hungry, (int) Math.floor(hall.storedRations()), fallen.size()), false);
        int shown = 0;
        for (int index = fallen.size() - 1; index >= 0 && shown < 8; index--, shown++)
        {
            TownHallBlockEntity.Fallen entry = fallen.get(index);
            long days = Math.max(0L, (level.getGameTime() - entry.time()) / 24000L);
            player.displayClientMessage(Component.translatable("message.ancientempires.hall.fallen", entry.message(),
                    entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(), days), false);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock()))
        {
            if (level.getBlockEntity(pos) instanceof TownHallBlockEntity hall)
            {
                for (ItemStack stack : hall.contents())
                {
                    Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
                }
            }
            if (level instanceof ServerLevel server)
            {
                NpcUpkeep.unregister(server, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
