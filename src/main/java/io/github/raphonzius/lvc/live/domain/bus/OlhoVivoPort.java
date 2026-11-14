package io.github.raphonzius.lvc.live.domain.bus;

import io.github.raphonzius.lvc.live.domain.FetchResult;

import java.util.List;

/**
 * Port interface for SPTrans OlhoVivo real-time bus data.
 * Hexagonal architecture boundary — domain side.
 *
 * Only the two endpoints used in the targeted polling workflow are active.
 */
public interface OlhoVivoPort {

    /** Resolves line codes (cl) from a search term via /Linha/Buscar; {@code Unavailable} on upstream failure. */
    FetchResult<List<BusLine.LineData>> searchLines(String terms);

    /** Fetches vehicle positions for a single line via /Posicao/Linha */
    FetchResult<VehiclePosition.PositionResponse> positionsByLine(int lineCode);
}
