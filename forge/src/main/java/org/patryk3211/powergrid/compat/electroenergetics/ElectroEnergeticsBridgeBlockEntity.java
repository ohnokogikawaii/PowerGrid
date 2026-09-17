package org.patryk3211.powergrid.compat.electroenergetics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;

public class ElectroEnergeticsBridgeBlockEntity extends ElectricBlockEntity {

    public ElectroEnergeticsBridgeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        // Power Grid side: terminal 0 = positive, terminal 1 = negative.
        builder.setTerminalCount(2);
    }

    public double getBridgeVoltage() {
        if (level == null)
            return 0;

        ElectricBehaviour behaviour =
                getBehaviour(ElectricBehaviour.TYPE);
        if (behaviour == null)
            return 0;

        OwnedFloatingNode positive = behaviour.getTerminal(0);
        OwnedFloatingNode negative = behaviour.getTerminal(1);
        if (positive == null || negative == null)
            return 0;

        return positive.getVoltage() - negative.getVoltage();
    }
}
