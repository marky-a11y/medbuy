# ADR-0004: Apache Kafka for ETL Data Pipeline — SUPERSEDED

## Status
**Superseded by [ADR-0007: Spring Application Events Replace Apache Kafka for Inter-Stage Messaging](./0007-spring-events-over-kafka.md)** (2026-06-30)

Original status: Accepted (2026-06-29)

## Date
2026-06-29

## Context
The Media Buying Dashboard ingests advertising performance metrics from 5 ad platforms (Google Ads, Meta Ads, TikTok Ads, LinkedIn Ads, iHeart Radio) on a 15-minute schedule. The data pipeline must:

1. Decouple ingestion from processing — platform API calls should not block dashboard queries.
2. Support replay — if a consumer fails, it should be able to reprocess missed messages.
3. Handle burst loads — all 5 platforms may return data simultaneously.
4. Provide durable storage — messages must survive broker restarts.
5. Allow multiple consumer groups — the dashboard pod and future analytics pipeline may both consume the same data.

## Decision
Use **Apache Kafka** (via Amazon MSK) with 3 topics, each with specific partition counts and replication factors.

### Topic Design

| Topic | Partitions | Replication Factor | Purpose | Retention |
|-------|------------|--------------------|---------|-----------|
| `kpi.raw` | 6 | 2 | Raw KPI events from ad platform ingestion | 7 days |
| `kpi.refresh` | 3 | 2 | Cache invalidation signals after DB write | 1 day |
| `data-refresh.internal` | 3 | 2 | Manual/cron-triggered refresh commands | 1 day |

### Message Flow

```
┌─────────────────┐     produce      ┌──────────────┐     consume     ┌────────────────┐
│ AdPlatform       │ ──────────────►  │  kpi.raw     │ ─────────────► │ KPIStreamConsumer│
│ Ingestion        │                  │  (6 parts)   │                │ (upsert → DB)   │
│ Scheduler        │                  └──────────────┘                └────────┬───────┘
│ (@Async 5 calls) │                                                            │
└─────────────────┘                                                             │
                                                                                │ produce
                                                                                ▼
┌────────────────┐                  ┌──────────────┐               ┌─────────────────────┐
│ CacheInvalid.   │ ◄────────────── │  kpi.refresh  │ ◄─────────── │ KpiRefreshProducer   │
│ Service         │                 │  (3 parts)    │              │ (post-upsert)        │
└────────────────┘                 └──────────────┘              └─────────────────────┘

┌────────────────┐                  ┌──────────────────────┐
│ DataRefresh     │ ◄────────────── │ data-refresh.internal │
│ Scheduler       │                 │ (3 parts)             │
│ (fallback)      │                 └──────────────────────┘
└────────────────┘
```

### Producer/Consumer Configuration

| Property | `kpi.raw` | `kpi.refresh` | `data-refresh.internal` |
|----------|-----------|---------------|-------------------------|
| `acks` | `all` | `1` | `1` |
| `compression.type` | `snappy` | `none` | `none` |
| `max.in.flight.requests.per.connection` | 1 (guaranteed order) | 5 | 5 |
| `enable.idempotence` | `true` | `false` | `false` |
| Consumer group | `media-buying-kpi-consumer` | `media-buying-refresh-consumer` | `media-buying-internal-consumer` |
| `auto.offset.reset` | `earliest` | `latest` | `latest` |
| Dead letter topic | `kpi.raw.dlq` | (none) | (none) |

## Alternatives Considered

### Alternative 1: RabbitMQ
- **Pros**: Lighter weight than Kafka. Built-in exchange/binding model. Easier to set up for simple pub/sub. Good for point-to-point messaging.
- **Cons**: No partition-level parallelism — messages are consumed sequentially per queue. Limited replay capability (messages are removed after acknowledgment). No offset management — a consumer restart loses position if not explicitly tracked. Throughput bottlenecks for high-volume ingestion bursts.

### Alternative 2: Amazon SQS + SNS
- **Pros**: Fully managed, no broker to operate. Infinite retention. Built-in dead-letter queues. FIFO queues for ordered delivery.
- **Cons**: No consumer group semantics — each consumer gets its own queue. Max message size 256KB (RawKPIEvent is ~2KB, but this is acceptable). No built-in replay mechanism — messages are deleted after visibility timeout. No partitioning — all messages go to a single queue, limiting parallel consumption.

### Alternative 3: Direct Database Writes from Scheduler
- **Pros**: Simplest architecture — no message broker. Scheduler writes directly to PostgreSQL. Single consistency domain.
- **Cons**: Tight coupling — if the database is under load, ingestion blocks. No replay — if the scheduler fails mid-batch, there is no record of what was sent. No decoupling for future microservices extraction. If the dashboard pod restarts, it cannot catch up on missed writes.

### Alternative 4: Redis Streams
- **Pros**: Same Redis infrastructure (no additional broker). Streams support consumer groups and replay.
- **Cons**: No built-in partitioning (single shard per stream). No replication across availability zones (single-region). Limited retention compared to Kafka. Not designed for long-term message storage. If Redis is under memory pressure, streams may be evicted.

## Consequences

### Positive
- **Decoupled ingestion and dashboard**: The ingestion scheduler can be scaled independently of the dashboard pods. A backlog on `kpi.raw` does not impact dashboard query performance.
- **Replay capability**: If the consumer crashes and restarts, `auto.offset.reset=earliest` allows it to reprocess messages from the beginning of the retention period.
- **Partition parallelism**: `kpi.raw` with 6 partitions allows up to 6 concurrent consumer threads, matching our 5 platforms + 1 spare.
- **Dead letter queue**: Malformed messages (e.g., deserialization failures) are routed to `kpi.raw.dlq` for manual inspection without blocking the main pipeline.
- **Idempotent producer**: Exactly-once semantics for `kpi.raw` prevent duplicate upserts during producer retries.

### Negative
- **Operational overhead**: MSK cluster management, broker patches, monitoring consumer lag. Mitigated by using Amazon MSK (fully managed).
- **Latency**: End-to-end latency from ingestion to dashboard display is ~2-5 seconds (producer → broker → consumer → DB → cache invalidation). Acceptable for a 15-minute refresh cycle.
- **Complexity**: Developers must understand Kafka concepts (topic, partition, consumer group, offset, rebalancing). Mitigated by documentation and starter templates.
- **Cost**: MSK cluster cost (~$0.50/hour for kafka.t3.small × 3 brokers) adds to the monthly bill. Acceptable for production.

### Partition Count Rationale

- **`kpi.raw` (6 partitions)**: One partition per platform (5) + 1 spare for future platform or rebalancing headroom. Each partition processes messages in order for a single platform.
- **`kpi.refresh` (3 partitions)**: Cache invalidation events are lightweight and can tolerate some ordering skew. 3 partitions provide sufficient parallelism for the cache invalidation consumer.
- **`data-refresh.internal` (3 partitions)**: Rarely used (manual trigger). 3 partitions are sufficient for a low-volume topic.

## Related Documents
- [HLD.md](/design/HLD.md) §4.2 — Event Streaming (Kafka)
- [LLD.md](/design/LLD.md) §12 — Kafka Configuration
- [Project_Plan.md](/design/Project_Plan.md) §5.3 — Kafka tasks (BACK-05, BACK-06, BACK-07)
- `docker-compose-dev.yml` — Local Kafka for development
