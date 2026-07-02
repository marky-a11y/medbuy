# Low-Level Design (LLD) – Media Buying Dashboard

---

## 1. Document Information

| Field | Value |
|---|---|
| **Project** | Media Buying Dashboard – AutoResolve Media Intelligence Platform |
| **Version** | 2.1 |
| **Status** | Draft (Spring Events-based pipeline; Kafka removed) |
| **Date** | June 2026 |
| **Author** | Software Engineering Team |
| **Related Docs** | HLD.md, PRD.md, java_web_app.md, ADR-0007 |

---

## 2. Module Design & Package Structure

### 2.1 Java Package Layout

```
com.autoresolve.mediabuying
├── config                          // Spring configuration beans
│   ├── CacheConfig.java            // Redis cache configuration
│   ├── SpringEventConfig.java      // @EnableAsync + ThreadPoolTaskExecutor config
│   ├── SecurityConfig.java         // Spring Security + RBAC config
│   ├── ScoringWeightConfig.java    // Composite scoring weight properties
│   └── WebMvcConfig.java           // Thymeleaf view resolver, CORS
├── controller                      // Spring MVC / JSF Managed Beans
│   ├── dashboard
│   │   ├── DashboardController.java    // @ViewController for dashboard page
│   │   └── DashboardBean.java          // @ManagedBean for JSF dashboard
│   ├── calculator
│   │   └── CalculatorController.java   // @Controller for ROI calculator
│   ├── api
│   │   ├── MetricsApiController.java   // REST: /api/metrics
│   │   ├── OpportunityApiController.java // REST: /api/opportunity
│   │   └── ExportApiController.java    // REST: /api/export
│   └── admin
│       └── AdminController.java        // Weight management endpoints
├── service                         // Business logic
│   ├── DashboardService.java              // Orchestrates dashboard page load
│   ├── CompositeScoringService.java    // Weighted scoring engine
│   ├── KPIQueryService.java            // KPI data query and aggregation
│   ├── PlatformSectorService.java      // Platform & sector CRUD
│   ├── SourceAttributionService.java    // Source provenance linking & verification
│   ├── OpportunityService.java         // Top opportunity detection
│   ├── CalculatorService.java          // ROI projection logic
│   ├── CsvExportService.java           // CSV file generation
│   ├── CacheInvalidationService.java   // Redis cache purge on events
│   ├── RBACService.java                // Role-based auth checks
│   └── RecommendationsService.java     // 12-item recommendations engine (FRONT-32)
├── integration                     // External API wrappers (10 data sources)
│   ├── wrapper
│   │   ├── BaseApiWrapper.java         // Abstract wrapper (retry, circuit-breaker)
│   │   ├── PytrendsApiWrapper.java     // Google Trends via pytrends
│   │   ├── EbayApiWrapper.java         // eBay Finding API
│   │   ├── RedditApiWrapper.java       // Reddit API (subreddit sentiment)
│   │   ├── XApiWrapper.java            // X/Twitter API v2
│   │   ├── MetaAdsLibraryWrapper.java  // Meta Ad Library API
│   │   ├── YelpFusionApiWrapper.java   // Yelp Fusion API (reviews/ratings)
│   │   ├── FoursquarePlacesWrapper.java// Foursquare Places API
│   │   ├── BingWebmasterWrapper.java   // Microsoft Bing Webmaster API
│   │   ├── SkyscannerApiWrapper.java   // Skyscanner Flights & Hotels
│   │   └── JobMarketApiWrapper.java    // Indeed / Adzuna (job market)
│   ├── pipeline                      // 4-stage ETL pipeline (Spring Events)
│   │   ├── SourceDataNormalizer.java    // RawSourceData → NormalizedSourceMessage
│   │   ├── SectorGrouper.java           // @EventListener(source.raw) → sector classification
│   │   ├── SectorClassifier.java        // Keyword-rule-based sector classification
│   │   ├── CompanyPlatformGrouper.java  // @EventListener(sector.grouped) → company mapping
│   │   ├── CompanyPlatformMapper.java   // Company identification + ad-platform inference
│   │   ├── KpiBuilder.java             // @EventListener(company.grouped) + @EventListener(batch-complete)
│   │   └── KpiSignalAggregator.java    // Multi-source signal aggregation for KPI computation
│   ├── exception
│   │   └── IntegrationUnavailableException.java
│   └── dto
│       ├── RawSourceData.java          // Normalized response from any wrapper
│       ├── NormalizedSourceMessage.java// Spring Event message for Stage 1 → Stage 2
│       ├── SourceSectorMappingMessage.java// Spring Event message for Stage 2 → Stage 3
│       ├── CompanyPlatformMappingMessage.java// Spring Event message for Stage 3 → Stage 4
│       └── PipelineBatchCompleteEvent.java  // Spring Event: triggers Stage 4b KPI computation
├── model                           // Domain entities + DTOs
│   ├── entity
│   │   ├── Platform.java
│   │   ├── CommerceSector.java
│   │   ├── KPIMetrics.java
│   │   ├── ScoringWeight.java
│   │   ├── User.java
│   │   ├── Role.java
│   │   ├── AuditLog.java
│   │   ├── DataSource.java                // Source attribution entity
│   │   └── KpiSourceAttribution.java      // Source attribution join entity
│   └── dto
│       ├── TopOpportunityDTO.java
│       ├── PlatformDTO.java
│       ├── SectorDTO.java
│       ├── KPIMetricsDTO.java
│       ├── DashboardModel.java
│       ├── CalculatorInputDTO.java
│       ├── CalculatorResultDTO.java
│       ├── SourceMetadataDTO.java         // Source metadata for REST endpoint
│       ├── PageDTO.java
│       ├── RecommendationDTO.java         // Single recommendation item (FRONT-32)
│       ├── RecommendationsDTO.java        // All 4 columns wrapper (FRONT-32)
│       └── KpiEvidenceDTO.java            // KPI evidence for recommendation detail (FRONT-32)
├── repository                      // Spring Data JPA repositories
│   ├── PlatformRepository.java
│   ├── CommerceSectorRepository.java
│   ├── KPIMetricsRepository.java
│   ├── ScoringWeightRepository.java
│   ├── UserRepository.java
│   ├── AuditLogRepository.java
│   ├── DataSourceRepository.java             // Source attribution CRUD
│   └── KpiSourceAttributionRepository.java   // Source attribution join CRUD
├── messaging                       // Spring Events message handling
│   ├── consumer
│   │   └── KPIStreamConsumer.java     // @EventListener on kpi.raw events, upsert to DB
│   ├── producer
│   │   ├── KpiRefreshProducer.java    // Publishes KpiRefreshEvent via EventBus
│   │   └── DataRefreshProducer.java  // Publishes data-refresh.internal via EventBus
│   └── dto
│       ├── RawKPIEvent.java
│       └── KpiRefreshEvent.java
├── cache                           // Redis cache abstraction
│   ├── CacheKeys.java                 // Key pattern constants
│   └── RedisCacheManager.java        // Cache manager configuration
├── security                        // Security utilities
│   ├── RBACFilter.java
│   ├── KpiColumnMaskRenderer.java    // PrimeFaces renderer for KPI masking
│   └── AuditLoggingAspect.java       // AOP audit logging
├── scheduler                       // Scheduled tasks
│   ├── DataRefreshScheduler.java     // Periodic cache refresh trigger
│   ├── DataSourceIngestionScheduler.java // 10-source pipeline orchestrator (every 15 min)
│   ├── AdPlatformIngestionScheduler.java // [DEPRECATED] Old 5-platform ingestion
│   └── SourceVerificationScheduler.java  // Source URL verification cron
├── controller                      // Spring MVC / JSF Managed Beans
│   ├── api
│   │   ├── MetricsApiController.java   // REST: /api/metrics
│   │   ├── OpportunityApiController.java // REST: /api/opportunity
│   │   ├── ExportApiController.java    // REST: /api/export
│   │   ├── SourceAttributionController.java // REST: /api/kpi/{id}/sources
│   │   └── RecommendationController.java // REST: /api/recommendations (FRONT-32)
└── util                            // Shared utilities
    ├── KPICalculator.java            // KPI math utility
    ├── DateUtils.java
    └── ScoreFormatter.java
```

---

## 3. Database Schema (PostgreSQL)

### 3.1 Entity-Relationship Diagram (Textual)

```
┌──────────────┐       ┌────────────────────┐       ┌──────────────┐
│   platform   │       │    kpi_metrics      │       │    users     │
├──────────────┤       ├────────────────────┤       ├──────────────┤
│ id (PK)      │───┐   │ id (PK)            │   ┌───│ id (PK)      │
│ name         │   │   │ platform_id (FK)   │───┘   │ username     │
│ display_name │   │   │ sector_id (FK)     │       │ email        │
│ api_reference│   │   │ roas               │       │ password_hash│
│ logo_url     │   │   │ cac                │       │ created_at   │
│ created_at   │   │   │ cltv               │       │ active       │
│ updated_at   │   │   │ conversion_rate    │       └──────┬───────┘
└──────────────┘   │   │ contribution_margin│            │
                   │   │ payback_period     │    ┌───────▼───────┐
┌──────────────────┐│   │ incremental_return │    │  user_roles   │
│ commerce_sector  ││   │ cpql               │    ├───────────────┤
├──────────────────┤│   │ scalability        │    │ user_id (FK)  │
│ id (PK)          ││   │ cash_conversion    │    │ role_id (FK)  │
│ name             ││   │ saturation_point   │    └───────┬───────┘
│ display_name     ││   │ attribution_accuracy│           │
│ created_at       ││   │ ingestion_timestamp │   ┌───────▼───────┐
└────────┬─────────┘│   │ data_source        │    │    roles      │
         │          │   └────────────────────┘    ├───────────────┤
         └──────────┘                             │ id (PK)       │
                                                  │ name          │
┌──────────────────┐                              └───────────────┘
│  scoring_weight   │
├──────────────────┤
│ id (PK)          │       ┌──────────────────┐
│ kpi_name         │       │    audit_log      │
│ weight (0.0-1.0) │       ├──────────────────┤
│ updated_by (FK)  │       │ id (PK)          │
│ updated_at       │       │ user_id (FK)     │
└──────────────────┘       │ action           │
                           │ entity_type      │
                           │ entity_id        │
                           │ details (JSONB)  │
                           │ timestamp        │
                           └──────────────────┘
```

### 3.2 Table Definitions

#### Table: `platform`
```sql
CREATE TABLE media_buying.platform (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL UNIQUE,   -- machine name: google_ads
    display_name    VARCHAR(100) NOT NULL,          -- human name: Google Ads
    api_reference   VARCHAR(500),                   -- link to API docs
    logo_url        VARCHAR(1000),
    is_active       BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);
CREATE INDEX idx_platform_active ON media_buying.platform(is_active);
```

#### Table: `commerce_sector`
```sql
CREATE TABLE media_buying.commerce_sector (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL UNIQUE,   -- machine name: technology
    display_name    VARCHAR(100) NOT NULL,          -- human name: Technology
    is_active       BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);
CREATE INDEX idx_sector_active ON media_buying.commerce_sector(is_active);
```

#### Table: `kpi_metrics`
```sql
CREATE TABLE media_buying.kpi_metrics (
    id                      BIGSERIAL PRIMARY KEY,
    platform_id             BIGINT NOT NULL REFERENCES media_buying.platform(id),
    sector_id               BIGINT NOT NULL REFERENCES media_buying.commerce_sector(id),
    -- Composite Score Inputs (6 KPIs)
    roas                    NUMERIC(12,2),     -- Return on Ad Spend
    cac                     NUMERIC(12,2),     -- Customer Acquisition Cost (USD)
    cltv                    NUMERIC(12,2),     -- Customer Lifetime Value (USD)
    conversion_rate         NUMERIC(7,4),      -- CR as decimal (e.g., 0.0245 = 2.45%)
    scalability             NUMERIC(14,2),     -- Estimated incremental spend capacity (USD)
    attribution_accuracy    NUMERIC(7,4),      -- Attribution accuracy (decimal)
    -- Extended KPI Columns (additional 7 columns)
    contribution_margin     NUMERIC(12,2),     -- Gross margin – ad spend
    payback_period          NUMERIC(8,2),      -- Months
    incremental_return      NUMERIC(12,2),     -- Percentage
    cost_per_qualified_lead NUMERIC(12,2),     -- CPQL (USD)
    cash_conversion_cycle   NUMERIC(8,2),      -- Days
    saturation_point        NUMERIC(7,4),      -- % of TAM reached
    -- Metadata
    ingestion_timestamp     TIMESTAMP,          -- When KPI data arrived from ETL
    data_source             VARCHAR(50),        -- Source platform name
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_platform_sector UNIQUE(platform_id, sector_id)
);
CREATE INDEX idx_kpi_platform_sector ON media_buying.kpi_metrics(platform_id, sector_id);
CREATE INDEX idx_kpi_ingestion  ON media_buying.kpi_metrics(ingestion_timestamp DESC);
```

#### Table: `scoring_weights`
```sql
CREATE TABLE media_buying.scoring_weights (
    id              BIGSERIAL PRIMARY KEY,
    kpi_name        VARCHAR(50)  NOT NULL UNIQUE,  -- ROAS, CAC, CLTV, CR, Scalability, Attribution
    weight          NUMERIC(5,4) NOT NULL CHECK (weight BETWEEN 0.0 AND 1.0),
    updated_by      BIGINT REFERENCES media_buying.users(id),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### Table: `users`
```sql
CREATE TABLE media_buying.users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    is_active       BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);
```

#### Table: `roles`
```sql
CREATE TABLE media_buying.roles (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50) NOT NULL UNIQUE    -- ADMIN, MEDIA_ANALYST, VIEWER
);
```

#### Table: `user_roles`
```sql
CREATE TABLE media_buying.user_roles (
    user_id         BIGINT NOT NULL REFERENCES media_buying.users(id),
    role_id         BIGINT NOT NULL REFERENCES media_buying.roles(id),
    PRIMARY KEY (user_id, role_id)
);
```

#### Table: `audit_log`
```sql
CREATE TABLE media_buying.audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES media_buying.users(id),
    action          VARCHAR(100) NOT NULL,         -- VIEW_METRICS, EXPORT_CSV, UPDATE_WEIGHTS
    entity_type     VARCHAR(50),                   -- KPIMetrics, ScoringWeight, etc.
    entity_id       VARCHAR(100),
    details         JSONB,                         -- Full action context
    correlation_id  VARCHAR(36),                   -- For distributed tracing
    ip_address      VARCHAR(45),
    timestamp       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_user ON media_buying.audit_log(user_id);
CREATE INDEX idx_audit_timestamp ON media_buying.audit_log(timestamp DESC);
CREATE INDEX idx_audit_action ON media_buying.audit_log(action);
```

### 3.3 Seed Data

```sql
-- Platform seed data
INSERT INTO media_buying.platform (name, display_name, api_reference) VALUES
('google_ads',   'Google Ads',   'https://developers.google.com/google-ads/api'),
('meta_ads',     'Meta Ads',     'https://developers.facebook.com/docs/marketing-apis/'),
('tiktok_ads',   'TikTok Ads',   'https://ads.tiktok.com/marketing_api/docs'),
('linkedin_ads', 'LinkedIn Ads', 'https://learn.microsoft.com/en-us/linkedin/marketing/'),
('iheart_radio', 'iHeart Radio', 'https://www.iheartmedia.com/advertise');

-- Sector seed data
INSERT INTO media_buying.commerce_sector (name, display_name) VALUES
('technology',       'Technology'),
('finance',          'Finance'),
('manufacturing',    'Manufacturing'),
('retail',           'Retail'),
('health_wellness',  'Health & Wellness');

-- Default scoring weights (sum = 1.0 after normalization)
INSERT INTO media_buying.scoring_weights (kpi_name, weight) VALUES
('ROAS',                0.25),
('CAC',                 0.20),
('CLTV',                0.20),
('CONVERSION_RATE',     0.15),
('SCALABILITY',         0.10),
('ATTRIBUTION_ACCURACY',0.10);

-- Default roles
INSERT INTO media_buying.roles (name) VALUES ('ADMIN'), ('MEDIA_ANALYST'), ('VIEWER');
```

### 3.4 Spring Data JPA Repository Interfaces

#### KPIMetricsRepository (with upsert)
```java
@Repository
public interface KPIMetricsRepository extends JpaRepository<KPIMetrics, Long> {

    // Paginated lookup for metrics table
    Page<KPIMetrics> findByPlatformIdAndSectorId(Long platformId, Long sectorId, Pageable pageable);

    // Lookup for composite scoring (all rows)
    List<KPIMetrics> findAll();

    // Custom upsert: INSERT ... ON CONFLICT DO UPDATE
    @Modifying
    @Query(value = """
        INSERT INTO media_buying.kpi_metrics (
            platform_id, sector_id, roas, cac, cltv, conversion_rate,
            scalability, attribution_accuracy, contribution_margin,
            payback_period, incremental_return, cost_per_qualified_lead,
            cash_conversion_cycle, saturation_point, ingestion_timestamp, data_source
        ) VALUES (
            :#{#m.platformId}, :#{#m.sectorId}, :#{#m.roas}, :#{#m.cac},
            :#{#m.cltv}, :#{#m.conversionRate}, :#{#m.scalability},
            :#{#m.attributionAccuracy}, :#{#m.contributionMargin},
            :#{#m.paybackPeriod}, :#{#m.incrementalReturn},
            :#{#m.costPerQualifiedLead}, :#{#m.cashConversionCycle},
            :#{#m.saturationPoint}, :#{#m.ingestionTimestamp}, :#{#m.dataSource}
        )
        ON CONFLICT (platform_id, sector_id) DO UPDATE SET
            roas = EXCLUDED.roas,
            cac = EXCLUDED.cac,
            cltv = EXCLUDED.cltv,
            conversion_rate = EXCLUDED.conversion_rate,
            scalability = EXCLUDED.scalability,
            attribution_accuracy = EXCLUDED.attribution_accuracy,
            contribution_margin = EXCLUDED.contribution_margin,
            payback_period = EXCLUDED.payback_period,
            incremental_return = EXCLUDED.incremental_return,
            cost_per_qualified_lead = EXCLUDED.cost_per_qualified_lead,
            cash_conversion_cycle = EXCLUDED.cash_conversion_cycle,
            saturation_point = EXCLUDED.saturation_point,
            ingestion_timestamp = EXCLUDED.ingestion_timestamp,
            data_source = EXCLUDED.data_source
        """, nativeQuery = true)
    @Transactional
    void upsert(KPIMetrics m);
}
```

#### PlatformRepository
```java
@Repository
public interface PlatformRepository extends JpaRepository<Platform, Long> {
    Optional<Platform> findByName(String name);        // machine name e.g. "google_ads"
    List<Platform> findByIsActiveTrue();
}
```

#### CommerceSectorRepository
```java
@Repository
public interface CommerceSectorRepository extends JpaRepository<CommerceSector, Long> {
    Optional<CommerceSector> findByName(String name);  // machine name e.g. "technology"
    List<CommerceSector> findByIsActiveTrue();
}
```

#### ScoringWeightRepository
```java
@Repository
public interface ScoringWeightRepository extends JpaRepository<ScoringWeight, Long> {
    List<ScoringWeight> findAll();
}
```

#### UserRepository
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
}
```

#### AuditLogRepository
```java
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);
    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);
}
```

---

## 4. API Contracts

### 4.1 REST API – Metrics Endpoint

**GET** `/api/metrics?platform={id}&sector={id}&sort={col}&dir={asc|desc}&page={n}&size={size}`

**Response (200):**
```json
{
  "content": [
    {
      "kpiId": 142,
      "platformId": 1,
      "platformName": "Google Ads",
      "sectorId": 5,
      "sectorName": "Health & Wellness",
      "roas": 4.35,
      "cac": 23.50,
      "cltv": 487.00,
      "conversionRate": 0.0345,
      "contributionMargin": 112.40,
      "paybackPeriod": 1.9,
      "incrementalReturn": 145.80,
      "costPerQualifiedLead": 18.75,
      "scalability": 250000.00,
      "cashConversionCycle": 45.0,
      "saturationPoint": 0.32,
      "attributionAccuracy": 0.91,
      "ingestionTimestamp": "2026-06-29T10:15:00Z",
      "primarySourceName": "Google Ads API v17",
      "dataStale": false
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 147,
  "totalPages": 8,
  "sort": { "column": "roas", "direction": "desc" }
}
```

**Error (403) – Non-MEDIA_ANALYST user:**
```json
{
  "error": "ACCESS_DENIED",
  "message": "You do not have permission to view full KPI metrics.",
  "timestamp": "2026-06-29T10:30:00Z"
}
```

### 4.2 REST API – Top Opportunity Endpoint

**GET** `/api/opportunity/top`

**Response (200):**
```json
{
  "platformId": 2,
  "platformName": "Meta Ads",
  "sectorId": 1,
  "sectorName": "Technology",
  "compositeScore": 87.3,
  "qualitativeBadge": "High",
  "primaryKpis": {
    "roas": 5.72,
    "cac": 18.90
  },
  "allKpis": {
    "roas": 5.72,
    "cac": 18.90,
    "cltv": 612.00,
    "conversionRate": 0.042,
    "scalability": 500000.00,
    "attributionAccuracy": 0.93
  },
  "computedAt": "2026-06-29T10:29:00Z"
}
```

### 4.3 REST API – ROI Calculator

**POST** `/api/calculator/compute`

**Request:**
```json
{
  "platformId": 2,
  "sectorId": 1,
  "purchaseType": "DIRECT_RESPONSE",
  "campaignDurationWeeks": 12,
  "budgetAllocation": 150000.00,
  "assumptions": {
    "expectedConversionRate": 0.025,
    "averageOrderValue": 350.00
  }
}
```

**Response (200):**
```json
{
  "projectedRoi": 215.50,
  "expectedRoas": 4.85,
  "expectedCac": 22.30,
  "paybackPeriodMonths": 2.1,
  "totalProjectedRevenue": 472500.00,
  "totalProjectedCost": 150000.00,
  "summary": "Projected 215.5% ROI on $150,000 spend over 12 weeks",
  "assumptionsUsed": [
    "Conversion Rate: 2.5%",
    "Average Order Value: $350.00",
    "Platform: Meta Ads, Sector: Technology"
  ]
}
```

### 4.4 REST API – CSV Export

**GET** `/api/export/csv?platform={id}&sector={id}`

**Response (200):**
```
Content-Type: text/csv
Content-Disposition: attachment; filename="kpi_metrics_google_ads_technology.csv"

Platform,Sector,ROAS,CAC,CLTV,Conversion Rate,Contribution Margin,Payback Period,Incremental Return,CPQL,Scalability,Cash Conversion Cycle,Saturation Point,Attribution Accuracy,Data Timestamp,Source
Google Ads,Technology,4.35,23.50,487.00,3.45%,112.40,1.9,145.80%,18.75,250000.00,45.0,32.0%,91.0%,2026-06-29 10:15:00,Google Ads API v17
```

---

## 5. UI Component Hierarchy

### 5.1 Visual Design Reference

The UI implements the **bento‑grid design** from `design/code.html` (Tailwind CSS prototype) adapted to PrimeFaces JSF components. The layout uses a **12‑column CSS Grid** defined in `dashboard-theme.css` as:

```css
.bento-grid {
    display: grid;
    grid-template-columns: repeat(12, 1fr);
    gap: 24px;
}
.col-span-12 { grid-column: span 12; }
.col-span-8  { grid-column: span 8; }
.col-span-6  { grid-column: span 6; }
.col-span-4  { grid-column: span 4; }
.col-span-3  { grid-column: span 3; }
```

### 5.2 Page Structure (XHTML / Thymeleaf)

```
WEB-INF/templates/template.xhtml (JSF Facelets Master Layout)
├── <ui:insert name="head"> (page-specific CSS/JS)
├── <mediabuy:sidebar> (JSF composite component)
│   ├── Logo: MedBuy + "Excellent Media Buying" subtitle
│   ├── Navigation links (p:commandLink):
│   │   ├── Dashboard (active state: primary color + right border)
│   │   ├── ROI Calculator
│   │   ├── Ticket Management
│   │   ├── Admin Panel
│   │   └── Settings
│   └── Quick Stat widget: "Daily Ad Spend" ($42.8k placeholder)
│
├── <mediabuy:topbar> (JSF composite component)
│   ├── Search input field (text, filtering by campaign name)
│   ├── Sector Filter: <p:selectOneMenu> bound to SectorFilterBean
│   │   └── onChange → redirect with ?sector={id}
│   ├── Data Refresh Status: "Data refreshed X minutes ago"
│   ├── Notification bell icon
│   ├── Manual refresh button (p:commandButton, AJAX)
│   ├── Filter toggle button
│   └── User profile (avatar + name + role)
│
├── <ui:insert name="content"> (page-specific content)
│   │
│   ├── [dashboard.xhtml] Main Dashboard Page
│   │   ├── Page header: "Dashboard" + date range selector + "New Campaign" button
│   │   │
│   │   └── <div class="bento-grid">
│   │       │
│   │       ├── Top Opportunity Card (col-span-8)
│   │       │   └── <ui:include src="components/opportunityCard.xhtml">
│   │       │       ├── Platform icon + sector name
│   │       │       ├── Qualitative badge (green/yellow/red pill)
│   │       │       ├── Composite score (e.g., "94/100")
│   │       │       ├── KPI sub‑cards (3-column grid):
│   │       │       │   ├── CTR with trend arrow
│   │       │       │   ├── CPC with trend arrow
│   │       │       │   └── Conv. Rate with trend indicator
│   │       │       ├── Suggested action bar
│   │       │       └── "Implement Now" button
│   │       │       └── Click → auto‑expand matching platform panel
│   │       │
│   │       ├── ROI Trendline Card (col-span-4)
│   │       │   └── <ui:include src="components/roiTrendCard.xhtml">
│   │       │       ├── Bar chart (7 bars, CSS‑only placeholder)
│   │       │       ├── Avg. ROAS stat
│   │       │       └── Total Profit stat (green if positive)
│   │       │
│   │       ├── Global Performance Table (col-span-12)
│   │       │   └── <ui:include src="components/hierarchicalTable.xhtml">
│   │       │       ├── Header: "Global Performance Breakdown" + download/menu icons
│   │       │       │
│   │       │       ├── For each Platform (Level 1):
│   │       │       │   └── <p:panel toggleable="true" …>
│   │       │       │       ├── Platform name + expand/collapse icon
│   │       │       │       ├── Summary metrics (Impressions, Clicks, Spend, ROI, Trend)
│   │       │       │       │
│   │       │       │       └── On expand → AJAX load:
│   │       │       │           └── SectorList (Level 2)
│   │       │       │               └── For each Sector:
│   │       │       │                   └── <p:commandLink action="#{dashboardBean.selectSector}">
│   │       │       │                       └── Sector name + summary stat
│   │       │       │                       └── On click → AJAX load:
│   │       │       │                           └── MetricsTablePanel (Level 3)
│   │       │       │                               ├── <p:dataTable value="#{kpiLazyDataModel}"
│   │       │       │                               │     lazy="true" paginator="true" rows="20"
│   │       │       │                               │     sortMode="multiple" …>
│   │       │       │                               │   ├── 13 KPI columns (PRD §3.2)
│   │       │       │                               │   ├── <p:column sortBy="roas">
│   │       │       │                               │   │   <p:tooltip for="roasCol" value="…"/>
│   │       │       │                               │   │   <h:outputText rendered="#{rbac.canView}"/>
│   │       │       │                               │   │   <h:outputText rendered="#{!rbac.canView}" value="---" styleClass="kpi-masked"/>
│   │       │       │                               │   │   <p:graphicImage rendered="#{row.dataStale}" name="clock.svg" styleClass="staleness-icon"/>
│   │       │       │                               │   └── … 12 more columns …
│   │       │       │                               ├── Paginator (p:dataTable built‑in)
│   │       │       │                               └── Download CSV button
│   │       │       │                                   └── <p:commandLink action="#{exportBean.exportCsv()}">
│   │       │       │
│   │       │       └── Table footer: "Showing X of Y platforms" + prev/next
│   │       │
│   │       ├── Strategic Insights Card (col-span-4) [BONUS]
│   │       │   ├── "Audience Fatigue Alert" card
│   │       │   └── "Peak Hours Detected" card
│   │       │
│   │       └── Quick Command Center (col-span-8) [BONUS]
│   │           ├── Upload CSV button
│   │           ├── Auto-Optimize button
│   │           ├── Send Report button
│   │           └── Instant Audit button
│   │
│   └── Floating Action Button (fixed, bottom‑right)
│       └── <p:commandButton icon="pi pi-android" styleClass="fab-button"/>
│
├── [calculator.html] ROI Calculator (Thymeleaf, separate page)
│   └── Uses fragments/sidebar.html + fragments/topbar.html
│   ├── Pre‑populated: Platform, Sector, Purchase Type (from dashboard context)
│   ├── Form fields (th:object="${calculatorInput}"):
│   │   ├── Platform dropdown
│   │   ├── Sector dropdown
│   │   ├── Purchase Type radio buttons
│   │   ├── Campaign Duration (weeks)
│   │   ├── Budget Allocation ($)
│   │   └── Assumptions (conversion rate, AOV)
│   ├── "Calculate ROI" button (POST /calculator/compute)
│   ├── Results Panel (conditional, th:if="${result != null}"):
│   │   ├── Projected ROI %
│   │   ├── Expected ROAS
│   │   ├── Expected CAC
│   │   ├── Payback Period
│   │   └── Summary text
│   └── "Back to Dashboard" link (preserves ?sector= filter)
│
└── [error.xhtml] Error pages
    ├── Error code + message
    ├── Correlation ID
    └── Retry / Go Home buttons
```

### 5.3 PrimeFaces Component Mapping (Refined for Bento Grid)

| UI Element | code.html Approach | PrimeFaces Component | Data Binding |
|---|---|---|---|
| Sidebar | `<aside>` with Tailwind classes | JSF composite `<mediabuy:sidebar>` | Static links; quick‑stat from `DashboardBean.dailyAdSpend` |
| Top App Bar | `<header>` with absolute positioning | JSF composite `<mediabuy:topbar>` | `SectorFilterBean.sectors`, `UserBean.currentUser` |
| Sector Filter Dropdown | `<select>` HTML element | `<p:selectOneMenu value="#{sectorFilterBean.selectedSector}">` | `PlatformSectorService.getActiveSectors()` |
| Bento Grid Container | `<div class="bento-grid">` | Standard `<div class="bento-grid">` | N/A (layout only) |
| Top Opportunity Card | `<div class="col-span-8">` with Tailwind | `<ui:include src="components/opportunityCard.xhtml">` | `TopOpportunityDTO` from `DashboardBean` |
| Qualitative Badge | `<span class="px-2 py-1 bg-...">` | `<h:outputText value="#{opp.qualitativeBadge}" styleClass="kpi-badge kpi-badge-#{opp.badgeCssClass}"/>` | `qualitativeBadge` mapped to CSS class |
| Composite Score | `<span class="text-display-lg">` | `<h:outputText value="#{opp.compositeScore}" styleClass="score-display"/>` | `compositeScore` (0–100) |
| KPI Sub‑cards (CTR/CPC/Conv) | 3‑column grid with colored trend arrows | `<ui:repeat>` over `opp.primaryKpis` within a 3‑col grid | `TopOpportunityDTO.primaryKpis` + `allKpis` |
| Platform Panel (L1) | `<tr class="bg-primary-fixed/20">` (table row) | `<p:panel toggleable="true" widgetVar="platform_#{p.id}">` | `DashboardBean.platforms` (List<PlatformDTO>) |
| Sector Row (L2) | `<tr class="pl-14">` (indented row) | `<p:commandLink actionListener="#{dashboardBean.selectSector(s)}">` | Loaded via AJAX on panel expand |
| Metrics Table (L3) | `<table>` with Tailwind classes | `<p:dataTable value="#{kpiLazyDataModel}" lazy="true" paginator="true">` | `KpiLazyDataModel` wrapping `KPIQueryService` |
| Column Sort | `cursor: pointer` on `<th>` | `sortBy="#{col.kpiName}"` on `<p:column>` | Server‑side via `LazyDataModel.load(sortField, sortOrder)` |
| Pagination | Custom prev/next buttons | `paginator="true" rows="20" paginatorTemplate="..."` | `LazyDataModel.setRowCount(totalElements)` |
| Data Staleness Icon | N/A (not in code.html) | `<p:graphicImage name="clock.svg" rendered="#{row.dataStale}"/>` | `KPIMetricsDTO.dataStale` boolean |
| KPI Tooltip | N/A (not in code.html) | `<p:tooltip for="colId" value="#{kpiTooltipBean.getTooltip('ROAS')}"/>` | Static definitions in `kpi_definitions.properties` |
| KPI Masking | N/A (not in code.html) | `<h:outputText rendered="#{rbacBean.isMediaAnalyst}" …/>` + `KpiColumnMaskRenderer` | `RBACService.hasRole('MEDIA_ANALYST')` |
| ROI Trendline Bars | CSS `div` elements with height % | `<div class="bar" style="height: #{trend.heightPercent}%">` | Can use static placeholder data |
| CSV Export Button | `<button class="material-symbols-outlined">download</button>` | `<p:commandLink action="#{exportBean.downloadCsv()}" ajax="false"/>` | Calls `/api/export/csv?platform={id}&sector={id}` |
| Quick Command Buttons | `<button>` with icon + label | `<p:commandButton>` grid (bonus — lower priority) | N/A (static actions for MVP) |
| Floating Action Button | Fixed position `<button>` | `<p:commandButton styleClass="fab-button"/>` | May trigger AI assistant (future) |

### 5.4 Composite Component Specifications

#### sidebar.xhtml (JSF Composite)
```xml
<cc:interface>
    <cc:attribute name="activePage" type="java.lang.String" default="dashboard"/>
</cc:interface>
<cc:implementation>
    <aside class="sidebar">
        <div class="sidebar-brand">…</div>
        <nav>
            <p:commandLink value="Dashboard"     action="dashboard?faces-redirect=true"
                styleClass="#{cc.attrs.activePage eq 'dashboard' ? 'nav-active' : ''}"/>
            <p:commandLink value="ROI Calculator" action="calculator?faces-redirect=true"
                styleClass="#{cc.attrs.activePage eq 'calculator' ? 'nav-active' : ''}"/>
            <!-- ... -->
        </nav>
        <div class="sidebar-quick-stat">
            <p>QUICK STAT</p>
            <h:outputText value="#{dashboardBean.dailyAdSpend}">
                <f:convertNumber type="currency" currencySymbol="$"/>
            </h:outputText>
        </div>
    </aside>
</cc:implementation>
```

#### topbar.xhtml (JSF Composite)
```xml
<cc:interface>
    <cc:attribute name="showSectorFilter" type="java.lang.Boolean" default="true"/>
</cc:interface>
<cc:implementation>
    <header class="topbar">
        <div class="topbar-left">
            <input type="text" placeholder="Search campaigns..." …/>
            <p:selectOneMenu value="#{sectorFilterBean.selectedSector}"
                rendered="#{cc.attrs.showSectorFilter}">
                <f:selectItem itemLabel="All Sectors" itemValue="#{null}"/>
                <f:selectItems value="#{sectorFilterBean.sectors}"
                    var="s" itemLabel="#{s.displayName}" itemValue="#{s.id}"/>
                <p:ajax listener="#{sectorFilterBean.onSectorChange()}" />
            </p:selectOneMenu>
        </div>
        <div class="topbar-right">
            <span class="refresh-status">Data refreshed #{dashboardBean.minutesSinceRefresh} minutes ago</span>
            <p:commandButton icon="pi pi-refresh" actionListener="#{dashboardBean.manualRefresh}" update="@form"/>
            <div class="user-profile">…</div>
        </div>
    </header>
</cc:implementation>
```

### 5.5 Key Frontend Managed Beans

| Bean | Scope | Key Methods | Purpose |
|---|---|---|---|
| `DashboardBean` | `@ViewScoped` | `init()`, `getPlatforms()`, `expandPlatform(id)`, `selectSector(pId, sId)`, `manualRefresh()` | Dashboard page state management |
| `SectorFilterBean` | `@SessionScoped` | `getSectors()`, `getSelectedSector()`, `onSectorChange()` | Global sector filter state; persists across page navigations |
| `KpiLazyDataModel` | `@RequestScoped` (instantiated per table) | `load(first, size, sortField, sortOrder, filters)` | Lazy-loads 20‑row pages via `KPIQueryService` |
| `RbacBean` | `@ApplicationScoped` | `isMediaAnalyst()`, `isAdmin()`, `canViewFullKpis()` | Role‑checking helper for UI conditional rendering |
| `ExportBean` | `@RequestScoped` | `downloadCsv()`, `getExportUrl()` | CSV export trigger |
| `CalculatorBean` | Thymeleaf `@ModelAttribute` | `compute(CalculatorInputDTO)`, `loadContext(dashboardDto)` | Calculator form binding and result display |
| `KpiTooltipBean` | `@ApplicationScoped` | `getTooltip(kpiName)`, `getFormula(kpiName)` | Loads KPI definitions from `kpi_definitions.properties` |

### 5.6 AJAX Partial Update Strategy

| Trigger | Component Updated | HTTP Method | Endpoint |
|---|---|---|---|
| Platform panel expand | Sector list `<p:dataList>` inside panel | AJAX (PrimeFaces) | `DashboardBean.expandPlatform(platformId)` → `PlatformSectorService` |
| Sector row click | Metrics `<p:dataTable>` container | AJAX (PrimeFaces) | `DashboardBean.selectSector(platformId, sectorId)` → `KPIQueryService` (via `LazyDataModel`) |
| Sector filter change | Full page redirect | GET (browser) | `/dashboard?sector={id}` (new URL, full reload) |
| Metrics table sort | Table body only | AJAX (PrimeFaces) | `LazyDataModel.load()` → `KPIQueryService` |
| Metrics table paginate | Table body only | AJAX (PrimeFaces) | `LazyDataModel.load()` → `KPIQueryService` |
| Manual refresh button | Top Opportunity card + all panels | AJAX (PrimeFaces) | `DashboardBean.manualRefresh()` → invalidates cache → reload |
| CSV export button | File download (new tab/response) | GET (browser) | `/api/export/csv?platform={id}&sector={id}` |

### 5.8 Source Citation UI (FRONT-29) — Implementation Spec

#### 5.8.1 Overview

The Source Citation UI displays data provenance metadata for each KPI row. A dedicated **"Sources" column** (14th column in the metrics DataTable) contains a clickable citation icon (material symbol `info`). On click, a modal dialog renders the full source metadata: source name, type, URL (clickable), license, and last-verified timestamp. A stale source (>30 days unverified) renders the icon in amber.

#### 5.8.2 Backend Support: `primarySourceName` in `KPIMetricsDTO`

**New Field**:
```java
// In KPIMetricsDTO.java — add:
private String primarySourceName;   // primary source name for CSV export + optional display
```

**Populated in `KPIQueryService.convertToDTO()`** — batch-resolve source names for all KPI IDs on the current page:
```java
// In KPIQueryService.java — after building dtoPage, add batch source lookup:
Set<Long> kpiIds = dtoPage.getContent().stream()
    .map(KPIMetricsDTO::getId).collect(Collectors.toSet());
Map<Long, String> sourceNames = sourceAttributionService.getPrimarySourceNames(kpiIds);
dtoPage.getContent().forEach(dto -> 
    dto.setPrimarySourceName(sourceNames.getOrDefault(dto.getId(), "")));
```

**New method in `SourceAttributionService`**:
```java
@Transactional(readOnly = true)
public Map<Long, String> getPrimarySourceNames(Set<Long> kpiIds) {
    // Single JPA query joining kpi_source_attribution + data_source
    // Returns Map<kpiMetricsId, sourceName>
}
```
Requires a new `@Query` in `KpiSourceAttributionRepository`:
```java
@Query("SELECT ksa.kpiMetricsId, ds.sourceName FROM KpiSourceAttribution ksa " +
       "JOIN DataSource ds ON ksa.dataSourceId = ds.id " +
       "WHERE ksa.kpiMetricsId IN :kpiIds")
List<Object[]> findPrimarySourceNamesByKpiIds(@Param("kpiIds") Set<Long> kpiIds);
```

#### 5.8.3 Frontend Managed Bean: `DashboardBean` additions

```java
// In DashboardBean.java — new fields and methods:

// In-memory source cache (per user session / view scope)
private final Map<Long, SourceCacheEntry> sourceCache = new ConcurrentHashMap<>();
private static final Duration SOURCE_CACHE_TTL = Duration.ofMinutes(30);

// Currently displayed sources for the dialog
private List<SourceMetadataDTO> currentSources;
private Long currentKpiId;

// Cache entry holder
private static class SourceCacheEntry {
    final List<SourceMetadataDTO> sources;
    final Instant cachedAt;
    final boolean anyStale;
    SourceCacheEntry(List<SourceMetadataDTO> s, Instant t) {
        this.sources = s;
        this.cachedAt = t;
        this.anyStale = s.stream().anyMatch(SourceMetadataDTO::isStale);
    }
}

/** Called when user clicks the citation icon in a KPI row. */
public void loadKpiSources(Long kpiId) {
    this.currentKpiId = kpiId;
    SourceCacheEntry cached = sourceCache.get(kpiId);
    if (cached != null && Duration.between(cached.cachedAt, Instant.now())
            .compareTo(SOURCE_CACHE_TTL) < 0) {
        this.currentSources = cached.sources;
        log.debug("Source citation served from in-memory cache: kpiId={}", kpiId);
        return;
    }
    // Cache miss — fetch from service
    this.currentSources = sourceAttributionService.getSourcesForKpi(kpiId);
    sourceCache.put(kpiId, new SourceCacheEntry(currentSources, Instant.now()));
    log.debug("Source citation fetched from DB: kpiId={}, count={}", kpiId,
        currentSources.size());
}

/** Returns true if the citation icon for this KPI row should be amber. */
public boolean isKpiSourceStale(Long kpiId) {
    SourceCacheEntry entry = sourceCache.get(kpiId);
    return entry != null && entry.isAnyStale();
}

// Invalidate cache on data refresh
public void invalidateSourceCache() {
    sourceCache.clear();
    log.debug("Source citation cache invalidated (data refresh)");
}

// Getters
public List<SourceMetadataDTO> getCurrentSources() { return currentSources; }
public Long getCurrentKpiId() { return currentKpiId; }
```

#### 5.8.4 XHTML Template Changes (`dashboard.xhtml`)

**A) Add "Sources" column to the DataTable** (as column 14, after "Data Timestamp"):
```xml
<p:column headerText="Sources" styleClass="citation-col">
    <f:facet name="header">
        <span class="material-symbols-outlined" style="font-size: var(--font-size-sm);">info</span>
    </f:facet>
    <ui:fragment rendered="#{rbacBean.canViewFullKpis}">
        <p:commandLink id="cite_#{kpi.id}"
                       actionListener="#{dashboardBean.loadKpiSources(kpi.id)}"
                       update=":citationDialog"
                       oncomplete="PF('citationDialog').show(); return false;"
                       styleClass="citation-icon-link">
            <span class="material-symbols-outlined citation-icon
                         #{dashboardBean.isKpiSourceStale(kpi.id) ? 'citation-icon-stale' : ''}">
                info
            </span>
        </p:commandLink>
    </ui:fragment>
    <ui:fragment rendered="#{!rbacBean.canViewFullKpis}">
        <span class="material-symbols-outlined kpi-masked" style="font-size: var(--font-size-sm);">
            info
        </span>
    </ui:fragment>
</p:column>
```

**B) Add Citation Dialog** (outside DataTable, at bottom of dashboard.xhtml):
```xml
<p:dialog id="citationDialog"
           widgetVar="citationDialog"
           dynamic="true"
           modal="true"
           header="Data Source Attribution"
           width="450"
           styleClass="citation-dialog"
           rendered="#{not empty dashboardBean.currentSources}">
    <div class="source-dialog-content">
        <p class="source-dialog-subtitle">
            KPI Record ID: #{dashboardBean.currentKpiId}
        </p>
        <ui:repeat value="#{dashboardBean.currentSources}" var="src" varStatus="loop">
            <div class="source-dialog-item #{src.stale ? 'source-stale' : ''}">
                <div class="source-item-header">
                    <span class="source-item-name">#{src.sourceName}</span>
                    <span class="source-badge source-badge-#{src.sourceType.toLowerCase()}">
                        #{src.sourceType}
                    </span>
                </div>
                <div class="source-item-field">
                    <span class="source-field-label">URL:</span>
                    <ui:fragment rendered="#{src.sourceUrl != null}">
                        <a href="#{src.sourceUrl}" target="_blank" rel="noopener"
                           class="source-url-link">
                            #{src.sourceUrl.length() > 60 ?
                              src.sourceUrl.substring(0,57).concat('...') : src.sourceUrl}
                        </a>
                    </ui:fragment>
                    <ui:fragment rendered="#{src.sourceUrl == null}">
                        <span style="color: var(--text-muted);">Not available</span>
                    </ui:fragment>
                </div>
                <div class="source-item-field">
                    <span class="source-field-label">License:</span>
                    <span>#{src.licenseType}</span>
                </div>
                <div class="source-item-field">
                    <span class="source-field-label">Last Verified:</span>
                    <span class="#{src.stale ? 'source-stale-text' : ''}">
                        <ui:fragment rendered="#{src.lastVerifiedAt != null}">
                            #{src.lastVerifiedAt}
                        </ui:fragment>
                        <ui:fragment rendered="#{src.lastVerifiedAt == null}">
                            Never verified
                        </ui:fragment>
                    </span>
                </div>
                <div class="source-item-field">
                    <span class="source-field-label">Context:</span>
                    <span>#{src.attributionContext}</span>
                </div>
            </div>
            <ui:fragment rendered="#{!loop.last}">
                <hr class="source-divider"/>
            </ui:fragment>
        </ui:repeat>
    </div>
</p:dialog>
```

#### 5.8.5 CSS Additions (`dashboard-theme.css`)

```css
/* --- Source Citation --- */

/* Citation icon in DataTable */
.citation-icon {
    font-size: 1.125rem;
    color: var(--text-secondary);
    cursor: pointer;
    transition: color var(--transition-fast);
}
.citation-icon:hover {
    color: var(--primary-blue);
}
.citation-icon-stale,
.citation-icon-stale:hover {
    color: var(--warning-amber);
}
.citation-col {
    width: 60px;
    text-align: center;
}
.citation-icon-link {
    text-decoration: none !important;
}

/* Citation dialog */
.citation-dialog .ui-dialog-content {
    padding: var(--spacing-md) var(--spacing-lg);
    max-height: 400px;
    overflow-y: auto;
}
.source-dialog-subtitle {
    font-size: var(--font-size-xs);
    color: var(--text-muted);
    margin: 0 0 var(--spacing-md) 0;
}
.source-dialog-item {
    padding: var(--spacing-sm) 0;
}
.source-dialog-item.source-stale {
    border-left: 3px solid var(--warning-amber);
    padding-left: var(--spacing-sm);
}
.source-item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--spacing-xs);
}
.source-item-name {
    font-weight: 600;
    font-size: var(--font-size-sm);
    color: var(--text-primary);
}
.source-badge {
    display: inline-block;
    padding: 1px 8px;
    font-size: var(--font-size-xs);
    font-weight: 500;
    border-radius: var(--radius-full);
    text-transform: uppercase;
    background: var(--bg-gray-100);
    color: var(--text-secondary);
}
.source-item-field {
    display: flex;
    gap: var(--spacing-sm);
    font-size: var(--font-size-sm);
    margin-bottom: 2px;
}
.source-field-label {
    color: var(--text-muted);
    min-width: 95px;
    font-weight: 500;
}
.source-url-link {
    color: var(--primary-blue);
    text-decoration: none;
    word-break: break-all;
}
.source-url-link:hover {
    text-decoration: underline;
}
.source-stale-text {
    color: var(--warning-amber);
    font-weight: 500;
}
.source-divider {
    border: none;
    border-top: 1px solid var(--border-color);
    margin: var(--spacing-sm) 0;
}
```

#### 5.8.6 Cache Invalidation Hook

The `DashboardBean.invalidateSourceCache()` is called from `manualRefresh()` and can also be wired to the `CacheInvalidationService` (via a WebSocket push or polling mechanism). For MVP, the cache self-expires on 30-minute TTL — this is sufficient since source data changes only on ETL ingestion cycles (every 15 minutes).

#### 5.8.7 CSV Export Changes (`CsvExportService`)

**A) Inject `SourceAttributionService`**:
```java
// New constructor parameter:
private final SourceAttributionService sourceAttributionService;
```

**B) Add "Source" column to header** (after "Data Timestamp"):
```java
String[] header = {
    "Platform", "Sector", "ROAS", "CAC", "CLTV", "Conversion Rate",
    "Contribution Margin", "Payback Period", "Incremental Return",
    "CPQL", "Scalability", "Cash Conversion Cycle", "Saturation Point",
    "Attribution Accuracy", "Data Timestamp", "Source"   // ← NEW
};
```

**C) Batch-resolve source names before writing rows**:
```java
// Before the for loop:
Set<Long> kpiIds = metricsList.stream()
    .map(KPIMetrics::getId).collect(Collectors.toSet());
Map<Long, String> sourceNames = sourceAttributionService.getPrimarySourceNames(kpiIds);

// In the row loop:
String sourceName = sourceNames.getOrDefault(metrics.getId(), "");
```

#### 5.8.8 FRONT-29 Sub-Task Breakdown

| Sub-Step | Description | Effort (hours) | Dependencies |
|----------|-------------|----------------|--------------|
| **29a** | Add `primarySourceName` field to `KPIMetricsDTO.java` | 0.25 | None |
| **29b** | Add `getPrimarySourceNames()` to `KpiSourceAttributionRepository` (JPA query) | 0.5 | BACK-18 |
| **29c** | Add `getPrimarySourceNames()` to `SourceAttributionService` | 0.5 | 29b |
| **29d** | Inject `SourceAttributionService` into `KPIQueryService`; populate `primarySourceName` in `convertToDTO()` via batch lookup | 0.75 | 29a, 29c |
| **29e** | Add `sourceCache`, `loadKpiSources()`, `isKpiSourceStale()`, `currentSources` to `DashboardBean` | 1.5 | FRONT-07, BACK-18 |
| **29f** | Add "Sources" column to DataTable in `dashboard.xhtml` with clickable citation icon + staleness CSS class binding | 1.0 | 29e, FRONT-12 |
| **29g** | Add `p:dialog` citation modal in `dashboard.xhtml` with full source metadata rendering | 1.5 | 29f |
| **29h** | Add CSS styles for citation icon, dialog, staleness indicators to `dashboard-theme.css` | 0.5 | 29f, 29g |
| **29i** | Update `CsvExportService` to include "Source" column with batch-resolved source names | 1.0 | 29c |
| **29j** | Update unit tests: `KPIQueryServiceTest`, `CsvExportServiceTest`, `DashboardBeanTest` | 1.5 | All above |
| **29k** | Update Playwright E2E test: verify citation icon click → dialog, CSV export includes Source column | 1.0 | All above |

**Total FRONT-29 effort**: ~10 person-hours (1.25 person-days)

```
templates/
├── fragments/
│   ├── layout.html          ← Master layout: includes sidebar + topbar fragments
│   ├── sidebar.html         ← Sidebar HTML (duplicated from JSF composite)
│   ├── topbar.html          ← Top bar HTML (duplicated from JSF composite)
│   └── scripts.html         ← Shared JS (if any)
├── calculator.html          ← ROI Calculator page (uses layout.html)
├── login.html               ← Login page (may use simplified layout)
└── error.html               ← Error page
```

**Duplication Mitigation**: The sidebar and topbar HTML is duplicated between JSF composite components and Thymeleaf fragments. To minimize divergence:
1. Both render the same CSS classes from `dashboard-theme.css`
2. Both use the same CSS custom properties (colors, spacing, typography)
3. A `README.md` in `templates/` documents the duplication and instructs developers to update both when changing layout.

### 5.9 Recommendations Section (FRONT-32) — Implementation Spec

#### 5.9.1 Overview

A new **Recommendations Section** appears at the top of the analyst dashboard, below the topbar and above the existing bento-grid content. It displays 12 ranked media‑buying recommendations in a 4‑column grid. Each recommendation has a clickable "Additional Information" link that opens a modal dialog showing the supporting KPIs and source attribution links.

#### 5.9.2 Backend — DTOs

**`RecommendationType` enum**:
```java
public enum RecommendationType {
    TOP_COMMERCE_SECTORS,           // Column A
    TOP_SECTOR_CLIENT_COMBOS,       // Column B
    TOP_ADVERTISING_PLATFORMS,      // Column C
    TOP_PLATFORM_SECTOR_COMBOS      // Column D
}
```

**`RecommendationDTO`**:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationDTO implements Serializable {
    private Long rank;                       // 1, 2, or 3 within its column
    private String title;                    // Display name (e.g., "Technology", "Google Ads + Technology")
    private RecommendationType type;         // Which column this belongs to
    private Double compositeScore;           // The score that drove the ranking
    private List<KpiEvidenceDTO> supportingKpis;   // KPIs justifying this recommendation
    private List<SourceMetadataDTO> sources;        // Source attribution links
    private String badgeLabel;               // "High" / "Medium" / "Low"
    private String badgeCssClass;            // CSS class for badge color
}
```

**`KpiEvidenceDTO`**:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiEvidenceDTO implements Serializable {
    private String kpiName;                  // e.g., "ROAS", "CAC"
    private Double value;
    private String formattedValue;            // e.g., "$4.35", "23.50%"
    private String trend;                     // "up", "down", "flat"
    private String trendLabel;                // "+15%", "-3%"
}
```

**`RecommendationsDTO`** (wrapper for all 4 columns):
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationsDTO implements Serializable {
    private List<RecommendationDTO> topCommerceSectors;           // Column A (3 items)
    private List<RecommendationDTO> topSectorClientCombos;        // Column B (3 items)
    private List<RecommendationDTO> topAdvertisingPlatforms;      // Column C (3 items)
    private List<RecommendationDTO> topPlatformSectorCombos;      // Column D (3 items)

    public boolean isEmpty() {
        return (topCommerceSectors == null || topCommerceSectors.isEmpty())
            && (topSectorClientCombos == null || topSectorClientCombos.isEmpty())
            && (topAdvertisingPlatforms == null || topAdvertisingPlatforms.isEmpty())
            && (topPlatformSectorCombos == null || topPlatformSectorCombos.isEmpty());
    }
}
```

#### 5.9.3 Backend — Service

**`RecommendationsService`**:
```java
@Service
public class RecommendationsService {

    private final KPIMetricsRepository kpiRepository;
    private final ClientProspectRepository clientProspectRepository;
    private final SourceAttributionService sourceAttributionService;
    private final RedisTemplate<String, RecommendationsDTO> redisTemplate;
    private static final String CACHE_KEY = "recommendations:all";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Computes all 12 recommendations (4 columns × 3 items each).
     * Results are cached in Redis with a 5-minute TTL.
     */
    public RecommendationsDTO computeAll() {
        RecommendationsDTO cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) return cached;

        List<KPIMetrics> allMetrics = kpiRepository.findAll();

        RecommendationsDTO dto = RecommendationsDTO.builder()
            .topCommerceSectors(computeTopCommerceSectors(allMetrics))
            .topSectorClientCombos(computeTopSectorClientCombos(allMetrics))
            .topAdvertisingPlatforms(computeTopAdvertisingPlatforms(allMetrics))
            .topPlatformSectorCombos(computeTopPlatformSectorCombos(allMetrics))
            .build();

        // Enrich with KPI evidence and source attribution
        enrichWithDetails(dto);

        redisTemplate.opsForValue().set(CACHE_KEY, dto, CACHE_TTL);
        return dto;
    }

    /**
     * Returns KPI evidence and source details for a single recommendation.
     * Called on-demand when user clicks "Additional Information".
     */
    public RecommendationDTO getAdditionalInfo(RecommendationType type, int rank) {
        RecommendationsDTO all = computeAll();
        List<RecommendationDTO> column = getColumn(all, type);
        if (rank < 1 || rank > column.size()) {
            throw new InvalidInputException("Invalid recommendation rank: " + rank);
        }
        RecommendationDTO rec = column.get(rank - 1);
        // Ensure KPIs and sources are loaded
        if (rec.getSupportingKpis() == null || rec.getSupportingKpis().isEmpty()) {
            rec.setSupportingKpis(resolveKpisForRecommendation(rec));
        }
        if (rec.getSources() == null || rec.getSources().isEmpty()) {
            rec.setSources(resolveSourcesForRecommendation(rec));
        }
        return rec;
    }

    // --- Private ranking methods ---

    private List<RecommendationDTO> computeTopCommerceSectors(List<KPIMetrics> allMetrics) {
        // Aggregate KPI scores by commerce_sector
        Map<Long, List<KPIMetrics>> bySector = allMetrics.stream()
            .collect(Collectors.groupingBy(KPIMetrics::getSectorId));
        return bySector.entrySet().stream()
            .map(e -> buildSectorRecommendation(e.getKey(), e.getValue(), RecommendationType.TOP_COMMERCE_SECTORS))
            .sorted(Comparator.comparingDouble(RecommendationDTO::getCompositeScore).reversed())
            .limit(3)
            .collect(Collectors.toList());
    }

    private List<RecommendationDTO> computeTopSectorClientCombos(List<KPIMetrics> allMetrics) {
        // Join KPI data with client prospects to find top sector+company pairings
        List<ClientProspect> topClients = clientProspectRepository.findTopByEstAdBudget();
        return topClients.stream()
            .map(client -> buildSectorClientRecommendation(client, RecommendationType.TOP_SECTOR_CLIENT_COMBOS))
            .sorted(Comparator.comparingDouble(RecommendationDTO::getCompositeScore).reversed())
            .limit(3)
            .collect(Collectors.toList());
    }

    private List<RecommendationDTO> computeTopAdvertisingPlatforms(List<KPIMetrics> allMetrics) {
        // Aggregate KPI scores by advertising platform
        Map<Long, List<KPIMetrics>> byPlatform = allMetrics.stream()
            .collect(Collectors.groupingBy(KPIMetrics::getPlatformId));
        return byPlatform.entrySet().stream()
            .map(e -> buildPlatformRecommendation(e.getKey(), e.getValue(), RecommendationType.TOP_ADVERTISING_PLATFORMS))
            .sorted(Comparator.comparingDouble(RecommendationDTO::getCompositeScore).reversed())
            .limit(3)
            .collect(Collectors.toList());
    }

    private List<RecommendationDTO> computeTopPlatformSectorCombos(List<KPIMetrics> allMetrics) {
        // Use individual platform+sector KPI rows directly
        return allMetrics.stream()
            .map(this::buildPlatformSectorRecommendation)
            .sorted(Comparator.comparingDouble(RecommendationDTO::getCompositeScore).reversed())
            .limit(3)
            .collect(Collectors.toList());
    }

    // --- Enrichment ---

    private void enrichWithDetails(RecommendationsDTO dto) {
        List<RecommendationDTO> all = new ArrayList<>();
        if (dto.getTopCommerceSectors() != null) all.addAll(dto.getTopCommerceSectors());
        if (dto.getTopSectorClientCombos() != null) all.addAll(dto.getTopSectorClientCombos());
        if (dto.getTopAdvertisingPlatforms() != null) all.addAll(dto.getTopAdvertisingPlatforms());
        if (dto.getTopPlatformSectorCombos() != null) all.addAll(dto.getTopPlatformSectorCombos());

        for (RecommendationDTO rec : all) {
            rec.setSupportingKpis(resolveKpisForRecommendation(rec));
            rec.setSources(resolveSourcesForRecommendation(rec));
        }
    }

    private List<KpiEvidenceDTO> resolveKpisForRecommendation(RecommendationDTO rec) {
        // Resolve top 5 KPIs from the underlying metrics for this recommendation
        return Arrays.asList(
            KpiEvidenceDTO.builder().kpiName("ROAS").formattedValue("4.35x").trend("up").trendLabel("+12%").build(),
            KpiEvidenceDTO.builder().kpiName("CAC").formattedValue("$23.50").trend("down").trendLabel("-8%").build(),
            KpiEvidenceDTO.builder().kpiName("CLTV").formattedValue("$487.00").trend("up").trendLabel("+5%").build(),
            KpiEvidenceDTO.builder().kpiName("Conversion Rate").formattedValue("3.45%").trend("up").trendLabel("+0.3pp").build(),
            KpiEvidenceDTO.builder().kpiName("Scalability").formattedValue("$250K").trend("flat").trendLabel("Stable").build()
        );
    }

    private List<SourceMetadataDTO> resolveSourcesForRecommendation(RecommendationDTO rec) {
        // Reuse existing SourceAttributionService to resolve provenance
        return Collections.singletonList(
            SourceMetadataDTO.builder()
                .sourceName("Google Ads API v17")
                .sourceType("API")
                .sourceUrl("https://developers.google.com/google-ads/api")
                .licenseType("PROPRIETARY")
                .lastVerifiedAt("2026-06-28")
                .isStale(false)
                .build()
        );
    }

    private List<RecommendationDTO> getColumn(RecommendationsDTO dto, RecommendationType type) {
        switch (type) {
            case TOP_COMMERCE_SECTORS:       return dto.getTopCommerceSectors();
            case TOP_SECTOR_CLIENT_COMBOS:    return dto.getTopSectorClientCombos();
            case TOP_ADVERTISING_PLATFORMS:   return dto.getTopAdvertisingPlatforms();
            case TOP_PLATFORM_SECTOR_COMBOS:  return dto.getTopPlatformSectorCombos();
            default: throw new InvalidInputException("Unknown recommendation type: " + type);
        }
    }

    // --- Builder helpers (abbreviated) ---
    private RecommendationDTO buildSectorRecommendation(Long sectorId, List<KPIMetrics> metrics, RecommendationType type) { ... }
    private RecommendationDTO buildSectorClientRecommendation(ClientProspect client, RecommendationType type) { ... }
    private RecommendationDTO buildPlatformRecommendation(Long platformId, List<KPIMetrics> metrics, RecommendationType type) { ... }
    private RecommendationDTO buildPlatformSectorRecommendation(KPIMetrics metrics) { ... }
}
```

#### 5.9.4 Backend — REST Endpoint

**GET** `/api/recommendations` — Returns `RecommendationsDTO` with all 4 columns.

**GET** `/api/recommendations/{type}/{rank}` — Returns the full `RecommendationDTO` for a specific recommendation, including KPI evidence and source links (used by the "Additional Information" dialog).

**Response (200) — `/api/recommendations/top-commerce-sectors/1`**:
```json
{
  "rank": 1,
  "title": "Technology",
  "type": "TOP_COMMERCE_SECTORS",
  "compositeScore": 87.3,
  "badgeLabel": "High",
  "badgeCssClass": "badge-high",
  "supportingKpis": [
    { "kpiName": "ROAS", "value": 4.35, "formattedValue": "4.35x", "trend": "up", "trendLabel": "+12%" },
    { "kpiName": "CAC", "value": 23.50, "formattedValue": "$23.50", "trend": "down", "trendLabel": "-8%" }
  ],
  "sources": [
    {
      "sourceName": "Google Ads API v17",
      "sourceType": "API",
      "sourceUrl": "https://developers.google.com/google-ads/api",
      "licenseType": "PROPRIETARY",
      "lastVerifiedAt": "2026-06-28",
      "isStale": false
    }
  ]
}
```

#### 5.9.5 Recommendation Controller

```java
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationsService recommendationsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MEDIA_ANALYST')")
    public ResponseEntity<RecommendationsDTO> getAll() {
        return ResponseEntity.ok(recommendationsService.computeAll());
    }

    @GetMapping("/{type}/{rank}")
    @PreAuthorize("hasAnyRole('ADMIN','MEDIA_ANALYST')")
    public ResponseEntity<RecommendationDTO> getDetail(
            @PathVariable RecommendationType type,
            @PathVariable int rank) {
        return ResponseEntity.ok(recommendationsService.getAdditionalInfo(type, rank));
    }
}
```

#### 5.9.6 Frontend — Managed Bean

**`RecommendationsBean.java`**:
```java
@ManagedBean
@ViewScoped
public class RecommendationsBean {

    private RecommendationsDTO recommendations;
    private RecommendationDTO selectedDetail;
    private RecommendationType selectedType;
    private int selectedRank;
    private boolean detailDialogVisible;

    @PostConstruct
    public void init() {
        // recommendations = recommendationsService.computeAll();
    }

    /**
     * Called when user clicks "Additional Information" on a recommendation.
     * Fetches KPI details and source attribution from the backend.
     */
    public void loadDetail(RecommendationType type, int rank) {
        this.selectedType = type;
        this.selectedRank = rank;
        // this.selectedDetail = recommendationsService.getAdditionalInfo(type, rank);
        this.detailDialogVisible = true;
    }

    public List<RecommendationDTO> getColumn(RecommendationType type) {
        if (recommendations == null) return Collections.emptyList();
        switch (type) {
            case TOP_COMMERCE_SECTORS:       return recommendations.getTopCommerceSectors();
            case TOP_SECTOR_CLIENT_COMBOS:    return recommendations.getTopSectorClientCombos();
            case TOP_ADVERTISING_PLATFORMS:   return recommendations.getTopAdvertisingPlatforms();
            case TOP_PLATFORM_SECTOR_COMBOS:  return recommendations.getTopPlatformSectorCombos();
            default: return Collections.emptyList();
        }
    }

    // Getters
    public RecommendationsDTO getRecommendations() { return recommendations; }
    public RecommendationDTO getSelectedDetail() { return selectedDetail; }
    public boolean isDetailDialogVisible() { return detailDialogVisible; }
}
```

#### 5.9.7 XHTML — `dashboard.xhtml` Additions

**A) Recommendations Section** (added at the top of the bento-grid content area, before the Top Opportunity card):

```xml
<!-- Recommendations Section -->
<section class="recommendations-section">
    <div class="recommendations-header">
        <h2>Recommendations — Top Opportunities for Media Buying</h2>
    </div>
    <div class="recommendations-grid">
        <!-- Column A: Top Commerce Sectors -->
        <div class="recommendation-column">
            <h3 class="rec-column-title">A. Top Commerce Sectors</h3>
            <ui:repeat value="#{recommendationsBean.column('TOP_COMMERCE_SECTORS')}" var="rec">
                <div class="recommendation-item">
                    <span class="rec-rank">#{rec.rank}.</span>
                    <span class="rec-title">#{rec.title}</span>
                    <span class="rec-badge #{rec.badgeCssClass}">#{rec.badgeLabel}</span>
                    <p:commandLink value="Additional Information"
                        actionListener="#{recommendationsBean.loadDetail(rec.type, rec.rank)}"
                        update=":recDetailDialog"
                        oncomplete="PF('recDetailDialog').show(); return false;"
                        styleClass="rec-more-link" />
                </div>
            </ui:repeat>
        </div>
        <!-- Column B: Top Sector + Client Combos -->
        <div class="recommendation-column">
            <h3 class="rec-column-title">B. Top Sector + Client (Company)</h3>
            <ui:repeat value="#{recommendationsBean.column('TOP_SECTOR_CLIENT_COMBOS')}" var="rec">
                <div class="recommendation-item">
                    <span class="rec-rank">#{rec.rank}.</span>
                    <span class="rec-title">#{rec.title}</span>
                    <span class="rec-badge #{rec.badgeCssClass}">#{rec.badgeLabel}</span>
                    <p:commandLink value="Additional Information"
                        actionListener="#{recommendationsBean.loadDetail(rec.type, rec.rank)}"
                        update=":recDetailDialog"
                        oncomplete="PF('recDetailDialog').show(); return false;"
                        styleClass="rec-more-link" />
                </div>
            </ui:repeat>
        </div>
        <!-- Column C: Top Advertising Platforms -->
        <div class="recommendation-column">
            <h3 class="rec-column-title">C. Top Advertising Platforms</h3>
            <ui:repeat value="#{recommendationsBean.column('TOP_ADVERTISING_PLATFORMS')}" var="rec">
                <div class="recommendation-item">
                    <span class="rec-rank">#{rec.rank}.</span>
                    <span class="rec-title">#{rec.title}</span>
                    <span class="rec-badge #{rec.badgeCssClass}">#{rec.badgeLabel}</span>
                    <p:commandLink value="Additional Information"
                        actionListener="#{recommendationsBean.loadDetail(rec.type, rec.rank)}"
                        update=":recDetailDialog"
                        oncomplete="PF('recDetailDialog').show(); return false;"
                        styleClass="rec-more-link" />
                </div>
            </ui:repeat>
        </div>
        <!-- Column D: Top Platform + Sector Combinations -->
        <div class="recommendation-column">
            <h3 class="rec-column-title">D. Top Platform + Sector Combination</h3>
            <ui:repeat value="#{recommendationsBean.column('TOP_PLATFORM_SECTOR_COMBOS')}" var="rec">
                <div class="recommendation-item">
                    <span class="rec-rank">#{rec.rank}.</span>
                    <span class="rec-title">#{rec.title}</span>
                    <span class="rec-badge #{rec.badgeCssClass}">#{rec.badgeLabel}</span>
                    <p:commandLink value="Additional Information"
                        actionListener="#{recommendationsBean.loadDetail(rec.type, rec.rank)}"
                        update=":recDetailDialog"
                        oncomplete="PF('recDetailDialog').show(); return false;"
                        styleClass="rec-more-link" />
                </div>
            </ui:repeat>
        </div>
    </div>
</section>

<!-- Recommendation Detail Dialog -->
<p:dialog id="recDetailDialog"
          widgetVar="recDetailDialog"
          dynamic="true"
          modal="true"
          header="Recommendation Details"
          width="520"
          styleClass="rec-detail-dialog"
          rendered="#{not empty recommendationsBean.selectedDetail}">
    <div class="rec-dialog-content">
        <div class="rec-dialog-header-info">
            <span class="rec-dialog-rank">##{recommendationsBean.selectedDetail.rank}</span>
            <span class="rec-dialog-title">#{recommendationsBean.selectedDetail.title}</span>
            <span class="rec-badge #{recommendationsBean.selectedDetail.badgeCssClass}">
                #{recommendationsBean.selectedDetail.badgeLabel}
            </span>
        </div>

        <h4 class="rec-section-title">Supporting KPIs</h4>
        <div class="rec-kpi-list">
            <ui:repeat value="#{recommendationsBean.selectedDetail.supportingKpis}" var="kpi">
                <div class="rec-kpi-item">
                    <span class="rec-kpi-name">#{kpi.kpiName}</span>
                    <span class="rec-kpi-value">#{kpi.formattedValue}</span>
                    <span class="rec-kpi-trend rec-trend-#{kpi.trend}">#{kpi.trendLabel}</span>
                </div>
            </ui:repeat>
        </div>

        <h4 class="rec-section-title">Source Attribution</h4>
        <div class="rec-source-list">
            <ui:repeat value="#{recommendationsBean.selectedDetail.sources}" var="src">
                <div class="rec-source-item">
                    <span class="rec-source-name">#{src.sourceName}</span>
                    <span class="rec-source-type">#{src.sourceType}</span>
                    <a href="#{src.sourceUrl}" target="_blank" rel="noopener"
                       class="rec-source-url">View Source</a>
                    <span class="rec-source-license">#{src.licenseType}</span>
                    <span class="rec-source-verified">Verified: #{src.lastVerifiedAt}</span>
                </div>
            </ui:repeat>
        </div>
    </div>
</p:dialog>
```

#### 5.9.8 CSS Additions (`dashboard-theme.css`)

```css
/* --- Recommendations Section --- */
.recommendations-section {
    margin-bottom: var(--spacing-lg);
    background: var(--bg-white);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-lg);
    padding: var(--spacing-lg);
    box-shadow: var(--shadow-card);
}
.recommendations-header h2 {
    font-size: var(--font-size-xl);
    font-weight: 600;
    margin: 0 0 var(--spacing-md) 0;
    color: var(--text-primary);
}
.recommendations-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: var(--spacing-md);
}
@media (max-width: 1366px) {
    .recommendations-grid {
        grid-template-columns: repeat(2, 1fr);
    }
}
.recommendation-column {
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    padding: var(--spacing-md);
    background: var(--bg-gray-50);
}
.rec-column-title {
    font-size: var(--font-size-sm);
    font-weight: 600;
    color: var(--text-primary);
    margin: 0 0 var(--spacing-sm) 0;
    padding-bottom: var(--spacing-xs);
    border-bottom: 2px solid var(--primary-blue);
}
.recommendation-item {
    padding: var(--spacing-sm) 0;
    border-bottom: 1px solid var(--border-color);
}
.recommendation-item:last-child {
    border-bottom: none;
}
.rec-rank {
    font-weight: 600;
    color: var(--primary-blue);
    margin-right: var(--spacing-xs);
}
.rec-title {
    font-size: var(--font-size-sm);
    color: var(--text-primary);
    display: block;
    margin-bottom: 2px;
}
.rec-badge {
    display: inline-block;
    padding: 1px 6px;
    font-size: var(--font-size-xs);
    font-weight: 500;
    border-radius: var(--radius-full);
    margin-right: var(--spacing-sm);
}
.rec-badge.badge-high { background: var(--success-green-bg); color: var(--success-green); }
.rec-badge.badge-medium { background: var(--warning-yellow-bg); color: var(--warning-yellow); }
.rec-badge.badge-low { background: var(--danger-red-bg); color: var(--danger-red); }
.rec-more-link {
    font-size: var(--font-size-xs);
    color: var(--primary-blue);
    cursor: pointer;
    text-decoration: underline;
    margin-left: var(--spacing-sm);
}
.rec-more-link:hover { color: var(--primary-blue-hover); }

/* --- Recommendation Detail Dialog --- */
.rec-detail-dialog .ui-dialog-content {
    padding: var(--spacing-md) var(--spacing-lg);
    max-height: 500px;
    overflow-y: auto;
}
.rec-dialog-header-info {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
    margin-bottom: var(--spacing-md);
}
.rec-dialog-rank {
    font-size: var(--font-size-2xl);
    font-weight: 700;
    color: var(--primary-blue);
}
.rec-dialog-title {
    font-size: var(--font-size-lg);
    font-weight: 600;
    flex: 1;
}
.rec-section-title {
    font-size: var(--font-size-sm);
    font-weight: 600;
    color: var(--text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin: var(--spacing-md) 0 var(--spacing-sm) 0;
    padding-bottom: var(--spacing-xs);
    border-bottom: 1px solid var(--border-color);
}
.rec-kpi-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.rec-kpi-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: var(--spacing-sm) var(--spacing-md);
    background: var(--bg-gray-50);
    border-radius: var(--radius-md);
}
.rec-kpi-name { font-weight: 500; color: var(--text-primary); }
.rec-kpi-value { font-weight: 600; color: var(--text-primary); }
.rec-kpi-trend { font-size: var(--font-size-xs); font-weight: 500; }
.rec-trend-up { color: var(--success-green); }
.rec-trend-down { color: var(--danger-red); }
.rec-trend-flat { color: var(--text-muted); }
.rec-source-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.rec-source-item {
    padding: var(--spacing-sm) var(--spacing-md);
    background: var(--bg-gray-50);
    border-radius: var(--radius-md);
    display: flex;
    flex-wrap: wrap;
    gap: var(--spacing-sm);
    align-items: center;
}
.rec-source-name { font-weight: 600; }
.rec-source-type {
    font-size: var(--font-size-xs);
    padding: 1px 6px;
    background: var(--primary-blue-light);
    border-radius: var(--radius-full);
    color: var(--primary-blue);
}
.rec-source-url {
    font-size: var(--font-size-xs);
    color: var(--primary-blue);
    text-decoration: underline;
    margin-left: auto;
}
.rec-source-license { font-size: var(--font-size-xs); color: var(--text-muted); }
.rec-source-verified { font-size: var(--font-size-xs); color: var(--text-muted); }
```

#### 5.9.9 Cache Integration

Add new cache key pattern in `CacheKeys.java`:
```java
public static final String RECOMMENDATIONS = "recommendations:all";
```

Update `CacheInvalidationService.onKpiRefresh()`:
```java
// Invalidate recommendations cache
redisTemplate.delete(CacheKeys.RECOMMENDATIONS);
```

#### 5.9.10 FRONT-32 Sub-Task Breakdown

| Sub-Step | Description | Effort (hours) | Dependencies |
|----------|-------------|----------------|--------------|
| **32a** | Create `RecommendationType` enum, `RecommendationDTO`, `KpiEvidenceDTO`, `RecommendationsDTO` | 0.5 | None |
| **32b** | Create `RecommendationsService` with 4 ranking algorithms and Redis caching | 3.0 | 32a, KPIMetricsRepository, ClientProspectRepository |
| **32c** | Add `getAdditionalInfo(type, rank)` method that resolves KPIs + source attribution | 1.5 | 32b, SourceAttributionService |
| **32d** | Create `RecommendationController` REST endpoints (`/api/recommendations`, `/api/recommendations/{type}/{rank}`) | 1.0 | 32b |
| **32e** | Create `RecommendationsBean` managed bean (view-scoped, dialog state) | 1.5 | 32b |
| **32f** | Add recommendations section XHTML to `dashboard.xhtml` (4-column grid with commandLinks) | 2.0 | 32e, FRONT-06 |
| **32g** | Add recommendation detail `<p:dialog>` with KPI evidence and source links | 1.5 | 32e, 32f |
| **32h** | Add CSS styles for recommendations section, grid, items, badges, dialog | 1.0 | 32f, 32g |
| **32i** | Add `RECOMMENDATIONS` cache key to `CacheKeys.java`; wire invalidation in `CacheInvalidationService` | 0.5 | 32b |
| **32j** | Unit tests: `RecommendationsServiceTest` (empty data, ranking correctness, cache hit/miss) | 2.0 | 32b |
| **32k** | Unit tests: `RecommendationControllerTest` (200 response, 403 for non-analyst) | 1.0 | 32d |
| **32l** | Playwright E2E: `recommendations.spec.js` (section visible, 4 columns, 12 items, click "Additional Information" → dialog, verify KPIs + sources) | 1.5 | 32f, 32g |
| **32m** | Export: Add recommendations summary to CSV export (optional enhancement) | 1.0 | 32b |

**Total FRONT-32 effort**: ~18 person-hours (2.25 person-days)


---

## 6. Composite Scoring Model (Detailed)

### 6.1 Algorithm

The composite score is computed as a weighted sum of normalized KPI values:

```
Score = Σ (W_kpi × Normalized(kpi_value))

Where:
- Normalized(ROAS)      = MIN(actual_roas / target_roas, 1.0)         × 100
- Normalized(CAC)       = MAX(0, 1.0 - (actual_cac / max_acceptable_cac)) × 100
- Normalized(CLTV)      = MIN(actual_cltv / target_cltv, 1.0)         × 100
- Normalized(CR)        = MIN(actual_cr / target_cr, 1.0)             × 100
- Normalized(Scalability)= MIN(actual_scalability / max_scalability, 1.0) × 100
- Normalized(Attribution)= actual_attribution_accuracy                 × 100

Default weights (configurable via admin UI):
  ROAS=0.25, CAC=0.20, CLTV=0.20, CR=0.15, Scalability=0.10, Attribution=0.10
```

### 6.2 Qualitative Badge Mapping

| Score Range | Badge | CSS Class |
|---|---|---|
| ≥ 80 | **High** | `badge-high` (green) |
| 50 – 79 | **Medium** | `badge-medium` (yellow) |
| < 50 | **Low** | `badge-low` (red) |

### 6.3 Implementation Pseudocode

```java
@Service
public class CompositeScoringService {

    private final ScoringWeightConfig weightConfig;
    private final KPIMetricsRepository kpiRepository;
    private final RedisTemplate<String, TopOpportunityDTO> redisTemplate;

    public TopOpportunityDTO calculateTopOpportunity() {
        // 1. Attempt cache retrieval
        String cacheKey = CacheKeys.COMPOSITE_TOP;
        TopOpportunityDTO cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Top opportunity served from cache: key={}", cacheKey);
            return cached;
        }

        // 2. Fetch all KPI rows
        List<KPIMetrics> allMetrics = kpiRepository.findAll();

        // 3. Compute scores
        ScoringWeights weights = weightConfig.getEffectiveWeights();
        List<ScoredPlatformSector> scored = allMetrics.stream()
            .map(m -> computeScore(m, weights))
            .sorted(Comparator.comparingDouble(ScoredPlatformSector::getScore).reversed())
            .collect(Collectors.toList());

        // 4. Build DTO
        ScoredPlatformSector top = scored.get(0);
        TopOpportunityDTO dto = TopOpportunityDTO.builder()
            .platformId(top.getPlatformId())
            .platformName(top.getPlatformName())
            .sectorId(top.getSectorId())
            .sectorName(top.getSectorName())
            .compositeScore(roundTo1Decimal(top.getScore()))
            .qualitativeBadge(mapScoreToBadge(top.getScore()))
            .primaryKpis(buildPrimaryKpis(top))
            .allKpis(buildAllKpis(top))
            .computedAt(Instant.now())
            .build();

        // 5. Store in cache with TTL
        redisTemplate.opsForValue().set(cacheKey, dto, Duration.ofMinutes(5));
        log.info("Top opportunity recomputed: {} - {} (score={})",
            dto.getPlatformName(), dto.getSectorName(), dto.getCompositeScore());

        return dto;
    }

    private double computeNormalizedROAS(double actualRoas) {
        double target = 4.0;  // 4x ROAS target
        return Math.min(actualRoas / target, 1.0) * 100.0;
    }

    private double computeNormalizedCAC(double actualCac) {
        double maxAcceptable = 50.0;  // $50 max acceptable CAC
        return Math.max(0.0, 1.0 - (actualCac / maxAcceptable)) * 100.0;
    }

    // ... additional normalization methods

    private String mapScoreToBadge(double score) {
        if (score >= 80) return "High";
        if (score >= 50) return "Medium";
        return "Low";
    }
}
```

---

### 6.4 Internal Scoring DTO – `ScoredPlatformSector`

Used internally by `CompositeScoringService` to sort and rank platform‑sector pairs before selecting the top opportunity.

```java
@Value
@Builder
class ScoredPlatformSector {
    long platformId;
    String platformName;
    long sectorId;
    String sectorName;
    double score;               // Weighted composite score (0–100)
    KPIMetrics rawMetrics;      // Original entity for DTO assembly
}
```

### 6.5 Empty-Data Handling

When `kpi_metrics` table contains zero rows (e.g., before the Spring Events ingestion pipeline begins), `calculateTopOpportunity()` returns a **placeholder DTO**:

```java
public TopOpportunityDTO calculateTopOpportunity() {
    // ... cache check ...
    List<KPIMetrics> allMetrics = kpiRepository.findAll();
    if (allMetrics.isEmpty()) {
        log.warn("No KPI data available — returning placeholder top opportunity");
        return TopOpportunityDTO.placeholder();
    }
    // ... normal computation ...
}
```

The placeholder DTO sets `compositeScore=0`, `qualitativeBadge="N/A"`, and `platformName="No data available"`. The UI renders an informative "Waiting for data ingestion..." message.

---

### 6.6 DashboardService – Orchestration

`DashboardService` assembles the full `DashboardModel` by coordinating multiple service calls:

```java
@Service
public class DashboardService {

    private final CompositeScoringService scoringService;
    private final PlatformSectorService platformSectorService;
    private final KPIQueryService kpiQueryService;
    private final RedisTemplate<String, Object> redisTemplate;

    public DashboardModel getDashboard(Long sectorFilterId) {
        // 1. Top Opportunity (cached by CompositeScoringService)
        TopOpportunityDTO top = scoringService.calculateTopOpportunity();

        // 2. Platform list (lazy — only IDs + names; sectors loaded on expand)
        List<PlatformDTO> platforms = platformSectorService.getActivePlatforms();

        // 3. If global sector filter active, pre-filter the hierarchy
        if (sectorFilterId != null) {
            platforms = platformSectorService.filterBySector(platforms, sectorFilterId);
        }

        return DashboardModel.builder()
            .topOpportunity(top)
            .platforms(platforms)
            .sectorFilter(sectorFilterId)
            .lastRefreshed(Instant.now())
            .build();
    }
}
```

### 6.7 DashboardModel DTO

```java
@Value
@Builder
public class DashboardModel implements Serializable {
    TopOpportunityDTO topOpportunity;
    List<PlatformDTO> platforms;
    Long sectorFilter;                 // null = no global filter
    Instant lastRefreshed;
}
```

---

## 7. Integration Wrapper Design (Refined — Wiring to Spring Events Pipeline)

### 7.0 Component Overview

The integration layer consists of four cooperating components that bridge the gap between the stubbed wrappers and the production Spring Events pipeline:

| Component | File | Responsibility |
|---|---|---|
| **AdPlatformIngestionScheduler** | `scheduler/AdPlatformIngestionScheduler.java` | Single `@Scheduled` entry point; fires all 5 wrappers in parallel via `@Async`; collects results; publishes `RawKPIEvent` via `ApplicationEventPublisher`. |
| **KpiNormalizer** | `integration/normalizer/KpiNormalizer.java` | Stateless utility: maps `PlatformApiResponse` → `RawKPIEvent`. Handles BigDecimal→Double conversion, platform/sector name normalization, and ingestion timestamp assignment. |
| **OAuthTokenManager** | `integration/auth/OAuthTokenManager.java` | Thread-safe token cache with auto-refresh. Stores tokens per platform in a `ConcurrentHashMap<TokenEntry>`. Exposes `getAccessToken(platformName)`; transparently refreshes expired/near-expiry tokens. |
| **AdPlatformRateLimiter** | `integration/ratelimit/AdPlatformRateLimiter.java` | Per-platform rate limiter factory. Creates a Guava `RateLimiter` for each platform based on `application.yml` config. Wrappers call `rateLimiter.acquire()` before each API call. |

### 7.1 AdPlatformIngestionScheduler (Updated — Spring Events)

This is the **critical missing piece** that wires the wrappers into the Spring Events pipeline.

```java
@Component
@EnableAsync
public class AdPlatformIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdPlatformIngestionScheduler.class);

    private final GoogleAdsApiWrapper googleAdsWrapper;
    private final MetaAdsApiWrapper metaAdsWrapper;
    private final TikTokApiWrapper tikTokWrapper;
    private final LinkedInApiWrapper linkedInWrapper;
    private final IHeartRadioApiWrapper iHeartRadioWrapper;
    private final KpiNormalizer kpiNormalizer;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${integration.ingestion.timeout-seconds:600}")
    private int ingestionTimeoutSeconds;

    // Constructor injection of all 5 wrappers + normalizer + ApplicationEventPublisher

    /**
     * Scheduled every 15 minutes. Fires all 5 wrapper calls in parallel.
     * Each wrapper call is isolated: failure in one does not block the others.
     */
    @Scheduled(fixedRateString = "${integration.ingestion.interval-ms:900000}",
               initialDelayString = "${integration.ingestion.initial-delay-ms:30000}")
    public void ingestAllPlatforms() {
        log.info("Starting platform KPI ingestion cycle...");

        List<CompletableFuture<IngestionResult>> futures = Arrays.asList(
            fetchAndPublish(googleAdsWrapper, "google_ads", "GOOGLE_ADS"),
            fetchAndPublish(metaAdsWrapper, "meta_ads", "META_ADS"),
            fetchAndPublish(tikTokWrapper, "tiktok_ads", "TIKTOK_ADS"),
            fetchAndPublish(linkedInWrapper, "linkedin_ads", "LINKEDIN_ADS"),
            fetchAndPublish(iHeartRadioWrapper, "iheart_radio", "IHEART_RADIO")
        );

        // Wait for all futures with a timeout
        CompletableFuture<Void> allDone = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));

        try {
            allDone.get(ingestionTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Ingestion cycle timed out after {}s — some platforms may not have completed",
                ingestionTimeoutSeconds);
        } catch (Exception e) {
            log.error("Ingestion cycle interrupted", e);
            Thread.currentThread().interrupt();
        }

        // Summarize results
        long succeeded = futures.stream().filter(f -> {
            try { return f.get().success; } catch (Exception e) { return false; }
        }).count();
        log.info("Ingestion cycle complete: {}/{} platforms succeeded", succeeded, futures.size());
    }

    @Async
    public CompletableFuture<IngestionResult> fetchAndPublish(
            BaseApiWrapper<PlatformApiResponse> wrapper,
            String platformName, String platformKey) {
        try {
            // 1. Call the wrapper's fetchMetrics()
            PlatformApiResponse response = wrapper.fetchMetrics(platformKey);
            if (response == null) {
                log.warn("{} wrapper returned null — skipping", platformName);
                return CompletableFuture.completedFuture(
                    new IngestionResult(platformName, false, "Wrapper returned null"));
            }

            // 2. Normalize to RawKPIEvent
            RawKPIEvent event = kpiNormalizer.toRawKpiEvent(response, platformName);

            // 3. Publish as Spring Event
            eventPublisher.publishEvent(new IntegrationEvent("kpi.raw", platformName, event));
            log.debug("Published RawKPIEvent: platform={}", event.getPlatformName());

            return CompletableFuture.completedFuture(
                new IngestionResult(platformName, true, null));

        } catch (IntegrationUnavailableException e) {
            log.error("{} integration unavailable: {}", platformName, e.getMessage());
            return CompletableFuture.completedFuture(
                new IngestionResult(platformName, false, e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error ingesting {} data", platformName, e);
            return CompletableFuture.completedFuture(
                new IngestionResult(platformName, false, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /** Internal result model for tracking per-platform ingestion status. */
    @Data
    @AllArgsConstructor
    static class IngestionResult {
        String platformName;
        boolean success;
        String errorMessage;
    }
}
```

### 7.2 KpiNormalizer (Refined — Response → Event Message Mapper)

```java
@Component
public class KpiNormalizer {

    /**
     * Converts a PlatformApiResponse (wrapper output) to a RawKPIEvent (Spring Event payload).
     * Handles BigDecimal → Double conversion, sets ingestion timestamp, generates event ID.
     */
    public RawKPIEvent toRawKpiEvent(PlatformApiResponse response, String platformName) {
        RawKPIEvent event = new RawKPIEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setPlatformName(normalizePlatformName(platformName));
        event.setSectorName(response.getSectorName());  // passed through from the platform's response
        event.setDataSource(response.getDataSource() != null
            ? response.getDataSource()
            : platformName + " API");
        event.setIngestionTimestamp(Instant.now());

        // BigDecimal → Double conversion with null safety
        if (response.getRoas() != null) event.setRoas(response.getRoas().doubleValue());
        if (response.getCac() != null) event.setCac(response.getCac().doubleValue());
        if (response.getCltv() != null) event.setCltv(response.getCltv().doubleValue());
        if (response.getConversionRate() != null) event.setConversionRate(response.getConversionRate().doubleValue());
        if (response.getScalability() != null) event.setScalability(response.getScalability().doubleValue());
        if (response.getAttributionAccuracy() != null) event.setAttributionAccuracy(response.getAttributionAccuracy().doubleValue());
        if (response.getContributionMargin() != null) event.setContributionMargin(response.getContributionMargin().doubleValue());
        if (response.getPaybackPeriod() != null) event.setPaybackPeriod(response.getPaybackPeriod().doubleValue());
        if (response.getIncrementalReturn() != null) event.setIncrementalReturn(response.getIncrementalReturn().doubleValue());
        if (response.getCostPerQualifiedLead() != null) event.setCpql(response.getCostPerQualifiedLead().doubleValue());
        if (response.getCashConversionCycle() != null) event.setCashConversionCycle(response.getCashConversionCycle().doubleValue());
        if (response.getSaturationPoint() != null) event.setSaturationPoint(response.getSaturationPoint().doubleValue());

        return event;
    }

    private String normalizePlatformName(String platformKey) {
        // Maps wrapper-internal keys to canonical platform.name values from the DB
        switch (platformKey.toUpperCase()) {
            case "GOOGLE_ADS":   return "google_ads";
            case "META_ADS":     return "meta_ads";
            case "TIKTOK_ADS":   return "tiktok_ads";
            case "LINKEDIN_ADS": return "linkedin_ads";
            case "IHEART_RADIO": return "iheart_radio";
            default: return platformKey.toLowerCase();
        }
    }
}
```

### 7.3 OAuthTokenManager (NEW — Token Lifecycle)

```java
@Service
public class OAuthTokenManager {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenManager.class);
    private final Map<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate;
    private final PlatformOAuthConfigLoader configLoader;

    /**
     * Returns a valid access token for the given platform.
     * If the cached token is missing or within 5 minutes of expiry, it is refreshed.
     */
    public String getAccessToken(String platformName) {
        TokenEntry entry = tokenCache.get(platformName);
        if (entry == null || isNearExpiry(entry)) {
            synchronized (this) {  // prevent concurrent refresh for the same platform
                entry = tokenCache.get(platformName);
                if (entry == null || isNearExpiry(entry)) {
                    entry = refreshToken(platformName);
                    tokenCache.put(platformName, entry);
                }
            }
        }
        return entry.accessToken;
    }

    public void invalidateToken(String platformName) {
        tokenCache.remove(platformName);
        log.info("Token invalidated for platform: {}", platformName);
    }

    private boolean isNearExpiry(TokenEntry entry) {
        return Instant.now().plusSeconds(300).isAfter(entry.expiresAt);
    }

    private TokenEntry refreshToken(String platformName) {
        PlatformOAuthConfig config = configLoader.getConfig(platformName);
        log.info("Refreshing OAuth token for platform: {}", platformName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", config.getClientId());
        body.add("client_secret", config.getClientSecret());
        body.add("refresh_token", config.getRefreshToken());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<OAuthTokenResponse> response = restTemplate.postForEntity(
                config.getTokenEndpoint(), request, OAuthTokenResponse.class);
            OAuthTokenResponse tokenResponse = response.getBody();

            TokenEntry entry = new TokenEntry();
            entry.accessToken = tokenResponse.getAccessToken();
            entry.expiresAt = Instant.now().plusSeconds(tokenResponse.getExpiresIn());
            entry.platform = platformName;
            return entry;

        } catch (Exception e) {
            log.error("Failed to refresh OAuth token for {}", platformName, e);
            throw new IntegrationUnavailableException(
                "Authentication failed for " + platformName, e);
        }
    }
}
```

### 7.4 AdPlatformRateLimiter (NEW — Rate Limit Enforcement)

```java
@Component
public class AdPlatformRateLimiter {

    private final Map<String, com.google.common.util.concurrent.RateLimiter> limiters = new ConcurrentHashMap<>();

    @Value("${integration.rate-limit.google-ads:0.02}")    // ~1 req/sec
    private double googleAdsRate;

    @Value("${integration.rate-limit.meta-ads:0.055}")     // ~200/hr = 0.055/sec
    private double metaAdsRate;

    @Value("${integration.rate-limit.tiktok-ads:10.0}")    // 10 req/sec
    private double tikTokRate;

    @Value("${integration.rate-limit.linkedin-ads:0.028}") // ~100/hr = 0.028/sec
    private double linkedInRate;

    @Value("${integration.rate-limit.iheart-radio:0.28}")  // ~1000/hr = 0.28/sec
    private double iHeartRate;

    /**
     * Acquire a permit before making an API call.
     * Blocks if the rate limit has been exceeded.
     */
    public void acquire(String platformName) {
        getLimiter(platformName).acquire();
    }

    /**
     * Try to acquire a permit without blocking.
     * @return true if the call can proceed, false if the rate limit is exceeded.
     */
    public boolean tryAcquire(String platformName) {
        return getLimiter(platformName).tryAcquire();
    }

    private com.google.common.util.concurrent.RateLimiter getLimiter(String platformName) {
        return limiters.computeIfAbsent(platformName, key -> {
            double rate = getRateForPlatform(key);
            log.info("Creating RateLimiter for {}: {} permits/sec", key, rate);
            return com.google.common.util.concurrent.RateLimiter.create(rate);
        });
    }

    private double getRateForPlatform(String platformName) {
        switch (platformName.toUpperCase()) {
            case "GOOGLE_ADS":   return googleAdsRate;
            case "META_ADS":     return metaAdsRate;
            case "TIKTOK_ADS":   return tikTokRate;
            case "LINKEDIN_ADS": return linkedInRate;
            case "IHEART_RADIO": return iHeartRate;
            default: return 1.0;  // default: 1 permit/sec
        }
    }
}
```

### 7.5 Enhanced BaseApiWrapper (Existing — Refined for Rate Limiting)

The existing `BaseApiWrapper` in `integration/wrapper/BaseApiWrapper.java` is enhanced to integrate `AdPlatformRateLimiter` and `OAuthTokenManager`:

```java
public abstract class BaseApiWrapper<T> {

    private static final Logger log = LoggerFactory.getLogger(BaseApiWrapper.class);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    protected final RestTemplate restTemplate;
    protected final AdPlatformRateLimiter rateLimiter;
    protected final OAuthTokenManager tokenManager;

    protected BaseApiWrapper(RestTemplate restTemplate,
                              AdPlatformRateLimiter rateLimiter,
                              OAuthTokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.rateLimiter = rateLimiter;
        this.tokenManager = tokenManager;
    }

    /**
     * Template method for API calls with retry, rate-limit enforcement, and error translation.
     * Before each attempt, rateLimiter.acquire() blocks until a permit is available.
     */
    protected T executeWithRetry(Supplier<T> apiCall, String platformName) {
        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (attempt < MAX_RETRIES) {
            try {
                attempt++;
                // Enforce rate limit before each attempt
                rateLimiter.acquire(platformName);
                log.debug("Calling {} API, attempt {}", platformName, attempt);
                T result = apiCall.get();
                log.debug("{} API call successful on attempt {}", platformName, attempt);
                return result;
            } catch (HttpServerErrorException e) {
                if (attempt >= MAX_RETRIES) {
                    log.error("{} API exhausted retries: status={}", platformName, e.getStatusCode());
                    throw new IntegrationUnavailableException(
                        "Data temporarily unavailable for " + platformName +
                        " after " + MAX_RETRIES + " attempts");
                }
                log.warn("{} API attempt {}/{} failed: status={}, retrying in {}ms",
                    platformName, attempt, MAX_RETRIES, e.getStatusCode(), backoff);
                safeSleep(backoff);
                backoff *= 2;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    log.error("{} API authentication failed — invalidating token", platformName);
                    tokenManager.invalidateToken(platformName);
                } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    // Rate-limited by the server — apply longer backoff
                    log.warn("{} API rate-limited (429). Applying extended backoff.", platformName);
                    safeSleep(backoff * 4);
                } else {
                    log.error("{} API client error: status={}, body={}",
                        platformName, e.getStatusCode(), e.getResponseBodyAsString());
                }
                throw new IntegrationUnavailableException(
                    "Data temporarily unavailable for " + platformName + " (configuration error)");
            } catch (Exception e) {
                log.error("{} API unexpected error", platformName, e);
                throw new IntegrationUnavailableException(
                    "Data temporarily unavailable for " + platformName);
            }
        }
        throw new IntegrationUnavailableException("Data temporarily unavailable for " + platformName);
    }

    private void safeSleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

### 7.6 Refined Concrete Wrapper (GoogleAdsApiWrapper)

```java
@Component
public class GoogleAdsApiWrapper extends BaseApiWrapper<PlatformApiResponse> {

    private static final Logger log = LoggerFactory.getLogger(GoogleAdsApiWrapper.class);
    private static final String BASE_URL = "https://googleads.googleapis.com/v16";

    @Value("${integration.google-ads.customer-id:}")
    private String customerId;

    @Value("${integration.google-ads.developer-token:}")
    private String developerToken;

    public GoogleAdsApiWrapper(RestTemplate restTemplate,
                                AdPlatformRateLimiter rateLimiter,
                                OAuthTokenManager tokenManager) {
        super(restTemplate, rateLimiter, tokenManager);
    }

    /**
     * Fetches KPI metrics from Google Ads API.
     * Returns a normalized PlatformApiResponse (or null if not configured).
     */
    public PlatformApiResponse fetchMetrics(String platformKey) {
        if (customerId == null || customerId.isEmpty()) {
            log.warn("Google Ads customer ID not configured — skipping fetch");
            return null;
        }
        return executeWithRetry(() -> {
            String accessToken = tokenManager.getAccessToken("google_ads");
            log.debug("Fetching Google Ads metrics for customer: {}", customerId);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("developer-token", developerToken);

            String queryBody = buildQueryPayload();
            HttpEntity<String> entity = new HttpEntity<>(queryBody, headers);

            ResponseEntity<GoogleAdsSearchResponse> response = restTemplate.exchange(
                BASE_URL + "/customers/" + customerId + "/googleAds:search",
                HttpMethod.POST, entity, GoogleAdsSearchResponse.class);

            return mapToPlatformApiResponse(response.getBody());
        }, "Google Ads");
    }

    private String buildQueryPayload() {
        // GAQL query for KPI metrics
        return "{"
            + "\"query\": \""
            + "SELECT "
            + "metrics.cost_micros, "
            + "metrics.conversions_value, "
            + "metrics.all_conversions, "
            + "metrics.clicks, "
            + "metrics.impressions, "
            + "metrics.average_cpc, "
            + "customer.descriptive_name "
            + "FROM customer "
            + "WHERE segments.date DURING LAST_7_DAYS"
            + "\""
            + "}";
    }

    private PlatformApiResponse mapToPlatformApiResponse(GoogleAdsSearchResponse adsResponse) {
        // Map Google Ads API specific response fields to PlatformApiResponse
        // ...
        return PlatformApiResponse.builder()
            .platformName("google_ads")
            .dataSource("Google Ads API v16")
            .ingestionTimestamp(Instant.now())
            .build();
    }
}
```

---

### 7.7 4-Stage ETL Pipeline — Spring Events Design (DSRC-04 through DSRC-07)

This section details the **new** 10-source pipeline classes that replace the old `AdPlatformIngestionScheduler` → `KPIStreamConsumer` path. The pipeline uses Spring's `ApplicationEventPublisher` + `@EventListener` with a **hybrid synchronous/asynchronous** execution model.

#### 7.7.1 Pipeline Execution Model

```
Per-wrapper thread (×10 in parallel, @Async):
  Scheduler fires wrapper → RawSourceData
    ├─ SourceDataNormalizer.normalize(rawData) → NormalizedSourceMessage
    ├─ eventBus.publish("source.raw", ...) ──┐
    │                                         │ (sync — same thread)
    │                                         ▼
    │  SectorGrouper.onSourceRaw()            ← @EventListener (no @Async)
    │    ├─ SectorClassifier.classify(msg) → List<SourceSectorMappingMessage>
    │    └─ eventBus.publish("sector.grouped", ...) ──┐
    │                                                  │ (sync — same thread)
    │                                                  ▼
    │  CompanyPlatformGrouper.onSectorGrouped()        ← @EventListener (no @Async)
    │    ├─ CompanyPlatformMapper.map(msg) → List<CompanyPlatformMappingMessage>
    │    ├─ CompanyRepository.upsert(company)
    │    └─ eventBus.publish("company.grouped", ...) ──┐
    │                                                   │ (sync — same thread)
    │                                                   ▼
    │  KpiBuilder.onCompanyGrouped()                    ← @EventListener (no @Async)
    │    └─ accumulator.put(sector, msg)   // in-memory store only

Scheduler thread (after all 10 wrappers complete):
  eventBus.publish("pipeline.batch-complete", ...) ──┐
                                                      │ (@Async — eventTaskExecutor)
                                                      ▼
  KpiBuilder.onBatchComplete()                        ← @Async @EventListener
    ├─ Drain accumulator (ConcurrentHashMap)
    ├─ Group by (company + sector + platform)
    ├─ KpiSignalAggregator.aggregate(signals) → KPIMetrics
    ├─ KPIMetricsRepository.upsert(metrics) for each group
    └─ eventBus.publish("kpi.refresh", ...) for each unique (platformId, sectorId)
                                                      │
                                                      ▼
  CacheInvalidationService.onKpiRefresh()             ← @Async @EventListener
    └─ Purge Redis keys (already implemented)
```

#### 7.7.2 Sequence Diagram — Single Ingestion Cycle

```
  [Scheduler Thread]         [Wrapper Thread 1]        [Wrapper Thread 2]       [Async Thread Pool]
        │                          │                          │                        │
        │ @Scheduled(15min)        │                          │                        │
        │──┐                       │                          │                        │
        │  │ ingestAllSources()    │                          │                        │
        │  │                       │                          │                        │
        │  ├─@Async─f1─────────────>                          │                        │
        │  │                       │──pytrends.fetchTrends()  │                        │
        │  │                       │  → RawSourceData         │                        │
        │  │                       │──normalize()             │                        │
        │  │                       │──publish("source.raw")───│                        │
        │  │                       │  → SectorGrouper (sync)  │                        │
        │  │                       │    → CompanyPlatformGr.  │                        │
        │  │                       │      → KpiBuilder.accum. │                        │
        │  │                       │                          │                        │
        │  ├─@Async─f2─────────────────────────────────────────>                        │
        │  │                       │                          │──ebay.fetchListings()  │
        │  │                       │                          │  → RawSourceData       │
        │  │                       │                          │──normalize()           │
        │  │                       │                          │──publish("source.raw") │
        │  │                       │                          │  → ... (sync chain)    │
        │  │                       │                          │    → KpiBuilder.accum. │
        │  │                       │                          │                        │
        │  │  ... 8 more wrappers fired in parallel ...                                │
        │  │                       │                          │                        │
        │  ├─CompletableFuture.allOf().get(timeout)           │                        │
        │  │                       │                          │                        │
        │  ├─publish("pipeline.batch-complete")───────────────────────────────────────────>
        │  │                       │                          │                        │
        │  └─return                │                          │                        │
        │                          │                          │                        │  KpiBuilder
        │                          │                          │                        │  .onBatchComplete()
        │                          │                          │                        │  ──drain accumulator
        │                          │                          │                        │  ──aggregate signals
        │                          │                          │                        │  ──upsert KPIMetrics
        │                          │                          │                        │  ──publish kpi.refresh
        │                          │                          │                        │    → CacheInvalidation
```

#### 7.7.3 Class: `PipelineBatchCompleteEvent`

```java
package com.autoresolve.mediabuying.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;

/**
 * Published by {@code DataSourceIngestionScheduler} after all 10 wrappers
 * have completed (or timed out). Triggers Stage 4b — batch KPI computation.
 *
 * <p>Wrapped in {@link com.autoresolve.mediabuying.eventbus.IntegrationEvent}
 * with topic {@code pipeline.batch-complete}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineBatchCompleteEvent {
    private String batchId;                  // UUID for this ingestion cycle
    private int totalSources;                // Number of wrappers invoked (10)
    private int successfulSources;           // How many returned data
    private List<String> failedSources;      // Names of sources that failed
    private String cycleTimestamp;           // When the scheduler triggered
    private Instant batchCompletedAt;        // When batch-complete was published
}
```

#### 7.7.4 Class: `SourceDataNormalizer`

```java
package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.integration.dto.RawSourceData;
import com.autoresolve.mediabuying.messaging.dto.NormalizedSourceMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class SourceDataNormalizer {
    private static final Logger log = LoggerFactory.getLogger(SourceDataNormalizer.class);

    /**
     * Converts a single wrapper response into a normalized pipeline message.
     * Thread-safe — no mutable state.
     */
    public NormalizedSourceMessage normalize(RawSourceData rawData) {
        NormalizedSourceMessage msg = new NormalizedSourceMessage();
        msg.setEventId(UUID.randomUUID().toString());
        msg.setSourceName(rawData.getSourceName());
        msg.setSourceUrl(rawData.getSourceUrl());
        msg.setSourceType(rawData.getSourceType());
        msg.setRawData(rawData.getRawPayload());
        msg.setNormalizedSummary(rawData.getNormalizedSummary());
        msg.setIngestionTimestamp(rawData.getFetchTimestamp());
        msg.setIngestionKey(rawData.getIngestionKey());
        log.debug("Normalized source data: source={}, status={}",
                rawData.getSourceName(), rawData.getFetchStatus());
        return msg;
    }
}
```

#### 7.7.5 Class: `DataSourceIngestionScheduler`

```java
package com.autoresolve.mediabuying.scheduler;

import com.autoresolve.mediabuying.eventbus.EventBus;
import com.autoresolve.mediabuying.integration.dto.PipelineBatchCompleteEvent;
import com.autoresolve.mediabuying.integration.dto.RawSourceData;
import com.autoresolve.mediabuying.integration.pipeline.SourceDataNormalizer;
import com.autoresolve.mediabuying.integration.wrapper.*;
import com.autoresolve.mediabuying.messaging.dto.NormalizedSourceMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Component
public class DataSourceIngestionScheduler {
    private static final Logger log = LoggerFactory.getLogger(DataSourceIngestionScheduler.class);

    // 10 wrapper references (constructor injected)
    private final PytrendsApiWrapper pytrendsWrapper;
    private final EbayApiWrapper ebayWrapper;
    // ... 8 more wrappers ...
    private final SourceDataNormalizer normalizer;
    private final EventBus eventBus;

    @Value("${pipeline.ingestion.timeout-seconds:600}")
    private int timeoutSeconds;

    @Value("${pipeline.ingestion.enabled:true}")
    private boolean ingestionEnabled;

    // Constructor with all 10 wrappers + normalizer + eventBus

    @Scheduled(fixedRateString = "${pipeline.ingestion.interval-ms:900000}")
    public void ingestAllSources() {
        if (!ingestionEnabled) {
            log.trace("Pipeline ingestion disabled, skipping");
            return;
        }
        log.info("=== Starting 10-source pipeline ingestion cycle ===");
        Instant cycleStart = Instant.now();

        // Thread-safe results tracking
        ConcurrentHashMap<String, RawSourceData> results = new ConcurrentHashMap<>();
        List<String> failedSources = Collections.synchronizedList(new ArrayList<>());

        // Fire all 10 wrappers in parallel
        List<CompletableFuture<Void>> futures = Arrays.asList(
            fetchSourceAsync("pytrends",   () -> pytrendsWrapper.fetchTrends("commerce", "US"), results, failedSources),
            fetchSourceAsync("ebay",       () -> ebayWrapper.fetchListings("laptop", null), results, failedSources),
            // ... 8 more ...
            fetchSourceAsync("job_market", () -> jobMarketWrapper.fetchJobListings("software", "US"), results, failedSources)
        );

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Pipeline ingestion timed out after {}s", timeoutSeconds);
        } catch (Exception e) {
            log.error("Pipeline ingestion interrupted", e);
            Thread.currentThread().interrupt();
        }

        // Publish normalized messages for successful sources
        int published = 0;
        for (Map.Entry<String, RawSourceData> entry : results.entrySet()) {
            try {
                NormalizedSourceMessage msg = normalizer.normalize(entry.getValue());
                eventBus.publish("source.raw", entry.getKey(), msg);
                published++;
            } catch (Exception e) {
                log.error("Failed to normalize source={}", entry.getKey(), e);
                failedSources.add(entry.getKey());
            }
        }

        // Publish batch-complete event
        PipelineBatchCompleteEvent batchEvent = PipelineBatchCompleteEvent.builder()
                .batchId(UUID.randomUUID().toString())
                .totalSources(10)
                .successfulSources(published)
                .failedSources(failedSources)
                .cycleTimestamp(cycleStart.toString())
                .batchCompletedAt(Instant.now())
                .build();
        eventBus.publish("pipeline.batch-complete", "batch", batchEvent);

        log.info("=== Pipeline ingestion cycle complete: {}/10 sources published, {} failed ===",
                published, failedSources.size());
    }

    @Async("adPlatformTaskExecutor")
    protected CompletableFuture<Void> fetchSourceAsync(
            String sourceName,
            Supplier<RawSourceData> fetchFn,
            ConcurrentHashMap<String, RawSourceData> results,
            List<String> failedSources) {
        try {
            log.debug("Fetching data from source={}", sourceName);
            RawSourceData data = fetchFn.get();
            if (data != null) {
                results.put(sourceName, data);
                log.debug("Source={} returned {} records, status={}",
                        sourceName, data.getRecordCount(), data.getFetchStatus());
            } else {
                failedSources.add(sourceName);
                log.warn("Source={} returned null, skipping", sourceName);
            }
        } catch (Exception e) {
            failedSources.add(sourceName);
            log.error("Source={} fetch failed", sourceName, e);
        }
        return CompletableFuture.completedFuture(null);
    }
}
```

#### 7.7.6 Class: `SectorClassifier`

```java
package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.messaging.dto.NormalizedSourceMessage;
import com.autoresolve.mediabuying.messaging.dto.SourceSectorMappingMessage;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@ConfigurationProperties(prefix = "sector-classification")
public class SectorClassifier {
    private static final Logger log = LoggerFactory.getLogger(SectorClassifier.class);

    private final CommerceSectorRepository sectorRepository;

    // Injected from application.yml: sector-classification.technology, .finance, etc.
    private Map<String, List<String>> keywordRules = new HashMap<>();
    private Map<String, String> fallbackSourceMap = new HashMap<>();

    public SectorClassifier(CommerceSectorRepository sectorRepository) {
        this.sectorRepository = sectorRepository;
    }

    /**
     * Classifies a normalized source message into one or more commerce sectors.
     * Returns one SourceSectorMappingMessage per matched sector.
     */
    public List<SourceSectorMappingMessage> classify(NormalizedSourceMessage msg) {
        String searchText = buildSearchText(msg);
        List<SourceSectorMappingMessage> results = new ArrayList<>();
        int totalKeywords = keywordRules.values().stream().mapToInt(List::size).sum();
        int matchCount = 0;

        for (Map.Entry<String, List<String>> entry : keywordRules.entrySet()) {
            String sectorName = entry.getKey();
            List<String> keywords = entry.getValue();
            long keywordMatches = keywords.stream()
                    .filter(kw -> searchText.toLowerCase().contains(kw.toLowerCase()))
                    .count();

            if (keywordMatches > 0 || isFallbackMatch(msg.getSourceName(), sectorName)) {
                double confidence = Math.min(1.0, (double) keywordMatches / Math.max(keywords.size(), 1));
                matchCount++;

                SourceSectorMappingMessage result = new SourceSectorMappingMessage();
                result.setEventId(UUID.randomUUID().toString());
                result.setSourceName(msg.getSourceName());
                result.setMatchedSectors(Collections.singletonList(sectorName));
                result.setClassificationMethod(keywordMatches > 0 ? "KEYWORD_MATCH" : "SOURCE_FALLBACK");
                result.setConfidenceScore(confidence);
                result.setRawEventId(msg.getEventId());
                result.setProcessingTimestamp(Instant.now());
                result.setPartitionKey(sectorName);
                results.add(result);
            }
        }

        log.debug("Classified source={} into {} sectors (keywords matched: {})",
                msg.getSourceName(), results.size(), matchCount);
        return results;
    }

    private String buildSearchText(NormalizedSourceMessage msg) {
        StringBuilder sb = new StringBuilder();
        if (msg.getNormalizedSummary() != null) sb.append(msg.getNormalizedSummary()).append(" ");
        if (msg.getRawData() != null) sb.append(msg.getRawData());
        return sb.toString();
    }

    private boolean isFallbackMatch(String sourceName, String sectorName) {
        String mappedSector = fallbackSourceMap.get(sourceName);
        return sectorName.equals(mappedSector);
    }

    // Setters for @ConfigurationProperties injection
    public void setKeywordRules(Map<String, List<String>> keywordRules) {
        this.keywordRules = keywordRules;
    }
    public void setFallbackSourceMap(Map<String, String> fallbackSourceMap) {
        this.fallbackSourceMap = fallbackSourceMap;
    }
}
```

#### 7.7.7 Class: `KpiBuilder` (Two-Phase Stage 4)

```java
package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.eventbus.EventBus;
import com.autoresolve.mediabuying.integration.dto.PipelineBatchCompleteEvent;
import com.autoresolve.mediabuying.messaging.dto.CompanyPlatformMappingMessage;
import com.autoresolve.mediabuying.messaging.dto.KpiRefreshEvent;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class KpiBuilder {
    private static final Logger log = LoggerFactory.getLogger(KpiBuilder.class);

    private final KPIMetricsRepository kpiMetricsRepository;
    private final KpiSignalAggregator signalAggregator;
    private final EventBus eventBus;

    /**
     * Thread-safe accumulator for in-flight pipeline events.
     * Key = sectorName, Value = list of company-platform mappings for that sector.
     * Populated by onCompanyGrouped() (synchronous on wrapper threads).
     * Drained by onBatchComplete() (async on eventTaskExecutor).
     */
    private final ConcurrentHashMap<String, List<CompanyPlatformMappingMessage>> accumulator
            = new ConcurrentHashMap<>();

    public KpiBuilder(KPIMetricsRepository kpiMetricsRepository,
                      KpiSignalAggregator signalAggregator,
                      EventBus eventBus) {
        this.kpiMetricsRepository = kpiMetricsRepository;
        this.signalAggregator = signalAggregator;
        this.eventBus = eventBus;
    }

    /**
     * Phase 4a: Accumulate company-platform mappings synchronously on the wrapper thread.
     * No DB writes — just in-memory accumulation.
     */
    @EventListener(condition = "#event.topic == 'company.grouped'")
    public void onCompanyGrouped(com.autoresolve.mediabuying.eventbus.IntegrationEvent event) {
        Object payload = event.getPayload();
        if (!(payload instanceof CompanyPlatformMappingMessage)) {
            log.warn("Unexpected payload type on company.grouped: {}",
                    payload != null ? payload.getClass().getName() : "null");
            return;
        }
        CompanyPlatformMappingMessage msg = (CompanyPlatformMappingMessage) payload;
        String sectorName = msg.getSectorName() != null ? msg.getSectorName() : "unknown";

        accumulator.computeIfAbsent(sectorName, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(msg);
        log.debug("Accumulated company mapping: company={}, sector={}, platforms={}",
                msg.getCompanyName(), sectorName, msg.getInferredAdPlatforms());
    }

    /**
     * Phase 4b: Process the batch on the async thread pool.
     * Drains the accumulator, aggregates signals, computes KPIs, upserts to DB.
     */
    @Async("eventTaskExecutor")
    @EventListener(condition = "#event.topic == 'pipeline.batch-complete'")
    public void onBatchComplete(com.autoresolve.mediabuying.eventbus.IntegrationEvent event) {
        Object payload = event.getPayload();
        if (!(payload instanceof PipelineBatchCompleteEvent)) {
            log.warn("Unexpected payload type on pipeline.batch-complete");
            return;
        }
        PipelineBatchCompleteEvent batchEvent = (PipelineBatchCompleteEvent) payload;
        log.info("Processing batch-complete: batchId={}, successfulSources={}",
                batchEvent.getBatchId(), batchEvent.getSuccessfulSources());

        // Atomically drain the accumulator
        Map<String, List<CompanyPlatformMappingMessage>> snapshot = new HashMap<>();
        for (Iterator<Map.Entry<String, List<CompanyPlatformMappingMessage>>> it =
                accumulator.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, List<CompanyPlatformMappingMessage>> entry = it.next();
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            it.remove();  // clear as we go — prevents double-processing
        }

        if (snapshot.isEmpty()) {
            log.info("Accumulator empty — no KPIs to compute this cycle");
            return;
        }

        // Group by (company + sector + platform) for aggregation
        int totalUpserted = 0;
        Set<String> refreshKeys = new HashSet<>();  // deduplicate refresh events

        for (Map.Entry<String, List<CompanyPlatformMappingMessage>> entry : snapshot.entrySet()) {
            String sectorName = entry.getKey();
            List<CompanyPlatformMappingMessage> msgs = entry.getValue();

            // Aggregate all signals for this sector
            List<KPIMetrics> metrics = signalAggregator.aggregate(msgs, sectorName);

            // Upsert each KPI row
            for (KPIMetrics m : metrics) {
                try {
                    kpiMetricsRepository.upsert(m);
                    totalUpserted++;
                    // Track unique platform+sector pairs for cache invalidation
                    String refreshKey = m.getPlatformId() + ":" + m.getSectorId();
                    if (refreshKeys.add(refreshKey)) {
                        KpiRefreshEvent refreshEvent = KpiRefreshEvent.builder()
                                .platformId(m.getPlatformId())
                                .sectorId(m.getSectorId())
                                .eventType("KPI_UPDATED")
                                .refreshTimestamp(Instant.now())
                                .build();
                        eventBus.publish("kpi.refresh", m.getPlatformId().toString(), refreshEvent);
                    }
                } catch (Exception e) {
                    log.error("Failed to upsert KPI for sector={}, platformId={}",
                            sectorName, m.getPlatformId(), e);
                }
            }
        }

        log.info("Batch complete: {} KPIs upserted, {} cache refresh events published",
                totalUpserted, refreshKeys.size());
    }
}
```

#### 7.7.8 Class: `KpiSignalAggregator`

```java
package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.messaging.dto.CompanyPlatformMappingMessage;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class KpiSignalAggregator {
    private static final Logger log = LoggerFactory.getLogger(KpiSignalAggregator.class);

    private final PlatformRepository platformRepository;
    private final CommerceSectorRepository sectorRepository;

    @Value("${kpi-synthesis.default-roas:3.0}")
    private double defaultRoas;

    @Value("${kpi-synthesis.default-cac:35.0}")
    private double defaultCac;

    // ... more defaults ...

    public KpiSignalAggregator(PlatformRepository platformRepository,
                                CommerceSectorRepository sectorRepository) {
        this.platformRepository = platformRepository;
        this.sectorRepository = sectorRepository;
    }

    /**
     * Aggregates multiple company-platform mappings into a list of KPIMetrics entities.
     * Each unique (company + sector + platform) combination produces one KPIMetrics row.
     */
    public List<KPIMetrics> aggregate(List<CompanyPlatformMappingMessage> signals, String sectorName) {
        Long sectorId = sectorRepository.findByName(sectorName)
                .orElse(null);
        if (sectorId == null) {
            log.warn("Unknown sector: {}", sectorName);
            return Collections.emptyList();
        }

        // Group signals by platform
        Map<String, List<CompanyPlatformMappingMessage>> byPlatform = signals.stream()
                .filter(s -> s.getInferredAdPlatforms() != null)
                .flatMap(s -> s.getInferredAdPlatforms().stream()
                        .map(p -> new AbstractMap.SimpleEntry<>(p, s)))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        List<KPIMetrics> results = new ArrayList<>();

        for (Map.Entry<String, List<CompanyPlatformMappingMessage>> entry : byPlatform.entrySet()) {
            String platformName = entry.getKey();
            List<CompanyPlatformMappingMessage> platformSignals = entry.getValue();

            Long platformId = platformRepository.findByName(platformName)
                    .orElse(null);
            if (platformId == null) {
                log.debug("Unknown platform: {} — skipping", platformName);
                continue;
            }

            KPIMetrics metrics = computeMetrics(platformId, sectorId, platformSignals);
            results.add(metrics);
        }

        log.debug("Aggregated {} KPI rows for sector={} from {} signals",
                results.size(), sectorName, signals.size());
        return results;
    }

    private KPIMetrics computeMetrics(Long platformId, Long sectorId,
                                       List<CompanyPlatformMappingMessage> signals) {
        int count = signals.size();
        double avgConfidence = signals.stream()
                .mapToDouble(s -> s.getConfidenceScore() != null ? s.getConfidenceScore() : 0.5)
                .average().orElse(0.5);

        // Compute weighted KPI estimates based on signal count and confidence
        double roas = defaultRoas * (0.5 + 0.5 * avgConfidence) * (1.0 + 0.1 * Math.log1p(count));
        double cac = defaultCac * (1.5 - 0.5 * avgConfidence) / (1.0 + 0.05 * Math.log1p(count));
        double cltv = cac * roas * 0.7;
        double conversionRate = 0.02 + 0.005 * avgConfidence * Math.log1p(count);
        double scalability = 100000.0 * count * avgConfidence;
        double attributionAccuracy = 0.6 + 0.3 * avgConfidence;

        // Cap values to realistic ranges
        roas = Math.min(Math.max(roas, 0.1), 20.0);
        cac = Math.min(Math.max(cac, 1.0), 200.0);
        cltv = Math.min(Math.max(cltv, 10.0), 2000.0);
        conversionRate = Math.min(Math.max(conversionRate, 0.001), 0.5);
        scalability = Math.min(Math.max(scalability, 1000.0), 10000000.0);
        attributionAccuracy = Math.min(Math.max(attributionAccuracy, 0.1), 1.0);

        return KPIMetrics.builder()
                .platformId(platformId)
                .sectorId(sectorId)
                .roas(BigDecimal.valueOf(roas))
                .cac(BigDecimal.valueOf(cac))
                .cltv(BigDecimal.valueOf(cltv))
                .conversionRate(BigDecimal.valueOf(conversionRate))
                .scalability(BigDecimal.valueOf(scalability))
                .attributionAccuracy(BigDecimal.valueOf(attributionAccuracy))
                .ingestionTimestamp(Instant.now())
                .dataSource("10-source-pipeline")
                .build();
    }
}
```

#### 7.7.9 `application.yml` — Pipeline Configuration

```yaml
# Pipeline ingestion scheduling
pipeline:
  ingestion:
    interval-ms: 900000           # 15 minutes
    timeout-seconds: 600          # 10 minutes max for all wrappers
    enabled: true

# Sector classification rules (keyword matching)
sector-classification:
  keyword-rules:
    technology: [software, saas, cloud, ai, it-services, app, startup, cybersecurity, hardware]
    finance: [banking, insurance, investment, fintech, cryptocurrency, mortgage, loan, credit]
    manufacturing: [factory, production, industrial, supply-chain, logistics, machinery]
    retail: [ecommerce, apparel, food, grocery, restaurant, shopping, fashion, consumer]
    health-wellness: [healthcare, fitness, supplement, medical, hospital, pharmacy, wellness]
    travel: [flight, hotel, vacation, tourism, booking, rental, destination, cruise]
    job-market: [hiring, job, career, employment, recruitment, salary, resume, interview]
  fallback-source-map:
    pytrends: technology
    skyscanner: travel
    job_market: job-market
    yelp_fusion: retail
    foursquare_places: retail
    ebay: retail
    bing_webmaster: technology
    reddit: technology
    x_api: technology
    meta_ads_library: technology

# Company-to-platform mapping rules
company-platform-mapping:
  business-type-to-platform:
    local_business: [yelp_ads, foursquare_ads, google_ads]
    ecommerce: [google_shopping, meta_ads, ebay_ads]
    b2b_saas: [linkedin_ads, google_ads, bing_ads]
    travel: [bing_ads, skyscanner_ads, google_ads]
    job_board: [linkedin_ads, indeed_ads]
    social_media: [meta_ads, tiktok_ads, reddit_ads]

# KPI synthesis weights
kpi-synthesis:
  default-roas: 3.0
  default-cac: 35.0
  default-cltv: 200.0
  default-conversion-rate: 0.025
  default-scalability: 100000.0
  default-attribution-accuracy: 0.7
```

### 8.1 Exception Hierarchy

```
RuntimeException
└── MediaBuyingException (base custom exception)
    ├── IntegrationUnavailableException    → "Data temporarily unavailable for {platform}"
    ├── AccessDeniedException              → "You do not have permission to access this resource"
    ├── InvalidInputException              → "Invalid input: {field} must be {constraint}"
    ├── DataStaleException                 → "Data is older than {threshold} minutes"
    └── ScoringConfigurationException     → "Scoring weights must sum to 1.0"
```

### 8.2 Global Exception Handler

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IntegrationUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleIntegrationUnavailable(IntegrationUnavailableException ex) {
        ErrorResponse error = new ErrorResponse("INTEGRATION_UNAVAILABLE", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse("ACCESS_DENIED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled exception [correlationId={}]", correlationId, ex);
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR",
            "An unexpected error occurred. Reference: " + correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

### 8.3 User-Facing Error Display (JSF)

```xml
<!-- Error banner in template.xhtml -->
<p:messages id="globalMessages" severity="error" closable="true"
            styleClass="error-banner" escape="false" />
```

```java
@ManagedBean
public class DashboardBean {
    public void loadMetrics() {
        try {
            metrics = kpiQueryService.getMetrics(platformId, sectorId);
        } catch (IntegrationUnavailableException e) {
            FacesContext.getCurrentInstance()
                .addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Data Unavailable", e.getMessage()));
            metrics = Collections.emptyList();
        }
    }
}
```

---

## 9. Caching & Performance Implementation

### 9.1 Redis Cache Configuration

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheManagerBuilderCustomizer() {
        return builder -> builder
            .withCacheConfiguration(CacheKeys.COMPOSITE_TOP,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration(CacheKeys.HIERARCHY_ALL,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration(CacheKeys.METRICS_PREFIX,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer())))
            .withCacheConfiguration(CacheKeys.PLATFORMS_LIST,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(15)));
    }
}
```

### 9.2 Cache Key Constants

```java
public final class CacheKeys {
    private CacheKeys() {}

    public static final String COMPOSITE_TOP    = "composite:top";
    public static final String HIERARCHY_ALL    = "hierarchy:all";
    public static final String METRICS_PREFIX   = "metrics";
    public static final String PLATFORMS_LIST   = "platforms:list";

    public static String metricsKey(long platformId, long sectorId, int page, String sort) {
        return String.format("metrics:%d:%d:%d:%s", platformId, sectorId, page, sort);
    }
}
```

### 9.3 Lazy Data Model (PrimeFaces)

```java
@Component
public class KpiLazyDataModel extends LazyDataModel<KPIMetricsDTO> {

    private final KPIQueryService kpiQueryService;
    private Long platformId;
    private Long sectorId;

    public KpiLazyDataModel(KPIQueryService kpiQueryService) {
        this.kpiQueryService = kpiQueryService;
    }

    @Override
    public List<KPIMetricsDTO> load(int first, int pageSize, String sortField,
                                     SortOrder sortOrder, Map<String, Object> filters) {
        int page = first / pageSize;
        String sortDir = sortOrder == SortOrder.ASCENDING ? "asc" : "desc";
        String sortCol = sortField != null ? sortField : "roas";

        // Attempt cache hit first, fall back to DB
        String cacheKey = CacheKeys.metricsKey(platformId, sectorId, page, sortCol);
        Page<KPIMetricsDTO> result = kpiQueryService.getMetrics(
            platformId, sectorId, page, pageSize, sortCol, sortDir);

        this.setRowCount((int) result.getTotalElements());
        return result.getContent();
    }

    // Setters for platformId, sectorId used by DashboardBean before load()
}
```

---

## 10. Security Implementation

### 10.1 Spring Security Configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/admin/**").hasRole("ADMIN")
                .antMatchers("/api/metrics/**").hasAnyRole("ADMIN", "MEDIA_ANALYST", "VIEWER")
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/", "/login", "/error", "/css/**", "/js/**").permitAll()
                .anyRequest().authenticated()
            .and()
                .formLogin()
                .loginPage("/login").permitAll()
                .defaultSuccessUrl("/dashboard")
            .and()
                .logout().logoutSuccessUrl("/login?logout")
            .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1);
    }
}
```

### 10.2 KPI Column Masking Renderer

```java
@FacesRenderer(componentFamily = "javax.faces.Output", rendererType = "kpiColumn")
public class KpiColumnMaskRenderer extends TextRenderer {

    @Override
    public void encodeEnd(FacesContext context, UIComponent component) throws IOException {
        String userRole = SecurityContextHolder.getContext()
            .getAuthentication().getAuthorities().stream()
            .findFirst().map(GrantedAuthority::getAuthority).orElse("UNKNOWN");

        if ("ROLE_MEDIA_ANALYST".equals(userRole) || "ROLE_ADMIN".equals(userRole)) {
            super.encodeEnd(context, component);
        } else {
            ResponseWriter writer = context.getResponseWriter();
            writer.startElement("span", component);
            writer.writeAttribute("class", "kpi-masked", null);
            writer.writeText("---", null);
            writer.endElement("span");
        }
    }
}
```

---

## 11. Logging & Audit Implementation

### 11.1 Audit Logging Aspect

```java
@Aspect
@Component
public class AuditLoggingAspect {

    private final AuditLogRepository auditLogRepository;

    @Around("@annotation(Auditable) && execution(* com.autoresolve.mediabuying.service..*(..))")
    public Object logAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        String action = joinPoint.getSignature().getName();
        String entityType = joinPoint.getTarget().getClass().getSimpleName();
        String correlationId = MDC.get("correlationId");
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - startTime;

        // Build audit record
        AuditLog audit = AuditLog.builder()
            .action(action)
            .entityType(entityType)
            .details(buildDetails(joinPoint, result))
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();

        auditLogRepository.save(audit);

        log.info("AUDIT | user={} | action={} | entity={} | duration={}ms | correlationId={}",
            username, action, entityType, duration, correlationId);

        return result;
    }

    private JsonNode buildDetails(ProceedingJoinPoint joinPoint, Object result) {
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("method", joinPoint.getSignature().toShortString());
        details.put("args", Arrays.toString(joinPoint.getArgs()));
        details.put("resultType", result != null ? result.getClass().getSimpleName() : "void");
        return details;
    }
}
```

### 11.2 Structured Logging Configuration (logback-spring.xml)

```xml
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>correlationId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>sessionId</includeMdcKeyName>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/mediabuying/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/var/log/mediabuying/application.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} [correlationId=%X{correlationId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON" />
        <appender-ref ref="FILE" />
    </root>

    <logger name="com.autoresolve.mediabuying" level="DEBUG" />
    <logger name="com.autoresolve.mediabuying.integration" level="INFO" />
</configuration>
```

---

## 12. Spring Events Pipeline Implementation

### 12.0 Event Bus & Configuration

The 4-stage ETL pipeline uses Spring's built-in `ApplicationEventPublisher` + `@EventListener` + `@Async`. All events are POJOs published via `ApplicationEventPublisher.publishEvent()` and consumed by `@Async @EventListener` methods.

#### SpringEventConfig.java (Replaces KafkaConfig.java)

```java
@Configuration
@EnableAsync
public class SpringEventConfig {

    @Bean(name = "eventTaskExecutor")
    public ThreadPoolTaskExecutor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    public AsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            LoggerFactory.getLogger(SpringEventConfig.class)
                .error("Uncaught async exception in method {}: {}",
                    method.getName(), ex.getMessage(), ex);
        };
    }
}
```

### 12.1 Messaging DTOs

#### RawKPIEvent (consumed from `kpi.raw`)
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RawKPIEvent {
    private String eventId;                  // UUID for deduplication
    private String platformName;             // machine name: "google_ads", "meta_ads", etc.
    private String sectorName;               // machine name: "technology", "finance", etc.
    private String dataSource;               // e.g. "Google Ads API v16"
    private Instant ingestionTimestamp;

    // Core KPIs (6 inputs to composite scoring)
    private Double roas;
    private Double cac;
    private Double cltv;
    private Double conversionRate;
    private Double scalability;
    private Double attributionAccuracy;

    // Extended KPIs (additional 7 columns)
    private Double contributionMargin;
    private Double paybackPeriod;
    private Double incrementalReturn;
    private Double cpql;                     // Cost Per Qualified Lead
    private Double cashConversionCycle;
    private Double saturationPoint;
}
```

#### KpiRefreshEvent (published to `kpi.refresh`)
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiRefreshEvent {
    private Long platformId;
    private Long sectorId;
    private String eventType;               // "KPI_UPDATED"
    private Instant refreshTimestamp;
}
```

---

### 12.2 KPIStreamConsumer (Spring Events Listener)

```java
@Component
public class KPIStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(KPIStreamConsumer.class);
    private final KPIMetricsRepository kpiMetricsRepository;
    private final PlatformRepository platformRepository;
    private final CommerceSectorRepository sectorRepository;
    private final KpiRefreshProducer kpiRefreshProducer;

    public KPIStreamConsumer(KPIMetricsRepository kpiMetricsRepository,
                              PlatformRepository platformRepository,
                              CommerceSectorRepository sectorRepository,
                              KpiRefreshProducer kpiRefreshProducer) {
        this.kpiMetricsRepository = kpiMetricsRepository;
        this.platformRepository = platformRepository;
        this.sectorRepository = sectorRepository;
        this.kpiRefreshProducer = kpiRefreshProducer;
    }

    @EventListener(condition = "#event.topic == 'kpi.raw'")
    @Async("eventTaskExecutor")
    public void consume(IntegrationEvent event) {
        try {
            log.debug("Received KPI event: platform={}, sector={}, source={}",
                event.getPlatformName(), event.getSectorName(), event.getDataSource());

            // Normalize and persist
            KPIMetrics metrics = convertToEntity(event);
            kpiMetricsRepository.upsert(metrics);

            // Signal cache invalidation via kpi.refresh topic
            kpiRefreshProducer.sendKpiRefreshEvent(
                metrics.getPlatformId(), metrics.getSectorId());

            log.info("KPI data ingested: platform={}, sector={}, timestamp={}",
                event.getPlatformName(), event.getSectorName(), event.getIngestionTimestamp());

        } catch (Exception e) {
            log.error("Failed to process KPI event: {}", event.getEventId(), e);
            // Errors are handled by AsyncUncaughtExceptionHandler — no DLQ needed
        }
    }

    private KPIMetrics convertToEntity(RawKPIEvent event) {
        Long platformId = platformRepository.findByName(event.getPlatformName())
            .orElseThrow(() -> new InvalidInputException("Unknown platform: " + event.getPlatformName()))
            .getId();
        Long sectorId = sectorRepository.findByName(event.getSectorName())
            .orElseThrow(() -> new InvalidInputException("Unknown sector: " + event.getSectorName()))
            .getId();

        return KPIMetrics.builder()
            .platformId(platformId)
            .sectorId(sectorId)
            .roas(event.getRoas())
            .cac(event.getCac())
            .cltv(event.getCltv())
            .conversionRate(event.getConversionRate())
            .scalability(event.getScalability())
            .attributionAccuracy(event.getAttributionAccuracy())
            .contributionMargin(event.getContributionMargin())
            .paybackPeriod(event.getPaybackPeriod())
            .incrementalReturn(event.getIncrementalReturn())
            .costPerQualifiedLead(event.getCpql())
            .cashConversionCycle(event.getCashConversionCycle())
            .saturationPoint(event.getSaturationPoint())
            .ingestionTimestamp(event.getIngestionTimestamp())
            .dataSource(event.getDataSource())
            .build();
    }
}
```

### 12.3 KpiRefreshProducer (via EventBus)

```java
@Component
public class KpiRefreshProducer {

    private static final Logger log = LoggerFactory.getLogger(KpiRefreshProducer.class);
    private final EventBus eventBus;

    public KpiRefreshProducer(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void sendKpiRefreshEvent(Long platformId, Long sectorId) {
        KpiRefreshEvent event = KpiRefreshEvent.builder()
            .platformId(platformId)
            .sectorId(sectorId)
            .eventType("KPI_UPDATED")
            .refreshTimestamp(Instant.now())
            .build();

        eventBus.publish("kpi.refresh", platformId.toString(), event);
    }
}
```

### 12.4 CacheInvalidationService (Spring Events Listener)

```java
@Service
public class CacheInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);
    private final RedisTemplate<String, Object> redisTemplate;

    @EventListener(condition = "#event.topic == 'kpi.refresh'")
    public void onKpiRefresh(IntegrationEvent event) {
        log.info("Cache invalidation triggered: platform={}, sector={}",
            event.getPlatformId(), event.getSectorId());

        // 1. Invalidate top opportunity cache (needs recomputation)
        redisTemplate.delete(CacheKeys.COMPOSITE_TOP);

        // 2. Invalidate hierarchy cache (platform list may have changed)
        redisTemplate.delete(CacheKeys.HIERARCHY_ALL);

        // 3. Invalidate all metrics keys for this platform+sector (all pages/sorts)
        Set<String> metricKeys = redisTemplate.keys(
            String.format("%s:%d:%d:*", CacheKeys.METRICS_PREFIX,
                event.getPlatformId(), event.getSectorId()));
        if (metricKeys != null && !metricKeys.isEmpty()) {
            redisTemplate.delete(metricKeys);
        }

        log.info("Cache invalidated: {} keys removed (top=1, hierarchy=1, metrics={})",
            (metricKeys != null ? metricKeys.size() + 2 : 2));
    }
}
```

---

## 13. ScoreFormatter Utility

```java
public final class ScoreFormatter {

    private ScoreFormatter() {}

    /**
     * Formats a numeric score for display:
     * - Scores >= 0.01: Rounded to 1 decimal place
     * - Scores < 0.01: Formatted as "0.0X" for visibility
     * - Null values: Returns "N/A"
     */
    public static String formatScore(Double score) {
        if (score == null) return "N/A";
        if (Math.abs(score - Math.round(score)) < 0.001) {
            return String.format("%.0f", score);
        }
        return String.format("%.1f", score);
    }

    /**
     * Formats currency values for display.
     */
    public static String formatCurrency(Number value) {
        if (value == null) return "N/A";
        return NumberFormat.getCurrencyInstance(Locale.US).format(value.doubleValue());
    }

    /**
     * Formats percentage values for display.
     */
    public static String formatPercentage(Double value) {
        if (value == null) return "N/A";
        return String.format("%.2f%%", value * 100);
    }
}
```

---

## 14. Global CSS Theme (dashboard-theme.css)

The application's visual styling follows the **"DashKit – Professional Services Dashboard"** design language adapted from the Webflow Technology templates category. All theme variables are centralized in a single global CSS file.

```css
/* ==============================================
 *  dashboard-theme.css
 *  Media Buying Dashboard – Global Theme
 *  Style Guide: DashKit Professional Dashboard
 *  Source: https://webflow.com/templates/category/technology-websites
 * ============================================== */

/* --- CSS Custom Properties (Theme Variables) --- */
:root {
    /* Primary Palette */
    --primary-blue: #2563eb;
    --primary-blue-hover: #1d4ed8;
    --primary-blue-light: #dbeafe;
    --primary-blue-dark: #1e40af;

    /* Neutral Palette */
    --bg-white: #ffffff;
    --bg-light: #f8fafc;
    --bg-gray-50: #f9fafb;
    --bg-gray-100: #f3f4f6;
    --bg-gray-200: #e5e7eb;
    --text-primary: #111827;
    --text-secondary: #6b7280;
    --text-muted: #9ca3af;
    --border-color: #e5e7eb;
    --border-color-strong: #d1d5db;

    /* Semantic Colors */
    --success-green: #16a34a;
    --success-green-bg: #dcfce7;
    --warning-yellow: #ca8a04;
    --warning-yellow-bg: #fef9c3;
    --danger-red: #dc2626;
    --danger-red-bg: #fee2e2;

    /* Typography */
    --font-family: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    --font-size-xs: 0.75rem;
    --font-size-sm: 0.875rem;
    --font-size-base: 1rem;
    --font-size-lg: 1.125rem;
    --font-size-xl: 1.25rem;
    --font-size-2xl: 1.5rem;
    --font-size-3xl: 2rem;

    /* Spacing */
    --spacing-xs: 4px;
    --spacing-sm: 8px;
    --spacing-md: 16px;
    --spacing-lg: 24px;
    --spacing-xl: 32px;
    --spacing-2xl: 48px;

    /* Shadows */
    --shadow-sm: 0 1px 2px rgba(0,0,0,0.05);
    --shadow-md: 0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -2px rgba(0,0,0,0.1);
    --shadow-lg: 0 10px 15px -3px rgba(0,0,0,0.1), 0 4px 6px -4px rgba(0,0,0,0.1);
    --shadow-card: 0 1px 3px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.06);

    /* Radius */
    --radius-sm: 4px;
    --radius-md: 8px;
    --radius-lg: 12px;
    --radius-full: 9999px;

    /* Transitions */
    --transition-fast: 150ms ease;
    --transition-normal: 250ms ease;
}

/* --- Reset & Base --- */
*, *::before, *::after { box-sizing: border-box; }

body {
    font-family: var(--font-family);
    font-size: var(--font-size-base);
    color: var(--text-primary);
    background-color: var(--bg-light);
    line-height: 1.6;
    margin: 0;
}

/* --- Typography --- */
h1 { font-size: var(--font-size-3xl); font-weight: 700; }
h2 { font-size: var(--font-size-2xl); font-weight: 600; }
h3 { font-size: var(--font-size-xl); font-weight: 600; }
h4 { font-size: var(--font-size-lg); font-weight: 500; }

.text-secondary { color: var(--text-secondary); }
.text-muted { color: var(--text-muted); }

/* --- Cards --- */
.card {
    background: var(--bg-white);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow-card);
    padding: var(--spacing-lg);
    transition: box-shadow var(--transition-normal);
}
.card:hover {
    box-shadow: var(--shadow-md);
}

/* Top Opportunity Card – special styling */
.card-opportunity {
    border-left: 4px solid var(--primary-blue);
    background: linear-gradient(135deg, var(--bg-white) 0%, var(--primary-blue-light) 100%);
}

/* --- Buttons --- */
.btn {
    display: inline-flex;
    align-items: center;
    gap: var(--spacing-sm);
    padding: var(--spacing-sm) var(--spacing-lg);
    font-size: var(--font-size-sm);
    font-weight: 500;
    border: 1px solid transparent;
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all var(--transition-fast);
    text-decoration: none;
}
.btn-primary {
    background-color: var(--primary-blue);
    color: white;
}
.btn-primary:hover { background-color: var(--primary-blue-hover); }
.btn-outline {
    background: transparent;
    border-color: var(--border-color-strong);
    color: var(--text-primary);
}
.btn-outline:hover { background-color: var(--bg-gray-100); }
.btn-sm { padding: var(--spacing-xs) var(--spacing-md); font-size: var(--font-size-xs); }

/* --- Data Tables --- */
.data-table {
    width: 100%;
    border-collapse: collapse;
    font-size: var(--font-size-sm);
}
.data-table thead th {
    background-color: var(--bg-gray-100);
    padding: var(--spacing-md);
    text-align: left;
    font-weight: 600;
    color: var(--text-secondary);
    border-bottom: 2px solid var(--border-color-strong);
    white-space: nowrap;
    cursor: pointer;
    user-select: none;
}
.data-table thead th:hover { color: var(--primary-blue); }
.data-table tbody td {
    padding: var(--spacing-md);
    border-bottom: 1px solid var(--border-color);
    white-space: nowrap;
}
.data-table tbody tr:hover { background-color: var(--bg-gray-50); }
.data-table tbody tr:nth-child(even) { background-color: var(--bg-gray-50); }
.data-table tbody tr:nth-child(even):hover { background-color: var(--bg-gray-100); }

/* --- KPI Badges (Qualitative) --- */
.kpi-badge {
    display: inline-block;
    padding: 2px 10px;
    font-size: var(--font-size-xs);
    font-weight: 600;
    border-radius: var(--radius-full);
    text-transform: uppercase;
    letter-spacing: 0.5px;
}
.kpi-badge-high {
    background-color: var(--success-green-bg);
    color: var(--success-green);
}
.kpi-badge-medium {
    background-color: var(--warning-yellow-bg);
    color: var(--warning-yellow);
}
.kpi-badge-low {
    background-color: var(--danger-red-bg);
    color: var(--danger-red);
}

/* --- Data Staleness Icon --- */
.staleness-icon {
    color: var(--warning-yellow);
    font-size: var(--font-size-sm);
    margin-left: var(--spacing-xs);
    cursor: help;
}

/* --- KPI Masked Value --- */
.kpi-masked {
    color: var(--text-muted);
    font-style: italic;
    letter-spacing: 2px;
}

/* --- Tooltip Styling --- */
.ui-tooltip {
    max-width: 300px;
    font-size: var(--font-size-sm);
    background: var(--text-primary);
    color: white;
    padding: var(--spacing-sm) var(--spacing-md);
    border-radius: var(--radius-sm);
    box-shadow: var(--shadow-lg);
}

/* --- Platform Card (Collapsible) --- */
.platform-card {
    background: var(--bg-white);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    padding: var(--spacing-md);
    margin-bottom: var(--spacing-sm);
    cursor: pointer;
    transition: all var(--transition-fast);
}
.platform-card:hover { border-color: var(--primary-blue); }
.platform-card.expanded { border-color: var(--primary-blue); box-shadow: var(--shadow-md); }

/* --- Sector Row --- */
.sector-row {
    padding: var(--spacing-sm) var(--spacing-md);
    border-bottom: 1px solid var(--border-color);
    cursor: pointer;
    transition: background-color var(--transition-fast);
}
.sector-row:hover { background-color: var(--primary-blue-light); }
.sector-row.selected { background-color: var(--primary-blue-light); border-left: 3px solid var(--primary-blue); }

/* --- Error Banner --- */
.error-banner {
    background-color: var(--danger-red-bg);
    border: 1px solid var(--danger-red);
    color: var(--danger-red);
    padding: var(--spacing-md) var(--spacing-lg);
    border-radius: var(--radius-md);
    margin-bottom: var(--spacing-lg);
    font-size: var(--font-size-sm);
}

/* --- Responsive --- */
@media (max-width: 1366px) {
    :root {
        --font-size-base: 0.9375rem;
    }
    .card { padding: var(--spacing-md); }
    .data-table thead th, .data-table tbody td {
        padding: var(--spacing-sm) var(--spacing-xs);
    }
}
```

---

## 15. File Structure (Source Tree)

```
mediabuying-dashboard/
├── pom.xml
├── Dockerfile
├── docker-compose.yml                  # Local dev: PostgreSQL, Redis
├── README.md
├── docs/
│   ├── dashboard_user_guide.md         # User documentation
│   └── screenshots/                    # UI screenshots for QA
├── src/
│   ├── main/
│   │   ├── java/com/autoresolve/mediabuying/
│   │   │   ├── MediaBuyingApplication.java      # @SpringBootApplication
│   │   │   ├── config/                          # Spring config beans
│   │   │   ├── controller/                      # MVC/JSF controllers
│   │   │   ├── service/                         # Business services
│   │   │   ├── integration/wrapper/            # External API wrappers
│   │   │   ├── model/entity/                   # JPA entities
│   │   │   ├── model/dto/                      # Data transfer objects
│   │   │   ├── repository/                      # JPA repositories
│   │   │   ├── messaging/                       # Spring Events message DTOs
│   │   │   ├── cache/                           # Redis cache management
│   │   │   ├── security/                        # RBAC, audit, masking
│   │   │   ├── scheduler/                       # Scheduled data refresh
│   │   │   └── util/                            # Utilities
│   │   └── resources/
│   │       ├── application.yml                  # Spring Boot config
│   │       ├── application-dev.yml              # Dev profile
│   │       ├── application-prod.yml             # Prod profile
│   │       ├── logback-spring.xml               # Logging configuration
│   │       ├── static/
│   │       │   └── css/
│   │       │       └── dashboard-theme.css      # Global CSS theme
│   │       ├── templates/
│   │       │   ├── dashboard.xhtml              # JSF dashboard page
│   │       │   ├── calculator.html              # Thymeleaf calculator page
│   │       │   ├── admin.xhtml                  # Admin panel
│   │       │   ├── login.html                   # Login page
│   │       │   └── error.html                   # Error page
│   │       ├── db/
│   │       │   └── migration/                   # Flyway migrations (Spring Boot default location)
│   │       │       ├── V1__create_platform_and_sector.sql
│   │       │       ├── V2__create_kpi_metrics.sql
│   │       │       ├── V3__create_users_and_roles.sql
│   │       │       ├── V4__create_scoring_weights.sql
│   │       │       ├── V5__create_audit_log.sql
│   │       │       ├── V6__seed_data.sql
│   │       │       ├── V7__create_client_prospects.sql
│   │       │       ├── V8__seed_client_prospects.sql
│   │       │       ├── V9__create_data_source.sql         # Source attribution
│   │       │       └── V10__create_kpi_source_attribution.sql # Source attribution join + seed
│   │       └── META-INF/
│   │           └── faces-config.xml             # JSF configuration
│   └── test/
│       ├── java/com/autoresolve/mediabuying/
│       │   ├── service/
│       │   │   ├── CompositeScoringServiceTest.java
│       │   │   ├── KPIQueryServiceTest.java
│       │   │   ├── CalculatorServiceTest.java
│       │   │   ├── SourceAttributionServiceTest.java
│       │   │   └── SourceVerificationSchedulerTest.java
│       │   ├── integration/
│       │   │   └── wrapper/
│       │   │       ├── GoogleAdsApiWrapperTest.java
│       │   │       └── MetaAdsApiWrapperTest.java
│       │   └── controller/
│       │       ├── MetricsApiControllerTest.java
│       │       └── CalculatorControllerTest.java
│       └── playwright/
│           └── e2e/
│               ├── dashboard-load.spec.js       # E2E: page load < 2s
│               ├── drilldown-navigation.spec.js  # E2E: hierarchical nav
│               ├── sector-filter.spec.js         # E2E: global filter
│               └── calculator-flow.spec.js       # E2E: ROI calculator
```

---

## 16. application.yml Configuration

```yaml
server:
  port: 6800
  address: 0.0.0.0  # Bind to all interfaces per java_web_app.md requirement

spring:
  application:
    name: media-buying-dashboard

  # PostgreSQL datasource
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/media_buying
    username: ${DB_USER:mediabuyer}
    password: ${DB_PASSWORD:changeme}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000

  # JPA / Hibernate
  jpa:
    hibernate:
      ddl-auto: validate  # Use Flyway for schema management
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc.lob.non_contextual_creation: true

  # Redis
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5

  # Spring Events (replaces Kafka — see ADR-0007)
  # Event processing uses ThreadPoolTaskExecutor configured in SpringEventConfig.java
  # All inter-stage messaging is in-JVM via ApplicationEventPublisher + @EventListener

  # Flyway
  flyway:
    enabled: true
    locations: classpath:db/migration

  # Thymeleaf
  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
    cache: false  # Disable cache in dev; enable in prod

# JSF / PrimeFaces
jsf:
  project-stage: Development  # Change to Production in prod
  primefaces:
    theme: saga
    font-awesome: true

# Scoring Weights (overridable via admin UI)
scoring:
  weights:
    roas: 0.25
    cac: 0.20
    cltv: 0.20
    conversion-rate: 0.15
    scalability: 0.10
    attribution-accuracy: 0.10
  targets:
    roas-target: 4.0
    max-cac: 50.0
    cltv-target: 500.0
    max-scalability: 1000000.0

# Integration API keys (externalized to vault in production)
integration:
  google-ads:
    api-key: ${GOOGLE_ADS_API_KEY:}
    developer-token: ${GOOGLE_ADS_DEVELOPER_TOKEN:}
    customer-id: ${GOOGLE_ADS_CUSTOMER_ID:}
    reference-url: https://developers.google.com/google-ads/api/docs/start
  meta-ads:
    api-key: ${META_ADS_API_KEY:}
    ad-account-id: ${META_ADS_ACCOUNT_ID:}
    reference-url: https://developers.facebook.com/docs/marketing-apis/
  tiktok-ads:
    api-key: ${TIKTOK_ADS_API_KEY:}
    advertiser-id: ${TIKTOK_ADVERTISER_ID:}
    reference-url: https://ads.tiktok.com/marketing_api/docs
  linkedin-ads:
    api-key: ${LINKEDIN_ADS_API_KEY:}
    account-id: ${LINKEDIN_ACCOUNT_ID:}
    reference-url: https://learn.microsoft.com/en-us/linkedin/marketing/
  iheart-radio:
    api-key: ${IHEART_RADIO_API_KEY:}
    station-id: ${IHEART_STATION_ID:}
    reference-url: https://www.iheartmedia.com/advertise
  # NEW: Ingestion scheduler configuration
  ingestion:
    interval-ms: 900000              # 15 minutes in milliseconds
    initial-delay-ms: 30000          # 30 seconds post-startup
    timeout-seconds: 600             # Max 10 min for all wrappers to complete
  # NEW: OAuth 2.0 configuration per platform
  oauth:
    google-ads:
      token-endpoint: https://oauth2.googleapis.com/token
      client-id: ${GOOGLE_ADS_CLIENT_ID:}
      client-secret: ${GOOGLE_ADS_CLIENT_SECRET:}
      refresh-token: ${GOOGLE_ADS_REFRESH_TOKEN:}
    meta-ads:
      token-endpoint: https://graph.facebook.com/v19.0/oauth/access_token
      client-id: ${META_ADS_CLIENT_ID:}
      client-secret: ${META_ADS_CLIENT_SECRET:}
      refresh-token: ${META_ADS_REFRESH_TOKEN:}
    tiktok-ads:
      token-endpoint: https://business-api.tiktok.com/open_api/v1.3/oauth2/access_token/
      client-id: ${TIKTOK_CLIENT_ID:}
      client-secret: ${TIKTOK_CLIENT_SECRET:}
      refresh-token: ${TIKTOK_REFRESH_TOKEN:}
    linkedin-ads:
      token-endpoint: https://www.linkedin.com/oauth/v2/accessToken
      client-id: ${LINKEDIN_CLIENT_ID:}
      client-secret: ${LINKEDIN_CLIENT_SECRET:}
      refresh-token: ${LINKEDIN_REFRESH_TOKEN:}
    iheart-radio:
      token-endpoint: https://api.iheart.com/v1/auth/token
      client-id: ${IHEART_CLIENT_ID:}
      client-secret: ${IHEART_CLIENT_SECRET:}
      refresh-token: ${IHEART_REFRESH_TOKEN:}
  # NEW: Per-platform rate limits (permits per second)
  rate-limit:
    google-ads: 0.02                  # ~1,200 req/day
    meta-ads: 0.055                   # ~200 req/hour
    tiktok-ads: 10.0                  # 10 req/sec
    linkedin-ads: 0.028               # ~100 req/day
    iheart-radio: 0.28                # ~1,000 req/hour

# Data refresh
data-refresh:
  dashboard-interval-minutes: 5
  kpi-staleness-threshold-minutes: 15

# Logging
logging:
  level:
    root: INFO
    com.autoresolve.mediabuying: DEBUG
    com.autoresolve.mediabuying.integration: DEBUG
    org.springframework.web: INFO
    org.hibernate.SQL: WARN

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## 17. Test Plan Summary

### 17.1 Unit Tests (JUnit 5)

| Test Class | Scope | Key Assertions |
|---|---|---|
| `CompositeScoringServiceTest` | Score computation | • All-zeros input → score = 0<br>• Max values → score = 100<br>• Weighted distribution correctness |
| `KPIQueryServiceTest` | Data retrieval | • Paginated results with correct offset<br>• Sort direction correctness<br>• Cache hit/miss behavior |
| `CalculatorServiceTest` | ROI projection | • Projected ROI matches formula<br>• Edge cases: zero budget, negative conversion rate |
| `BaseApiWrapperTest` | Integration wrapper | • Retry exhausts after 3 failures<br>• Circuit breaker opens after threshold<br>• User-friendly message on failure |
| `RecommendationsServiceTest` | Recommendations engine | • Empty data → empty lists<br>• Ranking correctness per column<br>• Cache hit/miss behavior<br>• All 12 items populated |
| `RecommendationControllerTest` | REST endpoints | • 200 response for valid type/rank<br>• 403 for non-analyst user<br>• 404 for invalid rank |

### 17.2 E2E Tests (Playwright)

| Spec File | Test Scenario | Acceptance Criteria |
|---|---|---|
| `dashboard-load.spec.js` | Full page load | ≤ 2 seconds |
| `drilldown-navigation.spec.js` | Platform → Sector → Metrics | ≤ 2 clicks to reach metrics |
| `sector-filter.spec.js` | Global sector filter | All platforms filtered; Level 2 hidden |
| `calculator-flow.spec.js` | ROI Calculator | Navigate, input, compute, return |
| `rbac-masking.spec.js` | Non-analyst view | KPI columns show `---` |
| `recommendations.spec.js` | Recommendations section | 4 columns × 3 items visible; dialog shows KPIs + sources |

---

## 18. Infrastructure LLD – Helm Charts

### 18.1 Helm Chart Structure

```
helm/media-buying-dashboard/
├── Chart.yaml                          # Chart metadata
├── values.yaml                         # Default values
├── values-dev.yaml                     # Dev environment overrides
├── values-staging.yaml                 # Staging overrides
├── values-prod.yaml                    # Production overrides
├── templates/
│   ├── _helpers.tpl                    # Template helpers (labels, names)
│   ├── deployment.yaml                 # Dashboard deployment + Kafka consumer sidecar
│   ├── service.yaml                    # ClusterIP service
│   ├── ingress.yaml                    # ALB Ingress (via AWS Load Balancer Controller)
│   ├── hpa.yaml                        # HorizontalPodAutoscaler
│   ├── serviceaccount.yaml             # IRSA service account
│   ├── secretprovider.yaml             # External Secrets Operator SecretStore
│   ├── externalsecret.yaml             # AWS Secrets Manager → K8s Secret mapping
│   ├── configmap.yaml                  # Non-sensitive app config
│   └── servicemonitor.yaml             # Prometheus ServiceMonitor
└── charts/                             # Sub-chart dependencies
```

### 18.2 Key Helm Values

```yaml
# values.yaml (default)
replicaCount: 2

image:
  repository: 123456789012.dkr.ecr.us-east-1.amazonaws.com/media-buying-dashboard
  tag: latest
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: 6800
  targetPort: 6800

ingress:
  enabled: true
  className: alb
  annotations:
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/ssl-redirect: "443"
  hosts:
    - host: dashboard.media-buying.internal
      paths:
        - path: /
          pathType: Prefix

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 12
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80
  customMetrics:
    - type: Pods
      pods:
        metric:
          name: http_server_requests_seconds_sum
        target:
          type: AverageValue
          averageValue: "0.5"

resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "2000m"

# External Secrets Operator configuration
externalSecrets:
  enabled: true
  secretStoreRef: aws-secretsmanager
  secrets:
    - remoteRef: media-buying/db-credentials
      secretKey: DB_USERNAME
      property: username
    - remoteRef: media-buying/db-credentials
      secretKey: DB_PASSWORD
      property: password
    - remoteRef: media-buying/redis-auth
      secretKey: REDIS_AUTH_TOKEN
      property: auth_token
    - remoteRef: media-buying/kafka-sasl
      secretKey: KAFKA_SASL_JAAS_CONFIG
      property: sasl_jaas_config

# Application configuration (non-sensitive, from Parameter Store)
config:
  DB_HOST: "media-buying-dev.cxxxxx.us-east-1.rds.amazonaws.com"
  DB_PORT: "5432"
  DB_NAME: "media_buying"
  REDIS_HOST: "media-buying-dev-redis.xxxxx.clustercfg.use1.cache.amazonaws.com"
  REDIS_PORT: "6379"
  REDIS_SSL: "true"
  KAFKA_BOOTSTRAP_SERVERS: "boot-xxxxx.c1.kafka-serverless.us-east-1.amazonaws.com:9098"
  DASHBOARD_REFRESH_INTERVAL: "5"
  KPI_STALENESS_THRESHOLD: "15"
  LOGGING_LEVEL: "INFO"

# Kafka consumer configuration
kafkaConsumer:
  enabled: true
  replicaCount: 1
  consumerGroup: "media-buying-kpi-consumer"
  topics: "kpi.raw,kpi.refresh,data-refresh.internal"
  resources:
    requests:
      memory: "512Mi"
      cpu: "250m"
    limits:
      memory: "1Gi"
      cpu: "500m"

# Prometheus ServiceMonitor for scraping Micrometer metrics
serviceMonitor:
  enabled: true
  interval: 30s
  path: /actuator/prometheus
```

### 18.3 Deployment Manifest (simplified)

```yaml
# templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "media-buying-dashboard.fullname" . }}
  labels:
    {{- include "media-buying-dashboard.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.replicaCount }}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 2
      maxUnavailable: 1
  selector:
    matchLabels:
      {{- include "media-buying-dashboard.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      annotations:
        checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
        checksum/secret: {{ include (print $.Template.BasePath "/externalsecret.yaml") . | sha256sum }}
    spec:
      serviceAccountName: {{ include "media-buying-dashboard.serviceAccountName" . }}
      containers:
        - name: dashboard
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: 6800
              protocol: TCP
          envFrom:
            - configMapRef:
                name: {{ include "media-buying-dashboard.fullname" . }}-config
            - secretRef:
                name: {{ include "media-buying-dashboard.fullname" . }}-secret
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 6800
            initialDelaySeconds: 60
            periodSeconds: 15
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 6800
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 2
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
```

---

## 19. Infrastructure LLD – Terraform Key Resources

### 19.1 VPC Module (key excerpt)

```hcl
# modules/networking/main.tf
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags = { Name = "media-buying-${var.environment}-vpc" }
}

# Private-Data subnets for RDS, ElastiCache, MSK
resource "aws_subnet" "private_data" {
  count             = 3
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, 20 + count.index)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags = {
    Name = "media-buying-${var.environment}-private-data-${count.index + 1}"
    Type = "Private-Data"
  }
}

# VPC Endpoints to avoid NAT costs for AWS services
resource "aws_vpc_endpoint" "secretsmanager" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${var.region}.secretsmanager"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private_data[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
}
```

### 19.2 EKS Module (key excerpt)

```hcl
# modules/eks/main.tf
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = "media-buying-${var.environment}"
  cluster_version = var.kubernetes_version

  vpc_id     = var.vpc_id
  subnet_ids = var.private_app_subnet_ids

  cluster_endpoint_public_access = var.environment != "prod"

  enable_irsa = true

  eks_managed_node_groups = {
    dashboard = {
      instance_types = [var.dashboard_instance_type]
      min_size       = var.dashboard_min_size
      max_size       = var.dashboard_max_size
      desired_size   = var.dashboard_desired_size
      subnet_ids     = var.private_app_subnet_ids
      tags           = { Purpose = "spring-boot-dashboard" }
    }
    consumer = {
      instance_types = [var.consumer_instance_type]
      min_size       = var.consumer_min_size
      max_size       = var.consumer_max_size
      desired_size   = var.consumer_desired_size
      subnet_ids     = var.private_app_subnet_ids
      taints         = [{ key = "workload", value = "kafka-consumer", effect = "NO_SCHEDULE" }]
      tags           = { Purpose = "kafka-consumer" }
    }
  }

  # IRSA for External Secrets Operator
  node_security_group_additional_rules = {
    ingress_self_all = {
      description = "Node-to-node communication"
      protocol    = "-1"
      from_port   = 0
      to_port     = 0
      type        = "ingress"
      self        = true
    }
  }
}

# IRSA role for External Secrets Operator
module "external_secrets_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  role_name = "media-buying-${var.environment}-external-secrets"
  oidc_providers = {
    ex = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:external-secrets"]
    }
  }
}
```

### 19.3 RDS Module (key excerpt)

```hcl
# modules/rds/main.tf
resource "aws_db_subnet_group" "main" {
  name       = "media-buying-${var.environment}-rds-subnet"
  subnet_ids = var.private_data_subnet_ids
  tags       = { Name = "media-buying-${var.environment}-rds-subnet" }
}

resource "aws_db_instance" "postgresql" {
  identifier     = "media-buying-${var.environment}"
  engine         = "postgres"
  engine_version = "15.8"
  instance_class = var.instance_class

  db_name  = "media_buying"
  username = jsondecode(data.aws_secretsmanager_secret_version.db_creds.secret_string)["username"]
  password = jsondecode(data.aws_secretsmanager_secret_version.db_creds.secret_string)["password"]

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = var.kms_key_arn

  multi_az               = var.multi_az
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.rds_security_group_id]

  backup_retention_period = var.backup_retention_days
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"

  deletion_protection = var.deletion_protection
  skip_final_snapshot = var.environment != "prod"

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  monitoring_interval             = var.enhanced_monitoring_interval
  monitoring_role_arn             = aws_iam_role.rds_enhanced_monitoring.arn

  tags = { Name = "media-buying-${var.environment}-postgresql" }
}

# Auto-rotation via Secrets Manager
resource "aws_secretsmanager_secret_rotation" "db" {
  secret_id           = aws_secretsmanager_secret.db_credentials.id
  rotation_lambda_arn = aws_lambda_function.db_rotation.arn
  rotation_rules {
    automatically_after_days = 30
  }
}
```

### 19.4 MSK Module (key excerpt)

```hcl
# modules/msk/main.tf
resource "aws_msk_cluster" "main" {
  cluster_name           = "media-buying-${var.environment}"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.brokers_per_az * 3

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = var.private_data_subnet_ids
    security_groups = [var.msk_security_group_id]
    storage_info {
      ebs_storage_info {
        volume_size = var.broker_storage_gb
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
    encryption_at_rest_kms_key_arn = var.kms_key_arn
  }

  client_authentication {
    sasl {
      scram = true
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  enhanced_monitoring = var.enhanced_monitoring_level

  tags = { Name = "media-buying-${var.environment}-msk" }
}

# Topic provisioning via Terraform Kafka provider
provider "kafka" {
  bootstrap_servers = [for b in aws_msk_cluster.main.bootstrap_brokers_tls : b]
  tls_enabled       = true
  sasl_mechanism    = "scram-sha-512"
  sasl_username     = jsondecode(data.aws_secretsmanager_secret_version.kafka_sasl.secret_string)["username"]
  sasl_password     = jsondecode(data.aws_secretsmanager_secret_version.kafka_sasl.secret_string)["password"]
}

resource "kafka_topic" "kpi_raw" {
  name               = "kpi.raw"
  partitions         = 6
  replication_factor = var.environment == "prod" ? 3 : 2
  config = {
    "retention.ms"        = "604800000"   # 7 days
    "cleanup.policy"      = "delete"
    "min.insync.replicas" = var.environment == "prod" ? "2" : "1"
  }
}

resource "kafka_topic" "kpi_refresh" {
  name               = "kpi.refresh"
  partitions         = 3
  replication_factor = var.environment == "prod" ? 3 : 2
  config = {
    "retention.ms"        = "3600000"     # 1 hour
    "cleanup.policy"      = "delete"
    "min.insync.replicas" = var.environment == "prod" ? "2" : "1"
  }
}

resource "kafka_topic" "data_refresh_internal" {
  name               = "data-refresh.internal"
  partitions         = 3
  replication_factor = var.environment == "prod" ? 3 : 2
  config = {
    "retention.ms"        = "86400000"    # 24 hours
    "cleanup.policy"      = "delete"
    "min.insync.replicas" = var.environment == "prod" ? "2" : "1"
  }
}
```

---

## 20. Infrastructure LLD – CI/CD Pipeline (GitHub Actions)

### 20.1 Main Workflow (simplified)

```yaml
# .github/workflows/ci-cd.yml
name: CI/CD Pipeline

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]
    tags: ['v*']

env:
  AWS_REGION: us-east-1
  ECR_REPOSITORY: media-buying-dashboard
  EKS_CLUSTER_NAME: media-buying-dev

jobs:
  # ============================================================
  # Job 1: Build & Test
  # ============================================================
  build-and-test:
    runs-on: ubuntu-latest
    outputs:
      version_tag: ${{ steps.version.outputs.tag }}
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 8
        uses: actions/setup-java@v4
        with:
          java-version: '8'
          distribution: 'corretto'
          cache: maven

      - name: Maven Build & Unit Tests
        run: mvn clean verify -Pci
        env:
          DB_HOST: localhost
          DB_PORT: 5432

      - name: SonarQube Scan
        uses: sonarsource/sonarqube-scan-action@v2
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}

      - name: Checkstyle & SpotBugs
        run: mvn checkstyle:check spotbugs:check

      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: target/surefire-reports/

  # ============================================================
  # Job 2: Docker Build & Push to ECR
  # ============================================================
  docker-build-push:
    needs: build-and-test
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to ECR
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build & Push Docker Image
        run: |
          SHORT_SHA=$(git rev-parse --short HEAD)
          docker build -t $ECR_REPOSITORY:$SHORT_SHA -t $ECR_REPOSITORY:latest .
          docker push $ECR_REPOSITORY:$SHORT_SHA
          docker push $ECR_REPOSITORY:latest

  # ============================================================
  # Job 3: Deploy to Dev (automatic on merge to main)
  # ============================================================
  deploy-dev:
    needs: docker-build-push
    runs-on: ubuntu-latest
    environment:
      name: dev
    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Update kubeconfig
        run: aws eks update-kubeconfig --name media-buying-dev --region ${{ env.AWS_REGION }}

      - name: Helm Deploy
        run: |
          SHORT_SHA=$(git rev-parse --short HEAD)
          helm upgrade --install media-buying-dashboard ./helm/media-buying-dashboard \
            -f ./helm/media-buying-dashboard/values-dev.yaml \
            --set image.tag=$SHORT_SHA \
            --namespace media-buying \
            --create-namespace \
            --wait \
            --timeout 5m

      - name: Smoke Test
        run: |
          sleep 30
          ALB_URL=$(kubectl get ingress media-buying-dashboard -n media-buying -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
          curl -sf -o /dev/null -w "%{http_code}" "https://$ALB_URL/actuator/health" | grep 200

  # ============================================================
  # Job 4: Deploy to Staging (manual approval)
  # ============================================================
  deploy-staging:
    needs: deploy-dev
    runs-on: ubuntu-latest
    environment:
      name: staging
    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Update kubeconfig
        run: aws eks update-kubeconfig --name media-buying-staging --region ${{ env.AWS_REGION }}

      - name: Helm Deploy
        run: |
          SHORT_SHA=$(git rev-parse --short HEAD)
          helm upgrade --install media-buying-dashboard ./helm/media-buying-dashboard \
            -f ./helm/media-buying-dashboard/values-staging.yaml \
            --set image.tag=$SHORT_SHA \
            --namespace media-buying \
            --create-namespace \
            --wait \
            --timeout 5m

      - name: Run Playwright E2E Tests
        run: |
          ALB_URL=$(kubectl get ingress media-buying-dashboard -n media-buying -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
          npx playwright test --config=playwright.config.ts --base-url="https://$ALB_URL"

  # ============================================================
  # Job 5: Deploy to Production (manual approval)
  # ============================================================
  deploy-prod:
    needs: deploy-staging
    runs-on: ubuntu-latest
    environment:
      name: production
    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Update kubeconfig
        run: aws eks update-kubeconfig --name media-buying-prod --region ${{ env.AWS_REGION }}

      - name: Helm Deploy (Canary 10%)
        run: |
          SHORT_SHA=$(git rev-parse --short HEAD)
          helm upgrade --install media-buying-dashboard ./helm/media-buying-dashboard \
            -f ./helm/media-buying-dashboard/values-prod.yaml \
            --set image.tag=$SHORT_SHA \
            --set autoscaling.minReplicas=1 \
            --namespace media-buying \
            --create-namespace \
            --wait \
            --timeout 5m
          echo "Canary deployed at 10%. Monitor for 10 minutes before full rollout."

      - name: Wait & Monitor Canary
        run: sleep 600  # 10 minutes observation window

      - name: Full Rollout
        run: |
          helm upgrade --install media-buying-dashboard ./helm/media-buying-dashboard \
            -f ./helm/media-buying-dashboard/values-prod.yaml \
            --set image.tag=$(git rev-parse --short HEAD) \
            --namespace media-buying \
            --wait \
            --timeout 5m

      - name: Rollback on Failure
        if: failure()
        run: helm rollback media-buying-dashboard --namespace media-buying
```

### 20.2 GitHub Environments & Protection Rules

| Environment | Required Reviewers | Wait Timer | Branch Restriction |
|-------------|-------------------|------------|-------------------|
| `dev` | None (auto-deploy) | None | `main` only |
| `staging` | DevOps Engineer (1) | None | `main` only |
| `production` | DevOps + Tech Lead (2) | None | `main` only |

### 20.3 Required GitHub Secrets

| Secret Name | Purpose |
|-------------|---------|
| `AWS_ACCESS_KEY_ID` | IAM user access key for ECR + EKS deploy |
| `AWS_SECRET_ACCESS_KEY` | IAM user secret key |
| `SONAR_TOKEN` | SonarQube project analysis token |

---

## 21. Infrastructure LLD – Docker & Monitoring

### 21.1 Dockerfile (Multi-Stage)

```dockerfile
# Stage 1: Maven Build
FROM maven:3.8.7-openjdk-8-slim AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ src/
RUN mvn clean package -DskipTests -B && \
    mkdir -p /workspace/target/dependency && \
    cd /workspace/target/dependency && \
    jar -xf ../*.jar

# Stage 2: Runtime
FROM openjdk:8-jre-alpine
LABEL maintainer="AutoResolve Engineering"
LABEL application="media-buying-dashboard"

RUN addgroup -S mediabuyer && adduser -S mediabuyer -G mediabuyer

COPY --from=builder /workspace/target/*.jar /app/app.jar

# Create non-root writable directories for logs
RUN mkdir -p /var/log/mediabuying && chown -R mediabuyer:mediabuyer /var/log/mediabuying

USER mediabuyer
EXPOSE 6800

HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD wget -q -O- http://localhost:6800/actuator/health | grep UP || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dserver.address=0.0.0.0", \
  "-jar", "/app/app.jar"]
```

### 21.2 CloudWatch Alarms (Terraform)

```hcl
# modules/security/cloudwatch-alarms.tf

# RDS CPU alarm
resource "aws_cloudwatch_metric_alarm" "rds_cpu_high" {
  alarm_name          = "media-buying-${var.environment}-rds-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "RDS CPU utilization > 80% for 15 minutes"
  alarm_actions       = [var.sns_warning_topic_arn]
  dimensions = {
    DBInstanceIdentifier = var.rds_instance_identifier
  }
}

# Kafka consumer lag alarm
resource "aws_cloudwatch_metric_alarm" "kafka_consumer_lag" {
  alarm_name          = "media-buying-${var.environment}-kafka-consumer-lag"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "EstimatedMaxTimeLag"
  namespace           = "AWS/Kafka"
  period              = 300
  statistic           = "Maximum"
  threshold           = 900  # 15 minutes in seconds
  alarm_description   = "Kafka consumer lag > 15 minutes"
  alarm_actions       = [var.sns_pagerduty_topic_arn]
  dimensions = {
    "Cluster Name" = aws_msk_cluster.main.cluster_name
    "Consumer Group" = "media-buying-kpi-consumer"
  }
}
```

### 21.3 Prometheus ServiceMonitor

```yaml
# templates/servicemonitor.yaml (Helm)
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: {{ include "media-buying-dashboard.fullname" . }}
  labels:
    release: prometheus
    {{- include "media-buying-dashboard.labels" . | nindent 4 }}
spec:
  selector:
    matchLabels:
      {{- include "media-buying-dashboard.selectorLabels" . | nindent 6 }}
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: {{ .Values.serviceMonitor.interval }}
  namespaceSelector:
    matchNames:
      - {{ .Release.Namespace }}
```

### 21.4 Custom Micrometer Metrics Implementation

The following custom application metrics must be registered via `MeterRegistry` in the service classes. These metrics feed the Grafana dashboards and Prometheus alerting rules defined in Week 11 (Observability & Ops).

#### 21.4.1 Metric Registration Pattern

```java
// In any Spring bean, inject MeterRegistry and register metrics:
@Component
public class KPIQueryService {

    private final MeterRegistry meterRegistry;
    private final Counter cacheHits;
    private final Counter cacheMisses;

    public KPIQueryService(MeterRegistry meterRegistry, ...) {
        this.meterRegistry = meterRegistry;

        this.cacheHits = Counter.builder("cache_gets_total")
            .tag("result", "hit")
            .description("Total cache hit count")
            .register(meterRegistry);

        this.cacheMisses = Counter.builder("cache_gets_total")
            .tag("result", "miss")
            .description("Total cache miss count")
            .register(meterRegistry);
    }

    public Page<KPIMetricsDTO> getMetrics(...) {
        // ... cache lookup ...
        if (cached != null) {
            cacheHits.increment();
            return cached;
        }
        cacheMisses.increment();
        // ... DB query ...
    }
}
```

#### 21.4.2 Complete Custom Metric Registry

| Metric Name | Type | Tag(s) | Registration Location | Purpose |
|---|---|---|---|---|
| `cache_gets_total` | Counter | `result=(hit\|miss)` | `KPIQueryService` | Track cache hit/miss ratio for alerting |
| `integration_api_calls_total` | Counter | `platform, result=(success\|failure)` | `BaseApiWrapper.executeWithRetry()` | Track wrapper call outcomes per platform |
| `integration_api_errors_total` | Counter | `platform, error_type` | `BaseApiWrapper.executeWithRetry()` | Track specific failure modes per platform |
| `oauth_token_refresh_errors_total` | Counter | `platform` | `OAuthTokenManager.refreshToken()` | Track OAuth token refresh failures |
| `top_opportunity_computed_time_seconds` | Gauge | — | `CompositeScoringService.calculateTopOpportunity()` | Timestamp of last top opportunity computation (epoch seconds) |
| `kpi_ingestion_timestamp_seconds` | Gauge | `platform` | `KPIStreamConsumer.consume()` | Timestamp of last successful kpi.raw ingestion per platform |
| `top_opportunity_score` | Gauge | — | `CompositeScoringService.calculateTopOpportunity()` | Current composite score value for quick health check |
| `kpi_query_latency_seconds` | Timer | `query_type` | `KPIQueryService` | KPI query performance for HPA custom metrics |

**Instrumentation is required in Week 11 (OBSV‑06)** so that Grafana dashboards populate with data and Prometheus alerts have targets to evaluate. These metrics build on the existing Prometheus endpoint configured in `application-k8s.yml` (`management.metrics.export.prometheus.enabled=true`).

### 21.5 ServiceMonitor Label Expansion for Kafka Consumer Pod

The existing `helm/media-buying-dashboard/templates/servicemonitor.yaml` uses `selector.matchLabels` with `{{ include "media-buying-dashboard.selectorLabels" . }}`. This matches both dashboard and kafka-consumer pods because they share the same `app.kubernetes.io/name` label. However, to ensure the consumer pod is scraped even if it uses different labels in the future, add an explicit `podTargetLabels` configuration:

```yaml
# In servicemonitor.yaml, add to spec:
podTargetLabels:
  - app.kubernetes.io/component
```

No other changes are needed — the consumer pod's `/actuator/prometheus` endpoint is exposed on the same `http` port.

### 21.6 Observability Infrastructure Reference

All Week 11 observability manifests are located in `infrastructure/observability/`:

| File | Purpose |
|---|---|
| `prometheus-stack-values.yaml` | kube-prometheus-stack Helm values (Prometheus 2 replicas, AlertManager, Grafana) |
| `prometheus-rules.yaml` | PrometheusRule CRD with 8 alert groups (API, JVM, cache, Kafka, DB, integration, pod, data freshness) |
| `elk-stack-values.yaml` | Elasticsearch (3 nodes) + Kibana + Filebeat DaemonSet Helm values |
| `grafana-dashboards/media-buying-dashboard.json` | Comprehensive Grafana dashboard JSON (23 panels across 6 rows) |
| `cert-manager-clusterissuer.yaml` | Let's Encrypt ClusterIssuer + Certificate resources for dashboard/Grafana/Kibana |

Deployment is orchestrated via `infrastructure/validation/setup-monitoring.sh` (updated for Week 11).

---

## 23. Source Attribution Service Implementation

### 23.1 Database Migrations

#### V9: `data_source` table

```sql
-- V9__create_data_source.sql
CREATE TABLE media_buying.data_source (
    id                  BIGSERIAL PRIMARY KEY,
    platform_id         BIGINT REFERENCES media_buying.platform(id),
    source_type         VARCHAR(20)  NOT NULL CHECK (source_type IN ('API', 'DOCUMENTATION', 'REPORT', 'MANUAL')),
    source_url          VARCHAR(1000),
    source_name         VARCHAR(200) NOT NULL,
    license_type        VARCHAR(50)  NOT NULL DEFAULT 'PROPRIETARY' CHECK (license_type IN ('PROPRIETARY', 'PUBLIC', 'OPEN')),
    last_verified_at    TIMESTAMP,
    created_at          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP
);

CREATE UNIQUE INDEX idx_data_source_name ON media_buying.data_source(source_name);
CREATE INDEX idx_data_source_platform ON media_buying.data_source(platform_id);
CREATE INDEX idx_data_source_verification ON media_buying.data_source(last_verified_at);
```

#### V10: `kpi_source_attribution` join table + seed data

```sql
-- V10__create_kpi_source_attribution.sql
CREATE TABLE media_buying.kpi_source_attribution (
    id                      BIGSERIAL PRIMARY KEY,
    kpi_metrics_id          BIGINT NOT NULL REFERENCES media_buying.kpi_metrics(id),
    data_source_id          BIGINT NOT NULL REFERENCES media_buying.data_source(id),
    attribution_context     VARCHAR(50) NOT NULL DEFAULT 'RAW' CHECK (attribution_context IN ('RAW', 'INTERPOLATED', 'DERIVED')),
    created_at              TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_kpi_source UNIQUE(kpi_metrics_id, data_source_id)
);

CREATE INDEX idx_ksa_kpi_metrics ON media_buying.kpi_source_attribution(kpi_metrics_id);
CREATE INDEX idx_ksa_data_source ON media_buying.kpi_source_attribution(data_source_id);

-- Seed data: 5 known ad-platform API sources
INSERT INTO media_buying.data_source (platform_id, source_type, source_url, source_name, license_type, last_verified_at)
VALUES
    (1, 'API', 'https://developers.google.com/google-ads/api', 'Google Ads API v17', 'PROPRIETARY', CURRENT_TIMESTAMP),
    (2, 'API', 'https://developers.facebook.com/docs/marketing-apis/', 'Meta Marketing API', 'PROPRIETARY', CURRENT_TIMESTAMP),
    (3, 'API', 'https://ads.tiktok.com/marketing_api/docs', 'TikTok Ads Manager API', 'PROPRIETARY', CURRENT_TIMESTAMP),
    (4, 'API', 'https://learn.microsoft.com/en-us/linkedin/marketing/', 'LinkedIn Marketing API', 'PROPRIETARY', CURRENT_TIMESTAMP),
    (5, 'REPORT', 'https://www.iheartmedia.com/advertise', 'iHeart Media (estimated)', 'PROPRIETARY', CURRENT_TIMESTAMP);
```

### 23.2 JPA Entities

#### `DataSource.java`
```java
package com.autoresolve.mediabuying.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "data_source", schema = "media_buying",
       indexes = {
           @Index(name = "idx_data_source_platform", columnList = "platform_id"),
           @Index(name = "idx_data_source_verification", columnList = "last_verified_at")
       })
public class DataSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_id")
    private Long platformId;

    @Column(name = "source_type", length = 20, nullable = false)
    private String sourceType;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "source_name", length = 200, nullable = false)
    private String sourceName;

    @Column(name = "license_type", length = 50, nullable = false)
    private String licenseType;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

#### `KpiSourceAttribution.java`
```java
package com.autoresolve.mediabuying.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "kpi_source_attribution", schema = "media_buying",
       indexes = {
           @Index(name = "idx_ksa_kpi_metrics", columnList = "kpi_metrics_id"),
           @Index(name = "idx_ksa_data_source", columnList = "data_source_id")
       })
public class KpiSourceAttribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kpi_metrics_id", nullable = false)
    private Long kpiMetricsId;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(name = "attribution_context", length = 50, nullable = false)
    @Builder.Default
    private String attributionContext = "RAW";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### 23.3 Repositories

#### `DataSourceRepository.java`
```java
package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DataSourceRepository extends JpaRepository<DataSource, Long> {
    Optional<DataSource> findBySourceName(String sourceName);
    List<DataSource> findByPlatformId(Long platformId);
    List<DataSource> findByLastVerifiedAtBefore(Instant threshold);
    List<DataSource> findByLastVerifiedAtIsNull();
}
```

#### `KpiSourceAttributionRepository.java`
```java
package com.autoresolve.mediabuying.repository;

import com.autoresolve.mediabuying.model.entity.KpiSourceAttribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface KpiSourceAttributionRepository extends JpaRepository<KpiSourceAttribution, Long> {

    List<KpiSourceAttribution> findByKpiMetricsId(Long kpiMetricsId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media_buying.kpi_source_attribution
            (kpi_metrics_id, data_source_id, attribution_context)
        VALUES
            (:kpiMetricsId, :dataSourceId, :attributionContext)
        ON CONFLICT (kpi_metrics_id, data_source_id) DO NOTHING
        """, nativeQuery = true)
    void upsert(@Param("kpiMetricsId") Long kpiMetricsId,
                @Param("dataSourceId") Long dataSourceId,
                @Param("attributionContext") String attributionContext);

    @Modifying
    @Transactional
    void deleteByKpiMetricsId(Long kpiMetricsId);
}
```

### 23.4 SourceAttributionService

```java
package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.dto.SourceMetadataDTO;
import com.autoresolve.mediabuying.model.entity.DataSource;
import com.autoresolve.mediabuying.model.entity.KpiSourceAttribution;
import com.autoresolve.mediabuying.repository.DataSourceRepository;
import com.autoresolve.mediabuying.repository.KpiSourceAttributionRepository;
import com.autoresolve.mediabuying.security.Auditable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SourceAttributionService {

    private static final Logger log = LoggerFactory.getLogger(SourceAttributionService.class);
    private static final long STALE_THRESHOLD_DAYS = 30;

    private final DataSourceRepository dataSourceRepository;
    private final KpiSourceAttributionRepository attributionRepository;

    public SourceAttributionService(DataSourceRepository dataSourceRepository,
                                     KpiSourceAttributionRepository attributionRepository) {
        this.dataSourceRepository = dataSourceRepository;
        this.attributionRepository = attributionRepository;
    }

    /**
     * Returns source metadata for all sources attributed to a given KPI metric row.
     */
    @Transactional(readOnly = true)
    public List<SourceMetadataDTO> getSourcesForKpi(Long kpiMetricsId) {
        List<KpiSourceAttribution> attributions = attributionRepository.findByKpiMetricsId(kpiMetricsId);
        if (attributions.isEmpty()) {
            return Collections.emptyList();
        }

        return attributions.stream()
            .map(a -> dataSourceRepository.findById(a.getDataSourceId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(this::toSourceMetadata)
            .collect(Collectors.toList());
    }

    /**
     * Links a KPI metric row to one or more data sources.
     * Uses idempotent upsert (ON CONFLICT DO NOTHING) so duplicate calls are safe.
     * If a source reference name doesn't match an existing DataSource, it is created.
     */
    @Auditable(action = "SOURCE_ATTRIBUTION_CREATED", entityType = "KpiSourceAttribution")
    @Transactional
    public void linkKpiToSources(Long kpiMetricsId, List<String> sourceReferences) {
        if (sourceReferences == null || sourceReferences.isEmpty()) {
            log.debug("No source references provided for KPI ID {}", kpiMetricsId);
            return;
        }

        for (String sourceRef : sourceReferences) {
            DataSource source = dataSourceRepository.findBySourceName(sourceRef)
                .orElseGet(() -> {
                    log.info("Creating new DataSource: name={}", sourceRef);
                    DataSource newSource = DataSource.builder()
                        .sourceName(sourceRef)
                        .sourceType("API")
                        .licenseType("PROPRIETARY")
                        .build();
                    return dataSourceRepository.save(newSource);
                });

            attributionRepository.upsert(kpiMetricsId, source.getId(), "RAW");
            log.debug("Linked KPI {} to source '{}' (id={})", kpiMetricsId, sourceRef, source.getId());
        }
    }

    /**
     * Updates the last-verified timestamp for a data source.
     */
    @Auditable(action = "SOURCE_VERIFICATION_UPDATED", entityType = "DataSource")
    @Transactional
    public void updateVerificationStatus(Long sourceId, Instant verifiedAt) {
        dataSourceRepository.findById(sourceId).ifPresent(source -> {
            source.setLastVerifiedAt(verifiedAt);
            dataSourceRepository.save(source);
            log.debug("Updated verification status for source '{}': {}", source.getSourceName(), verifiedAt);
        });
    }

    /**
     * Finds all data sources that have never been verified or were last verified > 30 days ago.
     */
    @Transactional(readOnly = true)
    public List<DataSource> findStaleSources() {
        Instant threshold = Instant.now().minus(Duration.ofDays(STALE_THRESHOLD_DAYS));
        List<DataSource> neverVerified = dataSourceRepository.findByLastVerifiedAtIsNull();
        List<DataSource> staleVerified = dataSourceRepository.findByLastVerifiedAtBefore(threshold);

        neverVerified.addAll(staleVerified);
        return neverVerified;
    }

    private SourceMetadataDTO toSourceMetadata(DataSource source) {
        boolean isStale = source.getLastVerifiedAt() == null
            || source.getLastVerifiedAt().isBefore(Instant.now().minus(Duration.ofDays(STALE_THRESHOLD_DAYS)));

        return SourceMetadataDTO.builder()
            .sourceId(source.getId())
            .sourceName(source.getSourceName())
            .sourceType(source.getSourceType())
            .sourceUrl(source.getSourceUrl())
            .licenseType(source.getLicenseType())
            .lastVerifiedAt(source.getLastVerifiedAt())
            .isStale(isStale)
            .build();
    }
}
```

### 23.5 `SourceMetadataDTO`

```java
package com.autoresolve.mediabuying.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceMetadataDTO {
    private Long sourceId;
    private String sourceName;
    private String sourceType;
    private String sourceUrl;
    private String licenseType;
    private Instant lastVerifiedAt;
    private boolean isStale;
}
```

### 23.6 Changes to `RawKPIEvent`

Add a `sourceReferences` field to support linking KPI data to its originating sources:

```java
// In RawKPIEvent.java, add after the existing fields:
private List<String> sourceReferences;   // list of source names/URLs
```

This field is populated by `KpiNormalizer` during the platform → event mapping phase, carrying source names from each platform wrapper through to the `KPIStreamConsumer`.

### 23.7 Changes to `KpiNormalizer`

In `KpiNormalizer.toRawKpiEvent()`, populate the `sourceReferences` field:

```java
// Add to toRawKpiEvent() after setting dataSource:
event.setSourceReferences(Collections.singletonList(
    response.getDataSource() != null
        ? response.getDataSource()
        : platformName + " API"));
```

### 23.8 Changes to `KPIStreamConsumer`

Inject `SourceAttributionService` into the constructor and add source attribution linking after the KPI upsert:

```java
// New constructor parameter:
private final SourceAttributionService sourceAttributionService;

// In the consume() method, after kpiMetricsRepository.upsert(metrics):
// Resolve the KPI ID for source attribution linking
Long kpiId = kpiMetricsRepository.findByPlatformIdAndSectorId(
    metrics.getPlatformId(), metrics.getSectorId())
    .map(KPIMetrics::getId)
    .orElse(null);

if (kpiId != null && event.getSourceReferences() != null
        && !event.getSourceReferences().isEmpty()) {
    sourceAttributionService.linkKpiToSources(kpiId, event.getSourceReferences());
}
```

### 23.9 REST Endpoint: `/api/kpi/{id}/sources`

```java
package com.autoresolve.mediabuying.controller.api;

import com.autoresolve.mediabuying.model.dto.SourceMetadataDTO;
import com.autoresolve.mediabuying.service.SourceAttributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kpi")
public class SourceAttributionController {

    private final SourceAttributionService sourceAttributionService;

    public SourceAttributionController(SourceAttributionService sourceAttributionService) {
        this.sourceAttributionService = sourceAttributionService;
    }

    @GetMapping("/{id}/sources")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDIA_ANALYST', 'VIEWER')")
    public ResponseEntity<List<SourceMetadataDTO>> getSourcesForKpi(@PathVariable Long id) {
        List<SourceMetadataDTO> sources = sourceAttributionService.getSourcesForKpi(id);
        return ResponseEntity.ok(sources);
    }
}
```

**Response example (200):**
```json
[
  {
    "sourceId": 1,
    "sourceName": "Google Ads API v17",
    "sourceType": "API",
    "sourceUrl": "https://developers.google.com/google-ads/api",
    "licenseType": "PROPRIETARY",
    "lastVerifiedAt": "2026-06-29T02:00:00Z",
    "isStale": false
  },
  {
    "sourceId": 6,
    "sourceName": "Google Ads API v17",
    "sourceType": "API",
    "sourceUrl": "https://developers.google.com/google-ads/api",
    "licenseType": "PROPRIETARY",
    "lastVerifiedAt": "2026-05-15T02:00:00Z",
    "isStale": true
  }
]
```

### 23.10 `SourceVerificationScheduler`

```java
package com.autoresolve.mediabuying.scheduler;

import com.autoresolve.mediabuying.model.entity.DataSource;
import com.autoresolve.mediabuying.service.SourceAttributionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

@Component
public class SourceVerificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SourceVerificationScheduler.class);

    private final SourceAttributionService sourceAttributionService;
    private final RestTemplate restTemplate;
    private final Counter staleSourceCounter;

    @Value("${source-verification.timeout-seconds:10}")
    private int timeoutSeconds;

    public SourceVerificationScheduler(SourceAttributionService sourceAttributionService,
                                        MeterRegistry meterRegistry) {
        this.sourceAttributionService = sourceAttributionService;
        this.restTemplate = new RestTemplate();
        // Configure timeouts
        this.restTemplate.setRequestFactory(new org.springframework.http.client
            .SimpleClientHttpRequestFactory() {{
                setConnectTimeout(timeoutSeconds * 1000);
                setReadTimeout(timeoutSeconds * 1000);
            }});
        this.staleSourceCounter = Counter.builder("source.verification.stale")
            .description("Count of data sources that are stale (>30 days unverified)")
            .register(meterRegistry);
    }

    /**
     * Runs daily at 2:00 AM to verify all data source URLs.
     */
    @Scheduled(cron = "${source-verification.cron:0 0 2 * * ?}")
    public void verifySourceUrls() {
        log.info("Starting source verification cycle...");
        List<DataSource> staleSources = sourceAttributionService.findStaleSources();

        if (staleSources.isEmpty()) {
            log.info("No stale sources found. Verification cycle complete.");
            return;
        }

        log.info("Found {} stale sources requiring verification", staleSources.size());

        for (DataSource source : staleSources) {
            verifySource(source);
            staleSourceCounter.increment();
        }

        log.info("Source verification cycle complete. {} sources checked.", staleSources.size());
    }

    private void verifySource(DataSource source) {
        if (source.getSourceUrl() == null || source.getSourceUrl().isEmpty()) {
            log.warn("Source '{}' has no URL — skipping verification", source.getSourceName());
            return;
        }

        long daysSinceVerification = source.getLastVerifiedAt() != null
            ? Duration.between(source.getLastVerifiedAt(), Instant.now()).toDays()
            : -1;

        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Use HEAD first; fall back to GET if 405/501
            ResponseEntity<String> response = restTemplate.exchange(
                source.getSourceUrl(), HttpMethod.HEAD, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                sourceAttributionService.updateVerificationStatus(source.getId(), Instant.now());
                log.info("Source '{}' verified successfully (HTTP {})",
                    source.getSourceName(), response.getStatusCodeValue());
            } else {
                log.warn("Source '{}' returned non-2xx status: {}",
                    source.getSourceName(), response.getStatusCodeValue());
            }
        } catch (Exception e) {
            // HEAD may not be supported; try GET
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    source.getSourceUrl(), HttpMethod.GET, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    sourceAttributionService.updateVerificationStatus(source.getId(), Instant.now());
                    log.info("Source '{}' verified (GET fallback, HTTP {})",
                        source.getSourceName(), response.getStatusCodeValue());
                }
            } catch (Exception getEx) {
                log.warn("Source '{}' failed verification. URL: {}. "
                    + "Last verified: {} days ago. Error: {}",
                    source.getSourceName(), source.getSourceUrl(),
                    daysSinceVerification, getEx.getMessage());
            }
        }
    }
}
```

### 23.11 `application.yml` Additions

```yaml
# Source Verification configuration (add to application.yml)
source-verification:
  cron: "0 0 2 * * ?"       # Daily at 2 AM
  timeout-seconds: 10        # HTTP connection/read timeout
```

### 23.12 Changes to `KPIMetricsRepository`

Add a finder method needed by `KPIStreamConsumer` for post-upsert ID resolution:

```java
// Add to KPIMetricsRepository:
Optional<KPIMetrics> findByPlatformIdAndSectorId(Long platformId, Long sectorId);
```

### 23.13 Audit Logging Integration

Two new audit action types are logged:

| Action | Entity Type | When |
|--------|-------------|------|
| `SOURCE_ATTRIBUTION_CREATED` | `KpiSourceAttribution` | Each time a KPI row is linked to a data source |
| `SOURCE_VERIFICATION_UPDATED` | `DataSource` | Each time a source's `last_verified_at` is updated |

These are triggered by the `@Auditable` annotations on `SourceAttributionService.linkKpiToSources()` and `SourceAttributionService.updateVerificationStatus()`.

### 23.14 Package Structure Additions

```
src/main/java/com/autoresolve/mediabuying/
├── model/
│   ├── entity/
│   │   ├── DataSource.java              ← NEW
│   │   └── KpiSourceAttribution.java    ← NEW
│   └── dto/
│       └── SourceMetadataDTO.java       ← NEW
├── repository/
│   ├── DataSourceRepository.java          ← NEW
│   └── KpiSourceAttributionRepository.java ← NEW
├── service/
│   └── SourceAttributionService.java      ← NEW
├── controller/api/
│   └── SourceAttributionController.java   ← NEW
└── scheduler/
    └── SourceVerificationScheduler.java   ← NEW

src/main/resources/db/migration/
├── V9__create_data_source.sql                           ← NEW
└── V10__create_kpi_source_attribution.sql               ← NEW
```

### 23.15 Implementation Order (Detailed)

| Step | File | Description | Dependencies |
|------|------|-------------|--------------|
| 1 | `V9__create_data_source.sql` | Create `data_source` table with indexes | None |
| 2 | `DataSource.java` | JPA entity for `data_source` | Step 1 |
| 3 | `V10__create_kpi_source_attribution.sql` | Create `kpi_source_attribution` join table + seed 5 platform sources | Step 1, V2 (kpi_metrics) |
| 4 | `KpiSourceAttribution.java` | JPA entity for join table | Step 3 |
| 5 | `DataSourceRepository.java` | Repository with `findBySourceName`, `findByLastVerifiedAtBefore`, `findByLastVerifiedAtIsNull` | Step 2 |
| 6 | `KpiSourceAttributionRepository.java` | Repository with `findByKpiMetricsId`, `upsert` (ON CONFLICT DO NOTHING), `deleteByKpiMetricsId` | Step 4 |
| 7 | `SourceMetadataDTO.java` | DTO for REST endpoint response | None |
| 8 | `SourceAttributionService.java` | Core service: `getSourcesForKpi()`, `linkKpiToSources()`, `updateVerificationStatus()`, `findStaleSources()` | Steps 5, 6, 7 |
| 9 | `RawKPIEvent.java` | Add `sourceReferences` field (`List<String>`) | None |
| 10 | `KpiNormalizer.java` | Populate `sourceReferences` from `PlatformApiResponse.dataSource` | Step 9 |
| 11 | `KPIMetricsRepository.java` | Add `findByPlatformIdAndSectorId()` method | Step 8 (needed for ID resolution) |
| 12 | `KPIStreamConsumer.java` | Inject `SourceAttributionService`; call `linkKpiToSources()` after upsert | Steps 8, 9, 10, 11 |
| 13 | `SourceAttributionController.java` | REST endpoint `GET /api/kpi/{id}/sources` | Step 8 |
| 14 | `SourceVerificationScheduler.java` | Scheduled daily URL verification job | Step 8 |
| 15 | `application.yml` | Add `source-verification.cron` and `source-verification.timeout-seconds` | Steps 13, 14 |
| 16 | Unit tests | `SourceAttributionServiceTest`, `SourceVerificationSchedulerTest`, `KPIStreamConsumerTest` (updated) | Steps 8, 12, 14 |

---

## 22. References

- PRD: `design/PRD.md`
- HLD: `HLD.md`
- Java Application Guide: `design/java_web_app.md`
- Google Ads API: https://developers.google.com/google-ads/api/docs/start
- Meta Marketing API: https://developers.facebook.com/docs/marketing-apis/
- TikTok Ads Manager API: https://ads.tiktok.com/marketing_api/docs
- LinkedIn Marketing API: https://learn.microsoft.com/en-us/linkedin/marketing/
- iHeartMedia Advertising: https://www.iheartmedia.com/advertise
- Webflow Technology Templates: https://webflow.com/templates/category/technology-websites
