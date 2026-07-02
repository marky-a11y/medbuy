# Observability Guide — Media Buying Dashboard

## Architecture Diagram & Component Overview

The observability stack follows a **three‑pillar approach**: Metrics (Prometheus + Grafana), Logs (Elasticsearch + Kibana + Filebeat), and Alerts (AlertManager → Slack + PagerDuty). TLS is handled by cert-manager + Let's Encrypt.

```
┌─────────────────────────────────────────────────────────────────┐
│                         Kubernetes Cluster (EKS)                 │
│                                                                  │
│  ┌──────────────┐   ┌──────────────────────────┐     │
│  │  Dashboard     │   │  Other Pods               │    │
│  │  Pods          │   │  (node-exporter,          │    │
│  │  (app)         │   │   kube-state-m)           │    │
│  └───────┬───────┘   └────────┬─────────────────┘     │
│          │                     │                       │
│          │  /actuator/         │                       │
│          │  prometheus         │                       │
│          ▼                  ▼                     ▼               │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │            Prometheus (kube-prometheus-stack)              │   │
│  │  ┌──────────┐  ┌───────────┐  ┌───────────────────────┐   │   │
│  │  │ Prometheus│  │AlertManager│  │ Grafana (+ sidecar)   │   │   │
│  │  │ 2 replicas│  │ 2 replicas │  │ 1 replica             │   │   │
│  │  └──────────┘  └─────┬─────┘  └───────────┬───────────┘   │   │
│  └──────────────────────┼────────────────────┼────────────────┘   │
│                         │                    │                     │
│                         ▼                    ▼                     │
│                  ┌──────────┐       ┌──────────────────┐          │
│                  │ Slack    │       │ Grafana Dashboards│          │
│                  │(#alerts- │       │ (Auto-discovered  │          │
│                  │ warning) │       │  via ConfigMap)   │          │
│                  └──────────┘       └──────────────────┘          │
│                         │                                         │
│                         ▼                                         │
│                  ┌──────────────┐                                 │
│                  │  PagerDuty   │                                 │
│                  │(CRITICAL only)│                                │
│                  └──────────────┘                                 │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │              ELK Stack (Logging)                           │   │
│  │  ┌──────────┐  ┌──────────┐  ┌───────────────────────┐   │   │
│  │  │ Filebeat  │→│Elasticsearch│→│ Kibana                │   │   │
│  │  │DaemonSet  │  │ 3 nodes    │  │ 1 replica + Ingress   │   │   │
│  │  └──────────┘  └──────────┘  └───────────────────────┘   │   │
│  └───────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │              cert-manager (TLS)                            │   │
│  │  ┌──────────────────┐  ┌──────────────────────────────┐   │   │
│  │  │ ClusterIssuer     │  │ Certificate resources        │   │   │
│  │  │ (Let's Encrypt)   │  │ - dashboard                  │   │   │
│  │  │                   │  │ - grafana                    │   │   │
│  │  │                   │  │ - kibana                     │   │   │
│  │  └──────────────────┘  └──────────────────────────────┘   │   │
│  └───────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Component Overview

| Component | Version | Replicas | Purpose |
|-----------|---------|----------|---------|
| **Prometheus** | 2.x (via kube-prometheus-stack) | 2 | Metrics collection, alert evaluation |
| **AlertManager** | 0.x | 2 | Alert deduplication, routing, inhibition |
| **Grafana** | 10.x | 1 | Visualization dashboards |
| **node-exporter** | 1.x | Per node | Host-level metrics (CPU, memory, disk) |
| **kube-state-metrics** | 2.x | 1 | Kubernetes object metrics |
| **Elasticsearch** | 8.x | 3 | Log storage & search |
| **Kibana** | 8.x | 1 | Log visualization & exploration |
| **Filebeat** | 8.x | DaemonSet | Log shipping from containers |
| **cert-manager** | 1.15 | 2 | TLS certificate provisioning & renewal |

---

## Accessing Grafana

### URL
- **Production**: `https://grafana.media-buying.autoresolve.com`
- **Dev/Staging**: via `kubectl port-forward`:
  ```bash
  kubectl port-forward -n monitoring svc/prometheus-stack-grafana 3000:80
  ```
  Then open `http://localhost:3000`

### Default Credentials
- **Username**: `admin`
- **Password**: Retrieved from the Helm values secret or from the generated admin password:
  ```bash
  kubectl get secret -n monitoring prometheus-stack-grafana -o jsonpath='{.data.admin-password}' | base64 -d
  ```

### Available Dashboards
| Dashboard | Description | UID |
|-----------|-------------|-----|
| **Media Buying Dashboard - Application Metrics** | JVM, API perf, cache, DB, integration | `media-buying-app` |
| **Kubernetes / Compute Resources / Namespace (Pods)** | Built-in: CPU/memory per pod | (auto) |
| **Kubernetes / Pods** | Built-in: Pod status, restarts | (auto) |

### How to Add a New Dashboard Panel

1. Open the target dashboard in Grafana → **Edit** (pencil icon).
2. Click **Add panel** → **Add a new panel**.
3. Select the **Prometheus** datasource.
4. Write a PromQL query using available metrics (see **Custom Metrics Reference** below).
5. Configure visualization type, unit, thresholds, legends.
6. Set panel title and description.
7. Click **Apply** → **Save dashboard**.

> **Important**: Dashboard changes made via the Grafana UI are NOT persisted across ConfigMap updates. To make permanent changes, edit the JSON file in `infrastructure/observability/grafana-dashboards/` and re-apply the ConfigMap:
> ```bash
> kubectl create configmap grafana-dashboards-media-buying \
>   --namespace monitoring \
>   --from-file=infrastructure/observability/grafana-dashboards/media-buying-dashboard.json \
>   --dry-run=client -o yaml | kubectl apply -f -
> ```

---

## Accessing Kibana

### URL
- **Production**: `https://kibana.media-buying.autoresolve.com`
- **Dev/Staging**: via port-forward:
  ```bash
  kubectl port-forward -n logging svc/kibana-kibana 5601:5601
  ```

### Credentials
- **Username**: `elastic`
- **Password**: Retrieve from Elasticsearch secret:
  ```bash
  kubectl get secret elasticsearch-master-credentials -n logging -o jsonpath='{.data.password}' | base64 -d
  ```

### Creating an Index Pattern
1. Login to Kibana → **Stack Management** → **Index Patterns**.
2. Click **Create index pattern**.
3. Enter `media-buying-logs-*` and select `@timestamp` as the time field.
4. Click **Create index pattern**.
5. Go to **Discover** to explore logs.

### Searching Logs
- Search by `correlationId`: `correlationId:"abc-123-def"`
- Search by user: `userId:"admin"`
- Search by log level: `level:"ERROR"`
- Time range filter: Last 15 minutes, Last 1 hour, etc.

---

## Alert Response Procedures

AlertManager routes alerts to **Slack** (`#alerts-warning`) and **PagerDuty** (CRITICAL only). Below are response procedures per alert group.

### 1. API Performance Alerts

| Alert | Severity | Action |
|-------|----------|--------|
| `HighApiErrorRate` | CRITICAL | 5XX error > 1% for 5 min. Check upstream dependencies. Look for recent deployments or external API changes. Check pod logs: `kubectl logs -n media-buying <pod>` |
| `HighApiLatencyP99` | WARNING | p99 latency > 2s. Check DB query performance, Redis cache hit ratio. Scale pods if under CPU/memory pressure. |
| `HighSlow5xxCount` | WARNING | >10 slow 5XX in 5 min. Investigate endpoint-specific issues. |
| `LowApiRequestRate` | WARNING | Near-zero traffic for 10 min. Possible networking issue or ingress misconfiguration. |

### 2. JVM Health Alerts

| Alert | Severity | Action |
|-------|----------|--------|
| `HighJvmHeapUsage` | WARNING | Heap > 85%. Check for memory leak. Consider increasing heap (`-Xmx`). |
| `CriticalJvmHeapUsage` | CRITICAL | Heap > 95%. Immediate OOM risk. Restart pod or scale up. |
| `HighGcOverhead` | WARNING | Average GC pause > 50ms. Tune G1GC settings. |
| `HighGcPauseLatency` | WARNING | Max GC pause > 1s. Indicates severe GC pressure. |
| `JvmThreadDeadlock` | CRITICAL | Deadlock detected. Thread dump and restart pod. |

### 3. Cache Performance Alerts

| Alert | Severity | Action |
|-------|----------|--------|
| `LowCacheHitRatio` | WARNING | Hit ratio < 80%. Check for cache invalidation storms. Verify Redis memory limits. |
| `CriticallyLowCacheHitRatio` | CRITICAL | Hit ratio < 50%. DB under severe load. Investigate immediately. |
| `HighCacheEvictionRate` | WARNING | Evictions > 100/min. Redis maxmemory may be too low. |
| `RedisConnectionPoolExhaustion` | WARNING | Pool > 90% utilized. Increase Lettuce pool size. |

### 4. Database Health Alerts

| Alert | Severity | Action |
|-------|----------|--------|
| `HighDbConnectionPoolUsage` | WARNING | Pool > 85%. Increase HikariCP maximum pool size. |
| `SlowDatabaseQueries` | WARNING | p95 query latency > 500ms. Check for missing indexes or slow queries. |

### 5. Integration Health Alerts

| Alert | Severity | Action |
|-------|----------|--------|
| `PlatformIntegrationFailing` | WARNING | >5 failures/min on platform API. Check platform status pages. |
| `OAuthTokenRefreshFailure` | CRITICAL | Token refresh failing. Check OAuth configuration and token endpoint availability. |

### 6. Pod Health Alerts

| Alert | Severity | Action |
|-------|----------|--------|
| `PodCrashLooping` | CRITICAL | Pod restarting frequently. Check logs: `kubectl logs --previous <pod>` |
| `PodNotReady` | CRITICAL | Pod not ready for >5 min. Check readiness probe and application health. |
| `DeploymentReplicasUnavailable` | CRITICAL | Deployment has unavailable replicas. Check recent rollouts. |
| `HpaNearMaxCapacity` | WARNING | HPA near max replicas. Increase `maxReplicas` in HPA config. |

### 7. Data Freshness Alerts

| Alert | Severity | Action |
|-------|----------|--------|
| `TopOpportunityStale` | WARNING | Top Opportunity not recomputed in >15 min. Check ingestion scheduler. |
| `KpiDataStale` | WARNING | KPI data >20 min old. Check ingestion pipeline. |
| `TopOpportunityScoreLow` | INFO | Top score < 50. Review scoring weights and KPI targets. |

---

## Custom Metrics Reference

The following custom Micrometer metrics are instrumented in the Java application code and exposed at `/actuator/prometheus`:

| Metric Name | Type | Tags | Source File | Description |
|-------------|------|------|-------------|-------------|
| `media_buying_cache_hit_total` | Counter | `service="KPIQueryService"` | `KPIQueryService.java` | Total number of cache hits in KPIQueryService |
| `media_buying_cache_miss_total` | Counter | `service="KPIQueryService"` | `KPIQueryService.java` | Total number of cache misses in KPIQueryService |
| `media_buying_integration_success_total` | Counter | `class=<WrapperClassName>` | `BaseApiWrapper.java` | Total successful integration API calls |
| `media_buying_integration_failure_total` | Counter | `class=<WrapperClassName>` | `BaseApiWrapper.java` | Total failed integration API calls |
| `media_buying_oauth_token_refresh_errors_total` | Counter | `component="OAuthTokenManager"` | `OAuthTokenManager.java` | Total OAuth token refresh errors |
| `media_buying_scoring_computation_seconds` | Timer | `service="CompositeScoringService"` | `CompositeScoringService.java` | Time taken to compute composite scores |
| `media_buying_scoring_top_score` | Gauge | `service="CompositeScoringService"` | `CompositeScoringService.java` | Current top opportunity composite score (0-100) |
| `media_buying_ingestion_timestamp_seconds` | Gauge | `component="KPIStreamConsumer"` | `KPIStreamConsumer.java` | Epoch timestamp of last successful KPI ingestion |

### Standard Micrometer Metrics (auto-configured)

| Metric Prefix | Source | Description |
|---------------|--------|-------------|
| `jvm_*` | Micrometer JVM extension | JVM memory, GC, threads, classes |
| `http_server_requests_*` | Spring Boot Actuator | HTTP request metrics (count, duration, buckets) |
| `hikaricp_*` | HikariCP connection pool | Active, idle, pending connections; connection latency |
| `lettuce_*` | Lettuce Redis client | Connection pool metrics, command latency |
| `logback_*` | Logback metrics | Log event counts per level |
| `process_*` | Micrometer | Process CPU, memory, file descriptors |
| `system_*` | Micrometer | System CPU, load average |

---

### Spring Events Monitoring

The pipeline uses Spring `ApplicationEventPublisher` + `@EventListener` for inter-stage messaging:

| Event Topic | Publisher | Listener | Async? | Metric |
|-------------|-----------|----------|--------|--------|
| `source.raw` | `DataSourceIngestionScheduler` | `SectorGrouper` | No | Event publish rate |
| `sector.grouped` | `SectorGrouper` | `CompanyPlatformGrouper` | No | Event publish rate |
| `company.grouped` | `CompanyPlatformGrouper` | `KpiBuilder.onCompanyGrouped()` | No | Accumulator size |
| `pipeline.batch-complete` | `DataSourceIngestionScheduler` | `KpiBuilder.onBatchComplete()` | Yes (`@Async`) | Processing time, KPI count |
| `kpi.refresh` | `KpiBuilder.onBatchComplete()` | `CacheInvalidationService` | Yes (`@Async`) | Cache invalidation count |

**Key metrics to monitor:**
- `pipeline.batch-complete` processing time (should be < 5 seconds for 10 sources)
- KPI upsert count per batch cycle
- Accumulator drain success rate
- Event processing errors (logged at ERROR level by `AsyncUncaughtExceptionHandler`)

---

## How to Add a New Alert Rule

1. Open `infrastructure/observability/prometheus-rules.yaml`.
2. Add a new rule inside the appropriate `groups[].rules[]` array.
3. Follow the existing pattern:
   ```yaml
   - alert: YourAlertName
     expr: |
       your_promql_expression > threshold
     for: 5m
     labels:
       severity: warning|critical|info
        component: api|jvm|cache|database|integration|pod|data
       metric: your_metric_name
     annotations:
       summary: "Human-readable summary with {{ $labels }}"
       description: "Detailed description with {{ $value }}"
       runbook_url: "https://wiki.autoresolve.com/runbooks/your-runbook"
   ```
4. Apply the changes:
   ```bash
   kubectl apply -f infrastructure/observability/prometheus-rules.yaml
   ```
5. Verify the rule appears in Prometheus UI: **Status** → **Rules**.
6. Test the rule by temporarily lowering the threshold.

### Alert Severity Classification

| Severity | Meaning | Notification Channel | Response Time |
|----------|---------|---------------------|---------------|
| `critical` | Service impacting or data loss imminent | PagerDuty + Slack | < 15 minutes |
| `warning` | Potential issue, needs investigation | Slack (`#alerts-warning`) | < 1 hour |
| `info` | Informational, no immediate action | Slack (`#alerts-warning`) | Next business day |

---

## How to Add a New Dashboard Panel

See **Accessing Grafana → How to Add a New Dashboard Panel** above for UI-based addition.

### Adding via ConfigMap (permanent)

1. Edit the dashboard JSON file at `infrastructure/observability/grafana-dashboards/media-buying-dashboard.json`.
2. Add a new panel object to the `panels[]` array with a unique `id`, `title`, `type`, `gridPos`, `datasource`, and `targets`.
3. Re-apply the ConfigMap:
   ```bash
   kubectl create configmap grafana-dashboards-media-buying \
     --namespace monitoring \
     --from-file=infrastructure/observability/grafana-dashboards/media-buying-dashboard.json \
     --dry-run=client -o yaml | kubectl apply -f -
   ```
4. The Grafana sidecar will auto-detect the change and reload the dashboard within 60 seconds.

---

## Troubleshooting

### Prometheus targets not UP
```bash
# Check if the target is reachable
kubectl port-forward -n monitoring svc/prometheus-operated 9090:9090
# Open http://localhost:9090/targets

# Check ServiceMonitor configuration
kubectl describe servicemonitor -n media-buying

# Check if pod has prometheus.io/scrape annotation
kubectl get pod -n media-buying <pod-name> -o jsonpath='{.metadata.annotations}'
```

### Grafana dashboards not showing
```bash
# Verify ConfigMap exists with correct label
kubectl get configmap -n monitoring grafana-dashboards-media-buying -o yaml

# Check Grafana sidecar logs
kubectl logs -n monitoring -l app.kubernetes.io/name=grafana -c grafana-sc-dashboard
```

### No logs in Kibana
```bash
# Check Filebeat status
kubectl get pods -n logging -l app=filebeat

# Check Filebeat logs
kubectl logs -n logging -l app=filebeat

# Verify Elasticsearch is accepting logs
kubectl exec -n logging elasticsearch-master-0 -- curl -s -k https://localhost:9200/_cat/indices
```

### TLS certificate issues
```bash
# Check certificate status
kubectl get certificates -A

# Describe a specific certificate for error details
kubectl describe certificate -n monitoring grafana-tls

# Check cert-manager logs
kubectl logs -n cert-manager -l app=cert-manager
```
