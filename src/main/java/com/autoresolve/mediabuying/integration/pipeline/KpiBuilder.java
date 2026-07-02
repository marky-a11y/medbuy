package com.autoresolve.mediabuying.integration.pipeline;

import com.autoresolve.mediabuying.eventbus.EventBus;
import com.autoresolve.mediabuying.eventbus.IntegrationEvent;
import com.autoresolve.mediabuying.integration.dto.PipelineBatchCompleteEvent;
import com.autoresolve.mediabuying.messaging.dto.CompanyPlatformMappingMessage;
import com.autoresolve.mediabuying.messaging.dto.KpiRefreshEvent;
import com.autoresolve.mediabuying.model.entity.DataSource;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.model.entity.KpiSourceAttribution;
import com.autoresolve.mediabuying.repository.DataSourceRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.KpiSourceAttributionRepository;
import com.autoresolve.mediabuying.service.KpiCsvWriter;
import com.autoresolve.mediabuying.service.OpportunityMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage 4 KPI building listener — two-phase design.
 * <p>
 * <strong>Phase 4a (synchronous):</strong> accumulates {@code company.grouped}
 * messages into an in-memory {@link ConcurrentHashMap} keyed by sector name.
 * Runs on the publisher's wrapper thread — no {@code @Async}.
 * </p>
 * <p>
 * <strong>Phase 4b (asynchronous):</strong> on {@code pipeline.batch-complete},
 * drains the accumulator, groups signals by (company + sector + platform),
 * blends into {@link KPIMetrics} via {@link KpiSignalAggregator}, upserts to the
 * database, and publishes {@link KpiRefreshEvent}s for each unique platform+sector.
 * </p>
 */
@Component
public class KpiBuilder {

    private static final Logger log = LoggerFactory.getLogger(KpiBuilder.class);

    private final KpiSignalAggregator kpiSignalAggregator;
    private final KPIMetricsRepository kpiMetricsRepository;
    private final EventBus eventBus;
    private final KpiCsvWriter kpiCsvWriter;
    private final KpiSourceAttributionRepository kpiSourceAttributionRepository;
    private final DataSourceRepository dataSourceRepository;
    private final OpportunityMetricsService opportunityMetricsService;

    /**
     * Per-sector accumulator. Key = sectorName (lowercase, trimmed), Value = synchronized list of messages.
     * Phase 4a appends; Phase 4b drains (removes + processes).
     */
    final ConcurrentHashMap<String, List<CompanyPlatformMappingMessage>> accumulator = new ConcurrentHashMap<>();

    public KpiBuilder(KpiSignalAggregator kpiSignalAggregator,
                      KPIMetricsRepository kpiMetricsRepository,
                      EventBus eventBus,
                      KpiCsvWriter kpiCsvWriter,
                      KpiSourceAttributionRepository kpiSourceAttributionRepository,
                      DataSourceRepository dataSourceRepository,
                      OpportunityMetricsService opportunityMetricsService) {
        this.kpiSignalAggregator = kpiSignalAggregator;
        this.kpiMetricsRepository = kpiMetricsRepository;
        this.eventBus = eventBus;
        this.kpiCsvWriter = kpiCsvWriter;
        this.kpiSourceAttributionRepository = kpiSourceAttributionRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.opportunityMetricsService = opportunityMetricsService;
    }

    // ========================================================================
    // Phase 4a: Synchronous accumulation
    // ========================================================================

    /**
     * Accumulate a {@code company.grouped} event into the per-sector buffer.
     * <p>
     * This method runs <strong>synchronously</strong> on the publisher's wrapper
     * thread. No DB writes — purely in-memory.
     * </p>
     *
     * @param event the integration event carrying a {@link CompanyPlatformMappingMessage} payload
     */
    @EventListener(condition = "#event.topic == 'company.grouped'")
    public void onCompanyGrouped(IntegrationEvent event) {
        if (event == null) {
            log.warn("Received null IntegrationEvent on topic 'company.grouped'");
            return;
        }

        Object payload = event.getPayload();
        if (!(payload instanceof CompanyPlatformMappingMessage)) {
            log.warn("Unexpected payload type on topic 'company.grouped': {} — expected CompanyPlatformMappingMessage",
                    payload != null ? payload.getClass().getName() : "null");
            return;
        }

        CompanyPlatformMappingMessage msg = (CompanyPlatformMappingMessage) payload;
        String sectorKey = msg.getSectorName();
        if (sectorKey == null) {
            sectorKey = "default";
        } else {
            sectorKey = sectorKey.toLowerCase().trim();
        }

        // Append to the synchronized list for this sector
        List<CompanyPlatformMappingMessage> list = accumulator
                .computeIfAbsent(sectorKey, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(msg);

        log.debug("Accumulated company.grouped: company='{}' sector='{}' accumulator-size={}",
                msg.getCompanyName(), sectorKey, list.size());
    }

    // ========================================================================
    // Phase 4b: Asynchronous batch processing
    // ========================================================================

    /**
     * Drain the accumulator on {@code pipeline.batch-complete}, compute KPIs,
     * upsert to DB, and publish refresh events.
     * <p>
     * Runs asynchronously on the {@code eventTaskExecutor} thread pool.
     * </p>
     *
     * @param event the integration event carrying a {@link PipelineBatchCompleteEvent} payload
     */
    @Async("eventTaskExecutor")
    @EventListener(condition = "#event.topic == 'pipeline.batch-complete'")
    public void onBatchComplete(IntegrationEvent event) {
        if (event == null) {
            log.warn("Received null IntegrationEvent on topic 'pipeline.batch-complete'");
            return;
        }

        Object payload = event.getPayload();
        PipelineBatchCompleteEvent batchEvent;
        if (payload instanceof PipelineBatchCompleteEvent) {
            batchEvent = (PipelineBatchCompleteEvent) payload;
        } else {
            log.warn("Unexpected payload type on topic 'pipeline.batch-complete': {} — expected PipelineBatchCompleteEvent",
                    payload != null ? payload.getClass().getName() : "null");
            return;
        }

        int totalSourcesProcessed = batchEvent.getTotalSources();
        int totalKpisUpserted = 0;
        int totalKpiRefreshEvents = 0;

        // Drain the accumulator: iterate over a snapshot of keys, remove each atomically
        Set<String> sectorKeys = new HashSet<>(accumulator.keySet());

        // Collect unique (platformId, sectorId) pairs for refresh events
        Set<String> refreshKeys = new HashSet<>();

        for (String sectorKey : sectorKeys) {
            // Atomically remove the entire list for this sector
            List<CompanyPlatformMappingMessage> signals = accumulator.remove(sectorKey);
            if (signals == null || signals.isEmpty()) {
                continue;
            }

            log.info("Processing {} signal(s) for sector '{}'", signals.size(), sectorKey);

            // Group signals by (companyName + sectorName + first inferredAdPlatform)
            Map<String, List<CompanyPlatformMappingMessage>> grouped = groupByCompanyPlatform(signals);

            for (Map.Entry<String, List<CompanyPlatformMappingMessage>> entry : grouped.entrySet()) {
                List<CompanyPlatformMappingMessage> groupSignals = entry.getValue();

                try {
                    // Aggregate into KPIMetrics
                    KPIMetrics kpiMetrics = kpiSignalAggregator.aggregate(groupSignals, sectorKey);
                    if (kpiMetrics == null) {
                        log.warn("KpiSignalAggregator returned null for group '{}' in sector '{}'",
                                entry.getKey(), sectorKey);
                        continue;
                    }

                    // Upsert to database
                    kpiMetricsRepository.upsert(kpiMetrics);
                    totalKpisUpserted++;

                    // Track unique (platformId, sectorId) for refresh
                    String refreshKey = kpiMetrics.getPlatformId() + ":" + kpiMetrics.getSectorId();
                    refreshKeys.add(refreshKey);

                    // Save source attribution for each unique source in this group
                    saveSourceAttribution(kpiMetrics, groupSignals);

                    log.debug("Upserted KPIMetrics for group '{}': platformId={} sectorId={} ROAS={}",
                            entry.getKey(), kpiMetrics.getPlatformId(), kpiMetrics.getSectorId(),
                            kpiMetrics.getRoas());

                } catch (Exception e) {
                    log.error("Failed to process KPI group '{}' in sector '{}': {}",
                            entry.getKey(), sectorKey, e.getMessage(), e);
                    // Continue with remaining groups — error isolation
                }
            }
        }

        // Publish a KpiRefreshEvent for each unique (platformId, sectorId)
        for (String refreshKey : refreshKeys) {
            try {
                String[] parts = refreshKey.split(":");
                Long platformId = Long.valueOf(parts[0]);
                Long sectorId = Long.valueOf(parts[1]);

                KpiRefreshEvent refreshEvent = KpiRefreshEvent.builder()
                        .platformId(platformId)
                        .sectorId(sectorId)
                        .eventType("KPI_UPDATED")
                        .refreshTimestamp(Instant.now())
                        .build();

                eventBus.publish("kpi.refresh", refreshKey, refreshEvent);
                totalKpiRefreshEvents++;
            } catch (Exception e) {
                log.error("Failed to publish KpiRefreshEvent for key '{}': {}", refreshKey, e.getMessage(), e);
            }
        }

        log.info("Batch complete: {} KPIs upserted, {} refresh events published from {} source(s)",
                totalKpisUpserted, totalKpiRefreshEvents, totalSourcesProcessed);

        if (accumulator.isEmpty()) {
            log.debug("Accumulator fully drained after batch-complete processing");
        } else {
            log.warn("Accumulator still has {} sector(s) after draining — possible concurrent accumulation",
                    accumulator.size());
        }

        // Auto-dump KPI data to a CSV file so the user can browse results
        // even when using the in-memory H2 database (dev profile).
        kpiCsvWriter.writeCsv();

        // Compute opportunity metrics from all data sources
        try {
            int opportunityCount = opportunityMetricsService.computeAll().size();
            log.info("Computed {} opportunity metric(s)", opportunityCount);
        } catch (Exception e) {
            log.error("Failed to compute opportunity metrics: {}", e.getMessage(), e);
        }
    }

    // ========== Internal grouping helper ==========

    /**
     * Group a list of signals by (companyName + sectorName + first inferred platform).
     * Returns a map keyed by a composite key string.
     */
    static Map<String, List<CompanyPlatformMappingMessage>> groupByCompanyPlatform(
            List<CompanyPlatformMappingMessage> signals) {
        Map<String, List<CompanyPlatformMappingMessage>> grouped = new HashMap<>();

        for (CompanyPlatformMappingMessage signal : signals) {
            String companyName = signal.getCompanyName() != null ? signal.getCompanyName() : "unknown";
            String sectorName = signal.getSectorName() != null ? signal.getSectorName() : "unknown";
            String platformName = "unknown";
            if (signal.getInferredAdPlatforms() != null && !signal.getInferredAdPlatforms().isEmpty()) {
                platformName = signal.getInferredAdPlatforms().get(0);
            }

            String key = companyName + "::" + sectorName + "::" + platformName;

            List<CompanyPlatformMappingMessage> group = grouped.get(key);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(key, group);
            }
            group.add(signal);
        }

        return grouped;
    }

    /**
     * Save KPI source attribution records for each unique source that
     * contributed signals to this KPI group.
     */
    private void saveSourceAttribution(KPIMetrics kpiMetrics,
                                        List<CompanyPlatformMappingMessage> signals) {
        if (kpiSourceAttributionRepository == null || dataSourceRepository == null) {
            return;
        }
        try {
            // Get the persisted KPI metrics ID
            java.util.Optional<KPIMetrics> saved = kpiMetricsRepository
                    .findTopByPlatformIdAndSectorIdOrderByIngestionTimestampDesc(
                            kpiMetrics.getPlatformId(), kpiMetrics.getSectorId());
            if (!saved.isPresent()) {
                log.warn("Cannot save attribution: KPI metrics not found for platform={} sector={}",
                        kpiMetrics.getPlatformId(), kpiMetrics.getSectorId());
                return;
            }
            Long kpiId = saved.get().getId();

            // Collect unique source names from the signal group
            java.util.Set<String> sourceNames = new java.util.HashSet<>();
            for (CompanyPlatformMappingMessage signal : signals) {
                if (signal.getSourceName() != null) {
                    sourceNames.add(signal.getSourceName().trim().toLowerCase());
                }
            }

            // Save one attribution record per source
            for (String sourceName : sourceNames) {
                java.util.Optional<DataSource> ds = dataSourceRepository.findBySourceName(sourceName);
                if (ds.isPresent()) {
                    KpiSourceAttribution attribution = KpiSourceAttribution.builder()
                            .kpiMetricsId(kpiId)
                            .dataSourceId(ds.get().getId())
                            .attributionContext("SIGNAL_GROUP")
                            .createdAt(java.time.Instant.now())
                            .build();
                    kpiSourceAttributionRepository.save(attribution);
                    log.trace("Saved source attribution: kpiId={} source={} dsId={}",
                            kpiId, sourceName, ds.get().getId());
                } else {
                    log.debug("No data_source record found for source name '{}'", sourceName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to save source attribution for platform={} sector={}: {}",
                    kpiMetrics.getPlatformId(), kpiMetrics.getSectorId(), e.getMessage());
        }
    }
}
