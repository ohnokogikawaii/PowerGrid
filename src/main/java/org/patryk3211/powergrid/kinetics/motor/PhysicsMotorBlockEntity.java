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
     * 20 Minecraft ticks per second.
     */
    protected static final double DELTA_TIME = 0.05;

    /**
     * Createに出力している速度。
     */
    protected float generatedSpeed = 0;

    protected final MotorState motorState =
            new MotorState();

    protected ElectricBehaviour electricBehaviour;

    @Nullable
    protected ThermalBehaviour thermalBehaviour;

    /**
     * Thevenin equivalent of the motor winding.
     */
    protected VoltageSourceCoupling motorSource;

    /**
     * Mechanical load supplied by Create.
     */
    protected double externalLoadTorque;

    public PhysicsMotorBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    ) {
        super(type, pos, state);
    }

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

        if (thermalBehaviour != null) {
            behaviours.add(
                    thermalBehaviour
            );
        }
    }

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
         * The motor is represented as:
         *
         *     R_eq + back EMF
         *
         * The inductive term is converted into an equivalent
         * resistance/source using backward Euler in MotorPhysics.
         */
        motorSource =
                builder.addInternalNode(
                        VoltageSourceCoupling.class,
                        builder.terminalNode(0),
                        builder.terminalNode(1),
                        (float) Math.max(
                                parameters().resistance(),
                                0.001
                        )
                );

        motorSource.setVoltage(0);
    }

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
     * Perform one complete motor update.
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
         * Read the current solved by the previous electrical
         * network iteration.
         *
         * Never feed an absurd solver value directly into the
         * mechanical model.
         */
        double solvedCurrent =
                motorSource.getCurrent();

        if (!Double.isFinite(solvedCurrent)) {
            solvedCurrent = 0;
        }

        /*
         * Clamp the current used by the mechanical model.
         *
         * This does NOT replace electrical current limiting.
         * It only prevents a solver instability from generating
         * hundreds of Nm of artificial torque.
         */
        double current =
                solvedCurrent;

        if (parameters.maxCurrent() > 0) {

            current =
                    MotorPhysicsClamp(
                            current,
                            -parameters.maxCurrent(),
                            parameters.maxCurrent()
                    );
        }

        /*
         * Actual external terminal voltage.
         */
        double terminalVoltage =
                motorSource.getPositive().getVoltage()
                        - (
                        motorSource.getNegative() != null
                                ? motorSource.getNegative().getVoltage()
                                : 0
                );

        if (!Double.isFinite(terminalVoltage)) {
            terminalVoltage = 0;
        }

        /*
         * Advance mechanical state.
         *
         * The state current is the bounded physical current,
         * rather than a potentially unstable raw solver value.
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
         * Construct the electrical equivalent for the NEXT
         * solver iteration.
         */
        double baseResistance =
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
         * Determine the resistance necessary to keep the
         * electrical current below maxCurrent.
         */
        double limitedResistance =
                MotorPhysics.currentLimitedResistance(
                        parameters,
                        motorState,
                        terminalVoltage,
                        equivalentVoltage,
                        baseResistance
                );

        if (
                !Double.isFinite(limitedResistance)
                        || limitedResistance <= 0
        ) {
            limitedResistance =
                    Math.max(
                            baseResistance,
                            0.001
                    );
        }

        /*
         * Update the electrical equivalent.
         *
         * VoltageSourceCoupling implements:
         *
         *     I =
         *       (Vterminal + Vsource) / R
         *
         * Therefore the motor's internal opposing voltage must
         * be supplied with a NEGATIVE sign.
         */
        motorSource.setResistance(
                (float) limitedResistance
        );

        motorSource.setVoltage(
                -equivalentVoltage
        );

        /*
         * Thermal loss.
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
         * Transfer physical RPM to Create.
         */
        updateMotorSpeed();
    }

    /**
     * Local finite clamp.
     */
    private static double MotorPhysicsClamp(
            double value,
            double min,
            double max
    ) {
        if (!Double.isFinite(value))
            return 0;

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    /**
     * Transfer physical motor RPM to Create.
     */
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
                        Math.min(
                                allowed,
                                rpm
                        )
                );

        motorState.rpm(
                rpm
        );

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