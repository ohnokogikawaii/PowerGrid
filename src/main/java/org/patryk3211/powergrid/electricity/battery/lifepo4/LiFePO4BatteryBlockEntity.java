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
        package org.patryk3211.powergrid.electricity.battery.lifepo4;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

public class LiFePO4BatteryBlockEntity
        extends BatteryBlockEntity
        implements IHaveGoggleInformation {

    private final LiFePO4BMS bms =
            new LiFePO4BMS();

    /*
     * 16S LiFePO4
     *
     * 0%   = 3.00V/cell = 48.0V
     * 100% = 3.65V/cell = 58.4V
     */
    private static final float MIN_VOLTAGE =
            48.0f;

    private static final float MAX_VOLTAGE =
            58.4f;

    /*
     * 通常時の内部抵抗。
     */
    private static final float NORMAL_RESISTANCE =
            0.004f;

    public LiFePO4BatteryBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    ) {
        super(type, pos, state);

        setLazyTickRate(2);
    }

    @Override
    public ThermalBehaviour specifyThermalBehaviour() {
        return null;
    }

    /**
     * SOCを0.0～1.0で取得する。
     *
     * SOCそのものはenergyから求める。
     */
    private double calculateSOC() {

        if (capacity <= 0.0)
            return 0.0;

        double soc =
                energy / capacity;

        if (soc < 0.0)
            return 0.0;

        if (soc > 1.0)
            return 1.0;

        return soc;
    }

    /**
     * バッテリー電圧を取得する。
     *
     * 電圧はSOCから計算する。
     *
     * 0%   -> 48.0V
     * 100% -> 58.4V
     */
    public double getBatteryVoltage() {

        double soc =
                calculateSOC();

        return spec.calculateVoltage(
                (float) soc
        );
    }

    /**
     * BMSへセル電圧を設定する。
     */
    private void updateBMSCellVoltages(
            double voltage
    ) {

        float cellVoltage =
                (float) (voltage / 16.0);

        for (int i = 0; i < 16; i++) {

            bms.setCellVoltage(
                    i,
                    cellVoltage
            );
        }
    }

    /**
     * BMSの現在状態をVoltageSourceCouplingへ反映する。
     *
     * 重要：
     *
     * ここではsetResistance()を使わない。
     *
     * 0%:
     *     放電禁止
     *     充電許可
     *
     * 100%:
     *     充電禁止
     *     放電許可
     *
     * 中間:
     *     両方向許可
     */
    private void updateBMSProtection() {

        if (sourceCoupling == null)
            return;

        double soc = calculateSOC();

        boolean chargeBlocked =
                bms.isChargeBlocked();

        boolean dischargeBlocked =
                bms.isDischargeBlocked();

        // SOCによる強制保護
        if (soc <= 0.0) {
            dischargeBlocked = true;
            chargeBlocked = false;
        }

        if (soc >= 1.0) {
            chargeBlocked = true;
            dischargeBlocked = false;
        }

        sourceCoupling.setResistance(
                NORMAL_RESISTANCE
        );

        sourceCoupling.setCurrentDirectionBlocked(
                chargeBlocked,
                dischargeBlocked
        );
    }

    @Override
    public void electricalTick() {

        if (sourceCoupling == null)
            return;

        /*
         * -------------------------------------------------
         * 1. 現在の電圧
         * -------------------------------------------------
         */
        double voltage =
                getBatteryVoltage();

        /*
         * -------------------------------------------------
         * 2. BMSセル電圧更新
         * -------------------------------------------------
         */
        updateBMSCellVoltages(
                voltage
        );

        /*
         * -------------------------------------------------
         * 3. 現在電流をBMSへ渡す
         * -------------------------------------------------
         */
        float current =
                (float) sourceCoupling.getCurrent();

        bms.setCurrent(
                current
        );

        /*
         * -------------------------------------------------
         * 4. BMS判定
         * -------------------------------------------------
         */
        bms.update();

        /*
         * -------------------------------------------------
         * 5. BMS保護状態を回路へ反映
         * -------------------------------------------------
         */
        updateBMSProtection();

        /*
         * -------------------------------------------------
         * 6. 電力計算
         * -------------------------------------------------
         *
         * BatteryBlockEntityの仕様：
         *
         * power > 0
         *     放電
         *
         * power < 0
         *     充電
         */
        float power =
                calculatePower();

        /*
         * -------------------------------------------------
         * 7. Energy更新
         * -------------------------------------------------
         */
        energy -=
                power * 0.05f;

        /*
         * -------------------------------------------------
         * 8. Energy範囲制限
         * -------------------------------------------------
         */
        if (energy < 0.0)
            energy = 0.0;

        if (energy > capacity)
            energy = capacity;

        /*
         * -------------------------------------------------
         * 9. エネルギー状態から電圧を更新
         * -------------------------------------------------
         */
        updateParameters();

        /*
         * -------------------------------------------------
         * 10. BMS状態を再計算
         * -------------------------------------------------
         *
         * SOCが0%または100%に到達したtickで、
         * 次の回路計算に確実に反映させる。
         */
        voltage =
                getBatteryVoltage();

        updateBMSCellVoltages(
                voltage
        );



        setChanged();
    }

    @Override
    public boolean addToGoggleTooltip(
            List<Component> tooltip,
            boolean isPlayerSneaking
    ) {
        Lang.translate("gui.lifepo4_info").forGoggles(tooltip);
        Lang.builder().translate("gui.lifepo4_info");

        tooltip.add(
                Component.literal(
                        "LiFePO4 Battery"
                ).withStyle(
                        ChatFormatting.GOLD
                )
        );

        double voltage =
                getBatteryVoltage();

        double current =
                sourceCoupling != null
                        ? sourceCoupling.getCurrent()
                        : 0.0;

        double soc =
                calculateSOC() * 100.0;

        double power =
                calculatePower();

        /*
         * Voltage
         */
        tooltip.add(
                Component.literal(
                        String.format(
                                "Voltage: %.2f V",
                                voltage
                        )
                )
        );

        /*
         * Current
         */
        tooltip.add(
                Component.literal(
                        String.format(
                                "Current: %.2f A",
                                current
                        )
                )
        );

        /*
         * Energy
         */
        tooltip.add(
                Component.literal(
                        String.format(
                                "Energy: %s / %s",
                                formatEnergy(energy),
                                formatEnergy(capacity)
                        )
                )
        );

        /*
         * Power
         */
        tooltip.add(
                Component.literal(
                        String.format(
                                "Power: %s",
                                formatPower(
                                        (float) power
                                )
                        )
                )
        );

        /*
         * SOC
         */
        tooltip.add(
                Component.literal(
                        String.format(
                                "SOC: %.2f%%",
                                soc
                        )
                )
        );

        /*
         * BMS状態
         */
        /*
         * ゴーグル表示用のBMS状態。
         *
         * LiFePO4:
         * 48.0V = 0%
         * 58.4V = 100%
         *
         * 0%:
         *     充電 OK
         *     放電 BLOCKED
         *
         * 100%:
         *     充電 BLOCKED
         *     放電 OK
         */
        double batteryVoltage = getBatteryVoltage();

        boolean chargeBlocked =
                batteryVoltage >= MAX_VOLTAGE - 0.001;

        boolean dischargeBlocked =
                batteryVoltage <= MIN_VOLTAGE + 0.001;


        /*
         * Charge
         */
        if (chargeBlocked) {

            tooltip.add(
                    Component.literal(
                            "Charge: BLOCKED"
                    ).withStyle(
                            ChatFormatting.RED
                    )
            );

        } else {

            tooltip.add(
                    Component.literal(
                            "Charge: OK"
                    ).withStyle(
                            ChatFormatting.GREEN
                    )
            );
        }


        /*
         * Discharge
         */
        if (dischargeBlocked) {

            tooltip.add(
                    Component.literal(
                            "Discharge: BLOCKED"
                    ).withStyle(
                            ChatFormatting.RED
                    )
            );

        } else {

            tooltip.add(
                    Component.literal(
                            "Discharge: OK"
                    ).withStyle(
                            ChatFormatting.GREEN
                    )
            );
        }

        return true;
    }

    /**
     * 電力表示。
     *
     * 放電:
     *     -
     *
     * 充電:
     *     +
     */
    private String formatPower(
            float watt
    ) {

        String sign =
                watt >= 0.0f
                        ? "-"
                        : "+";

        float abs =
                Math.abs(watt);

        if (abs >= 1000.0f) {

            return String.format(
                    "%s%.2f kW",
                    sign,
                    abs / 1000.0f
            );
        }

        return String.format(
                "%s%.0f W",
                sign,
                abs
        );
    }

    /**
     * EnergyをJからWh/kWhへ変換する。
     */
    private String formatEnergy(
            double joule
    ) {

        double wh =
                joule / 3600.0;

        if (wh >= 1000.0) {

            return String.format(
                    "%.2f kWh",
                    wh / 1000.0
            );
        }

        return String.format(
                "%.0f Wh",
                wh
        );
    }

    /**
     * SOCを%で返す。
     */
    public double getSOC() {

        return calculateSOC() * 100.0;
    }

    public LiFePO4BMS getBMS() {

        return bms;
    }

    public double getMinimumVoltage() {

        return MIN_VOLTAGE;
    }

    public double getMaximumVoltage() {

        return MAX_VOLTAGE;
    }
}

