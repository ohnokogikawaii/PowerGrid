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
     * Last current calculated by Electro Energetics.
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
     * Power Grid voltage is supplied to EE as a Thevenin
     * voltage source.
     *
     * Node 0 = +
     * Node 1 = -
     */

    @Override
    public void preTick(BridgeCollector bridges) {

    }

    /*
     * ------------------------------------------------------------
     * EE post-tick
     * ------------------------------------------------------------
     *
     * EE has finished solving its circuit here.
     *
     * Read the actual current flowing through the bridge and
     * transfer it to the Power Grid side.
     */

    @Override
    public void postTick(SimulationResults results) {
        double current =
                results.getCurrentThrough(
                        pos,
                        0,
                        1
                );

        if (!Double.isFinite(current)) {
            current = 0.0;
        }

        lastElectroEnergeticsCurrent = current;

        /*
         * Transfer the EE current to Power Grid.
         */
        if (level != null) {
            if (level.getBlockEntity(pos)
                    instanceof ElectroEnergeticsBridgeBlockEntity bridge) {

                bridge.setElectroEnergeticsCurrent(current);
            }
        }
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
     * Debug / monitoring accessors
     * ------------------------------------------------------------
     */

    public double getLastPowerGridVoltage() {
        return lastPowerGridVoltage;
    }

    public double getLastElectroEnergeticsCurrent() {
        return lastElectroEnergeticsCurrent;
    }
}