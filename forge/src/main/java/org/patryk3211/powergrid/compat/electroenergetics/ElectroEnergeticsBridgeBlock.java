package org.patryk3211.powergrid.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

import java.util.LinkedHashMap;
import java.util.Map;

public class ElectroEnergeticsBridgeBlock
        extends Rotation4ElectricBlock
        implements IBE<ElectroEnergeticsBridgeBlockEntity>,
        ElectricalDeviceBlock<ElectroEnergeticsBridgeDevice> {

    /*
     * ------------------------------------------------------------
     * Power Grid terminals
     * ------------------------------------------------------------
     *
     * Terminal 0 = positive
     * Terminal 1 = negative
     *
     * These are intentionally kept as Power Grid terminals so that
     * existing Power Grid wires can connect to the bridge.
     */

    private static final TerminalBoundingBox[] TERMINALS =
            new TerminalBoundingBox[] {
                    new TerminalBoundingBox(
                            IDecoratedTerminal.POSITIVE,
                            5.5,
                            1.0,
                            1.5,
                            10.5,
                            4.0,
                            4.5
                    ).withColor(IDecoratedTerminal.RED),

                    new TerminalBoundingBox(
                            IDecoratedTerminal.NEGATIVE,
                            5.5,
                            1.0,
                            11.5,
                            10.5,
                            4.0,
                            14.5
                    ).withColor(IDecoratedTerminal.BLUE)
            };

    /*
     * ------------------------------------------------------------
     * Physical block shape
     * ------------------------------------------------------------
     *
     * Rotation4ElectricBlock uses this shape together with the
     * terminal bounding boxes to construct the final block outline.
     *
     * The slightly larger central body also makes the block itself
     * easier to interact with.
     */

    private static final VoxelShape SHAPE =
            box(
                    4.5,
                    0.0,
                    3.5,
                    11.5,
                    5.0,
                    12.5
            );

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
     * ------------------------------------------------------------
     * Power Grid Block Entity
     * ------------------------------------------------------------
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
     * ------------------------------------------------------------
     * Electro Energetics simulated device
     * ------------------------------------------------------------
     */

    @Override
    public SimulatedDeviceType<ElectroEnergeticsBridgeDevice> getDevice() {
        return ElectroEnergeticsCompat.BRIDGE_DEVICE.get();
    }

    /*
     * ------------------------------------------------------------
     * Electro Energetics nodes
     * ------------------------------------------------------------
     *
     * EE uses positions relative to the bottom corner of the block.
     *
     * These positions deliberately match the centers of the
     * corresponding Power Grid terminals.
     *
     * Node 0 = Power Grid +
     * Node 1 = Power Grid -
     *
     * Terminal centers:
     *
     * + : (8, 2.5, 3) / 16
     * - : (8, 2.5, 13) / 16
     */

    @Override
    public Map<Integer, Vec3> getNodePositions(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        Map<Integer, Vec3> nodes = new LinkedHashMap<>();

        /*
         * Positive node.
         */
        nodes.put(
                0,
                new Vec3(
                        8.0 / 16.0,
                        2.5 / 16.0,
                        3.0 / 16.0
                )
        );

        /*
         * Negative node.
         */
        nodes.put(
                1,
                new Vec3(
                        8.0 / 16.0,
                        2.5 / 16.0,
                        13.0 / 16.0
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
        return getNodePositions(
                level,
                pos,
                state
        ).get(id);
    }

    /*
     * ------------------------------------------------------------
     * Node labels
     * ------------------------------------------------------------
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
                    case 0 -> "Power Grid +";
                    case 1 -> "Power Grid -";
                    default -> "Power Grid Node";
                }
        );
    }

    /*
     * ------------------------------------------------------------
     * Node hitbox
     * ------------------------------------------------------------
     *
     * EE uses this value for node interaction/outline size.
     *
     * 6/16 is deliberately larger than the default 4/16 because
     * these nodes are placed on the small Power Grid terminals.
     */

    @Override
    public float getNodeSize(
            Level level,
            BlockPos pos,
            BlockState state,
            int id
    ) {
        return 6.0f / 16.0f;
    }

    /*
     * ------------------------------------------------------------
     * Node accessibility
     * ------------------------------------------------------------
     *
     * Both nodes are externally connectable.
     */

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
     * ------------------------------------------------------------
     * Block interaction
     * ------------------------------------------------------------
     *
     * PASS allows normal Power Grid / Create interactions to
     * continue through the block.
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
     * ------------------------------------------------------------
     * Power Grid wire compatibility
     * ------------------------------------------------------------
     */

    @Override
    public boolean accepts(ItemStack wireStack) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<
                    net.minecraft.world.level.block.Block,
                    BlockState
                    > builder
    ) {
        super.createBlockStateDefinition(builder);
    }
}