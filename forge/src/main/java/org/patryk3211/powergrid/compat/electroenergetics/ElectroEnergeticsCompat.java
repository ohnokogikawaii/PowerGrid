package org.patryk3211.powergrid.compat.electroenergetics;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.patryk3211.powergrid.PowerGrid;

import java.util.List;

public final class ElectroEnergeticsCompat {

    private ElectroEnergeticsCompat() {
    }

    /**
     * Electro Energetics simulated-device registration.
     *
     * The block, item and block entity are registered separately in
     * ElectroEnergeticsBridgeRegistration.
     */
    public static final DeferredRegister<SimulatedDeviceType<?>> DEVICES =
            DeferredRegister.create(
                    CEERegistries.SIMULATED_DEVICE_TYPE,
                    PowerGrid.MOD_ID
            );

    public static final DeferredHolder<
            SimulatedDeviceType<?>,
            SimulatedDeviceType<ElectroEnergeticsBridgeDevice>
            > BRIDGE_DEVICE =
            DEVICES.register(
                    "electroenergetics_bridge",
                    () -> new SimulatedDeviceType<>(
                            PowerGrid.asResource("electroenergetics_bridge"),

                            (type, level, pos, deviceSD) ->
                                    new ElectroEnergeticsBridgeDevice(
                                            type,
                                            (ServerLevel) level,
                                            pos,
                                            deviceSD
                                    ),

                            List.of(
                                    ElectroEnergeticsBridgeRegistration
                                            .BRIDGE_BLOCK
                                            .get()
                            )
                    )
            );

    public static void init(IEventBus bus) {
        DEVICES.register(bus);
    }
}