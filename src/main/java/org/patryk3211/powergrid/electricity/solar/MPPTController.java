package org.patryk3211.powergrid.electricity.solar;

public class MPPTController {

    private final SolarSpec spec;

    private double voltage;
    private double current;
    private double resistance;
    private double maximumPower;

    public MPPTController(SolarSpec spec) {
        this.spec = spec;
    }

    public void update(
            double irradiance,
            double temperature
    ) {

        /*
         * 基準日射量:
         * 1000 W/m²
         */
        double irradianceFactor =
                Math.max(
                        0.0,
                        Math.min(
                                1.0,
                                irradiance / 1000.0
                        )
                );

        /*
         * 日射量・温度補正後のImpp
         */
        double correctedCurrent =
                spec.getTemperatureImpp(temperature)
                        * irradianceFactor;

        /*
         * 温度補正後のVmpp
         */
        double correctedVoltage =
                spec.getTemperatureVmpp(temperature);

        /*
         * 最大電力
         */
        maximumPower =
                Math.max(
                        0.0,
                        correctedVoltage * correctedCurrent
                );

        /*
         * MPPTの最大電力点
         */
        current = correctedCurrent;
        voltage = correctedVoltage;

        /*
         * 最大電力点に対応する内部抵抗
         *
         * Rmp = Vmpp / Impp
         */
        if (current > 0.001) {
            resistance =
                    voltage / current;
        } else {
            resistance = 1_000_000.0;
        }
    }

    /**
     * 実際のMPPT電圧
     */
    public double getVoltage() {
        return voltage;
    }

    /**
     * MPPT電流
     */
    public double getCurrent() {
        return current;
    }

    /**
     * MPPT点に対応する内部抵抗
     */
    public double getResistance() {
        return resistance;
    }

    /**
     * 現在の日射条件での最大発電電力
     */
    public double getMaximumPower() {
        return maximumPower;
    }

    /**
     * テブナン等価回路の電圧源電圧。
     *
     * Vth = 2 × Vmpp
     *
     * これにより Rload = Rinternal のとき、
     * 端子電圧 = Vmpp
     * 電流 = Impp
     * となる。
     */
    public double getTheveninVoltage() {
        return voltage * 2.0;
    }
}