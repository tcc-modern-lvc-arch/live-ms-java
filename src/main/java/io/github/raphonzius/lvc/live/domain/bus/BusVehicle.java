package io.github.raphonzius.lvc.live.domain.bus;

/**
 * Domain model for a located bus vehicle.
 * Sealed interface — VehicleData (position) and VehicleWithArrival (forecast) are permitted.
 */
public sealed interface BusVehicle permits BusVehicle.VehicleData, BusVehicle.VehicleWithArrival {

    int p();
    boolean a();
    String ta();
    double py();
    double px();

    /**
     * Vehicle position data from /Posicao endpoints.
     *
     * @param p  vehicle prefix
     * @param a  accessible (true) or not
     * @param ta UTC timestamp of position capture (ISO 8601)
     * @param py latitude
     * @param px longitude
     */
    record VehicleData(
            int p,
            boolean a,
            String ta,
            double py,
            double px
    ) implements BusVehicle {}

    /**
     * Vehicle data enriched with arrival time prediction.
     * Used in /Previsao responses.
     *
     * @param p  vehicle prefix
     * @param a  accessible
     * @param ta UTC timestamp of position capture (ISO 8601)
     * @param t  predicted arrival time at the stop (local time, e.g. "23:11")
     * @param py latitude
     * @param px longitude
     */
    record VehicleWithArrival(
            int p,
            boolean a,
            String ta,
            String t,
            double py,
            double px
    ) implements BusVehicle {}
}
