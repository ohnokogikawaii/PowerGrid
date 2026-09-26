package org.patryk3211.powergrid.kinetics.motor;

/**
 * Physical simulation of a permanent-magnet DC motor.
 *
 * Electrical model:
 *
 *     V = R * I + L * dI/dt + Ke * omega
 *
 * Mechanical model:
 *
 *     T = Kt * I
 *
 *     J * alpha =
 *         Tmotor
 *         - Tload
 *         - Tfriction
 *
 * The electrical network is represented by a Thevenin equivalent:
 *
 *     R_eq = R + L / dt
 *
 *     V_eq = -(E + L / dt * I_previous)
 *
 * so that the VoltageSourceCoupling produces:
 *
 *     I = (Vterminal - E - L/dt*I_previous) / R_eq
 *
 * The motor current is additionally limited by maxCurrent.
 */
public final class MotorPhysics {

    private static final double EPSILON = 1e-9;

    private static final double ZERO_SPEED = 1e-5;

    private MotorPhysics() {
    }

    /**
     * Advance the mechanical state by one simulation step.
     *
     * The current supplied here is the current solved by the
     * electrical network during the previous solver pass.
     */
    public static void update(
            MotorParameters parameters,
            MotorState state,
            double current,
            double appliedVoltage,
            double loadTorque,
            double deltaTime
    ) {
        if (parameters == null || state == null)
            return;

        if (!Double.isFinite(deltaTime) || deltaTime <= 0)
            return;

        deltaTime = Math.min(deltaTime, 0.1);

        current = finite(current);
        appliedVoltage = finite(appliedVoltage);

        loadTorque = Math.max(
                0,
                Math.abs(finite(loadTorque))
        );

        /*
         * Never allow an electrical solver overshoot to produce
         * unrealistic electromagnetic torque.
         */
        double maximumCurrent =
                parameters.maxCurrent();

        if (
                maximumCurrent > 0
                        && Double.isFinite(maximumCurrent)
        ) {
            current = clamp(
                    current,
                    -maximumCurrent,
                    maximumCurrent
            );
        }

        double previousOmega =
                finite(state.angularVelocity());

        double kt =
                parameters.torqueConstant();

        double ke =
                parameters.backEmfConstant();

        double inertia =
                Math.max(
                        parameters.inertia(),
                        EPSILON
                );

        double frictionMagnitude =
                Math.max(
                        0,
                        parameters.frictionTorque()
                );

        /*
         * Electromagnetic torque.
         */
        double electromagneticTorque =
                kt * current;

        /*
         * Determine rotor direction.
         */
        double direction;

        if (Math.abs(previousOmega) > ZERO_SPEED) {

            direction =
                    Math.signum(previousOmega);

        } else if (
                Math.abs(electromagneticTorque)
                        > ZERO_SPEED
        ) {

            direction =
                    Math.signum(
                            electromagneticTorque
                    );

        } else {

            direction = 0;
        }

        /*
         * Static friction while stopped.
         */
        if (
                Math.abs(previousOmega)
                        <= ZERO_SPEED
        ) {

            double resistingTorque =
                    loadTorque
                            + frictionMagnitude;

            if (
                    Math.abs(
                            electromagneticTorque
                    )
                            <= resistingTorque
            ) {

                state.voltage(
                        appliedVoltage
                );

                state.current(
                        current
                );

                state.backEmf(0);

                state.electromagneticTorque(
                        electromagneticTorque
                );

                state.loadTorque(
                        loadTorque
                );

                state.frictionTorque(0);

                state.netTorque(
                        electromagneticTorque
                                - loadTorque
                );

                state.angularVelocity(0);

                state.angularAcceleration(0);

                state.rpm(0);

                state.electricalPower(
                        appliedVoltage * current
                );

                state.mechanicalPower(0);

                state.copperLoss(
                        current
                                * current
                                * parameters.resistance()
                );

                return;
            }
        }

        /*
         * Dynamic friction.
         */
        double frictionTorque =
                direction
                        * frictionMagnitude;

        /*
         * External load always opposes rotation.
         */
        double signedLoadTorque =
                direction
                        * loadTorque;

        /*
         * Mechanical torque balance.
         */
        double netTorque =
                electromagneticTorque
                        - signedLoadTorque
                        - frictionTorque;

        double angularAcceleration =
                netTorque / inertia;

        /*
         * Integrate rotor speed.
         */
        double newOmega =
                previousOmega
                        + angularAcceleration
                        * deltaTime;

        /*
         * Prevent numerical oscillation through zero.
         */
        if (
                Math.abs(previousOmega)
                        > ZERO_SPEED
                        &&
                        Math.signum(previousOmega)
                                != Math.signum(newOmega)
        ) {
            newOmega = 0;
        }

        /*
         * Physical motor RPM limit.
         */
        double maxOmega =
                MotorParameters.rpmToRadPerSecond(
                        parameters.maxRPM()
                );

        if (maxOmega > 0) {

            newOmega =
                    clamp(
                            newOmega,
                            -maxOmega,
                            maxOmega
                    );
        }

        double omega =
                newOmega;

        /*
         * Once the motor reaches its physical speed limit,
         * do not report artificial acceleration beyond it.
         */
        double actualAngularAcceleration =
                (
                        omega
                                - previousOmega
                )
                        / deltaTime;

        /*
         * Back EMF.
         */
        double backEmf =
                ke * omega;

        /*
         * Physical RPM.
         */
        double rpm =
                MotorParameters.radPerSecondToRPM(
                        omega
                );

        /*
         * Electrical input power.
         */
        double electricalPower =
                appliedVoltage * current;

        /*
         * Electromagnetic mechanical power.
         */
        double mechanicalPower =
                electromagneticTorque
                        * omega;

        /*
         * Copper loss.
         */
        double copperLoss =
                current
                        * current
                        * parameters.resistance();

        /*
         * Store state.
         */
        state.voltage(
                appliedVoltage
        );

        state.current(
                current
        );

        state.backEmf(
                backEmf
        );

        state.electromagneticTorque(
                electromagneticTorque
        );

        state.loadTorque(
                signedLoadTorque
        );

        state.frictionTorque(
                frictionTorque
        );

        state.netTorque(
                netTorque
        );

        state.angularVelocity(
                omega
        );

        state.angularAcceleration(
                actualAngularAcceleration
        );

        state.rpm(
                rpm
        );

        state.electricalPower(
                electricalPower
        );

        state.mechanicalPower(
                mechanicalPower
        );

        state.copperLoss(
                copperLoss
        );
    }

    /**
     * Calculate back EMF.
     */
    public static double backEmf(
            MotorParameters parameters,
            double angularVelocity
    ) {
        return parameters.backEmfConstant()
                * angularVelocity;
    }

    /**
     * Calculate electromagnetic torque.
     */
    public static double torque(
            MotorParameters parameters,
            double current
    ) {
        double maximumCurrent =
                parameters.maxCurrent();

        if (
                maximumCurrent > 0
                        && Double.isFinite(maximumCurrent)
        ) {
            current =
                    clamp(
                            current,
                            -maximumCurrent,
                            maximumCurrent
                    );
        }

        return parameters.torqueConstant()
                * current;
    }

    /**
     * Backward-Euler equivalent resistance.
     *
     *     R_eq = R + L/dt
     */
    public static double equivalentResistance(
            MotorParameters parameters,
            double deltaTime
    ) {
        if (
                deltaTime <= 0
                        || !Double.isFinite(deltaTime)
        ) {
            return Math.max(
                    parameters.resistance(),
                    EPSILON
            );
        }

        return Math.max(
                parameters.resistance()
                        + parameters.inductance()
                        / deltaTime,
                EPSILON
        );
    }

    /**
     * Backward-Euler equivalent internal voltage.
     *
     * The returned value is the magnitude of the voltage that
     * opposes the external terminal voltage.
     *
     * PhysicsMotorBlockEntity applies this value to
     * VoltageSourceCoupling with a negative sign.
     */
    public static double equivalentVoltage(
            MotorParameters parameters,
            MotorState state,
            double deltaTime
    ) {
        if (
                deltaTime <= 0
                        || !Double.isFinite(deltaTime)
        ) {
            return finite(
                    state.backEmf()
            );
        }

        double previousCurrent =
                finite(
                        state.current()
                );

        double inductiveVoltage =
                parameters.inductance()
                        / deltaTime
                        * previousCurrent;

        return finite(
                state.backEmf()
                        + inductiveVoltage
        );
    }

    /**
     * Calculate the minimum resistance required to keep the
     * electrical current at or below maxCurrent.
     *
     * This method is deliberately based on the voltage that is
     * actually available across the motor equivalent.
     *
     *     I = (Vterminal - Veq) / R
     *
     * Therefore:
     *
     *     R >= |Vterminal - Veq| / Imax
     *
     * A small safety margin is added so the nonlinear electrical
     * solver does not repeatedly cross the current limit.
     */
    public static double currentLimitedResistance(
            MotorParameters parameters,
            MotorState state,
            double terminalVoltage,
            double equivalentVoltage,
            double baseResistance
    ) {
        double maximumCurrent =
                parameters.maxCurrent();

        if (
                maximumCurrent <= 0
                        || !Double.isFinite(maximumCurrent)
        ) {
            return Math.max(
                    baseResistance,
                    parameters.resistance()
            );
        }

        terminalVoltage =
                finite(terminalVoltage);

        equivalentVoltage =
                finite(equivalentVoltage);

        baseResistance =
                Math.max(
                        baseResistance,
                        parameters.resistance()
                );

        /*
         * Voltage actually available to force armature current.
         */
        double voltageDifference =
                Math.abs(
                        terminalVoltage
                                - equivalentVoltage
                );

        /*
         * Add a 2% margin to avoid solver oscillation directly
         * around the current limit.
         */
        double targetCurrent =
                maximumCurrent * 0.98;

        double requiredResistance =
                voltageDifference
                        / Math.max(
                        targetCurrent,
                        EPSILON
                );

        /*
         * If the last solved current was already above the limit,
         * make the resistance more aggressive.
         */
        double previousCurrent =
                Math.abs(
                        finite(
                                state.current()
                        )
                );

        if (
                previousCurrent
                        > maximumCurrent
        ) {

            double correction =
                    previousCurrent
                            / maximumCurrent;

            requiredResistance =
                    Math.max(
                            requiredResistance,
                            baseResistance
                                    * correction
                                    * 1.10
                    );
        }

        return Math.max(
                baseResistance,
                requiredResistance
        );
    }

    private static double finite(
            double value
    ) {
        return Double.isFinite(value)
                ? value
                : 0;
    }

    private static double clamp(
            double value,
            double min,
            double max
    ) {
        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }
}