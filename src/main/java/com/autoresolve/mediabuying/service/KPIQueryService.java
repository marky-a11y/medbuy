package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheKeys;
import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.config.ScoringWeightConfig;
import com.autoresolve.mediabuying.model.dto.KPIMetricsDTO;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.model.entity.Platform;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KPIQueryService {

    private static final Logger log = LoggerFactory.getLogger(KPIQueryService.class);

    private final KPIMetricsRepository kpiMetricsRepository;
    private final PlatformRepository platformRepository;
    private final CommerceSectorRepository sectorRepository;
    private final CacheService cacheService;
    private final SourceAttributionService sourceAttributionService;
    private final int stalenessThresholdMinutes;

    // Custom Micrometer metrics
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public KPIQueryService(KPIMetricsRepository kpiMetricsRepository,
                            PlatformRepository platformRepository,
                            CommerceSectorRepository sectorRepository,
                            CacheService cacheService,
                            ScoringWeightConfig scoringWeightConfig,
                            SourceAttributionService sourceAttributionService,
                            MeterRegistry meterRegistry) {
        this.kpiMetricsRepository = kpiMetricsRepository;
        this.platformRepository = platformRepository;
        this.sectorRepository = sectorRepository;
        this.cacheService = cacheService;
        this.sourceAttributionService = sourceAttributionService;
        this.stalenessThresholdMinutes = 15; // Default, could be from config

        // Register custom Micrometer metrics
        this.cacheHitCounter = Counter.builder("media_buying_cache_hit_total")
                .description("Total number of cache hits in KPIQueryService")
                .tag("service", "KPIQueryService")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("media_buying_cache_miss_total")
                .description("Total number of cache misses in KPIQueryService")
                .tag("service", "KPIQueryService")
                .register(meterRegistry);
    }

    @SuppressWarnings("unchecked")
    public Page<KPIMetricsDTO> getMetrics(Long platformId, Long sectorId,
                                           int page, int size, String sortCol, String sortDir) {
        String cacheKey = CacheKeys.metricsKey(platformId, sectorId, page, sortCol);

        // Try cache first
        Page<KPIMetricsDTO> cached = null;
        try {
            cached = cacheService.get(cacheKey);
        } catch (Exception e) {
            log.warn("Cache read failed for key={}, falling through to DB", cacheKey, e);
        }
        if (cached != null) {
            log.debug("Metrics cache hit: key={}", cacheKey);
            try {
                cacheHitCounter.increment();
            } catch (Exception e) {
                // Metric failure must never affect business logic
                log.trace("Failed to record cache hit metric", e);
            }
            return cached;
        }

        // Record cache miss
        try {
            cacheMissCounter.increment();
        } catch (Exception e) {
            log.trace("Failed to record cache miss metric", e);
        }

        // Build sort
        Sort sort;
        if (sortCol != null && !sortCol.isEmpty()) {
            sort = "desc".equalsIgnoreCase(sortDir) ?
                    Sort.by(sortCol).descending() : Sort.by(sortCol).ascending();
        } else {
            sort = Sort.by("roas").descending();
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        // Query DB
        Page<KPIMetrics> metricsPage = kpiMetricsRepository
                .findByPlatformIdAndSectorId(platformId, sectorId, pageable);

        Page<KPIMetricsDTO> dtoPage = metricsPage.map(this::convertToDTO);

        // Batch-resolve primary source names for all KPIs on this page
        Set<Long> kpiIds = dtoPage.getContent().stream()
                .map(KPIMetricsDTO::getId)
                .collect(Collectors.toSet());
        if (!kpiIds.isEmpty()) {
            Map<Long, String> sourceNames = sourceAttributionService.getPrimarySourceNames(kpiIds);
            dtoPage.getContent().forEach(dto ->
                    dto.setPrimarySourceName(sourceNames.getOrDefault(dto.getId(), null)));
        }

        // Cache the result
        cacheService.put(cacheKey, dtoPage, 5 * 60 * 1000L);

        return dtoPage;
    }

    public boolean isDataStale(KPIMetrics metrics) {
        if (metrics.getIngestionTimestamp() == null) {
            return true;
        }
        return Duration.between(metrics.getIngestionTimestamp(), Instant.now())
                .toMinutes() > stalenessThresholdMinutes;
    }

    private KPIMetricsDTO convertToDTO(KPIMetrics metrics) {
        String platformName = "Unknown";
        String sectorName = "Unknown";

        Optional<Platform> platform = platformRepository.findById(metrics.getPlatformId());
        if (platform.isPresent()) {
            platformName = platform.get().getDisplayName();
        }

        Optional<CommerceSector> sector = sectorRepository.findById(metrics.getSectorId());
        if (sector.isPresent()) {
            sectorName = sector.get().getDisplayName();
        }

        return KPIMetricsDTO.builder()
                .id(metrics.getId())
                .platformId(metrics.getPlatformId())
                .platformName(platformName)
                .sectorId(metrics.getSectorId())
                .sectorName(sectorName)
                .roas(metrics.getRoas())
                .cac(metrics.getCac())
                .cltv(metrics.getCltv())
                .conversionRate(metrics.getConversionRate())
                .scalability(metrics.getScalability())
                .attributionAccuracy(metrics.getAttributionAccuracy())
                .contributionMargin(metrics.getContributionMargin())
                .paybackPeriod(metrics.getPaybackPeriod())
                .incrementalReturn(metrics.getIncrementalReturn())
                .costPerQualifiedLead(metrics.getCostPerQualifiedLead())
                .cashConversionCycle(metrics.getCashConversionCycle())
                .saturationPoint(metrics.getSaturationPoint())
                .ingestionTimestamp(metrics.getIngestionTimestamp())
                .dataSource(metrics.getDataSource())
                .dataStale(isDataStale(metrics))
                .build();
    }
}
