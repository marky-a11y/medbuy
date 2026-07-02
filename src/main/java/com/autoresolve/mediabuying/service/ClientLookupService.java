package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheKeys;
import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.model.dto.ClientInsightMetricsDTO;
import com.autoresolve.mediabuying.model.dto.ClientProspectDTO;
import com.autoresolve.mediabuying.model.entity.ClientProspect;
import com.autoresolve.mediabuying.repository.ClientProspectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClientLookupService {

    private static final Logger log = LoggerFactory.getLogger(ClientLookupService.class);

    private static final long CLIENTS_CACHE_TTL_MILLIS = 15 * 60 * 1000L;

    private final ClientProspectRepository clientProspectRepository;
    private final CacheService cacheService;
    private final ClientMetricsService clientMetricsService;

    public ClientLookupService(ClientProspectRepository clientProspectRepository,
                                CacheService cacheService,
                                ClientMetricsService clientMetricsService) {
        this.clientProspectRepository = clientProspectRepository;
        this.cacheService = cacheService;
        this.clientMetricsService = clientMetricsService;
    }

    /**
     * Finds the top 5 client prospects for a given sector, ranked by estimated ad budget (desc),
     * with computed sector fit scores. Results are cached with a 15-minute TTL.
     *
     * @param sectorId  the commerce sector ID
     * @param platformId the platform ID (reserved for future filtering; currently unused)
     * @return list of up to 5 ClientProspectDTOs sorted by ad budget descending
     */
    @SuppressWarnings("unchecked")
    public List<ClientProspectDTO> findTopClients(Long sectorId, Long platformId) {
        // 1. Check cache
        String cacheKey = CacheKeys.clientsTopKey(sectorId);
        List<ClientProspectDTO> cached = cacheService.get(cacheKey);
        if (cached != null) {
            log.debug("Top clients served from cache: key={}", cacheKey);
            return cached;
        }

        // 2. Query top 5 by ad budget DESC
        List<ClientProspect> topProspects = clientProspectRepository
                .findTop5BySectorIdAndIsActiveTrueOrderByEstAdBudgetDesc(sectorId);

        if (topProspects.isEmpty()) {
            log.warn("No active client prospects found for sectorId={}", sectorId);
            return Collections.emptyList();
        }

        // 3. Compute sector fit scores for all prospects in the sector (for normalization)
        List<ClientProspect> allInSector = clientProspectRepository
                .findBySectorIdAndIsActiveTrue(sectorId);

        double maxBudgetVal = 1.0;
        double maxRevenueVal = 1.0;
        double maxGrowthVal = 1.0;
        if (!allInSector.isEmpty()) {
            maxBudgetVal = allInSector.stream()
                    .mapToDouble(c -> c.getEstAdBudget() != null ? c.getEstAdBudget().doubleValue() : 0.0)
                    .max().orElse(1.0);
            maxRevenueVal = allInSector.stream()
                    .mapToDouble(c -> c.getEstAnnualRevenue() != null ? c.getEstAnnualRevenue().doubleValue() : 0.0)
                    .max().orElse(1.0);
            maxGrowthVal = allInSector.stream()
                    .mapToDouble(c -> c.getYoyGrowthRate() != null ? c.getYoyGrowthRate().doubleValue() : 0.0)
                    .map(Math::abs)
                    .max().orElse(1.0);
        }
        // Make effectively final copies for lambda
        final double maxBudget = maxBudgetVal;
        final double maxRevenue = maxRevenueVal;
        final double maxGrowth = maxGrowthVal;

        // 4. Build DTOs with computed scores and client-level KPI metrics
        List<ClientProspectDTO> dtos = topProspects.stream()
                .map(entity -> {
                    double budget = entity.getEstAdBudget() != null ? entity.getEstAdBudget().doubleValue() : 0.0;
                    double revenue = entity.getEstAnnualRevenue() != null ? entity.getEstAnnualRevenue().doubleValue() : 0.0;
                    double growth = entity.getYoyGrowthRate() != null ? Math.abs(entity.getYoyGrowthRate().doubleValue()) : 0.0;

                    int budgetScore = (int) Math.round((budget / maxBudget) * 100.0);
                    int revenueScore = (int) Math.round((revenue / maxRevenue) * 100.0);
                    int growthScore = (int) Math.round((growth / maxGrowth) * 100.0);

                    // Weight: budget 40%, revenue 30%, growth 30%
                    int fitScore = (int) Math.round(budgetScore * 0.4 + revenueScore * 0.3 + growthScore * 0.3);
                    fitScore = Math.min(100, Math.max(0, fitScore));

                    ClientProspectDTO dto = ClientProspectDTO.from(entity, fitScore);

                    // Enrich with client-level KPI metrics (Yelp rating, website quality, competitor density)
                    if (clientMetricsService != null) {
                        try {
                            ClientInsightMetricsDTO metrics = clientMetricsService.getMetrics(
                                    entity.getCompanyName(), entity.getIndustryVertical());
                            if (metrics != null) {
                                dto.setAvgYelpRating(metrics.getAvgYelpRating());
                                dto.setWebsiteQualityScore(metrics.getWebsiteQualityScore());
                                dto.setCompetitorDensity(metrics.getCompetitorDensity());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to load client metrics for '{}': {}",
                                    entity.getCompanyName(), e.getMessage());
                        }
                    }

                    return dto;
                })
                .collect(Collectors.toList());

        // 5. Store in cache
        try {
            cacheService.put(cacheKey, dtos, CLIENTS_CACHE_TTL_MILLIS);
        } catch (Exception e) {
            log.warn("Failed to cache client prospects for sectorId={}: {}", sectorId, e.getMessage());
        }

        log.info("Top client prospects computed for sectorId={}: {} clients returned", sectorId, dtos.size());
        return dtos;
    }
}
