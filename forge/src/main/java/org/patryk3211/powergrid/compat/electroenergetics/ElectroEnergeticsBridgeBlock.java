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

    private static final TerminalBoundingBox[] TERMINALS =
            new TerminalBoundingBox[] {
                    new TerminalBoundingBox(
                            IDecoratedTerminal.POSITIVE,
                            5.5,
                            1,
                            1.5,
                            10.5,
                            4,
                            4.5
                    ).withColor(IDecoratedTerminal.RED),

                    new TerminalBoundingBox(
                            IDecoratedTerminal.NEGATIVE,
                            5.5,
                            1,
                            11.5,
                            10.5,
                            4,
                            14.5
                    ).withColor(IDecoratedTerminal.BLUE)
            };

    private static final VoxelShape SHAPE =
            box(
                    4.5,
                    0,
                    3.5,
                    11.5,
                    4,
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

    @Override
    public Class<ElectroEnergeticsBridgeBlockEntity> getBlockEntityClass() {
        return ElectroEnergeticsBridgeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ElectroEnergeticsBridgeBlockEntity>
    getBlockEntityType() {
        return ElectroEnergeticsBridgeRegistration.BRIDGE_BLOCK_ENTITY.get();
    }

    @Override
    public SimulatedDeviceType<ElectroEnergeticsBridgeDevice> getDevice() {
        return ElectroEnergeticsCompat.BRIDGE_DEVICE.get();
    }

    /*
     * EE nodes
     *
     * Node 0 = Power Grid +
     * Node 1 = Power Grid -
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
                new Vec3(
                        4.0 / 16.0,
                        1.0,
                        8.0 / 16.0
                )
        );

        nodes.put(
                1,
                new Vec3(
                        12.0 / 16.0,
                        1.0,
                        8.0 / 16.0
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

    @Override
    public MutableComponent getNodeLabel(
            Level level,
            BlockPos pos,
            BlockState state,
            int id
    ) {
        return Component.literal(
                id == 0
                        ? "Power Grid +"
                        : "Power Grid -"
        );
    }

    @Override
    public float getNodeSize(
            Level level,
            BlockPos pos,
            BlockState state,
            int id
    ) {
        return 4.0f / 16.0f;
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        /*
         * PASS keeps the normal block interaction available.
         */
        return InteractionResult.PASS;
    }

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