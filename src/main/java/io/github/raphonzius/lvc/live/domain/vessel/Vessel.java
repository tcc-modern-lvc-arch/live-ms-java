package io.github.raphonzius.lvc.live.domain.vessel;

/**
 * Domain model representing a vessel position report.
 * Sealed interface — only {@link VesselData} is permitted.
 *
 * <p>Field names are domain names; the AIS Friends wire format (snake_case JSON, extra
 * fields) lives in the AIS input adapter ({@code AisVesselResponse}) and never reaches here.</p>
 */
public sealed interface Vessel permits Vessel.VesselData {

    long id();
    String name();
    String shipType();
    double latitude();
    double longitude();
    long timestamp();

    /**
     * Immutable vessel position report, carrying only the fields the service uses.
     *
     * @param id         provider-assigned identifier (fallback entity id when {@code mmsi} is absent)
     * @param mmsi       Maritime Mobile Service Identity, if reported
     * @param name       registered name, falling back to the AIS-broadcast name; may be {@code null}
     * @param shipType   ship type label (e.g. {@code Type_71}); may be {@code null}
     * @param latitude   latitude in decimal degrees
     * @param longitude  longitude in decimal degrees
     * @param timestamp  position timestamp as reported by the provider ({@code 0} when unknown)
     * @param speedKnots speed over ground in knots, if reported
     * @param headingDeg true heading in degrees, if reported
     */
    record VesselData(
            long id,
            Long mmsi,
            String name,
            String shipType,
            double latitude,
            double longitude,
            long timestamp,
            Double speedKnots,
            Integer headingDeg
    ) implements Vessel {
    }
}
