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
            long vessel_id,
            String vesselClass,
            long imo,
            long mmsi,
            String name,
            String name_ais,
            long ship_type_id,
            long detailed_type_id,
            long timestamp_of_position,
            long length,
            long beam,
            long to_bow,
            long to_stern,
            long to_port,
            long to_starboard,
            long true_heading,
            double course_over_ground,
            double speed_over_ground,
            double draught,
            long navigational_status_id,
            String flag,
            double latitude,
            double longitude,
            long lat_grid,
            long lon_grid
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
            return "Type_" + detailed_type_id;
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
            return timestamp_of_position;
        }
    }
}
