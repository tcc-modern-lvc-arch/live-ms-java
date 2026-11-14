# ADR-0001: Spring Boot 4.1 vs Quarkus 3.40 para os microserviços Java do LVC

**Status:** Proposto
**Data:** 2026-11-14
**Decisores:** mantenedor do projeto (TCC Modern LVC Architecture)
**Escopo:** `live-ms-java` e `virtual-ms-java` (o `event-hub` é Rust e o `constructive` é Python: fora do escopo)

## Contexto

Os dois serviços Java foram modernizados para Java 25 (apenas JEPs finais), Spring Boot 4.1.2,
Spring Cloud 2025.1.4 e gRPC 1.85.1, com zero vulnerabilidades HIGH/CRITICAL no Trivy (fs, config e imagem).
A pergunta era se o Quarkus otimizaria esses microsserviços o bastante para justificar uma migração.

Perfil dos serviços:

| | live-ms-java | virtual-ms-java |
|---|---|---|
| Papel | Ingestão: 3 pollers HTTP (AIS, OlhoVivo, CGESP) → `EventHub.SendEvent` (gRPC) | Digital twin: geofencing, CQRS, UI Vaadin, servidor gRPC `AreaService`, assinante do event-hub |
| Stack | Web MVC, OpenFeign, Resilience4j, Spring Cloud Vault, Jsoup, cliente gRPC | Web MVC, JPA/Hibernate, Flyway, MariaDB, InfluxDB, **Vaadin 25**, Apache POI, JTS, servidor e cliente gRPC |
| Estado | Stateless | Stateful (MariaDB) |
| Tamanho | ~3,6 kLOC main, 91 testes | ~2,8 kLOC main, 33 testes |
| Execução | Processo de longa duração (`docker compose`) | Processo de longa duração (`docker compose`) |

Para medir em vez de opinar, foi construída uma PoC Quarkus da fatia AIS do live-ms
(`poc-quarkus/`) e um benchmark reproduzível (`bench/`): AIS falso (2000 navios), EventHub gRPC falso,
2 vCPUs por app, G1 + Compact Object Headers, `-Xmx512m`, 7 repetições de startup e
5×20 ciclos de carga por variante.

## Resultados do benchmark (mediana)

| Variante | Pronto (ms) | RSS idle (MB) | Eventos/s | Poll p50/p99 (ms) | Artefato (MB) |
|---|---|---|---|---|---|
| Spring JVM (32 RPCs em voo) | 6444 | 311 | 9247 | 205 / 401 | 65 |
| Spring JVM (1 em voo) | 6468 | 305 | 4644 | 429 / 972 | 65 |
| **Spring + AOT cache** (32) | **3834** | 339 | 9329 | 202 / 454 | 139 |
| Quarkus JVM (sequencial) | 1215 | 172 | 5700 | 349 / 503 | 32 |
| Quarkus + AOT cache | 828 | 193 | 5372 | 345 / 639 | 73 |
| Quarkus native (Mandrel) | 33 | 99 | 4664 | 412 / 508 | 70 |

O que os números sustentam (detalhes e vieses em `bench/README.md`):

- **Startup e memória:** o Quarkus é cerca de 5× mais rápido para ficar pronto e usa cerca de 55% do RSS.
  O AOT cache do JDK 25 (JEPs 483/514/515) reduz o startup do Spring em cerca de 40%. Ele já está nas imagens Docker,
  onde o startup cai de 4,3 s para 1,8 s (live) e de 5,5 s para 2,3 s (virtual).
- **Throughput:** com a mesma concorrência de publicação, o Quarkus é 20–25% mais rápido.
  A concorrência limitada com virtual threads do Spring (32 em voo) supera em cerca de 60% o publisher
  sequencial da PoC. **O desenho do publisher pesa mais que o framework.**
- **Native:** o startup é praticamente instantâneo e o RSS é o menor, mas o throughput em regime é o menor (Serial GC, sem JIT).
- **Vieses:** o Spring medido é a app completa, com 3 integrações, actuator, Vault, springdoc e Resilience4j;
  a PoC só tem AIS. Parte da diferença de startup e RSS vem apenas de haver mais beans. O sandbox era
  compartilhado e não houve teste estatístico, então diferenças menores que cerca de 20% não são significativas.

## Decisão

**Manter Spring Boot 4.1 nos dois serviços agora.** A migração do `live-ms-java` para Quarkus fica como
**opção preparada**, a ser disparada por gatilhos objetivos (ver *Revisitar quando*). O `virtual-ms-java`
permanece em Spring Boot sem previsão de migração.

## Opções consideradas

### Opção A: Spring Boot 4.1 nos dois (escolhida)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Baixa: já implementado, testado e escaneado |
| Custo | Nenhum custo de migração |
| Startup/memória | 1,8–2,3 s com AOT cache; 300–340 MB RSS por serviço |
| Familiaridade | Uma stack só para os dois serviços Java |

**Prós:** um único ecossistema (BOMs, starters gRPC do Boot, observabilidade, testes). Integração de primeira classe
com Vaadin, Spring Data JPA, Flyway e Spring Cloud Vault. O AOT cache e as virtual threads já capturam boa parte do
ganho de startup e de concorrência.
**Contras:** consome cerca de 140 MB de RSS a mais por instância que o Quarkus JVM. O startup continua na casa dos segundos.

### Opção B: live-ms em Quarkus, virtual-ms em Spring

| Dimensão | Avaliação |
|---|---|
| Complexidade | Média: reescrever Feign → REST Client, Resilience4j → SmallRye FT, Vault, schedulers, 91 testes |
| Custo | Estimado em 3–5 dias com o mesmo nível de testes e hardening; a PoC cobre só ~1/3 (AIS) |
| Startup/memória | 0,8–1,2 s e cerca de 170–190 MB (JVM); 33 ms e 99 MB (native) |
| Familiaridade | Duas stacks Java para manter |

**Prós:** o menor footprint e o menor startup. O native image fica viável, e a PoC já compila em native.
**Contras:** duas stacks Java, com dois conjuntos de BOMs, CVEs e padrões de teste. A paridade não é 1:1: o SmallRye FT não tem
`minimumNumberOfCalls` nem slow-call, e o Feign `ErrorDecoder` não foi portado. A PoC exigiu fixar protobuf 4.36.2 contra o
BOM do Quarkus, e o stub gRPC sintético dificulta mocks.

### Opção C: os dois em Quarkus

Descartada. O `virtual-ms-java` depende de Vaadin (cujo suporte a Quarkus existe, mas é secundário em relação ao Spring),
JPA, Flyway, POI e JTS, e tem estado. O ganho de startup não importa para um serviço stateful de longa duração com UI,
e o risco e o custo da migração são os maiores.

## Análise de trade-offs

1. **O que o ambiente valoriza.** Os serviços rodam como processos de longa duração em `docker compose`, não em
   scale-to-zero nem em serverless. Nesse cenário, startup de 1,8 s contra 0,8 s é irrelevante após o boot, e a
   diferença de cerca de 140 MB de RSS é o único ganho contínuo.
2. **O throughput depende do publisher, não do framework.** O maior ganho medido veio do `EventPublisher` com concorrência
   limitada, que já foi implementado no Spring. Migrar sem esse desenho deixaria o serviço *mais lento*.
3. **Custo de duas stacks.** Manter a mesma disciplina (Trivy zero, BOMs, testes, CI) em dois ecossistemas Java
   dobra o trabalho de manutenção. Nada no ambiente atual compensa isso.
4. **Native só vale para scale-to-zero.** Os 33 ms e 99 MB vêm com o menor throughput em regime e com builds
   mais lentos e frágeis (reflexão de protobuf e Jackson).

## Consequências

- **Fica mais fácil:** uma única stack Java, com upgrades coordenados pelo BOM do Boot e o mesmo pipeline de segurança.
- **Fica mais difícil:** nada novo. O custo é deixar de colher cerca de 140 MB e cerca de 1 s de startup por instância do live-ms.
- **Preservado:** a PoC (`poc-quarkus/`) e o benchmark (`bench/`) continuam no repositório como base executável para
  uma migração futura, com os achados de paridade documentados no README da PoC.

## Revisitar quando

Migrar o `live-ms-java` para Quarkus (opção B), começando pelo native, se qualquer um destes ocorrer:
- deploy em plataforma com scale-to-zero ou cobrança por memória/tempo (Cloud Run, Knative, Lambda);
- várias réplicas do live-ms em hardware restrito (por exemplo, edge), em que cerca de 140 MB por instância passe a ser relevante;
- exigência de startup abaixo de 1 s (por exemplo, autoscaling reativo a picos de AIS).

## Decisões relacionadas a gRPC

- Continuar com `SendEvent` unário mais concorrência limitada no cliente. Com o publisher atual (~9–10 mil eventos/s em 2 vCPUs),
  o teto observado possivelmente é do hub falso (não calibrado), e não do cliente.
- **Proposta (fora do escopo deste ADR, exige mudança no `event-hub-ms-rust` e no `proto-shared`):**
  um RPC client-streaming `SendEvents(stream EventRequest) returns (EventBatchAck)` para os snapshots
  periódicos (2000+ entidades por ciclo). Ele reduziria overhead por chamada e cabeçalhos, mas deve ser medido com o
  `bench/` antes de ser adotado.

## Itens de ação

1. [ ] Aceitar ou ajustar este ADR (mantenedor).
2. [ ] Rodar o `bench/` no ambiente-alvo real, com mais CPUs, antes de qualquer revisão desta decisão.
3. [ ] Se um gatilho ocorrer: portar OlhoVivo e CGESP para a PoC, adicionar publisher concorrente e repetir o benchmark.
4. [ ] Avaliar o RPC `SendEvents` client-streaming com o `bench/` (ADR próprio, envolvendo o event-hub).
