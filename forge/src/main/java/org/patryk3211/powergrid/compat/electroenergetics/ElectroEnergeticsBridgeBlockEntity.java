package org.patryk3211.powergrid.compat.electroenergetics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;

public class ElectroEnergeticsBridgeBlockEntity
        extends ElectricBlockEntity {

    public ElectroEnergeticsBridgeBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        super(
                ElectroEnergeticsBridgeRegistration
                        .BRIDGE_BLOCK_ENTITY
                        .get(),
                pos,
                state
        );
    }

    /*
     * ------------------------------------------------------------
     * Power Grid circuit
     * ------------------------------------------------------------
     *
     * Terminal 0 = +
     * Terminal 1 = -
     */

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);
    }

    /*
     * ------------------------------------------------------------
     * Differential voltage
     * ------------------------------------------------------------
     *
     * This is the voltage that is exported to Electro Energetics.
     */

    public double getBridgeVoltage() {
        if (level == null) {
            return 0.0;
        }

        ElectricBehaviour behaviour =
                getBehaviour(ElectricBehaviour.TYPE);

        if (behaviour == null) {
            return 0.0;
        }

        OwnedFloatingNode positive =
                behaviour.getTerminal(0);

        OwnedFloatingNode negative =
                behaviour.getTerminal(1);

        if (positive == null || negative == null) {
            return 0.0;
        }

        return positive.getVoltage()
                - negative.getVoltage();
    }

    /*
     * ------------------------------------------------------------
     * Positive terminal voltage
     * ------------------------------------------------------------
     */

    public double getPositiveVoltage() {
        if (level == null) {
            return 0.0;
        }

        ElectricBehaviour behaviour =
                getBehaviour(ElectricBehaviour.TYPE);

        if (behaviour == null) {
            return 0.0;
        }

        OwnedFloatingNode node =
                behaviour.getTerminal(0);

        if (node == null) {
            return 0.0;
        }

        return node.getVoltage();
    }

    /*
     * ------------------------------------------------------------
     * Negative terminal voltage
     * ------------------------------------------------------------
     */

    public double getNegativeVoltage() {
        if (level == null) {
            return 0.0;
        }

        ElectricBehaviour behaviour =
                getBehaviour(ElectricBehaviour.TYPE);

        if (behaviour == null) {
            return 0.0;
        }

        OwnedFloatingNode node =
                behaviour.getTerminal(1);

        if (node == null) {
            return 0.0;
        }

        return node.getVoltage();
    }
}