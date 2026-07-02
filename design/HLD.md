# High-Level Design (HLD) – Media Buying Dashboard - MedBuyExcellent

---

## 1. Document Information

| Field | Value |
|---|---|
| **Project** | Media Buying Dashboard – AutoResolve Media Intelligence Platform |
| **Version** | 2.0 |
| **Status** | Draft (Data Source Architecture redesigned) |
| **Date** | June 2026 |
| **Author** | Architecture Team |
| **Related Docs** | Business_Plan.md, PRD.md, LLD.md, java_web_app.md |

---

## 2. Executive Summary

The Media Buying Dashboard is a **Java 8 / Spring Boot / PrimeFaces** web application that provides media-buying teams with a single, interactive dashboard to surface the highest-performing advertising opportunities across commerce sectors (Technology, Finance, Manufacturing, Retail, Health & Wellness, Travel, Job Market, etc.) by aggregating market intelligence from ten diverse external data sources: **pytrends, eBay, Reddit, X API (Twitter), Meta Ads Library, Yelp Fusion, Foursquare Places, Microsoft Bing Webmaster, Skyscanner, and Indeed/Adzuna**.

The system follows a **layered monolithic architecture** (evolvable toward microservices), ingests raw market data via a **four-stage ETL pipeline** (Spring Events-based, synchronous chain within each wrapper thread): **(1) Source Ingestion → (2) Sector Grouping → (3) Company/Platform Grouping → (4) KPI Building (two-phase: accumulate + batch-complete)**, computes a composite opportunity score, and renders a three-level hierarchical view (Platform → Sector → Metrics) with filtering, sorting, pagination, and data staleness indicators.

---

## 3. Architecture Style

### 3.1 Primary Pattern
- **Modular Monolith** – All business logic and rendering run in a single JVM using Spring Boot, with clear package boundaries that map to future microservices.

### 3.2 Supporting Patterns
| Pattern | Purpose |
|---|---|
| **Layered Architecture** | Strict separation between Presentation (PrimeFaces/Thymeleaf), Service (business logic), and Data (repository) layers. |
| **Wrapper Pattern** | All 10 external data-source APIs are called through wrapper classes that handle auth, retry, circuit-breaking, and error translation. |
| **Pipeline Pattern** | Raw data flows through a 4-stage pipeline: Source Ingestion → Sector Grouping → Company/Platform Grouping → KPI Building. Stages 1–3 run as a synchronous chain within each wrapper thread (via `@EventListener` without `@Async`). Stage 4a accumulates results in-memory; Stage 4b processes the batch asynchronously (`@Async`) when all wrappers complete. All inter-stage messaging uses Spring `ApplicationEventPublisher` with topic-based routing via `IntegrationEvent`. |
| **Cache-Aside** | Redis caches KPI query results and composite scores with TTL-based eviction. |
| **CQRS-Lite** | Dashboard reads use denormalized/materialized views; writes (data ingestion) flow through the ETL pipeline. |
| **Observer / Event-Driven** | Internal application events decouple data-refresh triggers from cache invalidation and UI update notifications. |

---

## 4. Technology Stack

| Layer | Technology | Version | Rationale |
|---|---|---|---|
| **Language** | Java | 8 | Compatibility with existing enterprise environment (per `java_web_app.md`) |
| **Build Tool** | Maven | 3.8+ | Standard Java build, dependency management |
| **Application Framework** | Spring Boot | 2.7.x | Mature, production-ready, last branch with full Java 8 support |
| **UI Framework** | PrimeFaces (JSF) | 12.0+ | Rich component library with DataTable, Charts, Lazy Loading; aligns with `java_web_app.md` |
| **Template Engine** | Thymeleaf | 3.1.x | Server-side rendering for SEO-friendly non-JSF pages (ROI Calculator, landing) |
| **ORM / Data Access** | Spring Data JPA + Hibernate | 5.6.x (compatible) | Standard JPA for transactional operations |
| **Database** | PostgreSQL | 15+ | Primary transactional & KPI store |
| **Distributed Cache** | Redis | 7.x | KPI query cache, session state, rate limiting |
| **Event Bus** | Spring Application Events (`ApplicationEventPublisher` + `@EventListener` + `@Async`) | Built-in (Spring Boot 2.7.x) | Zero-infrastructure inter-stage messaging for the 4-stage ETL pipeline; async processing via `ThreadPoolTaskExecutor` |
| **Logging** | SLF4J + Logback | 1.7.x / 1.4.x | Structured logging with MDC context |
| **HTTP Client** | Apache HttpClient 5 / Spring RestTemplate | 5.x | External API integration (with wrapper) |
| **Testing** | JUnit 5, Playwright | 5.x | Unit + E2E testing |
| **Monitoring** | Micrometer + Prometheus + Grafana | — | JVM metrics, API latency, cache hit ratios |
| **CI/CD** | GitHub Actions / Jenkins | — | Build, test, deploy |

**Port Binding**: The server binds to `0.0.0.0` (per `java_web_app.md`).

**Reference Top 10 Tech Stack Alignment**: This stack aligns with the **Java + Spring Boot + PostgreSQL** pattern used by enterprise companies (Netflix backend, Amazon, LinkedIn) and is validated against the stack ranking at [techloset.com](https://www.techloset.com/blog/top-10-tech-stacks-successful-companies-use).

---

## 5. Frontend Component Architecture (Updated for Weeks 6-7 MVP)

### 5.1 Visual Design Reference

The UI follows the **"MedBuy – Dashboard"** design (see `design/code.html`), implementing a bento‑grid layout with a fixed left sidebar, top app bar, and content area arranged in a 12‑column CSS Grid.

| Design Element | code.html Representation | PrimeFaces/JSF Adaptation |
|---|---|---|
| **Left Sidebar** (256px fixed) | `<aside>` with navigation links, quick stat widget | JSF composite `<mediabuy:sidebar>` / Thymeleaf fragment `sidebar :: sidebar` |
| **Top App Bar** (64px fixed) | `<header>` with search, sector filter, refresh status, user profile | JSF composite `<mediabuy:topbar>` / Thymeleaf fragment `topbar :: topbar` |
| **Bento Grid** (12‑col CSS Grid) | `<div class="bento-grid">` container | Pure CSS Grid — PrimeFaces panels rendered inside grid cells |
| **Top Opportunity Card** (8 cols) | Blue‑accent card with score badge, KPI sub‑cards, and **Top Client Prospects** table (5 companies in the selected sector ranked by ad budget, revenue, or growth rate) | `<ui:composition>` template bound to `TopOpportunityDTO` (extended with `List<ClientProspectDTO>`) |
| **ROI Trendline Card** (4 cols) | Bar chart placeholder + summary stats | `<p:panel>` with static `<div>` bars or `<p:chart>` |
| **Global Performance Table** (12 cols) | Hierarchical table with platform parent rows + sector child rows | Two‑part: collapsible `p:panel` per platform + `p:dataTable` with `LazyDataModel` on sector select |
| **Strategic Insights** (4 cols) | Alert cards with colored left‑border | `<p:panel>` with conditional styling |
| **Quick Command Center** (8 cols) | Grid of 4 action buttons | `<p:commandButton>` grid (bonus — lower priority) |
| **Floating Action Button** (fixed BR) | AI assistant button | `<p:commandButton>` with fixed CSS positioning |

### 5.2 Template Engine Split

```
┌──────────────────────────────────────────────────────────────┐
│                   Shared: dashboard-theme.css                  │
│  (CSS Grid bento layout, cards, badges, tables, typography)   │
└────────────┬─────────────────────────────┬────────────────────┘
             │                             │
    ┌────────▼──────────┐        ┌────────▼──────────┐
    │  PrimeFaces / JSF  │        │     Thymeleaf      │
    │  (XHTML templates) │        │   (HTML templates)  │
    ├────────────────────┤        ├─────────────────────┤
    │ • Dashboard page   │        │ • ROI Calculator    │
    │ • Admin panel      │        │ • Login page        │
    │ • Error pages      │        │ • Landing page      │
    │ • Ticket mgmt      │        │ • Error pages       │
    └────────┬──────────┘        └────────┬─────────────┘
             │                             │
    ┌────────▼─────────────────────────────▼──────────────┐
    │         Spring MVC Controllers / JSF Beans          │
    │  DashboardBean | SectorFilterBean | CalculatorBean  │
    └────────────────────────┬────────────────────────────┘
                             │
                    Spring Service Layer
```

**Rationale**: PrimeFaces is used for the dashboard because it provides native `LazyDataModel`, AJAX partial‑update, and rich DataTable components. Thymeleaf is used for simpler form‑based pages (ROI Calculator) because it integrates naturally with Spring MVC `@Controller` and form binding.

### 5.3 Component Tree & Data Flow

```
Page Load: GET /dashboard
├── DashboardBean.init()
│   └── DashboardService.getDashboard(sectorFilterId)
│       ├── CompositeScoringService.calculateTopOpportunity()
│       │   └── → TopOpportunityDTO { compositeScore, badge, platform, sector, kpis, topClients }
│       │       └── ClientLookupService.findTopClients(sectorId, platformId)
│       │           └── → List<ClientProspectDTO> (top 5 clients: name, revenue, growth%, ad budget est.)
│       └── PlatformSectorService.getActivePlatforms()
│           └── → List<PlatformDTO> (only ID + name; sectors loaded lazy)
│
├── Rendered as:
│   ├── <mediabuy:sidebar>        ← static links, quick‑stat widget
│   ├── <mediabuy:topbar>         ← sector filter, search, refresh status, user
│   └── <div class="bento-grid">
│       ├── <mediabuy:opportunityCard dto="#{dashboardBean.topOpportunity}">
│       │   ├── Badge rendered as <span class="kpi-badge kpi-badge-{high|medium|low}">
│       │   ├── **Top Client Prospects** table (5 rows): company name, est. revenue,
│       │   │   YoY growth %, est. ad budget, sector fit score
│       │   │   └── Sorted by: ad budget DESC | revenue DESC | growth rate DESC
│       │   └── Click → auto‑expand matching platform panel
│       ├── <div class="col-span-4"> ← ROI Trendline card
│       ├── <div class="col-span-12"> ← Hierarchical platform list
│       │   └── For each platform:
│       │       ├── <p:panel toggleable="true" ...>
│       │       │   └── On toggle → AJAX load sectors via PlatformSectorService
│       │       └── On sector click → AJAX load <p:dataTable> via LazyDataModel
│       ├── <div class="col-span-4"> ← Strategic Insights (static/optional)
│       └── <div class="col-span-8"> ← Quick Command Center (bonus)
│
└── <button class="fab"> ← Floating action button (fixed BR)
```

### 5.4 Key Frontend Architecture Decisions

| Decision | Rationale | Trade-Off |
|---|---|---|
| **CSS Grid** over PrimeFaces `PanelGrid` | PrimeFaces `PanelGrid` renders as `<table>` — cannot achieve bento layout. CSS Grid provides true 2D layout. | CSS classes must be manually maintained; no PrimeFaces AJAX for grid layout changes (acceptable — layout is static). |
| **Composite Components** for sidebar/topbar | Reusability across dashboard, calculator (Thymeleaf needs fragments), and future pages. JSF composite `<mediabuy:sidebar>` ensures consistent rendering. | Minor duplication: sidebar HTML exists in two forms (JSF composite + Thymeleaf fragment). |
| **AJAX Sector Load** (not pre‑loaded) | Reduces initial page payload. Sectors are fetched only when a platform is expanded. | Adds ~200ms latency on expand. Acceptable per PRD drill‑down ≤ 1s. |
| **Separate `p:dataTable` per sector** (not tree table) | Matches PRD 3‑level hierarchy. `p:treeTable` does not support 13‑column horizontal layout well. | More JSF state management (multiple table instances). Mitigated by lazy loading — only 1 table visible at a time. |
| **Thymeleaf for Calculator** (not JSF) | Calculator is a simple form with validation + results panel. Thymeleaf integrates naturally with Spring MVC `@PostMapping` and `BindingResult`. | Two template engines in one app adds complexity. Mitigated by shared CSS and fragment duplication management. |
| **`?sector=` URL param** over session state | Makes dashboard shareable/bookmarkable. Aligns with PRD §3.3. | Requires JavaScript-free redirect (`window.location` or server redirect); PrimeFaces AJAX does not change URL natively. |
| **`dataStale` flag** from backend DTO | Backend (KPIQueryService) already computes staleness. Frontend only needs conditional rendering. | Frontend must trust server clock. Acceptable — server is authoritative. |
| **Optional cards** (Insights, Command Center) deferred | Not in PRD acceptance criteria. Can be added as bonus after core features pass. | code.html shows them, but they add scope risk. Implement as low‑priority placeholders. |
| **Client Prospect List in Top Opportunity card** | Surface high‑value target companies within the winning commerce sector. Each client is scored by estimated advertising budget, annual revenue, and YoY growth rate. Data comes from `client_prospects` table populated via a lightweight research ETL (or manual curation). The top 5 clients are displayed in the opportunity card. | Requires a `client_prospects` database table, a `ClientLookupService`, and a `ClientProspectDTO`. Adds ~50ms to page load (cached). Prospect data accuracy depends on curation frequency. |

---
### 5.5 Client List Section

The dashboard will include a dedicated **Client List** region beneath the Top Opportunity card.

**Client Grid** – A PrimeFaces `<p:dataTable>` (or Thymeleaf table) displaying the following columns:
1. **Client Name** – Company name.
2. **Commerce Sector** – Linked to the `commerce_sector` table.
3. **Media Buying Contract** – Description of the contract type (e.g., "Full‑Service", "Performance‑Based").
4. **Retention Period** – Duration of the contract (e.g., "12 months").
5. **Outlook Score** – Numeric score (0‑100) indicating future revenue potential.

The grid is populated via `ClientListService` which returns a paginated list of fake client records for the MVP. Sorting and pagination are enabled.

**LLM‑powered Insights Paragraph** – Directly below the client grid, an LLM‑generated paragraph provides strategic insights about gaps in currently “hot” commerce sectors. The `InsightsEngine` service calls the NVIDIA NIM LLM with aggregated client and market data, producing a concise recommendation paragraph displayed in a `<div class="insights‑panel">`. The paragraph updates on each dashboard load.

**Security & RBAC** – The client grid respects the existing RBAC model: non‑analyst users see the grid rows masked (`---`) for the `Outlook Score` column.

---

### 5.6 Recommendations Section

The analyst dashboard includes a top-of-page **Recommendations Section** that surfaces the 12 highest-value media buying opportunities across four dimensions. This section sits above the existing bento-grid content and is always visible on dashboard load.

#### 5.6.1 Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│  Recommendations — Top Opportunities for Media Buying               │
├─────────────┬─────────────┬─────────────┬───────────────────────────┤
│ A. Top      │ B. Top      │ C. Top      │ D. Top Platform +        │
│ Commerce    │ Sector +    │ Advertising │ Sector Combination        │
│ Sectors     │ Client      │ Platforms   │                           │
│             │             │             │                           │
│ 1. Tech     │ 1. Tech +   │ 1. Google   │ 1. Google Ads + Tech     │
│    [More]   │    Apple    │    Ads      │    [More]                 │
│             │    [More]   │    [More]   │                           │
│ 2. Finance  │ 2. Finance+ │ 2. Meta Ads │ 2. Meta Ads + Retail     │
│    [More]   │    Chase    │    [More]   │    [More]                 │
│             │    [More]   │             │                           │
│ 3. Retail   │ 3. Retail + │ 3. TikTok   │ 3. LinkedIn + Finance    │
│    [More]   │    Walmart  │    [More]   │    [More]                 │
│             │    [More]   │             │                           │
└─────────────┴─────────────┴─────────────┴───────────────────────────┘
```

Each **[More]** link is a clickable "Additional Information" hyperlink.

#### 5.6.2 Component Breakdown

| Component | Responsibility | Data Binding |
|-----------|---------------|--------------|
| **RecommendationsSection** | Container for the 4‑column bento grid; positioned at the top of the dashboard content area. | `RecommendationsBean.recommendations` |
| **RecommendationColumn** | Renders a single column (header + 3 recommendation rows). Reused for all 4 types. | `List<RecommendationDTO>` |
| **RecommendationRow** | Displays one recommendation item (rank number, title, "Additional Information" link). | Single `RecommendationDTO` |
| **RecommendationDialog** | Modal pop-up showing supporting KPIs and source links for the selected recommendation. | `RecommendationsBean.selectedRecommendation` |

#### 5.6.3 Data Flow

```
Page Load: GET /dashboard
├── DashboardBean.init()
│   ├── DashboardService.getDashboard(sectorFilterId)
│   │   └── RecommendationsService.getRecommendations()
│   │       ├── Column A: getTopCommerceSectors()       → 3 RecommendationDTO
│   │       ├── Column B: getTopSectorClientCombos()     → 3 RecommendationDTO
│   │       ├── Column C: getTopAdvertisingPlatforms()   → 3 RecommendationDTO
│   │       └── Column D: getTopPlatformSectorCombos()   → 3 RecommendationDTO
│   └── → RecommendationsDTO { columns: List<List<RecommendationDTO>> }
│
└── Rendered as:
    ├── <section class="recommendations-section">
    │   ├── <div class="recommendation-column"> (×4)
    │   │   ├── <h3>Column Title</h3>
    │   │   └── <div class="recommendation-item"> (×3)
    │   │       ├── <span class="rank">1.</span>
    │   │       ├── <span class="title">Recommendation Name</span>
    │   │       └── <a href="#" class="more-link">Additional Information</a>
    │   └── </div>
    │
    └── <p:dialog id="recommendationDialog">
        ├── Supporting KPIs list
        └── Source attribution links
```

#### 5.6.4 Key Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| **Separate `RecommendationsService`** over bundling into `DashboardService` | Keeps the 4 ranking algorithms isolated and independently testable. Each ranking type can be cached with distinct TTLs. |
| **Single `RecommendationDTO`** reused for all 4 column types | Contains `type` enum field to distinguish column context; the dialog rendering adapts based on type. Reduces DTO count. |
| **Dialog loads on-demand** via AJAX `actionListener` | Avoids fetching KPI + source data for all 12 recommendations on page load. Only the clicked recommendation's details are fetched. |
| **Source attribution reuses `SourceAttributionService`** | Consistent with the existing KPI source citation pattern (§5.8). The `RecommendationDialog` calls the same service to resolve source metadata. |

---

## 6. System Context Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    External Systems                                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐ ┌──────────┐│
│  │Pytrends  │ │  eBay    │ │  Reddit  │ │ X (Tweet)│ │Meta Ads  │ │   Yelp    │ │ Foursquare││
│  │ (Google  │ │  API     │ │   API    │ │   API    │ │ Library  │ │  Fusion   │ │  Places   ││
│  │  Trends) │ └────┬─────┘ └────┬─────┘ └────┬─────┘ │   API    │ │   API     │ │   API     ││
│  └────┬─────┘      │           │           │        └────┬─────┘ └─────┬─────┘ └─────┬─────┘│
│       │            │           │           │             │            │            │       │
│  ┌────▼─────┐ ┌────▼─────┐ ┌───▼──────┐ ┌──▼──────┐                                       │
│  │Microsoft │ │Skyscanner│ │ Indeed / │ │ (more    │                                       │
│  │Bing Webm │ │Flights & │ │ Adzuna   │ │ sources  │                                       │
│  │  API     │ │ Hotels   │ │   API    │ │  ...     │                                       │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘                                        │
└───────┼────────────┼───────────┼────────────┼───────────────────────────────────────────────┘
        │            │           │            │
        ▼            ▼           ▼            ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                              4-Stage Data Pipeline (Spring Events)                           │
│  ┌──────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────┐ │
│  │ Stage 1: Source  │→│ Stage 2: Sector Group │→│ Stage 3: Company/Plat │→│ Stage 4: KPI │ │
│  │   Ingestion      │  │   (Commerce Sector    │  │   Grouping            │  │   Building   │ │
│  │  @Async publish  │  │    @EventListener     │  │    @EventListener     │  │ @EventListener│ │
│  │  Event via       │  │    classification     │  │   mapping)            │  │ (Score &     │ │
│  │  EventPublisher  │  │                       │  │                       │  │  aggregate)  │ │
│  └──────────────────┘  └──────────────────────┘  └──────────────────────┘  └──────────────┘ │
└────────────────────────────┬─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    Media Buying Dashboard (Spring Boot)                    │
│                                                                          │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                      Presentation Layer                            │  │
│  │  ┌─────────────────────┐  ┌──────────────────────────────────┐   │  │
│  │  │  JSF Views (XHTML)  │  │  Thymeleaf Templates (HTML)      │   │  │
│  │  │  - Dashboard         │  │  - ROI Calculator                │   │  │
│  │  │  - Ticket Mgmt       │  │  - Landing Page                  │   │  │
│  │  │  - Admin Panel       │  │  - Error Pages                   │   │  │
│  │  └──────────┬──────────┘  └───────────────┬──────────────────┘   │  │
│  │             │                             │                       │  │
│  │  ┌──────────▼─────────────────────────────▼──────────────────┐   │  │
│  │  │           Managed Beans / Spring MVC Controllers          │   │  │
│  │  │  - DashboardBean         - SectorFilterBean               │   │  │
│  │  │  - MetricsTableBean      - CalculatorController           │   │  │
│  │  └────────────────────────────┬──────────────────────────────┘   │  │
│  └───────────────────────────────┼──────────────────────────────────┘  │
│                                  │                                      │
│  ┌───────────────────────────────▼──────────────────────────────────┐  │
│  │                       Service Layer                               │  │
│  │  ┌───────────────────────────────────────────────────────────┐   │  │
│  │  │  CompositeScoringService  │  KPIQueryService              │   │  │
│  │  │  OpportunityService       │  PlatformSectorService         │   │  │
│  │  │  CalculatorService        │  CsvExportService              │   │  │
│  │  │  CacheInvalidationService │  RBACService                   │   │  │
│  │  └───────────────────────────┬───────────────────────────────┘   │  │
│  └──────────────────────────────┼───────────────────────────────────┘  │
│                                 │                                       │
│  ┌──────────────────────────────▼──────────────────────────────────┐   │
│  │                      Integration Layer                            │   │
│  │  ┌────────────────────────────────────────────────────────┐      │   │
│  │  │  Integration Wrappers (with error → user‑message)       │      │   │
│  │  │  - GoogleAdsApiWrapper    - MetaAdsApiWrapper            │      │   │
│  │  │  - TikTokApiWrapper       - LinkedInApiWrapper           │      │   │
│  │  │  - IHeartRadioApiWrapper                                 │      │   │
│  │  └────────────────────────────────────────────────────────┘      │   │
│  └──────────────────────────────┬───────────────────────────────────┘   │
│                                 │                                       │
│  ┌──────────────────────────────▼──────────────────────────────────┐   │
│  │                       Data Access Layer                           │   │
│  │  ┌───────────────┐  ┌───────────────┐  ┌────────────────────┐    │   │
│  │  │ Spring Data   │  │ Redis Cache   │  │ Event Listeners    │    │   │
│  │  │ JPA Repos     │  │ (Lettuce)     │  │ (@EventListener)   │    │   │
│  │  └───────┬───────┘  └───────┬───────┘  └────────┬───────────┘    │   │
│  └──────────┼──────────────────┼───────────────────┼───────────────┘   │
└─────────────┼──────────────────┼───────────────────┼───────────────────┘
              │                  │                   │
              ▼                  ▼                   ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                        Data & Infrastructure                             │
│  ┌───────────────┐  ┌───────────────┐                                    │
│  │  PostgreSQL   │  │    Redis      │                                    │
│  │  (RDS/on-prem)│  │  (ElastiCache) │                                    │
│  └───────────────┘  └───────────────┘                                    │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────────┐│
│  │  Kubernetes (EKS / on-prem) – Horizontal Pod Autoscaling             ││
│  │  Prometheus + Grafana Stack                                          ││
│  │  ELK Stack (Elasticsearch, Logstash, Kibana)                         ││
│  └──────────────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Data Flow – Key User Journeys

### 6.1 Dashboard Page Load
```
Browser ──GET /dashboard──► Spring MVC / JSF Controller
                                │
                                ├─► DashboardService.getDashboard()
                                │       │
                                 │       ├─► CompositeScoringService.calculateTopOpportunity()
                                 │       │       │
                                 │       │       ├─► Redis Cache: "composite:top" (TTL 5 min)
                                 │       │       │       └─► IF MISS: Query KPI table, compute, store
                                 │       │       │       └─► Also calls ClientLookupService.findTopClients()
                                 │       │       │               for the winning sector/platform pair
                                 │       │       └─► Return TopOpportunityDTO (includes top 5 ClientProspectDTO)
                                 │       │
                                 │       ├─► ClientLookupService.findTopClients(sectorId, platformId)
                                 │       │       │
                                 │       │       ├─► Redis Cache: "clients:top:{sector}" (TTL 15 min)
                                 │       │       │       └─► IF MISS: Query client_prospects table WHERE
                                 │       │       │           sector_id = ? ORDER BY est_ad_budget DESC,
                                 │       │       │           annual_revenue DESC, growth_rate DESC LIMIT 5
                                 │       │       └─► Return List<ClientProspectDTO>
                                 │       │
                                │       │
                                │       ├─► PlatformSectorService.getPlatformsWithSectors()
                                │       │       │
                                │       │       ├─► Redis Cache: "hierarchy:all" (TTL 5 min)
                                │       │       │       └─► IF MISS: Lazy-load platforms; sectors loaded
                                │       │       │           on expand (client-side AJAX/LazyDataModel)
                                │       │       └─► Return List<PlatformDTO>
                                │       │
                                │       └─► Return DashboardModel
                                │
                                ├─► model.addAttribute("topOpportunity", ...)
                                ├─► model.addAttribute("platforms", lazyDataModel)
                                └─► Return "dashboard.xhtml" (JSF) / "dashboard.html" (Thymeleaf)

Browser ◄── HTML + CSS + JS (PrimeFaces widgets) ──┤
```

### 6.2 Drill-Down (Platform → Sector → Metrics)
```
Browser ──AJAX (expand platform)──► DashboardBean.onPlatformExpand()
                                        │
                                        ├─► KPIQueryService.getMetricsForPlatform(platformId, sectorFilter?)
                                        │       │
                                        │       ├─► Redis Cache: "metrics:{platform}:{sector}" (TTL 5 min)
                                        │       └─► Return List<KPIMetricsDTO> (paginated)
                                        │
                                        └─► PrimeFaces LazyDataModel.load() → partial-update #metricsTable

Browser ◄── Partial HTML update (AJAX) ──┤
```

### 6.3 Global Sector Filter
```
Browser ──Change dropdown──► SectorFilterBean.onSectorChange(sectorId)
                                  │
                                  ├─► Redirect: GET /dashboard?sector={sectorId}
                                  │
                                  └─► DashboardBean.init()
                                          │
                                          ├─► Applies sector filter to all queries
                                          ├─► Hides Level‑2 (sector list) in UI
                                          └─► Expands platforms directly to metrics table
```

---

## 8. Component Design Summary

| Component | Responsibility | Key Technologies |
|---|---|---|
| **DashboardService** | Orchestrates dashboard page load: calls scoring, platform/sector, KPI, and client-prospect services; assembles `DashboardModel` for the presentation layer. | Spring `@Service` |
| **ClientLookupService** | Identifies high‑value target companies within a specific commerce sector and ad platform. Ranks prospects by estimated advertising budget, annual revenue, and YoY growth rate. Returns top N (default 5) clients for display in the Top Opportunity card. | Spring `@Service`, `ClientProspectRepository` |
| **SourceAttributionService** | Tracks, stores, and retrieves provenance metadata for all KPI data points. Resolves which external platform and data source produced each metric; supports audit and verification workflows. | Spring `@Service`, `EntityManager` |
| **DashboardBean** | JSF managed bean for dashboard rendering; manages lazy-load pagination and AJAX partial updates. | JSF Managed Bean, PrimeFaces LazyDataModel |
| **CompositeScoringService** | Computes weighted composite score from six KPI inputs; returns Top Opportunity. | Spring `@Service`, configurable `ScoringWeightConfig` |
| **KPIQueryService** | Queries KPI data for platform‑sector pairs with pagination, sorting, and Redis cache-aside. | Spring Data JPA, Redis Cache |
| **PlatformSectorService** | Manages platform and sector domain objects; handles lazy expansion from the UI. | Spring Data JPA |
| **IntegrationWrappers** | Wrap calls to external ad platform APIs (Google Ads, Meta, TikTok, LinkedIn, iHeart). All errors caught and translated to user-facing messages. | Spring RestTemplate, Resilience4j CircuitBreaker |
| **AdPlatformIngestionScheduler** | Central orchestrator that periodically invokes all platform wrappers (every 15 minutes), normalizes responses into `RawKPIEvent` DTOs, and publishes them as Spring `ApplicationEventPublisher` events to the `kpi.raw` event topic. Implements per-platform isolation so one platform's failure does not block others. | Spring `@Scheduled`, `@Async`, `CompletableFuture` |
| **KpiNormalizer** | Maps platform‑specific `PlatformApiResponse` → canonical `RawKPIEvent` format. Handles BigDecimal‑to‑Double conversion, platform‑name normalization, and sector inference. | Java 8 Streams, Jackson |
| **OAuthTokenManager** | Manages OAuth 2.0 token lifecycle for all platform wrappers. Provides token provision, refresh, and caching with a thread‑safe `ConcurrentHashMap`. | Spring `@Service`, RestTemplate |
| **AdPlatformRateLimiter** | Per‑platform rate limiter using token‑bucket or fixed‑window strategy. Prevents wrappers from exceeding API quotas defined in `application.yml`. | Guava `RateLimiter` or Resilience4j `RateLimiter` |
| **CacheInvalidationService** | Listens for `KpiRefreshEvent` (via Spring `@EventListener` on the `kpi.refresh` event topic) and purges all relevant Redis keys. | Spring `@EventListener`, Redis |
| **RBACService** | Enforces role-based access; masks KPI columns for non-`Media Analyst` users. | Spring Security, Custom JSF `@Renderer` |
| **CsvExportService** | Generates CSV from current metrics table view. | Apache Commons CSV / OpenCSV |
| **CalculatorService** | Runs ROI projection algorithm based on user-defined parameters and baseline KPI metrics. | Spring `@Service` |
| **AuditLoggingAspect** | Logs all user actions and service method calls for the audit trail. | Spring AOP, SLF4J |
| **ClientListService** | Retrieves list of current clients with fake data, supports sorting/pagination for UI grid. | Spring `@Service`, `ClientRepository` |
| **InsightsEngine** | Calls NVIDIA NIM LLM to generate insights paragraph about hot commerce sector gaps based on client data. | Spring `@Service`, HTTP client to NVIDIA NIM |
| **RecommendationsService** | Computes 12 ranked media‑buying recommendations across 4 dimensions (top commerce sectors, sector+client combos, top platforms, platform+sector combos). Provides `getAdditionalInfo(type, rank)` for on‑demand KPI evidence and source attribution retrieval. Results cached in Redis with 5‑min TTL. | Spring `@Service`, `KPIMetricsRepository`, `ClientProspectRepository`, `SourceAttributionService`, Redis Cache |

---

## 9. Integration Points &amp; APIs

### 9.1 External Data Source APIs (all called through Wrappers)

The dashboard ingests market-intelligence data from **10 external sources** grouped by domain. Each source has a dedicated wrapper class that normalizes raw API responses into a canonical `RawSourceData` format for the 4-stage pipeline.

| # | Source | API Reference URL | Wrapper Class | Auth Method | Primary Data |
|---|---|---|---|---|---|
| 1 | **Pytrends** | [pytrends (GitHub)](https://github.com/GeneralMills/pytrends) | `PytrendsApiWrapper` | No auth (public Google Trends data) | Search interest trends by keyword/region |
| 2 | **eBay** | [eBay Finding API](https://developer.ebay.com/Devzone/finding/Concepts/FindingAPIGuide.html) | `EbayApiWrapper` | App ID (OAuth 2.0) | Product listings, sold prices, category trends |
| 3 | **Reddit** | [Reddit API](https://www.reddit.com/dev/api/) | `RedditApiWrapper` | OAuth 2.0 (script app) | Subreddit sentiment, trending topics, community size |
| 4 | **X API (Twitter)** | [X API v2](https://developer.twitter.com/en/docs/twitter-api) | `XApiWrapper` | OAuth 1.0a / OAuth 2.0 | Tweet volume, trending hashtags, engagement metrics |
| 5 | **Meta Ads Library** | [Meta Ad Library API](https://www.facebook.com/ads/library/api/) | `MetaAdsLibraryWrapper` | Access Token (FB app) | Active ad counts, spend ranges, demographic reach |
| 6 | **Yelp Fusion** | [Yelp Fusion API](https://fusion.yelp.com/) | `YelpFusionApiWrapper` | API Key (Bearer) | Business reviews, ratings, category, location |
| 7 | **Foursquare Places** | [Foursquare Places API](https://docs.foursquare.com/developer/reference/places-api) | `FoursquarePlacesWrapper` | API Key (Bearer) | Venue popularity, foot-traffic patterns, check-ins |
| 8 | **Microsoft Bing Webmaster** | [Bing Webmaster API](https://learn.microsoft.com/bingwebmaster/) | `BingWebmasterWrapper` | API Key (OAuth 2.0) | Search volume, click-through rate, keyword ranking |
| 9 | **Skyscanner** | [Skyscanner APIs](https://www.partners.skyscanner.net/) | `SkyscannerApiWrapper` | API Key (Bearer) | Flight prices, hotel rates, travel demand trends |
| 10 | **Indeed / Adzuna** | [Adzuna API](https://developer.adzuna.com/) / [Indeed Publisher](https://www.indeed.com/hire/api) | `JobMarketApiWrapper` | API Key (Adzuna) | Job postings, salary data, hiring demand by sector/region |

**Wrapper Pattern Requirement** (from design guidelines):
- All integrations **must** be called inside a wrapper class.
- Wrappers handle: authentication, retry logic (3 attempts with exponential backoff), circuit breaking (Resilience4j), rate limiting, and error translation into user-facing messages.
- If an external API fails, the wrapper logs the error via SLF4J and throws a custom `IntegrationUnavailableException` that the UI layer renders as "Data temporarily unavailable for {Source}."

### 9.2 Four-Stage Data Processing Pipeline (Spring Events)

This is the **critical integration wiring** that transforms raw market-intelligence data from 10 external sources into actionable KPI metrics through a 4-stage ETL pipeline, each stage decoupled by Spring's `ApplicationEventPublisher` + `@EventListener`. Stages 1–3 run **synchronously within each wrapper thread** to form a deterministic chain; Stage 4 accumulates results and processes them asynchronously on a batch-complete signal.

#### 9.2.1 Pipeline Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                    DataSourceIngestionScheduler (@Scheduled, every 15 min)              │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐│
│  │  Fires all 10 wrappers in parallel via @Async + CompletableFuture.allOf()        ││
│  │  ┌─────────────┐  ┌──────────────┐         ┌──────────────┐                      ││
│  │  │Pytrends     │  │EbayWrapper   │  ...10  │JobMarket     │                      ││
│  │  │Wrapper       │  │              │         │Wrapper        │                      ││
│  │  └──────┬──────┘  └──────┬───────┘         └──────┬───────┘                      ││
│  │         │                │                         │                               ││
│  │         ▼                ▼                         ▼                               ││
│  │  ┌───────────────────────────────────────────────────────────────────────────┐   ││
│  │  │              Stage 1: Normalization (within each wrapper thread)            │   ││
│  │  │  SourceDataNormalizer: RawSourceData → NormalizedSourceMessage             │   ││
│  │  │  eventBus.publish("source.raw", sourceName, msg)                           │   ││
│  │  └───────────────────────────────┬───────────────────────────────────────────┘   ││
│  │                                  │ Spring Event: NormalizedSourceMessage          ││
│  │                                  ▼ (same wrapper thread — synchronous chain)       ││
│  │  ┌───────────────────────────────────────────────────────────────────────────┐   ││
│  │  │             Stage 2: Sector Classification (no @Async — sync)               │   ││
│  │  │  SectorGrouper: classifies into commerce sector(s)                         │   ││
│  │  │  eventBus.publish("sector.grouped", sectorKey, result)                     │   ││
│  │  └───────────────────────────────┬───────────────────────────────────────────┘   ││
│  │                                  │ Spring Event: SourceSectorMappingMessage        ││
│  │                                  ▼ (same wrapper thread — synchronous chain)       ││
│  │  ┌───────────────────────────────────────────────────────────────────────────┐   ││
│  │  │            Stage 3: Company/Platform Mapping (no @Async — sync)             │   ││
│  │  │  CompanyPlatformGrouper: identifies companies, maps to ad platforms        │   ││
│  │  │  Upserts into companies table                                              │   ││
│  │  │  eventBus.publish("company.grouped", companyKey, result)                   │   ││
│  │  └───────────────────────────────┬───────────────────────────────────────────┘   ││
│  │                                  │ Spring Event: CompanyPlatformMappingMessage     ││
│  │                                  ▼ (same wrapper thread — synchronous chain)       ││
│  │  ┌───────────────────────────────────────────────────────────────────────────┐   ││
│  │  │           Stage 4a: KPI Accumulation (no @Async — sync)                     │   ││
│  │  │  KpiBuilder: stores CompanyPlatformMappingMessage in ConcurrentHashMap     │   ││
│  │  │  (No DB write yet — just accumulation for batch processing)                 │   ││
│  │  └───────────────────────────────────────────────────────────────────────────┘   ││
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
│  After all 10 wrappers complete (allOf.get(timeout)):                                 │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐│
│  │  eventBus.publish("pipeline.batch-complete", "batch", new PipelineBatchComplete())││
│  └────────────────────────────────┬─────────────────────────────────────────────────┘│
└───────────────────────────────────┼───────────────────────────────────────────────────┘
                                    │ Spring Event: PipelineBatchCompleteEvent
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                     Stage 4b: KPI Building (@Async @EventListener)                      │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐│
│  │  Listens for "pipeline.batch-complete" event                                     ││
│  │  1. Drains all accumulated CompanyPlatformMappingMessage from ConcurrentHashMap   ││
│  │  2. Groups by (company + sector + platform)                                       ││
│  │  3. Aggregates multi-source signals per group using weighted blending rules       ││
│  │  4. Builds KPIMetrics entity with 13 KPI values                                   ││
│  │  5. Upserts each into kpi_metrics (INSERT ... ON CONFLICT DO UPDATE)              ││
│  │  6. Publishes KpiRefreshEvent for each upserted (platformId, sectorId) pair        ││
│  └───────────────────────────────────────────┬──────────────────────────────────────┘│
└──────────────────────────────────────────────┼────────────────────────────────────────┘
                                               │ Spring Event: KpiRefreshEvent
                                               ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                     CacheInvalidationService (@Async @EventListener)                   │
│  Listens for "kpi.refresh" events                                                     │
│  → Deletes Redis keys: composite:top, hierarchy:all, metrics:{p}:{s}:*, clients:*    │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

#### 9.2.2 Event Bus Topics (Spring Events)

| Event Bus Topic | Event DTO | Publisher | Consumer | Async? |
|---|---|---|---|---|
| `source.raw` | `NormalizedSourceMessage` | `DataSourceIngestionScheduler` (within each wrapper thread) | `SectorGrouper.onSourceRaw()` | **No** (sync chain) |
| `sector.grouped` | `SourceSectorMappingMessage` | `SectorGrouper` (same wrapper thread) | `CompanyPlatformGrouper.onSectorGrouped()` | **No** (sync chain) |
| `company.grouped` | `CompanyPlatformMappingMessage` | `CompanyPlatformGrouper` (same wrapper thread) | `KpiBuilder.onCompanyGrouped()` — accumulate only | **No** (sync chain) |
| `pipeline.batch-complete` | `PipelineBatchCompleteEvent` | `DataSourceIngestionScheduler` (scheduler thread, after all wrappers) | `KpiBuilder.onBatchComplete()` — compute & upsert | **Yes** (`@Async`) |
| `kpi.refresh` | `KpiRefreshEvent` | `KpiBuilder` (async thread, after upsert) | `CacheInvalidationService.onKpiRefresh()` | **Yes** (`@Async`) |
| `data-refresh.internal` | `String` (command) | `DataRefreshProducer` | `DataRefreshScheduler.onRefreshCommand()` | **No** |

#### 9.2.3 Key Pipeline Design Decisions

| Decision | Rationale |
|---|---|
| **Spring Events instead of Kafka** | Each stage publishes/publishes via `ApplicationEventPublisher.publishEvent()` wrapped through the `EventBus` interface. Since all stages run in the same JVM (modular monolith), inter-process messaging is unnecessary. Spring Events provide zero-infrastructure decoupling. See [ADR-0007](/docs/adr/0007-spring-events-over-kafka.md). |
| **Synchronous chain for Stages 1–3** | Stages 2–3 use `@EventListener` without `@Async`, so they execute on the publisher's thread (the wrapper thread). This ensures deterministic ordering within each wrapper result: Normalize → Classify → Map → Accumulate runs as a synchronous chain. Different wrapper threads process their results in parallel via the `@Async` executor pool. |
| **Two-phase Stage 4 (accumulate + batch)** | Phase 4a accumulates `CompanyPlatformMappingMessage` events into a thread-safe `ConcurrentHashMap` on each wrapper thread. Phase 4b triggers when the batch-complete event fires (after all wrappers complete), draining the accumulator and performing multi-source signal aggregation. This solves the cross-source aggregation problem without needing Kafka offsets or a `source_signals` database table. |
| **`@Async` on Stage 4b only** | The batch-complete handler runs asynchronously so the scheduler thread can return immediately. KPI computation + DB upserts run on the `eventTaskExecutor` pool. Cache invalidation events published by Stage 4b are also consumed asynchronously by `CacheInvalidationService` (already `@Async`). |
| **Per-source isolation** | Each wrapper call is wrapped in a `CompletableFuture` with its own try-catch. `IntegrationUnavailableException` is caught, logged, and the source is marked "degraded" for that cycle — never propagated to crash the scheduler. Failed wrapper results simply skip the event chain for that source. |
| **Idempotent upserts** | Stage 4b uses `INSERT ... ON CONFLICT (platform_id, sector_id) DO UPDATE` (PostgreSQL upsert, already implemented in `KPIMetricsRepository.upsert()`), making the pipeline safely replayable. |
| **No message persistence** | In-flight events exist only in memory. If the JVM crashes mid-pipeline, events are lost. This is acceptable because: (a) the ingestion scheduler re-runs every 15 minutes, (b) upserts are idempotent, (c) the pipeline is designed for eventual consistency. |

### 9.3 OAuth Token Management Flow

```
AdPlatformIngestionScheduler
        │
        ▼
GoogleAdsApiWrapper.fetchMetrics()
        │
        ├─► OAuthTokenManager.getToken("google_ads")
        │       │
        │       ├─► Cache hit (TTL 55 min for 60-min tokens)?
        │       │   └─► Return cached token
        │       │
        │       └─► Cache miss or near-expiry?
        │           └─► POST https://oauth2.googleapis.com/token
        │               { grant_type: "refresh_token", refresh_token: "..." }
        │               └─► Store in cache, return new access_token
        │
        └─► Set Bearer header → call external API (e.g., Yelp Fusion API)
```

The `OAuthTokenManager` is a thread-safe singleton holding a `ConcurrentHashMap<String, TokenEntry>` keyed by platform name. Each token entry stores the access token, expiry timestamp, and refresh token. The `getToken()` method is synchronized per-platform to prevent concurrent token refresh storms.

### 9.4 Internal APIs (REST)

| Endpoint | Method | Purpose | Auth |
|---|---|---|---|
| `/api/metrics?platform={id}&sector={id}&sort={col}&dir={asc|desc}&page={n}` | GET | Returns paginated KPI rows | RBAC |
| `/api/opportunity/top` | GET | Returns current Top Opportunity DTO | RBAC |
| `/api/calculator/compute` | POST | Runs ROI calculation | RBAC |
| `/api/export/csv?platform={id}&sector={id}` | GET | Exports CSV file with 15 columns (includes Source column) | RBAC |
| `/api/admin/weights` | GET/PUT | CRUD for scoring weights | Admin |

---

## 10. Data Layer Design

### 9.1 Database – PostgreSQL

- **Schema**: `media_buying`
- **Core Tables**: `platform`, `commerce_sector`, `kpi_metrics`, `scoring_weights`, `users`, `roles`, `audit_log`, `client_prospects`
- **Client Table**: `client` – stores current client records. Columns: `id` PK, `client_name`, `commerce_sector_id` FK, `media_buying_contract` (text), `retention_period` (interval), `outlook_score` (numeric). Populated with fake data for MVP.
- **Client Prospects Table** (`client_prospects`): Stores pre‑researched high‑value target companies per commerce sector. Columns: `id`, `sector_id` (FK), `company_name`, `est_annual_revenue`, `yoy_growth_rate`, `est_ad_budget`, `industry_vertical`, `notes`, `last_updated`. Populated via a lightweight research ETL or manual curation process (not real‑time).
- **Source Attribution Tables**:
  - `data_source` – canonical registry of all data sources (platform API endpoint, documentation URL, license, last verified date)
  - `kpi_source_attribution` – join table linking each KPI metric row to its source record(s); supports many-to-many (a metric may be derived from multiple sources)
- **Connection Pool**: HikariCP (Spring Boot default)
- **Migrations**: Flyway (preferred for Java 8 compatibility)

### 9.2 Cache – Redis

| Key Pattern | TTL | Purpose |
|---|---|---|
| `composite:top` | 5 min | Cached Top Opportunity DTO |
| `hierarchy:all` | 5 min | Cached platform‑sector hierarchy (level‑1) |
| `metrics:{platform}:{sector}:{page}:{sort}` | 5 min | Cached paginated KPI rows |
| `platforms:list` | 15 min | Cached platform definitions |
| `clients:top:{sector}` | 15 min | Cached top‑5 client prospects for a commerce sector (ranked by est. ad budget, revenue, growth) |

### 10.3 Event Bus – Spring Application Events

Inter-stage messaging within the 4-stage ETL pipeline uses Spring's built-in `ApplicationEventPublisher` + `@EventListener`. This is a zero-infrastructure, Java-native event bus that runs entirely within the same JVM. All messages are wrapped in `IntegrationEvent` with a topic string for SpEL-based listener routing.

| Event Bus Topic | Event DTO | Publisher | Listener | Async? |
|---|---|---|---|---|
| `source.raw` | `NormalizedSourceMessage` | `DataSourceIngestionScheduler` (within each wrapper thread) | `SectorGrouper.onSourceRaw()` — classifies into commerce sectors | **No** — sync chain within wrapper thread |
| `sector.grouped` | `SourceSectorMappingMessage` | `SectorGrouper` (after sector classification, same wrapper thread) | `CompanyPlatformGrouper.onSectorGrouped()` — identifies companies, maps to ad platforms | **No** — sync chain |
| `company.grouped` | `CompanyPlatformMappingMessage` | `CompanyPlatformGrouper` (after company/platform mapping, same wrapper thread) | `KpiBuilder.onCompanyGrouped()` — accumulates in `ConcurrentHashMap` (no DB write) | **No** — sync accumulation |
| `pipeline.batch-complete` | `PipelineBatchCompleteEvent` | `DataSourceIngestionScheduler` (scheduler thread, after all 10 wrappers finish) | `KpiBuilder.onBatchComplete()` — drains accumulator, aggregates signals, computes KPIs, upserts to DB | **Yes** — `@Async("eventTaskExecutor")` |
| `kpi.refresh` | `KpiRefreshEvent` | `KpiBuilder.onBatchComplete()` (async thread, after successful upserts) | `CacheInvalidationService.onKpiRefresh()` — purges Redis keys | **Yes** — `@Async("eventTaskExecutor")` |
| `data-refresh.internal` | `String` (JSON command) | `DataRefreshProducer` (5-min scheduler or manual) | `DataRefreshScheduler.onRefreshCommand()` — triggers ingestion cycle | **No** — sync dispatch |

**Async Thread Pool**: A `ThreadPoolTaskExecutor` (core=4, max=10, queue=100, `CallerRunsPolicy`) is used ONLY for `@Async` listeners (`pipeline.batch-complete`, `kpi.refresh`, and wrapper `@Async` calls). Non-`@Async` listeners (`source.raw`, `sector.grouped`, `company.grouped`) execute synchronously on the publisher's thread, forming a deterministic chain within each wrapper result.

**Why synchronous chain for Stages 1–3?** This ensures that when the `pipeline.batch-complete` event fires, all preceding pipeline stages have already completed (they ran synchronously within the wrapper threads). No race conditions between batch-complete and in-flight events. Different wrapper results still process in parallel across the `@Async` executor pool threads.

**Error Handling**: Synchronous listeners (Stages 2–3): exceptions propagate back through the event publisher to the wrapper thread, where they are caught by the wrapper's `try-catch` and logged. Async listeners (Stage 4b): exceptions are caught by `AsyncUncaughtExceptionHandler` in `SpringEventConfig`, logged with full stack trace, and the event is discarded. No dead-letter queue — the ingestion scheduler re-runs every 15 minutes, and database upserts are idempotent, so lost events are naturally recovered.

**Note**: This replaces the Kafka-based event streaming described in earlier design versions. See [ADR-0007](/docs/adr/0007-spring-events-over-kafka.md) for the full decision rationale.

### 10.1 Source Attribution & Data Provenance

**Purpose**: Every KPI value displayed in the dashboard is traceable to a specific, verifiable external source. Users can inspect the origin of any data point to assess authenticity and recency.

#### 10.1.1 Data Model

```
data_source
├── id (PK, UUID)
├── platform_id (FK → platform)
├── source_type        -- "API", "DOCUMENTATION", "REPORT", "MANUAL"
├── source_url         -- publicly accessible reference URL
├── source_name        -- human-readable label (e.g., "Yelp Fusion API v3")
├── license_type       -- "PROPRIETARY", "PUBLIC", "OPEN"
├── last_verified_at   -- timestamp of last automated/manual verification
└── created_at

kpi_source_attribution
├── id (PK, UUID)
├── kpi_metrics_id (FK → kpi_metrics)
├── data_source_id (FK → data_source)
├── attribution_context -- "RAW", "INTERPOLATED", "DERIVED"
└── created_at
```

#### 10.1.2 Service Components

| Component | Responsibility | Key Technologies |
|---|---|---|
| `SourceAttributionService` | Core service for linking KPI rows to source records. Provides `getSourcesForKpi(kpiId)` returning source metadata. Handles source refresh verification and stale-source alerting. | Spring `@Service`, JPA |
| `DataSourceRepository` | CRUD repository for `DataSource` entity | Spring Data JPA |
| `KpiSourceAttributionRepository` | CRUD repository for `KpiSourceAttribution` join entity | Spring Data JPA |
| `SourceVerificationScheduler` | Scheduled job that re-fetches `source_url` and updates `last_verified_at`. Emits alert if source > 30 days unverified. | Spring `@Scheduled` |

#### 10.1.3 Verification Workflow

1. **Ingestion** – ETL populates `source_url` from the platform API response headers or documentation reference. The `RawKPIEvent` DTO carries `sourceReferences[]` — a list of source IDs populated at write time.
2. **Storage** – `KPIStreamConsumer` writes `kpi_source_attribution` rows linking each metric batch to its source.
3. **Query** – `SourceAttributionService.getSourcesForKpi(kpiId)` returns source metadata for a given metric.
4. **UI Display** – Each KPI cell or row renders a citation icon (ℹ️ or chain-link) which expands to a popover/tooltip showing: source name, source type, URL (clickable), license, and last-verified timestamp.
5. **Verification** – `SourceVerificationScheduler` periodically re-fetches `source_url` and updates `last_verified_at`. Sources > 30 days unverified trigger a monitoring alert.

#### 10.1.4 UI Citation Display (Refined for FRONT-29)

**Component**: A dedicated **"Sources" column** (14th column) in the metrics DataTable containing a clickable citation icon per row.

**Interaction Flow**:
1. Each KPI row displays a **citation icon** (material symbol `info`) in the Sources column.
2. **On click**, a `p:commandLink` triggers `DashboardBean.loadKpiSources(kpi.id)` via AJAX.
3. The bean calls `SourceAttributionService.getSourcesForKpi(kpiId)` — result is **cached in-memory** (`ConcurrentHashMap<Long, SourceCacheEntry>`, TTL 30 minutes) to avoid repeated DB queries.
4. A `p:dialog` modal renders the source metadata list with:
   - **Source Name** and type (badge: "API", "DOCUMENTATION", "REPORT", "MANUAL")
   - **Source URL** – rendered as a clickable `<a target="_blank">` link that opens in a new tab
   - **License Type** – "PROPRIETARY", "PUBLIC", or "OPEN"
   - **Last Verified timestamp** – formatted as ISO-8601 date
5. User dismisses the dialog; subsequent clicks on the same row serve from cache.

**Staleness Indicator**:
- If `last_verified_at` > 30 days (or null), the citation icon renders in **amber/warning color** (`var(--warning-amber)`).
- Staleness is computed per source in `SourceMetadataDTO.isStale`; the icon shows amber if **any** attributed source is stale.
- The dialog also color-codes stale sources individually (amber date text).

**Caching & Performance**:
- **Per-row in-memory cache** in `DashboardBean.sourceCache`: `Map<Long, SourceCacheEntry>` with `CachedAt` timestamp.
- TTL = 30 minutes (source metadata is static; only changes on ETL ingestion or manual verification).
- Cache invalidated when `CacheInvalidationService` processes `kpi.refresh` events.
- **Click-only trigger** avoids accidental AJAX calls from hover sweeps across 20+ visible rows.

**Export Inclusion**:
- `KPIMetricsDTO` gains a `primarySourceName` field, populated by `KPIQueryService` via batch lookup.
- `CsvExportService` appends a **"Source"** column (after "Data Timestamp") with the `primarySourceName` value.

**PrimeFaces Implementation**:
- `p:dialog` with `id="citationDialog"`, `dynamic="true"`, `modal="true"`, `header="Data Source Attribution"`.
- Dialog content: `<ui:repeat>` over `#{dashboardBean.currentSources}` rendering each source.
- CSS classes: `.citation-icon` (base), `.citation-icon-stale` (amber), `.source-dialog-item` (list item), `.source-url-link` (clickable link).

#### 10.1.5 Audit Integration

All source attributions are logged to `audit_log` with actions `SOURCE_ATTRIBUTION_CREATED` or `SOURCE_ATTRIBUTION_UPDATED`, capturing the user and timestamp.

#### 10.1.6 Implementation Notes

Full implementation details (Flyway migrations V9/V10, JPA entities `DataSource`/`KpiSourceAttribution`, repositories, `SourceAttributionService`, `RawKPIEvent.sourceReferences` field, `KPIStreamConsumer` linking logic, `SourceVerificationScheduler`, REST endpoint `/api/kpi/{id}/sources`, DTOs, and unit tests) are specified in **LLD §23 – Source Attribution Service Implementation**.

Key design decisions:
- **V9/V10 migration split**: `data_source` (V9) has no dependency on `kpi_metrics`; `kpi_source_attribution` (V10) depends on both — split allows independent rollback.
- **Idempotent upsert**: `ON CONFLICT DO NOTHING` on `(kpi_metrics_id, data_source_id)` for safe re-ingestion.
- **Source resolution by name**: `RawKPIEvent.sourceReferences` carries source *names* (not DB IDs), resolved by `DataSourceRepository.findBySourceName()` at ingest time — decouples event producer from DB state.
- **Daily verification**: `SourceVerificationScheduler` runs at 2 AM, uses HTTP HEAD (GET fallback), logs stale sources >30 days unverified, and emits a Micrometer counter for monitoring alert integration.

---

## 11. Security Architecture

| Layer | Mechanism |
|---|---|
| **Authentication** | Spring Security with OAuth 2.0 / LDAP; session managed in Redis |
| **Authorization** | Role-Based Access Control (RBAC): roles = `ADMIN`, `MEDIA_ANALYST`, `VIEWER` |
| **Data Masking** | Custom PrimeFaces renderer hides KPI values for non-`MEDIA_ANALYST` roles, showing `---` |
| **Transport Security** | TLS 1.2+ enforced via server configuration |
| **PII Handling** | GDPR‑compliant; customer identifiers masked in UI; audit logs track all data access |
| **Audit Trail** | All service-method calls logged via AOP; persisted in `audit_log` table |

---

## 12. Performance &amp; Scalability Targets

| Metric | Target | Strategy |
|---|---|---|
| Page Load (initial dashboard) | ≤ 2 seconds | Redis cache for composite score + hierarchy; lazy loading for sector/metrics |
| Drill-Down Response | ≤ 1 second | Client‑side AJAX + server‑side paginated queries (20 rows) |
| Concurrent Users | 10,000 | Horizontal scaling in Kubernetes; stateless services; shared Redis/PostgreSQL |
| Data Freshness | < 15 minutes | Ingestion scheduler runs every 15 min; dashboard self‑refreshes every 5 min |
| Uptime SLA | 99.9 % | Multi‑AZ PostgreSQL, Redis Sentinel, Kubernetes HPA |

---

## 13. Observability (Refined — Week 11 Deliverable)

### 13.1 Observability Pillars

| Pillar | Stack | Details |
|---|---|---|
| **Metrics** | Micrometer → Prometheus (kube-prometheus-stack) → Grafana | API latency (p50/p95/p99), cache hit ratio, Spring Events processing time, JVM heap/GC, DB connection pool, integration health |
| **Logging** | SLF4J + Logback (LogstashEncoder) → Filebeat DaemonSet → Elasticsearch → Kibana | Structured JSON logs with `correlationId`, `userId`, `sessionId` MDC fields; ILM policy (30-day retention) |
| **Tracing** | OpenTelemetry (Phase 4) | Future: distributed tracing across ETL → service → cache → DB via Tempo or Jaeger |
| **Alerting** | PrometheusRule CRD → AlertManager → Slack + PagerDuty | 7 alert groups: API perf, JVM health, cache perf, DB health, integration health, pod health, data freshness |
| **Dashboarding** | Grafana (ConfigMap auto-discovery) | 6 dashboard rows: JVM/heap/GC, API latency/errors, cache hit ratio, Spring Events processing, DB pool, integration status |

### 13.2 Monitoring Stack Deployment

All monitoring components are deployed via Helm on the EKS cluster:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        Monitoring Namespace (EKS)                              │
│                                                                               │
│  ┌───────────────────────────┐  ┌───────────────────────────┐                 │
│  │  kube-prometheus-stack     │  │     ELK Stack              │                 │
│  │  ┌───────────────────────┐ │  │  ┌───────────────────────┐ │                 │
│  │  │ Prometheus (x2)       │ │  │  │ Elasticsearch (x3)    │ │                 │
│  │  │ 15d retention, 50Gi   │ │  │  │ 100Gi gp3 per node    │ │                 │
│  │  ├───────────────────────┤ │  │  ├───────────────────────┤ │                 │
│  │  │ AlertManager (x2)     │ │  │  │ Kibana (x1)           │ │                 │
│  │  │ Slack + PagerDuty     │ │  │  │ Index pattern auto-   │ │                 │
│  │  ├───────────────────────┤ │  │  │   discovery            │ │                 │
│  │  │ Grafana (x2)          │ │  │  └───────────────────────┘ │                 │
│  │  │ Dashboards via CM     │ │  └───────────────────────────┘                 │
│  │  └───────────────────────┘ │                                                │
│  └───────────────────────────┘                                                │
│                                                                               │
│  ┌───────────────────────────────────────────────────────────────────┐       │
│  │  Filebeat DaemonSet (1 pod per node)                               │       │
│  │  • Reads /var/log/containers/media-buying-dashboard-*.log          │       │
│  │  • Parses JSON via LogstashEncoder → Elasticsearch                 │       │
│  │  • Multiline stack trace aggregation                               │       │
│  │  • Kubernetes metadata enrichment (pod, node, namespace, labels)   │       │
│  └───────────────────────────────────────────────────────────────────┘       │
│                                                                               │
│  ┌───────────────────────────────────────────────────────────────────┐       │
│  │  cert-manager + Let's Encrypt                                      │       │
│  │  • ClusterIssuer (HTTP-01 challenge via ALB)                       │       │
│  │  • Auto-renew Certificates for dashboard, Grafana, Kibana          │       │
│  └───────────────────────────────────────────────────────────────────┘       │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 13.3 Prometheus Scraping

| Endpoint | Source | Metrics Collected |
|---|---|---|
| `/actuator/prometheus` | ServiceMonitor CRD (dashboard pods) | JVM (heap, GC, threads), HTTP (latency, rate, errors), HikariCP, Redis Lettuce, Spring Events metrics, custom application counters |
| `/metrics` | kube-state-metrics | Pod status, replicas, HPA metrics, deployment status |
| `/metrics` | node-exporter | Node CPU, memory, disk, network |

The ServiceMonitor is deployed as part of the application Helm chart (`helm/media-buying-dashboard/templates/servicemonitor.yaml`) and auto-discovered by Prometheus via the label `release: prometheus-stack`.

### 13.4 Alerting Rules (PrometheusRule CRD)

8 alert groups configured in `infrastructure/observability/prometheus-rules.yaml`:

| Group | Key Alerts | Severities |
|---|---|---|
| **api-performance** | `HighApiErrorRate` (>1% 5XX), `HighApiLatencyP99` (>2s), `SlowDashboardPageLoad` (>2s p95) | Critical / Warning |
| **jvm-health** | `HighJvmHeapUsage` (>85%), `CriticalJvmHeapUsage` (>95%), `HighGcOverhead`, `JvmThreadDeadlock` | Critical / Warning |
| **cache-performance** | `LowCacheHitRatio` (<70%), `CriticallyLowCacheHitRatio` (<50%), `RedisConnectionPoolExhaustion` (>90%) | Critical / Warning |
| **database-health** | `HighDbConnectionPoolUsage` (>85% HikariCP), `SlowDatabaseQueries` (p95 > 500ms) | Warning |
| **integration-health** | `PlatformIntegrationFailing`, `OAuthTokenRefreshFailure` | Warning / Critical |
| **pod-health** | `PodCrashLooping`, `PodNotReady`, `HpaNearMaxCapacity` (>85%) | Critical / Warning |
| **data-freshness** | `TopOpportunityStale` (>15min), `KpiDataStale` (>20min) | Warning |

### 13.5 AlertManager Routing

| Severity | Group Wait | Group Interval | Receiver | Channel |
|---|---|---|---|---|---|
| `critical` | 30s | 5m | `pagerduty-critical` + Slack `#media-buying-critical` | PagerDuty + Slack |
| `warning` | 30s | 5m | `slack-warning` | Slack `#media-buying-alerts` |
| `component=api` | 30s | 5m | `slack-api` | Slack `#media-buying-api` |

Inhibition rules: `NodeDown` suppresses all pod-level critical alerts on the same node to prevent alert storms.

### 13.6 Grafana Dashboards

One comprehensive dashboard (`Media Buying Dashboard - Application Metrics`) organized as 6 rows:

| Row | Panels | Metrics Visualized |
|---|---|---|
| **JVM** | Heap memory (used/max/committed), Non-heap memory, GC pause time, Thread states, Deadlocked threads | `jvm_memory_used_bytes`, `jvm_gc_pause_seconds`, `jvm_threads_live_threads`, `jvm_threads_deadlocked_threads` |
| **Container** | CPU usage (cores), Memory working set | `container_cpu_usage_seconds_total`, `container_memory_working_set_bytes` |
| **API** | Request rate (req/s), Error rate (%), p50/p95/p99 latency (s) | `http_server_requests_seconds_count`, `http_server_requests_seconds_bucket` |
| **Cache** | Hit ratio (%), Command rate, Connection pool, Evictions/Expirations | `cache_gets_total`, `redis_commands_duration_seconds_count`, `lettuce_*` |
| **Spring Events** | Event publish rate, Listener execution time (p50/p95 ms), Thread pool queue depth, Async task completion rate | `spring_integration_events_total`, custom Micrometer timers |
| **DB** | HikariCP pool, Query latency (p50/p95 ms) | `hikaricp_connections_active`, `hikaricp_connections_usage_seconds_bucket` |
| **Integration** | Wrapper error rate, Success/failure by platform | Custom Micrometer counters from `BaseApiWrapper` |
| **Health** | Pod count, Restarts, KPI freshness (min), Top Opportunity score | `kube_pod_info`, `kube_pod_container_status_restarts_total`, `kpi_ingestion_timestamp_seconds` |

Dashboards are deployed as Kubernetes ConfigMaps with the label `grafana_dashboard: "1"` for auto-discovery by the Grafana sidecar. The JSON definition is at `infrastructure/observability/grafana-dashboards/media-buying-dashboard.json`.

### 13.7 Logging Architecture (ELK)

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────┐
│ Spring Boot  │     │  Filebeat    │     │ Elasticsearch│     │  Kibana  │
│  (Pod)       │────▶│  DaemonSet   │────▶│  Cluster     │────▶│  (UI)    │
│              │     │              │     │  (x3 nodes)  │     │          │
│  JSON logs   │     │  Parse JSON  │     │  Index:      │     │  Search  │
│  to stdout   │     │  + k8s meta  │     │  media-buying│     │  Visualize│
│  via         │     │  + multiline │     │  -logs-*     │     │  Dashboards│
│  Logstash    │     │  aggregation │     │  ILM: 30d    │     │           │
│  Encoder     │     │              │     │              │     │           │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────┘
```

- **Log format**: Structured JSON with MDC fields (`correlationId`, `userId`, `sessionId`, `action`)
- **Filebeat**: DaemonSet on all EKS nodes, reads `/var/log/containers/media-buying-dashboard-*.log`
- **Multiline**: Java stack traces aggregated into single log entries
- **Index lifecycle**: Hot phase (rollover at 30GB or 7 days), Delete phase (30 days)
- **Access**: Kibana at `https://kibana.media-buying.autoresolve.com` (TLS via cert-manager)

### 13.8 TLS & Certificate Management

All external endpoints are TLS-secured via cert-manager + Let's Encrypt:

| Domain | Certificate | Issuer |
|---|---|---|
| `dashboard.media-buying.autoresolve.com` | `media-buying-dashboard-tls` (ALB Ingress) | `letsencrypt-prod` |
| `dev-dashboard.media-buying.autoresolve.com` | `media-buying-dashboard-tls` (shared SAN) | `letsencrypt-prod` |
| `grafana.media-buying.autoresolve.com` | `grafana-tls` | `letsencrypt-prod` |
| `kibana.media-buying.autoresolve.com` | `kibana-tls` | `letsencrypt-prod` |

Internal services (Prometheus, AlertManager, Elasticsearch) are accessed via `kubectl port-forward` or VPN — no external exposure.

### 13.9 Custom Application Metrics

The following Micrometer counters and gauges are instrumented in the application code to support the observability stack:

| Metric Name | Type | Tag(s) | Purpose |
|---|---|---|---|
| `cache_gets_total` | Counter | `result=(hit\|miss)` | Track cache hit/miss ratio |
| `integration_api_calls_total` | Counter | `platform, result=(success\|failure)` | Track wrapper health per platform |
| `integration_api_errors_total` | Counter | `platform, error_type` | Track wrapper failures for alerting |
| `oauth_token_refresh_errors_total` | Counter | `platform` | Track OAuth token refresh failures |
| `top_opportunity_computed_time_seconds` | Gauge | — | Timestamp of last composite score computation |
| `kpi_ingestion_timestamp_seconds` | Gauge | `platform` | Timestamp of last successful KPI ingestion |
| `top_opportunity_score` | Gauge | — | Current composite score value |
| `kpi_query_latency_seconds` | Timer | `query_type` | KPI query performance for HPA custom metrics |

### 13.10 Observability Configuration Files

| File | Purpose |
|---|---|
| `infrastructure/observability/prometheus-stack-values.yaml` | kube-prometheus-stack Helm values (Prometheus, AlertManager, Grafana) |
| `infrastructure/observability/prometheus-rules.yaml` | PrometheusRule CRD with 8 alert groups |
| `infrastructure/observability/elk-stack-values.yaml` | Elasticsearch, Kibana, and Filebeat Helm values |
| `infrastructure/observability/grafana-dashboards/media-buying-dashboard.json` | Grafana dashboard JSON (23 panels) |
| `infrastructure/observability/cert-manager-clusterissuer.yaml` | ClusterIssuer + Certificates for TLS |
| `helm/media-buying-dashboard/templates/servicemonitor.yaml` | ServiceMonitor CRD (already exists, Week 2‑3) |
| `infrastructure/terraform/modules/security/cloudwatch-alarms.tf` | AWS CloudWatch alarms (already exists, Week 2‑3) |

---

## 14. Deployment Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Kubernetes Cluster (EKS / on-prem)            │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                     Dashboard Pods (x N, HPA)                     │ │
│  │   ┌──────────────────────────────────────────────────────────┐  │ │
│  │   │ Spring Boot JAR (0.0.0.0:6800)                           │  │ │
│  │   │   • PrimeFaces/Thymeleaf UI                              │  │ │
│  │   │   • All REST API controllers                             │  │ │
│  │   │   • 4-stage ETL pipeline (Spring Events, same JVM)       │  │ │
│  │   │   • @EventListener beans (async processing)              │  │ │
│  │   │   • Cache invalidation                                   │  │ │
│  │   └──────────────────────────────────────────────────────────┘  │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │                      Ingress / API Gateway                      │   │
│  │                     (NGINX / Traefik / Kong)                    │   │
│  │                     TLS Termination + Rate Limiting             │   │
│  └───────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
         │                    │
         ▼                    ▼
┌─────────────┐    ┌───────────────┐
│ PostgreSQL   │    │    Redis      │
│ (RDS / HA)   │    │ (Sentinel/HA) │
└─────────────┘    └───────────────┘
```

---

### 13.1 Spring Events Processing Model

All pipeline event listeners run within the same Spring Boot JAR as the dashboard web application. There is no separate consumer deployment — a single `Deployment` with HPA scales all pods identically.

The pipeline uses a **hybrid synchronous/asynchronous** execution model:
- **Stages 1–3** (`source.raw` → `sector.grouped` → `company.grouped`): `@EventListener` runs **without** `@Async`, so each stage executes synchronously on the wrapper thread that published the event. This forms a deterministic chain per wrapper result.
- **Stage 4b** (`pipeline.batch-complete`): Uses `@Async("eventTaskExecutor")` so the scheduler thread returns immediately. KPI computation and DB upserts run on the async pool.
- **Cache invalidation** (`kpi.refresh`): Uses `@Async("eventTaskExecutor")` (already implemented).
- **Wrapper calls**: Use `@Async` via a separate `adPlatformTaskExecutor` pool for parallel execution of all 10 sources.

| Pod Type | Spring Profile | Replicas | Responsibilities |
|---|---|---|---|
| **Dashboard Pod** | `web` (default) | 2–12 (HPA) | Thymeleaf/JSF rendering, REST API, service layer, 4-stage ETL pipeline (Spring Events: synchronous chain Stages 1-3 + async Stage 4b), cache invalidation |

The `@Async` `ThreadPoolTaskExecutor` (core=4, max=10, queue=100, `CallerRunsPolicy`) handles Stage 4b batch processing and cache invalidation. The synchronous chain (Stages 1–3) ensures that when `pipeline.batch-complete` fires, all preceding pipeline stages have already completed — no race conditions between batch-complete and in-flight event processing.

**Rationale**: The modular monolith runs all business logic in a single process. Spring Events provide sufficient decoupling between pipeline stages without the operational overhead of a separate consumer pod. When stages are extracted to independent microservices (Phase 4), each will become its own deployment with inter-service messaging restored.

---

## 15. Infrastructure Architecture (AWS)

This section details the infrastructure-as-code design for provisioning the AWS cloud environment. All resources are managed via Terraform with remote state in S3 + DynamoDB.

### 14.1 Architecture Decision: EKS over ECS

| Factor | EKS (Chosen) | ECS Fargate |
|--------|-------------|-------------|
| **Helm Support** | ✅ Native | ❌ Not supported |
| **Horizontal Pod Autoscaling (HPA)** | ✅ Native K8s HPA | ❌ Service Auto Scaling only |
| **Microservices Migration Path** | ✅ Extract package → deploy as pod | ❌ Requires re-architecting to task definitions |
| **Multi-Cloud Portability** | ✅ Same manifests on any K8s | ❌ AWS lock-in |
| **IRSA (IAM Roles for Service Accounts)** | ✅ Native | ❌ Task roles only |
| **Service Mesh Readiness** | ✅ Istio/Linkerd | ❌ App Mesh only |
| **Operational Overhead** | Medium (managed control plane) | Low |

**Verdict**: EKS is chosen because:
1. The modular monolith is explicitly designed to evolve toward microservices — EKS supports this seamlessly.
2. Helm charts (section 14.6) provide a standardized, versioned deployment pattern across dev/staging/prod.
3. HPA is essential for the 10,000 concurrent user SLA — K8s HPA with custom Prometheus metrics provides finer control than ECS auto-scaling.
4. IRSA enables fine-grained IAM permissions per pod (e.g., Kafka consumer pod ≠ dashboard pod permissions).

### 14.2 Terraform Module Structure

```
infrastructure/terraform/
├── modules/
│   ├── networking/                 # VPC, subnets, NAT gateways, VPC endpoints
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── eks/                        # EKS cluster, managed node groups, IRSA
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   └── iam.tf                  # IRSA roles, cluster autoscaler policy
│   ├── rds/                        # PostgreSQL Multi-AZ, parameter groups
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── elasticache/                # Redis Cluster Mode, parameter groups
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   └── security/                   # Secrets Manager, KMS, IAM policies
│       ├── main.tf
│       ├── variables.tf
│       └── outputs.tf
├── environments/
│   ├── dev/
│   │   ├── main.tf                 # Module instantiations
│   │   ├── terraform.tfvars        # Dev-specific values
│   │   └── backend.tf              # S3 remote state
│   ├── staging/
│   │   ├── main.tf
│   │   ├── terraform.tfvars
│   │   └── backend.tf
│   └── prod/
│       ├── main.tf
│       ├── terraform.tfvars
│       └── backend.tf
└── backend.tf                      # Shared S3 + DynamoDB backend config
```

**Terraform State**: Remote state stored in S3 (`media-buying-terraform-state`) with DynamoDB locking (`media-buying-terraform-locks`). State is segregated per environment via key prefix.

### 14.3 Networking Design (VPC)

| Resource | Configuration | Rationale |
|----------|--------------|-----------|
| **VPC CIDR** | `10.0.0.0/16` | Sufficient for 3 AZs × 3 subnet tiers |
| **Availability Zones** | 3 (us-east-1a, 1b, 1c) | High availability for RDS Multi-AZ, ElastiCache, MSK |
| **Public Subnets** | 10.0.1.0/24, 10.0.2.0/24, 10.0.3.0/24 | ALB ingress, NAT Gateways |
| **Private-App Subnets** | 10.0.11.0/24, 10.0.12.0/24, 10.0.13.0/24 | EKS worker nodes |
| **Private-Data Subnets** | 10.0.21.0/24, 10.0.22.0/24, 10.0.23.0/24 | RDS, ElastiCache |
| **NAT Gateways** | 1 per AZ (3 total) | Outbound internet access from private subnets |
| **VPC Endpoints (Gateway)** | S3, ECR API, Secrets Manager | Reduce NAT Gateway data transfer costs for AWS service calls |
| **VPC Endpoints (Interface)** | ECR DKR, CloudWatch Logs, STS | Private connectivity for EKS control plane communication |

**Security Group Rules** (summary):
- **EKS Node SG**: Inbound from ALB SG on 6800; outbound to RDS (5432), Redis (6379)
- **RDS SG**: Inbound from EKS Node SG on 5432 only
- **ElastiCache SG**: Inbound from EKS Node SG on 6379 only
- **ALB SG**: Inbound from 0.0.0.0/0 on 443

### 14.4 EKS Cluster Design

| Resource | Dev | Staging | Prod |
|---|---|---|---|
| **K8s Version** | 1.31 | 1.31 | 1.31 |
| **Cluster Endpoint** | Public (IP-restricted) | Public (IP-restricted) | Private only |
| **Dashboard Node Group** | t3.medium (2 vCPU, 4 GB) | t3.large (2 vCPU, 8 GB) | t3.xlarge (4 vCPU, 16 GB) |
| **Dashboard Min/Max** | 1 / 3 | 2 / 6 | 3 / 12 |
| **AMI Type** | AL2023 x86_64 | AL2023 x86_64 | AL2023 x86_64 |
| **Disk Size** | 50 GB gp3 | 50 GB gp3 | 100 GB gp3 |
| **Cluster Autoscaler** | Enabled | Enabled | Enabled |

**Add-ons installed via EKS Blueprints / Terraform**:
- `vpc-cni` — VPC CNI for pod networking
- `coredns` — Cluster DNS
- `kube-proxy` — Service networking
- `aws-load-balancer-controller` — ALB/NLB ingress
- `ebs-csi-driver` — Persistent volumes (for Prometheus if needed)
- `external-secrets` — Secrets Manager → K8s Secret sync

### 14.5 RDS PostgreSQL Design

| Parameter | Dev | Staging | Prod |
|-----------|-----|---------|------|
| **Engine** | PostgreSQL 15.8 | PostgreSQL 15.8 | PostgreSQL 15.8 |
| **Instance Class** | db.t3.medium | db.t3.large | db.r5.xlarge |
| **Multi-AZ** | No | Yes | Yes |
| **Storage** | 100 GB gp3 | 200 GB gp3 | 500 GB gp3 (auto-scale to 1 TB) |
| **Storage IOPS** | 3,000 (baseline) | 3,000 (baseline) | 12,000 provisioned |
| **Backup Retention** | 7 days | 14 days | 30 days |
| **Point-in-Time Recovery** | Enabled | Enabled | Enabled |
| **Encryption** | KMS CMK | KMS CMK | KMS CMK |
| **Enhanced Monitoring** | 60-second | 60-second | 30-second |
| **Deletion Protection** | No | Yes | Yes |
| **Minor Version Auto-Upgrade** | Yes | No (manual) | No (manual) |
| **Parameter Group** | Custom: `max_connections=200`, `shared_buffers` tuned | Same | Tuned per instance |

**Subnet Group**: Private-Data subnets across 3 AZs.

**Secrets Manager Integration**: Database credentials are stored in AWS Secrets Manager (`media-buying/{env}/db-credentials`) with automatic rotation every 30 days via Lambda.

### 14.6 ElastiCache Redis Design

| Parameter | Dev | Staging | Prod |
|-----------|-----|---------|------|
| **Engine** | Redis 7.1 | Redis 7.1 | Redis 7.1 |
| **Cluster Mode** | Enabled (1 shard) | Enabled (2 shards) | Enabled (3 shards) |
| **Replicas per Shard** | 0 | 1 | 2 |
| **Node Type** | cache.t3.micro | cache.t3.small | cache.m5.large |
| **Multi-AZ** | No (single shard) | Yes | Yes |
| **Encryption at Rest** | KMS | KMS | KMS |
| **Encryption in Transit** | TLS 1.2+ | TLS 1.2+ | TLS 1.2+ |
| **AUTH Token** | ✅ (Secrets Manager) | ✅ (Secrets Manager) | ✅ (Secrets Manager) |
| **Parameter Group** | `volatile-lru` eviction | `volatile-lru` eviction | `volatile-lru` eviction |
| **Automatic Failover** | N/A | Enabled | Enabled |
| **Snapshot Retention** | 1 day | 7 days | 14 days |

### 14.7 Network Security Groups (Updated)

```
┌─────────────────────────────────────────────────────────────┐
│                    GitHub Actions Pipeline                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  [PR opened/synced]                                          │
│         │                                                    │
│         ▼                                                    │
│  ┌──────────────────┐                                       │
│  │ 1. Build & Test   │  mvn clean verify (JUnit 5)          │
│  │                   │  SonarQube scan                      │
│  │                   │  Checkstyle + SpotBugs               │
│  └────────┬─────────┘                                       │
│           │                                                  │
│           ▼                                                  │
│  ┌──────────────────┐                                       │
│  │ 2. Docker Build   │  docker build (multi-stage)          │
│  │    & Push         │  tag: {sha}-{env} + latest           │
│  │                   │  push to ECR                         │
│  └────────┬─────────┘                                       │
│           │                                                  │
│     [Merge to main]                                          │
│           │                                                  │
│           ▼                                                  │
│  ┌──────────────────┐                                       │
│  │ 3. Deploy to Dev  │  helm upgrade --install              │
│  │                   │  Smoke tests (health checks)         │
│  └────────┬─────────┘                                       │
│           │                                                  │
│           ▼                                                  │
│  ┌──────────────────┐                                       │
│  │ 4. Manual Approval│  Required: DevOps / Tech Lead         │
│  └────────┬─────────┘                                       │
│           │                                                  │
│           ▼                                                  │
│  ┌──────────────────┐                                       │
│  │ 5. Deploy Staging │  helm upgrade --install              │
│  │                   │  Integration tests (Playwright)       │
│  └────────┬─────────┘                                       │
│           │                                                  │
│           ▼                                                  │
│  ┌──────────────────┐                                       │
│  │ 6. Manual Approval│  Required: QA + Product Owner         │
│  └────────┬─────────┘                                       │
│           │                                                  │
│           ▼                                                  │
│  ┌──────────────────┐                                       │
│  │ 7. Deploy Prod    │  helm upgrade --install              │
│  │                   │  Canary (10%) → full rollout         │
│  └──────────────────┘                                       │
│                                                              │
│  [Rollback] ── helm rollback media-buying-dashboard ──►     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**GitHub Actions Secrets**:
- `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` — IAM user for ECR push + EKS deploy
- `SONAR_TOKEN` — SonarQube project token
- `ECR_REPOSITORY` — ECR repository URI

**Docker Multi-Stage Build** (conceptual):
```dockerfile
# Stage 1: Build
FROM maven:3.8-openjdk-8 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM openjdk:8-jre-alpine
RUN addgroup --system mediabuyer && adduser --system --no-create-home --ingroup mediabuyer mediabuyer
COPY --from=build /app/target/media-buying-dashboard.jar /app.jar
USER mediabuyer
EXPOSE 6800
ENTRYPOINT ["java", "-jar", "/app.jar", "--server.address=0.0.0.0"]
```

### 14.9 Secrets Management

| AWS Service | Purpose | Rotation |
|-------------|---------|----------|
| **Secrets Manager** | All sensitive credentials | |
| `media-buying/{env}/db-credentials` | PostgreSQL username/password | Auto-rotated every 30 days (Lambda) |
| `media-buying/{env}/redis-auth` | Redis AUTH token | Manual rotation |
| `media-buying/{env}/yelp-fusion-api-key` | Yelp Fusion API key | Manual rotation |
| `media-buying/{env}/ebay-api-key` | eBay Finding API App ID | Manual rotation |
| `media-buying/{env}/reddit-api-key` | Reddit API credentials | Manual rotation |
| `media-buying/{env}/x-api-key` | X/Twitter API credentials | Manual rotation |
| `media-buying/{env}/meta-ads-library-key` | Meta Ads Library API access token | Manual rotation |
| `media-buying/{env}/foursquare-api-key` | Foursquare Places API key | Manual rotation |
| `media-buying/{env}/bing-webmaster-key` | Bing Webmaster API key | Manual rotation |
| `media-buying/{env}/skyscanner-api-key` | Skyscanner API key | Manual rotation |
| `media-buying/{env}/jobmarket-api-key` | JobMarket (Indeed/Adzuna) API key | Manual rotation |
| `media-buying/{env}/pytrends-proxy` | Pytrends proxy configuration (optional) | Manual rotation |
| **Parameter Store** | Non-sensitive configuration | N/A |
| `/media-buying/{env}/dashboard-refresh-interval` | 5 (minutes) | |
| `/media-buying/{env}/kpi-staleness-threshold` | 15 (minutes) | |
| `/media-buying/{env}/scoring-weights-default` | JSON with default weights | |

**External Secrets Operator (ESO)**: Deployed on EKS via Helm. Synchronizes AWS Secrets Manager entries to Kubernetes Secrets. The Spring Boot application reads from K8s secrets via environment variables injected through `envFrom` in the deployment manifest.

### 14.10 Monitoring & Alerting Infrastructure

| Pillar | AWS Native | Application-Level |
|--------|-----------|-------------------|
| **Metrics** | CloudWatch (RDS, ElastiCache, ALB) | Prometheus (Micrometer) → Grafana dashboards |
| **Logs** | CloudWatch Logs (EKS control plane) | Filebeat → Elasticsearch → Kibana (ELK) |
| **Dashboards** | CloudWatch dashboards | Grafana: API latency, cache hit %, JVM heap/GC, Spring Events |
| **Alerting** | CloudWatch Alarms → SNS | Prometheus AlertManager → PagerDuty |

**Key CloudWatch Alarms**:
| Metric | Threshold | Action |
|--------|-----------|--------|
| RDS `CPUUtilization` > 80% for 5 min | WARNING | SNS → DevOps email |
| RDS `FreeStorageSpace` < 10 GB | CRITICAL | SNS → PagerDuty |
| ElastiCache `CPUUtilization` > 80% for 5 min | WARNING | SNS → DevOps email |
| ALB `TargetResponseTime` > 2 sec for 5 min | WARNING | SNS → DevOps email |
| EKS `node_cpu_utilization` > 80% for 10 min | WARNING | SNS → DevOps email (scale investigation) |

---

## 16. Development Phases

| Phase | Scope | Deliverables |
|---|---|---|
| **Phase 1 – MVP** | Dashboard with static mock data; Platform→Sector→Metrics hierarchy; Top Opportunity card; Global sector filter; basic RBAC | Deployable WAR/JAR |
| **Phase 2 – Data Integration** | Spring Events ETL ingestion; real KPI tables; compute composite scores; Redis caching layer | Integration wrappers for 3+ platforms |
| **Phase 3 – Advanced UI** | Sorting, pagination, CSV export, ROI Calculator page, data‑staleness indicators, tooltip help | Full feature parity with PRD |
| **Phase 4 – Production Hardening** | Horizontal scaling, monitoring stack, 10k‑user load testing, GDPR compliance audit, user guide | Production release |

---

## 17. Style Guide Reference

The application UI follows the **"DashKit – Professional Services Dashboard"** design language from the Webflow template marketplace, adapted for PrimeFaces components:

- **Template Category**: Technology / Professional Services
- **Source**: [Webflow Templates – Technology Category](https://webflow.com/templates/category/technology-websites)
- **Key Styling Elements**:
  - Clean white/light-gray background with card-based component layout
  - Blue accent (#2563eb / PrimeFaces `primary` theme) for interactive elements
  - Sans-serif font stack: Inter, system-ui
  - Data tables with alternating row colors and hover states
  - KPI badges with color coding (green = High, yellow = Medium, red = Low)
- **Global CSS**: All theme variables are defined in a single `dashboard-theme.css` file loaded globally via PrimeFaces `web.xml` context parameter.

---

## 18. Competitive Analysis (GitHub Trending Comparison)

The proposed architecture was compared against trending Java repositories on [GitHub Trending – Java](https://github.com/trending/java?since=monthly):

| Reference Repo | Similarity | Key Takeaway |
|---|---|---|
| **apache/druid** | Real-time analytics dashboard | Uses `Lookup`/`SegmentMetadata` queries for fast aggregations – our caching strategy mirrors this pattern. |
| **thingsboard/thingsboard** | IoT Dashboard with PrimeFaces-like widgets | Demonstrates PrimeFaces DataTable with lazy loading at scale; validates our LazyDataModel approach. |
| **NationalSecurityAgency/ghidra** | Large Java modular monolith | Shows clean package-by-feature modularization – we adopt the same structure. |

**Conclusion**: The modular monolith → Spring Events → Redis → PostgreSQL pattern is a mature, validated architecture for dashboard applications handling millions of aggregated rows, evidenced by production systems like Druid and ThingsBoard.

---

## 19. Key Design Decisions

| Decision | Rationale | Trade-Off |
|---|---|---|
| **Modular Monolith** over Microservices | Phase 1‑2 scope fits a single deployable; microservices overhead not justified for < 100 devs | Must enforce strict module boundaries to avoid monolith anti-patterns |
| **PrimeFaces** over React | Aligns with `java_web_app.md`; rich out-of-the-box DataTable/LazyDataModel; no separate frontend build step | Less flexibility for custom UX; heavier server-side rendering load |
| **Redis Cache** over Only DB | PRD requires ≤ 2 second page load; DB queries alone cannot meet this under 10k users | Cache invalidation complexity; stale data window overlaps with 5‑min refresh |
| **Spring Events** over Polling REST | Pipeline stages are decoupled via in-JVM events; `@Async @EventListener` provides non-blocking, loosely-coupled processing without external infrastructure | No cross-JVM event delivery; must reintroduce message broker when extracting microservices (Phase 4) |
| **Wrapper Pattern** for Integrations | Required by design guidelines; isolates platform API volatility from core logic | Extra indirection; must keep wrappers thin to avoid becoming god classes |

---

## 20. Risks &amp; Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Ad platform API changes break data pipelines | **High** – dashboard shows stale data | Wrapper abstraction; circuit breaker fallback to stale cache; monitoring alerts |
| 10,000 concurrent users overwhelm PostgreSQL | **High** – page load > 2 seconds | Redis cache shields DB; read replicas for reporting queries; HPA for pods |
| Composite scoring weights are miscalibrated | **Medium** – wrong "Top Opportunity" surfaced | Admin UI for weight tuning; scoring model logs all inputs for audit |
| Ingestion scheduler failure causes data freshness > 15 min | **Medium** – stale data shown | Monitor scheduler execution in Prometheus; alert at > 20 min since last run; idempotent upserts ensure no data corruption on retry |

---

## 21. References

- Product Requirements: `design/PRD.md`
- Java Application Guide: `design/java_web_app.md`
- GitHub Trending Java: https://github.com/trending/java?since=monthly
- Top 10 Tech Stacks: https://www.techloset.com/blog/top-10-tech-stacks-successful-companies-use
- Webflow Style Templates: https://webflow.com/templates/categories
- System Design Reference: https://www.geeksforgeeks.org/system-design/difference-between-high-level-design-and-low-level-design/
