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
 * The model follows the basic motor relationship:
 *
 *     V = R * I + L * dI/dt + Ke * omega
 *
 *     T = Kt * I
 *
 *     J * domega/dt = T - Tload - Tfriction
 *
 * Therefore startup behavior naturally produces:
 *
 *     high current
 *          ↓
 *     high torque
 *          ↓
 *     acceleration
 *          ↓
 *     increasing RPM
 *          ↓
 *     increasing back-EMF
 *          ↓
 *     decreasing current
 *
 * The class has no dependency on Minecraft or Create.
 */
public final class MotorPhysics {

    private MotorPhysics() {
    }

    /**
     * Updates the motor state by one simulation step.
     *
     * @param parameters motor's static parameters
     * @param state current motor state
     * @param appliedVoltage voltage supplied to the motor
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
        if (deltaTime <= 0)
            return;

        deltaTime = Math.min(deltaTime, 0.1);

        double voltage = finiteOrZero(appliedVoltage);

        /*
         * Electrical system
         *
         * E = Ke * omega
         */
        double omega = state.angularVelocity();

        double backEmf = parameters.backEmfConstant() * omega;

        /*
         * RL motor winding:
         *
         * dI/dt = (V - E - R*I) / L
         *
         * Euler integration is sufficient for the first implementation.
         */
        double resistance = Math.max(parameters.resistance(), 1e-9);
        double inductance = Math.max(parameters.inductance(), 1e-9);

        double current = state.current();

        double currentDerivative =
                (voltage - backEmf - resistance * current) / inductance;

        current += currentDerivative * deltaTime;

        /*
         * Prevent the simulation from exceeding the motor's
         * electrical current capability.
         */
        current = clamp(
                current,
                -parameters.maxCurrent(),
                parameters.maxCurrent()
        );

        /*
         * Electromagnetic torque.
         *
         * Positive current produces positive torque and negative
         * current produces regenerative/braking torque.
         */
        double electromagneticTorque =
                parameters.torqueConstant() * current;

        /*
         * Mechanical load.
         *
         * External load is supplied by the Create kinetic network.
         * The load opposes the current direction of rotation.
         */
        double loadTorque = Math.max(0, Math.abs(finiteOrZero(externalLoadTorque)));

        double frictionTorque = 0;

        if (Math.abs(omega) > 1e-6) {
            frictionTorque =
                    parameters.frictionTorque() * Math.signum(omega);
        }

        /*
         * Static friction is represented when the rotor is almost
         * stopped. This prevents a tiny load from causing endless
         * numerical oscillation around zero RPM.
         */
        if (Math.abs(omega) <= 1e-6 && Math.abs(electromagneticTorque) <= loadTorque) {
            omega = 0;
        }

        double signedLoadTorque = loadTorque * directionOrOne(omega, electromagneticTorque);

        /*
         * Net torque.
         */
        double netTorque =
                electromagneticTorque
                        - signedLoadTorque
                        - frictionTorque;

        /*
         * Mechanical equation:
         *
         * alpha = T / J
         */
        double inertia = Math.max(parameters.inertia(), 1e-9);

        double angularAcceleration =
                netTorque / inertia;

        omega += angularAcceleration * deltaTime;

        /*
         * Do not allow numerical integration to cross zero when the
         * remaining torque is opposing the current direction.
         */
        if (Math.signum(omega) != Math.signum(
                angularAcceleration == 0 ? omega : angularAcceleration
        ) && Math.abs(omega) < 1e-5) {
            omega = 0;
        }

        /*
         * Mechanical speed limit.
         */
        double maxAngularVelocity =
                MotorParameters.rpmToRadPerSecond(parameters.maxRPM());

        omega = clamp(
                omega,
                -maxAngularVelocity,
                maxAngularVelocity
        );

        /*
         * At the speed limit, prevent the state from accumulating
         * acceleration in the same direction.
         */
        if (Math.abs(omega) >= maxAngularVelocity
                && Math.signum(omega) == Math.signum(angularAcceleration)) {
            angularAcceleration = 0;
        }

        double rpm =
                MotorParameters.radPerSecondToRPM(omega);

        /*
         * Electrical input power.
         */
        double electricalPower = voltage * current;

        /*
         * Mechanical output power.
         */
        double mechanicalPower =
                electromagneticTorque * omega;

        /*
         * Copper loss:
         *
         * P = I²R
         */
        double copperLoss =
                current * current * resistance;

        /*
         * Store the complete state.
         */
        state.voltage(voltage);
        state.current(current);
        state.backEmf(backEmf);
        state.electromagneticTorque(electromagneticTorque);
        state.loadTorque(signedLoadTorque);
        state.frictionTorque(frictionTorque);
        state.netTorque(netTorque);
        state.angularVelocity(omega);
        state.angularAcceleration(angularAcceleration);
        state.rpm(rpm);
        state.electricalPower(electricalPower);
        state.mechanicalPower(mechanicalPower);
        state.copperLoss(copperLoss);
    }

    /**
     * Calculates the steady-state current at a given voltage and speed.
     *
     * This is useful for displaying or predicting the motor's operating
     * point without advancing the simulation.
     */
    public static double calculateSteadyStateCurrent(
            MotorParameters parameters,
            double voltage,
            double rpm
    ) {
        double omega =
                MotorParameters.rpmToRadPerSecond(rpm);

        double backEmf =
                parameters.backEmfConstant() * omega;

        double current =
                (voltage - backEmf) / parameters.resistance();

        return clamp(
                current,
                -parameters.maxCurrent(),
                parameters.maxCurrent()
        );
    }

    /**
     * Calculates back EMF at a given RPM.
     */
    public static double calculateBackEmf(
            MotorParameters parameters,
            double rpm
    ) {
        double omega =
                MotorParameters.rpmToRadPerSecond(rpm);

        return parameters.backEmfConstant() * omega;
    }

    /**
     * Calculates electromagnetic torque from current.
     */
    public static double calculateTorque(
            MotorParameters parameters,
            double current
    ) {
        return parameters.torqueConstant()
                * clamp(
                current,
                -parameters.maxCurrent(),
                parameters.maxCurrent()
        );
    }

    /**
     * Calculates mechanical output power.
     */
    public static double calculateMechanicalPower(
            double torque,
            double rpm
    ) {
        double omega =
                MotorParameters.rpmToRadPerSecond(rpm);

        return torque * omega;
    }

    /**
     * Returns the approximate current required to produce a given torque.
     */
    public static double calculateCurrentForTorque(
            MotorParameters parameters,
            double torque
    ) {
        return clamp(
                torque / parameters.torqueConstant(),
                -parameters.maxCurrent(),
                parameters.maxCurrent()
        );
    }

    private static double directionOrOne(
            double speed,
            double torque
    ) {
        if (Math.abs(speed) > 1e-6)
            return Math.signum(speed);

        if (Math.abs(torque) > 1e-6)
            return Math.signum(torque);

        return 1.0;
    }

    private static double clamp(
            double value,
            double min,
            double max
    ) {
        return Math.max(min, Math.min(max, value));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0;
    }
}