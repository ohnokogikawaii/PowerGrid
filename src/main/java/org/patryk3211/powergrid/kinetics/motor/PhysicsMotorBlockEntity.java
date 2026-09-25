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
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.List;

import static org.patryk3211.powergrid.PowerGrid.maxRPM;

public class PhysicsMotorBlockEntity
        extends GeneratingKineticBlockEntity
        implements IElectricEntity {

    protected static final double DELTA_TIME = 0.05;

    protected final MotorState motorState =
            new MotorState();

    protected ElectricBehaviour electricBehaviour;

    @Nullable
    protected ThermalBehaviour thermalBehaviour;

    protected VoltageSourceCoupling motorSource;

    protected double externalLoadTorque;

    public PhysicsMotorBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    ) {
        super(type, pos, state);
    }

    /**
     * Get the parameters of the motor represented by this block.
     */
    protected MotorParameters parameters() {
        if (getBlockState().getBlock()
                instanceof PhysicsMotorBlock block) {

            return block.getMotorParameters();
        }

        throw new IllegalStateException(
                "PhysicsMotorBlockEntity is attached to a non-physics motor block"
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

        MotorParameters parameters =
                parameters();

        float maximumPower =
                (float) parameters.ratedPower();

        float dissipation =
                ThermalBehaviour.dissipationFactor(
                        maximumPower,
                        150
                );

        thermalBehaviour =
                ThermalBehaviour.simple(
                        this,
                        3.5f,
                        dissipation
                );

        if (thermalBehaviour != null)
            behaviours.add(thermalBehaviour);
    }

    @Override
    public void remove() {
        if (electricBehaviour != null)
            electricBehaviour.remove();

        super.remove();
    }

    @Override
    public void buildCircuit(
            CircuitBuilder builder
    ) {
        builder.setTerminalCount(2);

        /*
         * The motor is represented by one Thevenin-equivalent
         * voltage source.
         *
         * terminal 0 = motor positive
         * terminal 1 = motor negative
         *
         * The source voltage is the back EMF and therefore
         * opposes the externally applied voltage.
         */
        motorSource =
                builder.addInternalNode(
                        VoltageSourceCoupling.class,
                        builder.terminalNode(0),
                        builder.terminalNode(1),
                        parameters().resistance()
                );

        motorSource.setVoltage(0);
    }

    /**
     * Create kinetic load is represented as a torque magnitude.
     *
     * Create exposes stress rather than a physical torque value,
     * so the ratio of used stress to available capacity is used
     * to scale the motor's rated torque.
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

        if (maxStress <= 0) {
            externalLoadTorque = 0;
            return;
        }

        double loadRatio =
                currentStress / maxStress;

        loadRatio =
                Math.max(
                        0,
                        Math.min(1, loadRatio)
                );

        externalLoadTorque =
                parameters().ratedTorque()
                        * loadRatio;
    }

    public void electricalTick() {
        if (level == null || level.isClientSide)
            return;

        if (motorSource == null)
            return;

        MotorParameters parameters =
                parameters();

        /*
         * Current direction of VoltageSourceCoupling is the same
         * as the passive motor current:
         *
         * terminal 0 -> terminal 1
         */
        double current =
                motorSource.getCurrent();

        if (!Double.isFinite(current))
            current = 0;

        /*
         * Terminal voltage.
         */
        double voltage =
                motorSource.getPositive().getVoltage()
                        - (
                        motorSource.getNegative() != null
                                ? motorSource.getNegative().getVoltage()
                                : 0
                );

        if (!Double.isFinite(voltage))
            voltage = 0;

        /*
         * Update mechanical physics from the current that the
         * electrical network actually solved.
         */
        MotorPhysics.update(
                parameters,
                motorState,
                current,
                voltage,
                externalLoadTorque,
                DELTA_TIME
        );

        /*
         * Calculate the Thevenin equivalent of the winding
         * inductance for the NEXT solver iteration.
         */
        double equivalentResistance =
                MotorPhysics.equivalentResistance(
                        parameters,
                        DELTA_TIME
                );

        double equivalentVoltage =
                MotorPhysics.equivalentVoltage(
                        parameters,
                        motorState,
                        DELTA_TIME
                );

        /*
         * Enforce the motor's maximum current.
         *
         * This prevents an unrealistic infinite starting current
         * while still allowing a real inrush current up to the
         * motor's defined limit.
         */
        double maximumCurrent =
                parameters.maxCurrent();

        if (maximumCurrent > 0
                && Math.abs(equivalentVoltage)
                / equivalentResistance
                > maximumCurrent) {

            equivalentResistance =
                    Math.max(
                            equivalentResistance,
                            Math.abs(equivalentVoltage)
                                    / maximumCurrent
                    );
        }

        motorSource.setResistance(
                (float) equivalentResistance
        );

        motorSource.setVoltage(
                equivalentVoltage
        );

        /*
         * Thermal loss is the actual copper loss.
         */
        if (thermalBehaviour != null) {

            double copperLoss =
                    motorState.copperLoss();

            if (Double.isFinite(copperLoss)
                    && copperLoss > 0) {

                thermalBehaviour.applyTickPower(
                        copperLoss
                );
            }
        }

        /*
         * Update Create rotation.
         */
        updateMotorSpeed();
    }

    protected void updateMotorSpeed() {

        double rpm =
                motorState.rpm();

        if (!Double.isFinite(rpm))
            rpm = 0;

        double allowed =
                Math.min(
                        parameters().maxRPM(),
                        maxRPM()
                );

        rpm =
                Math.max(
                        -allowed,
                        Math.min(allowed, rpm)
                );

        motorState.rpm(rpm);

        float speed =
                (float) rpm;

        if (Math.abs(speed - getGeneratedSpeed()) > 0.001f)
            updateGeneratedRotation();
    }

    @Override
    public float getGeneratedSpeed() {

        float speed =
                (float) motorState.rpm();

        return convertToDirection(
                speed,
                getBlockState()
                        .getValue(
                                PhysicsMotorBlock.FACING
                        )
        );
    }

    public double getRPM() {
        return motorState.rpm();
    }

    public double getCurrent() {
        return motorState.current();
    }

    public double getVoltage() {
        return motorState.voltage();
    }

    public double getBackEmf() {
        return motorState.backEmf();
    }

    public double getTorque() {
        return motorState.electromagneticTorque();
    }

    public double getLoadTorque() {
        return motorState.loadTorque();
    }

    public double getMechanicalPower() {
        return motorState.mechanicalPower();
    }

    public double getElectricalPower() {
        return motorState.electricalPower();
    }

    public double getCopperLoss() {
        return motorState.copperLoss();
    }

    public float getSoundRPMFactor() {

        double maximum =
                parameters().maxRPM();

        if (maximum <= 0)
            return 0;

        return (float) Math.min(
                1,
                Math.abs(motorState.rpm())
                        / maximum
        );
    }

    public float getSoundLoadFactor() {

        double ratedCurrent =
                parameters().ratedCurrent();

        if (ratedCurrent <= 0)
            return 0;

        return (float) Math.min(
                1.5,
                Math.abs(motorState.current())
                        / ratedCurrent
        );
    }

    @Override
    protected void read(
            CompoundTag tag,
            HolderLookup.Provider registries,
            boolean clientPacket
    ) {
        super.read(
                tag,
                registries,
                clientPacket
        );

        motorState.voltage(
                tag.getDouble("MotorVoltage")
        );

        motorState.current(
                tag.getDouble("MotorCurrent")
        );

        motorState.backEmf(
                tag.getDouble("MotorBackEmf")
        );

        motorState.electromagneticTorque(
                tag.getDouble("MotorTorque")
        );

        motorState.loadTorque(
                tag.getDouble("MotorLoadTorque")
        );

        motorState.frictionTorque(
                tag.getDouble("MotorFriction")
        );

        motorState.netTorque(
                tag.getDouble("MotorNetTorque")
        );

        motorState.angularVelocity(
                tag.getDouble("MotorOmega")
        );

        motorState.angularAcceleration(
                tag.getDouble("MotorAlpha")
        );

        motorState.rpm(
                tag.getDouble("MotorRPM")
        );

        motorState.electricalPower(
                tag.getDouble("MotorElectricalPower")
        );

        motorState.mechanicalPower(
                tag.getDouble("MotorMechanicalPower")
        );

        motorState.copperLoss(
                tag.getDouble("MotorCopperLoss")
        );

        motorState.temperature(
                tag.getDouble("MotorTemperature")
        );
    }

    @Override
    protected void write(
            CompoundTag tag,
            HolderLookup.Provider registries,
            boolean clientPacket
    ) {
        super.write(
                tag,
                registries,
                clientPacket
        );

        tag.putDouble(
                "MotorVoltage",
                motorState.voltage()
        );

        tag.putDouble(
                "MotorCurrent",
                motorState.current()
        );

        tag.putDouble(
                "MotorBackEmf",
                motorState.backEmf()
        );

        tag.putDouble(
                "MotorTorque",
                motorState.electromagneticTorque()
        );

        tag.putDouble(
                "MotorLoadTorque",
                motorState.loadTorque()
        );

        tag.putDouble(
                "MotorFriction",
                motorState.frictionTorque()
        );

        tag.putDouble(
                "MotorNetTorque",
                motorState.netTorque()
        );

        tag.putDouble(
                "MotorOmega",
                motorState.angularVelocity()
        );

        tag.putDouble(
                "MotorAlpha",
                motorState.angularAcceleration()
        );

        tag.putDouble(
                "MotorRPM",
                motorState.rpm()
        );

        tag.putDouble(
                "MotorElectricalPower",
                motorState.electricalPower()
        );

        tag.putDouble(
                "MotorMechanicalPower",
                motorState.mechanicalPower()
        );

        tag.putDouble(
                "MotorCopperLoss",
                motorState.copperLoss()
        );

        tag.putDouble(
                "MotorTemperature",
                motorState.temperature()
        );
    }
}