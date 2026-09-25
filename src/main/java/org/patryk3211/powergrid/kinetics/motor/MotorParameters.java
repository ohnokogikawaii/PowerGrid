
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
     * 300 V
     * 10 kW electrical input
     * approximately 9 kW mechanical output
     * approximately 58,500 SU maximum
     *
     * 58,500 SU / 64 = 914.06 RPM
     */
    public static MotorParameters small() {
        return new MotorParameters(
                "small",
                300.0,
                10_000.0,

                // Approximately 9 kW at 914 RPM.
                94.02,

                914.0625,
                914.0625,

                // Approximately 10% of input power is electrical loss.
                0.90,

                0.05,

                2.8207,

                // Approximately 1.2x rated current.
                40.0,

                10.0,

                // Gives a small no-load current.
                1.41
        );
    }

    /**
     * Medium motor.
     *
     * 500 V
     * 15 kW electrical input
     * approximately 13.5 kW mechanical output
     * approximately 87,800 SU maximum
     *
     * 87,800 SU / 64 = 1,371.875 RPM
     */
    public static MotorParameters medium() {
        return new MotorParameters(
                "medium",
                500.0,
                15_000.0,

                // Approximately 13.5 kW at 1,372 RPM.
                93.97,

                1371.875,
                1371.875,

                // Approximately 10% of input power is electrical loss.
                1.67,

                0.10,

                3.1323,

                // Approximately 1.2x rated current.
                36.0,

                30.0,

                // Gives a small no-load current.
                0.94
        );
    }

    /**
     * Large motor.
     *
     * 1,000 V
     * 18 kW electrical input
     * approximately 16.2 kW mechanical output
     * approximately 105,300 SU maximum
     *
     * 105,300 SU / 64 = 1,645.3125 RPM
     */
    public static MotorParameters large() {
        return new MotorParameters(
                "large",
                1_000.0,
                18_000.0,

                // Approximately 16.2 kW at 1,645 RPM.
                94.02,

                1645.3125,
                1645.3125,

                // Approximately 10% of input power is electrical loss.
                5.56,

                0.20,

                5.2235,

                // Approximately 1.2x rated current.
                22.0,

                80.0,

                // Gives a small no-load current.
                0.78
        );
    }
}

