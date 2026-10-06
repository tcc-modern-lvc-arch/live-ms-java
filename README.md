# Live Microservice - Java 25

Live bus position reader for SPTrans OlhoVivo. Polls configured bus lines and stores every vehicle position snapshot in PostgreSQL.

## Tech Stack

- **Java 25** — Latest LTS with preview features
- **Spring Boot 4** — Application framework
- **Hexagonal Architecture** — Ports & Adapters pattern
- **PostgreSQL** — Historical bus position storage
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
| `/api/buses/lines` | GET | Search bus lines |
| `/api/buses/positions/line` | GET | Bus positions for a line |
| `/api/buses/poll` | POST | Manual bus polling trigger |

## Architecture

Hexagonal (Ports & Adapters) structure:

```
domain/           # Business logic, sealed interfaces + records
application/      # Services orchestrating domain operations
infrastructure/   # OlhoVivo HTTP client, REST endpoints, PostgreSQL adapter
```

Data flows: **Scheduler** → **Service** → **OlhoVivo API** → **Persistence Port** → **PostgreSQL**

Each persisted bus position keeps both OlhoVivo's numeric line code (`cl`) and its readable identifier (`lt-tl`, for example `178L-10`). Positions are stored in the `olhovivo.bus_positions` table. On PostgreSQL, startup creates the `olhovivo` schema and moves an existing `public.bus_positions` table there, preserving its data; it also adds the `line_sign` column when needed.

## External Data Sources

- **OlhoVivo** — SPTrans API (bus positions, cookie auth)

## Configuration

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `OLHOVIVO_TOKEN` in `.env`. For Supabase's session pooler use the pooler host, port `5432`, and username `postgres.<project-ref>`; keep the password in `DB_PASSWORD` (no URL encoding needed). The file is loaded by Spring Boot and Docker Compose and ignored by Git. Supabase connections require SSL.

```yaml
olhovivo:
  base-url: https://api.olhovivo.sptrans.com.br/v2.1
  polling:
    interval: 60000
```

AIS and CGESP event producers are disabled by default. They remain available only when their source flags and `event-hub.enabled` are explicitly enabled.

## License

Apache 2.0
