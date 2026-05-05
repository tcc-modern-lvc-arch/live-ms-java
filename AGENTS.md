# Project Context

## Overview

Live microservice for real-time vessel tracking (AIS), bus positions (SPTrans/OlhoVivo), and flooding alerts (CGESP). Streams data to Event Hub via gRPC for downstream consumers.

**Tech Stack:** Java 25, Spring Boot 4, Hexagonal Architecture (Ports & Adapters), gRPC, Feign HTTP clients

## Quick Start

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run single test class
./mvnw test -Dtest=ClassName

# Start full stack
docker compose up --build
```

**Default endpoints (port 8080):**
- `GET /api/v1/vessels/bounding-box` — vessels in bounding box (defaults to Brazil coast)
- `POST /api/v1/vessels/poll` — manual AIS polling trigger
- `GET /api/v1/vessels/health` — health check

## Architecture

### Hexagonal Structure (Ports & Adapters)

```
domain/
  vessel/
    AisPort                    # Input port (fetching data)
    Vessel                     # Sealed interface + VesselData record
  bus/ flooding/               # Same pattern for OlhoVivo and CGESP
  streaming/
    VesselStreamingPort        # Output port (AIS)
    BusStreamingPort           # Output port (OlhoVivo)
    FloodingStreamingPort      # Output port (CGESP)
  TimeZones                    # Shared ZoneId constants

application/
  service/
    AisService / OlhoVivoService / CgespService
  exception/                   # Sealed ApplicationException hierarchy

infrastructure/
  adapter/web/
    VesselController / OlhoVivoController / CgespController
  input/external/
    ais/ olhovivo/ cgesp/      # Feign clients + CircuitBreaker adapters
  output/streaming/grpc/
    Grpc*StreamingAdapter      # Publishes to Event Hub via gRPC
  scheduler/
    *PollingScheduler          # @Scheduled per data source
  config/
    JacksonConfiguration / WebConfig
    properties/*Properties
```

### Data Flow Example (AIS)

`AisPollingScheduler` → `AisService` → `AisPort` (domain) → `AisAdapter` → `AisFeignClient` → back through `AisService` → `VesselStreamingPort` → `GrpcVesselStreamingAdapter` → Event Hub via gRPC

## Conventions

- **Domain models:** Java 25 sealed interfaces + records (records can't extend classes)
- **Ports:** Plain Java interfaces in `domain` package — no framework deps
- **Adapters:** `@Component` implementations in `infrastructure`
- **Circuit breaker:** `@CircuitBreaker(name = "ais-api")` on `AisAdapter.fetchVessels`, fallback returns empty list. Config in `application.yaml`
- **Polling interval:** Controlled by `ais.polling.interval` (ms) in `application.yaml` (default 30000)
- **Lombok:** Requires explicit `annotationProcessorPaths` in `maven-compiler-plugin`
- **Virtual threads:** Enabled via `spring.threads.virtual.enabled: true`
- **Record patterns:** Used in switch expressions for type-safe deconstruction

## Streaming Channels

Data is published to Event Hub via gRPC for downstream consumption.

| Data | Port | Adapter | Destination |
|------|------|---------|-------------|
| AIS vessel positions | `VesselStreamingPort` | `GrpcVesselStreamingAdapter` | Event Hub (gRPC) |
| SPTrans bus positions | `BusStreamingPort` | `GrpcBusStreamingAdapter` | Event Hub (gRPC) |
| CGESP flooding points | `FloodingStreamingPort` | `GrpcFloodingStreamingAdapter` | Event Hub (gRPC) |

## External APIs

### AIS (AISFriends)
- HTTP client via Feign to aisfriends.com
- No auth required

### OlhoVivo (SPTrans)
- Base URL: `https://api.olhovivo.sptrans.com.br/v2.1`
- Cookie-based session auth via `OlhoVivoAuthInterceptor`
- Lazy auth: POST `/Login/Autenticar?token={token}` on first request, caches `JSESSIONID`
- Re-authenticates on 401

**Endpoints:**
- `GET /api/buses/lines?q=` — search bus lines
- `GET /api/buses/positions/line?codigoLinha=` — positions for one line
- `POST /api/buses/poll` — manual trigger

### CGESP (Flooding)
- Base URL: `https://www.cgesp.org`
- No auth — HTML scraping via Jsoup
- Endpoint: `GET /v3/alagamentos.jsp?dataBusca=DD%2FMM%2FYYYY`
- Scrapes: zone (`h1.tit-bairros`), neighborhood (`td.bairro`), flooding points (`div.ponto-de-alagamento`)
- End time inference: missing → current SP time (if today) or `23:59` (if historical)

## Common Tasks

### Add a New External API
See `domain/template/NewApiTemplate.java` for step-by-step guide covering all layers: domain model → port → Feign client → config → error decoder → adapter → service → scheduler → controller → `application.yaml`.

### Update Polling Interval
Edit `ais.polling.interval` in `application.yaml` (milliseconds).

### Adjust gRPC Settings
Edit gRPC configuration in `application.yaml`.

## Key Files

| File | Purpose |
|------|---------|
| `pom.xml` | Maven config, Lombok annotation processor |
| `docker-compose.yml` | App orchestration |
| `application.yaml` | Circuit breaker, polling intervals, API base URLs |
| `domain/template/NewApiTemplate.java` | Template for adding new APIs |
