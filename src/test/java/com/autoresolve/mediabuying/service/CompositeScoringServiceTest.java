package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.config.ScoringWeightConfig;
import com.autoresolve.mediabuying.model.dto.TopOpportunityDTO;
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

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompositeScoringServiceTest {

    @Mock
    private ScoringWeightConfig weightConfig;

    @Mock
    private KPIMetricsRepository kpiRepository;

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private CommerceSectorRepository sectorRepository;

    @Mock
    private CacheService cacheService;

    @Mock
    private ClientLookupService clientLookupService;

    private CompositeScoringService scoringService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scoringService = new CompositeScoringService(
                weightConfig, kpiRepository, platformRepository,
                sectorRepository, cacheService, clientLookupService, meterRegistry);
    }

    @Test
    void testEmptyTableReturnsPlaceholder() {
        when(kpiRepository.findAll()).thenReturn(Collections.emptyList());

        TopOpportunityDTO result = scoringService.calculateTopOpportunity();

        assertNotNull(result);
        assertEquals("No data available", result.getPlatformName());
        assertEquals("N/A", result.getQualitativeBadge());
        assertEquals(0.0, result.getCompositeScore());
    }

    @Test
    void testAllZerosReturnsZeroScore() {
        KPIMetrics metrics = KPIMetrics.builder()
                .platformId(1L)
                .sectorId(1L)
                .roas(BigDecimal.ZERO)
                .cac(BigDecimal.ZERO)
                .cltv(BigDecimal.ZERO)
                .conversionRate(BigDecimal.ZERO)
                .scalability(BigDecimal.ZERO)
                .attributionAccuracy(BigDecimal.ZERO)
                .build();

        when(kpiRepository.findAll()).thenReturn(Collections.singletonList(metrics));
        when(platformRepository.findAll()).thenReturn(Collections.singletonList(
                Platform.builder().id(1L).displayName("Test Platform").build()));
        when(sectorRepository.findAll()).thenReturn(Collections.singletonList(
                CommerceSector.builder().id(1L).displayName("Test Sector").build()));

        Map<String, Double> weights = new HashMap<>();
        weights.put("roas", 0.25);
        weights.put("cac", 0.20);
        weights.put("cltv", 0.20);
        weights.put("conversion-rate", 0.15);
        weights.put("scalability", 0.10);
        weights.put("attribution-accuracy", 0.10);
        when(weightConfig.getWeights()).thenReturn(weights);

        ScoringWeightConfig.Targets targets = new ScoringWeightConfig.Targets();
        targets.setRoasTarget(4.0);
        targets.setMaxCac(50.0);
        targets.setCltvTarget(500.0);
        targets.setMaxScalability(1000000.0);
        targets.setConversionRateTarget(0.05);
        when(weightConfig.getTargets()).thenReturn(targets);

        TopOpportunityDTO result = scoringService.calculateTopOpportunity();

        assertNotNull(result);
        assertEquals(0.0, result.getCompositeScore());
        assertEquals("Low", result.getQualitativeBadge());
    }

    @Test
    void testMaxValuesReturnsHighScore() {
        KPIMetrics metrics = KPIMetrics.builder()
                .platformId(1L)
                .sectorId(1L)
                .roas(BigDecimal.valueOf(100.0))
                .cac(BigDecimal.valueOf(1.0))
                .cltv(BigDecimal.valueOf(10000.0))
                .conversionRate(BigDecimal.valueOf(0.5))
                .scalability(BigDecimal.valueOf(10000000.0))
                .attributionAccuracy(BigDecimal.valueOf(1.0))
                .build();

        when(kpiRepository.findAll()).thenReturn(Collections.singletonList(metrics));
        when(platformRepository.findAll()).thenReturn(Collections.singletonList(
                Platform.builder().id(1L).displayName("Test Platform").build()));
        when(sectorRepository.findAll()).thenReturn(Collections.singletonList(
                CommerceSector.builder().id(1L).displayName("Test Sector").build()));

        Map<String, Double> weights = new HashMap<>();
        weights.put("roas", 0.25);
        weights.put("cac", 0.20);
        weights.put("cltv", 0.20);
        weights.put("conversion-rate", 0.15);
        weights.put("scalability", 0.10);
        weights.put("attribution-accuracy", 0.10);
        when(weightConfig.getWeights()).thenReturn(weights);

        ScoringWeightConfig.Targets targets = new ScoringWeightConfig.Targets();
        targets.setRoasTarget(4.0);
        targets.setMaxCac(50.0);
        targets.setCltvTarget(500.0);
        targets.setMaxScalability(1000000.0);
        targets.setConversionRateTarget(0.05);
        when(weightConfig.getTargets()).thenReturn(targets);

        TopOpportunityDTO result = scoringService.calculateTopOpportunity();

        assertNotNull(result);
        assertTrue(result.getCompositeScore() > 80);
        assertEquals("High", result.getQualitativeBadge());
    }

    @Test
    void testWeightedDistribution() {
        // Create two entities with different scores
        KPIMetrics better = KPIMetrics.builder()
                .platformId(1L).sectorId(1L)
                .roas(BigDecimal.valueOf(8.0))
                .cac(BigDecimal.valueOf(10.0))
                .cltv(BigDecimal.valueOf(1000.0))
                .conversionRate(BigDecimal.valueOf(0.1))
                .scalability(BigDecimal.valueOf(500000.0))
                .attributionAccuracy(BigDecimal.valueOf(0.9))
                .build();

        KPIMetrics worse = KPIMetrics.builder()
                .platformId(2L).sectorId(2L)
                .roas(BigDecimal.valueOf(1.0))
                .cac(BigDecimal.valueOf(100.0))
                .cltv(BigDecimal.valueOf(50.0))
                .conversionRate(BigDecimal.valueOf(0.01))
                .scalability(BigDecimal.valueOf(1000.0))
                .attributionAccuracy(BigDecimal.valueOf(0.3))
                .build();

        when(kpiRepository.findAll()).thenReturn(Arrays.asList(better, worse));
        when(platformRepository.findAll()).thenReturn(Arrays.asList(
                Platform.builder().id(1L).displayName("Better Platform").build(),
                Platform.builder().id(2L).displayName("Worse Platform").build()));
        when(sectorRepository.findAll()).thenReturn(Arrays.asList(
                CommerceSector.builder().id(1L).displayName("Better Sector").build(),
                CommerceSector.builder().id(2L).displayName("Worse Sector").build()));

        Map<String, Double> weights = new HashMap<>();
        weights.put("roas", 0.25);
        weights.put("cac", 0.20);
        weights.put("cltv", 0.20);
        weights.put("conversion-rate", 0.15);
        weights.put("scalability", 0.10);
        weights.put("attribution-accuracy", 0.10);
        when(weightConfig.getWeights()).thenReturn(weights);

        ScoringWeightConfig.Targets targets = new ScoringWeightConfig.Targets();
        targets.setRoasTarget(4.0);
        targets.setMaxCac(50.0);
        targets.setCltvTarget(500.0);
        targets.setMaxScalability(1000000.0);
        targets.setConversionRateTarget(0.05);
        when(weightConfig.getTargets()).thenReturn(targets);

        TopOpportunityDTO result = scoringService.calculateTopOpportunity();

        assertNotNull(result);
        assertEquals("Better Platform", result.getPlatformName());
        assertTrue(result.getCompositeScore() > 50);
    }

    @Test
    void testCacheHitReturnsCachedData() {
        TopOpportunityDTO cached = TopOpportunityDTO.builder()
                .platformId(1L)
                .platformName("Cached Platform")
                .compositeScore(95.0)
                .qualitativeBadge("High")
                .computedAt(new Date().toInstant())
                .build();

        when(cacheService.get("composite:top")).thenReturn(cached);

        TopOpportunityDTO result = scoringService.calculateTopOpportunity();

        assertNotNull(result);
        assertEquals("Cached Platform", result.getPlatformName());
        assertEquals(95.0, result.getCompositeScore());

        verify(kpiRepository, never()).findAll();
    }
}
