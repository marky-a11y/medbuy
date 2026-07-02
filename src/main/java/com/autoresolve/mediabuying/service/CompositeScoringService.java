package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheKeys;
import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.config.ScoringWeightConfig;
import com.autoresolve.mediabuying.model.dto.ClientProspectDTO;
import com.autoresolve.mediabuying.model.dto.ScoredPlatformSector;
import com.autoresolve.mediabuying.model.dto.TopOpportunityDTO;
import com.autoresolve.mediabuying.model.entity.KPIMetrics;
import com.autoresolve.mediabuying.model.entity.Platform;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.repository.KPIMetricsRepository;
import com.autoresolve.mediabuying.repository.PlatformRepository;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import com.autoresolve.mediabuying.service.ClientLookupService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CompositeScoringService {

    private static final Logger log = LoggerFactory.getLogger(CompositeScoringService.class);

    private final ScoringWeightConfig weightConfig;
    private final KPIMetricsRepository kpiRepository;
    private final PlatformRepository platformRepository;
    private final CommerceSectorRepository sectorRepository;
    private final CacheService cacheService;
    private final ClientLookupService clientLookupService;

    // Custom Micrometer metrics
    private final Timer scoringComputationTimer;
    private final Gauge topScoreGauge;

    public CompositeScoringService(ScoringWeightConfig weightConfig,
                                    KPIMetricsRepository kpiRepository,
                                    PlatformRepository platformRepository,
                                    CommerceSectorRepository sectorRepository,
                                    CacheService cacheService,
                                    ClientLookupService clientLookupService,
                                    MeterRegistry meterRegistry) {
        this.weightConfig = weightConfig;
        this.kpiRepository = kpiRepository;
        this.platformRepository = platformRepository;
        this.sectorRepository = sectorRepository;
        this.cacheService = cacheService;
        this.clientLookupService = clientLookupService;

        // Register custom Micrometer metrics
        this.scoringComputationTimer = Timer.builder("media_buying_scoring_computation_seconds")
                .description("Time taken to compute composite scores")
                .tag("service", "CompositeScoringService")
                .register(meterRegistry);
        this.topScoreGauge = Gauge.builder("media_buying_scoring_top_score", this,
                CompositeScoringService::getCurrentTopScore)
                .description("Current top opportunity composite score (0-100)")
                .tag("service", "CompositeScoringService")
                .register(meterRegistry);
    }

    // Holder for the latest top score, used by the Gauge supplier
    private volatile double currentTopScore = 0.0;

    public double getCurrentTopScore() {
        return currentTopScore;
    }

    @SuppressWarnings("unchecked")
    public TopOpportunityDTO calculateTopOpportunity() {
        // Start timer for scoring computation
        Timer.Sample sample = Timer.start();
        String cacheKey = CacheKeys.COMPOSITE_TOP;
        TopOpportunityDTO cached = cacheService.get(cacheKey);
        if (cached != null) {
            log.debug("Top opportunity served from cache: key={}", cacheKey);
            return cached;
        }

        // 2. Fetch all KPI rows
        List<KPIMetrics> allMetrics = kpiRepository.findAll();

        if (allMetrics.isEmpty()) {
            log.warn("No KPI data available — returning placeholder top opportunity");
            TopOpportunityDTO placeholder = TopOpportunityDTO.placeholder();
            cacheService.put(cacheKey, placeholder, 5 * 60 * 1000L);
            return placeholder;
        }

        // 3. Build lookup maps for platform/sector names
        Map<Long, String> platformNames = platformRepository.findAll().stream()
                .collect(Collectors.toMap(Platform::getId, Platform::getDisplayName));
        Map<Long, String> sectorNames = sectorRepository.findAll().stream()
                .collect(Collectors.toMap(CommerceSector::getId, CommerceSector::getDisplayName));

        // 4. Compute scores
        Map<String, Double> weights = weightConfig.getWeights();
        ScoringWeightConfig.Targets targets = weightConfig.getTargets();

        List<ScoredPlatformSector> scored = allMetrics.stream()
                .map(m -> computeScore(m, weights, targets, platformNames, sectorNames))
                .sorted(Comparator.comparingDouble(ScoredPlatformSector::getScore).reversed())
                .collect(Collectors.toList());

        // 5. Build DTO
        ScoredPlatformSector top = scored.get(0);
        TopOpportunityDTO dto = buildTopOpportunityDTO(top);

        // 6. Attach top client prospects for the winning sector
        try {
            List<ClientProspectDTO> clients = clientLookupService.findTopClients(
                    top.getSectorId(), top.getPlatformId());
            dto.setTopClients(clients);
        } catch (Exception e) {
            log.warn("Failed to fetch client prospects for sector={}: {}",
                    top.getSectorId(), e.getMessage());
            dto.setTopClients(Collections.emptyList());
        }

        // 7. Store in cache with TTL
        cacheService.put(cacheKey, dto, 5 * 60 * 1000L);
        log.info("Top opportunity recomputed: {} - {} (score={})",
                dto.getPlatformName(), dto.getSectorName(), dto.getCompositeScore());

        // Record metrics
        try {
            sample.stop(scoringComputationTimer);
            this.currentTopScore = dto.getCompositeScore();
        } catch (Exception e) {
            log.trace("Failed to record scoring metrics", e);
        }

        return dto;
    }

    private ScoredPlatformSector computeScore(KPIMetrics m,
                                               Map<String, Double> weights,
                                               ScoringWeightConfig.Targets targets,
                                               Map<Long, String> platformNames,
                                               Map<Long, String> sectorNames) {
        double roasScore = computeNormalizedROAS(toDouble(m.getRoas()), targets.getRoasTarget());
        double cacScore = computeNormalizedCAC(toDouble(m.getCac()), targets.getMaxCac());
        double cltvScore = computeNormalizedCLTV(toDouble(m.getCltv()), targets.getCltvTarget());
        double crScore = computeNormalizedCR(toDouble(m.getConversionRate()), targets.getConversionRateTarget());
        double scalabilityScore = computeNormalizedScalability(toDouble(m.getScalability()), targets.getMaxScalability());
        double attributionScore = computeNormalizedAttribution(toDouble(m.getAttributionAccuracy()));

        double totalScore = 0;
        totalScore += roasScore * weights.getOrDefault("roas", 0.25);
        totalScore += cacScore * weights.getOrDefault("cac", 0.20);
        totalScore += cltvScore * weights.getOrDefault("cltv", 0.20);
        totalScore += crScore * weights.getOrDefault("conversion-rate", 0.15);
        totalScore += scalabilityScore * weights.getOrDefault("scalability", 0.10);
        totalScore += attributionScore * weights.getOrDefault("attribution-accuracy", 0.10);

        return ScoredPlatformSector.builder()
                .platformId(m.getPlatformId())
                .platformName(platformNames.getOrDefault(m.getPlatformId(), "Unknown"))
                .sectorId(m.getSectorId())
                .sectorName(sectorNames.getOrDefault(m.getSectorId(), "Unknown"))
                .score(totalScore)
                .rawMetrics(m)
                .build();
    }

    private TopOpportunityDTO buildTopOpportunityDTO(ScoredPlatformSector top) {
        KPIMetrics m = top.getRawMetrics();

        Map<String, Double> primaryKpis = new LinkedHashMap<>();
        primaryKpis.put("roas", toDouble(m.getRoas()));
        primaryKpis.put("cac", toDouble(m.getCac()));

        Map<String, Double> allKpis = new LinkedHashMap<>();
        allKpis.put("roas", toDouble(m.getRoas()));
        allKpis.put("cac", toDouble(m.getCac()));
        allKpis.put("cltv", toDouble(m.getCltv()));
        allKpis.put("conversionRate", toDouble(m.getConversionRate()));
        allKpis.put("scalability", toDouble(m.getScalability()));
        allKpis.put("attributionAccuracy", toDouble(m.getAttributionAccuracy()));

        return TopOpportunityDTO.builder()
                .platformId(top.getPlatformId())
                .platformName(top.getPlatformName())
                .sectorId(top.getSectorId())
                .sectorName(top.getSectorName())
                .compositeScore(roundTo1Decimal(top.getScore()))
                .qualitativeBadge(mapScoreToBadge(top.getScore()))
                .primaryKpis(primaryKpis)
                .allKpis(allKpis)
                .computedAt(Instant.now())
                .build();
    }

    private double computeNormalizedROAS(double actualRoas, Double target) {
        double t = target != null && target > 0 ? target : 4.0;
        return Math.min(actualRoas / t, 1.0) * 100.0;
    }

    private double computeNormalizedCAC(double actualCac, Double maxAcceptable) {
        double max = maxAcceptable != null && maxAcceptable > 0 ? maxAcceptable : 50.0;
        if (actualCac <= 0.0) {
            return 0.0; // Zero or missing data yields no CAC score
        }
        return Math.max(0.0, 1.0 - (actualCac / max)) * 100.0;
    }

    private double computeNormalizedCLTV(double actualCltv, Double target) {
        double t = target != null && target > 0 ? target : 500.0;
        return Math.min(actualCltv / t, 1.0) * 100.0;
    }

    private double computeNormalizedCR(double actualCr, Double target) {
        double t = target != null && target > 0 ? target : 0.05;
        return Math.min(actualCr / t, 1.0) * 100.0;
    }

    private double computeNormalizedScalability(double actualScalability, Double maxScalability) {
        double max = maxScalability != null && maxScalability > 0 ? maxScalability : 1000000.0;
        return Math.min(actualScalability / max, 1.0) * 100.0;
    }

    private double computeNormalizedAttribution(double actualAttribution) {
        return Math.min(actualAttribution, 1.0) * 100.0;
    }

    private String mapScoreToBadge(double score) {
        if (score >= 80) return "High";
        if (score >= 50) return "Medium";
        return "Low";
    }

    private double roundTo1Decimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }
}
