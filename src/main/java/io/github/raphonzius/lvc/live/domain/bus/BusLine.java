package io.github.raphonzius.lvc.live.domain.bus;

import java.util.List;

/**
 * Domain model for an SPTrans bus line.
 * Sealed interface — only LineData record is permitted.
 */
public sealed interface BusLine permits BusLine.LineData {

    int cl();
    boolean lc();
    String lt();
    int sl();
    int tl();
    String tp();
    String ts();

    /**
     * Concrete record representation of a bus line.
     * Fields match the OlhoVivo v2.1 API short-key JSON response.
     *
     * @param cl unique line code (per direction)
     * @param lc true if circular (no secondary terminal)
     * @param lt numeric part of the line sign (e.g. "8000")
     * @param sl direction: 1 = main→secondary, 2 = secondary→main
     * @param tl operation mode: 10 BASE, 21/23/32/41 ATENDIMENTO
     * @param tp sign from main terminal to secondary terminal
     * @param ts sign from secondary terminal to main terminal
     */
    record LineData(
            int cl,
            boolean lc,
            String lt,
            int sl,
            int tl,
            String tp,
            String ts
    ) implements BusLine {

        public static List<LineData> empty() {
            return List.of();
        }
    }
}
