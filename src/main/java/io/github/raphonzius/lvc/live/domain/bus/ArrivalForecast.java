package io.github.raphonzius.lvc.live.domain.bus;

import java.util.List;

/**
 * Domain model for the OlhoVivo arrival forecast response.
 * Sealed interface — only ForecastResponse is permitted.
 * Maps to GET /Previsao, /Previsao/Linha, /Previsao/Parada responses.
 */
public sealed interface ArrivalForecast permits ArrivalForecast.ForecastResponse {

    String hr();

    /**
     * Arrival forecast snapshot.
     *
     * @param hr reference timestamp of the data generation
     * @param p  single stop forecast (used in /Previsao and /Previsao/Parada)
     * @param ps list of stop forecasts (used in /Previsao/Linha)
     */
    record ForecastResponse(
            String hr,
            StopForecast p,
            List<StopForecast> ps
    ) implements ArrivalForecast {

        public static ForecastResponse empty() {
            return new ForecastResponse("--:--", null, List.of());
        }
    }

    /**
     * Forecast for a single bus stop.
     *
     * @param cp stop code
     * @param np stop name
     * @param py latitude
     * @param px longitude
     * @param l  lines serving this stop with vehicle forecasts (used in /Previsao/Parada)
     * @param vs vehicles for this stop (used in /Previsao/Linha)
     */
    record StopForecast(
            int cp,
            String np,
            double py,
            double px,
            List<LineArrival> l,
            List<BusVehicle.VehicleWithArrival> vs
    ) {}

    /**
     * A line's arrival data at a stop.
     *
     * @param c   full line sign
     * @param cl  line code
     * @param sl  direction
     * @param lt0 destination sign
     * @param lt1 origin sign
     * @param qv  number of vehicles
     * @param vs  vehicles with predicted arrival times
     */
    record LineArrival(
            String c,
            int cl,
            int sl,
            String lt0,
            String lt1,
            int qv,
            List<BusVehicle.VehicleWithArrival> vs
    ) {}
}
