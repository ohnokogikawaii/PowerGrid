package org.patryk3211.powergrid.electricity.solar;


public class SolarSpec {

    // セル数
    private final int cells;

    // 開放電圧 V
    private final double voc;

    // 短絡電流 A
    private final double isc;

    // 最大電力点電圧 V
    private final double vmpp;

    // 最大電力点電流 A
    private final double impp;

    /*
     * 電圧温度係数
     *
     * 例:
     * -0.003 = -0.3 %/℃
     */
    private final double voltageTempCoefficient;

    /*
     * 電流温度係数
     *
     * 例:
     * +0.0005 = +0.05 %/℃
     */
    private final double currentTempCoefficient;


    public SolarSpec(
            int cells,
            double voc,
            double isc,
            double vmpp,
            double impp
    ) {

        this(
                cells,
                voc,
                isc,
                vmpp,
                impp,
                -0.003,
                0.0005
        );

    }


    public SolarSpec(
            int cells,
            double voc,
            double isc,
            double vmpp,
            double impp,
            double voltageTempCoefficient,
            double currentTempCoefficient
    ) {

        this.cells =
                cells;

        this.voc =
                voc;

        this.isc =
                isc;

        this.vmpp =
                vmpp;

        this.impp =
                impp;

        this.voltageTempCoefficient =
                voltageTempCoefficient;

        this.currentTempCoefficient =
                currentTempCoefficient;

    }


    public int getCells() {
        return cells;
    }


    public double getVoc() {
        return voc;
    }


    public double getIsc() {
        return isc;
    }


    public double getVmpp() {
        return vmpp;
    }


    public double getImpp() {
        return impp;
    }


    /**
     * 温度補正後Voc
     *
     * 基準温度: 25℃
     */
    public double getTemperatureVoltage(
            double temperature
    ) {

        double delta =
                temperature - 25.0;

        return voc *
                (
                        1.0
                                + voltageTempCoefficient * delta
                );

    }


    /**
     * 温度補正後Vmpp
     *
     * 基準温度: 25℃
     */
    public double getTemperatureVmpp(
            double temperature
    ) {

        double delta =
                temperature - 25.0;

        return vmpp *
                (
                        1.0
                                + voltageTempCoefficient * delta
                );

    }


    /**
     * 温度補正後Isc
     *
     * 基準温度: 25℃
     */
    public double getTemperatureCurrent(
            double temperature
    ) {

        double delta =
                temperature - 25.0;

        return isc *
                (
                        1.0
                                + currentTempCoefficient * delta
                );

    }


    /**
     * 温度補正後Impp
     *
     * 基準温度: 25℃
     */
    public double getTemperatureImpp(
            double temperature
    ) {

        double delta =
                temperature - 25.0;

        return impp *
                (
                        1.0
                                + currentTempCoefficient * delta
                );

    }


    /**
     * 温度補正後最大電力
     */
    public double getTemperaturePmpp(
            double temperature
    ) {

        return getTemperatureVmpp(temperature)
                *
                getTemperatureImpp(temperature);

    }


    /**
     * 25℃での最大電力
     */
    public double getPmpp() {

        return vmpp * impp;

    }

}