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
         *//*


        package org.patryk3211.powergrid.kinetics.motor;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.List;

import static org.patryk3211.powergrid.PowerGrid.maxRPM;

public abstract class PhysicsMotorBlockEntity
        extends GeneratingKineticBlockEntity
        implements IElectricEntity {

    */
/**
     * Number of Minecraft ticks represented by one normal physics update.
     *//*

    protected static final double TICK_SECONDS = 0.05;

    */
/**
     * Motor parameter set.
     *//*

    protected abstract MotorParameters parameters();

    protected final MotorState motorState = new MotorState();

    protected ElectricBehaviour electricBehaviour;

    @Nullable
    protected ThermalBehaviour thermalBehaviour;

    */
/**
     * Resistive part of the armature winding.
     *
     * Current through this wire is the motor armature current.
     *//*

    protected ElectricWire winding;

    */
/**
     * Back-EMF source.
     *
     * Its polarity is intentionally opposite to the applied motor voltage.
     *//*

    protected VoltageSourceCoupling backEmf;

    */
/**
     * Create-side mechanical load, expressed as torque.
     *//*

    protected double externalLoadTorque;

    */
/**
     * Last calculated electrical input voltage.
     *//*

    protected double appliedVoltage;

    */
/**
     * Last calculated armature current.
     *//*

    protected double armatureCurrent;

    */
/**
     * Whether the motor is currently electrically connected.
     *//*

    protected boolean electricallyConnected;

    public PhysicsMotorBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    ) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        electricBehaviour = new ElectricBehaviour(this);
        behaviours.add(electricBehaviour);

        */
/*
         * Thermal behaviour is deliberately conservative here.
         *
         * The actual copper loss is supplied by the motor physics
         * rather than relying on Create's kinetic stress calculation.
         *//*

        float maximumPower =
                (float) parameters().ratedPower();

        float baseFactor =
                ThermalBehaviour.dissipationFactor(maximumPower, 150);

        thermalBehaviour =
                ThermalBehaviour.simple(this, 3.5f, baseFactor);

        if (thermalBehaviour != null)
            behaviours.add(thermalBehaviour);
    }

    @Override
    public void remove() {
        super.remove();

        if (electricBehaviour != null)
            electricBehaviour.remove();
    }

    */
/**
     * Build the electrical equivalent circuit:
     *
     * terminal 0 (+)
     *      |
     *      +--- winding resistance ---+
     *                                  |
     *                             back EMF
     *                                  |
     * terminal 1 (-)
     *
     * The back-EMF source is oriented from terminal 1 toward the
     * internal winding node so that it opposes the applied voltage.
     *//*

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);

        var positive = builder.terminalNode(0);
        var negative = builder.terminalNode(1);

        */
/*
         * Internal node between the winding and the back-EMF source.
         *//*

        IElectricNode windingNode =
                builder.addInternalNode();

        */
/*
         * Armature winding resistance.
         *//*

        winding = builder.connect(
                parameters().resistance(),
                positive,
                windingNode
        );

        */
/*
         * Back EMF:
         *
         * positive = terminal 1
         * negative = internal winding node
         *
         * This makes the generated EMF oppose the applied voltage.
         *//*

        backEmf = builder.addInternalNode(
                VoltageSourceCoupling.class,
                negative,
                windingNode,
                parameters().resistance()
        );

        backEmf.setVoltage(0);

        electricallyConnected = true;
    }

    */
/**
     * Update the mechanical load supplied by Create.
     *
     * The Create kinetic network reports the current stress and maximum
     * stress. We convert that ratio into the torque currently demanded
     * from the motor.
     *//*

    @Override
    public void updateFromNetwork(
            float maxStress,
            float currentStress,
            int networkSize
    ) {
        super.updateFromNetwork(maxStress, currentStress, networkSize);

        double ratedTorque = parameters().ratedTorque();

        if (maxStress > 0) {
            double loadRatio =
                    Math.max(0.0, currentStress / maxStress);

            externalLoadTorque =
                    ratedTorque * Math.min(loadRatio, 1.0);
        } else {
            externalLoadTorque = 0;
        }

        */
/*
         * The motor can also be unloaded while still connected to a
         * kinetic network. A small friction torque remains inside
         * MotorPhysics.
         *//*

        motorState.setLoadTorque(externalLoadTorque);
    }

    */
/**
     * Main electrical + mechanical update.
     *
     * ElectricBlockEntity's normal tick calls electricalTick() on the
     * server side before its own super.tick().
     *//*

    @Override
    public void electricalTick() {
        if (level == null || level.isClientSide)
            return;

        if (winding == null || backEmf == null)
            return;

        */
/*
         * The resistor wire gives us the actual solved winding current.
         *
         * terminal 0 -> windingNode is the positive motor current
         * direction.
         *//*

        double current = winding.current();

        double voltage = winding.potentialDifference();

        */
/*
         * Protect against solver transients producing NaN/Infinity.
         *//*

        if (!Double.isFinite(current))
            current = 0;

        if (!Double.isFinite(voltage))
            voltage = 0;

        appliedVoltage = voltage;
        armatureCurrent = current;

        */
/*
         * Feed the solved electrical current into the mechanical motor
         * model.
         *//*

        motorState.setVoltage(voltage);
        motorState.setCurrent(current);
        motorState.setLoadTorque(externalLoadTorque);

        MotorPhysics.step(
                parameters(),
                motorState,
                TICK_SECONDS
        );

        */
/*
         * Update the generated back-EMF for the next electrical solve.
         *//*

        double emf = motorState.getBackEmf();

        if (!Double.isFinite(emf))
            emf = 0;

        emf = Math.max(0, emf);

        backEmf.setVoltage(emf);

        */
/*
         * Thermal power is the copper loss:
         *
         * P = I²R
         *//*

        if (thermalBehaviour != null) {
            double copperLoss =
                    motorState.getCopperLoss();

            if (Double.isFinite(copperLoss) && copperLoss > 0)
                thermalBehaviour.applyTickPower(copperLoss);
        }

        */
/*
         * Update Create's rotational speed.
         *//*

        updateMotorSpeed();

        */
/*
         * Persist important state.
         *//*

        setChanged();
    }

    */
/**
     * Convert MotorPhysics RPM into Create's generated speed.
     *
     * The motor's own maximum RPM is respected in addition to Create's
     * global kinetic limit.
     *//*

    protected void updateMotorSpeed() {
        double rpm = motorState.getRpm();

        if (!Double.isFinite(rpm))
            rpm = 0;

        double allowed =
                Math.min(parameters().maxRPM(), maxRPM());

        rpm = Math.max(-allowed, Math.min(allowed, rpm));

        motorState.setRpm(rpm);

        float generated =
                (float) rpm;

        */
/*
         * Keep the generated speed smooth. Unlike the original
         * ElectricMotorBlockEntity, there is intentionally no lazy
         * averaging here because MotorPhysics already contains the
         * motor inertia.
         *//*

        if (Math.abs(generated - generatedSpeed) > 0.001f) {
            generatedSpeed = generated;
            updateGeneratedRotation();
        }
    }

    */
/**
     * Create asks the block entity for its generated rotational speed.
     *//*

    @Override
    public float getGeneratedSpeed() {
        return convertToDirection(
                generatedSpeed,
                getMotorFacing()
        );
    }

    */
/**
     * Physics motors use the same orientation property as their block.
     *
     * Subclasses override this if their block uses a different property.
     *//*

    protected net.minecraft.core.Direction getMotorFacing() {
        return getBlockState()
                .getValue(PhysicsMotorBlock.FACING);
    }

    */
/**
     * Current motor RPM.
     *//*

    public double getRPM() {
        return motorState.getRpm();
    }

    */
/**
     * Current armature current.
     *//*

    public double getArmatureCurrent() {
        return armatureCurrent;
    }

    */
/**
     * Current back EMF.
     *//*

    public double getBackEmf() {
        return motorState.getBackEmf();
    }

    */
/**
     * Current electromagnetic torque.
     *//*

    public double getElectromagneticTorque() {
        return motorState.getElectromagneticTorque();
    }

    */
/**
     * Current mechanical load torque.
     *//*

    public double getLoadTorque() {
        return externalLoadTorque;
    }

    */
/**
     * Current copper loss.
     *//*

    public double getCopperLoss() {
        return motorState.getCopperLoss();
    }

    */
/**
     * Used by the future motor sound implementation.
     *
     * 0 = stopped
     * 1 = maximum motor speed
     *//*

    public float getSoundRPMFactor() {
        double max = parameters().maxRPM();

        if (max <= 0)
            return 0;

        return (float) Math.min(
                1.0,
                Math.abs(motorState.getRpm()) / max
        );
    }

    */
/**
     * Used by the future motor sound implementation.
     *
     * Represents the electrical load relative to rated current.
     *//*

    public float getSoundLoadFactor() {
        double ratedCurrent =
                parameters().ratedCurrent();

        if (ratedCurrent <= 0)
            return 0;

        return (float) Math.min(
                1.5,
                Math.abs(armatureCurrent) / ratedCurrent
        );
    }

    @Override
    protected void read(
            CompoundTag compound,
            HolderLookup.Provider registries,
            boolean clientPacket
    ) {
        super.read(compound, registries, clientPacket);

        motorState.setAngularVelocity(
                compound.getDouble("AngularVelocity")
        );

        motorState.setAngularAcceleration(
                compound.getDouble("AngularAcceleration")
        );

        motorState.setRpm(
                compound.getDouble("RPM")
        );

        motorState.setVoltage(
                compound.getDouble("Voltage")
        );

        motorState.setCurrent(
                compound.getDouble("Current")
        );

        motorState.setBackEmf(
                compound.getDouble("BackEmf")
        );

        motorState.setElectromagneticTorque(
                compound.getDouble("ElectromagneticTorque")
        );

        motorState.setLoadTorque(
                compound.getDouble("LoadTorque")
        );

        motorState.setFrictionTorque(
                compound.getDouble("FrictionTorque")
        );

        motorState.setNetTorque(
                compound.getDouble("NetTorque")
        );

        motorState.setElectricalPower(
                compound.getDouble("ElectricalPower")
        );

        motorState.setMechanicalPower(
                compound.getDouble("MechanicalPower")
        );

        motorState.setCopperLoss(
                compound.getDouble("CopperLoss")
        );

        motorState.setTemperature(
                compound.getDouble("Temperature")
        );

        generatedSpeed =
                (float) motorState.getRpm();
    }

    @Override
    protected void write(
            CompoundTag compound,
            HolderLookup.Provider registries,
            boolean clientPacket
    ) {
        super.write(compound, registries, clientPacket);

        compound.putDouble(
                "AngularVelocity",
                motorState.getAngularVelocity()
        );

        compound.putDouble(
                "AngularAcceleration",
                motorState.getAngularAcceleration()
        );

        compound.putDouble(
                "RPM",
                motorState.getRpm()
        );

        compound.putDouble(
                "Voltage",
                motorState.getVoltage()
        );

        compound.putDouble(
                "Current",
                motorState.getCurrent()
        );

        compound.putDouble(
                "BackEmf",
                motorState.getBackEmf()
        );

        compound.putDouble(
                "ElectromagneticTorque",
                motorState.getElectromagneticTorque()
        );

        compound.putDouble(
                "LoadTorque",
                motorState.getLoadTorque()
        );
*/
