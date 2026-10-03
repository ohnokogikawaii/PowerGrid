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

import org.patryk3211.powergrid.electricity.sim.solver.IResidualAdder;
import org.patryk3211.powergrid.electricity.sim.solver.IStaticResidual;

import java.util.Collection;
import java.util.List;

/**
 * Two-terminal ideal current source.
 *
 * Positive current flows from positive to negative.
 *
 * This is intended to represent an externally calculated current
 * flowing between two Power Grid nodes.
 */
public class CurrentSourceCoupling
        extends CouplingNode
        implements IStaticResidual {

    protected final IElectricNode positive;
    protected final IElectricNode negative;

    /**
     * Current flowing from positive -> negative.
     */
    protected double current;

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

    /**
     * Set current flowing from positive -> negative.
     */
    public void setCurrent(double current) {
        this.current = current;
    }

    /**
     * Get current flowing from positive -> negative.
     */
    public double getCurrent() {
        return current;
    }

    public IElectricNode getPositive() {
        return positive;
    }

    public IElectricNode getNegative() {
        return negative;
    }

    @Override
    public void couple(
            org.patryk3211.powergrid.electricity.sim.solver.IAdmittanceAdder admittance
    ) {
        /*
         * Ideal current source does not modify the admittance matrix.
         */
    }

    @Override
    public Collection<IElectricNode> coupledNodes() {
        return List.of(
                positive,
                negative
        );
    }

    @Override
    public void addStaticResidual(IResidualAdder residual) {
        /*
         * Positive current means:
         *
         *      positive -----> negative
         *
         * Therefore current leaves the positive node and enters
         * the negative node.
         */
        residual.add(
                positive.getIndex(),
                current
        );

        residual.add(
                negative.getIndex(),
                -current
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