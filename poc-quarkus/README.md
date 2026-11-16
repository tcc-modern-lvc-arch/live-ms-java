# live-ms-quarkus-poc

PoC Quarkus 3.40.1.2 / Java 25 da fatia AIS do `live-ms-java`, para benchmark contra o Spring Boot
(startup, RSS, throughput gRPC). Projeto Maven independente, **nao** e modulo do pom raiz.

## Build e execucao

```bash
export JAVA_HOME=<jdk25>; export PATH=$JAVA_HOME/bin:$PATH
mvn -B -ntp verify                                   # build JVM + testes
java -Dais.polling.interval=1h -jar target/quarkus-app/quarkus-run.jar
curl localhost:8080/api/v1/vessels/health            # {"status":"UP"}
curl -X POST localhost:8080/api/v1/vessels/poll      # um ciclo sincrono -> {"vessels":N}
curl localhost:8080/q/health
```

Config (`application.properties`): `ais.polling.interval` (duracao, ex. `30s`), `quarkus.rest-client.ais-api.url`,
`quarkus.grpc.clients.event-hub.host/port`, `event-hub.grpc.area-id`.

Native (nao executado; viavel, ver abaixo):
`mvn -B -ntp verify -Dnative -Dquarkus.native.container-build=true -Dquarkus.native.builder-image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`

## Mapeamento Spring -> Quarkus

| Componente | Spring | Quarkus |
|---|---|---|
| HTTP client | `AisFeignClient` (OpenFeign) + interceptor de headers | `AisClient` `@RegisterRestClient(configKey="ais-api")` + `@ClientHeaderParam` (`quarkus-rest-client-jackson`) |
| Timeouts | openfeign connect 5s / read 10s | `quarkus.rest-client.ais-api.connect-timeout/read-timeout` |
| Circuit breaker | Resilience4j `ais-api` (window 10, 50%, wait 30s, half-open 3) | `@CircuitBreaker(requestVolumeThreshold=10, failureRatio=0.5, delay=30s, successThreshold=3)` + `@Fallback` (lista vazia) |
| Scheduler | `@Scheduled(fixedDelayString)` | `@Scheduled(every="${ais.polling.interval}", concurrentExecution=SKIP)` + `@RunOnVirtualThread` |
| gRPC client | `ManagedChannel` + `EventHubBlockingStub` beans | `@GrpcClient("event-hub") EventHubBlockingStub` (stubs pre-gerados do `proto-shared`) |
| Virtual threads | `spring.threads.virtual.enabled` | `@RunOnVirtualThread` no scheduler e no `POST /poll` |
| Dominio/portas | `domain.vessel`, `VesselStreamingPort` | copiados em `io.github.raphonzius.lvc.live.quarkus.{domain,application,infrastructure}` |
| Mapeamento EventRequest | `GrpcVesselStreamingAdapter` | identico (mesmos campos/fallbacks) |
| Health | Actuator | `quarkus-smallrye-health` (`/q/health`) + `GET /api/v1/vessels/health` |
| Config | `application.yaml` | `application.properties` |

## Notas / limitacoes

- `quarkus.grpc.codegen.skip=true`: o jar `proto-shared` traz `.proto` + stubs compilados; sem isso o codegen tentaria recompilar. O stub injetado e o blocking stub padrao (nao Mutiny).
- Protobuf: o BOM Quarkus traz protobuf-java 4.35.0, mas os stubs foram gerados com 4.36.2 (checagem `RuntimeVersion` falha). O pom fixa `protobuf-java`/`protobuf-java-util` em 4.36.2. gRPC runtime segue o BOM (1.81.0); stubs 1.85.1 funcionam.
- SmallRye FT nao tem `minimumNumberOfCalls` (usa a janela `requestVolumeThreshold`) nem circuit breaker por slow call; `slowCallDurationThreshold` nao foi reproduzido. `ErrorDecoder` customizado do Feign nao foi portado (excecoes padrao do REST client contam como falha).
- `every` do Quarkus exige duracao (`30s`), nao milissegundos como no Spring.
- Teste: o stub gRPC e `@Singleton` sintetico e classe final, entao `@InjectMock` nao se aplica; o adapter e `@Singleton` e o teste troca o campo `eventHubStub` por um mock Mockito. O REST client usa `@InjectMock @RestClient`.
- `POST /poll` chama o AIS real (aisfriends) se a rede permitir; falhas de gRPC sao logadas e engolidas (igual ao Spring).
