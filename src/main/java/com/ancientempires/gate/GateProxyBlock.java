package com.ancientempires.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The solid, invisible stand-in a closed gate fills its opening with (AW's
 * gate proxy), so collision and paths treat the gate as a wall without the
 * entity colliding. It cannot be mined; clicks and blows go to its gate.
 */
public class GateProxyBlock extends Block
{
    public GateProxyBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.block();
    }

    /** The gate whose opening or deck this cell is, or null (a stray proxy). */
    @Nullable
    public static GateEntity gateAt(Level level, BlockPos pos)
    {
        List<GateEntity> gates = level.getEntitiesOfClass(GateEntity.class, new AABB(pos).inflate(24.0D),
                gate -> gate.getBoundingBox().inflate(0.5D).contains(pos.getCenter()) || covers(gate, pos));
        return gates.isEmpty() ? null : gates.get(0);
    }

    private static boolean covers(GateEntity gate, BlockPos pos)
    {
        BlockPos min = gate.min();
        BlockPos max = gate.max();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        GateEntity gate = gateAt(level, pos);
        return gate == null ? InteractionResult.PASS : gate.interact(player, hand);
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player)
    {
        GateEntity gate = gateAt(level, pos);
        if (gate != null && !level.isClientSide)
        {
            gate.hurt(level.damageSources().playerAttack(player), (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
    }
}
