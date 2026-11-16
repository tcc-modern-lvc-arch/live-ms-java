# Live Microservice - Java 25

Real-time data adapter for vessel tracking (AIS), bus positions (SPTrans/OlhoVivo), and flooding alerts (CGESP). Publishes the data to Event Hub via gRPC for downstream consumers.

## Tech Stack

- **Java 25** — Final JEPs only (no preview features)
- **Spring Boot 4.1.2** — Application framework
- **Spring Cloud 2025.1.4** — OpenFeign, Resilience4j circuit breakers, Vault config
- **gRPC 1.85.1 / protobuf 4.36.2** — Event Hub contracts from `proto-shared`
- **Hexagonal Architecture** — Ports & Adapters pattern

## Quick Start

```bash
# Build
mvn -B -ntp clean package

# Run
mvn spring-boot:run

# Run tests
mvn test

# Start with Docker (run from the parent directory of the repos)
docker compose up --build
```

## API Endpoints

Versioning uses the `X-API-Version` header (supported: `1.0`); paths have no version segment.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/vessels/bounding-box` | GET | Vessels in bounding box (defaults to Brazil coast) |
| `/api/vessels/poll` | POST | Manual AIS polling trigger |
| `/api/buses/lines` | GET | Search bus lines (`q`) |
| `/api/buses/positions/line` | GET | Bus positions for a line (`codigoLinha`) |
| `/api/buses/poll` | POST | Manual bus polling trigger |
| `/api/floodings` | GET | Flooding points for a date (`date`) |
| `/api/floodings/range` | GET | Flooding points for a date range (`from`, `to`) |
| `/api/floodings/poll` | POST | Manual CGESP polling trigger |
| `/actuator/health` | GET | Health (probes: `/liveness`, `/readiness`) |

Swagger UI: `/swagger-ui.html`.

## Architecture

Hexagonal (Ports & Adapters) structure:

```
domain/           # Business logic, sealed interfaces + records, ports
application/      # Services orchestrating domain operations
infrastructure/   # Adapters (web controllers, Feign clients, gRPC publishing, scheduler)
```

Data flows: **Scheduler** → **Service** → **Domain Port** → **Adapter** → **External API** → back to **Service** → **Streaming Port** → **gRPC Adapter** → **Event Hub**

## External Data Sources

- **AIS** — AISFriends API (vessel positions, no auth)
- **OlhoVivo** — SPTrans API (bus positions, cookie auth)
- **CGESP** — HTML scraping (flooding alerts)

## Configuration

Key properties in `application.yaml`:

```yaml
ais:
  api.url: https://www.aisfriends.com
  polling:
    interval: 30000  # ms

olhovivo:
  api.url: https://api.olhovivo.sptrans.com.br/v2.1
  polling:
    interval: 60000  # ms

event-hub:
  grpc:
    host: localhost
    port: 50051
    deadline: 2s
    max-concurrency: 32
    batch-size: 500
```

## Docker

- The build context is the parent directory that holds the sibling repos (`live-ms-java/` and `proto-shared/`): `docker build -f live-ms-java/Dockerfile .`
- The image trains a JDK 25 AOT cache at build time, so startup is faster (about 1.8 s to ready in the measured run).
- Runs as non-root UID 10001. The HEALTHCHECK uses `/actuator/health/readiness`.
- No AppCDS, entrypoint script or data volume.

## CI

`.github/workflows/security.yml` runs `mvn verify` and two Trivy gates (filesystem and image) that block HIGH/CRITICAL vulnerabilities with an available fix.

## Documentation

- `docs/adr/0001-spring-boot-vs-quarkus.md`: decision record (Spring Boot 4.1 kept for now)
- `bench/README.md`: Spring Boot vs Quarkus benchmark of the AIS slice
- `poc-quarkus/README.md`: Quarkus proof of concept of the AIS slice

## License

Apache 2.0
