package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.model.dto.KPIMetricsDTO;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.model.entity.Platform;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KPIQueryServiceTest {

    @Mock
    private KPIMetricsRepository kpiMetricsRepository;

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private CommerceSectorRepository sectorRepository;

    @Mock
    private CacheService cacheService;

    @Mock
    private SourceAttributionService sourceAttributionService;

    private KPIQueryService kpiQueryService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        kpiQueryService = new KPIQueryService(
                kpiMetricsRepository, platformRepository, sectorRepository,
                cacheService, null, sourceAttributionService, meterRegistry);
    }

    @Test
    void testGetMetricsReturnsPaginatedResults() {
        KPIMetrics metrics = KPIMetrics.builder()
                .id(1L)
                .platformId(1L)
                .sectorId(1L)
                .roas(BigDecimal.valueOf(4.5))
                .cac(BigDecimal.valueOf(20.0))
                .build();

        Page<KPIMetrics> page = new PageImpl<>(Collections.singletonList(metrics));
        when(kpiMetricsRepository.findByPlatformIdAndSectorId(
                anyLong(), anyLong(), any(Pageable.class))).thenReturn(page);
        when(platformRepository.findById(1L)).thenReturn(
                Optional.of(Platform.builder().id(1L).displayName("Google Ads").build()));
        when(sectorRepository.findById(1L)).thenReturn(
                Optional.of(CommerceSector.builder().id(1L).displayName("Technology").build()));

        Page<KPIMetricsDTO> result = kpiQueryService.getMetrics(1L, 1L, 0, 20, "roas", "desc");

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        KPIMetricsDTO dto = result.getContent().get(0);
        assertEquals("Google Ads", dto.getPlatformName());
        assertEquals("Technology", dto.getSectorName());
        assertEquals(BigDecimal.valueOf(4.5), dto.getRoas());
    }

    @Test
    void testGetMetricsPopulatesPrimarySourceName() {
        KPIMetrics metrics = KPIMetrics.builder()
                .id(1L)
                .platformId(1L)
                .sectorId(1L)
                .roas(BigDecimal.valueOf(4.5))
                .build();

        Page<KPIMetrics> page = new PageImpl<>(Collections.singletonList(metrics));
        when(kpiMetricsRepository.findByPlatformIdAndSectorId(
                anyLong(), anyLong(), any(Pageable.class))).thenReturn(page);
        when(platformRepository.findById(1L)).thenReturn(
                Optional.of(Platform.builder().id(1L).displayName("Google Ads").build()));
        when(sectorRepository.findById(1L)).thenReturn(
                Optional.of(CommerceSector.builder().id(1L).displayName("Technology").build()));

        Map<Long, String> sourceMap = new HashMap<>();
        sourceMap.put(1L, "Google Ads API");
        when(sourceAttributionService.getPrimarySourceNames(anySet())).thenReturn(sourceMap);

        Page<KPIMetricsDTO> result = kpiQueryService.getMetrics(1L, 1L, 0, 20, "roas", "desc");

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        KPIMetricsDTO dto = result.getContent().get(0);
        assertEquals("Google Ads API", dto.getPrimarySourceName());
    }

    @Test
    void testCacheHitReturnsCachedData() {
        @SuppressWarnings("unchecked")
        Page<KPIMetricsDTO> cachedPage = mock(Page.class);
        when(cacheService.get(anyString())).thenReturn(cachedPage);

        Page<KPIMetricsDTO> result = kpiQueryService.getMetrics(1L, 1L, 0, 20, "roas", "desc");

        assertNotNull(result);
        verify(kpiMetricsRepository, never()).findByPlatformIdAndSectorId(anyLong(), anyLong(), any(Pageable.class));
    }

    @Test
    void testDataStaleness() {
        KPIMetrics freshMetrics = KPIMetrics.builder()
                .ingestionTimestamp(Instant.now())
                .build();

        KPIMetrics staleMetrics = KPIMetrics.builder()
                .ingestionTimestamp(Instant.now().minusSeconds(3600)) // 1 hour old
                .build();

        KPIMetrics nullTimestampMetrics = KPIMetrics.builder()
                .ingestionTimestamp(null)
                .build();

        assertFalse(kpiQueryService.isDataStale(freshMetrics));
        assertTrue(kpiQueryService.isDataStale(staleMetrics));
        assertTrue(kpiQueryService.isDataStale(nullTimestampMetrics));
    }
}
