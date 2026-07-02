# ADR-0006: Ten-Source Market Data Pipeline — SUPERSEDED

## Status
**Superseded by [ADR-0007: Spring Application Events Replace Apache Kafka for Inter-Stage Messaging](./0007-spring-events-over-kafka.md)** (2026-06-30)

The 4-stage pipeline concept (10 sources → sector grouping → company/platform grouping → KPI building) is **retained**, but the Kafka-based messaging backbone described in this ADR is replaced with Spring `ApplicationEventPublisher` + `@EventListener` + `@Async`.

Original status: Proposed (2026-06-30)

## Context
The Media Buying Dashboard currently sources KPI data from five ad-platform APIs (Google Ads, Meta Ads, TikTok Ads, LinkedIn Ads, iHeart Radio). These platforms provide direct advertising metrics but have significant limitations:

1. **Narrow signal scope** — Ad platform APIs only report on campaigns already running on that platform. They cannot surface organic market demand, competitive activity, or industry trends.
2. **API access barriers** — Google/Meta/TikTok/LinkedIn require business-verified developer accounts with active ad spend, making local development and testing impractical without pre-configured API keys.
3. **Single dimension** — Each API provides metrics per-platform, per-campaign. There is no cross-source correlation or market-intelligence aggregation.

The product owner has specified a new data-sourcing strategy: aggregate market intelligence from 10 diverse, publicly-accessible data sources and synthesize media-buying KPIs through a multi-stage processing pipeline.

## Decision: 4-Stage Pipeline with 10 External Sources

Replace the 5 direct ad-platform API wrappers with 10 market-data sources feeding a **4-stage Kafka-based ETL pipeline**:

**Stage 1 — Source Ingestion:** 10 wrappers (pytrends, eBay, Reddit, X API, Meta Ads Library, Yelp Fusion, Foursquare Places, Bing Webmaster, Skyscanner, Indeed/Adzuna) normalize raw API responses into `NormalizedSourceMessage` DTOs and publish to `source.raw`.

**Stage 2 — Sector Grouping:** `SectorGrouper` consumer classifies each source message into one or more commerce sectors using configurable keyword/category rules. Publishes to `sector.grouped`.

**Stage 3 — Company/Platform Grouping:** `CompanyPlatformGrouper` consumer identifies companies/brands from source data, maps them to commerce sectors and target ad platforms. Publishes to `company.grouped`.

**Stage 4 — KPI Building:** `KpiBuilder` consumer aggregates cross-source signals for each company+sector+platform combination, computes composite KPI values (ROAS, CAC, CLTV, Conversion Rate, Scalability, Attribution Accuracy), and upserts into the `kpi_metrics` table. Emits `KpiRefreshEvent` for cache invalidation.

### New Kafka Topics

| Topic | Partitions | Purpose |
|---|---|---|
| `source.raw` | 12 | Raw normalized data from 10 wrappers |
| `sector.grouped` | 6 | Source data mapped to commerce sectors |
| `company.grouped` | 6 | Companies grouped by sector and ad platform |
| `kpi.raw` | 6 | (Retained — KPI data from pipeline) |
| `kpi.refresh` | 3 | (Retained — cache invalidation signals) |

### New Entity Tables

| Table | Purpose |
|---|---|
| `companies` | Stores identified companies/brands with sector and ad-platform mappings |
| `source_raw` | Append-only log of raw source data for audit and replay |

### Configuration: Sector Classification Rules

Sector assignment is driven by keyword-matching rules externalized in `application.yml`:
```yaml
sector-classification:
  technology: [software, saas, cloud, ai, it-services]
  finance: [banking, insurance, investment, fintech]
  retail: [ecommerce, apparel, food, grocery, restaurant]
  health-wellness: [healthcare, fitness, supplement, medical]
  travel: [flight, hotel, vacation, tourism]
  job-market: [hiring, job, career, employment]
```

## Rationale

- **Multi-source triangulation** — By aggregating signals from search trends (pytrends, Bing), marketplace activity (eBay), social sentiment (Reddit, X), ad library data (Meta), and location data (Yelp, Foursquare), the KPI Builder produces more robust, market-representative estimates than any single ad platform API can provide.
- **No active ad-account requirement** — All 10 sources are accessible with free-tier API keys or public data, enabling local development and CI/CD testing without business-verified ad accounts.
- **Pipeline replayability** — Each stage writes to a durable Kafka topic, enabling independent scaling, fault isolation, and historical replay if KPI computation logic changes.
- **Sector/company discovery** — Unlike ad-platform APIs which only report known campaign metrics, the 4-stage pipeline automatically discovers trending companies and sectors from organic market data, expanding the dashboard's "opportunity surface."
- **Configurable mapping rules** — Sector classification, company-to-platform mapping, and KPI synthesis weights are all externalized in `application.yml`, allowing analysts to tune the pipeline without code changes.

## Consequences

### Positive
- Dashboard can surface opportunities that don't yet have active campaigns (market-leading indicator).
- Development and testing can proceed without external ad-account dependencies.
- Each stage can be horizontally scaled independently via Kafka consumer groups.
- New data sources can be added by creating a wrapper + normalizer mapping — no other stage changes required.

### Negative
- Increased Kafka infrastructure (3 new topics, 3 new consumer groups).
- KPI accuracy depends on synthesis weights which require calibration and back-testing.
- Stage 4 computation is more complex than direct API-to-KPI mapping (multiple sources, weighted aggregation).
- Company-to-platform mapping may produce false positives if classifier is not tuned.

## Migration Path
1. Keep existing `kpi_metrics` table schema — Stage 4 produces the same `KPIMetrics` entities, so `CompositeScoringService` and the dashboard UI remain unchanged.
2. Run the new pipeline in parallel with existing ad-platform ingestion during a transition period.
3. Once pipeline KPIs are validated against actual campaign data, deprecate the 5 direct ad-platform wrappers.

## Related Documents
- [HLD.md](/design/HLD.md) §9 — Integration Points (10-source table + 4-stage pipeline diagram)
- [LLD.md](/design/LLD.md) §2 — Package Structure (revised wrapper/normalizer layout)
- [Project_Plan.md](/design/Project_Plan.md) §5.6 — Data Source Migration task
