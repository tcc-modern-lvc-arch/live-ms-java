package io.github.raphonzius.lvc.live.infrastructure.input.external.cgesp;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for the CGESP flooding page.
 * Returns raw HTML — parsing is handled by {@link CgespAdapter}.
 *
 * <p>Base URL: <a href="https://www.cgesp.org">https://www.cgesp.org</a></p>
 */
@FeignClient(
        name = "cgesp-api",
        url = "https://www.cgesp.org",
        configuration = CgespFeignConfiguration.class
)
public interface CgespFeignClient {

    /**
     * Fetches the flooding HTML page for a given date.
     *
     * @param dataBusca  date in DD/MM/YYYY format (will be URL-encoded as DD%2FMM%2FYYYY)
     * @param enviaBusca always "Buscar"
     * @return raw HTML of the alagamentos page
     */
    @GetMapping("/v3/alagamentos.jsp")
    String fetchFloodingsHtml(
            @RequestParam("dataBusca") String dataBusca,
            @RequestParam("enviaBusca") String enviaBusca
    );
}
