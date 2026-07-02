# ADR-0003: Modular Monolith Architecture

## Status
Accepted (2026-06-29)

## Date
2026-06-29

## Context
The Media Buying Dashboard must be delivered within a 12-week MVP timeline with a 2-person core team. The architecture must support future growth into independently scalable services without requiring a complete rewrite. The system needs to:

1. Ingest data from 5 ad platforms on a 15-minute schedule.
2. Process and persist KPI metrics into PostgreSQL.
3. Serve a dashboard UI with sub-second query response times.
4. Scale to handle 10k+ concurrent users and 100k+ KPI rows.
5. Support future extraction of components into dedicated services.

## Decision
Adopt a **modular monolith** architecture: a single Spring Boot application deployed as one JAR, but with strict package boundaries that mirror a future microservices decomposition. Kafka already decouples the ingestion pipeline from the dashboard.

### Package Boundary Map

```
┌──────────────────────────────────────────────────────────────────┐
│                  Modular Monolith (single JVM)                    │
│                                                                   │
│  ┌─────────────────────┐  ┌──────────────────────────────────┐  │
│  │  integration/         │  │  controller/  service/  bean/    │  │
│  │  ── wrapper/          │  │  (Dashboard REST + JSF Beans)    │  │
│  │  ── auth/             │  │  ── DashboardService             │  │
│  │  ── normalizer/       │  │  ── KPIQueryService              │  │
│  │  ── ratelimit/        │  │  ── CompositeScoringService      │  │
│  │  schedulers/          │  │  ── CalculatorService             │  │
│  │  (Ingestion pipeline) │  │  ── ClientLookupService           │  │
│  └─────────┬─────────────┘  └──────────┬───────────────────────┘  │
│            │                            │                          │
│            │  Kafka kpi.raw             │  (same JVM call)         │
│            ▼                            ▼                          │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │                    messaging/  model/  repository/             │ │
│  │  (Shared domain: RawKPIEvent, KPIMetrics, Platform, etc.)    │ │
│  └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### Package-to-Service Mapping for Future Extraction

| Current Package | Future Microservice | Extraction Trigger |
|----------------|---------------------|--------------------|
| `integration/*` | **Ingestion Service** | If ingestion needs independent scaling or different deployment schedule |
| `scheduler/` | **ETL Processor** | If Kafka consumer group needs dedicated infrastructure |
| `service/CompositeScoringService` | **Scoring Engine** | If scoring algorithm becomes complex or needs GPU/ML offloading |
| `bean/` + `controller/` | **Dashboard API** | If SPA frontend is built and JSF is deprecated |
| `calculator/` | **Calculator Service** | If ROI calculator needs high availability independent of dashboard |
| `aspect/AuditLoggingAspect` | **Audit Service** | If audit compliance requires dedicated storage or querying |

## Alternatives Considered

### Alternative 1: Full Microservices from Day One
- **Pros**: Independent deployability, independent scaling, technology diversity.
- **Cons**: 12-week MVP impossible with 2-person team. Requires service mesh, API gateway, distributed tracing, event sourcing, saga patterns, and CI/CD for 6+ services. Networking overhead and latency between services. Requires DevOps effort for service discovery and load balancing.

### Alternative 2: Modular Monolith (Chosen)
- **Pros**: Fastest path to MVP. Single deployment unit. No inter-service network calls. Shared JPA entity manager and transaction context. Easy refactoring with package boundaries.
- **Cons**: No independent scaling — the entire monolith scales together. A memory leak in the ingestion scheduler can impact the dashboard UI. Requires developer discipline to respect package boundaries.

### Alternative 3: Modular Monolith with Kafka Decoupling (Chosen Variant)
- **Pros**: The ingestion pipeline is already decoupled via Kafka. The `integration/` package communicates with the rest of the system asynchronously. This is the first step toward microservices extraction.
- **Cons**: Adds Kafka operational overhead. Some cross-package calls remain synchronous (e.g., scoring service calling KPI query service).

## Consequences

### Positive
- **Fast development**: Sprint velocity is 2–3× higher than a microservices approach for a team of 3 developers.
- **Single deployment**: One JAR, one Docker image, one Helm release, one set of probes.
- **Shared transactions**: JPA operations within the same `@Transactional` boundary — no distributed transaction headaches.
- **Easy debugging**: A single IDE project, a single debugger session, a single log stream.
- **Clear extraction path**: Package boundaries map directly to future services. When extraction is needed, the code can be moved into a new module with minimal changes.

### Negative
- **Co-located concerns**: The ingestion scheduler, scoring engine, and UI controllers run in the same JVM. A CPU-intensive scoring computation can delay an HTTP response.
- **Single scaling unit**: If the ingestion pipeline needs more CPU, the entire dashboard scales up. This is inefficient for MVP scale but acceptable.
- **Discipline required**: Developers must respect package boundaries and not create circular dependencies. Enforced by `maven-checkstyle-plugin` with custom rules.
- **Dependency coupling**: A change to a shared entity (`KPIMetrics`) affects all packages. Mitigated by keeping entities thin and using DTOs at package boundaries.

### Extraction Readiness

The following conditions indicate it's time to extract:
1. **Team size**: 3+ developers working on the same codebase causing merge conflicts.
2. **Scaling divergence**: Ingestion consumers need different instance types or scaling rules than the dashboard pods.
3. **Deployment frequency**: Ingestion changes require different release cadence than dashboard UI changes.
4. **Technology divergence**: Need to use a different database, caching strategy, or programming language for a sub-system.

## Related Documents
- [HLD.md](/design/HLD.md) §2 — Architecture Style (Event-Driven Microservices Architecture)
- [HLD.md](/design/HLD.md) §15 — Optional Evolution Path
- [LLD.md](/design/LLD.md) §2 — Package Structure
- [Project_Plan.md](/design/Project_Plan.md) §5.3 — Backend Development (WBS)
