package com.autoresolve.mediabuying.controller.dashboard;

import com.autoresolve.mediabuying.model.dto.SourceMetadataDTO;
import com.autoresolve.mediabuying.service.DashboardService;
import com.autoresolve.mediabuying.service.PlatformSectorService;
import com.autoresolve.mediabuying.service.SourceAttributionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardBean source citation functionality (FRONT-29).
 */
@ExtendWith(MockitoExtension.class)
class DashboardBeanTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private PlatformSectorService platformSectorService;

    @Mock
    private SourceAttributionService sourceAttributionService;

    private DashboardBean dashboardBean;

    @BeforeEach
    void setUp() {
        // Create the bean with mocks injected via reflection
        dashboardBean = new DashboardBean();
        setField(dashboardBean, "sourceAttributionService", sourceAttributionService);
    }

    @Test
    void testLoadKpiSourcesCacheMiss() {
        Long kpiId = 1L;
        SourceMetadataDTO source = SourceMetadataDTO.builder()
                .sourceName("Test Source")
                .sourceType("API")
                .sourceUrl("https://example.com")
                .licenseType("MIT")
                .isStale(false)
                .build();
        List<SourceMetadataDTO> sources = Collections.singletonList(source);
        when(sourceAttributionService.getSourcesForKpi(kpiId)).thenReturn(sources);

        dashboardBean.loadKpiSources(kpiId);

        assertNotNull(dashboardBean.getCurrentSources());
        assertEquals(1, dashboardBean.getCurrentSources().size());
        assertEquals("Test Source", dashboardBean.getCurrentSources().get(0).getSourceName());
        assertFalse(dashboardBean.isKpiSourceStale(kpiId));

        // Verify service called exactly once
        verify(sourceAttributionService, times(1)).getSourcesForKpi(kpiId);
    }

    @Test
    void testLoadKpiSourcesCacheHit() {
        Long kpiId = 1L;
        SourceMetadataDTO source = SourceMetadataDTO.builder()
                .sourceName("Cached Source")
                .sourceType("API")
                .isStale(false)
                .build();
        when(sourceAttributionService.getSourcesForKpi(kpiId))
                .thenReturn(Collections.singletonList(source));

        // First call — cache miss
        dashboardBean.loadKpiSources(kpiId);
        // Second call — cache hit
        dashboardBean.loadKpiSources(kpiId);

        // Service should only be called once (the second call hits cache)
        verify(sourceAttributionService, times(1)).getSourcesForKpi(kpiId);
    }

    @Test
    void testKpiSourceStaleFlag() {
        Long kpiId = 1L;
        SourceMetadataDTO staleSource = SourceMetadataDTO.builder()
                .sourceName("Stale Source")
                .sourceType("CSV")
                .isStale(true)
                .build();
        when(sourceAttributionService.getSourcesForKpi(kpiId))
                .thenReturn(Collections.singletonList(staleSource));

        dashboardBean.loadKpiSources(kpiId);

        assertTrue(dashboardBean.isKpiSourceStale(kpiId));
    }

    @Test
    void testKpiSourceStaleFalseForFreshSources() {
        Long kpiId = 1L;
        SourceMetadataDTO freshSource = SourceMetadataDTO.builder()
                .sourceName("Fresh Source")
                .sourceType("API")
                .isStale(false)
                .build();
        when(sourceAttributionService.getSourcesForKpi(kpiId))
                .thenReturn(Collections.singletonList(freshSource));

        dashboardBean.loadKpiSources(kpiId);

        assertFalse(dashboardBean.isKpiSourceStale(kpiId));
    }

    @Test
    void testLoadKpiSourcesReturnsEmptyListOnServiceError() {
        Long kpiId = 1L;
        when(sourceAttributionService.getSourcesForKpi(kpiId))
                .thenThrow(new RuntimeException("Service unavailable"));

        dashboardBean.loadKpiSources(kpiId);

        assertNotNull(dashboardBean.getCurrentSources());
        assertTrue(dashboardBean.getCurrentSources().isEmpty());
    }

    @Test
    void testInvalidateSourceCache() {
        Long kpiId = 1L;
        when(sourceAttributionService.getSourcesForKpi(kpiId))
                .thenReturn(Collections.singletonList(
                        SourceMetadataDTO.builder().sourceName("S").sourceType("T").build()));

        dashboardBean.loadKpiSources(kpiId);
        assertNotNull(dashboardBean.getCurrentSources());
        assertTrue(dashboardBean.isKpiSourceStale(kpiId) || !dashboardBean.isKpiSourceStale(kpiId)); // just checking no NPE

        dashboardBean.invalidateSourceCache();
        assertNull(dashboardBean.getCurrentSources());
        // After invalidation, stale check should return false
        assertFalse(dashboardBean.isKpiSourceStale(kpiId));
    }

    @Test
    void testSourceCacheEntry() {
        SourceMetadataDTO stale = SourceMetadataDTO.builder().isStale(true).build();
        SourceMetadataDTO fresh = SourceMetadataDTO.builder().isStale(false).build();

        DashboardBean.SourceCacheEntry entryAllStale = new DashboardBean.SourceCacheEntry(
                Arrays.asList(stale, stale));
        assertTrue(entryAllStale.isAnyStale());

        DashboardBean.SourceCacheEntry entryMixed = new DashboardBean.SourceCacheEntry(
                Arrays.asList(fresh, stale));
        assertTrue(entryMixed.isAnyStale());

        DashboardBean.SourceCacheEntry entryAllFresh = new DashboardBean.SourceCacheEntry(
                Arrays.asList(fresh, fresh));
        assertFalse(entryAllFresh.isAnyStale());

        DashboardBean.SourceCacheEntry entryEmpty = new DashboardBean.SourceCacheEntry(
                Collections.emptyList());
        assertFalse(entryEmpty.isAnyStale());

        assertNotNull(entryAllFresh.getCachedAt());
        assertNotNull(entryAllFresh.getSources());
    }

    /**
     * Helper to inject the mock via setter (uses reflection to set private field).
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}

// Extend DashboardBean to allow setter injection for testing
// (We use reflection-based helper above instead)
