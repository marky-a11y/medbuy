package com.autoresolve.mediabuying.service;

import com.autoresolve.mediabuying.cache.CacheKeys;
import com.autoresolve.mediabuying.cache.CacheService;
import com.autoresolve.mediabuying.integration.llm.LlmInsightProvider;
import com.autoresolve.mediabuying.model.dto.InsightDTO;
import com.autoresolve.mediabuying.model.entity.Client;
import com.autoresolve.mediabuying.model.entity.CommerceSector;
import com.autoresolve.mediabuying.repository.ClientRepository;
import com.autoresolve.mediabuying.repository.CommerceSectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightsEngineTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private CommerceSectorRepository commerceSectorRepository;

    @Mock
    private LlmInsightProvider llmInsightProvider;

    @Mock
    private CacheService cacheService;

    @Captor
    private ArgumentCaptor<List<InsightDTO>> insightListCaptor;

    private InsightsEngine insightsEngine;

    @BeforeEach
    void setUp() {
        insightsEngine = new InsightsEngine(clientRepository, commerceSectorRepository,
                llmInsightProvider, cacheService);
    }

    @Test
    void testGetClientGapInsights_CacheHit() {
        // Arrange
        List<InsightDTO> cachedInsights = Collections.singletonList(
                InsightDTO.builder().sectorName("Tech").insightType("GAP").build());
        when(cacheService.get(CacheKeys.INSIGHTS_CLIENT_GAPS)).thenReturn(cachedInsights);

        // Act
        List<InsightDTO> result = insightsEngine.getClientGapInsights();

        // Assert
        assertEquals(1, result.size());
        assertEquals("Tech", result.get(0).getSectorName());
        // Verify no calls to repositories or provider
        verify(clientRepository, never()).findByIsActiveTrue();
        verify(commerceSectorRepository, never()).findByIsActiveTrue();
        verify(llmInsightProvider, never()).generateInsights(any());
    }

    @Test
    void testGetClientGapInsights_CacheMiss() {
        // Arrange
        when(cacheService.get(CacheKeys.INSIGHTS_CLIENT_GAPS)).thenReturn(null);

        CommerceSector sector = CommerceSector.builder()
                .id(1L).name("tech").displayName("Technology").isActive(true).build();
        when(commerceSectorRepository.findByIsActiveTrue())
                .thenReturn(Collections.singletonList(sector));

        Client client = Client.builder()
                .id(1L).clientName("Client A").outlookScore(85)
                .contractType("RETAINER")
                .contractEndDate(LocalDate.now().plusMonths(1))
                .sector(sector).isActive(true).build();
        when(clientRepository.findByIsActiveTrue())
                .thenReturn(Collections.singletonList(client));

        List<InsightDTO> generatedInsights = Collections.singletonList(
                InsightDTO.builder().sectorName("Technology").insightType("RISK").build());
        when(llmInsightProvider.generateInsights(any())).thenReturn(generatedInsights);

        // Act
        List<InsightDTO> result = insightsEngine.getClientGapInsights();

        // Assert
        assertEquals(1, result.size());
        assertEquals("Technology", result.get(0).getSectorName());

        // Verify cache was written
        verify(cacheService).put(eq(CacheKeys.INSIGHTS_CLIENT_GAPS), anyList(),
                eq(5 * 60 * 1000L));
    }

    @Test
    void testGetClientGapInsights_EmptyData() {
        // Arrange
        when(cacheService.get(CacheKeys.INSIGHTS_CLIENT_GAPS)).thenReturn(null);
        when(commerceSectorRepository.findByIsActiveTrue()).thenReturn(Collections.emptyList());
        when(clientRepository.findByIsActiveTrue()).thenReturn(Collections.emptyList());

        LlmInsightProvider.ClientAnalytics analyticsCaptured = new LlmInsightProvider.ClientAnalytics(Collections.emptyMap());
        when(llmInsightProvider.generateInsights(any())).thenReturn(Collections.emptyList());

        // Act
        List<InsightDTO> result = insightsEngine.getClientGapInsights();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetClientGapInsights_ProviderException() {
        // Arrange
        when(cacheService.get(CacheKeys.INSIGHTS_CLIENT_GAPS)).thenReturn(null);

        CommerceSector sector = CommerceSector.builder()
                .id(1L).name("finance").displayName("Finance").isActive(true).build();
        when(commerceSectorRepository.findByIsActiveTrue())
                .thenReturn(Collections.singletonList(sector));

        Client client = Client.builder()
                .id(1L).clientName("Client B").outlookScore(50)
                .contractType("PERFORMANCE")
                .contractEndDate(LocalDate.now().plusMonths(6))
                .sector(sector).isActive(true).build();
        when(clientRepository.findByIsActiveTrue())
                .thenReturn(Collections.singletonList(client));

        when(llmInsightProvider.generateInsights(any()))
                .thenThrow(new RuntimeException("LLM provider unavailable"));

        // Act
        List<InsightDTO> result = insightsEngine.getClientGapInsights();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetClientGapInsights_CorrectAnalyticsDelegation() {
        // Arrange
        when(cacheService.get(CacheKeys.INSIGHTS_CLIENT_GAPS)).thenReturn(null);

        CommerceSector sector = CommerceSector.builder()
                .id(1L).name("retail").displayName("Retail").isActive(true).build();
        when(commerceSectorRepository.findByIsActiveTrue())
                .thenReturn(Collections.singletonList(sector));

        Client client1 = Client.builder()
                .id(1L).clientName("Client A").outlookScore(90)
                .contractType("RETAINER")
                .contractEndDate(LocalDate.now().plusMonths(1))
                .sector(sector).isActive(true).build();
        Client client2 = Client.builder()
                .id(2L).clientName("Client B").outlookScore(75)
                .contractType("RETAINER")
                .contractEndDate(LocalDate.now().plusMonths(6))
                .sector(sector).isActive(true).build();
        when(clientRepository.findByIsActiveTrue())
                .thenReturn(List.of(client1, client2));

        List<InsightDTO> expectedInsights = Collections.singletonList(
                InsightDTO.builder().sectorName("Retail").insightType("GAP").build());
        when(llmInsightProvider.generateInsights(any())).thenReturn(expectedInsights);

        // Act
        List<InsightDTO> result = insightsEngine.getClientGapInsights();

        // Assert
        assertEquals(1, result.size());
        assertEquals("Retail", result.get(0).getSectorName());

        // Verify the provider was called with correct analytics
        ArgumentCaptor<LlmInsightProvider.ClientAnalytics> analyticsCaptor =
                ArgumentCaptor.forClass(LlmInsightProvider.ClientAnalytics.class);
        verify(llmInsightProvider).generateInsights(analyticsCaptor.capture());

        LlmInsightProvider.ClientAnalytics captured = analyticsCaptor.getValue();
        assertNotNull(captured.getSectors());
        assertTrue(captured.getSectors().containsKey("Retail"));
        LlmInsightProvider.SectorStats stats = captured.getSectors().get("Retail");
        assertEquals(2, stats.getClientCount());
        assertEquals(82.5, stats.getAvgOutlookScore(), 0.01);
        assertEquals(1, stats.getExpiringContractCount()); // client1 has 1 month to expiry
        assertEquals(2, stats.getContractTypeCounts().get("RETAINER").intValue());
    }
}
