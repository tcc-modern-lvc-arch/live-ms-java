package io.github.raphonzius.lvc.live.domain.bus;

import java.util.List;

/** Output port for persisting OlhoVivo vehicle position snapshots. */
public interface BusPositionPersistencePort {

    void savePositions(
            int lineCode,
            String lineSign,
            String snapshotReference,
            List<BusVehicle.VehicleData> vehicles
    );
}
