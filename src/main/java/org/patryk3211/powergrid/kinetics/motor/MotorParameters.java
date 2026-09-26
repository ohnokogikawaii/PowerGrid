package org.patryk3211.powergrid.kinetics.motor;

/**
 * Static parameters of a DC motor.
 *
 * All physical motor values use SI units:
 *
 * Voltage       V
 * Current       A
 * Resistance    ohm
 * Inductance    H
 * Torque        N*m
 * Inertia       kg*m^2
 * Speed         RPM
 *
 * The motor's physical speed is limited independently from
 * Create's kinetic speed.
 *
 * Physical motor RPM:
 *     maximum = maxRPM()
 *
 * Create RPM:
 *     physical RPM * createSpeedMultiplier()
 *
 * This class describes a permanent-magnet DC motor.
 *
 * Electrical model:
 *
 *     V = R * I + L * dI/dt + Ke * omega
 *
 * Mechanical model:
 *
 *     T = Kt * I
 *
 * For this simplified SI model:
 *
 *     Kt = Ke
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
     * For a permanent-magnet DC motor in SI units,
     * this has the same numerical value as Ke.
     */
    private final double torqueConstant;

    /**
     * Maximum permitted current [A].
     *
     * This is a protection/overload limit.
     * It is NOT a constant current target.
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

    /**
     * Conversion from physical motor RPM to Create RPM.
     */
    private final double createSpeedMultiplier;

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
            double frictionTorque,
            double createSpeedMultiplier
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
        this.createSpeedMultiplier = createSpeedMultiplier;
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

    /**
     * Maximum physical motor speed.
     *
     * This is intentionally independent from Create's
     * global maxRotationSpeed config.
     */
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
     * Back-EMF constant.
     *
     * For the permanent-magnet DC motor model:
     *
     *     Ke = Kt
     */
    public double backEmfConstant() {
        return torqueConstant;
    }

    /**
     * Maximum permitted current.
     *
     * This is NOT the normal operating current.
     */
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
     * Conversion from physical motor RPM to Create RPM.
     */
    public double createSpeedMultiplier() {
        return createSpeedMultiplier;
    }

    /**
     * Calculate the current corresponding to the rated torque.
     */
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
     * Rated:
     *
     *     300 V
     *     10 kW
     *     256 RPM
     *
     * At 256 RPM:
     *
     *     omega = 26.8076 rad/s
     *
     * Rated torque:
     *
     *     T = P / omega
     *       = approximately 373 N*m
     *
     * The motor is intentionally modeled as a
     * low-speed / high-torque DC motor.
     */
    public static MotorParameters small() {
        return new MotorParameters(
                "small",

                // Rated voltage
                300.0,

                // Rated power
                10_000.0,

                // Rated torque
                372.99,

                // Rated physical RPM
                256.0,

                // Maximum physical RPM
                256.0,

                // Winding resistance
                0.45,

                // Winding inductance
                0.05,

                // Torque / back-EMF constant
                10.94,

                // Maximum current
                40.0,

                // Rotor inertia
                10.0,

                // Mechanical friction
                1.41,

                // 256 RPM -> 914.0625 Create RPM
                3.570556640625
        );
    }

    /**
     * Medium motor.
     *
     * Rated:
     *
     *     500 V
     *     15 kW
     *     256 RPM
     */
    public static MotorParameters medium() {
        return new MotorParameters(
                "medium",

                // Rated voltage
                500.0,

                // Rated power
                15_000.0,

                // Rated torque
                559.49,

                // Rated physical RPM
                256.0,

                // Maximum physical RPM
                256.0,

                // Winding resistance
                0.85,

                // Winding inductance
                0.10,

                // Torque / back-EMF constant
                17.36,

                // Maximum current
                36.0,

                // Rotor inertia
                30.0,

                // Mechanical friction
                0.94,

                // 256 RPM -> 1,371.875 Create RPM
                5.35888671875
        );
    }

    /**
     * Large motor.
     *
     * Rated:
     *
     *     1000 V
     *     18 kW
     *     256 RPM
     */
    public static MotorParameters large() {
        return new MotorParameters(
                "large",

                // Rated voltage
                1_000.0,

                // Rated power
                18_000.0,

                // Rated torque
                671.39,

                // Rated physical RPM
                256.0,

                // Maximum physical RPM
                256.0,

                // Winding resistance
                2.10,

                // Winding inductance
                0.20,

                // Torque / back-EMF constant
                34.00,

                // Maximum current
                22.0,

                // Rotor inertia
                80.0,

                // Mechanical friction
                0.78,

                // 256 RPM -> 1,645.3125 Create RPM
                6.427001953125
        );
    }
}