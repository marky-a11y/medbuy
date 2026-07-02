# ADR: Integration Wrappers

## Status
Accepted (2026-06-29)

## Context
The Media Buying Dashboard ingests advertising performance metrics from five external ad platforms:
- Google Ads
- Meta (Facebook) Ads
- TikTok Ads
- LinkedIn Ads
- iHeart Radio

Each platform exposes a REST API with different authentication schemes, rate limits, data models,
and SLAs. The system must:
1. Fetch data from all five platforms on a recurring schedule (every 15 minutes by default).
2. Normalize responses into a common `PlatformApiResponse` format.
3. Publish normalized events to a Kafka topic (`kpi.raw`) for downstream ETL processing.
4. Handle failures gracefully so one platform outage does not block others.
5. Respect each platform's rate limits to avoid throttling or bans.

## Decision 1: Single Scheduler + Async Wrappers (vs. Per-Platform Schedulers)

### Decision
Use a single `@Scheduled` method in `AdPlatformIngestionScheduler` that fires all five
wrapper calls in parallel via `@Async` + `CompletableFuture.allOf()`.

### Rationale
- **Simplicity** — One scheduler, one cron expression, one configuration point.
- **Deterministic behaviour** — All platforms are fetched within the same time window; if the
  15-minute cycle is missed (e.g. due to backlog), all platforms shift together.
- **Resource control** — A single thread pool (`adPlatformTaskExecutor` with core-size=10)
  limits total concurrency; individual wrappers cannot exhaust the entire system.
- **Observability** — One log line summarises the success/failure of all five platforms.

### Trade-Offs
- If a single wrapper hangs, the `CompletableFuture.allOf().get(600, SECONDS)` timeout
  prevents indefinite blocking, but the scheduler thread is occupied for up to 10 minutes.
- Per-platform schedulers would allow different intervals (e.g. iHeart Radio every hour),
  but this complexity is not justified for the MVP.

## Decision 2: ConcurrentHashMap for Token Cache (vs. Redis)

### Decision
Use a `ConcurrentHashMap<String, TokenEntry>` in `OAuthTokenManager` to cache OAuth access tokens.

### Rationale
- **Speed** — Token lookups are sub-millisecond, no network round-trip to Redis.
- **Locality** — Tokens are private to each application instance; there is no need for
  cross-instance sharing (each pod manages its own token lifecycle).
- **Simplicity** — No additional Redis key expiry/TTL management; token expiry is tracked
  in-memory via the `expiresAt` field (with a 5-minute refresh buffer).
- **Stateless refresh** — If a pod restarts, tokens are re-fetched on the first API call.

### Trade-Offs
- In a multi-pod deployment, each pod obtains its own token, slightly increasing the
  number of OAuth refresh calls. This is acceptable because refresh tokens rotate slowly
  (typically valid for months) and the buffer window prevents thundering herd.
- If Redis were used, tokens could be shared across pods, reducing refresh calls.
  We may revisit this decision if we observe high OAuth provider latency under load.

## Decision 3: Mock for iHeart Radio (vs. Real API)

### Decision
The `IHeartRadioApiWrapper` returns realistic mock data by default, with a
`mock-enabled` toggle for future real API integration.

### Rationale
- **No public ad API** — iHeart Media does not offer a fully documented, self-serve
  advertising API comparable to Google/Meta/TikTok/LinkedIn.
- **Business continuity** — The dashboard must show all five platforms to provide a
  complete view; a mock with estimated data fills the gap without blocking the pipeline.
- **Data source annotation** — Mocked data is labelled `"iHeart Radio (estimated)"`
  and is clearly distinguishable from real API data.
- **Extensibility** — The `mock-enabled` flag allows a seamless transition to real API
  calls when an official integration becomes available.

### Trade-Offs
- Estimated data may not reflect actual campaign performance. Users are warned via the
  data source label and staleness indicators.
- If iHeart later provides an official API, the wrapper implementation must be updated,
  but the interface and pipeline remain unchanged.

## Decision 4: Rate Limiting Strategy

### Decision
Use Guava's `RateLimiter` (token-bucket algorithm) with per-platform rates configured
in `application.yml`.

### Platform Rates
| Platform      | Rate (permits/sec) | Reasoning |
|---------------|--------------------|-----------|
| Google Ads    | 0.02 (1 per 50s)   | Google's QPS limit is ~1 per 50s per developer token. |
| Meta Ads      | 0.055 (1 per 18s)  | Meta's 200 calls per hour per user ≈ 0.055/s. |
| TikTok Ads    | 10.0               | TikTok's default limit is 10 requests/second. |
| LinkedIn Ads  | 0.028 (1 per 36s)  | LinkedIn's 100 calls per day ≈ 0.028/s (with burst allowance). |
| iHeart Radio  | 0.28 (1 per 3.6s)  | Conservative default for an API with unknown limits. |

### Rationale
- **In-process** — No external dependency; rate limiting works even if Redis is down.
- **Fine-grained** — Each platform gets its own `RateLimiter` instance, so a burst on
  one platform does not affect others.
- **Configurable** — Rates are externalised in `application.yml` and can be tuned
  without code changes.

### Trade-Offs
- Guava's `RateLimiter` is single-JVM; in a multi-pod deployment each pod enforces
  its own rate independently. This means the aggregate rate across all pods may exceed
  the per-instance limit. We accept this because our ingestion is a single pod task
  (the scheduler is not distributed).
- If horizontal scaling of ingestion is needed in the future, a distributed rate limiter
  (e.g. Redis-based) would be required.

## Decision 5: Separate KpiNormalizer (vs. Inline Conversion)

### Decision
Create a dedicated `KpiNormalizer` component that converts `PlatformApiResponse` → `RawKPIEvent`,
rather than embedding the conversion in each wrapper or in the scheduler.

### Rationale
- **Single responsibility** — Wrappers focus on API calls; the normalizer handles mapping,
  null-safety, UUID generation, and platform name normalisation.
- **Testability** — The normalizer is a stateless `@Component` with pure functions;
  easily unit-testable without mocking external services.
- **Reusability** — If we add REST endpoints that accept `PlatformApiResponse` directly,
  the same normalizer can be used.

### Trade-Offs
- An extra class and method call per message, but the overhead is negligible
  (sub-millisecond).
- The normalizer must be kept in sync with `PlatformApiResponse` and `RawKPIEvent`
  schemas; any field changes require updates in both DTOs and the normalizer.

## Consequences
- The integration pipeline is horizontally scalable (Kafka consumers can be scaled
  independently of the scheduler).
- Each platform's failure is isolated; one API outage does not affect the others.
- Rate limits are respected without external infrastructure.
- Mocked iHeart Radio data is clearly identified as estimated.
- OAuth tokens are managed efficiently without Redis overhead.
- The system can be extended to new platforms by creating a new wrapper and adding
  its configuration — no changes to the scheduler or pipeline are required.

## Related Documents
- [HLD.md](/design/HLD.md) §9 — Integration Layer
- [LLD.md](/design/LLD.md) §7 — Integration Wrappers
- [Project_Plan.md](/design/Project_Plan.md) §5.4.1 — Integration Wrappers (WBS)
