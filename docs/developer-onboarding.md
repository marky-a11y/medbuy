# Developer Onboarding Guide — Media Buying Dashboard

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Quick Start (5-Minute Setup)](#quick-start-5-minute-setup)
3. [API Keys & Credentials — properties.env](#api-keys--credentials--propertiesenv)
4. [Repository Structure Walkthrough](#repository-structure-walkthrough)
5. [IDE Setup](#ide-setup)
6. [Configuration Profiles](#configuration-profiles)
7. [Build Commands](#build-commands)
8. [Testing](#testing)
9. [CI/CD Pipeline](#cicd-pipeline)
10. [Docker Local Stack](#docker-local-stack)
11. [How to Add a New Ad Platform](#how-to-add-a-new-ad-platform)
11. [Troubleshooting](#troubleshooting)
12. [Code Style](#code-style)

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 8 (JDK 8u312+) | Runtime and compilation — mandated by `java_web_app.md` |
| Maven | 3.8+ | Build tool, dependency management, site generation |
| Docker | 24.0+ | Local stack (PostgreSQL) via `docker-compose-dev.yml` (Kafka available but optional — pipeline uses Spring Events) |
| Git | 2.40+ | Version control |
| IDE | IntelliJ IDEA 2023+ or Eclipse 2023-12+ | Development environment |
| Lombok | 1.18.30 (IDE plugin required) | Boilerplate reduction (`@Data`, `@Builder`, `@Slf4j`) |
| Playwright | 1.40+ (for E2E tests) | Browser automation testing |

> **Note**: Java 8 is the current mandate (see [ADR-0001](adr/0001-java8-springboot2.7.md)). A future upgrade to Java 17 is planned.

---

## Quick Start (5-Minute Setup)

### 1. Clone the Repository
```bash
git clone https://github.com/autoresolve/media-buying-dashboard.git
cd media-buying-dashboard
```

### 2. Start Local Dependencies
```bash
docker compose -f docker-compose-dev.yml up -d
```
This starts PostgreSQL (5432). Kafka (9092) is also started but is **not required** for the default `dev` profile — the pipeline uses Spring Events in-process.

### 3. Build and Verify
```bash
./mvnw clean compile   # Compile all sources
./mvnw test           # Run unit tests
```

### 4. Configure API Keys (optional)
All API keys are managed via `src/main/resources/properties.env`. In development, wrappers default to **mock mode**, so you can leave the keys blank. If you want to test against real APIs, edit `properties.env` and set the relevant credentials, then set `mock-enabled: false` in the corresponding section of `application.yml`.

### 5. Run the Application
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The dashboard is now available at `http://localhost:8080/dashboard/` (the default port; set the `PORT` env var to override).

### 6. Log In
Use the demo credentials:
- **Admin**: `admin` / `admin123` (full access)
- **Analyst**: `analyst` / `analyst123` (can view KPI metrics)
- **Viewer**: `viewer` / `viewer123` (limited view)

---

## API Keys & Credentials — `properties.env`

All external API keys, OAuth tokens, and service credentials are read from a single **`properties.env`** file, **not** from OS environment variables.

### How It Works

1. **`src/main/resources/properties.env`** — bundled in the JAR; contains every credential the application needs, pre-populated with empty defaults.
2. **`PropertiesEnvPostProcessor`** — a Spring Boot `EnvironmentPostProcessor` that loads this file at startup and injects all key/value pairs into the Spring `Environment`.
3. The `application.yml` placeholders (e.g., `${YELP_FUSION_API_KEY}`) resolve from the `.env` file entries.

### File Format

```ini
# Comments start with #
KEY=VALUE
```

- No quoting is needed around values.
- Leading/trailing whitespace is trimmed.
- You can also place a `properties.env` file in the **working directory**; it will be loaded _after_ the classpath version, overriding any values.

### Quick Reference

All 26 credential keys defined in `properties.env`:

| Category | Keys |
|---|---|
| **Wrapper API Keys** | `YELP_FUSION_API_KEY`, `FOURSQUARE_PLACES_API_KEY`, `SKYSCANNER_API_KEY`, `BING_WEBMASTER_API_KEY`, `BING_WEBMASTER_SITE_URL`, `EBAY_API_KEY`, `EBAY_APP_ID`, `REDDIT_API_KEY`, `REDDIT_USER_AGENT`, `X_API_KEY`, `X_BEARER_TOKEN`, `META_ADS_LIBRARY_ACCESS_TOKEN`, `JOB_MARKET_API_KEY`, `JOB_MARKET_APP_ID`, `JOB_MARKET_PROVIDER` |
| **OAuth Credentials** | `EBAY_OAUTH_CLIENT_ID`, `EBAY_OAUTH_CLIENT_SECRET`, `EBAY_OAUTH_ACCESS_TOKEN`, `EBAY_OAUTH_REFRESH_TOKEN`, `REDDIT_OAUTH_CLIENT_ID`, `REDDIT_OAUTH_CLIENT_SECRET`, `REDDIT_OAUTH_ACCESS_TOKEN`, `REDDIT_OAUTH_REFRESH_TOKEN`, `X_OAUTH_CLIENT_ID`, `X_OAUTH_CLIENT_SECRET`, `X_OAUTH_ACCESS_TOKEN`, `X_OAUTH_REFRESH_TOKEN`, `META_ADS_LIBRARY_CLIENT_ID`, `META_ADS_LIBRARY_CLIENT_SECRET`, `META_ADS_LIBRARY_ACCESS_TOKEN`, `META_ADS_LIBRARY_REFRESH_TOKEN` |

> **Important**: In development, all 10 data-source wrappers default to **mock mode** (`mock-enabled: true`), so the API keys in `properties.env` can remain empty. To use live APIs, set `mock-enabled: false` in `application.yml` and populate the real credentials in `properties.env`.

### Security Notes

- `properties.env` is **not** committed to version control in production workflows; it is provisioned as a Kubernetes `Secret` (or Vault) via the Helm chart.
- For local development, the file _is_ tracked in Git to document the required keys, but all values are empty by default.
- The `PropertiesEnvPostProcessor` also checks for `./properties.env` in the working directory at runtime, allowing developers to keep a local override without modifying the tracked file.

---

## Pipeline Architecture

The ingestion pipeline follows a **4-stage event-driven flow** using Spring `ApplicationEventPublisher` + `@EventListener`:

```
Source Ingestion → Sector Grouping → Company/Platform Grouping → KPI Building
```

### Stage 1: Source Ingestion
`DataSourceIngestionScheduler` (replaces the old `AdPlatformIngestionScheduler`) runs on a `@Scheduled` timer and calls all 10 market-data source wrappers in parallel. Each wrapper returns a normalized `MarketDataFeed` object. Once all feeds are collected, a `source.raw` Spring event is published.

### Stage 2: Sector Grouping
`SectorGrouper` listens for `source.raw` events, classifies each feed into a commerce sector (Technology, Finance, Manufacturing, Retail, Health & Wellness), and publishes a single `sector.grouped` event per sector.

### Stage 3: Company/Platform Grouping
`CompanyPlatformGrouper` receives `sector.grouped` events, maps each feed to specific companies and platforms using the `company_platform` mapping table, and publishes `company.grouped` events to an in-memory accumulator.

### Stage 4: KPI Building
`KpiBuilder` has two listeners:
- **`onCompanyGrouped()`** — accepts `company.grouped` events and accumulates them in a thread-local buffer
- **`onBatchComplete()`** — triggered by `pipeline.batch-complete` (published by `DataSourceIngestionScheduler` after Stage 1), drains the accumulator and persists KPI metrics to the database via `kpi_metrics` upserts

Finally, `kpi.refresh` events are published (async) to trigger cache invalidation.

> **Note**: All inter-stage messaging uses Spring Events (in-JVM). Kafka is no longer required for the pipeline. The `kafka` service in `docker-compose-dev.yml` is retained only for profiles that explicitly require it (e.g., `docker`, `k8s`).

---

## Verifying the 10-Source Pipeline

After starting the application with `mvn spring-boot:run`:

1. **Wait ~20 seconds** for the first ingestion cycle to complete
2. **Open** `http://localhost:8080/dashboard`
3. **The Top Opportunity card** should show a composite score > 0 with a badge of "High", "Medium", or "Low" (not "N/A")
4. **The sector filter dropdown** should be populated with 5+ sectors
5. **The KPI table** in any sector should show populated values (not "---")

### Running the Pipeline Integration Test

Alternatively, run the dedicated integration test that exercises the full pipeline end-to-end using mock data:

```bash
mvn test -Dtest="PipelineVerificationTest"
```

This test:
- Triggers all 10 market-data source wrappers (mock mode — no API keys needed)
- Verifies the data flows through all 4 pipeline stages (ingestion, sector classification, company/platform mapping, KPI aggregation)
- Polls the database for up to 30 seconds waiting for asynchronous KPI processing
- Asserts that `kpi_metrics` rows are persisted with realistic values (ROAS 0–20, non-negative CAC/CLTV)
- Uses the in-memory H2 database (test profile) — no external services required

---

## Repository Structure Walkthrough

```
media-buying-dashboard/
├── .github/
│   └── workflows/
│       └── ci-cd.yml                  # 5-stage GitHub Actions pipeline
├── design/
│   ├── HLD.md                         # High-level design document
│   ├── LLD.md                         # Low-level design document
│   ├── PRD.md                         # Product requirements document
│   ├── Project_Plan.md                # Master project plan
│   ├── java_web_app.md                # Java web app constraints & standards
│   ├── code.html                      # Reference HTML wireframe
│   └── screen.png                     # Screenshot mockup
├── docs/
│   ├── adr/
│   │   ├── 0001-java8-springboot2.7.md
│   │   ├── 0002-primefaces-over-react.md
│   │   ├── 0003-modular-monolith.md
│   │   ├── 0004-kafka-for-etl.md
│   │   ├── 0005-redis-cache.md
│   │   └── integration-wrappers.md
│   ├── dashboard_user_guide.md        # End-user documentation
│   ├── developer-onboarding.md        # THIS FILE
│   └── observability-guide.md         # Monitoring & alerting procedures
├── helm/
│   └── media-buying-dashboard/        # Helm chart for EKS deployment
│       ├── Chart.yaml
│       ├── values.yaml                # Default values
│       ├── values-dev.yaml
│       ├── values-staging.yaml
│       ├── values-prod.yaml
│       ├── templates/
│       │   ├── deployment.yaml
│       │   ├── service.yaml
│       │   ├── ingress.yaml
│       │   ├── hpa.yaml
│       │   ├── serviceaccount.yaml
│       │   ├── configmap.yaml
│       │   ├── externalsecret.yaml
│       │   ├── servicemonitor.yaml
│       │   └── clustersecretstore.yaml
├── infrastructure/
│   ├── terraform/                     # IaC (VPC, EKS, RDS, Redis, MSK)
│   └── observability/                 # Prometheus rules, Grafana dashboards
├── src/
│   ├── main/
│   │   ├── java/com/autoresolve/mediabuying/
│   │   │   ├── MeidaBuyingApplication.java   # Main Spring Boot entry point
│   │   │   ├── aspect/                        # AuditLoggingAspect
│   │   │   ├── bean/                          # JSF managed beans (DashboardBean, etc.)
│   │   │   ├── cache/                         # CacheKeys, CacheConfig
│   │   │   ├── calculator/                    # CalculatorService, DTOs
│   │   │   ├── config/                        # SecurityConfig, EventBus config (Spring Events in dev, Kafka in k8s)
│   │   ├── eventbus/                      # Pipeline events (Spring Events in dev, Kafka in k8s)
│   │   │   ├── controller/                    # REST API controllers
│   │   │   ├── dto/                           # Data transfer objects
│   │   │   ├── exception/                     # GlobalExceptionHandler
│   │   │   ├── integration/                   # Ad platform wrappers, OAuth, rate limiter
│   │   │   │   ├── auth/                      # OAuthTokenManager
│   │   │   │   ├── normalizer/                # KpiNormalizer
│   │   │   │   ├── ratelimit/                 # AdPlatformRateLimiter
│   │   │   │   └── wrapper/                   # BaseApiWrapper + 10 market-data source wrappers
│   │   │   ├── messaging/                     # Event DTOs (deprecated — pipeline now uses Spring Events)
│   │   │   ├── model/entity/                  # JPA entities
│   │   │   ├── repository/                    # Spring Data repositories
│   │   │   ├── scheduler/                     # DataSourceIngestionScheduler (replaces AdPlatformIngestionScheduler)
│   │   │   ├── service/                       # Business services
│   │   │   └── util/                          # CSV export, etc.
│   │   ├── resources/
│   │   │   ├── application.yml                # Default config
│   │   │   ├── application-dev.yml            # Dev profile overrides
│   │   │   ├── application-prod.yml           # Prod profile overrides
│   │   │   ├── db/migration/                  # Flyway migrations (V1–V10)
│   │   │   ├── static/css/
│   │   │   │   └── dashboard-theme.css        # Global CSS theme
│   │   │   ├── templates/                     # Thymeleaf templates (ROI calculator, etc.)
│   │   │   ├── webapp/WEB-INF/
│   │   │   │   ├── templates/                 # JSF Facelets template
│   │   │   │   └── faces-config.xml           # JSF configuration
│   │   │   └── resources/mediabuy/            # JSF composite components
│   │   └── webapp/                            # Web resources
│   └── test/
│       ├── java/com/autoresolve/mediabuying/  # JUnit 5 test classes
│       └── resources/
│           └── application-test.yml           # Test profile config
├── playwright/
│   └── e2e/                                   # Playwright E2E test specs
├── docker-compose-dev.yml                     # Local dependency stack
├── Dockerfile                                 # Multi-stage container build
├── pom.xml                                    # Maven project descriptor
└── opencode.json                              # OpenCode AI agent configuration
```

### Key Package Responsibilities

| Package | Responsibility |
|---------|---------------|
| `aspect/` | AOP-based audit logging |
| `bean/` | JSF `@ManagedBean` classes (view-scoped / session-scoped / request-scoped) |
| `cache/` | Redis cache key constants and `CacheManager` configuration |
| `calculator/` | ROI projection engine (`CalculatorService`) |
| `config/` | Spring Security, EventBus config (Spring Events in dev, Kafka in k8s), thread pool, async configuration |
| `controller/` | REST endpoints (`/api/metrics`, `/api/opportunity`, `/api/export`, `/api/calculator`) |
| `integration/` | Market-data source wrappers (10 sources), OAuth token management, rate limiting |
| `messaging/` | Event DTOs (legacy — pipeline now uses Spring Events) |
| `model/entity/` | JPA entities (`Platform`, `CommerceSector`, `KPIMetrics`, `ClientProspect`, etc.) |
| `repository/` | Spring Data JPA repositories with native queries |
| `scheduler/` | `@Scheduled` tasks (ingestion via `DataSourceIngestionScheduler`, data refresh, source verification) |
| `service/` | Core business logic (scoring, KPI query, dashboard orchestration, client lookup, source attribution) |
| `util/` | CSV export utility |

---

## IDE Setup

### IntelliJ IDEA

1. **Install Lombok plugin**:
   - `Settings` → `Plugins` → Search "Lombok" → Install → Restart IDE

2. **Enable annotation processing**:
   - `Settings` → `Build, Execution, Deployment` → `Compiler` → `Annotation Processors`
   - Check **Enable annotation processing**
   - Check **Obtain processors from project classpath**

3. **Import project**:
   - `File` → `Open` → Select `pom.xml` → Open as Project
   - Wait for Maven indexing to complete

4. **Configure Checkstyle** (optional):
   - Install "Checkstyle-IDEA" plugin
   - Import `google_checks.xml` from classpath or use the project's custom ruleset

### Eclipse

1. **Install Lombok**:
   - Download `lombok.jar` from projectlombok.org
   - Run `java -jar lombok.jar` → Select Eclipse installation → Install
   - Restart Eclipse

2. **Import project**:
   - `File` → `Import` → `Maven` → `Existing Maven Projects`
   - Browse to project root → Finish

3. **Configure Checkstyle**:
   - Install "Checkstyle" plugin from Eclipse Marketplace
   - Configure with project rules

---

## Configuration Profiles

The application uses Spring profiles to manage environment-specific configuration.

| Profile | Purpose | Activated By |
|---------|---------|-------------|
| `dev` (default) | Local development — H2 in-memory DB, Spring Events (no Kafka needed), verbose logging, relaxed security | `-Dspring-boot.run.profiles=dev` or `SPRING_PROFILES_ACTIVE=dev` |
| `docker` | Docker Compose stack — PostgreSQL, Kafka as containers | `SPRING_PROFILES_ACTIVE=docker` |
| `k8s` | Kubernetes/EKS deployment — ConfigMap-driven, external secrets | Set in Helm `env.SPRING_PROFILES_ACTIVE` |
| `test` | Automated tests — H2, fast defaults | Auto-activated by `application-test.yml` |
| `prod` | Production — strict security, performance tuned | `SPRING_PROFILES_ACTIVE=prod` |

### Profile-Specific Files

- `application.yml` — Commons settings (shared across all profiles)
- `application-dev.yml` — Dev overrides (H2, debug logging, relaxed CSP)
- `application-prod.yml` — Prod overrides (connection pooling, SSL, strict CSP)
- `application-test.yml` — Test configuration (in-memory H2 DB, Spring Events for pipeline, optional Redis mock)

---

## Build Commands

| Command | Description |
|---------|-------------|
| `./mvnw clean compile` | Compile all source code |
| `./mvnw test` | Run unit tests (JUnit 5 + Mockito) |
| `./mvnw verify` | Run unit + integration tests + Checkstyle + JaCoCo coverage |
| `./mvnw package -DskipTests` | Build JAR without tests |
| `./mvnw site` | Generate project site (reports, coverage, Checkstyle) |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | Run locally with dev profile |
| `./mvnw clean verify -Pintegration` | Run full verification including integration tests |
| `./mvnw checkstyle:check` | Run Checkstyle validation only |

---

## Testing

### Unit Tests (JUnit 5 + Mockito)

Located in `src/test/java/com/autoresolve/mediabuying/`.

```bash
./mvnw test
```

Key test classes:
- `CompositeScoringServiceTest` — Scoring algorithm, empty data, weight distribution
- `KPIQueryServiceTest` — Pagination, sorting, cache hit/miss, source name population
- `CalculatorServiceTest` — ROI formula, edge cases, validation
- `BaseApiWrapperTest` — Retry, 401/429 handling, circuit breaker
- `SourceAttributionServiceTest` — Source linking, verification
- `DataSourceIngestionSchedulerTest` — Parallel execution, failure isolation (replaces `AdPlatformIngestionSchedulerTest`)
- `PipelineVerificationTest` — End-to-end pipeline integration test (10 sources, 4 stages)
- `KpiNormalizerTest` — Field mapping, null safety, name normalization

### Integration Tests

```bash
./mvnw verify -Pintegration
```

Uses Testcontainers to spin up PostgreSQL (Redis optional). Tests end-to-end pipeline from ingestion scheduler through Spring Events to database persistence.

### E2E Tests (Playwright)

Located in `playwright/e2e/`.

```bash
# Install Playwright browsers
npx playwright install --with-deps

# Run all E2E tests
npx playwright test

# Run a specific test
npx playwright test dashboard-load.spec.js

# Run with UI mode
npx playwright test --ui
```

Test specs:
- `dashboard-load.spec.js` — Page load time < 2s, sidebar/topbar visibility
- `drilldown-navigation.spec.js` — Platform expand → sector click → metrics table
- `sector-filter.spec.js` — Dropdown selection, URL redirect
- `calculator-flow.spec.js` — Form fill → submit → results display
- `rbac-masking.spec.js` — Viewer sees masked values, admin sees real values
- `csv-export.spec.js` — Download headers and content verification
- `source-citation.spec.js` — Citation icon click → dialog → source metadata
- `client-prospects.spec.js` — Client table in opportunity card

---

## CI/CD Pipeline

The GitHub Actions pipeline (`.github/workflows/ci-cd.yml`) consists of 5 stages:

```
Push to main → Build & Test → Docker Build → Deploy Dev → Deploy Staging → Deploy Prod
                                                                   ↑              ↑
                                                            Manual approval  Manual approval
```

### Stage 1: Build & Test
- **Trigger**: Push to `main`, `develop`, or PR to `main`
- **Steps**: Maven compile → Unit tests → SonarQube analysis → Checkstyle validation → JaCoCo coverage (≥ 80%)
- **Artifacts**: JAR file, coverage report, Checkstyle report

### Stage 2: Docker Build & Push
- **Steps**: Build container image → Tag with git SHA and `latest` → Push to ECR
- **Security**: Trivy vulnerability scan before push

### Stage 3: Deploy to Dev (Automatic)
- **Steps**: `helm upgrade --install` with `values-dev.yaml`
- **Verification**: Smoke test (HTTP 200 on `/actuator/health`)

### Stage 4: Deploy to Staging (Manual Approval)
- **Steps**: Deploy with `values-staging.yaml` → Run Playwright E2E suite
- **Approval**: GitHub Environment protection rule

### Stage 5: Deploy to Prod (Manual Approval)
- **Steps**: Deploy with `values-prod.yaml` → Canary (10% traffic, 3 steps) → Full rollout
- **Approval**: GitHub Environment protection rule + Slack notification

---

## Docker Local Stack

The `docker-compose-dev.yml` file starts all infrastructure dependencies locally:

```yaml
services:
  postgres:
    image: postgres:15.8
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: media_buying
      POSTGRES_USER: mediabuyer
      POSTGRES_PASSWORD: devpassword
    volumes:
      - pgdata:/var/lib/postgresql/data

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
```

### Starting the Stack
```bash
docker compose -f docker-compose-dev.yml up -d
```

### Stopping the Stack
```bash
docker compose -f docker-compose-dev.yml down
```

### Resetting Data
```bash
docker compose -f docker-compose-dev.yml down -v   # Removes volumes
docker compose -f docker-compose-dev.yml up -d      # Fresh start
```

---

## How to Add a New Ad Platform

Follow these 6 steps to integrate a new advertising platform (e.g., Snapchat Ads, Pinterest Ads, Amazon Ads).

### Step 1: Create the API Wrapper
Create `src/main/java/com/autoresolve/mediabuying/integration/wrapper/SnapchatApiWrapper.java`:

```java
@Component
public class SnapchatApiWrapper extends BaseApiWrapper {

    @Override
    public String getPlatformName() {
        return "snapchat_ads";
    }

    @Override
    protected PlatformApiResponse doFetchMetrics() {
        // Use tokenManager.getAccessToken("snapchat_ads") for auth
        // Call Snapchat Marketing API
        // Map response to PlatformApiResponse using builder
        return PlatformApiResponse.builder()
                .platformName("snapchat_ads")
                .sectorName("Technology")
                .roas(new BigDecimal("3.2"))
                .cac(new BigDecimal("45.00"))
                // ... other KPI fields
                .build();
    }
}
```

### Step 2: Register OAuth Configuration
Add OAuth config to `application.yml`:
```yaml
integration:
  oauth:
    snapchat-ads:
      token-url: https://accounts.snapchat.com/login/oauth2/access_token
      client-id: ${INTEGRATION_SNAPCHAT_ADS_CLIENT_ID}
      client-secret: ${INTEGRATION_SNAPCHAT_ADS_CLIENT_SECRET}
      scopes: ads:read
```

### Step 3: Add Rate Limits
```yaml
integration:
  rate-limit:
    snapchat-ads: 5.0    # 5 requests per second
```

### Step 4: Register API Key Secret
In `values.yaml` (and environment-specific values), add the secret reference:
```yaml
externalSecret:
  secrets:
    - name: snapchat-ads-api-key
      secretKey: INTEGRATION_SNAPCHAT_ADS_API_KEY
      remoteRef:
        key: media-buying/{{ .Values.global.environment }}/snapchat-ads-api-key
```

### Step 5: Add to Ingestion Scheduler
> **Note**: The old `AdPlatformIngestionScheduler` has been **removed** and replaced by `DataSourceIngestionScheduler`. For new market-data sources, register your wrapper in `DataSourceIngestionScheduler.java`.

In `DataSourceIngestionScheduler.java`, add your wrapper to the parallel fetch list:
```java
CompletableFuture<MarketDataFeed> snapchatFuture = fetchAndPublish(snapchatApiWrapper, "snapchat_ads");
```

### Step 6: Add Database Seed Data (if new sector)
Insert the new platform into the `platform` table via a new Flyway migration or seed script:
```sql
INSERT INTO platform (name, display_name, is_active) VALUES ('snapchat_ads', 'Snapchat Ads', true);
```

---

## Troubleshooting

### Port Conflicts

**Symptom**: `Address already in use` when starting Docker stack or Spring Boot.

**Solution**:
```bash
# Check what's using the port
sudo lsof -i :5432    # PostgreSQL
sudo lsof -i :6379    # Redis
sudo lsof -i :9092    # Kafka
sudo lsof -i :8080    # Application

# Kill the process or change the port in docker-compose-dev.yml / application.yml
```

### Lombok Not Working

**Symptom**: IDE shows compilation errors on `@Data`, `@Builder`, `@Slf4j` annotations.

**Solution**:
1. Ensure Lombok plugin is installed and enabled in your IDE.
2. Enable annotation processing (IntelliJ: `Settings` → `Compiler` → `Annotation Processors` → Enable).
3. Verify Lombok version in `pom.xml` matches the IDE plugin version.
4. Restart IDE and run `./mvnw clean compile` from command line to verify.

### H2 Console Not Accessible

**Symptom**: Cannot access `http://localhost:8080/h2-console` in dev mode.

**Solution**:
1. Verify `application-dev.yml` has `spring.h2.console.enabled=true`.
2. Ensure `spring.h2.console.path=/h2-console` is set.
3. Check that Spring Security allows `/h2-console/**` in `SecurityConfig` (already configured for `dev` and `test` profiles).

### Event Bus / Spring Events Issues

> **Note**: The old `AdPlatformIngestionScheduler` has been **removed** and replaced by `DataSourceIngestionScheduler`. The pipeline now uses Spring Events (`ApplicationEventPublisher` + `@EventListener`) for all inter-stage messaging.

The application uses an **EventBus abstraction** with two implementations:
- **`dev` profile** (default): Spring Events (in-JVM, no external services needed)
- **`k8s` / `docker` profiles**: Apache Kafka (requires a running broker)

**Symptom (dev profile)**: Errors related to Kafka or brokers.  
**Fix**: Ensure you're using the `dev` profile — it uses in-JVM Spring Events, not Kafka. If Kafka is not running, the application will still start correctly under `dev`.

**Symptom (docker/k8s profiles)**: `KafkaException: Failed to connect to broker` or consumer not receiving messages.

**Solution**:
```bash
# 1. Verify Kafka is running
docker compose -f docker-compose-dev.yml ps

# 2. Check Kafka logs
docker compose -f docker-compose-dev.yml logs kafka

# 3. Verify broker is listening
docker compose -f docker-compose-dev.yml exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# 4. Reset Kafka offsets (dev only)
docker compose -f docker-compose-dev.yml exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group media-buying-kpi-consumer \
  --reset-offsets --to-earliest \
  --execute --all-topics
```

**Symptom (Spring Events dev profile)**: Events not being processed or accumulator not draining.

**Solution**:
```bash
# 1. Check the application logs for EventBus-related errors
# 2. Verify @Async is properly configured (see AsyncConfig.java)
# 3. Check that DataSourceIngestionScheduler is running (@Scheduled)
# 4. Look for ERROR-level logs from AsyncUncaughtExceptionHandler
```

### Flyway Migration Failures

**Symptom**: Application fails to start with Flyway validation errors.

**Solution**:
1. Check migration checksums: `./mvnw flyway:info`
2. Repair if needed: `./mvnw flyway:repair` (dev only — do not run against prod)
3. Reset database (dev only):
   ```bash
   docker compose -f docker-compose-dev.yml down -v
   docker compose -f docker-compose-dev.yml up -d
   ```
4. For prod, create a new migration to correct the schema; never modify existing migrations.

### Cache Not Populating

**Symptom**: Cache miss on every request; high database load.

**Solution**:
1. Check that `CacheInvalidationService` is not continuously purging keys:
   - Look for `kpi.refresh` Spring Events being produced too frequently
   - Check the Spring Events processing metrics (see Observability Guide → Spring Events Monitoring)
2. Verify TTL settings in `NativeCacheService.java` — 5 minutes for metrics, 15 minutes for platforms.

---

## Code Style

The project uses **Checkstyle** with Google Java Style as the baseline, with the following custom rules:

| Rule | Standard |
|------|----------|
| **Maximum line length** | 120 characters (Google default is 100) |
| **Indentation** | 4 spaces (no tabs) |
| **File encoding** | UTF-8 |
| **Javadoc** | Required on all public methods and classes |
| **Package naming** | `com.autoresolve.mediabuying.<module>` |
| **Imports** | No wildcard imports; unused imports flagged as error |

### Running Checkstyle
```bash
./mvnw checkstyle:check
```

### Checkstyle Configuration
The rules are defined in the `pom.xml` under the `maven-checkstyle-plugin` configuration. CI will fail if Checkstyle violations are found.

### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `KPIQueryService`, `TopOpportunityDTO` |
| Methods | camelCase | `getMetrics()`, `calculateTopOpportunity()` |
| Constants | UPPER_SNAKE_CASE | `COMPOSITE_TOP`, `KPI_STALENESS_THRESHOLD` |
| Packages | lowercase.dot.separated | `com.autoresolve.mediabuying.service` |
| Database tables | snake_case | `kpi_metrics`, `client_prospects` |
| Database columns | snake_case | `est_annual_revenue`, `ingestion_timestamp` |
| Spring Events topics | lowercase.dot.separated | `source.raw`, `sector.grouped`, `company.grouped`, `pipeline.batch-complete`, `kpi.refresh` |
| REST endpoints | kebab-case | `/api/kpi/{id}/sources`, `/api/opportunity/top` |
| Environment variables | UPPER_SNAKE_CASE | `SPRING_DATASOURCE_URL`, `KAFKA_BOOTSTRAP_SERVERS` |

---

## Related Documents

- [Architecture Decision Records](adr/) — Key technology decisions with rationale
- [User Guide](dashboard_user_guide.md) — End-user documentation
- [Observability Guide](observability-guide.md) — Monitoring, alerting, and logging
- [HLD.md](/design/HLD.md) — High-level system architecture
- [LLD.md](/design/LLD.md) — Detailed component design
- [Project Plan](/design/Project_Plan.md) — Full project timeline and WBS
