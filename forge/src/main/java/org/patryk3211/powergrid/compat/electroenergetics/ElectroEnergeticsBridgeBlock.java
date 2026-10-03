package org.patryk3211.powergrid.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class ElectroEnergeticsBridgeBlock
        extends Rotation4ElectricBlock
        implements IBE<ElectroEnergeticsBridgeBlockEntity>,
        ElectricalDeviceBlock<ElectroEnergeticsBridgeDevice> {

    /*
     * ============================================================
     * Power Grid terminals
     * ============================================================
     *
     * The two Power Grid terminals are located on the TOP surface
     * of the block when the block is placed normally.
     *
     * Terminal 0 = positive
     * Terminal 1 = negative
     */

    private static final TerminalBoundingBox[] TERMINALS =
            new TerminalBoundingBox[] {
                    /*
                     * Power Grid +
                     *
                     * Top surface, left side.
                     */
                    new TerminalBoundingBox(
                            IDecoratedTerminal.POSITIVE,
                            3.5,
                            12.0,
                            5.5,
                            8.5,
                            15.0,
                            10.5
                    ).withColor(IDecoratedTerminal.RED),

                    /*
                     * Power Grid -
                     *
                     * Top surface, right side.
                     */
                    new TerminalBoundingBox(
                            IDecoratedTerminal.NEGATIVE,
                            7.5,
                            12.0,
                            5.5,
                            12.5,
                            15.0,
                            10.5
                    ).withColor(IDecoratedTerminal.BLUE)
            };

    /*
     * ============================================================
     * Block shape
     * ============================================================
     */

    private static final VoxelShape SHAPE =
            box(
                    2.5,
                    0.0,
                    2.5,
                    13.5,
                    12.0,
                    13.5
            );

    /*
     * ============================================================
     * Electro Energetics nodes
     * ============================================================
     *
     * EE has its own two electrical connection nodes.
     *
     * Node 0 = EE +
     * Node 1 = EE -
     *
     * They are deliberately NOT the same physical terminals as
     * the Power Grid terminals.
     *
     * The nodes are located on opposite side faces:
     *
     *       North
     *         |
     *      EE +
     *
     *      [BLOCK]
     *
     *      EE -
     *         |
     *       South
     *
     * The positions are defined for the default DOWN orientation
     * and subsequently transformed using the same orientation
     * rules as the Power Grid terminals.
     */

    private static final Vec3[] EE_NODE_POSITIONS =
            new Vec3[] {
                    /*
                     * EE +
                     * North side
                     */
                    new Vec3(
                            8.0 / 16.0,
                            7.0 / 16.0,
                            1.0 / 16.0
                    ),

                    /*
                     * EE -
                     * South side
                     */
                    new Vec3(
                            8.0 / 16.0,
                            7.0 / 16.0,
                            15.0 / 16.0
                    )
            };

    public ElectroEnergeticsBridgeBlock(Properties properties) {
        super(properties);

        setTerminalCollection(
                Rotation4ElectricBlock.rotation4DownTerminals(
                        this,
                        TERMINALS,
                        SHAPE
                )
        );
    }

    /*
     * ============================================================
     * Block Entity
     * ============================================================
     */

    @Override
    public Class<ElectroEnergeticsBridgeBlockEntity> getBlockEntityClass() {
        return ElectroEnergeticsBridgeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ElectroEnergeticsBridgeBlockEntity>
    getBlockEntityType() {
        return ElectroEnergeticsBridgeRegistration
                .BRIDGE_BLOCK_ENTITY
                .get();
    }

    /*
     * ============================================================
     * Electro Energetics device
     * ============================================================
     */

    @Override
    public SimulatedDeviceType<ElectroEnergeticsBridgeDevice> getDevice() {
        return ElectroEnergeticsCompat.BRIDGE_DEVICE.get();
    }

    /*
     * ============================================================
     * Electro Energetics node positions
     * ============================================================
     */

    @Override
    public Map<Integer, Vec3> getNodePositions(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        Map<Integer, Vec3> nodes = new LinkedHashMap<>();

        nodes.put(
                0,
                transformNodePosition(
                        EE_NODE_POSITIONS[0],
                        state
                )
        );

        nodes.put(
                1,
                transformNodePosition(
                        EE_NODE_POSITIONS[1],
                        state
                )
        );

        return nodes;
    }

    @Override
    public @Nullable Vec3 getNodePosition(
            Level level,
            BlockPos pos,
            BlockState state,
            int id
    ) {
        if (id < 0 || id >= EE_NODE_POSITIONS.length)
            return null;

        return transformNodePosition(
                EE_NODE_POSITIONS[id],
                state
        );
    }

    /*
     * ============================================================
     * Node labels
     * ============================================================
     */

    @Override
    public MutableComponent getNodeLabel(
            Level level,
            BlockPos pos,
            BlockState state,
            int id
    ) {
        return Component.literal(
                switch (id) {
                    case 0 -> "Electro Energetics +";
                    case 1 -> "Electro Energetics -";
                    default -> "Electro Energetics Node";
                }
        );
    }

    @Override
    public float getNodeSize(
            Level level,
            BlockPos pos,
            BlockState state,
            int id
    ) {
        return 6.0f / 16.0f;
    }

    @Override
    public boolean isNodeAccessible(
            Level level,
            BlockPos pos,
            BlockState state,
            int id
    ) {
        return id == 0 || id == 1;
    }

    /*
     * ============================================================
     * EE node registration
     * ============================================================
     *
     * ElectricalDeviceBlock itself does not schedule the initial
     * registration. This is why this block explicitly schedules a
     * tick after placement.
     *
     * This follows the same mechanism used by EE's
     * SimpleElectricalDeviceBlock.
     */

    @Override
    public void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean movedByPiston
    ) {
        super.onPlace(
                state,
                level,
                pos,
                oldState,
                movedByPiston
        );

        level.scheduleTick(
                pos,
                this,
                1
        );
    }

    @Override
    public void tick(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random
    ) {
        ArrayList<Integer> nodes =
                new ArrayList<>(
                        getNodePositions(
                                level,
                                pos,
                                state
                        ).keySet()
                );

        InfrastructureSavedData sd =
                InfrastructureSavedData.load(level);

        sd.registerOrUpdateNodes(
                pos,
                nodes
        );
    }

    /*
     * ============================================================
     * Remove EE nodes when the block is actually destroyed.
     * ============================================================
     *
     * A state change belonging to this same block is ignored so
     * that rotation/orientation changes do not delete the nodes.
     */

    @Override
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean isMoving
    ) {
        if (state.getBlock() != newState.getBlock()
                && level instanceof ServerLevel serverLevel) {

            InfrastructureSavedData sd =
                    InfrastructureSavedData.load(serverLevel);

            sd.removeNodes(pos);
        }

        super.onRemove(
                state,
                level,
                pos,
                newState,
                isMoving
        );
    }

    /*
     * ============================================================
     * Normal block interaction
     * ============================================================
     */

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        return InteractionResult.PASS;
    }

    /*
     * ============================================================
     * Power Grid wire compatibility
     * ============================================================
     */

    @Override
    public boolean accepts(ItemStack wireStack) {
        return true;
    }

    /*
     * ============================================================
     * Node transformation
     * ============================================================
     *
     * Rotation4ElectricBlock can orient the block in all six
     * directions and rotate it around its facing axis.
     *
     * The EE nodes must follow the same transformation as the
     * Power Grid terminals.
     */

    private static Vec3 transformNodePosition(
            Vec3 original,
            BlockState state
    ) {
        Direction facing =
                state.getValue(FACING);

        int rotation =
                state.getValue(ROTATION);

        Vec3 result = original;

        /*
         * First apply the FACING transformation.
         *
         * These transformations mirror TerminalBoundingBox's
         * rotateAroundX/Y/Z implementations.
         */

        result = switch (facing) {
            case DOWN -> result;

            case UP ->
                    rotateX(result, 180);

            case EAST ->
                    rotateY(
                            rotateZ(result, 90),
                            180
                    );

            case WEST ->
                    rotateZ(result, 90);

            case NORTH ->
                    rotateY(
                            rotateZ(result, 90),
                            90
                    );

            case SOUTH ->
                    rotateY(
                            rotateZ(result, 90),
                            -90
                    );
        };

        /*
         * Apply Rotation4.
         *
         * This is intentionally kept identical to the rotation
         * logic used by Rotation4ElectricBlock.
         */

        int angle;

        if (facing == Direction.SOUTH
                || facing == Direction.EAST) {

            angle = -(90 * rotation - 90);

        } else {

            angle = 90 * rotation - 90;
        }

        result = switch (facing.getAxis()) {
            case X -> rotateX(result, angle);
            case Y -> rotateY(result, angle);
            case Z -> rotateZ(result, angle);
        };

        return result;
    }

    private static Vec3 rotateX(
            Vec3 v,
            int angle
    ) {
        return switch (normalizeAngle(angle)) {
            case 0 -> v;

            case 90 ->
                    new Vec3(
                            v.x,
                            v.z,
                            1.0 - v.y
                    );

            case 180 ->
                    new Vec3(
                            v.x,
                            1.0 - v.y,
                            1.0 - v.z
                    );

            case 270 ->
                    new Vec3(
                            v.x,
                            1.0 - v.z,
                            v.y
                    );

            default -> v;
        };
    }

    private static Vec3 rotateY(
            Vec3 v,
            int angle
    ) {
        return switch (normalizeAngle(angle)) {
            case 0 -> v;

            case 90 ->
                    new Vec3(
                            1.0 - v.z,
                            v.y,
                            v.x
                    );

            case 180 ->
                    new Vec3(
                            1.0 - v.x,
                            v.y,
                            1.0 - v.z
                    );

            case 270 ->
                    new Vec3(
                            v.z,
                            v.y,
                            1.0 - v.x
                    );

            default -> v;
        };
    }

    private static Vec3 rotateZ(
            Vec3 v,
            int angle
    ) {
        return switch (normalizeAngle(angle)) {
            case 0 -> v;

            case 90 ->
                    new Vec3(
                            v.y,
                            1.0 - v.x,
                            v.z
                    );

            case 180 ->
                    new Vec3(
                            1.0 - v.x,
                            1.0 - v.y,
                            v.z
                    );

            case 270 ->
                    new Vec3(
                            1.0 - v.y,
                            v.x,
                            v.z
                    );

            default -> v;
        };
    }

    private static int normalizeAngle(int angle) {
        angle %= 360;

        if (angle < 0)
            angle += 360;

        return angle;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        super.createBlockStateDefinition(builder);
    }
}