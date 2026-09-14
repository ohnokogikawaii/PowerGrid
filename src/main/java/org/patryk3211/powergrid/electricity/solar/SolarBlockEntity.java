package org.patryk3211.powergrid.electricity.solar;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

public class SolarBlockEntity
        extends ElectricBlockEntity
        implements IHaveGoggleInformation {

    /*
     * 使用するSolarSpec
     */
    private final SolarSpec spec;

    private double temperature = 25.0;

    private static final double MAX_IRRADIANCE = 1000.0;

    private VoltageSourceCoupling source;

    private final MPPTController mppt;

    /*
     * ゴーグル表示用
     */
    private double displayVoltage = 0.0;
    private double displayCurrent = 0.0;
    private double displayPower = 0.0;
    private double displayMaximumPower = 0.0;
    private double displayOutputPercent = 0.0;
    public double getAvailablePower;
    public SolarBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    ) {
        super(type, pos, state);

        /*
         * LVYUAN 410W
         */
        spec = SolarRegistry.LVYUAN_410W;

        mppt = new MPPTController(spec);

        setLazyTickRate(5);
    }

    /**
     * 太陽高度による発電係数
     *
     * 0 = 夜
     * 1 = 正午
     */
    private double getSolarFactor() {

        if (level == null)
            return 0.0;

        long time =
                level.getDayTime() % 24000;

        /*
         * 夜
         */
        if (time > 12000)
            return 0.0;

        double progress;

        /*
         * 0～6000
         *
         * 朝6時 → 昼12時
         */
        if (time <= 6000) {

            progress =
                    time / 6000.0;

        } else {

            /*
             * 6000～12000
             *
             * 昼12時 → 夕方18時
             */
            progress =
                    (12000.0 - time)
                            / 6000.0;
        }

        return Math.sin(
                progress * Math.PI / 2.0
        );
    }

    /**
     * 現在の日射量
     */
    private double getIrradiance() {

        return
                MAX_IRRADIANCE
                        * getSolarFactor();
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {

        /*
         * terminal 0 = ＋
         * terminal 1 = －
         */
        builder.setTerminalCount(2);

        /*
         * Solarはテブナン等価回路として表現する。
         *
         * 実際の電圧源電圧はelectricalTick()で設定する。
         */
        source =
                builder.addInternalNode(
                        VoltageSourceCoupling.class,
                        builder.terminalNode(0),
                        builder.terminalNode(1),
                        1.0f
                );
        /*
         * このVoltageSourceはSolarのMPPT電源。
         *
         * StabilizedPowerSupplyが
         * MPPT ONの場合に検出する。
         */
        source.setMpptSource(
                true
        );
    }

    @Override
    public void electricalTick() {

        if (source == null)
            return;

        double irradiance =
                getIrradiance();

        /*
         * 現在の日射・温度条件から
         * MPPT動作点を計算
         */
        mppt.update(
                irradiance,
                temperature
        );

        /*
         * MPPT等価回路
         *
         * Vth = 2 × Vmpp
         *
         * Rint = Vmpp / Impp
         */
        source.setVoltage(
                mppt.getTheveninVoltage()
        );

        source.setResistance(
                (float) mppt.getResistance()
        );

        /*
         * ソルバーが計算した実電流。
         *
         * Solarでは発電方向を正として扱う。
         */
        double current =
                Math.max(
                        0.0,
                        -source.getCurrent()
                );

        /*
         * 実際の端子電圧を取得する。
         *
         * VoltageSourceCoupling.getVoltage()は
         * 内部電圧源の電圧なので使用しない。
         */
        double positiveVoltage =
                source.getPositive().getVoltage();

        double negativeVoltage = 0.0;

        if (source.getNegative() != null) {
            negativeVoltage =
                    source.getNegative().getVoltage();
        }

        double voltage =
                Math.max(
                        0.0,
                        positiveVoltage - negativeVoltage
                );

        /*
         * 実際に負荷へ供給されている電力
         */
        double power =
                voltage * current;

        /*
         * 現在の日射条件での最大電力
         */
        double maximumPower =
                mppt.getMaximumPower();

        /*
         * 最大電力に対する現在の出力率
         */
        double outputPercent = 0.0;

        if (maximumPower > 0.000001) {

            outputPercent =
                    power
                            / maximumPower
                            * 100.0;

            outputPercent =
                    Math.clamp(
                            outputPercent
                            ,
                            0.0,
                            100.0);
        }

        /*
         * ゴーグル表示
         */
        displayVoltage = voltage;
        displayCurrent = current;
        displayPower = power;
        displayMaximumPower = maximumPower;
        displayOutputPercent = outputPercent;

        setChanged();
    }

    public double getSolarPower() {
        return displayPower;
    }

    public double getVoltage() {
        return displayVoltage;
    }

    public double getCurrent() {
        return displayCurrent;
    }

    public double getPower() {
        return displayPower;
    }

    public double getMaximumPower() {
        return displayMaximumPower;
    }

    public double getOutputPercent() {
        return displayOutputPercent;
    }

    @Override
    protected void write(
            CompoundTag tag,
            HolderLookup.Provider registries,
            boolean clientPacket
    ) {
        super.write(tag, registries, clientPacket);

        tag.putDouble("DisplayVoltage", displayVoltage);
        tag.putDouble("DisplayCurrent", displayCurrent);
        tag.putDouble("DisplayPower", displayPower);
        tag.putDouble("DisplayMaximumPower", displayMaximumPower);
        tag.putDouble("DisplayOutputPercent", displayOutputPercent);
    }

    @Override
    public void lazyTick() {

        super.lazyTick();

        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    @Override
    protected void read(
            CompoundTag tag,
            HolderLookup.Provider registries,
            boolean clientPacket
    ) {
        super.read(tag, registries, clientPacket);

        displayVoltage = tag.getDouble("DisplayVoltage");
        displayCurrent = tag.getDouble("DisplayCurrent");
        displayPower = tag.getDouble("DisplayPower");
        displayMaximumPower = tag.getDouble("DisplayMaximumPower");
        displayOutputPercent = tag.getDouble("DisplayOutputPercent");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        IHaveGoggleInformation.super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        Lang.translate("gui.solar_info")
                .forGoggles(tooltip);
        LangBuilder line;

        tooltip.add(
                Component.literal(
                        String.format(
                                "Voltage: %.2f V",
                                displayVoltage
                        )
                )
        );

        tooltip.add(
                Component.literal(
                        String.format(
                                "Current: %.2f A",
                                displayCurrent
                        )
                )
        );

        tooltip.add(
                Component.literal(
                        String.format(
                                "Power: %.2f W",
                                displayPower
                        )
                )
        );

        tooltip.add(
                Component.literal(
                        "Maximum: "
                                + String.format(
                                "%.2f W",
                                displayMaximumPower
                        )
                )
        );

        tooltip.add(
                Component.literal(
                        String.format(
                                "Output: %.1f%%",
                                displayOutputPercent
                        )
                )
        );

        return true;
    }
}