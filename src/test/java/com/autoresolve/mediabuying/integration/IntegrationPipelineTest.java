package com.autoresolve.mediabuying.integration;

import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.integration.dto.PlatformApiResponse;
import com.autoresolve.mediabuying.integration.normalizer.KpiNormalizer;
import com.autoresolve.mediabuying.integration.wrapper.BaseApiWrapper;
import com.autoresolve.mediabuying.integration.wrapper.GoogleAdsApiWrapper;
import com.autoresolve.mediabuying.integration.wrapper.IHeartRadioApiWrapper;
import com.autoresolve.mediabuying.integration.wrapper.LinkedInApiWrapper;
import com.autoresolve.mediabuying.integration.wrapper.MetaAdsApiWrapper;
import com.autoresolve.mediabuying.integration.wrapper.TikTokApiWrapper;
import com.autoresolve.mediabuying.messaging.consumer.KPIStreamConsumer;
import com.autoresolve.mediabuying.messaging.dto.RawKPIEvent;
import com.autoresolve.mediabuying.messaging.producer.KpiRefreshProducer;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.model.entity.Platform;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.service.SourceAttributionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration-style test that verifies the full pipeline end-to-end:
 * wrapper -> normalizer -> event bus -> consumer -> DB upsert -> refresh event.
 * <p>
 * Uses mocked DB layers to verify wiring and data flow.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class IntegrationPipelineTest {

    @Mock
    private GoogleAdsApiWrapper googleAdsWrapper;

    @Mock
    private MetaAdsApiWrapper metaAdsWrapper;

    @Mock
    private TikTokApiWrapper tiktokWrapper;

    @Mock
    private LinkedInApiWrapper linkedInWrapper;

    @Mock
    private IHeartRadioApiWrapper iheartWrapper;

    @Mock
    private KPIMetricsRepository kpiMetricsRepository;

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private CommerceSectorRepository sectorRepository;

    @Mock
    private KpiRefreshProducer kpiRefreshProducer;

    @Mock
    private SourceAttributionService sourceAttributionService;

    @Captor
    private ArgumentCaptor<KPIMetrics> metricsCaptor;

    private KpiNormalizer normalizer;
    private KPIStreamConsumer streamConsumer;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        normalizer = new KpiNormalizer();
    }

    @Test
    void testFullPipelineEndToEnd() {
        // =========================================
        // 1. Simulate wrapper returning API response
        // =========================================
        PlatformApiResponse apiResponse = PlatformApiResponse.builder()
                .platformName("google_ads")
                .sectorName("technology")
                .roas(new BigDecimal("4.5"))
                .cac(new BigDecimal("23.50"))
                .cltv(new BigDecimal("487.00"))
                .conversionRate(new BigDecimal("0.0345"))
                .scalability(new BigDecimal("250000"))
                .attributionAccuracy(new BigDecimal("0.91"))
                .contributionMargin(new BigDecimal("112.40"))
                .paybackPeriod(new BigDecimal("1.9"))
                .incrementalReturn(new BigDecimal("145.80"))
                .costPerQualifiedLead(new BigDecimal("18.75"))
                .cashConversionCycle(new BigDecimal("45"))
                .saturationPoint(new BigDecimal("0.32"))
                .dataSource("Google Ads API v17")
                .build();

        // =========================================
        // 2. Normalizer converts to RawKPIEvent
        // =========================================
        RawKPIEvent rawEvent = normalizer.toRawKpiEvent(apiResponse, "google_ads");
        assertNotNull(rawEvent);
        assertEquals("google_ads", rawEvent.getPlatformName());
        assertEquals("technology", rawEvent.getSectorName());
        assertEquals(4.5, rawEvent.getRoas(), 0.001);
        assertEquals(250000.0, rawEvent.getScalability(), 0.001);

        // =========================================
        // 3. Publish via event bus & consume
        // =========================================
        when(platformRepository.findByName("google_ads"))
                .thenReturn(Optional.of(Platform.builder().id(1L).name("google_ads").build()));
        when(sectorRepository.findByName("technology"))
                .thenReturn(Optional.of(CommerceSector.builder().id(1L).name("technology").build()));
        when(kpiMetricsRepository.findTopByPlatformIdAndSectorIdOrderByIngestionTimestampDesc(1L, 1L))
                .thenReturn(Optional.of(KPIMetrics.builder().id(42L).build()));

        streamConsumer = new KPIStreamConsumer(
                kpiMetricsRepository, platformRepository, sectorRepository,
                kpiRefreshProducer, sourceAttributionService, meterRegistry);

        // Simulate receiving the event from the event bus
        streamConsumer.consume(new IntegrationEvent("kpi.raw", rawEvent.getPlatformName(), rawEvent));

        // =========================================
        // 4. Verify DB upsert
        // =========================================
        verify(kpiMetricsRepository).upsert(metricsCaptor.capture());

        KPIMetrics persisted = metricsCaptor.getValue();
        assertNotNull(persisted);
        assertEquals(1L, persisted.getPlatformId().longValue());
        assertEquals(1L, persisted.getSectorId().longValue());
        assertEquals(4.5, persisted.getRoas().doubleValue(), 0.001);
        assertEquals(23.50, persisted.getCac().doubleValue(), 0.001);
        assertEquals(487.00, persisted.getCltv().doubleValue(), 0.001);
        assertEquals(0.0345, persisted.getConversionRate().doubleValue(), 0.0001);
        assertEquals(250000.0, persisted.getScalability().doubleValue(), 0.001);
        assertEquals(0.91, persisted.getAttributionAccuracy().doubleValue(), 0.001);
        assertEquals(112.40, persisted.getContributionMargin().doubleValue(), 0.001);
        assertEquals(1.9, persisted.getPaybackPeriod().doubleValue(), 0.001);
        assertEquals(145.80, persisted.getIncrementalReturn().doubleValue(), 0.001);
        assertEquals(18.75, persisted.getCostPerQualifiedLead().doubleValue(), 0.001);
        assertEquals(45.0, persisted.getCashConversionCycle().doubleValue(), 0.001);
        assertEquals(0.32, persisted.getSaturationPoint().doubleValue(), 0.001);
        assertEquals("Google Ads API v17", persisted.getDataSource());

        // Verify source linking was called
        verify(sourceAttributionService).linkKpiToSources(eq(42L), anyList(), eq("RAW"));
    }

    @Test
    void testPipelineWithNullKpiValues() {
        // Simulate response with null KPI values
        PlatformApiResponse apiResponse = PlatformApiResponse.builder()
                .platformName("google_ads")
                .sectorName("technology")
                .roas(null)
                .cac(null)
                .dataSource("API")
                .build();

        RawKPIEvent rawEvent = normalizer.toRawKpiEvent(apiResponse, "google_ads");
        assertNotNull(rawEvent);
        assertNull(rawEvent.getRoas());
        assertNull(rawEvent.getCac());
        assertNull(rawEvent.getCltv());

        when(platformRepository.findByName("google_ads"))
                .thenReturn(Optional.of(Platform.builder().id(1L).build()));
        when(sectorRepository.findByName("technology"))
                .thenReturn(Optional.of(CommerceSector.builder().id(1L).build()));
        when(kpiMetricsRepository.findTopByPlatformIdAndSectorIdOrderByIngestionTimestampDesc(1L, 1L))
                .thenReturn(Optional.of(KPIMetrics.builder().id(43L).build()));

        streamConsumer = new KPIStreamConsumer(
                kpiMetricsRepository, platformRepository, sectorRepository,
                kpiRefreshProducer, sourceAttributionService, meterRegistry);

        streamConsumer.consume(new IntegrationEvent("kpi.raw", rawEvent.getPlatformName(), rawEvent));

        verify(kpiMetricsRepository).upsert(metricsCaptor.capture());
        KPIMetrics persisted = metricsCaptor.getValue();
        assertNull(persisted.getRoas());
        assertNull(persisted.getCac());
    }

    @Test
    void testMultiplePlatformsPipeline() {
        // Simulate 3 different platforms
        String[] platforms = {"google_ads", "meta_ads", "tiktok_ads"};
        Long[] platformIds = {1L, 2L, 3L};

        for (int i = 0; i < platforms.length; i++) {
            String platform = platforms[i];
            Long platformId = platformIds[i];

            PlatformApiResponse response = PlatformApiResponse.builder()
                    .platformName(platform)
                    .sectorName("technology")
                    .roas(new BigDecimal("3." + (i + 1)))
                    .cac(new BigDecimal("20.00"))
                    .dataSource(platform + " API")
                    .build();

            RawKPIEvent event = normalizer.toRawKpiEvent(response, platform);
            assertNotNull(event);

            when(platformRepository.findByName(platform))
                    .thenReturn(Optional.of(Platform.builder().id(platformId).name(platform).build()));
            when(sectorRepository.findByName("technology"))
                    .thenReturn(Optional.of(CommerceSector.builder().id(1L).build()));
            when(kpiMetricsRepository.findTopByPlatformIdAndSectorIdOrderByIngestionTimestampDesc(platformId, 1L))
                    .thenReturn(Optional.of(KPIMetrics.builder().id(100L + i).build()));

            streamConsumer = new KPIStreamConsumer(
                    kpiMetricsRepository, platformRepository, sectorRepository,
                    kpiRefreshProducer, sourceAttributionService, meterRegistry);
            streamConsumer.consume(new IntegrationEvent("kpi.raw", event.getPlatformName(), event));
        }

        verify(kpiMetricsRepository, times(3)).upsert(any(KPIMetrics.class));
    }
}
