package org.patryk3211.powergrid.kinetics.motor;

/**
 * Static parameters of a DC motor.
 *
 * All values use SI units:
 *
 * Voltage       V
 * Current       A
 * Resistance    ohm
 * Inductance    H
 * Torque        N*m
 * Inertia       kg*m^2
 * Speed         RPM
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
     * Torque constant [N*m/A].
     *
     * For the simplified permanent-magnet DC motor model,
     * Ke has the same numerical value in V*s/rad.
     */
    private final double torqueConstant;

    /**
     * Maximum permitted current.
     */
    private final double maxCurrent;

    /**
     * Rotor inertia [kg*m^2].
     */
    private final double inertia;

    /**
     * Mechanical friction torque [N*m].
     */
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

    public double ratedCurrent() {
        if (torqueConstant <= 0)
            return 0;

        return ratedTorque / torqueConstant;
    }

    public static double rpmToRadPerSecond(double rpm) {
        return rpm * Math.PI / 30.0;
    }

    public static double radPerSecondToRPM(double radPerSecond) {
        return radPerSecond * 30.0 / Math.PI;
    }

    /**
     * Small motor.
     *
     * 256 V
     * 5 kW
     * 200 N*m
     * 240 RPM rated
     * 256 RPM maximum
     */
    public static MotorParameters small() {
        return new MotorParameters(
                "small",
                256.0,
                5_000.0,
                200.0,
                240.0,
                256.0,
                0.2335,
                0.05,
                10.0,
                30.0,
                10.0,
                5.0
        );
    }

    /**
     * Medium motor.
     *
     * 512 V
     * 10 kW
     * 250 N*m
     * 380 RPM rated
     * 512 RPM maximum
     */
    public static MotorParameters medium() {
        return new MotorParameters(
                "medium",
                512.0,
                10_000.0,
                250.0,
                380.0,
                512.0,
                0.729,
                0.10,
                12.5,
                30.0,
                30.0,
                8.0
        );
    }

    /**
     * Large motor.
     *
     * 1024 V
     * 18 kW
     * 300 N*m
     * 570 RPM rated
     * 768 RPM maximum
     */
    public static MotorParameters large() {
        return new MotorParameters(
                "large",
                1_024.0,
                18_000.0,
                300.0,
                570.0,
                768.0,
                1.61,
                0.20,
                16.7,
                30.0,
                80.0,
                12.0
        );
    }
}