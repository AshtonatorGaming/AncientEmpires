package com.ancientempires.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.ancientempires.registry.ModEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * One of AW's decor blocks, its states and boxes from {@link AwDecor.Spec}. AW's
 * {@code facing}, {@code axis}, and {@code lit} become vanilla's properties (so
 * structures turn them), other yes/no ones booleans, the rest named values. A
 * {@code visible=false} state is the hidden upper part of a tall piece: drawn
 * by the part below, it only collides.
 */
public class AwDecorBlock extends Block implements EntityBlock
{
    private static final ThreadLocal<List<Property<?>>> BUILDING = new ThreadLocal<>();
    /** Table legs by corner, clockwise, so a turn moves each leg one corner on. */
    private static final String[] LEGS = {"leg_northeast", "leg_southeast", "leg_southwest", "leg_northwest"};

    /** One part of a tall piece: blocks up and along (away from its front), and the properties that mark it. Part 0 is the one that drops. */
    private record Part(int up, int along, Map<String, String> marks)
    {
    }

    private static final List<Part> TWO_HIGH = List.of(new Part(0, 0, Map.of("visible", "true")), new Part(1, 0, Map.of("visible", "false")));
    private static final List<Part> FOUR_HIGH = List.of(new Part(0, 0, Map.of("part", "bottom", "visible", "true")),
            new Part(1, 0, Map.of("part", "bottom", "visible", "false")), new Part(2, 0, Map.of("part", "top", "visible", "true")),
            new Part(3, 0, Map.of("part", "top", "visible", "false")));
    /** AW's tall pieces, as its blocks placed them ({@code onBlockPlacedBy}). */
    private static final Map<String, List<Part>> LAYOUTS = Map.of(
            "chair", TWO_HIGH, "tribal_chair", TWO_HIGH, "wooden_throne", TWO_HIGH,
            "golden_throne", List.of(new Part(0, 0, Map.of("part", "bottom")), new Part(1, 0, Map.of("part", "top"))),
            "golden_idol", List.of(new Part(0, 0, Map.of("bottom_part", "true")), new Part(1, 0, Map.of("bottom_part", "false"))),
            "stake", List.of(new Part(0, 0, Map.of("part", "bottom", "visible", "true")), new Part(1, 0, Map.of("part", "top", "visible", "true")),
                    new Part(2, 0, Map.of("part", "top", "visible", "false"))),
            "wooden_post", FOUR_HIGH, "iron_cage", FOUR_HIGH, "gibbet", FOUR_HIGH,
            "stretching_rack", List.of(new Part(0, 0, Map.of("part", "south", "visible", "true")), new Part(0, 1, Map.of("part", "south", "visible", "false")),
                    new Part(0, 2, Map.of("part", "north", "visible", "true")), new Part(0, 3, Map.of("part", "north", "visible", "false"))));
    /** AW's seats and how high one sits on each ({@code getSeatOffset}). */
    private static final Map<String, Double> SEATS = Map.of("chair", 0.47D, "bench", 0.35D, "stool", 0.35D, "tribal_stool", 0.4D,
            "tribal_chair", 0.5D, "scissor_seat", 0.5D, "wooden_throne", 0.5D, "golden_throne", 0.5D);

    private final AwDecor.Spec spec;
    private final List<Property<?>> properties;
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public static AwDecorBlock create(AwDecor.Spec spec, Properties settings)
    {
        List<Property<?>> properties = properties(spec);
        BUILDING.set(properties);
        try
        {
            return new AwDecorBlock(spec, properties, settings);
        }
        finally
        {
            BUILDING.remove();
        }
    }

    protected AwDecorBlock(AwDecor.Spec spec, List<Property<?>> properties, Properties settings)
    {
        super(settings);
        this.spec = spec;
        this.properties = properties;
        BlockState state = this.stateDefinition.any();
        for (Property<?> property : properties)
        {
            if (property instanceof BooleanProperty flag && !property.getName().startsWith("leg_"))
            {
                state = state.setValue(flag, flag.getName().equals("visible") || flag.getName().equals("lit") || flag.getName().equals("bottom_part"));
            }
        }
        this.registerDefaultState(state);
        for (BlockState possible : this.stateDefinition.getPossibleStates())
        {
            this.shapes.put(possible, this.shapeOf(possible));
        }
    }

    /** Gravestones keep loot and a stake's foot keeps its mob ({@link AwDecorBlockEntity}); the rest hold nothing. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        boolean holds = this.spec.name().equals("gravestone") || this.spec.name().startsWith("altar_candle") || this.spec.name().endsWith("_cloth")
                || this.spec.name().equals("stake") && partOf(state, LAYOUTS.get("stake")) == LAYOUTS.get("stake").get(0);
        return holds ? new AwDecorBlockEntity(pos, state) : null;
    }

    public String decorName()
    {
        return this.spec.name();
    }

    private static List<Property<?>> properties(AwDecor.Spec spec)
    {
        List<Property<?>> properties = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : spec.properties().entrySet())
        {
            String name = entry.getKey();
            List<String> values = entry.getValue();
            if (name.equals("facing") && values.size() == 4)
            {
                properties.add(BlockStateProperties.HORIZONTAL_FACING);
            }
            else if (name.equals("axis"))
            {
                properties.add(BlockStateProperties.AXIS);
            }
            else if (name.equals("lit"))
            {
                properties.add(BlockStateProperties.LIT);
            }
            else if (values.size() == 2 && values.contains("true") && values.contains("false"))
            {
                properties.add(BooleanProperty.create(name));
            }
            else
            {
                properties.add(new AwStringProperty(name, values));
            }
        }
        return properties;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BUILDING.get().toArray(new Property<?>[0]));
    }

    private VoxelShape shapeOf(BlockState state)
    {
        StringJoiner key = new StringJoiner(",");
        for (Property<?> property : this.properties)
        {
            key.add(property.getName() + "=" + valueName(state, property));
        }
        VoxelShape shape = Shapes.empty();
        for (double[] box : this.spec.shapes().getOrDefault(key.toString(), List.of()))
        {
            shape = Shapes.or(shape, Block.box(box[0], box[1], box[2], box[3], box[4], box[5]));
        }
        return shape.optimize();
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property)
    {
        return property.getName(state.getValue(property));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        VoxelShape shape = this.shapes.get(state);
        return shape == null || shape.isEmpty() ? Shapes.block() : shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return this.shapes.getOrDefault(state, Shapes.empty());
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockState state = this.defaultBlockState();
        Direction looking = context.getHorizontalDirection();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
        {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, looking.getOpposite());
        }
        if (state.hasProperty(BlockStateProperties.AXIS))
        {
            state = state.setValue(BlockStateProperties.AXIS, looking.getClockWise().getAxis());
        }
        state = this.connected(state, context.getLevel(), context.getClickedPos());
        List<Part> layout = LAYOUTS.get(this.spec.name());
        if (layout != null)
        {
            state = marked(state, layout.get(0));
            for (Part part : layout.subList(1, layout.size()))
            {
                BlockPos at = partPos(context.getClickedPos(), state, part);
                if (context.getLevel().isOutsideBuildHeight(at) || !context.getLevel().getBlockState(at).canBeReplaced(context))
                {
                    return null;
                }
            }
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos)
    {
        return this.connected(state, level, pos);
    }

    /**
     * AW's table and bench legs, worked out from neighbours as its getActualState
     * did: a table has a leg at a corner only where neither side touching it has
     * another table; a bench (along its axis, only benches lying the same way)
     * has legs at its free ends.
     */
    private BlockState connected(BlockState state, BlockGetter level, BlockPos pos)
    {
        BooleanProperty[] legs = this.legs(state);
        if (legs != null)
        {
            boolean north = level.getBlockState(pos.north()).is(this);
            boolean east = level.getBlockState(pos.east()).is(this);
            boolean south = level.getBlockState(pos.south()).is(this);
            boolean west = level.getBlockState(pos.west()).is(this);
            return state.setValue(legs[0], !north && !east).setValue(legs[1], !south && !east)
                    .setValue(legs[2], !south && !west).setValue(legs[3], !north && !west);
        }
        Property<?> benchLegs = this.stateDefinition.getProperty("legs");
        if (benchLegs instanceof AwStringProperty named && state.hasProperty(BlockStateProperties.AXIS))
        {
            Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
            String value;
            if (axis == Direction.Axis.X)
            {
                boolean west = this.sameBench(state, level.getBlockState(pos.west()));
                boolean east = this.sameBench(state, level.getBlockState(pos.east()));
                value = west && !east ? "left" : !west && east ? "right" : !west ? "both" : "none";
            }
            else
            {
                boolean south = this.sameBench(state, level.getBlockState(pos.south()));
                boolean north = this.sameBench(state, level.getBlockState(pos.north()));
                value = north && !south ? "right" : !north && south ? "left" : !north ? "both" : "none";
            }
            return named.getPossibleValues().contains(value) ? state.setValue(named, value) : state;
        }
        return state;
    }

    private boolean sameBench(BlockState state, BlockState other)
    {
        return other.is(this) && other.getValue(BlockStateProperties.AXIS) == state.getValue(BlockStateProperties.AXIS);
    }

    /** The rest of a tall piece goes up with it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        List<Part> layout = LAYOUTS.get(this.spec.name());
        if (layout == null || level.isClientSide)
        {
            return;
        }
        for (Part part : layout.subList(1, layout.size()))
        {
            level.setBlock(partPos(pos, state, part), marked(state, part), Block.UPDATE_ALL);
        }
    }

    /** Breaking any part takes the whole piece down; the first part is the one that drops. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        List<Part> layout = LAYOUTS.get(this.spec.name());
        Part self = layout == null ? null : partOf(state, layout);
        if (self != null && !level.isClientSide)
        {
            BlockPos origin = pos.below(self.up()).relative(along(state), -self.along());
            for (Part part : layout)
            {
                BlockPos at = partPos(origin, state, part);
                BlockState there = level.getBlockState(at);
                if (at.equals(pos) || there.getBlock() != this || partOf(there, layout) != part)
                {
                    continue;
                }
                if (part == layout.get(0) && !player.isCreative())
                {
                    Block.dropResources(there, level, at, null, player, player.getMainHandItem());
                }
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, at, Block.getId(there));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        ItemStack held = player.getItemInHand(hand);
        if (state.hasProperty(BlockStateProperties.LIT))
        {
            // AW's braziers and fire pits: flint and steel or a torch lights them, a water bucket puts them out.
            boolean lit = state.getValue(BlockStateProperties.LIT);
            if (!lit && (held.is(Items.FLINT_AND_STEEL) || held.is(Items.TORCH)))
            {
                if (!level.isClientSide)
                {
                    level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL);
                    held.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(hand));
                    level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (lit && held.is(Items.WATER_BUCKET))
            {
                if (!level.isClientSide)
                {
                    level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
                    if (!player.getAbilities().instabuild)
                    {
                        player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                    }
                    level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.0F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        Double seat = SEATS.get(this.spec.name());
        if (seat == null || player.isSecondaryUseActive())
        {
            return InteractionResult.PASS;
        }
        List<Part> layout = LAYOUTS.get(this.spec.name());
        Part self = layout == null ? null : partOf(state, layout);
        BlockPos seatPos = self == null ? pos : pos.below(self.up());
        if (!level.isClientSide && !occupied(level, seatPos))
        {
            SeatEntity entity = ModEntities.SEAT.get().create(level);
            if (entity != null)
            {
                entity.configure(Vec3.atLowerCornerOf(seatPos).add(0.5D, seat, 0.5D), seatPos);
                level.addFreshEntity(entity);
                player.startRiding(entity);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean occupied(Level level, BlockPos seat)
    {
        for (SeatEntity entity : level.getEntitiesOfClass(SeatEntity.class, new AABB(seat).inflate(1.0D)))
        {
            if (entity.seat().equals(seat) && entity.isVehicle())
            {
                return true;
            }
        }
        return false;
    }

    /** The way a rack lies: away from its front (AW laid it out along the placer's view). */
    private static Direction along(BlockState state)
    {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite() : Direction.NORTH;
    }

    private static BlockPos partPos(BlockPos origin, BlockState state, Part part)
    {
        return origin.above(part.up()).relative(along(state), part.along());
    }

    private BlockState marked(BlockState state, Part part)
    {
        for (Map.Entry<String, String> mark : part.marks().entrySet())
        {
            for (Property<?> property : this.properties)
            {
                if (property.getName().equals(mark.getKey()))
                {
                    state = withValue(state, property, mark.getValue());
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value)
    {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    @Nullable
    private static Part partOf(BlockState state, List<Part> layout)
    {
        for (Part part : layout)
        {
            boolean matches = true;
            for (Map.Entry<String, String> mark : part.marks().entrySet())
            {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(mark.getKey());
                matches &= property != null && valueName(state, property).equals(mark.getValue());
            }
            if (matches)
            {
                return part;
            }
        }
        return null;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation)
    {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
        {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        }
        if (state.hasProperty(BlockStateProperties.AXIS) && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90))
        {
            Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
            state = state.setValue(BlockStateProperties.AXIS, axis == Direction.Axis.X ? Direction.Axis.Z : axis == Direction.Axis.Z ? Direction.Axis.X : axis);
        }
        int turns = switch (rotation)
        {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        if (turns > 0 && legs(state) != null)
        {
            BooleanProperty[] legs = legs(state);
            boolean[] old = new boolean[4];
            for (int i = 0; i < 4; i++)
            {
                old[i] = state.getValue(legs[i]);
            }
            for (int i = 0; i < 4; i++)
            {
                state = state.setValue(legs[(i + turns) % 4], old[i]);
            }
        }
        return state;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror)
    {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
        {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, mirror.mirror(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        }
        return state;
    }

    @Nullable
    private BooleanProperty[] legs(BlockState state)
    {
        BooleanProperty[] legs = new BooleanProperty[4];
        for (Property<?> property : this.properties)
        {
            for (int i = 0; i < 4; i++)
            {
                if (property.getName().equals(LEGS[i]) && property instanceof BooleanProperty flag)
                {
                    legs[i] = flag;
                }
            }
        }
        for (BooleanProperty leg : legs)
        {
            if (leg == null)
            {
                return null;
            }
        }
        return legs;
    }
}
