package io.github.raphonzius.lvc.live.domain.bus;

import java.util.List;

/**
 * Domain model for an SPTrans bus stop.
 * Sealed interface — only StopData record is permitted.
 */
public sealed interface BusStop permits BusStop.StopData {

    int cp();
    String np();
    String ed();
    double py();
    double px();

    /**
     * Concrete record representation of a bus stop.
     *
     * @param cp unique stop identifier
     * @param np stop name
     * @param ed stop address
     * @param py latitude
     * @param px longitude
     */
    record StopData(
            int cp,
            String np,
            String ed,
            double py,
            double px
    ) implements BusStop {

        public static List<StopData> empty() {
            return List.of();
        }
    }
}
