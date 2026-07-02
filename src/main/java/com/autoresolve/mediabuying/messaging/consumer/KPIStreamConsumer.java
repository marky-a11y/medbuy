package com.autoresolve.mediabuying.messaging.consumer;

import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.exception.InvalidInputException;
import com.autoresolve.mediabuying.messaging.dto.RawKPIEvent;
import com.autoresolve.mediabuying.messaging.producer.KpiRefreshProducer;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import com.autoresolve.mediabuying.service.SourceAttributionService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class KPIStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(KPIStreamConsumer.class);

    private final KPIMetricsRepository kpiMetricsRepository;
    private final PlatformRepository platformRepository;
    private final CommerceSectorRepository sectorRepository;
    private final KpiRefreshProducer kpiRefreshProducer;
    private final SourceAttributionService sourceAttributionService;

    // Custom Micrometer gauge — tracks the epoch seconds of the last successful ingestion
    private volatile double lastIngestionTimestampEpochSeconds = 0.0;

    public KPIStreamConsumer(KPIMetricsRepository kpiMetricsRepository,
                              PlatformRepository platformRepository,
                              CommerceSectorRepository sectorRepository,
                              KpiRefreshProducer kpiRefreshProducer,
                              SourceAttributionService sourceAttributionService,
                              MeterRegistry meterRegistry) {
        this.kpiMetricsRepository = kpiMetricsRepository;
        this.platformRepository = platformRepository;
        this.sectorRepository = sectorRepository;
        this.kpiRefreshProducer = kpiRefreshProducer;
        this.sourceAttributionService = sourceAttributionService;

        // Register a gauge that reports the epoch seconds of the last successful KPI ingestion
        Gauge.builder("media_buying_ingestion_timestamp_seconds", this,
                KPIStreamConsumer::getLastIngestionTimestampEpochSeconds)
                .description("Epoch timestamp (seconds) of the last successful KPI data ingestion")
                .tag("component", "KPIStreamConsumer")
                .register(meterRegistry);
    }

    /**
     * Returns the epoch seconds of the last successful ingestion for the Micrometer gauge.
     */
    public double getLastIngestionTimestampEpochSeconds() {
        return lastIngestionTimestampEpochSeconds;
    }

    /**
     * Processes RawKPIEvent payloads from the {@code kpi.raw} event bus topic.
     * Events are delivered asynchronously via Spring Events
     * using the {@code eventTaskExecutor} thread pool.
     */
    @Async("eventTaskExecutor")
    @EventListener(condition = "#event.topic == 'kpi.raw'")
    public void consume(IntegrationEvent event) {
        Object payload = event.getPayload();
        if (!(payload instanceof RawKPIEvent)) {
            log.warn("Unexpected payload type on kpi.raw: {}", payload.getClass().getName());
            return;
        }
        RawKPIEvent rawEvent = (RawKPIEvent) payload;

        try {
            log.debug("Received KPI event: platform={}, sector={}, source={}",
                    rawEvent.getPlatformName(), rawEvent.getSectorName(), rawEvent.getDataSource());

            // Normalize and persist
            KPIMetrics metrics = convertToEntity(rawEvent);
            kpiMetricsRepository.upsert(metrics);

            // Retrieve the upserted record to get its auto-generated ID for source attribution
            Long platformId = metrics.getPlatformId();
            Long sectorId = metrics.getSectorId();
            linkSourcesIfPresent(rawEvent, platformId, sectorId);

            // Signal cache invalidation via kpi.refresh topic
            kpiRefreshProducer.sendKpiRefreshEvent(platformId, sectorId);

            log.info("KPI data ingested: platform={}, sector={}, timestamp={}",
                    rawEvent.getPlatformName(), rawEvent.getSectorName(), rawEvent.getIngestionTimestamp());

            // Update the ingestion timestamp gauge
            try {
                this.lastIngestionTimestampEpochSeconds = (double) Instant.now().getEpochSecond();
            } catch (Exception metricEx) {
                log.trace("Failed to update ingestion timestamp metric", metricEx);
            }

        } catch (Exception e) {
            log.error("Failed to process KPI event: {}", rawEvent.getEventId(), e);
            throw e;
        }
    }

    /**
     * Links source references from the event to the newly upserted KPI metrics record.
     * Source linking failures are logged but do NOT block ingestion.
     */
    private void linkSourcesIfPresent(RawKPIEvent event, Long platformId, Long sectorId) {
        if (event.getSourceReferences() == null || event.getSourceReferences().isEmpty()) {
            return;
        }

        try {
            kpiMetricsRepository.findTopByPlatformIdAndSectorIdOrderByIngestionTimestampDesc(platformId, sectorId)
                    .ifPresent(saved -> {
                        sourceAttributionService.linkKpiToSources(
                                saved.getId(),
                                event.getSourceReferences(),
                                "RAW");
                    });
        } catch (Exception e) {
            log.warn("Source linking failed for platform={}, sector={} – ingestion continues",
                    platformId, sectorId, e);
        }
    }

    private KPIMetrics convertToEntity(RawKPIEvent event) {
        Long platformId = platformRepository.findByName(event.getPlatformName())
                .orElseThrow(() -> new InvalidInputException("Unknown platform: " + event.getPlatformName()))
                .getId();

        Long sectorId = sectorRepository.findByName(event.getSectorName())
                .orElseThrow(() -> new InvalidInputException("Unknown sector: " + event.getSectorName()))
                .getId();

        KPIMetrics.KPIMetricsBuilder builder = KPIMetrics.builder()
                .platformId(platformId)
                .sectorId(sectorId)
                .ingestionTimestamp(event.getIngestionTimestamp() != null ?
                        event.getIngestionTimestamp() : Instant.now())
                .dataSource(event.getDataSource());

        if (event.getRoas() != null) builder.roas(BigDecimal.valueOf(event.getRoas()));
        if (event.getCac() != null) builder.cac(BigDecimal.valueOf(event.getCac()));
        if (event.getCltv() != null) builder.cltv(BigDecimal.valueOf(event.getCltv()));
        if (event.getConversionRate() != null) builder.conversionRate(BigDecimal.valueOf(event.getConversionRate()));
        if (event.getScalability() != null) builder.scalability(BigDecimal.valueOf(event.getScalability()));
        if (event.getAttributionAccuracy() != null) builder.attributionAccuracy(BigDecimal.valueOf(event.getAttributionAccuracy()));
        if (event.getContributionMargin() != null) builder.contributionMargin(BigDecimal.valueOf(event.getContributionMargin()));
        if (event.getPaybackPeriod() != null) builder.paybackPeriod(BigDecimal.valueOf(event.getPaybackPeriod()));
        if (event.getIncrementalReturn() != null) builder.incrementalReturn(BigDecimal.valueOf(event.getIncrementalReturn()));
        if (event.getCpql() != null) builder.costPerQualifiedLead(BigDecimal.valueOf(event.getCpql()));
        if (event.getCashConversionCycle() != null) builder.cashConversionCycle(BigDecimal.valueOf(event.getCashConversionCycle()));
        if (event.getSaturationPoint() != null) builder.saturationPoint(BigDecimal.valueOf(event.getSaturationPoint()));

        return builder.build();
    }
}
