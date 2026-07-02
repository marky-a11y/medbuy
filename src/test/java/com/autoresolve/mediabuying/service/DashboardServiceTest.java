package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.model.dto.DashboardModel;
import com.autoresolve.mediabuying.model.dto.PlatformDTO;
import com.autoresolve.mediabuying.model.dto.TopOpportunityDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private CompositeScoringService scoringService;

    @Mock
    private PlatformSectorService platformSectorService;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(scoringService, platformSectorService);
    }

    @Test
    void testGetDashboardWithoutFilter() {
        TopOpportunityDTO top = TopOpportunityDTO.builder()
                .platformId(1L)
                .platformName("Google Ads")
                .compositeScore(85.0)
                .qualitativeBadge("High")
                .computedAt(Instant.now())
                .build();

        List<PlatformDTO> platforms = Arrays.asList(
                PlatformDTO.builder().id(1L).displayName("Google Ads").build(),
                PlatformDTO.builder().id(2L).displayName("Meta Ads").build()
        );

        when(scoringService.calculateTopOpportunity()).thenReturn(top);
        when(platformSectorService.getActivePlatforms()).thenReturn(platforms);

        DashboardModel result = dashboardService.getDashboard(null);

        assertNotNull(result);
        assertEquals("Google Ads", result.getTopOpportunity().getPlatformName());
        assertEquals(2, result.getPlatforms().size());
        assertNull(result.getSectorFilter());
        assertNotNull(result.getLastRefreshed());
    }

    @Test
    void testGetDashboardWithSectorFilter() {
        TopOpportunityDTO top = TopOpportunityDTO.placeholder();
        List<PlatformDTO> platforms = Arrays.asList(
                PlatformDTO.builder().id(1L).displayName("Google Ads").build()
        );
        List<PlatformDTO> filteredPlatforms = Collections.singletonList(
                PlatformDTO.builder().id(1L).displayName("Google Ads").build()
        );

        when(scoringService.calculateTopOpportunity()).thenReturn(top);
        when(platformSectorService.getActivePlatforms()).thenReturn(platforms);
        when(platformSectorService.filterBySector(anyList(), eq(1L))).thenReturn(filteredPlatforms);

        DashboardModel result = dashboardService.getDashboard(1L);

        assertNotNull(result);
        assertEquals(1L, result.getSectorFilter().longValue());
        assertEquals(1, result.getPlatforms().size());
    }
}
