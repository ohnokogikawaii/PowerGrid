package org.patryk3211.powergrid.kinetics.motor;

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

    /**
     * Simulation time per electrical/mechanical update.
     *
     * 20 ticks per second = 0.05 seconds.
     */
    protected static final double DELTA_TIME = 0.05;

    /**
     * Createに現在出力している回転速度。
     *
     * MotorState.rpm() は物理シミュレーション上のRPM、
     * generatedSpeed はCreateへ実際に渡している速度。
     */
    protected float generatedSpeed = 0;

    protected final MotorState motorState =
            new MotorState();

    protected ElectricBehaviour electricBehaviour;

    @Nullable
    protected ThermalBehaviour thermalBehaviour;

    /**
     * The Thevenin-equivalent electrical model of the motor.
     *
     * terminal 0 = positive
     * terminal 1 = negative
     *
     * Resistance represents:
     *
     *     winding resistance
     *     + inductive equivalent resistance
     *     + dynamic current limiting resistance
     *
     * Voltage represents the motor's back EMF and the
     * backward-Euler inductive source term.
     */
    protected VoltageSourceCoupling motorSource;

    /**
     * External mechanical load applied by the Create kinetic
     * network.
     */
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

        if (
                getBlockState().getBlock()
                        instanceof PhysicsMotorBlock block
        ) {

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
        super.addBehaviours(
                behaviours
        );

        electricBehaviour =
                new ElectricBehaviour(
                        this
                );

        behaviours.add(
                electricBehaviour
        );

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
            behaviours.add(
                    thermalBehaviour
            );
    }

    /**
     * PhysicsMotorBlockEntity does not extend ElectricBlockEntity,
     * so the electrical update must be called explicitly here.
     */
    @Override
    public void tick() {

        assert level != null;

        if (
                !level.isClientSide
                        || isVirtual()
        ) {

            electricalTick();
        }

        super.tick();
    }

    @Override
    public void remove() {

        super.remove();

        if (electricBehaviour != null) {

            electricBehaviour.remove();
        }
    }

    @Override
    public void buildCircuit(
            CircuitBuilder builder
    ) {
        builder.setTerminalCount(2);

        /*
         * Permanent-magnet DC motor equivalent circuit:
         *
         *       R_eq
         *  + ---/\/\/---[ E_back ]--- -
         *
         * terminal 0 -> terminal 1
         *
         * The electrical resistance is updated every simulation
         * step to include:
         *
         *     winding resistance
         *     inductive equivalent
         *     current limiting
         */
        motorSource =
                builder.addInternalNode(
                        VoltageSourceCoupling.class,
                        builder.terminalNode(0),
                        builder.terminalNode(1),
                        (float) parameters().resistance()
                );

        motorSource.setVoltage(0);
    }

    /**
     * Update the mechanical load from the Create kinetic network.
     *
     * Create exposes stress rather than physical torque, so the
     * stress ratio is converted into a fraction of the motor's
     * rated torque.
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
                currentStress
                        / maxStress;

        loadRatio =
                Math.max(
                        0,
                        Math.min(
                                1,
                                loadRatio
                        )
                );

        externalLoadTorque =
                parameters().ratedTorque()
                        * loadRatio;
    }

    /**
     * Perform one electrical/mechanical simulation step.
     *
     * Order:
     *
     * 1. Read the current solved by PowerGrid.
     * 2. Read terminal voltage.
     * 3. Advance the mechanical motor model.
     * 4. Calculate back EMF.
     * 5. Build the next electrical equivalent.
     * 6. Increase equivalent resistance when current limiting
     *    is required.
     * 7. Feed the equivalent back into PowerGrid.
     */
    public void electricalTick() {

        if (
                level == null
                        || level.isClientSide
        ) {
            return;
        }

        if (motorSource == null)
            return;

        MotorParameters parameters =
                parameters();

        /*
         * Current solved by the PowerGrid electrical network.
         */
        double current =
                motorSource.getCurrent();

        if (!Double.isFinite(current))
            current = 0;

        /*
         * Voltage across the external motor terminals.
         */
        double terminalVoltage =
                motorSource.getPositive().getVoltage()
                        - (
                        motorSource.getNegative() != null
                                ? motorSource.getNegative().getVoltage()
                                : 0
                );

        if (!Double.isFinite(terminalVoltage))
            terminalVoltage = 0;

        /*
         * Advance the mechanical model.
         *
         * MotorPhysics internally applies maxCurrent so that a
         * temporary electrical solver overshoot cannot create
         * impossible electromagnetic torque.
         */
        MotorPhysics.update(
                parameters,
                motorState,
                current,
                terminalVoltage,
                externalLoadTorque,
                DELTA_TIME
        );

        /*
         * Build the equivalent electrical model for the next
         * solver iteration.
         *
         *     R_eq = R + L/dt
         *
         *     V_eq =
         *         E + L/dt * I_previous
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
         * Apply the actual motor current limit.
         *
         * The resistance is increased only when necessary.
         *
         * At low current:
         *
         *     normal winding + inductive resistance
         *
         * At excessive current:
         *
         *     additional virtual resistance
         *
         * This preserves the normal motor characteristics while
         * preventing excessive armature current.
         */
        equivalentResistance =
                MotorPhysics.currentLimitedResistance(
                        parameters,
                        motorState,
                        terminalVoltage,
                        equivalentVoltage,
                        equivalentResistance
                );

        /*
         * Apply the electrical equivalent for the next solver pass.
         */
        motorSource.setResistance(
                (float) equivalentResistance
        );

        motorSource.setVoltage(
                -equivalentVoltage
        );

        /*
         * Feed actual copper loss into the thermal system.
         */
        if (thermalBehaviour != null) {

            double copperLoss =
                    motorState.copperLoss();

            if (
                    Double.isFinite(copperLoss)
                            && copperLoss > 0
            ) {

                thermalBehaviour.applyTickPower(
                        copperLoss
                );
            }
        }

        /*
         * Update Create's generated kinetic speed.
         */
        updateMotorSpeed();
    }

    /**
     * Transfer the physical motor RPM into Create's kinetic system.
     */
    protected void updateMotorSpeed() {

        double rpm =
                motorState.rpm();

        if (!Double.isFinite(rpm))
            rpm = 0;

        /*
         * Limit both:
         *
         * 1. the motor's physical maximum RPM
         * 2. Create's global maximum RPM
         *
         * The motor parameters therefore remain the physical
         * authority.
         */
        double allowed =
                Math.min(
                        parameters().maxRPM(),
                        maxRPM()
                );

        rpm =
                Math.max(
                        -allowed,
                        Math.min(
                                allowed,
                                rpm
                        )
                );

        motorState.rpm(
                rpm
        );

        /*
         * Convert physical motor RPM into Create RPM.
         *
         * Example:
         *
         *     physical 256 RPM
         *     × 3.570556640625
         *     = 914.0625 Create RPM
         */
        double createRPM =
                rpm
                        * parameters()
                        .createSpeedMultiplier();

        float speed =
                convertToDirection(
                        (float) createRPM,
                        getBlockState()
                                .getValue(
                                        PhysicsMotorBlock.FACING
                                )
                );

        if (
                Math.abs(
                        speed
                                - generatedSpeed
                ) > 0.001f
        ) {

            generatedSpeed =
                    speed;

            updateGeneratedRotation();
        }
    }

    @Override
    public float getGeneratedSpeed() {

        float speed =
                (float) (
                        motorState.rpm()
                                * parameters()
                                .createSpeedMultiplier()
                );

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

    /**
     * 0.0 = stopped
     * 1.0 = maximum physical RPM
     */
    public float getSoundRPMFactor() {

        double maximum =
                parameters().maxRPM();

        if (maximum <= 0)
            return 0;

        return (float) Math.min(
                1,
                Math.abs(
                        motorState.rpm()
                ) / maximum
        );
    }

    /**
     * 1.0 = rated current.
     *
     * Values above 1.0 represent overload/inrush conditions.
     */
    public float getSoundLoadFactor() {

        double ratedCurrent =
                parameters().ratedCurrent();

        if (ratedCurrent <= 0)
            return 0;

        return (float) Math.min(
                1.5,
                Math.abs(
                        motorState.current()
                ) / ratedCurrent
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
                tag.getDouble(
                        "MotorVoltage"
                )
        );

        motorState.current(
                tag.getDouble(
                        "MotorCurrent"
                )
        );

        motorState.backEmf(
                tag.getDouble(
                        "MotorBackEmf"
                )
        );

        motorState.electromagneticTorque(
                tag.getDouble(
                        "MotorTorque"
                )
        );

        motorState.loadTorque(
                tag.getDouble(
                        "MotorLoadTorque"
                )
        );

        motorState.frictionTorque(
                tag.getDouble(
                        "MotorFriction"
                )
        );

        motorState.netTorque(
                tag.getDouble(
                        "MotorNetTorque"
                )
        );

        motorState.angularVelocity(
                tag.getDouble(
                        "MotorOmega"
                )
        );

        motorState.angularAcceleration(
                tag.getDouble(
                        "MotorAlpha"
                )
        );

        motorState.rpm(
                tag.getDouble(
                        "MotorRPM"
                )
        );

        motorState.electricalPower(
                tag.getDouble(
                        "MotorElectricalPower"
                )
        );

        motorState.mechanicalPower(
                tag.getDouble(
                        "MotorMechanicalPower"
                )
        );

        motorState.copperLoss(
                tag.getDouble(
                        "MotorCopperLoss"
                )
        );

        motorState.temperature(
                tag.getDouble(
                        "MotorTemperature"
                )
        );

        /*
         * Rebuild Create's generated rotation from the saved
         * physical motor speed.
         */
        if (
                level != null
                        && !level.isClientSide
        ) {

            generatedSpeed =
                    getGeneratedSpeed();

            updateGeneratedRotation();
        }
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