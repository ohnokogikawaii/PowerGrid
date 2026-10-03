package org.patryk3211.powergrid.compat.electroenergetics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;

public class ElectroEnergeticsBridgeBlockEntity
        extends ElectricBlockEntity {

    /*
     * Power Grid side:
     *
     * Terminal 0 = +
     * Terminal 1 = -
     */
    private CurrentSourceCoupling electroEnergeticsCurrentSource;

    /*
     * Last current received from Electro Energetics.
     *
     * Positive:
     *     EE Node 0 -> EE Node 1
     *
     * which corresponds to:
     *     Power Grid + -> Power Grid -
     */
    private double electroEnergeticsCurrent = 0.0;

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
     */

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        /*
         * Terminal 0 = +
         * Terminal 1 = -
         */
        builder.setTerminalCount(2);

        /*
         * Create a two-terminal current source.
         *
         * The current is updated by the Electro Energetics
         * simulation after it has calculated its circuit.
         */
        electroEnergeticsCurrentSource =
                builder.addInternalNode(
                        CurrentSourceCoupling.class,
                        builder.terminalNode(0),
                        builder.terminalNode(1)
                );
    }

    /*
     * ------------------------------------------------------------
     * EE -> Power Grid current
     * ------------------------------------------------------------
     */

    /**
     * Set the current calculated by Electro Energetics.
     *
     * Positive current means:
     *
     *     Power Grid + -> Power Grid -
     */
    public void setElectroEnergeticsCurrent(double current) {
        /*
         * Protect the Power Grid solver from invalid values.
         */
        if (!Double.isFinite(current)) {
            current = 0.0;
        }

        electroEnergeticsCurrent = current;

        if (electroEnergeticsCurrentSource != null) {
            electroEnergeticsCurrentSource.setCurrent(current);
        }
    }

    /**
     * Get the last current received from Electro Energetics.
     */
    public double getElectroEnergeticsCurrent() {
        return electroEnergeticsCurrent;
    }

    /*
     * ------------------------------------------------------------
     * Power Grid -> EE voltage
     * ------------------------------------------------------------
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