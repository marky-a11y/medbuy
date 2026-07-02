package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheKeys;
import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.messaging.dto.KpiRefreshEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class CacheInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);

    private final CacheService cacheService;

    public CacheInvalidationService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Processes KpiRefreshEvent payloads from the {@code kpi.refresh} event bus topic.
     * Events are delivered asynchronously via Spring Events
     * using the {@code eventTaskExecutor} thread pool.
     */
    @Async("eventTaskExecutor")
    @EventListener(condition = "#event.topic == 'kpi.refresh'")
    public void onKpiRefresh(IntegrationEvent event) {
        Object payload = event.getPayload();
        if (!(payload instanceof KpiRefreshEvent)) {
            log.warn("Unexpected payload type on kpi.refresh: {}", payload.getClass().getName());
            return;
        }
        KpiRefreshEvent refreshEvent = (KpiRefreshEvent) payload;

        log.info("Cache invalidation triggered: platform={}, sector={}",
                refreshEvent.getPlatformId(), refreshEvent.getSectorId());

        // 1. Invalidate top opportunity cache
        cacheService.delete(CacheKeys.COMPOSITE_TOP);

        // 2. Invalidate hierarchy cache
        cacheService.delete(CacheKeys.HIERARCHY_ALL);

        // 3. Invalidate all metrics keys for this platform+sector (all pages/sorts)
        String metricsPattern = String.format("%s:%d:%d:*", CacheKeys.METRICS_PREFIX,
                refreshEvent.getPlatformId(), refreshEvent.getSectorId());
        Set<String> metricKeys = cacheService.keys(metricsPattern);
        if (metricKeys != null && !metricKeys.isEmpty()) {
            for (String key : metricKeys) {
                cacheService.delete(key);
            }
            log.debug("Invalidated {} metrics cache keys", metricKeys.size());
        }

        // 4. Invalidate client prospects cache for this sector
        String clientsKey = CacheKeys.clientsTopKey(refreshEvent.getSectorId());
        cacheService.delete(clientsKey);
        log.debug("Invalidated client prospects cache: key={}", clientsKey);

        // 5. Invalidate all client list cache keys
        Set<String> clientListKeys = cacheService.keys(CacheKeys.clientsListWildcard());
        if (clientListKeys != null && !clientListKeys.isEmpty()) {
            for (String key : clientListKeys) {
                cacheService.delete(key);
            }
            log.debug("Invalidated {} client list cache keys", clientListKeys.size());
        }

        // 6. Invalidate insights cache
        cacheService.delete(CacheKeys.INSIGHTS_CLIENT_GAPS);
        log.debug("Invalidated insights cache: key={}", CacheKeys.INSIGHTS_CLIENT_GAPS);

        log.info("Cache invalidated: keys removed (top={}, hierarchy={}, metrics={}, clients={}, clientLists={}, insights={})",
                "1", "1", (metricKeys != null ? metricKeys.size() : 0), "1",
                (clientListKeys != null ? clientListKeys.size() : 0), "1");
    }

    /**
     * Manually invalidates all cached data. Useful for admin-triggered refreshes.
     */
    public void manualRefresh() {
        log.info("Manual cache refresh triggered");

        // Invalidate top opportunity cache
        cacheService.delete(CacheKeys.COMPOSITE_TOP);

        // Invalidate hierarchy cache
        cacheService.delete(CacheKeys.HIERARCHY_ALL);

        // Invalidate all metrics keys
        Set<String> allMetricsKeys = cacheService.keys(CacheKeys.METRICS_PREFIX + ":*");
        if (allMetricsKeys != null && !allMetricsKeys.isEmpty()) {
            for (String key : allMetricsKeys) {
                cacheService.delete(key);
            }
            log.debug("Invalidated {} metrics cache keys", allMetricsKeys.size());
        }

        // Invalidate client prospects caches
        cacheService.deleteByPattern(CacheKeys.CLIENTS_TOP_PREFIX + ":*");

        // Invalidate all client list cache keys
        Set<String> clientListKeys = cacheService.keys(CacheKeys.clientsListWildcard());
        if (clientListKeys != null && !clientListKeys.isEmpty()) {
            for (String key : clientListKeys) {
                cacheService.delete(key);
            }
        }

        // Invalidate insights cache
        cacheService.delete(CacheKeys.INSIGHTS_CLIENT_GAPS);

        log.info("Manual cache refresh complete");
    }
}
