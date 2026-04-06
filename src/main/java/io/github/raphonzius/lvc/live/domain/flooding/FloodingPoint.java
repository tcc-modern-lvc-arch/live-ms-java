package io.github.raphonzius.lvc.live.domain.flooding;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Domain model for a CGESP flooding point.
 * Sealed interface — only FloodData record is permitted.
 */
public sealed interface FloodingPoint permits FloodingPoint.FloodData {

    /**
     * Classification of a flooding point derived from the CGESP HTML CSS class.
     */
    enum FloodStatus {
        ATIVO_INTRANSITAVEL,
        ATIVO_TRANSITAVEL,
        INATIVO_INTRANSITAVEL,
        INATIVO_TRANSITAVEL;

        public static FloodStatus fromCssClass(String cssClass) {
            return switch (cssClass.trim()) {
                case "ativo-intransitavel"   -> ATIVO_INTRANSITAVEL;
                case "ativo-transitavel"     -> ATIVO_TRANSITAVEL;
                case "inativo-intransitavel" -> INATIVO_INTRANSITAVEL;
                case "inativo-transitavel"   -> INATIVO_TRANSITAVEL;
                default -> throw new IllegalArgumentException("Unknown flood status css class: " + cssClass);
            };
        }
    }

    /**
     * Concrete record representation of a single flooding point scraped from CGESP.
     *
     * @param date         date of the flooding event
     * @param zone         São Paulo zone (e.g. "Zona Leste")
     * @param neighborhood neighborhood (e.g. "São Mateus")
     * @param status       one of the 4 CGESP flood classifications
     * @param startTime    time the flooding started (HH:MM)
     * @param endTime      time the flooding ended — inferred to 23:59 for history or current SP time if active today
     * @param street       street/avenue affected
     * @param direction    traffic direction affected (e.g. "CENTRO/BAIRRO")
     * @param reference    cross-reference landmark (e.g. "AV SAPOPEMBA")
     */
    record FloodData(
            LocalDate date,
            String zone,
            String neighborhood,
            FloodStatus status,
            LocalTime startTime,
            LocalTime endTime,
            String street,
            String direction,
            String reference
    ) implements FloodingPoint {

        public static List<FloodData> empty() {
            return List.of();
        }
    }
}
