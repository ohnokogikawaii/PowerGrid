package org.patryk3211.powergrid.electricity.battery.lifepo4;

import org.patryk3211.powergrid.electricity.battery.BatterySpec;

public class SimpleBatterySpec {

    public static final BatterySpec LIFEPO4 =
            new BatterySpec() {

                @Override
                public float getInitialCharge() {
                    return 18_432_000f * 0.5f;
                }

                @Override
                public float getMaxCharge() {
                    return 18_432_000f;
                }

                @Override
                public float calculateVoltage(float soc) {

                    /*
                     * =========================================
                     * 51.2V LiFePO4 / 16S
                     * =========================================
                     *
                     * 公称電圧 : 51.2V
                     * 16セル × 3.20V
                     *
                     * 満充電電圧 : 58.4V
                     * 16セル × 3.65V
                     *
                     * SOCはenergy / capacityから求める。
                     * 電圧からSOCを逆算しない。
                     *
                     * 休止状態に近い電圧の近似カーブ。
                     *
                     * 0%   ≒ 48.0V
                     * 10%  ≒ 50.0V
                     * 20%  ≒ 51.0V
                     * 30%  ≒ 51.5V
                     * 40%  ≒ 51.7V
                     * 50%  ≒ 51.8V
                     * 60%  ≒ 52.0V
                     * 70%  ≒ 52.1V
                     * 80%  ≒ 52.3V
                     * 90%  ≒ 52.5V
                     * 95%  ≒ 53.0V
                     * 100% = 58.4V
                     *
                     * LiFePO4は中間SOCでは電圧変化が小さく、
                     * 満充電付近で電圧が大きく上昇する。
                     */

                    soc = Math.max(
                            0.0f,
                            Math.min(1.0f, soc)
                    );

                    /*
                     * -----------------------------------------
                     * 0 ～ 10%
                     * 48.0V → 50.0V
                     * -----------------------------------------
                     */
                    if (soc <= 0.10f) {

                        float x =
                                soc / 0.10f;

                        return 48.0f
                                + x * 2.0f;
                    }

                    /*
                     * -----------------------------------------
                     * 10 ～ 20%
                     * 50.0V → 51.0V
                     * -----------------------------------------
                     */
                    if (soc <= 0.20f) {

                        float x =
                                (soc - 0.10f)
                                        / 0.10f;

                        return 50.0f
                                + x * 1.0f;
                    }

                    /*
                     * -----------------------------------------
                     * 20 ～ 30%
                     * 51.0V → 51.5V
                     * -----------------------------------------
                     */
                    if (soc <= 0.30f) {

                        float x =
                                (soc - 0.20f)
                                        / 0.10f;

                        return 51.0f
                                + x * 0.5f;
                    }

                    /*
                     * -----------------------------------------
                     * 30 ～ 90%
                     * 51.5V → 52.5V
                     *
                     * LiFePO4の平坦な領域。
                     * -----------------------------------------
                     */
                    if (soc <= 0.90f) {

                        float x =
                                (soc - 0.30f)
                                        / 0.60f;

                        return 51.5f
                                + x * 1.0f;
                    }

                    /*
                     * -----------------------------------------
                     * 90 ～ 95%
                     * 52.5V → 53.0V
                     * -----------------------------------------
                     */
                    if (soc <= 0.95f) {

                        float x =
                                (soc - 0.90f)
                                        / 0.05f;

                        return 52.5f
                                + x * 0.5f;
                    }

                    /*
                     * -----------------------------------------
                     * 95 ～ 100%
                     *
                     * 53.0V → 58.4V
                     *
                     * 満充電付近の急激な電圧上昇を
                     * smoothstepで再現する。
                     * -----------------------------------------
                     */

                    float x =
                            (soc - 0.95f)
                                    / 0.05f;

                    /*
                     * smoothstep
                     *
                     * x = 0 → 0
                     * x = 1 → 1
                     */
                    x =
                            x * x * (3.0f - 2.0f * x);

                    return 53.0f
                            + x * 5.4f;
                }

                @Override
                public float calculateResistance(float soc) {

                    /*
                     * バッテリー内部抵抗
                     *
                     * 現在は一定値。
                     */
                    return 0.004f;
                }

                public float getVoltage(float charge) {

                    return calculateVoltage(charge);
                }


                public float getResistance(float charge) {

                    return calculateResistance(charge);
                }
            };
}

