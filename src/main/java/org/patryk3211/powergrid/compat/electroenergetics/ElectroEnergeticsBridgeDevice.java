package org.patryk3211.powergrid.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.TickingElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class ElectroEnergeticsBridgeDevice extends SimulatedDevice
        implements TickingElectricalDevice {

    private double lastPowerGridVoltage;
    private double lastElectroEnergeticsCurrent;

    public ElectroEnergeticsBridgeDevice(
            SimulatedDeviceType<ElectroEnergeticsBridgeDevice> type,
            Level level,
            BlockPos pos,
            DevicesSavedData deviceSD) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        double voltage = getPowerGridVoltage();
        lastPowerGridVoltage = voltage;

        /*
         * Stage 1:
         * Power Grid is the voltage source and EE is the receiving circuit.
         *
         * A small source resistance keeps the EE solver well-conditioned.
         * The two electrical networks are never merged.
         */
        bridges.builder(pos)
                .voltageSourceWithResistance(0, 1, 0.01, voltage);
    }

    @Override
    public void postTick(SimulationResults results) {
        /*
         * This is intentionally only measured for now.
         * Stage 2 will use this current to feed a controlled current source
         * back into the Power Grid side.
         */
        lastElectroEnergeticsCurrent =
                results.getCurrentThrough(pos, 0, 1);
    }

    private double getPowerGridVoltage() {
        if (level == null)
            return 0;

        if (!(level.getBlockEntity(pos)
                instanceof ElectroEnergeticsBridgeBlockEntity bridge))
            return 0;

        return bridge.getBridgeVoltage();
    }

    public double getLastPowerGridVoltage() {
        return lastPowerGridVoltage;
    }

    public double getLastElectroEnergeticsCurrent() {
        return lastElectroEnergeticsCurrent;
    }
}
