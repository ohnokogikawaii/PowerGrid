package org.patryk3211.powergrid.kinetics.motor;

/**
 * Mechanical simulation of a permanent-magnet DC motor.
 *
 * Electrical current is supplied by the PowerGrid circuit solver.
 *
 *     T = Kt * I
 *
 *     J * alpha = Tmotor - Tload - Tfriction
 *
 *     E = Ke * omega
 *
 * The electrical circuit and the mechanical motor are intentionally
 * separated:
 *
 * PowerGrid:
 *
 *     terminal voltage
 *          +
 *          |
 *          v
 *     electrical current
 *          |
 *          v
 *     MotorPhysics
 *          |
 *          +---- electromagnetic torque
 *          |
 *          v
 *     rotor acceleration
 *          |
 *          v
 *     rotor speed
 *          |
 *          v
 *     back EMF
 *
 * The resulting back EMF is then fed back into the electrical
 * equivalent circuit by PhysicsMotorBlockEntity.
 */
public final class MotorPhysics {

    private static final double EPSILON = 1e-9;

    /**
     * Speed below which the rotor is considered stationary.
     */
    private static final double ZERO_SPEED = 1e-5;

    private MotorPhysics() {
    }

    /**
     * Advance the mechanical motor state by one simulation step.
     *
     * @param parameters motor parameters
     * @param state motor runtime state
     * @param current solved armature current
     * @param appliedVoltage actual terminal voltage
     * @param loadTorque mechanical load magnitude
     * @param deltaTime simulation time in seconds
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

        /*
         * Prevent an unexpectedly large time step from causing
         * unrealistic acceleration.
         */
        deltaTime = Math.min(
                deltaTime,
                0.1
        );

        current = finite(current);
        appliedVoltage = finite(appliedVoltage);

        loadTorque =
                Math.max(
                        0,
                        Math.abs(
                                finite(loadTorque)
                        )
                );

        /*
         * Apply the motor's physical current limit.
         *
         * The electrical solver is also given a dynamic resistance
         * so that the actual circuit current converges toward this
         * limit.
         *
         * This clamp protects the mechanical simulation from a
         * temporary solver overshoot.
         */
        double maximumCurrent =
                parameters.maxCurrent();

        if (maximumCurrent > 0) {

            current =
                    clamp(
                            current,
                            -maximumCurrent,
                            maximumCurrent
                    );
        }

        /*
         * Previous rotor speed.
         */
        double previousOmega =
                finite(
                        state.angularVelocity()
                );

        /*
         * Motor constants.
         */
        double kt =
                parameters.torqueConstant();

        double ke =
                parameters.backEmfConstant();

        /*
         * Rotor inertia.
         */
        double inertia =
                Math.max(
                        parameters.inertia(),
                        EPSILON
                );

        /*
         * Mechanical friction.
         */
        double frictionMagnitude =
                Math.max(
                        0,
                        parameters.frictionTorque()
                );

        /*
         * Electromagnetic torque.
         *
         * T = Kt * I
         */
        double electromagneticTorque =
                kt * current;

        /*
         * Determine the direction of rotation.
         *
         * At normal speed the existing rotor direction is used.
         *
         * At standstill the electromagnetic torque determines the
         * direction in which the rotor wants to start.
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
         * Static friction.
         *
         * When the motor is stopped, it must first produce enough
         * torque to overcome both the external load and static
         * friction.
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

                /*
                 * Rotor remains stationary.
                 */
                double backEmf = 0;

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

                /*
                 * Keep the actual external load visible in
                 * the state even while stopped.
                 */
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
         * Dynamic friction always opposes the direction of motion.
         */
        double frictionTorque =
                direction
                        * frictionMagnitude;

        /*
         * External mechanical load also opposes the direction
         * of rotation.
         */
        double signedLoadTorque =
                direction
                        * loadTorque;

        /*
         * Mechanical torque balance.
         *
         * J * alpha =
         *
         *     Tmotor
         *     - Tload
         *     - Tfriction
         */
        double netTorque =
                electromagneticTorque
                        - signedLoadTorque
                        - frictionTorque;

        /*
         * Angular acceleration.
         */
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
         * Prevent a single numerical step from crossing through
         * zero into the opposite direction.
         */
        if (
                Math.abs(previousOmega)
                        > ZERO_SPEED
                        &&
                        Math.signum(previousOmega)
                                != Math.signum(newOmega)
                        &&
                        Math.signum(angularAcceleration)
                                != Math.signum(previousOmega)
        ) {

            newOmega = 0;
        }

        /*
         * Physical maximum speed.
         *
         * This is the actual motor RPM limit, not Create's
         * generated kinetic RPM.
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

        /*
         * Store the final angular velocity.
         */
        double omega =
                newOmega;

        /*
         * Recalculate acceleration from the actual final speed.
         *
         * This is important when the motor reaches its maximum
         * physical RPM. The reported acceleration must then become
         * zero instead of continuing to report acceleration beyond
         * the speed limit.
         */
        double actualAngularAcceleration =
                (
                        omega
                                - previousOmega
                )
                        / deltaTime;

        /*
         * Back EMF.
         *
         * E = Ke * omega
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
         *
         * P = T * omega
         */
        double mechanicalPower =
                electromagneticTorque
                        * omega;

        /*
         * Copper loss.
         *
         * P_loss = I^2 * R
         */
        double copperLoss =
                current
                        * current
                        * parameters.resistance();

        /*
         * Store complete state.
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
     * Back EMF from rotor speed.
     */
    public static double backEmf(
            MotorParameters parameters,
            double angularVelocity
    ) {
        return parameters.backEmfConstant()
                * angularVelocity;
    }

    /**
     * Electromagnetic torque from current.
     */
    public static double torque(
            MotorParameters parameters,
            double current
    ) {
        double maximumCurrent =
                parameters.maxCurrent();

        if (maximumCurrent > 0) {

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
     * Equivalent resistance for the next backward-Euler
     * electrical step.
     *
     *     R_eq = R + L/dt
     */
    public static double equivalentResistance(
            MotorParameters parameters,
            double deltaTime
    ) {
        if (deltaTime <= 0)
            return parameters.resistance();

        return parameters.resistance()
                + parameters.inductance()
                / deltaTime;
    }

    /**
     * Equivalent source voltage for the next backward-Euler
     * electrical step.
     *
     *     V = E + R*I + L*dI/dt
     *
     * becomes
     *
     *     Vsource =
     *         E + L/dt * I_previous
     */
    public static double equivalentVoltage(
            MotorParameters parameters,
            MotorState state,
            double deltaTime
    ) {
        if (deltaTime <= 0)
            return state.backEmf();

        return state.backEmf()
                + parameters.inductance()
                / deltaTime
                * state.current();
    }

    /**
     * Calculate the minimum equivalent resistance required to
     * prevent the motor current from exceeding maxCurrent.
     *
     * The VoltageSourceCoupling uses the source voltage with the
     * opposite sign for the motor's internal voltage drop:
     *
     *     setVoltage(-Veq)
     *
     * Therefore the motor current is effectively:
     *
     *     I = (Vterminal - Veq) / R
     *
     * where:
     *
     *     Veq = E + L/dt * I_previous
     *
     * Thus the additional resistance required to keep the current
     * below Imax is:
     *
     *     R >= |Vterminal - Veq| / Imax
     *
     * The normal winding/inductive resistance is always preserved.
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
            return baseResistance;
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
         * Correct voltage available to force armature current.
         *
         * The back EMF and inductive voltage oppose the applied
         * terminal voltage.
         */
        double voltageDifference =
                Math.abs(
                        terminalVoltage
                                - equivalentVoltage
                );

        double voltageLimitedResistance =
                voltageDifference
                        / maximumCurrent;

        /*
         * If the previous solver result was already above the
         * physical current limit, increase the resistance by the
         * same ratio.
         */
        double measuredCurrent =
                Math.abs(
                        state.current()
                );

        double currentLimitedResistance =
                baseResistance;

        if (measuredCurrent > maximumCurrent) {

            currentLimitedResistance =
                    baseResistance
                            * (
                            measuredCurrent
                                    / maximumCurrent
                    );
        }

        return Math.max(
                baseResistance,
                Math.max(
                        voltageLimitedResistance,
                        currentLimitedResistance
                )
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