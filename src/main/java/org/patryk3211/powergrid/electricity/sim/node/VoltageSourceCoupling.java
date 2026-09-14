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

import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.sim.solver.IAdmittanceAdder;
import org.patryk3211.powergrid.electricity.sim.solver.IResidualAdder;
import org.patryk3211.powergrid.electricity.sim.solver.IStaticResidual;

import java.util.Collection;
import java.util.List;

public class VoltageSourceCoupling extends CouplingNode implements IStaticResidual {
    protected final IElectricNode positive;
    @Nullable
    protected final IElectricNode negative;
    private double voltage;
    private float resistance;
    private boolean mpptSource;

    public VoltageSourceCoupling(IElectricNode positive, @Nullable IElectricNode negative, float resistance) {
        this.positive = positive;
        this.negative = negative;
        this.resistance = resistance;
    }

    public VoltageSourceCoupling(IElectricNode positive, @Nullable IElectricNode negative, Number resistance) {
        this.positive = positive;
        this.negative = negative;
        this.resistance = resistance.floatValue();
    }

    public VoltageSourceCoupling(IElectricNode positive, @Nullable IElectricNode negative, float resistance, float voltage) {
        this(positive, negative, resistance);
        setVoltage(voltage);
    }
    private static final float BLOCKED_RESISTANCE = 1_000_000f;

    private boolean chargeBlocked;
    private boolean dischargeBlocked;
    public void setCurrentDirectionBlocked(
            boolean chargeBlocked,
            boolean dischargeBlocked
    ) {
        this.chargeBlocked = chargeBlocked;
        this.dischargeBlocked = dischargeBlocked;

        updateNetworkResistance();
    }

    private void updateNetworkResistance() {

        if (network == null)
            return;

        /*
         * 重要：
         *
         * 現在の抵抗と有効抵抗の差だけ変更する。
         */
        float effectiveResistance =
                getEffectiveResistance();

        float delta =
                effectiveResistance - resistance;

        if (Math.abs(delta) < 0.000001f)
            return;

        network.alterConductanceMatrix(
                index,
                index,
                -delta
        );
    }
    private float getEffectiveResistance() {

        double positiveVoltage =
                positive.getVoltage();

        double negativeVoltage =
                negative != null
                        ? negative.getVoltage()
                        : 0.0;

        double externalVoltage =
                positiveVoltage - negativeVoltage;

        /*
         * 充電方向
         */
        if (
                chargeBlocked
                        && externalVoltage > voltage
        ) {
            return BLOCKED_RESISTANCE;
        }

        /*
         * 放電方向
         */
        if (
                dischargeBlocked
                        && externalVoltage < voltage
        ) {
            return BLOCKED_RESISTANCE;
        }

        return resistance;
    }



    @Override
    public boolean isSource() {
        return true;
    }

    public void setVoltage(double voltage) {
        this.voltage = voltage;
    }

    public void setResistance(float resistance) {
        if(network != null)
            network.alterConductanceMatrix(this.index, this.index, -(resistance - this.resistance));
        this.resistance = resistance;
    }

    @Override
    public void couple(IAdmittanceAdder admittance) {
        admittance.add(this.index, positive.getIndex(),  1);
        admittance.add(positive.getIndex(), this.index,  1);
        admittance.add(this.index, this.index, -resistance);
        if(negative != null) {
            admittance.add(this.index, negative.getIndex(), -1);
            admittance.add(negative.getIndex(), this.index, -1);
        }
    }

    @Override
    public Collection<IElectricNode> coupledNodes() {
        if(negative == null)
            return List.of(positive);
        return List.of(positive, negative);
    }

    public double getCurrent() {
        return getStateValue();
    }

    public double getVoltage() {
        return voltage;
    }

    public float getResistance() {
        return resistance;
    }

    public IElectricNode getPositive() {
        return positive;
    }

    @Nullable
    public IElectricNode getNegative() {
        return negative;
    }

    @Override
    public void addStaticResidual(IResidualAdder residual) {
        residual.add(index, voltage);
    }

    @Override
    public List<INode> affectedNodes() {
        if(negative != null)
            return List.of(positive, negative);
        return List.of(positive);
    }

    @Override
    public String toString() {
        if(negative != null)
            return String.format("VoltageSource(%s %s V=%g)", positive, negative, voltage);
        return String.format("VoltageSource(%s V=%g)", positive, voltage);
    }

    public void setMpptSource(
            boolean mpptSource
    ) {
        this.mpptSource =
                mpptSource;
    }
}
