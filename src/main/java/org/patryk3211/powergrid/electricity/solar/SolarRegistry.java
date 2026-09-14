package org.patryk3211.powergrid.electricity.solar;

import java.util.HashMap;
import java.util.Map;


public class SolarRegistry {

    private static final Map<String, SolarSpec> PANELS =
            new HashMap<>();


    /**
     * Default solar panel.
     *
     * Approximately 409 W.
     */
    public static final SolarSpec DEFAULT_PANEL =
            new SolarSpec(
                    108,     // Cells
                    42.8,    // Voc [V]
                    14.9,    // Isc [A]
                    37.2,    // Vmpp [V]
                    13.8    // Impp [A]
            );


    /*
     * LVYUAN 410 W class panel.
     *
     * Vmpp × Impp
     * = 37.2 × 11.02
     * ≈ 409.94 W
     */
    public static final SolarSpec LVYUAN_410W =
            new SolarSpec(
                    108,
                    42.8,
                    14.9,
                    37.2,
                    11.02
            );


    static {

        register(
                "default",
                DEFAULT_PANEL
        );

        register(
                "lvyuan_410w",
                LVYUAN_410W
        );
    }


    public static void register(
            String id,
            SolarSpec spec
    ) {
        PANELS.put(
                id,
                spec
        );
    }


    public static SolarSpec get(
            String id
    ) {
        return PANELS.getOrDefault(
                id,
                DEFAULT_PANEL
        );
    }
}