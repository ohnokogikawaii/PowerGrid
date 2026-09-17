package org.patryk3211.powergrid.compat.electroenergetics;

import net.neoforged.bus.api.IEventBus;

public final class ElectroEnergeticsBridgeRegistration {

    private ElectroEnergeticsBridgeRegistration() {
    }

    public static void register(IEventBus modBus) {
        ElectroEnergeticsCompat.init(modBus);
    }
}