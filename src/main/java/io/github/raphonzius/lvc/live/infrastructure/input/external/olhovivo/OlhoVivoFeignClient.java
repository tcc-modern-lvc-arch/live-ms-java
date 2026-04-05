package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import io.github.raphonzius.lvc.live.domain.bus.BusLine;
import io.github.raphonzius.lvc.live.domain.bus.VehiclePosition;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for the SPTrans OlhoVivo API v2.1.
 * Authentication is handled by OlhoVivoAuthInterceptor (cookie-based session).
 *
 * Base URL: https://api.olhovivo.sptrans.com.br/v2.1
 *
 * Only the endpoints required for the targeted polling workflow are active.
 * Commented endpoints are available in the API but not used in this project.
 */
@FeignClient(
        name = "olhovivo-api",
        url = "https://api.olhovivo.sptrans.com.br/v2.1",
        configuration = OlhoVivoFeignConfiguration.class
)
public interface OlhoVivoFeignClient {

    // -------------------------------------------------------------------------
    // ACTIVE — used in targeted polling workflow
    // -------------------------------------------------------------------------

    /** Step 1: resolve line codes from a configured search term */
    @GetMapping("/Linha/Buscar")
    List<BusLine.LineData> searchLines(@RequestParam("termosBusca") String terms);

    /** Step 2: fetch vehicle positions for a single line (codigoLinha = cl from searchLines) */
    @GetMapping("/Posicao/Linha")
    VehiclePosition.PositionResponse positionsByLine(@RequestParam("codigoLinha") int lineCode);

    // -------------------------------------------------------------------------
    // INACTIVE — not carried out in this project (commented for future reference)
    // -------------------------------------------------------------------------

    // @GetMapping("/Linha/BuscarLinhaSentido")
    // List<BusLine.LineData> searchLinesByDirection(
    //         @RequestParam("termosBusca") String terms,
    //         @RequestParam("sentido") int direction
    // );

    // @GetMapping("/Parada/Buscar")
    // List<BusStop.StopData> searchStops(@RequestParam("termosBusca") String terms);

    // @GetMapping("/Parada/BuscarParadasPorLinha")
    // List<BusStop.StopData> stopsByLine(@RequestParam("codigoLinha") int lineCode);

    // @GetMapping("/Parada/BuscarParadasPorCorredor")
    // List<BusStop.StopData> stopsByCorridor(@RequestParam("codigoCorredor") int corridorCode);

    // @GetMapping("/Corredor")
    // List<Corridor.CorridorData> listCorridors();

    // @GetMapping("/Posicao")
    // VehiclePosition.PositionResponse allPositions();

    // @GetMapping("/Previsao")
    // ArrivalForecast.ForecastResponse forecastByStopAndLine(
    //         @RequestParam("codigoParada") int stopCode,
    //         @RequestParam("codigoLinha") int lineCode
    // );

    // @GetMapping("/Previsao/Parada")
    // ArrivalForecast.ForecastResponse forecastByStop(@RequestParam("codigoParada") int stopCode);
}
