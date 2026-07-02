package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.dto.DashboardModel;
import com.autoresolve.mediabuying.model.dto.PlatformDTO;
import com.autoresolve.mediabuying.model.dto.TopOpportunityDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DashboardService {

    private final CompositeScoringService scoringService;
    private final PlatformSectorService platformSectorService;

    public DashboardService(CompositeScoringService scoringService,
                             PlatformSectorService platformSectorService) {
        this.scoringService = scoringService;
        this.platformSectorService = platformSectorService;
    }

    public DashboardModel getDashboard(Long sectorFilterId) {
        // 1. Top Opportunity (cached by CompositeScoringService)
        TopOpportunityDTO top = scoringService.calculateTopOpportunity();

        // 2. Platform list (lazy — only IDs + names; sectors loaded on expand)
        List<PlatformDTO> platforms = platformSectorService.getActivePlatforms();

        // 3. If global sector filter active, pre-filter the hierarchy
        if (sectorFilterId != null) {
            platforms = platformSectorService.filterBySector(platforms, sectorFilterId);
        }

        return DashboardModel.builder()
                .topOpportunity(top)
                .platforms(platforms)
                .sectorFilter(sectorFilterId)
                .lastRefreshed(Instant.now())
                .build();
    }
}
