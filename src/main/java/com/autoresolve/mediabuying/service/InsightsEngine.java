package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheKeys;
import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.integration.llm.LlmInsightProvider;
import com.autoresolve.mediabuying.model.dto.InsightDTO;
import com.autoresolve.mediabuying.model.entity.Client;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.repository.ClientRepository;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that orchestrates the generation of AI-powered client gap/opportunity/risk insights.
 * Uses a cache-aside pattern with a 5-minute TTL to avoid redundant LLM calls.
 */
@Service
public class InsightsEngine {

    private static final Logger log = LoggerFactory.getLogger(InsightsEngine.class);

    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    private final ClientRepository clientRepository;
    private final CommerceSectorRepository commerceSectorRepository;
    private final LlmInsightProvider llmInsightProvider;
    private final CacheService cacheService;

    public InsightsEngine(ClientRepository clientRepository,
                          CommerceSectorRepository commerceSectorRepository,
                          LlmInsightProvider llmInsightProvider,
                          CacheService cacheService) {
        this.clientRepository = clientRepository;
        this.commerceSectorRepository = commerceSectorRepository;
        this.llmInsightProvider = llmInsightProvider;
        this.cacheService = cacheService;
    }

    /**
     * Returns AI-generated client gap/opportunity/risk insights.
     * Results are cached under {@code insights:client-gaps} for 5 minutes.
     *
     * @return list of insights (may be empty)
     */
    @SuppressWarnings("unchecked")
    public List<InsightDTO> getClientGapInsights() {
        // 1. Check cache
        try {
            List<InsightDTO> cached = cacheService.get(CacheKeys.INSIGHTS_CLIENT_GAPS);
            if (cached != null) {
                log.debug("Cache hit for client gap insights");
                return cached;
            }
        } catch (Exception e) {
            log.warn("Cache read failed for {}: {}", CacheKeys.INSIGHTS_CLIENT_GAPS, e.getMessage());
        }

        log.debug("Cache miss for client gap insights — generating fresh insights");

        try {
            // 2. Load active sectors and clients
            List<CommerceSector> activeSectors = commerceSectorRepository.findByIsActiveTrue();
            List<Client> activeClients = clientRepository.findByIsActiveTrue();

            // 3. Group clients by sector
            Map<Long, List<Client>> clientsBySector = activeClients.stream()
                    .filter(c -> c.getSector() != null && c.getSector().getId() != null)
                    .collect(Collectors.groupingBy(c -> c.getSector().getId()));

            // 4. Build analytics map keyed by sector display name
            Map<String, LlmInsightProvider.SectorStats> sectorAnalytics = new HashMap<>();

            for (CommerceSector sector : activeSectors) {
                List<Client> sectorClients = clientsBySector.getOrDefault(sector.getId(), Collections.emptyList());
                int clientCount = sectorClients.size();

                double avgOutlook = sectorClients.stream()
                        .filter(c -> c.getOutlookScore() != null)
                        .mapToInt(Client::getOutlookScore)
                        .average()
                        .orElse(0.0);

                int expiringCount = (int) sectorClients.stream()
                        .filter(c -> c.getContractEndDate() != null)
                        .filter(c -> ChronoUnit.MONTHS.between(LocalDate.now(), c.getContractEndDate()) < 3)
                        .count();

                Map<String, Integer> contractTypeCounts = sectorClients.stream()
                        .filter(c -> c.getContractType() != null)
                        .collect(Collectors.groupingBy(
                                Client::getContractType,
                                Collectors.summingInt(c -> 1)
                        ));

                sectorAnalytics.put(sector.getDisplayName(),
                        new LlmInsightProvider.SectorStats(clientCount, avgOutlook, expiringCount, contractTypeCounts));
            }

            // 5. Generate insights via LLM provider
            LlmInsightProvider.ClientAnalytics analytics =
                    new LlmInsightProvider.ClientAnalytics(sectorAnalytics);
            List<InsightDTO> insights = llmInsightProvider.generateInsights(analytics);

            // 6. Cache result
            try {
                cacheService.put(CacheKeys.INSIGHTS_CLIENT_GAPS, insights, CACHE_TTL_MILLIS);
            } catch (Exception e) {
                log.warn("Cache write failed for {}: {}", CacheKeys.INSIGHTS_CLIENT_GAPS, e.getMessage());
            }

            return insights;

        } catch (Exception e) {
            log.error("Failed to generate client gap insights: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
