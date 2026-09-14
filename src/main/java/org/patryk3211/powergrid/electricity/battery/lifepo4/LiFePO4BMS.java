package org.patryk3211.powergrid.electricity.battery.lifepo4;

public class LiFePO4BMS {

    private static final float MAX_CURRENT = 100f;

    private boolean chargeBlocked;
    private boolean dischargeBlocked;

    private float temperature = 25f;

    private final float[] cellVoltage = new float[16];

    private float current;

    public void update() {

        chargeBlocked = false;
        dischargeBlocked = false;

        // セル電圧保護
        for (int i = 0; i < 16; i++) {

            float voltage = cellVoltage[i];

            // 過充電
            if (voltage >= 3.65f) {
                chargeBlocked = true;
            }

            // 過放電
            if (voltage <= 3.00f) {
                dischargeBlocked = true;
            }
        }

        // 温度保護
        if (temperature < 0.0f || temperature > 50.0f) {
            chargeBlocked = true;
            dischargeBlocked = true;
        }

        // 過電流
        if (Math.abs(current) > MAX_CURRENT) {

            /*
             * BatteryBlockEntityでは
             *
             * current > 0
             *     放電
             *
             * current < 0
             *     充電
             *
             * として扱う。
             */
            if (current > 0.0f) {
                dischargeBlocked = true;
            } else {
                chargeBlocked = true;
            }
        }
    }

    public void setCurrent(float current) {
        this.current = current;
    }

    public float getCurrent() {
        return current;
    }

    public boolean isChargeBlocked() {
        return chargeBlocked;
    }

    public boolean isDischargeBlocked() {
        return dischargeBlocked;
    }

    public void setCellVoltage(int cell, float voltage) {
        if (cell >= 0 && cell < 16) {
            cellVoltage[cell] = voltage;
        }
    }

    public float getCellVoltage(int cell) {
        return cellVoltage[cell];
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public float getTemperature() {
        return temperature;
    }
}