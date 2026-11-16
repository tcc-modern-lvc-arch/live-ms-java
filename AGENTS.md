# Project Context

## Overview

Live microservice for real-time vessel tracking (AIS), bus positions (SPTrans/OlhoVivo), and flooding alerts (CGESP). Polls the three external sources and publishes the results to Event Hub via gRPC for downstream consumers.

**Tech Stack:** Java 25 (final JEPs only, no `--enable-preview`), Spring Boot 4.1.2, Spring Cloud 2025.1.4 (OpenFeign, Resilience4j circuit breaker, Vault), gRPC 1.85.1 / protobuf 4.36.2 (contracts from `proto-shared` 1.0.0), Jsoup, Hexagonal Architecture (Ports & Adapters).

## Quick Start

```bash
# Build (Maven on PATH; the repo has no mvnw script)
mvn -B -ntp clean package

# Run
mvn spring-boot:run

# Run tests
mvn test

# Run single test class
mvn test -Dtest=ClassName

# Start full stack (build context is the parent directory, see Docker and CI)
docker compose up --build
```

**Endpoints (port 8080).** API versioning uses the `X-API-Version` header (default and only supported value: `1.0`). Paths have no version segment.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/vessels/bounding-box` | AIS vessels in a bounding box (`lonMin`, `latMin`, `lonMax`, `latMax`, `zoom`; defaults to the Brazil coast) |
| POST | `/api/vessels/poll` | Manual AIS polling trigger |
| GET | `/api/vessels/health` | Liveness message |
| GET | `/api/buses/lines?q=` | Search bus lines |
| GET | `/api/buses/positions/line?codigoLinha=` | Positions for one line |
| POST | `/api/buses/poll` | Manual OlhoVivo polling trigger |
| GET | `/api/buses/health` | Liveness message |
| GET | `/api/floodings?date=` | CGESP flooding points for a date |
| GET | `/api/floodings/range?from=&to=` | Flooding points for a date range (capped by `cgesp.max-range-days`) |
| POST | `/api/floodings/poll` | Manual CGESP polling trigger |
| GET | `/api/floodings/health` | Liveness message |

Other endpoints: `GET /actuator/health` (with `/liveness` and `/readiness` probes; only the health endpoint is exposed). Swagger UI at `/swagger-ui.html`, spec at `src/main/resources/static/openapi.yaml`.

## Architecture

### Hexagonal Structure (Ports & Adapters)

```
domain/
  vessel/                      AisPort (input port), Vessel (sealed interface + VesselData record)
  bus/ flooding/               Same pattern for OlhoVivo and CGESP
  streaming/                   Output ports: VesselStreamingPort, BusStreamingPort, FloodingStreamingPort
  exception/                   DomainException (sealed hierarchy)
  template/NewApiTemplate      Step-by-step guide for adding a new API
  TimeZones                    Shared ZoneId constants

application/
  service/                     AisService / OlhoVivoService / CgespService
  exception/                   ApplicationException (sealed hierarchy)

infrastructure/
  adapter/web/                 VesselController / OlhoVivoController / CgespController, GlobalExceptionHandler
  adapter/web/api/             Versioned API interfaces (@GetMapping/@PostMapping with version = "1.0")
  input/external/
    ais/ olhovivo/ cgesp/      Feign clients, wire DTOs (e.g. AisVesselResponse), error decoders, adapters with @CircuitBreaker
  output/grpc/                 EventPublisher, EventRequestMapper, EventSender / GrpcEventSender,
                               Grpc*StreamingAdapter, PublishSummary
  scheduler/                   *PollingScheduler (implement PollingJob), PollingJobRunner, PollingOutcome
  observability/               CorrelationId (ScopedValue + MDC)
  config/                      EventHubGrpcConfig, WebConfig, properties/*
```

### Data Flow Example (AIS)

`AisPollingScheduler` → `PollingJobRunner` (correlation id, timing, error isolation) → `AisService` → `AisPort` → `AisAdapter` → `AisFeignClient` → `AisVesselResponse.toDomain()` → back to `AisService` → `VesselStreamingPort` → `GrpcVesselStreamingAdapter` → `EventRequestMapper` → `EventPublisher` → `GrpcEventSender` → Event Hub `EventHub.SendEvent`

## Conventions

- **Domain models:** Java sealed interfaces + records (records can't extend classes). No framework imports in `domain`.
- **Ports:** Plain Java interfaces in `domain` package.
- **Adapters:** `@Component` / Feign implementations in `infrastructure`.
- **Wire DTOs:** External JSON names (snake_case) appear only in `*Response` records with `@JsonProperty`, translated by `toDomain()`. `com.fasterxml.jackson.annotation` works with Jackson 2 and 3.
- **Event-hub mapping:** Only `EventRequestMapper` builds `EventRequest` protobuf messages. Streaming adapters handle transport only.
- **Circuit breaker:** `@CircuitBreaker(name = "ais-api" | "olhovivo-api" | "cgesp-api", fallbackMethod = ...)` on each adapter method. The AIS fallback logs a warning and returns an empty list. Config in `application.yaml` (`resilience4j.circuitbreaker`).
- **Polling:** Each scheduler implements `PollingJob` and its `@Scheduled` method delegates to `PollingJobRunner`. The runner binds a fresh correlation id per cycle, records a timer (`lvc.polling.duration`), and never lets an exception escape to the scheduler thread.
- **Polling interval:** `ais.polling.interval`, `cgesp.polling.interval`, `olhovivo.polling.interval` (ms) in `application.yaml`.
- **Correlation id:** `CorrelationId` keeps the id in a `ScopedValue` (JEP 506) and mirrors it into MDC (`correlationId`), printed via `logging.pattern.correlation`. Code that fans out (`EventPublisher`) re-binds it inside each task.
- **Lombok:** Requires explicit `annotationProcessorPaths` in `maven-compiler-plugin` (already configured in `pom.xml`).
- **Virtual threads:** Enabled via `spring.threads.virtual.enabled: true`.
- **Java 25 features in use:** sealed types, record patterns in switch, `ScopedValue`, unnamed variables (JEP 456), flexible constructor bodies (JEP 513), `Gatherers.windowFixed` (JEP 485). Do not add `--enable-preview`.

## Streaming Channels

Data is published to Event Hub via gRPC for downstream consumption.

| Data | Port | Adapter | Destination |
|------|------|---------|-------------|
| AIS vessel positions | `VesselStreamingPort` | `GrpcVesselStreamingAdapter` | Event Hub (gRPC) |
| SPTrans bus positions | `BusStreamingPort` | `GrpcBusStreamingAdapter` | Event Hub (gRPC) |
| CGESP flooding points | `FloodingStreamingPort` | `GrpcFloodingStreamingAdapter` | Event Hub (gRPC) |

### gRPC client and publishing

- **Channel:** Boot-managed named channel `event-hub` (`spring.grpc.client.channel.event-hub.*`): static target, keepalive, and service-config retry (`UNAVAILABLE`, exponential backoff, 4 attempts). `EventHubGrpcConfig` enables retry explicitly and sets the shutdown grace period.
- **`EventPublisher`:** Publishes each batch on virtual threads with a semaphore shared by all streams. Batches are cut into windows of `batch-size` (`Gatherers.windowFixed`). An event counts as delivered only on an `accepted` ack. One summary line per batch (INFO / WARN partial / ERROR nothing delivered) with the first failure reason. **Fail-fast:** if a whole window fails with `UNAVAILABLE`/`DEADLINE_EXCEEDED`, the remaining windows are skipped and counted as failed. Counter `lvc.events.published{stream,result}`.
- **Outcomes:** streaming ports return a domain `PublishResult`; source ports return a sealed `FetchResult` (`Fetched | Unavailable`). `PollingJobRunner` records `lvc.polling.duration{job,outcome=success|partial|failed}`, so a hub or upstream outage is visible in metrics, not only in logs. Manual `POST .../poll` endpoints go through the same runner.
- **Per-item isolation:** invalid upstream records (missing id/lat/lon) are dropped and counted (`lvc.upstream.invalid_records{api}`); mapping failures count as failed events instead of aborting the batch. Circuit breakers wrap only the HTTP call (`AisUpstream`); fallbacks are counted in `lvc.upstream.fallback{api,reason}`.
- **Correlation id:** `CorrelationIdFilter` binds `X-Correlation-Id` (validated, or generated) as a ScopedValue for each request and echoes it in the response; scheduled cycles get one per cycle.
- **REST contract:** `GET /api/vessels/bounding-box` (v1.0) keeps its original snake_case JSON through `VesselV1Response`, decoupled from the domain record.
- **Per-call deadline:** `GrpcEventSender` sets `event-hub.grpc.deadline` on each `SendEvent` call.

## External APIs

### AIS (AISFriends)
- Feign client, base URL `spring.cloud.openfeign.client.config.ais-api.url` (default `https://www.aisfriends.com`); no auth
- Bounding-box query; response parsed by `AisVesselResponse` (ignores unknown fields)
- Polling every `ais.polling.interval` (default 30000 ms)

### OlhoVivo (SPTrans)
- Base URL `spring.cloud.openfeign.client.config.olhovivo-api.url` (default `https://api.olhovivo.sptrans.com.br/v2.1`)
- Cookie-based session auth via `OlhoVivoAuthInterceptor`; the token is injected from Vault (`secret/live-ms-java`)
- Lazy auth: POST `/Login/Autenticar?token={token}` on first request, caches `JSESSIONID`; re-authenticates on 401
- Line filters per bus line in `olhovivo.polling.line-terms`
- Polling every `olhovivo.polling.interval` (default 60000 ms)

### CGESP (Flooding)
- Base URL `spring.cloud.openfeign.client.config.cgesp-api.url` (default `https://www.cgesp.org`); no auth
- HTML scraping via Jsoup: `GET /v3/alagamentos.jsp?dataBusca=DD%2FMM%2FYYYY`
- Scrapes: zone (`h1.tit-bairros`), neighborhood (`td.bairro`), flooding points (`div.ponto-de-alagamento`)
- End time inference: missing → current SP time (if today) or `23:59` (if historical)
- Circuit breaker `cgesp-api` opens faster and stays open longer (HTML scraper)
- Polling every `cgesp.polling.interval` (default 60000 ms)

## Configuration

| Area | Keys (`application.yaml`) |
|------|--------------------------|
| Source URLs | `spring.cloud.openfeign.client.config.{ais-api,cgesp-api,olhovivo-api}.url` (override with `-D`/`--` properties; the OlhoVivo login uses `olhovivo.base-url`) |
| Polling | `ais.polling.interval`, `cgesp.polling.interval`, `olhovivo.polling.interval` |
| Event Hub publishing | `event-hub.grpc.area-id`, `deadline` (2s), `max-concurrency` (32), `batch-size` (500), `shutdown-grace-period` (5s) |
| Event Hub transport | `spring.grpc.client.channel.event-hub.*` (target built from `event-hub.grpc.host/port`) |
| Resilience | `resilience4j.circuitbreaker.configs.default` / `instances.{ais-api,cgesp-api,olhovivo-api}` |
| Secrets | `spring.cloud.vault.*` with `optional:vault://` import; missing secrets cause NPEs in production |
| Actuator | `management.endpoints.web.exposure.include: health`, probes enabled |

Env vars used by `docker-compose.yml`: `SPRING_CLOUD_VAULT_HOST`, `EVENT_HUB_GRPC_HOST`.

## Docker and CI

- **Build context:** the parent directory that holds the sibling repos (`live-ms-java/` and `proto-shared/`): `docker build -f live-ms-java/Dockerfile .` (`docker-compose.yml` uses `context: ..`).
- **Build stage:** installs `proto-shared` into the Maven cache, resolves the POM layer first, then builds the jar and splits it into Spring Boot layers.
- **AOT cache:** the runtime image trains a JEP 483/514/515 AOT cache at build time (`/app/app.aot`) by booting the context with `spring.context.exit=onRefresh` and external I/O off (Vault disabled, lazy gRPC channel, polling pushed out, port 0). The JVM flags used for training must match the `ENTRYPOINT`. There is no AppCDS, no entrypoint script and no `/data` volume.
- **Runtime:** `liberica` JRE 25 image, runs as non-root UID 10001. `JAVA_TOOL_OPTIONS` sets compact object headers, G1, `MaxRAMPercentage=75`, `ExitOnOutOfMemoryError` and `--enable-native-access`.
- **HEALTHCHECK:** `wget` against `http://127.0.0.1:8080/actuator/health/readiness`.
- **`.github/workflows/security.yml`:**
  - `build`: `mvn verify`, with `proto-shared` checked out next to the repo.
  - `trivy fs`: blocking gate for vulnerabilities, secrets and misconfigurations at HIGH/CRITICAL with a fix (`ignore-unfixed`); SARIF upload.
  - `trivy image`: builds the image without push and runs the same gate.
  - Optional secret `PROTO_SHARED_TOKEN` when `proto-shared` is private (falls back to `GITHUB_TOKEN`). Exceptions go in `live-ms-java/.trivyignore`, if it exists.

## Common Tasks

### Add a New External API
Follow the steps in `domain/template/NewApiTemplate.java` (domain model → port → Feign client → wire DTO → Feign configuration → error decoder → adapter with `@CircuitBreaker` → application service → event-hub mapping → scheduler implementing `PollingJob` → REST controller → `application.yaml`). `AisVesselResponse` is the reference for the wire DTO.

### Update Polling Interval
Edit `*.polling.interval` in `application.yaml` (milliseconds).

### Adjust gRPC Settings
Edit `event-hub.grpc.*` (publishing) or `spring.grpc.client.channel.event-hub.*` (transport) in `application.yaml`.

## Key Files

| File | Purpose |
|------|---------|
| `pom.xml` | Maven config, Spring Boot 4.1.2 parent, Spring Cloud BOM, Lombok annotation processor |
| `Dockerfile` | Multi-stage image with AOT cache, non-root runtime |
| `docker-compose.yml` | App orchestration (build context is the parent directory) |
| `.github/workflows/security.yml` | Build plus Trivy gates (fs and image) |
| `src/main/resources/application.yaml` | Polling intervals, API URLs, circuit breakers, gRPC, actuator |
| `src/main/resources/static/openapi.yaml` | OpenAPI spec served by Swagger UI |
| `domain/template/NewApiTemplate.java` | Template for adding new APIs |
