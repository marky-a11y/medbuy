package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.dto.SourceMetadataDTO;
import com.autoresolve.mediabuying.model.entity.DataSource;
import com.autoresolve.mediabuying.model.entity.KpiSourceAttribution;
import com.autoresolve.mediabuying.repository.DataSourceRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.KpiSourceAttributionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for linking KPI metrics to their data sources
 * and verifying source URL availability.
 */
@Service
public class SourceAttributionService {

    private static final Logger log = LoggerFactory.getLogger(SourceAttributionService.class);

    private final DataSourceRepository dataSourceRepository;
    private final KpiSourceAttributionRepository kpiSourceAttributionRepository;
    private final KPIMetricsRepository kpiMetricsRepository;
    private final Counter staleSourceCounter;

    @Value("${source-verification.stale-threshold-days:30}")
    private int staleThresholdDays;

    @Autowired
    public SourceAttributionService(DataSourceRepository dataSourceRepository,
                                     KpiSourceAttributionRepository kpiSourceAttributionRepository,
                                     KPIMetricsRepository kpiMetricsRepository,
                                     MeterRegistry meterRegistry) {
        this.dataSourceRepository = dataSourceRepository;
        this.kpiSourceAttributionRepository = kpiSourceAttributionRepository;
        this.kpiMetricsRepository = kpiMetricsRepository;
        this.staleSourceCounter = Counter.builder("source.verification.stale")
                .description("Number of stale source URL verifications that failed")
                .register(meterRegistry);
    }

    // Package-private constructor for testing — allows direct injection of a Counter mock
    SourceAttributionService(DataSourceRepository dataSourceRepository,
                              KpiSourceAttributionRepository kpiSourceAttributionRepository,
                              KPIMetricsRepository kpiMetricsRepository,
                              Counter staleSourceCounter) {
        this.dataSourceRepository = dataSourceRepository;
        this.kpiSourceAttributionRepository = kpiSourceAttributionRepository;
        this.kpiMetricsRepository = kpiMetricsRepository;
        this.staleSourceCounter = staleSourceCounter;
    }

    /**
     * Links a KPI metrics record to one or more data sources.
     * Unknown source names are logged as warnings but do not throw exceptions.
     *
     * @param kpiMetricsId the ID of the KPIMetrics record
     * @param sourceNames  the list of data source names to link
     * @param context      the attribution context (RAW, INTERPOLATED, DERIVED)
     */
    @Transactional
    public void linkKpiToSources(Long kpiMetricsId, List<String> sourceNames, String context) {
        if (sourceNames == null || sourceNames.isEmpty()) {
            log.debug("No source references to link for kpiMetricsId={}", kpiMetricsId);
            return;
        }

        for (String sourceName : sourceNames) {
            try {
                dataSourceRepository.findBySourceName(sourceName)
                        .ifPresentOrElse(
                                ds -> {
                                    kpiSourceAttributionRepository.upsert(kpiMetricsId, ds.getId(), context);
                                    log.debug("Linked KPI {} to source '{}' (id={})", kpiMetricsId, sourceName, ds.getId());
                                },
                                () -> log.warn("Unknown data source name '{}' – skipping link for KPI {}", sourceName, kpiMetricsId)
                        );
            } catch (Exception e) {
                log.error("Failed to link KPI {} to source '{}'", kpiMetricsId, sourceName, e);
            }
        }
    }

    /**
     * Retrieves all source metadata for a given KPI metrics record.
     *
     * @param kpiMetricsId the ID of the KPIMetrics record
     * @return a list of SourceMetadataDTOs
     */
    @Transactional(readOnly = true)
    public List<SourceMetadataDTO> getSourcesForKpi(Long kpiMetricsId) {
        List<KpiSourceAttribution> attributions = kpiSourceAttributionRepository.findByKpiMetricsId(kpiMetricsId);
        List<SourceMetadataDTO> result = new ArrayList<>(attributions.size());

        for (KpiSourceAttribution ksa : attributions) {
            dataSourceRepository.findById(ksa.getDataSourceId())
                    .ifPresent(ds -> result.add(SourceMetadataDTO.from(ksa, ds)));
        }

        return result;
    }

    /**
     * Batch-resolves primary source names for a set of KPI IDs.
     * Multiple sources per KPI will be comma-joined.
     *
     * @param kpiIds the set of KPI metrics IDs
     * @return a map of KPI ID to its primary source name(s)
     */
    @Transactional(readOnly = true)
    public Map<Long, String> getPrimarySourceNames(Set<Long> kpiIds) {
        if (kpiIds == null || kpiIds.isEmpty()) return Collections.emptyMap();
        return kpiSourceAttributionRepository.findPrimarySourceNamesByKpiIds(kpiIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (String) row[1],
                        (a, b) -> a + ", " + b  // merge multiple sources per KPI
                ));
    }

    /**
     * Verifies all stale data sources by sending HTTP HEAD requests to their URLs.
     * Updates {@code lastVerifiedAt} on success; increments a Micrometer counter on failure.
     */
    public void verifySourceUrls() {
        Instant threshold = Instant.now().minus(staleThresholdDays, ChronoUnit.DAYS);
        List<DataSource> staleSources = dataSourceRepository.findStaleSources(threshold);

        if (staleSources.isEmpty()) {
            log.info("No stale data sources to verify");
            return;
        }

        int succeeded = 0;
        int total = staleSources.size();

        for (DataSource ds : staleSources) {
            HttpURLConnection connection = null;
            try {
                URI uri = new URI(ds.getSourceUrl());
                connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                int responseCode = connection.getResponseCode();

                if (responseCode >= 200 && responseCode < 400) {
                    ds.setLastVerifiedAt(Instant.now());
                    dataSourceRepository.save(ds);
                    succeeded++;
                    log.debug("Source '{}' verified successfully (HTTP {})", ds.getSourceName(), responseCode);
                } else {
                    log.warn("Source '{}' returned HTTP {} – not updating verification timestamp",
                            ds.getSourceName(), responseCode);
                    staleSourceCounter.increment();
                }
            } catch (Exception e) {
                log.warn("Failed to verify source '{}' at URL '{}': {}",
                        ds.getSourceName(), ds.getSourceUrl(), e.getMessage());
                staleSourceCounter.increment();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        log.info("Source verification complete: {}/{} URLs verified", succeeded, total);
    }
}
