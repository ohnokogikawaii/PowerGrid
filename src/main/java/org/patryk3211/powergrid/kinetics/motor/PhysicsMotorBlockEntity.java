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

package org.patryk3211.powergrid.kinetics.motor;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.advancements.PGAdvancementBehaviour;
import org.patryk3211.powergrid.collections.ModdedAdvancements;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;

import java.util.List;

public class PhysicsMotorBlockEntity
        extends GeneratingKineticBlockEntity
        implements IElectricEntity {

    public static final int TICK_RATE = 1;

    /**
     * Physics state of this motor.
     */
    protected final MotorState motorState = new MotorState();

    /**
     * Electric parameters are supplied by the block.
     */
    protected MotorParameters motorParameters;

    protected ElectricBehaviour electricBehaviour;

    @Nullable
    protected ThermalBehaviour thermalBehaviour;

    /**
     * Armature/coil used by the PowerGrid circuit.
     */
    protected ElectricWire coil;

    /**
     * Mechanical load obtained from the Create kinetic network.
     */
    protected float mechanicalLoadTorque = 0;

    /**
     * Current generated speed.
     *
     * Kept separately because Create expects the generator speed
     * through getGeneratedSpeed().
     */
    protected float generatedSpeed = 0;

    public PhysicsMotorBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    ) {
        super(type, pos, state);

        motorParameters =
                ((PhysicsMotorBlock) state.getBlock())
                        .getMotorParameters();
    }

    /**
     * Returns the parameters for this particular motor.
     */
    public MotorParameters getMotorParameters() {
        if (motorParameters == null) {
            motorParameters =
                    ((PhysicsMotorBlock) getBlockState().getBlock())
                            .getMotorParameters();
        }

        return motorParameters;
    }

    /**
     * Current physics state.
     */
    public MotorState getMotorState() {
        return motorState;
    }

    /**
     * Current simulated RPM.
     */
    public double getRPM() {
        return motorState.rpm();
    }

    /**
     * Current armature current.
     */
    public double getMotorCurrent() {
        return motorState.current();
    }

    /**
     * Current back EMF.
     */
    public double getBackEmf() {
        return motorState.backEmf();
    }

    /**
     * Current electromagnetic torque.
     */
    public double getMotorTorque() {
        return motorState.electromagneticTorque();
    }

    /**
     * Current external mechanical load.
     */
    public double getMechanicalLoadTorque() {
        return motorState.loadTorque();
    }

    /**
     * Create supplies the stress values of the kinetic network.
     *
     * We convert the stress ratio into a mechanical torque demand.
     *
     * 0 % load  -> 0 torque
     * 100 %     -> rated torque
     *
     * This gives the physics engine a real mechanical load signal
     * without changing the existing motor implementation.
     */
    @Override
    public void updateFromNetwork(
            float maxStress,
            float currentStress,
            int networkSize
    ) {
        super.updateFromNetwork(
                maxStress,
                currentStress,
                networkSize
        );

        double loadRatio;

        if (maxStress > 0) {
            loadRatio =
                    Math.max(
                            0,
                            Math.min(
                                    1,
                                    currentStress / maxStress
                            )
                    );
        } else {
            loadRatio = 0;
        }

        /*
         * The Create stress network describes how much mechanical
         * capacity is being consumed.
         *
         * Use the motor's rated torque as the 100 % reference.
         */
        mechanicalLoadTorque =
                (float) (
                        getMotorParameters().ratedTorque()
                                * loadRatio
                );
    }

    @Override
    public void addBehaviours(
            List<BlockEntityBehaviour> behaviours
    ) {
        super.addBehaviours(behaviours);

        electricBehaviour =
                new ElectricBehaviour(this);

        behaviours.add(electricBehaviour);

        /*
         * Keep the existing PowerGrid advancement integration.
         */
        var awards =
                new PGAdvancementBehaviour(
                        this,
                        ModdedAdvancements.ELECTRIC_MOTOR
                );

        behaviours.add(awards);

        /*
         * Thermal behaviour is intentionally attached to the new
         * motor independently from the existing electric motor.
         *
         * Copper loss is still calculated by MotorPhysics.
         * ThermalBehaviour can later be connected more precisely.
         */
        var parameters = getMotorParameters();

        var baseFactor =
                ThermalBehaviour.dissipationFactor(
                        parameters.ratedPower(),
                        150
                );

        thermalBehaviour =
                ThermalBehaviour.simple(
                        this,
                        3.5f,
                        baseFactor
                );

        if (thermalBehaviour != null) {
            behaviours.add(thermalBehaviour);
            awards.add(ModdedAdvancements.BLOW_UP);
        }
    }

    /**
     * Applies the electric power calculation used by PowerGrid's
     * existing thermal system.
     */
    protected void applyPower(
            AbstractElectricWire wire
    ) {
        if (wire == null)
            return;

        if (thermalBehaviour != null)
            thermalBehaviour.applyWirePower(wire);
    }

    @Override
    public void tick() {
        assert level != null;

        if (!level.isClientSide || isVirtual()) {

            /*
             * Let PowerGrid update the electrical/thermal side.
             */
            if (coil != null) {
                applyPower(coil);
            }

            /*
             * The electrical network supplies the terminal voltage.
             *
             * MotorPhysics then calculates:
             *
             *     V
             *     ↓
             *     current
             *     ↓
             *     torque
             *     ↓
             *     acceleration
             *     ↓
             *     RPM
             *     ↓
             *     back EMF
             */
            double voltage =
                    coil != null
                            ? coil.potentialDifference()
                            : 0;

            MotorPhysics.update(
                    getMotorParameters(),
                    motorState,
                    voltage,
                    mechanicalLoadTorque,
                    1.0 / 20.0
            );

            generatedSpeed =
                    (float) motorState.rpm();

            /*
             * Keep Create's kinetic network synchronized with the
             * simulated motor speed.
             */
            updateGeneratedRotation();
        }

        super.tick();
    }

    @Override
    public float getGeneratedSpeed() {
        DirectionalSpeed direction =
                new DirectionalSpeed(
                        generatedSpeed,
                        getBlockState()
                                .getValue(
                                        PhysicsMotorBlock.FACING
                                )
                );

        return convertToDirection(
                direction.speed,
                direction.direction
        );
    }

    @Override
    public void buildCircuit(
            CircuitBuilder builder
    ) {
        builder.setTerminalCount(2);

        coil =
                builder.connect(
                        getMotorParameters().resistance(),
                        builder.terminalNode(0),
                        builder.terminalNode(1)
                );
    }

    @Override
    public void remove() {
        super.remove();

        if (electricBehaviour != null) {
            electricBehaviour.remove();
        }
    }

    @Override
    protected void read(
            CompoundTag compound,
            HolderLookup.Provider registries,
            boolean clientPacket
    ) {
        super.read(
                compound,
                registries,
                clientPacket
        );

        generatedSpeed =
                compound.getFloat(
                        "GeneratedSpeed"
                );

        motorState.angularVelocity(
                MotorParameters.rpmToRadPerSecond(
                        generatedSpeed
                )
        );

        motorState.rpm(
                generatedSpeed
        );

        updateGeneratedRotation();
    }

    @Override
    protected void write(
            CompoundTag compound,
            HolderLookup.Provider registries,
            boolean clientPacket
    ) {
        super.write(
                compound,
                registries,
                clientPacket
        );

        compound.putFloat(
                "GeneratedSpeed",
                generatedSpeed
        );
    }

    /**
     * Small helper used to keep the Create direction conversion
     * explicit.
     */
    private static final class DirectionalSpeed {

        private final float speed;
        private final net.minecraft.core.Direction direction;

        private DirectionalSpeed(
                float speed,
                net.minecraft.core.Direction direction
        ) {
            this.speed = speed;
            this.direction = direction;
        }
    }
}