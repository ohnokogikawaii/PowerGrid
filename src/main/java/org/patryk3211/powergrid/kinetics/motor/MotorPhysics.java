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
 */
public final class MotorPhysics {

    private static final double EPSILON = 1e-9;
    private static final double ZERO_SPEED = 1e-5;

    private MotorPhysics() {
    }

    /**
     * Advance the mechanical motor state.
     *
     * @param parameters motor parameters
     * @param state motor runtime state
     * @param current solved armature current
     * @param appliedVoltage terminal voltage
     * @param loadTorque mechanical load magnitude
     * @param deltaTime seconds
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
        loadTorque = Math.max(0, Math.abs(finite(loadTorque)));

        double omega = finite(state.angularVelocity());

        double kt = parameters.torqueConstant();
        double ke = parameters.backEmfConstant();

        double inertia = Math.max(
                parameters.inertia(),
                EPSILON
        );

        double frictionMagnitude = Math.max(
                0,
                parameters.frictionTorque()
        );

        /*
         * Electromagnetic torque.
         */
        double electromagneticTorque = kt * current;

        /*
         * Determine intended rotation direction.
         */
        double direction;

        if (Math.abs(omega) > ZERO_SPEED) {
            direction = Math.signum(omega);
        } else if (Math.abs(electromagneticTorque) > ZERO_SPEED) {
            direction = Math.signum(electromagneticTorque);
        } else {
            direction = 0;
        }

        /*
         * Static friction.
         *
         * If the rotor is stopped and motor torque cannot overcome
         * the load and friction, it remains stopped.
         */
        if (Math.abs(omega) <= ZERO_SPEED) {

            double resisting =
                    loadTorque + frictionMagnitude;

            if (Math.abs(electromagneticTorque) <= resisting) {

                omega = 0;

                double backEmf = 0;

                state.voltage(appliedVoltage);
                state.current(current);
                state.backEmf(backEmf);

                state.electromagneticTorque(
                        electromagneticTorque
                );

                state.loadTorque(0);
                state.frictionTorque(0);
                state.netTorque(0);

                state.angularVelocity(0);
                state.angularAcceleration(0);
                state.rpm(0);

                state.electricalPower(
                        appliedVoltage * current
                );

                state.mechanicalPower(0);

                state.copperLoss(
                        current * current * parameters.resistance()
                );

                return;
            }
        }

        /*
         * Dynamic friction opposes rotation.
         */
        double frictionTorque =
                direction * frictionMagnitude;

        /*
         * Mechanical load also opposes rotation.
         */
        double signedLoadTorque =
                direction * loadTorque;

        /*
         * Net torque.
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
         * Integrate angular velocity.
         */
        double newOmega =
                omega + angularAcceleration * deltaTime;

        /*
         * Avoid numerical sign crossing caused by a single
         * large braking step.
         */
        if (Math.abs(omega) > ZERO_SPEED
                && Math.signum(omega) != Math.signum(newOmega)
                && Math.signum(angularAcceleration) != Math.signum(omega)) {

            newOmega = 0;
        }

        omega = newOmega;

        /*
         * Maximum mechanical speed.
         */
        double maxOmega =
                MotorParameters.rpmToRadPerSecond(
                        parameters.maxRPM()
                );

        if (maxOmega > 0) {
            omega = clamp(
                    omega,
                    -maxOmega,
                    maxOmega
            );
        }

        /*
         * Recalculate back EMF from the final rotor speed.
         */
        double backEmf = ke * omega;

        double rpm =
                MotorParameters.radPerSecondToRPM(omega);

        /*
         * Electrical power.
         */
        double electricalPower =
                appliedVoltage * current;

        /*
         * Electromagnetic mechanical power.
         */
        double mechanicalPower =
                electromagneticTorque * omega;

        /*
         * Copper loss.
         */
        double copperLoss =
                current
                        * current
                        * parameters.resistance();

        state.voltage(appliedVoltage);
        state.current(current);
        state.backEmf(backEmf);

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
                angularAcceleration
        );

        state.rpm(rpm);

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
        return parameters.torqueConstant()
                * current;
    }

    /**
     * Equivalent resistance for the next backward-Euler
     * electrical step.
     */
    public static double equivalentResistance(
            MotorParameters parameters,
            double deltaTime
    ) {
        if (deltaTime <= 0)
            return parameters.resistance();

        return parameters.resistance()
                + parameters.inductance() / deltaTime;
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

    private static double finite(double value) {
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
                Math.min(max, value)
        );
    }
}