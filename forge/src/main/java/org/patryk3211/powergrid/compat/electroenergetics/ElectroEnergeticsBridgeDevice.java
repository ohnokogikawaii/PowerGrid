package org.patryk3211.powergrid.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.TickingElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class ElectroEnergeticsBridgeDevice
        extends SimulatedDevice
        implements TickingElectricalDevice {

    private double lastPowerGridVoltage;
    private double lastElectroEnergeticsCurrent;

    public ElectroEnergeticsBridgeDevice(
            SimulatedDeviceType<ElectroEnergeticsBridgeDevice> type,
            ServerLevel level,
            BlockPos pos,
            DevicesSavedData deviceSD
    ) {
        super(
                level,
                pos,
                deviceSD,
                type
        );
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        double voltage = getPowerGridVoltage();

        lastPowerGridVoltage = voltage;

        /*
         * Power Grid -> EE
         *
         * Node 0 = +
         * Node 1 = -
         *
         * A small internal resistance prevents the bridge from becoming
         * an ideal infinite-power source.
         */
        double resistance = 0.01;

        bridges.builder(pos)
                .voltageSourceWithResistance(
                        0,
                        1,
                        resistance,
                        voltage
                );
    }

    @Override
    public void postTick(SimulationResults results) {
        lastElectroEnergeticsCurrent =
                results.getCurrentThrough(
                        pos,
                        0,
                        1
                );
    }

    private double getPowerGridVoltage() {
        if (level == null) {
            return 0.0;
        }

        if (!(level.getBlockEntity(pos)
                instanceof ElectroEnergeticsBridgeBlockEntity bridge)) {
            return 0.0;
        }

        return bridge.getBridgeVoltage();
    }

    public double getLastPowerGridVoltage() {
        return lastPowerGridVoltage;
    }

    public double getLastElectroEnergeticsCurrent() {
        return lastElectroEnergeticsCurrent;
    }
}