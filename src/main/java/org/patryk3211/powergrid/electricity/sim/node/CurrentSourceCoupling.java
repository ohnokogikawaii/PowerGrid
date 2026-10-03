/*
 * Copyright 2025 patryk3211
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.patryk3211.powergrid.electricity.sim.node;

import org.patryk3211.powergrid.electricity.sim.solver.IAdmittanceAdder;
import org.patryk3211.powergrid.electricity.sim.solver.IResidualAdder;
import org.patryk3211.powergrid.electricity.sim.solver.IStaticResidual;

import java.util.Collection;
import java.util.List;

/**
 * Ideal two-terminal current source.
 *
 * The coupling node has an auxiliary current variable.
 *
 * The MNA equations are:
 *
 *   I = current
 *
 * and the current is injected into the positive terminal
 * and removed from the negative terminal.
 */
public class CurrentSourceCoupling extends CouplingNode implements IStaticResidual {

    protected final IElectricNode positive;
    protected final IElectricNode negative;

    private double current;

    public CurrentSourceCoupling(
            IElectricNode positive,
            IElectricNode negative
    ) {
        this.positive = positive;
        this.negative = negative;
        this.current = 0.0;
    }

    public CurrentSourceCoupling(
            IElectricNode positive,
            IElectricNode negative,
            double current
    ) {
        this.positive = positive;
        this.negative = negative;
        this.current = current;
    }

    @Override
    public boolean isSource() {
        return true;
    }

    public void setCurrent(double current) {
        if (!Double.isFinite(current)) {
            current = 0.0;
        }

        this.current = current;
    }

    public double getCurrent() {
        return current;
    }

    public IElectricNode getPositive() {
        return positive;
    }

    public IElectricNode getNegative() {
        return negative;
    }

    /**
     * Build the MNA equations for the current source.
     *
     * The coupling node itself represents the current I.
     *
     * Positive terminal:
     *
     *     +I
     *
     * Negative terminal:
     *
     *     -I
     *
     * Coupling-node equation:
     *
     *     I = current
     *
     * This last equation is important. Without it the auxiliary
     * current row would be completely empty and the MNA matrix
     * would become singular.
     */
    @Override
    public void couple(IAdmittanceAdder admittance) {

        // Current flowing into the positive terminal.
        admittance.add(
                positive.getIndex(),
                index,
                1
        );

        // Current flowing out of the negative terminal.
        admittance.add(
                negative.getIndex(),
                index,
                -1
        );

        // Auxiliary current variable:
        //
        //     I = current
        //
        admittance.add(
                index,
                index,
                1
        );
    }

    @Override
    public Collection<IElectricNode> coupledNodes() {
        return List.of(
                positive,
                negative
        );
    }

    /**
     * Set the right-hand side of:
     *
     *     I = current
     */
    @Override
    public void addStaticResidual(IResidualAdder residual) {
        residual.add(
                index,
                current
        );
    }

    @Override
    public List<INode> affectedNodes() {
        return List.of(
                positive,
                negative
        );
    }

    @Override
    public String toString() {
        return String.format(
                "CurrentSource(%s %s I=%g)",
                positive,
                negative,
                current
        );
    }
}