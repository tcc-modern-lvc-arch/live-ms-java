package io.github.raphonzius.lvc.live.domain.bus;

import java.util.List;

/**
 * Domain model for an SPTrans smart corridor (corredor inteligente).
 * Sealed interface — only CorridorData record is permitted.
 */
public sealed interface Corridor permits Corridor.CorridorData {

    int cc();
    String nc();

    /**
     * @param cc unique corridor identifier
     * @param nc corridor name
     */
    record CorridorData(
            int cc,
            String nc
    ) implements Corridor {

        public static List<CorridorData> empty() {
            return List.of();
        }
    }
}
