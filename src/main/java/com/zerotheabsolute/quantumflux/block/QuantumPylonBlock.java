package com.zerotheabsolute.quantumflux.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.zerotheabsolute.quantumflux.blockentity.QuantumPylonBlockEntity;
import com.zerotheabsolute.quantumflux.init.QFBlockEntities;
import com.zerotheabsolute.quantumflux.network.QFNetworking;
import com.zerotheabsolute.quantumflux.network.data.QuantumFluxNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class QuantumPylonBlock extends BaseEntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<QuantumPylonBlock> CODEC = simpleCodec(QuantumPylonBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final EnumProperty<PylonHalf> HALF = EnumProperty.create("half", PylonHalf.class);

    // Shapes follow the authored model closely so targeting and collision match the silhouette.
    private static final VoxelShape BOTTOM_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(1, 3, 1, 15, 4, 15),
            Block.box(3, 4, 3, 13, 10, 13),
            Block.box(2, 7, 2, 14, 9, 14),
            Block.box(5, 9, 5, 11, 16, 11),
            Block.box(3, 10, 3, 4.5, 15, 4.5),
            Block.box(11.5, 10, 3, 13, 15, 4.5),
            Block.box(3, 10, 11.5, 4.5, 15, 13),
            Block.box(11.5, 10, 11.5, 13, 15, 13)
    ).optimize();

    private static final VoxelShape TOP_SHAPE = Shapes.or(
            Block.box(5, 0, 5, 11, 5, 11),
            Block.box(3, 4, 3, 13, 6, 13),
            Block.box(6, 6, 6, 10, 13, 10),
            Block.box(3.5, 9, 2.5, 12.5, 11, 4.5),
            Block.box(3.5, 9, 11.5, 12.5, 11, 13.5),
            Block.box(2.5, 9, 4.5, 4.5, 11, 11.5),
            Block.box(11.5, 9, 4.5, 13.5, 11, 11.5),
            Block.box(2.75, 5.5, 2.75, 4.25, 15, 4.25),
            Block.box(11.75, 5.5, 2.75, 13.25, 15, 4.25),
            Block.box(2.75, 5.5, 11.75, 4.25, 15, 13.25),
            Block.box(11.75, 5.5, 11.75, 13.25, 15, 13.25)
    ).optimize();

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public QuantumPylonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(HALF, PylonHalf.BOTTOM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, HALF);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(HALF) == PylonHalf.BOTTOM ? BOTTOM_SHAPE : TOP_SHAPE;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ── Multiblock placement ──

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1
                && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return defaultBlockState().setValue(HALF, PylonHalf.BOTTOM);
        }
        return null; // can't place — no room for TOP half
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            level.setBlock(pos.above(),
                    state.setValue(HALF, PylonHalf.TOP).setValue(ACTIVE, state.getValue(ACTIVE)),
                    3);
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == PylonHalf.BOTTOM) {
            return true;
        }
        // TOP requires BOTTOM below
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == PylonHalf.BOTTOM;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            PylonHalf half = state.getValue(HALF);
            BlockPos otherPos = half == PylonHalf.BOTTOM ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);

            // Unregister from network EARLY (before BE is removed by onRemove/super)
            BlockPos bottomPos = half == PylonHalf.BOTTOM ? pos : pos.below();
            if (level.getBlockEntity(bottomPos) instanceof QuantumPylonBlockEntity be) {
                if (be.getNetworkId() != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(serverLevel);
                    if (manager.removePylonFromAny(bottomPos)) {
                        QFNetworking.broadcastNetworkLists(serverLevel, manager);
                    }
                    be.setNetworkId(null);
                }
            }

            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                if (player.isCreative() && half == PylonHalf.TOP) {
                    // Match vanilla double plants: remove the loot-bearing bottom before
                    // the top so updateShape cannot route it through a drop-producing path.
                    level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                }
                level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        PylonHalf half = state.getValue(HALF);
        // If the partner half disappears (commands, pistons, etc.), break self
        if ((half == PylonHalf.BOTTOM && direction == Direction.UP)
                || (half == PylonHalf.TOP && direction == Direction.DOWN)) {
            if (!neighborState.is(this)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ── Block Entity (BOTTOM only) ──

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == PylonHalf.BOTTOM
                ? new QuantumPylonBlockEntity(pos, state)
                : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(HALF) != PylonHalf.BOTTOM) return null;
        return createTickerHelper(type, QFBlockEntities.QUANTUM_PYLON_BE.get(),
                QuantumPylonBlockEntity::serverTick);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return state.getValue(HALF) == PylonHalf.BOTTOM;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(HALF) != PylonHalf.BOTTOM) return 0;
        if (level.getBlockEntity(pos) instanceof QuantumPylonBlockEntity be) {
            return be.getAnalogOutputSignal();
        }
        return 0;
    }

    @Override
    public void spawnAfterBreak(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && state.getValue(HALF) == PylonHalf.BOTTOM) {
            if (level.getBlockEntity(pos) instanceof QuantumPylonBlockEntity be) {
                be.getUpgrades().dropContents();
                be.unlinkAll();
                // Unregister from network
                if (be.getNetworkId() != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    QuantumFluxNetworkManager manager = QuantumFluxNetworkManager.get(serverLevel);
                    if (manager.removePylon(be.getNetworkId(), pos)) {
                        QFNetworking.broadcastNetworkLists(serverLevel, manager);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // ── PylonHalf enum ──

    public enum PylonHalf implements StringRepresentable {
        BOTTOM("bottom"),
        TOP("top");

        private final String name;

        PylonHalf(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
