package io.github.raphonzius.lvc.live.domain.bus;

import java.util.List;

/**
 * Domain model for the OlhoVivo vehicle position response.
 * Sealed interface — only PositionResponse is permitted.
 * Maps to GET /Posicao and GET /Posicao/Linha responses.
 */
public sealed interface VehiclePosition permits VehiclePosition.PositionResponse {

    String hr();

    /**
     * Full position snapshot returned by the API.
     *
     * @param hr  reference timestamp of the data generation
     * @param l   list of lines with located vehicles (used in /Posicao)
     * @param vs  list of vehicles (used in /Posicao/Linha — single line query)
     */
    record PositionResponse(
            String hr,
            List<LinePosition> l,
            List<BusVehicle.VehicleData> vs
    ) implements VehiclePosition {

        public static PositionResponse empty() {
            return new PositionResponse("--:--", List.of(), List.of());
        }
    }

    /**
     * A single line's position data inside a full position snapshot.
     *
     * @param c   full line sign (e.g. "5015-10")
     * @param cl  line code
     * @param sl  direction (1 or 2)
     * @param lt0 destination sign
     * @param lt1 origin sign
     * @param qv  number of located vehicles
     * @param vs  located vehicles
     */
    record LinePosition(
            String c,
            int cl,
            int sl,
            String lt0,
            String lt1,
            int qv,
            List<BusVehicle.VehicleData> vs
    ) {}
}
