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
 * Static parameters describing a DC electric motor.
 *
 * All electrical and mechanical values use SI units:
 * - voltage: V
 * - current: A
 * - resistance: ohm
 * - inductance: H
 * - torque: N*m
 * - inertia: kg*m^2
 * - angular velocity: rad/s
 * - power: W
 */
public final class MotorParameters {

    private final String name;

    private final double ratedVoltage;
    private final double ratedPower;
    private final double ratedTorque;
    private final double ratedRPM;
    private final double maxRPM;

    private final double resistance;
    private final double inductance;

    /**
     * Torque constant in N*m/A.
     *
     * For a conventional permanent-magnet DC motor, the numerical
     * value of the torque constant is also used as the back-EMF
     * constant when the latter is expressed in V*s/rad.
     */
    private final double torqueConstant;

    /**
     * Maximum permitted motor current.
     *
     * This is intentionally higher than the rated current so that
     * starting current and short-duration overload can occur.
     */
    private final double maxCurrent;

    private final double inertia;
    private final double frictionTorque;

    public MotorParameters(
            String name,
            double ratedVoltage,
            double ratedPower,
            double ratedTorque,
            double ratedRPM,
            double maxRPM,
            double resistance,
            double inductance,
            double torqueConstant,
            double maxCurrent,
            double inertia,
            double frictionTorque
    ) {
        this.name = name;
        this.ratedVoltage = ratedVoltage;
        this.ratedPower = ratedPower;
        this.ratedTorque = ratedTorque;
        this.ratedRPM = ratedRPM;
        this.maxRPM = maxRPM;
        this.resistance = resistance;
        this.inductance = inductance;
        this.torqueConstant = torqueConstant;
        this.maxCurrent = maxCurrent;
        this.inertia = inertia;
        this.frictionTorque = frictionTorque;
    }

    public String name() {
        return name;
    }

    public double ratedVoltage() {
        return ratedVoltage;
    }

    public double ratedPower() {
        return ratedPower;
    }

    public double ratedTorque() {
        return ratedTorque;
    }

    public double ratedRPM() {
        return ratedRPM;
    }

    public double maxRPM() {
        return maxRPM;
    }

    public double resistance() {
        return resistance;
    }

    public double inductance() {
        return inductance;
    }

    public double torqueConstant() {
        return torqueConstant;
    }

    /**
     * Back-EMF constant in V*s/rad.
     *
     * For the simplified permanent-magnet DC motor model:
     *
     *     E = Ke * omega
     *
     * and Ke == Kt numerically.
     */
    public double backEmfConstant() {
        return torqueConstant;
    }

    public double maxCurrent() {
        return maxCurrent;
    }

    public double inertia() {
        return inertia;
    }

    public double frictionTorque() {
        return frictionTorque;
    }

    /**
     * Rated current estimated from rated electrical power.
     */
    public double ratedCurrent() {
        if (ratedVoltage <= 0)
            return 0;

        return ratedPower / ratedVoltage;
    }

    /**
     * Converts RPM to rad/s.
     */
    public static double rpmToRadPerSecond(double rpm) {
        return rpm * Math.PI / 30.0;
    }

    /**
     * Converts rad/s to RPM.
     */
    public static double radPerSecondToRPM(double radiansPerSecond) {
        return radiansPerSecond * 30.0 / Math.PI;
    }

    /**
     * Small motor.
     *
     * Approximate rated operating point:
     * 256 V, 5 kW, 200 N*m, 240 RPM.
     */
    public static MotorParameters small() {
        return new MotorParameters(
                "small",
                256.0,
                5_000.0,
                200.0,
                240.0,
                256.0,
                0.80,
                0.15,
                10.0,
                30.0,
                10.0,
                5.0
        );
    }

    /**
     * Medium motor.
     *
     * Approximate rated operating point:
     * 512 V, 10 kW, 250 N*m, 380 RPM.
     */
    public static MotorParameters medium() {
        return new MotorParameters(
                "medium",
                512.0,
                10_000.0,
                250.0,
                380.0,
                512.0,
                1.60,
                0.30,
                12.5,
                30.0,
                30.0,
                8.0
        );
    }

    /**
     * Large motor.
     *
     * Approximate rated operating point:
     * 1024 V, 18 kW, 300 N*m, 570 RPM.
     */
    public static MotorParameters large() {
        return new MotorParameters(
                "large",
                1_024.0,
                18_000.0,
                300.0,
                570.0,
                768.0,
                3.20,
                0.60,
                16.7,
                30.0,
                80.0,
                12.0
        );
    }
}