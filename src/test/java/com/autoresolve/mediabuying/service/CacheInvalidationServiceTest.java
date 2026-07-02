package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheKeys;
import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.messaging.dto.KpiRefreshEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheInvalidationServiceTest {

    @Mock
    private CacheService cacheService;

    private CacheInvalidationService cacheInvalidationService;

    @BeforeEach
    void setUp() {
        cacheInvalidationService = new CacheInvalidationService(cacheService);
    }

    @Test
    void testOnKpiRefreshInvalidatesAllCacheKeys() {
        KpiRefreshEvent event = KpiRefreshEvent.builder()
                .platformId(1L)
                .sectorId(1L)
                .eventType("KPI_UPDATED")
                .refreshTimestamp(Instant.now())
                .build();

        Set<String> metricKeys = new HashSet<>();
        metricKeys.add("metrics:1:1:0:roas");
        metricKeys.add("metrics:1:1:1:roas");
        when(cacheService.keys("metrics:1:1:*")).thenReturn(metricKeys);

        Set<String> clientListKeys = new HashSet<>();
        clientListKeys.add("clients:list:0:10:outlookScore:desc");
        when(cacheService.keys("clients:list:*")).thenReturn(clientListKeys);

        cacheInvalidationService.onKpiRefresh(new IntegrationEvent("kpi.refresh", "test", event));

        // Verify single-key deletes: COMPOSITE_TOP, HIERARCHY_ALL, clientsTopKey, INSIGHTS_CLIENT_GAPS
        verify(cacheService).delete(CacheKeys.COMPOSITE_TOP);
        verify(cacheService).delete(CacheKeys.HIERARCHY_ALL);
        verify(cacheService).delete(CacheKeys.clientsTopKey(1L));
        verify(cacheService).delete(CacheKeys.INSIGHTS_CLIENT_GAPS);

        // Verify iterated deletes for metric keys (2 keys)
        verify(cacheService).delete("metrics:1:1:0:roas");
        verify(cacheService).delete("metrics:1:1:1:roas");

        // Verify iterated delete for client list keys (1 key)
        verify(cacheService).delete("clients:list:0:10:outlookScore:desc");

        // Total single-key deletes: 4 + 2 + 1 = 7
        verify(cacheService, times(7)).delete(anyString());
    }

    @Test
    void testOnKpiRefreshWithNoMetricKeys() {
        KpiRefreshEvent event = KpiRefreshEvent.builder()
                .platformId(2L)
                .sectorId(3L)
                .eventType("KPI_UPDATED")
                .refreshTimestamp(Instant.now())
                .build();

        when(cacheService.keys("metrics:2:3:*")).thenReturn(new HashSet<>());
        when(cacheService.keys("clients:list:*")).thenReturn(new HashSet<>());

        cacheInvalidationService.onKpiRefresh(new IntegrationEvent("kpi.refresh", "test", event));

        verify(cacheService).delete(CacheKeys.COMPOSITE_TOP);
        verify(cacheService).delete(CacheKeys.HIERARCHY_ALL);
        verify(cacheService).delete(CacheKeys.INSIGHTS_CLIENT_GAPS);

        // No metric or client-list keys to iterate over
        verify(cacheService, times(4)).delete(anyString());
    }
}
