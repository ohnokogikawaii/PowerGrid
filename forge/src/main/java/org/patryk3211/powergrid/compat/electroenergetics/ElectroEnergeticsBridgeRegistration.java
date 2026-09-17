package org.patryk3211.powergrid.compat.electroenergetics;

import net.neoforged.bus.api.IEventBus;

/**
 * Small platform-side entry point kept separate so the whole compatibility
 * package is only loaded when Electro Energetics is present.
 */
public final class ElectroEnergeticsBridgeRegistration {
    private ElectroEnergeticsBridgeRegistration() {}

    public static void register(IEventBus modBus) {
        ElectroEnergeticsCompat.init(modBus);
    }
}
