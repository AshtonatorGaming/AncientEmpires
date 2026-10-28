package com.ancientempires.block;

import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcFactions;
import com.ancientempires.registry.ModBlocks;
import com.ancientempires.structure.StructureClaims;
import com.ancientempires.structure.StructureRecords;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * AW's flags: a faction's standard on a pole, turned in sixteen steps like a
 * banner. A decorative flag is just that. A structure's protection flag
 * guards it (see {@link StructureClaims}): its blocks are hard to mine and its
 * containers locked until a player claims the flag, which takes clearing the
 * hostiles inside. Admins sneak-right-click to cycle a flag's faction.
 */
public class FlagBlock extends BaseEntityBlock
{
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    private static final VoxelShape POLE = Block.box(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D);
    /** Pole and the cloth's lower part; the rest is {@link FlagTopBlock} above. */
    private static final VoxelShape BANNER = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    public FlagBlock(Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(ROTATION);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockPos above = context.getClickedPos().above();
        if (context.getLevel().isOutsideBuildHeight(above) || !context.getLevel().getBlockState(above).canBeReplaced(context))
        {
            return null;
        }
        return this.defaultBlockState().setValue(ROTATION, Mth.floor((180.0F + context.getRotation()) * 16.0F / 360.0F + 0.5D) & 15);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return BANNER;
    }

    /** Only the pole is solid; the cloth is walked through. */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return POLE;
    }

    /** The banner's upper part, where there is room for it (a flag under a ceiling just goes without). */
    public static void placeTop(LevelAccessor level, BlockPos pos, int flags)
    {
        BlockPos above = pos.above();
        if (!level.isOutsideBuildHeight(above) && level.getBlockState(above).canBeReplaced())
        {
            level.setBlock(above, ModBlocks.FLAG_TOP.get().defaultBlockState(), flags);
        }
    }

    /** The pole and cloth are drawn by the flag's renderer. */
    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof FlagBlockEntity flag))
        {
            return InteractionResult.PASS;
        }
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown() && NpcEntity.mayToggleCreative(player))
        {
            List<String> factions = new ArrayList<>(NpcFactions.ids());
            factions.sort(String::compareTo);
            int next = (factions.indexOf(flag.faction()) + 1) % (factions.size() + 1);
            flag.setFaction(next == factions.size() ? "" : factions.get(next));
            player.displayClientMessage(Component.translatable("message.ancientempires.flag.faction",
                    flag.faction().isEmpty() ? "-" : flag.faction()), true);
            return InteractionResult.CONSUME;
        }
        if (flag.isProtection() && level instanceof ServerLevel server)
        {
            StructureClaims.tryClaim(server, pos, flag, player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Half the width of the area a hand-placed protection flag guards (structures give their own bounds). */
    private static final int MANUAL_RADIUS = 16;

    /**
     * A protection flag placed by hand (an admin building) guards the area
     * around it; one placed with a structure is tied to its record instead.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        placeTop(level, pos, Block.UPDATE_ALL);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof FlagBlockEntity flag && flag.isProtection() && flag.record() == 0)
        {
            BoundingBox box = new BoundingBox(pos.getX() - MANUAL_RADIUS, pos.getY() - 8, pos.getZ() - MANUAL_RADIUS,
                    pos.getX() + MANUAL_RADIUS, pos.getY() + 24, pos.getZ() + MANUAL_RADIUS);
            flag.setProtection(true, StructureRecords.get(server).add("manual", box, flag.faction(), pos).id);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new FlagBlockEntity(pos, state);
    }
}
