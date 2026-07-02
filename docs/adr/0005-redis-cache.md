# ADR-0005: Cache Strategy for Dashboard Performance

## Status
Superseded by NativeCacheService (2026-07-01) — migrated from Redis to in-memory `ConcurrentHashMap`-based cache for simplification.

## Date
2026-06-29 (original), 2026-07-01 (updated)

## Context
The Media Buying Dashboard must serve KPI data and computed scores with sub-second latency while handling 10k+ concurrent users and up to 100k KPI rows. The primary data source is PostgreSQL, which can become a bottleneck under concurrent read-heavy workloads. The system needs a caching layer that:

1. Reduces database load by serving frequently accessed data from memory.
2. Supports cache-aside pattern — data is loaded on first access and invalidated on updates.
3. Provides configurable TTLs for different data types (composite scores, platform lists, KPI query results).
4. Survives pod restarts (cached data should persist across application redeployments).
5. Integrates with the Kafka invalidation pipeline — when new KPI data arrives, relevant cache entries are purged.

## Decision
Use **Redis** (via Amazon ElastiCache) with a **cache-aside** (lazy-loading) pattern. Five distinct key patterns with specific TTLs, invalidation via Kafka on `kpi.refresh` topic.

### Cache Key Patterns

| Pattern | Key Format | TTL | Cache Population | Invalidation Trigger |
|---------|------------|-----|------------------|----------------------|
| Composite Top Opportunity | `composite:top` | 5 minutes | On miss: `CompositeScoringService` computes from DB | `kpi.refresh` → delete key |
| Hierarchy (Platforms + Sectors) | `hierarchy:all` | 15 minutes | On miss: `PlatformSectorService` queries DB | `kpi.refresh` → delete key |
| Metrics Page | `metrics:{platformId}:{sectorId}:{page}:{sortCol}:{sortDir}` | 5 minutes | On miss: `KPIQueryService.getMetrics()` queries DB | `kpi.refresh` → delete `metrics:*` wildcard |
| Platform List | `platforms:list` | 15 minutes | On miss: `PlatformSectorService` | Manual cache purge (admin action) |
| Client Prospects | `clients:top:{sectorId}` | 15 minutes | On miss: `ClientLookupService.findTopClients()` | `kpi.refresh` → delete `clients:top:{sectorId}` |

### Cache-Aside Flow

```
1. Client request arrives
2. Service checks NativeCacheService key
   ├── HIT → return cached DTO
   └── MISS → query PostgreSQL → build DTO → store in NativeCacheService with TTL → return DTO
3. On data update: async listener on `kpi.refresh` → delete relevant cache keys via CacheInvalidationService
```

### Cache Eviction Policy

- **Policy**: Lazy expiry on read — expired entries are evicted when accessed.
- `NativeCacheService` periodically scans and purges expired entries to free memory.

### Serialization

- **Library**: `GenericJackson2JsonRedisSerializer` — stores DTOs as JSON strings in Redis.
- **Reasoning**: Human-readable in Redis CLI, language-agnostic, supports evolving DTO schemas (additive fields are backward-compatible).

### Connection Pool

- **Client**: Lettuce (reactive, non-blocking, connection pooling).
- **Pool settings**:
  - `max-active`: 16 connections
  - `min-idle`: 2 connections
  - `max-idle`: 8 connections
  - `timeout`: 5000ms

## Alternatives Considered

### Alternative 1: Caffeine (JVM-Level Cache)
- **Pros**: No network round-trip (sub-microsecond access). Simple configuration. No additional infrastructure. Built-in statistics.
- **Cons**: Per-pod cache — each pod has its own copy, leading to up to N copies of the same data. Cache invalidation is local — a KPI update on one pod does not invalidate cache on other pods. Memory pressure on the JVM heap. Lost on pod restart.

### Alternative 2: Hazelcast (Distributed In-Memory Grid)
- **Pros**: Distributed across pods with automatic partitioning. Near-cache support for local speed with cluster consistency.
- **Cons**: Heavier than Redis — requires a Hazelcast cluster alongside the application. Heap pressure from near-cache entries. Less common in Spring Boot ecosystems compared to Redis. Network overhead for cluster membership and partition replication.

### Alternative 3: PostgreSQL Query Cache + Materialized Views
- **Pros**: No additional infrastructure (leveraging existing PostgreSQL). Materialized views can pre-compute composite scores. Built-in query caching.
- **Cons**: PostgreSQL query cache is limited to buffer pool — not designed for application-level data caching. Materialized views require refresh scheduling (stale data until next refresh). No TTL-based eviction per key. More DB CPU/memory pressure.

### Alternative 4: No Cache (Direct DB Queries)
- **Pros**: Simplest architecture. No cache invalidation logic. No serialization overhead. No additional infrastructure cost.
- **Cons**: Each page load (potentially 10k+ concurrent users) hits PostgreSQL. The composite scoring query is expensive — it scans all KPI rows, normalizes, and sorts. Without caching, the database becomes the bottleneck at a fraction of the target load.

## Consequences

### Positive
- **Database load reduction**: Cache hit ratio targets > 80%. At 10k concurrent users, the database sees only ~2k read requests per minute instead of 10k+.
- **Sub-second response times**: Redis reads are 1-5ms vs. 20-100ms for PostgreSQL queries. The Top Opportunity card (composite score computation) drops from ~500ms DB time to ~2ms cache time.
- **Survives pod restarts**: Redis is external to the JVM — when a pod restarts, the cache is warm from the first request.
- **Configurable TTLs**: Different data types have different freshness requirements. Composite scores (5 min TTL) are recomputed more frequently than platform lists (15 min TTL).
- **Kafka-driven invalidation**: The `kpi.refresh` topic ensures that cache entries are purged within seconds of a KPI data update, keeping staleness windows tight.

### Negative
- **Additional infrastructure**: ElastiCache cluster must be provisioned, monitored, and maintained. Adds ~$15-30/month for `cache.t3.micro` (dev/staging) and more for production-size clusters.
- **Network round-trip**: Every cache operation adds ~1ms network latency. Acceptable compared to the DB query time saved.
- **Serialization overhead**: DTOs are serialized/deserialized on every cache access. `GenericJackson2JsonRedisSerializer` is slower than `StringRedisSerializer` but provides schema flexibility.
- **Cache invalidation complexity**: Stale data can be served if cache invalidation fails (e.g., Kafka consumer is down). Mitigated by short TTLs (5-15 minutes) — data self-invalidates even if the invalidation consumer fails.
- **Wildcard key deletion**: Invalidating all `metrics:*` keys on every KPI refresh is O(n) in key count. For 1000+ metric keys, this takes ~50ms on a `cache.t3.micro`. Acceptable for the refresh frequency (every 15 minutes).

### Cache Invalidation Edge Cases

| Scenario | Behavior | Mitigation |
|----------|----------|------------|
| Kafka consumer lag on `kpi.refresh` | Cache serves stale data for up to the lag duration | TTL-based self-invalidation; alert on consumer lag > 1000 records |
| Redis node failure | All cache keys lost; DB takes full load until cache warms up | `maxmemory` + `volatile-lru` prevents OOM; connection pool health checks redirect to DB |
| Partial cache invalidation (one key missed) | Specific metrics page shows stale data while others update | Short TTL (5 min) ensures eventual consistency |
| Concurrent cache invalidation and read | Read may populate cache with stale data just before invalidation | Use Redis `DEL` (not `UNLINK`) to block until key is removed; the stale data is replaced within TTL |

## 2026-07-01 Update: Redis → NativeCacheService Migration

Redis has been removed in favor of a Java-native `ConcurrentHashMap`-based cache (`NativeCacheService`).

### Changes Made

- Created `CacheService` interface and `NativeCacheService` implementation (TTL-based, lazy eviction, glob pattern matching).
- Removed `spring-boot-starter-data-redis`, `lettuce-core`, and `spring-session-data-redis` dependencies from `pom.xml`.
- Removed all Redis configuration from `application.yml`, `application-dev.yml`, and `application-test.yml`.
- Changed session storage from Redis to in-memory (`spring.session.store-type: none`).
- Updated all service classes (`KPIQueryService`, `CompositeScoringService`, `PlatformSectorService`, `ClientLookupService`, `ClientListService`, `InsightsEngine`, `CacheInvalidationService`) to inject `CacheService` instead of `RedisTemplate`.
- Updated `CacheConfig` to use `ConcurrentMapCacheManager` instead of `RedisCacheManager`.

### Rationale

- Single-JVM development and deployment does not require a distributed cache.
- Eliminates the need to run a Redis Docker container for local development.
- Removes Redis connection failures as a source of startup errors.
- Simpler deployment — no ElastiCache cluster to provision.

## Related Documents
- [HLD.md](/design/HLD.md) §4.1 — Databases (Redis)
- [LLD.md](/design/LLD.md) §9 — Caching Strategy
- [Project_Plan.md](/design/Project_Plan.md) §5.3 — Cache tasks (BACK-07, BACK-08, BACK-09)
- `CacheConfig.java` — Cache manager configuration
- `CacheInvalidationService.java` — Event-driven cache invalidation
- `NativeCacheService.java` — In-memory cache implementation
