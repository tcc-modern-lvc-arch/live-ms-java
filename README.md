# Live Microservice - Java 25

Real-time data adapter for vessel tracking (AIS), bus positions (SPTrans/OlhoVivo), and flooding alerts (CGESP). Streams data to Event Hub via gRPC for downstream consumers.

## Tech Stack

- **Java 25** — Latest LTS with preview features
- **Spring Boot 4** — Application framework
- **Hexagonal Architecture** — Ports & Adapters pattern
- **gRPC** — Streaming to Event Hub
- **Feign** — HTTP clients for external APIs
- **Resilience4j** — Circuit breakers

## Quick Start

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run

# Run tests
./mvnw test

# Start with Docker
docker compose up --build
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/vessels/bounding-box` | GET | Vessels in bounding box (defaults to Brazil coast) |
| `/api/v1/vessels/poll` | POST | Manual AIS polling trigger |
| `/api/v1/buses/lines` | GET | Search bus lines |
| `/api/v1/buses/positions/line` | GET | Bus positions for a line |
| `/api/v1/buses/poll` | POST | Manual bus polling trigger |
| `/api/v1/floodings` | GET | Flooding points for date |
| `/api/v1/floodings/poll` | POST | Manual CGESP polling trigger |

## Architecture

Hexagonal (Ports & Adapters) structure:

```
domain/           # Business logic, sealed interfaces + records
application/      # Services orchestrating domain operations
infrastructure/   # Adapters (web controllers, Feign clients, gRPC)
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
  polling:
    interval: 30000  # ms

olhovivo:
  base-url: https://api.olhovivo.sptrans.com.br/v2.1

eventhub:
  grpc:
    host: localhost
    port: 50051
```

## License

Apache 2.0
