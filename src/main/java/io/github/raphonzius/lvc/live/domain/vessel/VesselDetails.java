package io.github.raphonzius.lvc.live.domain.vessel;

/**
 * Optional descriptive attributes of a vessel, as reported by the provider.
 *
 * <p>Kept apart from {@link Vessel.VesselData} so the core position report stays small: only
 * consumers that need the full description (the REST v1 contract) read this record; the
 * event-hub mapping never does. Names are Java names; the legacy snake_case wire/JSON names
 * live in the adapters ({@code AisVesselResponse}, {@code VesselV1Response}).</p>
 *
 * @param vesselId             provider vessel id
 * @param vesselClass          provider vessel class
 * @param imo                  IMO number
 * @param name                 registered name exactly as reported (before falling back to {@code nameAis})
 * @param nameAis              AIS-broadcast name
 * @param shipTypeId           ship type id
 * @param detailedTypeId       detailed ship type id
 * @param timestampOfPosition  provider position timestamp exactly as reported ({@code null} when absent)
 * @param length               length (m)
 * @param beam                 beam (m)
 * @param toBow                distance from antenna to bow (m)
 * @param toStern              distance from antenna to stern (m)
 * @param toPort               distance from antenna to port side (m)
 * @param toStarboard          distance from antenna to starboard side (m)
 * @param courseOverGround     course over ground (degrees)
 * @param draught              draught (m)
 * @param navigationalStatusId navigational status id
 * @param flag                 flag state
 * @param latGrid              provider latitude grid cell
 * @param lonGrid              provider longitude grid cell
 */
public record VesselDetails(
        Long vesselId,
        String vesselClass,
        Long imo,
        String name,
        String nameAis,
        Long shipTypeId,
        Long detailedTypeId,
        Long timestampOfPosition,
        Long length,
        Long beam,
        Long toBow,
        Long toStern,
        Long toPort,
        Long toStarboard,
        Double courseOverGround,
        Double draught,
        Long navigationalStatusId,
        String flag,
        Long latGrid,
        Long lonGrid
) {
}
