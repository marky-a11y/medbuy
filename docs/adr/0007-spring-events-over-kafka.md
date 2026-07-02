# ADR-0007: Spring Application Events Replace Apache Kafka for Inter-Stage Messaging

## Status
Accepted (2026-06-30)

## Date
2026-06-30

## Context

The Media Buying Dashboard originally used Apache Kafka (via Amazon MSK) to decouple the 4-stage ETL pipeline: Source Ingestion → Sector Grouping → Company/Platform Grouping → KPI Building. This was documented in ADR-0004 and later extended in ADR-0006 to support 10 external data sources across 6 Kafka topics with 6 consumer groups.

During implementation, the following realities emerged:

1. **Single-JVM deployment** — The application is a modular monolith (Spring Boot single-JAR). All "stages" of the pipeline run within the same process. The use of Kafka as an inter-process message bus within a single process adds unnecessary serialization overhead and infrastructure complexity.

2. **MSK provisioning delay** — AWS MSK cluster provisioning takes 20-30 minutes, and SASL/SCRAM configuration adds friction to local development, CI/CD, and onboarding. Developers cannot run the full pipeline locally without Dockerized Kafka.

3. **No true multi-consumer replay** — Although Kafka topics enable replay, all 6 "consumer groups" are deployed as separate `@KafkaListener` beans within the same JAR. In practice, there is no independent scaling of stages — the JVM either is up (all stages process) or down (nothing processes). True per-stage scaling would require extracting each stage into its own microservice, which is Phase 4 work.

4. **Operational overhead** — Kafka consumer lag monitoring, dead-letter queue management, rebalancing alerts, MSK broker patching, and SASL credential rotation add operational complexity disproportionate to the benefit for a single-JVM app.

5. **The code already abstracted messaging** — An `EventBus` interface with `SpringEventBus` (dev) and `KafkaEventBus` (k8s) implementations already existed. Both `KPIStreamConsumer` and `CacheInvalidationService` were already consuming via `@EventListener`. The Kafka path was only exercised under the `k8s` profile. Removing Kafka means collapsing to the Spring Events path entirely.

## Decision

**Replace all Kafka-based inter-stage messaging with Spring's built-in `ApplicationEventPublisher` + `@EventListener` + `@Async`.**

The 4-stage pipeline will use Spring Events as the messaging backbone:

```
Stage 1 (Source Ingestion) → publishEvent(NormalizedSourceMessage)
Stage 2 (Sector Grouping)  → @EventListener → publishEvent(SourceSectorMappingMessage)
Stage 3 (Company/Platform) → @EventListener → publishEvent(CompanyPlatformMappingMessage)
Stage 4 (KPI Building)     → @EventListener → publishEvent(KpiRefreshEvent)
Cache Invalidation          → @EventListener(KpiRefreshEvent)
```

### Implementation Details

| Aspect | Implementation |
|---|---|
| **Event Publishing** | Direct `ApplicationEventPublisher.publishEvent(event)` calls. No wrapper class needed. |
| **Event Consumption** | `@Async` + `@EventListener` on each pipeline stage method. |
| **Thread Pool** | `ThreadPoolTaskExecutor` (core=4, max=10, queue=100) via `@EnableAsync` on a `SpringEventConfig`. |
| **Error Handling** | `AsyncUncaughtExceptionHandler` logs errors. No dead-letter queue — failed events are logged and the pipeline continues. |
| **Profile** | No profile gating — events work identically in dev, docker, and k8s. No separate `kafka-consumer` pod type. |
| **Topic Routing** | The existing `IntegrationEvent` wrapper with topic-based SpEL filtering (`#event.topic == 'kpi.raw'`) is retained for clean separation of concerns. |

### What is Removed

| Removal | Rationale |
|---|---|
| `KafkaConfig.java` (212 lines) | All consumer/producer factory beans, SASL config, DLQ handler, 6 consumer groups |
| `KafkaEventBus.java` | `@Profile("k8s")` implementation using `KafkaTemplate` |
| `spring-kafka` Maven dependency | No longer needed |
| `KAFKA_*` environment variables | `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SASL_USERNAME`, `KAFKA_SASL_PASSWORD` |
| Terraform `modules/msk/` | MSK cluster, topics, ACLs |
| `kafka-consumer` EKS node group | No separate consumer deployment |
| Kafka Prometheus rules (4 alerts) | Consumer lag, rebalancing alerts |
| Kafka Grafana dashboard row | Consumer lag panel |
| `kafka-consumer` Spring profile | All `@EventListener` beans are always active |

## Alternatives Considered

### Alternative 1: Keep Kafka
- **Pros**: Decoupled stages, replay capability, message durability across restarts.
- **Cons**: Infrastructure complexity, MSK cost, local development friction, no true per-stage scaling yet (all in one JVM). The benefits of Kafka only materialize when stages are deployed as independent services, which is a Phase 4 goal.

### Alternative 2: Database Queue (PostgreSQL `LISTEN/NOTIFY` or a `pipeline_queue` table)
- **Pros**: No additional infrastructure (already have PostgreSQL). Messages are durable.
- **Cons**: Polling overhead, no partitioning, table bloat from queued messages, slower than in-memory events. Overkill for an in-JVM pipeline.

### Alternative 3: Redis Streams
- **Pros**: Same Redis infrastructure, streams support consumer groups.
- **Cons**: No built-in partitioning, single-region replication, Redis memory pressure risks stream eviction. Adds complexity without Kafka's durability guarantees.

### Alternative 4: Direct Method Calls (no messaging)
- **Pros**: Simplest possible approach — each stage calls the next directly.
- **Cons**: Tight coupling between stages. Adding a new stage requires modifying the previous stage's code. No async processing — Stage 1 blocks until Stage 4 completes. Loss of topic-based routing and the ability to add side-effect listeners (e.g., metrics, auditing) without modifying producers.

### Why Spring Events Won

Spring Events provide the **right balance** for a single-JVM modular monolith:
- **Zero infrastructure** — no broker, no topics, no consumer groups.
- **Async by opt-in** — `@Async` on listeners provides parallel processing without blocking publishers.
- **Loose coupling** — producers publish events without knowing who consumes them (same as Kafka topic model).
- **Observability** — Micrometer can instrument `ApplicationEventMulticaster` to track event publication count and listener execution time.
- **Evolution path** — When stages are extracted to microservices (Phase 4), the `EventListener` interface maps 1:1 to a Kafka consumer, and `publishEvent` maps 1:1 to a Kafka producer. The event DTOs remain unchanged.

## Consequences

### Positive
- **Simplified infrastructure** — No MSK cluster, no Kafka topics, no consumer groups, no SASL credentials. The entire pipeline runs in a single JVM with Redis + PostgreSQL as the only external dependencies.
- **Faster local development** — `mvn spring-boot:run` with `dev` profile starts the full pipeline. No Docker Compose with Kafka/Zookeeper needed.
- **Lower cost** — MSK cluster cost (~$0.50/hr × 3 brokers × 730h = ~$1,095/month) eliminated. One fewer EKS node group.
- **Simpler deployment** — No `kafka-consumer` pod type. Single `Deployment` HPA scales all pods identically.
- **Easier testing** — Integration tests don't need Testcontainers with Kafka. `@SpringBootTest` with `ApplicationEventPublisher` is sufficient.

### Negative
- **No message persistence between restarts** — If the JVM crashes mid-pipeline, in-flight events are lost. Acceptable because: (a) the ingestion scheduler re-runs every 15 minutes, (b) database upserts are idempotent, (c) the pipeline is designed for eventual consistency, not transactional exactly-once.
- **No cross-JVM event delivery** — If stages are extracted to separate microservices (Phase 4), the Spring Events approach will need to be replaced with Kafka or gRPC. This is an explicit trade-off: simplify now, reintroduce messaging later when scaling demands it.
- **Synchronous by default** — Without `@Async`, event listeners run on the publisher's thread. This is mitigated by the `@Async` annotation on all pipeline listeners and the `ThreadPoolTaskExecutor`.
- **No dead-letter queue** — Malformed events are logged and discarded. The pipeline must handle event validation at the publisher side (before `publishEvent`). If a listener throws, the `AsyncUncaughtExceptionHandler` logs the error and the event is lost. Retry logic, if needed, must be implemented within the listener itself.

## Migration Plan

1. Create `SpringEventConfig.java` with `@EnableAsync` + `ThreadPoolTaskExecutor`.
2. Remove `KafkaConfig.java` and `KafkaEventBus.java`.
3. Remove `spring-kafka` from `pom.xml`.
4. Update `application.yml` — remove all `spring.kafka.*` properties.
5. Verify `mvn compile` and `mvn test` pass.
6. Remove Kafka Terraform (`modules/msk/`, topic resources, consumer node group).
7. Remove Kafka observability (Prometheus rules, Grafana panels).
8. Update Helm charts — remove `KAFKA_*` env vars, consumer pod template.
9. Run full integration test to verify end-to-end pipeline.

See [Project_Plan.md §5.6 — DSRC-ARCH](/design/Project_Plan.md) for the detailed sub-task breakdown (ARCH-a through ARCH-w).

## Related Documents
- [ADR-0004: Apache Kafka for ETL Data Pipeline](/docs/adr/0004-kafka-for-etl.md) — **Superseded by this ADR**
- [ADR-0006: Ten-Source Market Data Pipeline](/docs/adr/0006-ten-source-market-data-pipeline.md) — **Superseded by this ADR**
- [HLD.md](/design/HLD.md) — Updated to reflect Spring Events architecture
- [LLD.md](/design/LLD.md) — Updated to remove all Kafka references
- [Project_Plan.md §5.6 — DSRC-ARCH](/design/Project_Plan.md) — Detailed implementation sub-steps
