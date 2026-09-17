package org.patryk3211.powergrid.compat.electroenergetics;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;

import java.util.LinkedHashMap;
import java.util.Map;

public class ElectroEnergeticsBridgeBlock extends DeviceConnectorBlock
        implements ElectricalDeviceBlock<ElectroEnergeticsBridgeDevice> {

    public ElectroEnergeticsBridgeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        // The bridge is self-supporting. Power Grid terminals remain on the
        // connector face inherited from DeviceConnectorBlock.
        return true;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, net.minecraft.world.InteractionHand hand,
                                 net.minecraft.world.phys.BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public Class<ElectroEnergeticsBridgeBlockEntity> getBlockEntityClass() {
        return ElectroEnergeticsBridgeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ElectroEnergeticsBridgeBlockEntity> getBlockEntityType() {
        return ElectroEnergeticsCompat.BRIDGE_BLOCK_ENTITY.get();
    }

    @Override
    public com.george_vi.electroenergetics.devices.device.SimulatedDeviceType<ElectroEnergeticsBridgeDevice> getDevice() {
        return ElectroEnergeticsCompat.BRIDGE_DEVICE.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Map<Integer, Vec3> nodes = new LinkedHashMap<>();
        nodes.put(0, new Vec3(4.0 / 16.0, 1.0, 8.0 / 16.0));
        nodes.put(1, new Vec3(12.0 / 16.0, 1.0, 8.0 / 16.0));
        return nodes;
    }

    @Override
    public @Nullable Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return getNodePositions(level, pos, state).get(id);
    }

    @Override
    public Component getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.literal(id == 0 ? "Power Grid +" : "Power Grid -");
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 4.0f / 16.0f;
    }

}
