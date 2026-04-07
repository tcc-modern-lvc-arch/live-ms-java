package io.github.raphonzius.lvc.live.infrastructure.input.external.cgesp;

import io.github.raphonzius.lvc.live.domain.TimeZones;
import io.github.raphonzius.lvc.live.domain.flooding.FloodingPoint;
import io.github.raphonzius.lvc.live.domain.flooding.FloodingPort;
import io.github.raphonzius.lvc.live.infrastructure.exception.CgespApiException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter implementing {@link FloodingPort} by scraping the CGESP HTML page with Jsoup.
 *
 * <p>Parsing strategy:
 * <ol>
 *   <li>Locate {@code div.col-alagamentos div.content} — the left column containing flooding data.</li>
 *   <li>Iterate children: {@code h1.tit-bairros} sets the current zone; {@code table.tb-pontos-de-alagamentos}
 *       contains neighborhood + one or more {@code div.ponto-de-alagamento} entries.</li>
 *   <li>For each point: extract status (CSS class), start/end time, street, direction, and reference.</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CgespAdapter implements FloodingPort {

    private static final ZoneId SAO_PAULO_TZ = TimeZones.SAO_PAULO;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern TIME_PATTERN = Pattern.compile("De (\\d{2}:\\d{2}) a\\s*(\\d{2}:\\d{2})?");
    private static final Set<String> STATUS_CLASSES = Set.of(
            "ativo-intransitavel", "ativo-transitavel", "inativo-intransitavel", "inativo-transitavel"
    );

    private final CgespFeignClient feignClient;

    @Override
    @CircuitBreaker(name = "cgesp-api", fallbackMethod = "fetchFloodingsFallback")
    public List<FloodingPoint.FloodData> fetchFloodings(LocalDate date) {
        String dateParam = date.format(DATE_FMT);
        log.debug("Fetching CGESP floodings for date={}", dateParam);
        String html = feignClient.fetchFloodingsHtml(dateParam, "Buscar");
        return parseHtml(html, date);
    }

    /** Circuit breaker fallback — returns empty list. */
    public List<FloodingPoint.FloodData> fetchFloodingsFallback(LocalDate date, Exception ex) {
        if (ex instanceof CallNotPermittedException) {
            log.warn("CGESP circuit open — skipping date={}", date);
        } else {
            log.warn("CGESP retries exhausted for date={}: {}", date, ex.getMessage());
        }
        return FloodingPoint.FloodData.empty();
    }

    // -------------------------------------------------------------------------
    // HTML parsing
    // -------------------------------------------------------------------------

    private List<FloodingPoint.FloodData> parseHtml(String html, LocalDate date) {
        Document doc = Jsoup.parse(html);
        Element content = doc.selectFirst("div.col-alagamentos div.content");
        if (content == null) {
            throw new CgespApiException(
                    "CGESP: flooding content section not found for date=" + date
                    + " — HTML structure may have changed", 200);
        }

        boolean isToday = date.equals(LocalDate.now(SAO_PAULO_TZ));
        List<FloodingPoint.FloodData> result = new ArrayList<>();
        String currentZone = null;

        for (Element child : content.children()) {
            if ("h1".equals(child.tagName()) && child.hasClass("tit-bairros")) {
                currentZone = child.text().trim();
            } else if ("table".equals(child.tagName()) && child.hasClass("tb-pontos-de-alagamentos")) {
                String neighborhood = extractNeighborhood(child);
                for (Element pointDiv : child.select("div.ponto-de-alagamento")) {
                    FloodingPoint.FloodData point = parseFloodPoint(pointDiv, date, currentZone, neighborhood, isToday);
                    if (point != null) result.add(point);
                }
            }
        }

        log.debug("CGESP: parsed {} flooding point(s) for date={}", result.size(), date);
        return result;
    }

    private String extractNeighborhood(Element table) {
        Element bairroTd = table.selectFirst("td.bairro");
        // ownText() excludes text inside child elements (e.g. the <hr/>)
        return bairroTd != null ? bairroTd.ownText().trim() : "";
    }

    private FloodingPoint.FloodData parseFloodPoint(
            Element pointDiv, LocalDate date, String zone, String neighborhood, boolean isToday) {

        // --- Status ---
        Element statusLi = pointDiv.selectFirst(
                "li.ativo-intransitavel, li.ativo-transitavel, li.inativo-intransitavel, li.inativo-transitavel");
        if (statusLi == null) {
            log.warn("CGESP: no status li found in flood point — skipping");
            return null;
        }
        String statusClass = statusLi.classNames().stream()
                .filter(STATUS_CLASSES::contains)
                .findFirst()
                .orElse(null);
        if (statusClass == null) return null;

        FloodingPoint.FloodStatus status;
        try {
            status = FloodingPoint.FloodStatus.fromCssClass(statusClass);
        } catch (IllegalArgumentException e) {
            log.warn("CGESP: unknown status class '{}' — skipping", statusClass);
            return null;
        }

        // --- Time + street from li.col-local ---
        // HTML example: "De 17:52 a <br/>AV JOSE ALENCAR GOMES DA SILVA"
        //           or: "De 17:50 a 19:00<br/>R ANTONIO DE ALMEIDA"
        Element timeLi = pointDiv.selectFirst("li.col-local");
        String[] timeParts = timeLi != null
                ? timeLi.html().split("(?i)<br\\s*/?>")
                : new String[0];

        LocalTime startTime = null;
        LocalTime endTime = null;
        String street = "";

        if (timeParts.length >= 1) {
            String timeLine = stripTags(timeParts[0]).trim();
            Matcher m = TIME_PATTERN.matcher(timeLine);
            if (m.find()) {
                startTime = LocalTime.parse(m.group(1));
                if (m.group(2) != null) {
                    endTime = LocalTime.parse(m.group(2));
                } else if (isToday) {
                    endTime = LocalTime.now(SAO_PAULO_TZ);
                } else {
                    endTime = LocalTime.of(23, 59);
                }
            }
        }
        if (timeParts.length >= 2) {
            street = stripTags(timeParts[1]).trim();
        }

        // --- Direction + reference from second li.arial-descr-alag (not col-local) ---
        // HTML example: "Sentido: CENTRO/BAIRRO<br/>Referência: AV SAPOPEMBA"
        String direction = "";
        String reference = "";
        Element descLi = pointDiv.selectFirst("li.arial-descr-alag:not(.col-local)");
        if (descLi != null) {
            String[] descParts = descLi.html().split("(?i)<br\\s*/?>");
            if (descParts.length >= 1) {
                direction = stripTags(descParts[0]).replace("Sentido: ", "").trim();
            }
            if (descParts.length >= 2) {
                reference = stripTags(descParts[1])
                        .replace("Referência: ", "")
                        .replace("Referencia: ", "")
                        .trim();
            }
        }

        return new FloodingPoint.FloodData(date, zone, neighborhood, status, startTime, endTime, street, direction, reference);
    }

    private static String stripTags(String html) {
        return Jsoup.parse(html).text();
    }
}
