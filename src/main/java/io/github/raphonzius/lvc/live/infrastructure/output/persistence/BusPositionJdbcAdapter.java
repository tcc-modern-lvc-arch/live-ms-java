package io.github.raphonzius.lvc.live.infrastructure.output.persistence;

import io.github.raphonzius.lvc.live.domain.bus.BusPositionPersistencePort;
import io.github.raphonzius.lvc.live.domain.bus.BusVehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BusPositionJdbcAdapter implements BusPositionPersistencePort {

    private static final String INSERT_POSITION = """
            INSERT INTO olhovivo.bus_positions (
                line_code, line_sign, vehicle_prefix, accessible, position_timestamp,
                latitude, longitude, snapshot_reference
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void savePositions(
            int lineCode,
            String lineSign,
            String snapshotReference,
            List<BusVehicle.VehicleData> vehicles
    ) {
        if (vehicles.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT_POSITION, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                BusVehicle.VehicleData vehicle = vehicles.get(index);
                statement.setInt(1, lineCode);
                statement.setString(2, lineSign);
                statement.setInt(3, vehicle.p());
                statement.setBoolean(4, vehicle.a());
                statement.setString(5, vehicle.ta());
                statement.setDouble(6, vehicle.py());
                statement.setDouble(7, vehicle.px());
                statement.setString(8, snapshotReference);
            }

            @Override
            public int getBatchSize() {
                return vehicles.size();
            }
        });
    }
}
