package org.patryk3211.powergrid.kinetics.motor;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.collections.ModdedBlockEntities;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.info.IHaveElectricProperties;
import org.patryk3211.powergrid.electricity.info.Resistance;
import org.patryk3211.powergrid.electricity.info.Voltage;
import org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord;
import org.patryk3211.powergrid.kinetics.base.ElectricKineticBlock;

import java.util.List;

public abstract class PhysicsMotorBlock
        extends ElectricKineticBlock
        implements IBE<PhysicsMotorBlockEntity>,
        IHaveElectricProperties,
        IAcceptCord {

    public static final DirectionProperty FACING =
            BlockStateProperties.FACING;

    public static final VoxelShape NORTH_SHAPE =
            Shapes.or(
                    box(
                            3, 3, 0.5,
                            13, 13, 15.5
                    ),
                    box(
                            2.5, 2.5, 10.5,
                            13.5, 13.5, 15.5
                    )
            );

    public static final VoxelShape UP_SHAPE =
            Shapes.or(
                    box(
                            3, 0.5, 3,
                            13, 15.5, 13
                    ),
                    box(
                            2.5, 0.5, 2.5,
                            13.5, 5.5, 13.5
                    )
            );

    private static final TerminalBoundingBox[] NORTH_TERMINALS =
            new TerminalBoundingBox[] {

                    new TerminalBoundingBox(
                            IDecoratedTerminal.POSITIVE,
                            5, 13, 14,
                            7, 14, 16
                    ).withColor(
                            IDecoratedTerminal.RED
                    ),

                    new TerminalBoundingBox(
                            IDecoratedTerminal.NEGATIVE,
                            9, 13, 14,
                            11, 14, 16
                    ).withColor(
                            IDecoratedTerminal.BLUE
                    )
            };

    protected PhysicsMotorBlock(Properties properties) {
        super(properties);

        setTerminalCollection(
                DirectionalElectricBlock.directionalNorthTerminals(
                        this,
                        NORTH_TERMINALS,
                        NORTH_SHAPE,
                        UP_SHAPE
                )
        );
    }

    public abstract MotorParameters getMotorParameters();

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    public Direction getPreferredFacing(
            BlockPlaceContext context
    ) {
        Direction preferredSide = null;

        for (Direction side : Iterate.directions) {

            BlockState blockState =
                    context.getLevel()
                            .getBlockState(
                                    context.getClickedPos()
                                            .relative(side)
                            );

            if (blockState.getBlock()
                    instanceof IRotate rotate) {

                if (rotate.hasShaftTowards(
                        context.getLevel(),
                        context.getClickedPos()
                                .relative(side),
                        blockState,
                        side.getOpposite()
                )) {

                    if (preferredSide != null
                            && preferredSide.getAxis()
                            != side.getAxis()) {

                        preferredSide = null;
                        break;

                    } else {
                        preferredSide = side;
                    }
                }
            }
        }

        return preferredSide == null
                ? null
                : preferredSide.getOpposite();
    }

    @Override
    public BlockState getStateForPlacement(
            BlockPlaceContext context
    ) {
        Direction preferred =
                getPreferredFacing(context);

        if (preferred == null
                || (
                context.getPlayer() != null
                        && context.getPlayer()
                        .isShiftKeyDown()
        )) {

            Direction looking =
                    context.getNearestLookingDirection();

            return defaultBlockState().setValue(
                    FACING,
                    context.getPlayer() != null
                            && context.getPlayer()
                            .isShiftKeyDown()
                            ? looking
                            : looking.getOpposite()
            );
        }

        return defaultBlockState().setValue(
                FACING,
                preferred.getOpposite()
        );
    }

    @Override
    public Direction.Axis getRotationAxis(
            BlockState state
    ) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(
            LevelReader world,
            BlockPos pos,
            BlockState state,
            Direction face
    ) {
        return face == state.getValue(FACING);
    }

    @Override
    public Class<PhysicsMotorBlockEntity>
    getBlockEntityClass() {
        return PhysicsMotorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PhysicsMotorBlockEntity>
    getBlockEntityType() {
        return ModdedBlockEntities.PHYSICS_MOTOR.get();
    }

    @Override
    public void appendProperties(
            ItemStack stack,
            Player player,
            List<Component> tooltip
    ) {
        MotorParameters parameters =
                getMotorParameters();

        Resistance.series(
                (float) parameters.resistance(),
                player,
                tooltip
        );

        Voltage.max(
                (int) parameters.ratedVoltage(),
                player,
                tooltip
        );
    }

    @Override
    public BlockState rotate(
            BlockState state,
            Rotation rotation
    ) {
        return state.setValue(
                FACING,
                rotation.rotate(
                        state.getValue(FACING)
                )
        );
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(
            BlockState state,
            Mirror mirror
    ) {
        return state.rotate(
                mirror.getRotation(
                        state.getValue(FACING)
                )
        );
    }

    @Override
    public boolean renderPlug() {
        return true;
    }
}