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

/**
 * Physics simulation for a simplified permanent-magnet DC motor.
 *
 * Electrical equation:
 *
 *     V = R * I + L * dI/dt + Ke * omega
 *
 * Mechanical equation:
 *
 *     J * domega/dt = Tmotor - Tload - Tfriction
 *
 * Motor torque:
 *
 *     Tmotor = Kt * I
 *
 * For a permanent-magnet DC motor:
 *
 *     Ke == Kt
 *
 * when the constants are expressed in SI units.
 *
 * The simulation is deliberately independent from Minecraft and Create.
 */
public final class MotorPhysics {

    private static final double EPSILON = 1e-9;
    private static final double ZERO_SPEED_EPSILON = 1e-6;

    private MotorPhysics() {
    }

    /**
     * Advances the motor simulation by one step.
     *
     * @param parameters motor parameters
     * @param state current motor state
     * @param appliedVoltage voltage applied to the motor
     * @param externalLoadTorque mechanical load torque
     * @param deltaTime simulation time in seconds
     */
    public static void update(
            MotorParameters parameters,
            MotorState state,
            double appliedVoltage,
            double externalLoadTorque,
            double deltaTime
    ) {
        if (parameters == null || state == null || deltaTime <= 0)
            return;

        /*
         * Prevent a very large timestep from destabilizing the simulation.
         *
         * The actual motor block can call this method every tick, but this
         * also makes the class safe to use from other update rates.
         */
        deltaTime = Math.min(deltaTime, 0.1);

        double voltage = finiteOrZero(appliedVoltage);

        double resistance = Math.max(
                finiteOrZero(parameters.resistance()),
                EPSILON
        );

        double inductance = Math.max(
                finiteOrZero(parameters.inductance()),
                EPSILON
        );

        double inertia = Math.max(
                finiteOrZero(parameters.inertia()),
                EPSILON
        );

        double torqueConstant = finiteOrZero(
                parameters.torqueConstant()
        );

        double maxCurrent = Math.max(
                0,
                finiteOrZero(parameters.maxCurrent())
        );

        /*
         * -----------------------------------------------------------------
         * Previous mechanical state
         * -----------------------------------------------------------------
         */

        double omega = finiteOrZero(state.angularVelocity());

        /*
         * -----------------------------------------------------------------
         * Back EMF
         *
         *     E = Ke * omega
         * -----------------------------------------------------------------
         */

        double backEmf = parameters.backEmfConstant() * omega;

        /*
         * -----------------------------------------------------------------
         * Electrical system
         *
         *     V = R*I + L*dI/dt + E
         *
         * Rearranged:
         *
         *     dI/dt = (V - E - R*I) / L
         *
         * Instead of a simple Euler integration, use the exact solution
         * of the first-order RL equation while assuming omega is constant
         * during this small timestep.
         *
         * This is considerably more stable during motor startup.
         * -----------------------------------------------------------------
         */

        double current = finiteOrZero(state.current());

        double targetCurrent =
                (voltage - backEmf) / resistance;

        /*
         * First-order RL response:
         *
         *     I(t) = Iinf + (I0 - Iinf)e^(-R/L*t)
         */

        double electricalTimeConstant =
                inductance / resistance;

        double response;

        if (electricalTimeConstant <= EPSILON) {
            response = 0;
        } else {
            response = Math.exp(
                    -deltaTime / electricalTimeConstant
            );
        }

        current =
                targetCurrent
                        + (current - targetCurrent) * response;

        current = clamp(
                current,
                -maxCurrent,
                maxCurrent
        );

        /*
         * -----------------------------------------------------------------
         * Electromagnetic torque
         *
         *     T = Kt * I
         * -----------------------------------------------------------------
         */

        double electromagneticTorque =
                torqueConstant * current;

        /*
         * -----------------------------------------------------------------
         * External load
         *
         * Create normally provides a positive magnitude for the mechanical
         * load. The motor must apply that load opposite to its direction
         * of rotation.
         * -----------------------------------------------------------------
         */

        double loadMagnitude = Math.max(
                0,
                Math.abs(finiteOrZero(externalLoadTorque))
        );

        /*
         * Determine the direction in which the rotor is trying to move.
         *
         * While stopped, use motor torque direction so that a motor can
         * actually start from zero.
         */

        double direction;

        if (Math.abs(omega) > ZERO_SPEED_EPSILON) {
            direction = Math.signum(omega);
        } else if (Math.abs(electromagneticTorque) > ZERO_SPEED_EPSILON) {
            direction = Math.signum(electromagneticTorque);
        } else {
            direction = 0;
        }

        /*
         * -----------------------------------------------------------------
         * Friction
         * -----------------------------------------------------------------
         */

        double frictionMagnitude = Math.max(
                0,
                finiteOrZero(parameters.frictionTorque())
        );

        double frictionTorque = 0;

        if (direction != 0) {
            frictionTorque =
                    frictionMagnitude * direction;
        }

        /*
         * -----------------------------------------------------------------
         * Static friction / startup
         *
         * If the rotor is stopped and motor torque cannot overcome the
         * external load plus friction, keep the rotor stationary.
         * -----------------------------------------------------------------
         */

        if (Math.abs(omega) <= ZERO_SPEED_EPSILON) {

            double resistingTorque =
                    loadMagnitude + frictionMagnitude;

            if (Math.abs(electromagneticTorque) <= resistingTorque) {

                omega = 0;

                /*
                 * The motor is stalled. No mechanical movement occurs.
                 * Keep the electromagnetic torque because the current is
                 * still physically meaningful.
                 */

                double electricalPower =
                        voltage * current;

                double copperLoss =
                        current * current * resistance;

                state.voltage(voltage);
                state.current(current);
                state.backEmf(0);
                state.electromagneticTorque(
                        electromagneticTorque
                );
                state.loadTorque(0);
                state.frictionTorque(0);
                state.netTorque(0);
                state.angularVelocity(0);
                state.angularAcceleration(0);
                state.rpm(0);
                state.electricalPower(electricalPower);
                state.mechanicalPower(0);
                state.copperLoss(copperLoss);

                return;
            }
        }

        /*
         * Load torque always opposes rotation.
         */

        double signedLoadTorque =
                loadMagnitude * direction;

        /*
         * -----------------------------------------------------------------
         * Net mechanical torque
         * -----------------------------------------------------------------
         */

        double netTorque =
                electromagneticTorque
                        - signedLoadTorque
                        - frictionTorque;

        /*
         * -----------------------------------------------------------------
         * Mechanical equation
         *
         *     alpha = T / J
         * -----------------------------------------------------------------
         */

        double angularAcceleration =
                netTorque / inertia;

        /*
         * Integrate angular velocity.
         */

        double newOmega =
                omega + angularAcceleration * deltaTime;

        /*
         * -----------------------------------------------------------------
         * Prevent numerical crossing through zero.
         *
         * Example:
         *
         *     +100 RPM
         *     strong braking torque
         *
         * The timestep could mathematically jump to -20 RPM.
         *
         * A physical motor should first reach zero, then reverse only if
         * the torque continues in the reverse direction.
         * -----------------------------------------------------------------
         */

        if (omega != 0
                && Math.signum(omega) != Math.signum(newOmega)
                && Math.signum(angularAcceleration) != Math.signum(omega)) {

            newOmega = 0;
        }

        omega = newOmega;

        /*
         * -----------------------------------------------------------------
         * Maximum RPM
         * -----------------------------------------------------------------
         */

        double maxAngularVelocity =
                Math.abs(
                        MotorParameters.rpmToRadPerSecond(
                                parameters.maxRPM()
                        )
                );

        if (maxAngularVelocity > 0) {

            omega = clamp(
                    omega,
                    -maxAngularVelocity,
                    maxAngularVelocity
            );
        }

        /*
         * If the motor has reached its mechanical speed limit while still
         * accelerating in that direction, remove the excess acceleration.
         */

        if (maxAngularVelocity > 0
                && Math.abs(omega) >= maxAngularVelocity
                && Math.signum(angularAcceleration)
                == Math.signum(omega)) {

            angularAcceleration = 0;
        }

        /*
         * -----------------------------------------------------------------
         * Recalculate quantities from the final mechanical state.
         *
         * This ensures the stored back-EMF corresponds to the stored RPM.
         * -----------------------------------------------------------------
         */

        backEmf =
                parameters.backEmfConstant() * omega;

        double rpm =
                MotorParameters.radPerSecondToRPM(omega);

        /*
         * -----------------------------------------------------------------
         * Power
         * -----------------------------------------------------------------
         *
         * Electrical input:
         *
         *     P = V * I
         *
         * Mechanical electromagnetic power:
         *
         *     P = T * omega
         *
         * Copper loss:
         *
         *     P = I²R
         */

        double electricalPower =
                voltage * current;

        double mechanicalPower =
                electromagneticTorque * omega;

        double copperLoss =
                current * current * resistance;

        /*
         * -----------------------------------------------------------------
         * Store complete state.
         * -----------------------------------------------------------------
         */

        state.voltage(voltage);
        state.current(current);
        state.backEmf(backEmf);
        state.electromagneticTorque(
                electromagneticTorque
        );
        state.loadTorque(signedLoadTorque);
        state.frictionTorque(frictionTorque);
        state.netTorque(netTorque);
        state.angularVelocity(omega);
        state.angularAcceleration(
                angularAcceleration
        );
        state.rpm(rpm);
        state.electricalPower(electricalPower);
        state.mechanicalPower(mechanicalPower);
        state.copperLoss(copperLoss);
    }

    /**
     * Calculates the steady-state current at a given voltage and speed.
     *
     *     I = (V - Ke*omega) / R
     */
    public static double calculateSteadyStateCurrent(
            MotorParameters parameters,
            double voltage,
            double rpm
    ) {
        if (parameters == null)
            return 0;

        double resistance =
                Math.max(parameters.resistance(), EPSILON);

        double omega =
                MotorParameters.rpmToRadPerSecond(rpm);

        double backEmf =
                parameters.backEmfConstant() * omega;

        double current =
                (finiteOrZero(voltage) - backEmf)
                        / resistance;

        return clamp(
                current,
                -parameters.maxCurrent(),
                parameters.maxCurrent()
        );
    }

    /**
     * Calculates back EMF at a given RPM.
     *
     *     E = Ke * omega
     */
    public static double calculateBackEmf(
            MotorParameters parameters,
            double rpm
    ) {
        if (parameters == null)
            return 0;

        double omega =
                MotorParameters.rpmToRadPerSecond(rpm);

        return parameters.backEmfConstant() * omega;
    }

    /**
     * Calculates electromagnetic torque from current.
     *
     *     T = Kt * I
     */
    public static double calculateTorque(
            MotorParameters parameters,
            double current
    ) {
        if (parameters == null)
            return 0;

        return parameters.torqueConstant()
                * clamp(
                finiteOrZero(current),
                -parameters.maxCurrent(),
                parameters.maxCurrent()
        );
    }

    /**
     * Calculates mechanical power.
     *
     *     P = T * omega
     */
    public static double calculateMechanicalPower(
            double torque,
            double rpm
    ) {
        double omega =
                MotorParameters.rpmToRadPerSecond(rpm);

        return finiteOrZero(torque) * omega;
    }

    /**
     * Calculates the current required to produce a given torque.
     *
     *     I = T / Kt
     */
    public static double calculateCurrentForTorque(
            MotorParameters parameters,
            double torque
    ) {
        if (parameters == null
                || Math.abs(parameters.torqueConstant()) <= EPSILON) {
            return 0;
        }

        return clamp(
                finiteOrZero(torque)
                        / parameters.torqueConstant(),
                -parameters.maxCurrent(),
                parameters.maxCurrent()
        );
    }

    /**
     * Clamps a value to the supplied range.
     */
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

    /**
     * Converts invalid floating-point values to zero.
     */
    private static double finiteOrZero(double value) {
        return Double.isFinite(value)
                ? value
                : 0;
    }
}