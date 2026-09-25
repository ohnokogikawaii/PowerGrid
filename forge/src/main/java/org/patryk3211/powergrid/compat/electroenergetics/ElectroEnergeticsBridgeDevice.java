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

    /*
     * Last Power Grid voltage exported to EE.
     */
    private double lastPowerGridVoltage;

    /*
     * Last current measured by EE through the bridge.
     *
     * This is currently only recorded.
     * It is not yet fed back into Power Grid.
     */
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

    /*
     * ------------------------------------------------------------
     * EE pre-tick
     * ------------------------------------------------------------
     *
     * Power Grid voltage is converted into an EE voltage source.
     *
     * Node 0 = +
     * Node 1 = -
     */

    @Override
    public void preTick(BridgeCollector bridges) {
        double voltage = getPowerGridVoltage();

        lastPowerGridVoltage = voltage;

        /*
         * A small series resistance prevents the bridge from
         * behaving as a mathematically ideal zero-resistance
         * voltage source.
         *
         * This can be changed later when the electrical coupling
         * model is expanded.
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

    /*
     * ------------------------------------------------------------
     * EE post-tick
     * ------------------------------------------------------------
     *
     * Current is currently measured only.
     *
     * Future implementation can use this value to calculate
     * current/power drawn from the Power Grid network.
     */

    @Override
    public void postTick(SimulationResults results) {
        lastElectroEnergeticsCurrent =
                results.getCurrentThrough(
                        pos,
                        0,
                        1
                );
    }

    /*
     * ------------------------------------------------------------
     * Power Grid voltage acquisition
     * ------------------------------------------------------------
     */

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

    /*
     * ------------------------------------------------------------
     * Debug / future integration accessors
     * ------------------------------------------------------------
     */

    public double getLastPowerGridVoltage() {
        return lastPowerGridVoltage;
    }

    public double getLastElectroEnergeticsCurrent() {
        return lastElectroEnergeticsCurrent;
    }
}