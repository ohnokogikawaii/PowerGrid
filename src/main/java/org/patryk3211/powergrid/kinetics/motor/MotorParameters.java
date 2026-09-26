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
 * This allows a physically low-speed, high-torque motor to
 * generate a large amount of Create stress capacity without
 * changing the physical motor simulation.
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
     * Maximum permitted current [A].
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
     *
     * Example:
     *
     * physical RPM = 256
     * multiplier   = 3.570556640625
     *
     * Create RPM = 914.0625
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
     * For the simplified permanent-magnet DC motor model,
     * Ke has the same numerical value as Kt.
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
     * Returns the multiplier used when converting the physical
     * motor RPM into Create's kinetic RPM.
     */
    public double createSpeedMultiplier() {
        return createSpeedMultiplier;
    }

    /**
     * Calculate the rated current from rated torque and
     * torque constant.
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
     * Electrical:
     *     300 V
     *     10 kW
     *
     * Physical motor:
     *     maximum 256 RPM
     *
     * Create:
     *     256 RPM * 3.570556640625
     *     = 914.0625 Create RPM
     *     = approximately 58,500 SU
     *
     * The motor therefore behaves as a low-speed,
     * high-torque motor physically.
     */
    public static MotorParameters small() {
        return new MotorParameters(
                "small",

                // Electrical voltage
                300.0,

                // Rated electrical power
                10_000.0,

                // Rated mechanical torque
                94.02,

                // Rated physical RPM
                256.0,

                // Maximum physical RPM
                256.0,

                // Winding resistance
                0.90,

                // Winding inductance
                0.05,

                // Torque constant / back-EMF constant
                2.8207,

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
     * Electrical:
     *     500 V
     *     15 kW
     *
     * Physical motor:
     *     maximum 256 RPM
     *
     * Create:
     *     256 RPM * 5.35888671875
     *     = 1,371.875 Create RPM
     *     = approximately 87,800 SU
     */
    public static MotorParameters medium() {
        return new MotorParameters(
                "medium",

                // Electrical voltage
                500.0,

                // Rated electrical power
                15_000.0,

                // Rated mechanical torque
                93.97,

                // Rated physical RPM
                256.0,

                // Maximum physical RPM
                256.0,

                // Winding resistance
                1.67,

                // Winding inductance
                0.10,

                // Torque constant / back-EMF constant
                3.1323,

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
     * Electrical:
     *     1,000 V
     *     18 kW
     *
     * Physical motor:
     *     maximum 256 RPM
     *
     * Create:
     *     256 RPM * 6.427001953125
     *     = 1,645.3125 Create RPM
     *     = approximately 105,300 SU
     */
    public static MotorParameters large() {
        return new MotorParameters(
                "large",

                // Electrical voltage
                1_000.0,

                // Rated electrical power
                18_000.0,

                // Rated mechanical torque
                94.02,

                // Rated physical RPM
                256.0,

                // Maximum physical RPM
                256.0,

                // Winding resistance
                5.56,

                // Winding inductance
                0.20,

                // Torque constant / back-EMF constant
                5.2235,

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