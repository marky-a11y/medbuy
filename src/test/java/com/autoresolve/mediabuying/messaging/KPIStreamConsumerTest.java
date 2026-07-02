package com.autoresolve.mediabuying.messaging;

import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.messaging.consumer.KPIStreamConsumer;
import com.autoresolve.mediabuying.messaging.dto.RawKPIEvent;
import com.autoresolve.mediabuying.messaging.producer.KpiRefreshProducer;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.model.entity.Platform;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
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

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KPIStreamConsumerTest {

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

    private KPIStreamConsumer consumer;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consumer = new KPIStreamConsumer(
                kpiMetricsRepository, platformRepository, sectorRepository,
                kpiRefreshProducer, sourceAttributionService, meterRegistry);
    }

    @Test
    void testConsumeValidEventPersistsAndProducesRefresh() {
        // Arrange
        RawKPIEvent event = new RawKPIEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setPlatformName("google_ads");
        event.setSectorName("technology");
        event.setDataSource("Google Ads API v16");
        event.setIngestionTimestamp(Instant.now());
        event.setRoas(4.5);
        event.setCac(23.50);
        event.setCltv(487.00);
        event.setConversionRate(0.0345);
        event.setScalability(250000.0);
        event.setAttributionAccuracy(0.91);
        event.setContributionMargin(112.40);
        event.setPaybackPeriod(1.9);
        event.setIncrementalReturn(145.80);
        event.setCpql(18.75);
        event.setCashConversionCycle(45.0);
        event.setSaturationPoint(0.32);
        event.setSourceReferences(Collections.singletonList("Google Ads API v17"));

        when(platformRepository.findByName("google_ads"))
                .thenReturn(Optional.of(Platform.builder().id(1L).build()));
        when(sectorRepository.findByName("technology"))
                .thenReturn(Optional.of(CommerceSector.builder().id(1L).build()));
        when(kpiMetricsRepository.findTopByPlatformIdAndSectorIdOrderByIngestionTimestampDesc(1L, 1L))
                .thenReturn(Optional.of(KPIMetrics.builder().id(42L).build()));

        // Act
        consumer.consume(new IntegrationEvent("kpi.raw", event.getPlatformName(), event));

        // Assert
        verify(kpiMetricsRepository).upsert(metricsCaptor.capture());
        KPIMetrics persisted = metricsCaptor.getValue();
        assertEquals(1L, persisted.getPlatformId().longValue());
        assertEquals(1L, persisted.getSectorId().longValue());
        assertNotNull(persisted.getRoas());
        assertEquals(4.5, persisted.getRoas().doubleValue(), 0.01);

        verify(kpiRefreshProducer).sendKpiRefreshEvent(1L, 1L);

        // Verify source linking was called
        verify(sourceAttributionService).linkKpiToSources(42L,
                Collections.singletonList("Google Ads API v17"), "RAW");
    }

    @Test
    void testConsumeWithoutSourceReferencesDoesNotCallLink() {
        RawKPIEvent event = new RawKPIEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setPlatformName("google_ads");
        event.setSectorName("technology");
        event.setIngestionTimestamp(Instant.now());

        when(platformRepository.findByName("google_ads"))
                .thenReturn(Optional.of(Platform.builder().id(1L).build()));
        when(sectorRepository.findByName("technology"))
                .thenReturn(Optional.of(CommerceSector.builder().id(1L).build()));

        consumer.consume(new IntegrationEvent("kpi.raw", event.getPlatformName(), event));

        verify(kpiMetricsRepository).upsert(any(KPIMetrics.class));
        verify(kpiRefreshProducer).sendKpiRefreshEvent(1L, 1L);
        verify(sourceAttributionService, never()).linkKpiToSources(anyLong(), anyList(), anyString());
    }

    @Test
    void testConsumeWithEmptySourceReferencesDoesNotCallLink() {
        RawKPIEvent event = new RawKPIEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setPlatformName("google_ads");
        event.setSectorName("technology");
        event.setIngestionTimestamp(Instant.now());
        event.setSourceReferences(Collections.emptyList());

        when(platformRepository.findByName("google_ads"))
                .thenReturn(Optional.of(Platform.builder().id(1L).build()));
        when(sectorRepository.findByName("technology"))
                .thenReturn(Optional.of(CommerceSector.builder().id(1L).build()));

        consumer.consume(new IntegrationEvent("kpi.raw", event.getPlatformName(), event));

        verify(kpiMetricsRepository).upsert(any(KPIMetrics.class));
        verify(kpiRefreshProducer).sendKpiRefreshEvent(1L, 1L);
        verify(sourceAttributionService, never()).linkKpiToSources(anyLong(), anyList(), anyString());
    }

    @Test
    void testSourceLinkingFailureDoesNotBlockIngestion() {
        RawKPIEvent event = new RawKPIEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setPlatformName("meta_ads");
        event.setSectorName("retail");
        event.setIngestionTimestamp(Instant.now());
        event.setSourceReferences(Collections.singletonList("Meta Marketing API"));

        when(platformRepository.findByName("meta_ads"))
                .thenReturn(Optional.of(Platform.builder().id(2L).build()));
        when(sectorRepository.findByName("retail"))
                .thenReturn(Optional.of(CommerceSector.builder().id(2L).build()));
        // findTopBy throws exception
        when(kpiMetricsRepository.findTopByPlatformIdAndSectorIdOrderByIngestionTimestampDesc(2L, 2L))
                .thenThrow(new RuntimeException("DB error"));

        // Should not throw - source linking failure is caught internally
        consumer.consume(new IntegrationEvent("kpi.raw", event.getPlatformName(), event));

        verify(kpiMetricsRepository).upsert(any(KPIMetrics.class));
        verify(kpiRefreshProducer).sendKpiRefreshEvent(2L, 2L);
        verify(sourceAttributionService, never()).linkKpiToSources(anyLong(), anyList(), anyString());
    }

    @Test
    void testConsumeWithUnknownPlatformThrowsException() {
        RawKPIEvent event = new RawKPIEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setPlatformName("unknown_platform");
        event.setSectorName("technology");

        when(platformRepository.findByName("unknown_platform")).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> consumer.consume(new IntegrationEvent("kpi.raw", event.getPlatformName(), event)));

        verify(kpiMetricsRepository, never()).upsert(any());
        verify(kpiRefreshProducer, never()).sendKpiRefreshEvent(anyLong(), anyLong());
    }

    @Test
    void testConsumeWithUnknownSectorThrowsException() {
        RawKPIEvent event = new RawKPIEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setPlatformName("google_ads");
        event.setSectorName("unknown_sector");

        when(platformRepository.findByName("google_ads"))
                .thenReturn(Optional.of(Platform.builder().id(1L).build()));
        when(sectorRepository.findByName("unknown_sector")).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> consumer.consume(new IntegrationEvent("kpi.raw", event.getPlatformName(), event)));

        verify(kpiMetricsRepository, never()).upsert(any());
        verify(kpiRefreshProducer, never()).sendKpiRefreshEvent(anyLong(), anyLong());
    }

    @Test
    void testConsumeEmptyEventHandlesGracefully() {
        RawKPIEvent event = new RawKPIEvent();
        event.setPlatformName("google_ads");
        event.setSectorName("technology");

        when(platformRepository.findByName("google_ads"))
                .thenReturn(Optional.of(Platform.builder().id(1L).build()));
        when(sectorRepository.findByName("technology"))
                .thenReturn(Optional.of(CommerceSector.builder().id(1L).build()));

        consumer.consume(new IntegrationEvent("kpi.raw", event.getPlatformName(), event));

        verify(kpiMetricsRepository).upsert(any(KPIMetrics.class));
        verify(kpiRefreshProducer).sendKpiRefreshEvent(1L, 1L);
    }
}
