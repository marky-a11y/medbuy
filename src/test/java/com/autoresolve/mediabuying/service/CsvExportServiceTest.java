package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class CsvExportServiceTest {

    @Mock
    private KPIMetricsRepository kpiMetricsRepository;

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private CommerceSectorRepository sectorRepository;

    @Mock
    private SourceAttributionService sourceAttributionService;

    private CsvExportService csvExportService;

    @BeforeEach
    void setUp() {
        when(sourceAttributionService.getPrimarySourceNames(anySet())).thenReturn(new HashMap<>());
        csvExportService = new CsvExportService(
                kpiMetricsRepository, platformRepository, sectorRepository, sourceAttributionService);
    }

    @Test
    void testGenerateCsvReturnsValidContent() {
        KPIMetrics metrics = KPIMetrics.builder()
                .id(1L)
                .platformId(1L)
                .sectorId(1L)
                .roas(BigDecimal.valueOf(4.5))
                .cac(BigDecimal.valueOf(23.50))
                .cltv(BigDecimal.valueOf(487.00))
                .conversionRate(BigDecimal.valueOf(0.0345))
                .contributionMargin(BigDecimal.valueOf(112.40))
                .paybackPeriod(BigDecimal.valueOf(1.9))
                .incrementalReturn(BigDecimal.valueOf(145.80))
                .costPerQualifiedLead(BigDecimal.valueOf(18.75))
                .scalability(BigDecimal.valueOf(250000.00))
                .cashConversionCycle(BigDecimal.valueOf(45.0))
                .saturationPoint(BigDecimal.valueOf(0.32))
                .attributionAccuracy(BigDecimal.valueOf(0.91))
                .ingestionTimestamp(Instant.parse("2026-06-29T10:15:00Z"))
                .dataSource("Google Ads API")
                .build();

        when(kpiMetricsRepository.findAll()).thenReturn(Collections.singletonList(metrics));
        when(platformRepository.findById(1L)).thenReturn(
                Optional.of(com.autoresolve.mediabuying.model.entity.Platform.builder()
                        .id(1L).displayName("Google Ads").build()));
        when(sectorRepository.findById(1L)).thenReturn(
                Optional.of(com.autoresolve.mediabuying.model.entity.CommerceSector.builder()
                        .id(1L).displayName("Technology").build()));

        // Set up source name mock
        Map<Long, String> sourceMap = new HashMap<>();
        sourceMap.put(1L, "Google Ads API");
        when(sourceAttributionService.getPrimarySourceNames(anySet())).thenReturn(sourceMap);

        String csv = csvExportService.generateCsv(null, null);

        assertNotNull(csv);
        assertTrue(csv.contains("Platform"));
        assertTrue(csv.contains("Google Ads"));
        assertTrue(csv.contains("Technology"));
        assertTrue(csv.contains("4.5"));
        // Verify Source column is present
        assertTrue(csv.contains("Source"));
        assertTrue(csv.contains("Google Ads API"));
    }
}
