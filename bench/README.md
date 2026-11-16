# AIS slice benchmark: Spring Boot 4.1.2 vs Quarkus 3.40 (PoC)

Reproducible harness comparing the AIS polling slice (`GET AIS -> map -> N x EventHub.SendEvent`) of
`live-ms-java` (Spring Boot 4.1.2, full app) against `poc-quarkus` (Quarkus 3.40.1.2, AIS-only PoC).
Numbers are meant for an ADR: read "Limitations / biases" before quoting any of them.

## Environment of the published run (2026-11-14)

| item | value |
|---|---|
| Host | Firecracker-style sandbox VM, Linux 6.18.44, 4 vCPU `Intel Xeon @ 2.10GHz`, 16 GB RAM, no swap |
| CPU split | apps pinned to CPUs 0,1 (`taskset -c 0,1`); fakes + orchestrator pinned to CPUs 2,3 |
| JDK | Temurin 25.0.5+8 (all JVM variants); native: Mandrel 25.0.5.0-Final (container `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`) |
| JVM flags (all JVM variants) | `-XX:+UseG1GC -XX:+UseCompactObjectHeaders -Xmx512m` (native: `-Xmx512m`, Serial GC, the only GC of a default Mandrel build) |
| Spring | Spring Boot 4.1.2, Spring Cloud OpenFeign 5.0.1, virtual threads on, `event-hub.grpc.max-concurrency=32` (default) |
| Quarkus | 3.40.1.2, quarkus-rest + rest-client-jackson + smallrye-fault-tolerance + scheduler + grpc; **publishes sequentially** (blocking stub, `forEach`) |
| gRPC / protobuf | proto-shared 1.0.0 stubs, grpc 1.85.1 (Spring, fake hub), protobuf-java 4.36.2 |
| Workload | 2000 vessels per AIS response (~1.1 MB JSON), 20 polls per run, 3 warm-up polls discarded |

## Variants

| name | what |
|---|---|
| `spring-jvm` | `java -jar live-ms-java.jar` |
| `spring-jvm-c1` | same, `-Devent-hub.grpc.max-concurrency=1` (fair-to-Quarkus publishing) |
| `spring-aot` / `spring-aot-c1` | `-Djarmode=tools extract`, trained with `-XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh`, run with `-XX:AOTCache=app.aot -XX:AOTMode=on` |
| `quarkus-jvm` | `java -jar quarkus-app/quarkus-run.jar` (fast-jar) |
| `quarkus-aot` | Quarkus' native AOT support: `-Dquarkus.package.jar.aot.enabled=true -Dquarkus.package.jar.aot.phase=BUILD` plus `quarkus.package.jar.aot.additional-recording-args` with the measured JVM flags (without it the cache is built without CompactObjectHeaders and the JVM refuses it) |
| `quarkus-native` | `-Dnative -Dquarkus.native.container-build=true` (built OK in ~2.5 min) |

## Results (median [min-max])

Startup: 7 repetitions per variant, executed round-robin (rep 1 of every variant, then rep 2, ...) to spread drift.
Load: 5 independent runs (fresh process each) per variant, also round-robin; each run = start, 5 s idle, 3 warm-up polls, 20 timed polls.
Raw data: `results/*.csv`.

### Startup, memory, size

"ready ext." = wall time from `exec` to the first HTTP 200 on the health endpoint (Spring `/actuator/health/readiness`,
Quarkus `/q/health/ready`), polled every 10 ms from outside. "framework" = what the framework prints
(`Started ... in X seconds` / `started in Xs`). RSS = `VmRSS` 5 s after ready. Artifact size is the on-disk deployable
(for `*-aot` it includes the AOT cache: Spring cache 77 MB, Quarkus cache 43 MB).

| variant | ready ext. ms | framework ms | RSS idle MB | CPU s until idle | artifact MB |
|---|---|---|---|---|---|
| spring-jvm | 6444 [6077-7106] | 5237 [4911-5749] | 311 [292-326] | 16.0 [15.2-17.8] | 65 (fat jar) |
| spring-jvm-c1 | 6468 [5785-6777] | 5285 [4662-5549] | 305 [297-325] | 17.6 [16.8-18.6] | 65 |
| spring-aot | 3834 [3328-4057] | 2826 [2465-3243] | 339 [331-342] | 14.3 [13.2-14.8] | 139 (extracted + cache) |
| spring-aot-c1 | 3766 [3396-4252] | 2873 [2531-3186] | 324 [318-334] | 15.2 [14.7-16.4] | 139 |
| quarkus-jvm | 1215 [1015-1373] | 1029 [854-1173] | 172 [166-181] | 6.5 [5.8-6.9] | 32 (fast-jar dir) |
| quarkus-aot | 828 [765-928] | 591 [502-666] | 193 [190-196] | 6.8 [5.7-7.3] | 73 (fast-jar + cache) |
| quarkus-native | 33 [33-44] | 23 [21-33] | 99 [98-100] | 0.3 [0.3-0.4] | 70 (binary) |

### Throughput, poll latency, RSS under load

Throughput = events received by the fake EventHub / wall time of the 20 sequential `POST .../poll` calls
(40 000 events; the hub count matched in every run, 0 lost). Both endpoints are **synchronous**: the HTTP call returns after
the AIS fetch and all `SendEvent` calls finished (Spring answers 202 `Polling initiated`, Quarkus 200 `{"vessels":2000}`),
so poll latency = response time of the POST. p50/p99 are pooled over the 5 runs (100 samples, so "p99" is essentially the
2nd-worst sample).

| variant | events/s | poll p50 ms (per-run medians) | p50 / p99 ms (pooled, n=100) | RSS after load MB | peak RSS MB |
|---|---|---|---|---|---|
| spring-jvm (32 in flight) | 9247 [7990-10096] | 211 [192-224] | 205 / 401 | 325 [311-336] | 325 |
| spring-jvm-c1 (1 in flight) | 4644 [3598-4884] | 418 [388-519] | 429 / 972 | 315 [311-321] | 315 |
| spring-aot (32) | 9329 [6891-10948] | 191 [181-271] | 202 / 454 | 345 [338-347] | 345 |
| spring-aot-c1 (1) | 4532 [3839-4822] | 411 [384-499] | 429 / 860 | 342 [338-347] | 342 |
| quarkus-jvm (sequential) | 5700 [5060-6254] | 340 [307-389] | 349 / 503 | 256 [253-263] | 256 |
| quarkus-aot (sequential) | 5372 [5287-6275] | 352 [307-369] | 345 / 639 | 214 [211-217] | 214 |
| quarkus-native (sequential, Serial GC) | 4664 [4356-5710] | 432 [347-459] | 412 / 508 | 102 [100-105] | 118 |

### Reading the numbers (what is and is not supported)

* Startup/RSS: Quarkus JVM starts ~5x faster and idles at ~55% of Spring's RSS; AOT cache helps Spring by ~40% (6.4 s -> 3.8 s)
  and Quarkus by ~30% (1.2 s -> 0.83 s) at the cost of +30 MB RSS (Spring) / +20 MB (Quarkus) and a large cache file. Native is ~30 ms / ~100 MB.
  These are real, large effects, but partly a consequence of Spring being the complete app (see biases).
* Throughput: the headline Spring-vs-Quarkus gap is dominated by publishing concurrency (32 vs 1), not by the framework.
  At equal concurrency (Spring `c1` vs Quarkus), Quarkus JVM is ~20-25% faster (5.7k vs 4.6k events/s) and has ~20% lower
  poll latency; the AOT/native variants do not improve steady-state throughput (native is slower: Serial GC, no JIT).
  Run-to-run spread is 10-30%, so differences below ~20% between single variants are not significant.
* JIT/AOT-cache variants vs plain JVM in the load tables are within noise of each other.

## Reproduce

```bash
export JAVA_HOME=/path/to/jdk-25                  # Temurin 25
export MVN_ARGS=-o                                # only if ~/.m2 is pre-warmed/offline (sandbox); omit otherwise
bench/prepare.sh --native                         # builds jars, AOT caches, fake-eventhub, (optional) native binary -> bench/work/
bench/gen-tls.sh                                  # self-signed cert for the fake AIS + hosts file (needs keytool)
taskset -c 2,3 python3 bench/run.py               # all variants whose artifact exists; apps pinned to CPUs 0,1
python3 bench/summarize.py                        # markdown tables from bench/results/*.csv
```

`run.py` options: `--startup-reps 7 --load-runs 5 --polls 20 --warmup 3 --variants spring-jvm,quarkus-jvm --only startup|load|both`.
Env: `APP_CPUS` (default `0,1`), `FAKE_CPUS` (default `2,3`), `WORK_DIR`. The fake AIS binds **port 443** (needs root, or
`sysctl net.ipv4.ip_unprivileged_port_start=443`). Nothing else may be listening on 8080/443/8081/50051. Native build needs
Docker and the Mandrel builder image. `prepare.sh` omits `--native` unless asked; `run.py` skips variants with no artifact.

Files: `FakeAis.java` (JEP 330/512 single-file; fixed 2000-vessel JSON, HTTPS), `fake-eventhub/` (Maven, gRPC `EventHub.SendEvent`
-> `accepted`, counts events; `GET :8081/stats`, `POST :8081/reset`), `run.py` (orchestration + CSVs), `prepare.sh`, `gen-tls.sh`,
`summarize.py`, `results/` (CSVs + per-run app logs in `results/logs/`).

### How the apps are pointed at the fakes (no production code changed)

* **Quarkus**: `-Dquarkus.rest-client.ais-api.url=https://127.0.0.1`, `-Dais.polling.interval=24h`; gRPC defaults already target `localhost:50051` plaintext.
* **Spring**: the AIS URL is a literal in `@FeignClient(url = "https://www.aisfriends.com")`. Annotation URL wins over
  `spring.cloud.openfeign.client.config.ais-api.url` (verified: the property is ignored and the real host was contacted), so there is no
  config knob. Instead the fake AIS speaks HTTPS on :443 with a self-signed cert, and the Spring JVM gets
  `-Djdk.net.hosts.file=bench/work/hosts` (maps `www.aisfriends.com`, `www.cgesp.org`, `api.olhovivo.sptrans.com.br` to 127.0.0.1) and
  `-Djavax.net.ssl.trustStore=bench/work/ais-trust.p12`. Quarkus uses the same fake over the same TLS and truststore (by IP, since Vert.x
  does not honour `jdk.net.hosts.file`), so both pay TLS cost equally.
* Spring other settings: `-Dspring.cloud.vault.enabled=false`, `-D{ais,cgesp,olhovivo}.polling.interval=86400000`, lvc/openfeign log level INFO.
  Quarkus: lvc log level INFO. (Default is DEBUG in both, with per-event log lines; INFO keeps logging I/O out of the comparison.)

## Limitations / biases (please read)

1. **Not like-for-like apps.** Spring is the full application (AIS + CGESP + OlhoVivo, actuator, Vault starter, Resilience4j
   circuit breakers + Micrometer, springdoc/Swagger UI, Spring gRPC, Feign), the PoC is AIS only with SmallRye FT. A large part of the
   startup/RSS/CPU gap is simply "more beans and more classes". Do not read it as a pure framework gap. Removing unused starters from
   Spring would narrow it; we did not do that (out of scope, would change the app).
2. **Different work per event.** Spring maps JSON to a 10-field `AisVesselResponse` then to the domain record; Quarkus deserializes the full
   26-field record directly. Spring Boot 4 defaults to Jackson 3, Quarkus ships Jackson 2.21; different HTTP clients (Feign (OpenFeign client) vs Vert.x) and gRPC
   channel stacks (Spring gRPC vs Quarkus gRPC client, grpc-java 1.85.1 vs 1.81.0 as shipped by the Quarkus BOM).
3. **Publishing concurrency differs by design**: Spring 32 in-flight RPCs (virtual threads + semaphore, batch windows of 500), Quarkus
   sequential. `spring-*-c1` is the fair framework comparison. Quarkus has no concurrency knob in the PoC; a concurrent Quarkus publisher would
   almost certainly close the headline throughput gap, which we did not measure.
4. **Fake downstreams are unrealistic.** Fake hub acks immediately (no persistence, no network latency), AIS payload is local and constant.
   Real throughput will be bounded by the real hub and network. The fake hub's own ceiling was not calibrated; it shares CPUs 2,3 with the fake AIS,
   the orchestrator and kernel loopback, so at the highest rates (Spring c32 ~9-10k events/s) a downstream bottleneck is possible.
5. **Shared sandbox VM**: 4 vCPUs of unknown host sharing, noisy neighbours, no CPU frequency control. Spread within a variant is 10-30% for
   throughput and ~10% for startup. Only 5-7 repetitions. p99 over 100 samples is not a stable estimate. No statistical test was done.
6. **CPU pinning is not isolation**: only 2 CPUs for the app means JIT/GC threads compete with the app (this inflates Spring startup, which burns ~16 CPU-s
   to boot). With more cores, Spring's startup would be shorter than reported here. Network goes over loopback.
7. **Startup includes one AIS poll.** Both frameworks fire the scheduler immediately at boot (Spring `fixedDelay` with no initial delay, Quarkus
   likewise; both received 2000 events before the 5 s idle sample). So "idle RSS" and "CPU s until idle" include one poll cycle and
   Spring's CGESP/OlhoVivo startup runs (they fail fast on a TLS-handshake error to the loopback fake; the failure path logs a stack trace).
   Readiness is probed while that first poll may be in flight.
8. **AOT cache training is minimal**: Spring trained with `-Dspring.context.exit=onRefresh` (context start only), Quarkus with its own build-time
   training run (start and stop); neither trained the poll path, so request-path classes are not in the cache. Cache validity requires identical JDK, flags
   and classpath/mtimes (the caches were rebuilt with the measured flags; `-XX:AOTMode=on` makes a stale cache fail instead of silently falling back).
   Spring artifact size for AOT counts the extracted layout (about twice the fat jar) plus the 77 MB cache.
9. **Native** is built with Mandrel defaults: Serial GC (no G1, no `-XX:+UseCompactObjectHeaders`), no PGO, no `-march=native`; steady-state numbers
   reflect that. Build took ~2.5 min (peak 3.8 GB RSS) and is not part of the table. The Spring app has no native variant here.
10. **Memory** is RSS of the process only (no container accounting, no NMT); `-Xmx512m` caps the heap but Spring/Quarkus differ in metaspace/code cache/threads.
    JVM variants use G1 which may expand RSS beyond need; native uses Serial.
11. **Measurement overhead**: Python orchestrator polls with a fresh TCP connection every 10 ms during startup (negligible vs 30 ms-6 s, but it quantizes the
    native number); load requests reuse one keep-alive connection. Sandbox `JAVA_TOOL_OPTIONS` (proxy/truststore) is removed for the measured JVMs.
12. **Hard-coded Spring URL** forced the hosts-file/TLS indirection. It adds a TLS handshake per new connection to both stacks equally, but the HTTP clients
    pool/keep-alive differently and the AIS payload (~1.1 MB) is large relative to the 2000 tiny gRPC messages.

> **Nota (pós-benchmark):** as URLs base dos clientes Feign passaram a ser configuráveis
> (`ais.api.url`, `cgesp.api.url`, `olhovivo.api.url`). Novas execuções podem apontar o Spring
> direto para o fake AIS (`-Dais.api.url=https://127.0.0.1`) sem o truque de `jdk.net.hosts.file`;
> os números acima foram medidos antes dessa mudança, que não afeta o caminho medido.
