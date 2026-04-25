package io.github.raphonzius.lvc.live.domain.vessel;

/**
 * Domain model representing a vessel with AIS data.
 * Sealed interface — only VesselData record is permitted.
 * Uses Java 25 features: sealed interfaces, records, pattern matching.
 */
public sealed interface Vessel permits Vessel.VesselData {

    long id();
    String name();
    String shipType();
    double latitude();
    double longitude();
    long timestamp();

    /**
     * Concrete implementation of Vessel as a record.
     * Immutable, compact representation of vessel data.
     */
    record VesselData(
            long id,
            Long vessel_id,
            String vesselClass,
            Long imo,
            Long mmsi,
            String name,
            String name_ais,
            Long ship_type_id,
            Long detailed_type_id,
            Long timestamp_of_position,
            Long length,
            Long beam,
            Long to_bow,
            Long to_stern,
            Long to_port,
            Long to_starboard,
            Long true_heading,
            Double course_over_ground,
            Double speed_over_ground,
            Double draught,
            Long navigational_status_id,
            String flag,
            double latitude,
            double longitude,
            Long lat_grid,
            Long lon_grid
    ) implements Vessel {

        @Override
        public long id() {
            return id;
        }

        @Override
        public String name() {
            return name != null ? name : name_ais;
        }

        @Override
        public String shipType() {
            return "Type_" + (detailed_type_id != null ? detailed_type_id : 0);
        }

        @Override
        public double latitude() {
            return latitude;
        }

        @Override
        public double longitude() {
            return longitude;
        }

        @Override
        public long timestamp() {
            return timestamp_of_position != null ? timestamp_of_position : 0L;
        }
    }
}
